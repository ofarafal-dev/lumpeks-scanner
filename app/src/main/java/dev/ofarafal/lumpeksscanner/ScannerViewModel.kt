package dev.ofarafal.lumpeksscanner

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.ofarafal.lumpeksscanner.data.Brand
import dev.ofarafal.lumpeksscanner.data.BrandDatabase
import dev.ofarafal.lumpeksscanner.data.BrandRepository
import dev.ofarafal.lumpeksscanner.match.BrandMatcher
import dev.ofarafal.lumpeksscanner.match.LabelAnalyzer
import dev.ofarafal.lumpeksscanner.match.LabelInfo
import dev.ofarafal.lumpeksscanner.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanResult(
    val brand: Brand?,
    val matchedAlias: String?,
    val manual: Boolean,
    val label: LabelInfo,
    val rawText: String,
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

    var database: BrandDatabase by mutableStateOf(repository.loadBest())
        private set
    private var matcher = BrandMatcher(database.brands)

    var state: UiState by mutableStateOf(UiState.Camera)
        private set

    /** Komunikat o aktualizacji bazy (pokazywany jako snackbar). */
    var updateMessage: String? by mutableStateOf(null)
        private set

    init {
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
                val (match, label) = withContext(Dispatchers.Default) {
                    matcher.match(text) to LabelAnalyzer.analyze(text)
                }
                UiState.Result(
                    ScanResult(
                        brand = match?.brand,
                        matchedAlias = match?.alias,
                        manual = false,
                        label = label,
                        rawText = text,
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

    override fun onCleared() {
        ocr.close()
    }
}
