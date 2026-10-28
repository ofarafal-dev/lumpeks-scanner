package dev.ofarafal.lumpeksscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ofarafal.lumpeksscanner.BuildConfig
import dev.ofarafal.lumpeksscanner.update.AppUpdate
import dev.ofarafal.lumpeksscanner.update.DownloadState

@Composable
fun UpdateDialog(
    update: AppUpdate,
    state: DownloadState?,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirmLabel = when (state) {
        null -> "Pobierz i zainstaluj"
        is DownloadState.Running -> "Pobieranie…"
        is DownloadState.Done -> "Zainstaluj"
        is DownloadState.Failed -> "Spróbuj ponownie"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dostępna nowa wersja") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zainstalowana: ${BuildConfig.VERSION_NAME}\nNowa: ${update.versionName}")
                when (state) {
                    is DownloadState.Running -> {
                        if (state.totalBytes > 0) {
                            LinearProgressIndicator(
                                progress = { state.downloadedBytes.toFloat() / state.totalBytes },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("${state.downloadedBytes / 1_048_576} / ${state.totalBytes / 1_048_576} MB")
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    is DownloadState.Done ->
                        Text("Pobrano. Zatwierdź instalację w oknie systemowym. Jeśli Android poprosi o zgodę na instalowanie aplikacji, włącz ją i naciśnij „Zainstaluj” jeszcze raz.")
                    is DownloadState.Failed -> Text(state.reason)
                    null -> Text("Aplikacja pobierze plik APK z GitHub Releases i otworzy instalator.")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload, enabled = state !is DownloadState.Running) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Później") }
        },
    )
}
