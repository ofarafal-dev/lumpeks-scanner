package dev.ofarafal.lumpeksscanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
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
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(16.dp))
                    .padding(20.dp),
            ) {
                Text(verdict, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    brand?.name ?: "Nie rozpoznano marki",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                )
                if (brand != null && brand.country.isNotBlank()) {
                    Text("Pochodzenie marki: ${brand.country}", color = Color.White)
                }
            }
            if (brand?.counterfeitWarning == true) {
                CounterfeitBadge(Modifier.fillMaxHeight())
            }
        }

        if (brand != null) {
            Text(brand.note, style = MaterialTheme.typography.bodyLarge)
            brand.tip?.let { tip ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
                    Text(
                        "💡 $tip",
                        modifier = Modifier.padding(12.dp),
                        color = Color(0xFF0D2A4A),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        when {
            scan.manual -> Text("Marka wybrana ręcznie", style = MaterialTheme.typography.labelMedium)
            scan.matchedAlias != null -> Text("Dopasowano po: „${scan.matchedAlias}”", style = MaterialTheme.typography.labelMedium)
        }

        if (brand?.counterfeitWarning == true) {
            Text(
                "Marka często podrabiana: sprawdź szwy, metki wewnętrzne, okucia i guziki, kody/hologramy " +
                    "oraz czy kraj produkcji pasuje do marki.",
                style = MaterialTheme.typography.bodySmall,
            )
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

        OcrDebugPanel(scan = scan, expanded = showRaw, onToggle = { showRaw = !showRaw })
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
private fun CounterfeitBadge(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(96.dp)
            .background(Color(0xFFFFE0B2), RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xFFEF6C00), RoundedCornerShape(16.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("⚠️", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Sprawdź autentyczność",
            color = Color(0xFF4E2600),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

/** Rozwijany panel do strojenia: surowy tekst OCR + wszystkie dopasowane aliasy z odległością. */
@Composable
private fun OcrDebugPanel(scan: ScanResult, expanded: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onToggle) {
                Text(if (expanded) "▲ Ukryj odczytany tekst" else "▼ Pokaż odczytany tekst")
            }
            if (expanded) {
                Text("Dopasowania aliasów", style = MaterialTheme.typography.titleSmall)
                if (scan.candidates.isEmpty()) {
                    Text("Żaden alias nie przeszedł progu.", style = MaterialTheme.typography.bodySmall)
                } else {
                    scan.candidates.forEachIndexed { i, c ->
                        Text(
                            "${i + 1}. ${c.brand.name} ← alias „${c.alias}” w tekście „${c.matchedText}” " +
                                "(błędy ${c.distance}/${c.tolerance}, wynik ${c.score.toInt()})",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                HorizontalDivider()
                Text("Surowy tekst OCR", style = MaterialTheme.typography.titleSmall)
                SelectionContainer {
                    Text(
                        scan.rawText.ifBlank { "(pusto)" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
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
