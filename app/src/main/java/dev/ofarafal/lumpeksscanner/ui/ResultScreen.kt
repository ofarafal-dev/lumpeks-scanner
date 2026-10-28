package dev.ofarafal.lumpeksscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ofarafal.lumpeksscanner.ScanResult
import dev.ofarafal.lumpeksscanner.data.Brand
import dev.ofarafal.lumpeksscanner.data.Rating
import dev.ofarafal.lumpeksscanner.match.CompositionVerdict
import dev.ofarafal.lumpeksscanner.match.TextNorm

@Composable
fun ResultScreen(
    scan: ScanResult,
    allBrands: List<Brand>,
    onCorrectBrand: (Brand) -> Unit,
    onScanAgain: () -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }
    val brand = scan.brand

    val (color, verdict) = when (brand?.rating) {
        Rating.GREEN -> Color(0xFF2E7D32) to "🟢 Bierz"
        Rating.YELLOW -> Color(0xFFF9A825) to "🟡 Zależy od stanu"
        Rating.RED -> Color(0xFFC62828) to "🔴 Zostaw"
        null -> Color(0xFF616161) to "⚪ Nieznana marka"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text(verdict, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                brand?.name ?: "Nie rozpoznano marki",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            if (brand != null && brand.country.isNotBlank()) {
                Text("Pochodzenie marki: ${brand.country}", color = Color.White)
            }
        }

        if (brand != null) {
            Text(brand.note, style = MaterialTheme.typography.bodyLarge)
        }
        when {
            scan.manual -> Text("Marka wybrana ręcznie", style = MaterialTheme.typography.labelMedium)
            scan.matchedAlias != null -> Text("Dopasowano po: „${scan.matchedAlias}”", style = MaterialTheme.typography.labelMedium)
        }

        if (brand?.counterfeitWarning == true) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2))) {
                Text(
                    "⚠️ Marka często podrabiana. Sprawdź szwy, metki wewnętrzne, jakość okuć i guzików, " +
                        "kody/hologramy oraz czy cena i miejsce produkcji pasują do marki.",
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFF4E2600),
                )
            }
        }

        HorizontalDivider()
        Text("Skład", style = MaterialTheme.typography.titleMedium)
        val composition = when (scan.label.verdict) {
            CompositionVerdict.PLUS -> "➕ Szlachetne włókna — plus"
            CompositionVerdict.MINUS -> "➖ Sam syntetyk (poliester/akryl) — minus"
            CompositionVerdict.NEUTRAL -> "Skład bez szczególnych plusów i minusów"
            CompositionVerdict.UNKNOWN -> "Nie wykryto składu — zrób zdjęcie metki ze składem"
        }
        Text(composition, fontWeight = FontWeight.SemiBold)
        scan.label.fibers.forEach { hit ->
            Text("• " + (hit.percent?.let { "$it% " } ?: "") + hit.fiber.displayName)
        }

        HorizontalDivider()
        Text("Kraj produkcji", style = MaterialTheme.typography.titleMedium)
        Text(scan.label.country ?: "Nie wykryto")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showPicker = true }) { Text("Popraw markę") }
            Button(onClick = onScanAgain) { Text("Skanuj dalej") }
        }

        TextButton(onClick = { showRaw = !showRaw }) {
            Text(if (showRaw) "Ukryj odczytany tekst" else "Pokaż odczytany tekst (OCR)")
        }
        if (showRaw) {
            Text(scan.rawText.ifBlank { "(pusto)" }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showPicker) {
        BrandPickerDialog(
            brands = allBrands,
            onDismiss = { showPicker = false },
            onPick = {
                showPicker = false
                onCorrectBrand(it)
            },
        )
    }
}

@Composable
private fun BrandPickerDialog(
    brands: List<Brand>,
    onDismiss: () -> Unit,
    onPick: (Brand) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val sorted = remember(brands) { brands.sortedBy { it.name.lowercase() } }
    val q = TextNorm.normalize(query)
    val filtered = if (q.isEmpty()) {
        sorted
    } else {
        sorted.filter { b -> (b.aliases + b.name).any { TextNorm.normalize(it).contains(q) } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
        title = { Text("Wybierz markę") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Szukaj") },
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.name }) { b ->
                        Text(
                            text = b.name + "  " + when (b.rating) {
                                Rating.GREEN -> "🟢"
                                Rating.YELLOW -> "🟡"
                                Rating.RED -> "🔴"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(b) }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        },
    )
}
