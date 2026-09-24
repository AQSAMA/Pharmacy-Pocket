package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqsama.pharmacypocket.data.Category
import com.aqsama.pharmacypocket.data.Medicine
import com.aqsama.pharmacypocket.data.formatAddedDate
import com.aqsama.pharmacypocket.data.formatPrice
import com.aqsama.pharmacypocket.data.hasArabic

fun colorFromHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF758790))

fun tintCategoryColor(hex: String, strength: Float = 0.08f): Color {
    val base = colorFromHex(hex)
    val amount = strength.coerceIn(0f, 1f)
    return Color(
        red = 1f - (1f - base.red) * amount,
        green = 1f - (1f - base.green) * amount,
        blue = 1f - (1f - base.blue) * amount,
        alpha = 1f,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            TextButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Text("‹", fontSize = 30.sp, color = MaterialTheme.colorScheme.secondary)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun SoftChip(
    label: String,
    selected: Boolean = false,
    accent: Color? = null,
    onClick: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = background,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
    ) {
        Row(
            Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (accent != null) {
                Surface(shape = RoundedCornerShape(50), color = accent, modifier = Modifier.size(9.dp)) {}
            }
            Text(label, color = foreground, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold)
        }
    }
}

@Composable
fun MedicineCard(
    item: Medicine,
    category: Category,
    large: Boolean,
    currency: String,
    first: Boolean,
    last: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = if (first) 18.dp else 0.dp,
        topEnd = if (first) 18.dp else 0.dp,
        bottomStart = if (last) 18.dp else 0.dp,
        bottomEnd = if (last) 18.dp else 0.dp,
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onOpen),
        color = tintCategoryColor(category.color, 0.09f),
        shape = shape,
        border = BorderStroke(0.5.dp, colorFromHex(category.color).copy(alpha = 0.20f)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        color = colorFromHex(category.color),
                        size = Size(4.dp.toPx(), size.height),
                    )
                }
                .padding(horizontal = 13.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Edit ${item.name}" },
                    ) {
                        Text("✎", fontSize = 19.sp, color = Color(0xFF55746A))
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = item.name,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            color = Color(0xFF173C30),
                            fontSize = if (large) 27.sp else 20.sp,
                            lineHeight = if (large) 36.sp else 27.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = TextStyle(textDirection = TextDirection.Content),
                        )
                        if (!large && item.note.isNotBlank()) {
                            Text(
                                text = item.note,
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFF71827A),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                maxLines = 2,
                                textAlign = if (hasArabic(item.note)) TextAlign.End else TextAlign.Start,
                                style = TextStyle(textDirection = TextDirection.Content),
                            )
                        }
                    }
                    TextButton(
                        onClick = onFavorite,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = if (item.favorite) {
                                    "Remove ${item.name} from favorites"
                                } else {
                                    "Add ${item.name} to favorites"
                                }
                            },
                    ) {
                        Text(
                            if (item.favorite) "★" else "☆",
                            fontSize = 21.sp,
                            color = if (item.favorite) Color(0xFFA87311) else Color(0xFF70867E),
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("OFFICIAL", color = Color(0xFF81928B), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                formatPrice(item.official),
                                color = Color(0xFF1C7352),
                                fontSize = if (large) 28.sp else 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                            )
                            Text(currency, color = Color(0xFF81928B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    item.discounted?.let { price ->
                        Column(Modifier.weight(0.8f)) {
                            Text(
                                if (price > item.official) "VERIFY" else "IF ASKED",
                                color = if (price > item.official) Color(0xFFA94E36) else Color(0xFF81928B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                formatPrice(price),
                                color = Color(0xFFA66C14),
                                fontSize = if (large) 26.sp else 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Text(
                    "${category.label}  •  Added ${formatAddedDate(item.createdAt)}",
                    color = Color(0xFF687C74),
                    fontSize = 12.sp,
                )
            }
        }
    }
