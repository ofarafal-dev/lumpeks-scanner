package dev.ofarafal.lumpeksscanner.data

import android.content.Context
import dev.ofarafal.lumpeksscanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Baza marek:
 *  1. wersja startowa w assets/brands.json (wbudowana w APK, z data/brands.json w repo),
 *  2. ostatnio pobrana wersja w filesDir/brands.json,
 *  3. przy starcie aplikacja próbuje pobrać nowszą wersję z GitHuba.
 * Używana jest zawsze wersja z najwyższym polem "version".
 */
sealed interface BrandUpdate {
    data class Updated(val database: BrandDatabase) : BrandUpdate
    data class UpToDate(val remoteVersion: Int) : BrandUpdate
    data class Failed(val reason: String) : BrandUpdate
}

class BrandRepository(private val context: Context) {

    private val localFile = File(context.filesDir, LOCAL_NAME)

    fun loadBest(): BrandDatabase {
        val bundled = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            .let { BrandDatabase.parse(it) }
        val cached = runCatching {
            if (localFile.exists()) BrandDatabase.parse(localFile.readText()) else null
        }.getOrNull()
        return if (cached != null && cached.version > bundled.version && cached.brands.isNotEmpty()) {
            cached
        } else {
            bundled
        }
    }

    /** Zwraca nową bazę, jeśli zdalna wersja jest nowsza; null przy braku sieci lub braku zmian. */
    suspend fun fetchUpdate(current: BrandDatabase): BrandDatabase? =
        (checkForUpdate(current) as? BrandUpdate.Updated)?.database

    /** Pełny wynik sprawdzenia — używany przy ręcznej aktualizacji z ekranu ustawień. */
    suspend fun checkForUpdate(current: BrandDatabase): BrandUpdate = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(BuildConfig.BRANDS_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    BrandUpdate.Failed("Serwer zwrócił HTTP ${connection.responseCode}")
                } else {
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    val remote = BrandDatabase.parse(text)
                    if (remote.version > current.version && remote.brands.isNotEmpty()) {
                        val tmp = File(context.filesDir, "$LOCAL_NAME.tmp")
                        tmp.writeText(text)
                        if (!tmp.renameTo(localFile)) {
                            localFile.writeText(text)
                            tmp.delete()
                        }
                        BrandUpdate.Updated(remote)
                    } else {
                        BrandUpdate.UpToDate(remote.version)
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: java.io.IOException) {
            BrandUpdate.Failed("Brak połączenia z internetem")
        } catch (e: Exception) {
            BrandUpdate.Failed("Błąd: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val ASSET_NAME = "brands.json"
        const val LOCAL_NAME = "brands.json"
        const val TIMEOUT_MS = 5_000
    }
}
