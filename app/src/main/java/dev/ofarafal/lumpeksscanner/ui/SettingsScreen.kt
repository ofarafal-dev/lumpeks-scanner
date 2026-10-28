package dev.ofarafal.lumpeksscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ofarafal.lumpeksscanner.BuildConfig
import dev.ofarafal.lumpeksscanner.ScannerViewModel

@Composable
fun SettingsScreen(vm: ScannerViewModel, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Wróć") }
            Text("Ustawienia", style = MaterialTheme.typography.headlineSmall)
        }

        SettingsCard(title = "Aplikacja") {
            Text("Wersja ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            ActionRow(
                label = "Sprawdź aktualizację",
                busy = vm.appCheckInProgress,
                onClick = vm::checkAppUpdateNow,
            )
            vm.appCheckMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Sprawdzaj przy starcie")
                    Text(
                        "Automatycznie szuka nowej wersji po uruchomieniu",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = vm.autoCheckUpdates, onCheckedChange = vm::setAutoCheck)
            }
        }

        SettingsCard(title = "Baza marek") {
            val db = vm.database
            Text("Wersja ${db.version} · ${db.updated} · ${db.brands.size} marek")
            ActionRow(
                label = "Aktualizuj bazę teraz",
                busy = vm.dbCheckInProgress,
                onClick = vm::updateBrandsNow,
            )
            vm.dbCheckMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text(
                "Baza aktualizuje się też sama przy każdym starcie, jeśli jest internet.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ActionRow(label: String, busy: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onClick, enabled = !busy) { Text(label) }
        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}
