package dev.ofarafal.lumpeksscanner

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ofarafal.lumpeksscanner.data.AppSettings
import dev.ofarafal.lumpeksscanner.data.Brand
import dev.ofarafal.lumpeksscanner.data.BrandUpdate
import dev.ofarafal.lumpeksscanner.data.BrandDatabase
import dev.ofarafal.lumpeksscanner.data.BrandRepository
import dev.ofarafal.lumpeksscanner.match.BrandMatch
import dev.ofarafal.lumpeksscanner.match.BrandMatcher
import dev.ofarafal.lumpeksscanner.match.LabelAnalyzer
import dev.ofarafal.lumpeksscanner.match.LabelInfo
import dev.ofarafal.lumpeksscanner.ocr.OcrEngine
import dev.ofarafal.lumpeksscanner.update.AppUpdate
import dev.ofarafal.lumpeksscanner.update.AppUpdater
import dev.ofarafal.lumpeksscanner.update.DownloadState
import dev.ofarafal.lumpeksscanner.update.UpdateCheck
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanResult(
    val brand: Brand?,
    val matchedAlias: String?,
    val manual: Boolean,
    val label: LabelInfo,
    val rawText: String,
    /** Wszystkie marki, które przeszły próg dopasowania (do strojenia aliasów/progów). */
    val candidates: List<BrandMatch> = emptyList(),
)

sealed interface UiState {
    data object Camera : UiState
    data object Processing : UiState
    data class Result(val scan: ScanResult) : UiState
    data class Error(val message: String) : UiState
}

class ScannerViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = BrandRepository(app)
    private val ocr = OcrEngine()
    private val updater = AppUpdater(app)
    private val settings = AppSettings(app)
    private var downloadJob: Job? = null

    var database: BrandDatabase by mutableStateOf(repository.loadBest())
        private set
    private var matcher = BrandMatcher(database.brands)

    var state: UiState by mutableStateOf(UiState.Camera)
        private set

    /** Komunikat o aktualizacji bazy (pokazywany jako snackbar). */
    var updateMessage: String? by mutableStateOf(null)
        private set

    /** Dostępna nowsza wersja aplikacji (null = brak lub zamknięty dialog). */
    var availableUpdate: AppUpdate? by mutableStateOf(null)
        private set
    var downloadState: DownloadState? by mutableStateOf(null)
        private set

    // --- Ekran ustawień ---
    var showSettings: Boolean by mutableStateOf(false)
        private set
    var autoCheckUpdates: Boolean by mutableStateOf(settings.autoCheckUpdates)
        private set
    var appCheckInProgress: Boolean by mutableStateOf(false)
        private set
    var appCheckMessage: String? by mutableStateOf(null)
        private set
    var dbCheckInProgress: Boolean by mutableStateOf(false)
        private set
    var dbCheckMessage: String? by mutableStateOf(null)
        private set

    init {
        if (!BuildConfig.DEBUG && settings.autoCheckUpdates) {
            viewModelScope.launch { availableUpdate = updater.check() }
        }
        viewModelScope.launch {
            repository.fetchUpdate(database)?.let { fresh ->
                database = fresh
                matcher = BrandMatcher(fresh.brands)
                updateMessage = "Zaktualizowano bazę marek do wersji ${fresh.version} (${fresh.brands.size} marek)"
            }
        }
    }

    fun onPhoto(bitmap: Bitmap, rotationDegrees: Int) {
        state = UiState.Processing
        viewModelScope.launch {
            state = try {
                val text = ocr.recognize(bitmap, rotationDegrees)
                val (candidates, label) = withContext(Dispatchers.Default) {
                    matcher.candidates(text) to LabelAnalyzer.analyze(text)
                }
                val match = candidates.firstOrNull()
                UiState.Result(
                    ScanResult(
                        brand = match?.brand,
                        matchedAlias = match?.alias,
                        manual = false,
                        label = label,
                        rawText = text,
                        candidates = candidates,
                    )
                )
            } catch (e: Exception) {
                UiState.Error("Nie udało się odczytać metki: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun correctBrand(brand: Brand) {
        val current = state as? UiState.Result ?: return
        state = UiState.Result(current.scan.copy(brand = brand, matchedAlias = null, manual = true))
    }

    fun backToCamera() {
        state = UiState.Camera
    }

    fun openSettings() {
        appCheckMessage = null
        dbCheckMessage = null
        showSettings = true
    }

    fun closeSettings() {
        showSettings = false
    }

    fun setAutoCheck(enabled: Boolean) {
        settings.autoCheckUpdates = enabled
        autoCheckUpdates = enabled
    }

    /** Ręczne sprawdzenie aktualizacji aplikacji (działa też w buildzie debug). */
    fun checkAppUpdateNow() {
        if (appCheckInProgress) return
        appCheckInProgress = true
        appCheckMessage = null
        viewModelScope.launch {
            when (val result = updater.checkDetailed()) {
                is UpdateCheck.Available -> {
                    downloadState = null
                    availableUpdate = result.update
                    appCheckMessage = "Dostępna wersja ${result.update.versionName}"
                }
                is UpdateCheck.UpToDate ->
                    appCheckMessage = "Masz najnowszą wersję (najnowszy build na GitHubie: ${result.latestBuild})"
                is UpdateCheck.Failed -> appCheckMessage = "Nie udało się sprawdzić: ${result.reason}"
            }
            appCheckInProgress = false
        }
    }

    /** Ręczna aktualizacja bazy marek. */
    fun updateBrandsNow() {
        if (dbCheckInProgress) return
        dbCheckInProgress = true
        dbCheckMessage = null
        viewModelScope.launch {
            dbCheckMessage = when (val result = repository.checkForUpdate(database)) {
                is BrandUpdate.Updated -> {
                    database = result.database
                    matcher = BrandMatcher(result.database.brands)
                    "Zaktualizowano do wersji ${result.database.version} (${result.database.brands.size} marek)"
                }
                is BrandUpdate.UpToDate -> "Baza jest aktualna (wersja na GitHubie: ${result.remoteVersion})"
                is BrandUpdate.Failed -> "Nie udało się pobrać: ${result.reason}"
            }
            dbCheckInProgress = false
        }
    }

    fun dismissUpdate() {
        downloadJob?.cancel()
        availableUpdate = null
        downloadState = null
    }

    fun downloadAndInstall() {
        val update = availableUpdate ?: return
        val done = downloadState as? DownloadState.Done
        if (done != null) {
            install(done)
            return
        }
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            updater.download(update) { state ->
                downloadState = state
                if (state is DownloadState.Done) install(state)
            }
        }
    }

    private fun install(done: DownloadState.Done) {
        if (updater.canInstall()) {
            updater.launchInstaller(done.file)
        } else {
            // Użytkownik musi raz zezwolić na instalację z tej aplikacji, potem ponownie kliknąć "Zainstaluj".
            updater.openInstallPermissionSettings()
        }
    }

    override fun onCleared() {
        ocr.close()
    }
}
