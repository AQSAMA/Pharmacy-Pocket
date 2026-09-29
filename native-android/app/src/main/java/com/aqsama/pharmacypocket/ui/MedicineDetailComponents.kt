package com.aqsama.pharmacypocket.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqsama.pharmacypocket.data.*

@Composable
internal fun MedicineDetailSummary(item: Medicine, currency: String, large: Boolean, onFavorite: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row {
            Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge.copy(textDirection = TextDirection.Content))
            PocketIconButton(if (item.favorite) PocketIcon.FAVORITE_FILLED else PocketIcon.FAVORITE,
                if (item.favorite) "Remove from favorites" else "Add to favorites", onFavorite)
        }
        if (item.note.isNotBlank()) SelectionContainer {
            Text(item.note, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Official price · $currency", style = MaterialTheme.typography.labelLarge)
                Text(formatPrice(item.official), style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = if (large) 38.sp else 32.sp, lineHeight = if (large) 44.sp else 38.sp,
                    textDirection = TextDirection.Ltr), fontWeight = FontWeight.Bold)
                item.discounted?.let { price ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 6.dp))
                    Text(if (price > item.official) "Check discounted price" else "Discounted price", style = MaterialTheme.typography.labelLarge,
                        color = if (price > item.official) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("${formatPrice(price)} $currency", style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Ltr))
                    discountPercent(item)?.let { percent ->
                        Text("Save ${formatPrice(item.official - price)} $currency · $percent%", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CopyableMedicineCode(code: MedicineCode, large: Boolean) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(when (code.kind) {
            CodeKind.BARCODE -> "Barcode"; CodeKind.QR -> "QR"; CodeKind.PRICE_STICKER_QR -> "Price sticker QR"
        }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (code.label.isNotBlank()) Text(code.label, style = MaterialTheme.typography.titleMedium)
        SelectionContainer {
            Text(code.value, style = (if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium).copy(textDirection = TextDirection.Ltr))
        }
        TextButton(onClick = {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Medicine code", code.value))
            Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
        }) { PocketIcon(PocketIcon.COPY); Text("Copy code", modifier = Modifier.padding(start = 8.dp)) }
    }
}

@Composable
internal fun MedicineShareButton(item: Medicine, category: Category, currency: String) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val text = buildString {
            appendLine(item.name)
            appendLine("${category.label} / ${subcategoryLabel(item.subcategory)}")
            if (item.note.isNotBlank()) appendLine(item.note)
            appendLine("Official price: ${formatPrice(item.official)} $currency")
            item.discounted?.let { appendLine("Discounted price: ${formatPrice(it)} $currency") }
            if (item.description.isNotBlank()) { appendLine(); appendLine(item.description) }
        }.trim()
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }, "Share medicine"))
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Share medicine details") }
}
