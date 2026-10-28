package dev.ofarafal.lumpeksscanner.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.ofarafal.lumpeksscanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(
    val build: Int,
    val versionName: String,
    val apkUrl: String,
    val pageUrl: String,
)

sealed interface DownloadState {
    data class Running(val downloadedBytes: Long, val totalBytes: Long) : DownloadState
    data class Done(val file: File) : DownloadState
    data class Failed(val reason: String) : DownloadState
}

/**
 * Aktualizacja aplikacji z GitHub Releases:
 * releases/latest → tag "build-N" → jeśli N > versionCode, pobierz lumpeks-scanner.apk przez
 * DownloadManager i otwórz systemowy instalator (użytkownik zatwierdza instalację sam).
 */
class AppUpdater(private val context: Context) {

    /** Nowsza wersja albo null (brak sieci, błąd API, brak nowszej wersji — bez komunikatu). */
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(BuildConfig.RELEASES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "lumpeks-scanner/${BuildConfig.VERSION_NAME}")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val build = json.optString("tag_name").removePrefix("build-").toIntOrNull()
                    ?: return@runCatching null
                if (build <= BuildConfig.VERSION_CODE) return@runCatching null
                val assets = json.optJSONArray("assets") ?: return@runCatching null
                val apkUrl = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name") == APK_NAME }
                    ?.optString("browser_download_url")
                    ?.takeIf { it.isNotBlank() }
                    ?: return@runCatching null
                AppUpdate(
                    build = build,
                    versionName = "1.0.$build",
                    apkUrl = apkUrl,
                    pageUrl = json.optString("html_url"),
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    /** Pobiera APK przez DownloadManager i emituje postęp do [onProgress]. */
    suspend fun download(update: AppUpdate, onProgress: (DownloadState) -> Unit) {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir == null) {
            onProgress(DownloadState.Failed("Brak dostępu do pamięci"))
            return
        }
        dir.listFiles { f -> f.name.endsWith(".apk") }?.forEach { it.delete() }
        val fileName = "lumpeks-scanner-${update.build}.apk"
        val target = File(dir, fileName)

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Lumpeks Scanner ${update.versionName}")
            .setDescription("Pobieranie aktualizacji")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
        val id = manager.enqueue(request)

        while (true) {
            val state = withContext(Dispatchers.IO) { query(manager, id, target) }
            onProgress(state)
            if (state !is DownloadState.Running) return
            delay(500)
        }
    }

    private fun query(manager: DownloadManager, id: Long, target: File): DownloadState {
        manager.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return DownloadState.Failed("Pobieranie anulowane")
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Done(target)
                DownloadManager.STATUS_FAILED -> {
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    DownloadState.Failed("Błąd pobierania (kod $reason)")
                }
                else -> DownloadState.Running(done, total)
            }
        }
    }

    /** Czy aplikacja ma zgodę na instalowanie APK (Ustawienia → Instalowanie nieznanych aplikacji). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Otwiera systemowy instalator; użytkownik sam zatwierdza instalację. */
    fun launchInstaller(apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private companion object {
        const val APK_NAME = "lumpeks-scanner.apk"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val TIMEOUT_MS = 5_000
    }
}
