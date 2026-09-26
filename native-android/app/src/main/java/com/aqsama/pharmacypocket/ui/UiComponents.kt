package com.aqsama.pharmacypocket.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun colorFromHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Color(0xFF758790))

@Composable
fun tintCategoryColor(hex: String, strength: Float = 0.08f): Color {
    val amount = if (LocalPharmacyDarkTheme.current) {
        (strength * 1.7f).coerceIn(0f, 0.24f)
    } else {
        strength.coerceIn(0f, 1f)
    }
    return lerp(MaterialTheme.colorScheme.surface, colorFromHex(hex), amount)
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

/** Displays a selectable category or filter chip with an optional color marker. */
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

/** Displays a medicine with centered details and separate edit and favorite actions. */
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
    onCamera: () -> Unit,
    loadPhoto: suspend (String) -> ByteArray?,
    photoVersion: Int,
) {
    var thumbnail by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.id, item.hasPhoto, photoVersion) {
        thumbnail = if (item.hasPhoto) {
            loadPhoto(item.id)?.let { bytes ->
                withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
        } else null
    }
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
        border = BorderStroke(0.5.dp, colorFromHex(category.color).copy(alpha = if (LocalPharmacyDarkTheme.current) 0.34f else 0.20f)),
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
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text("✎", fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            color = MaterialTheme.colorScheme.onSurface,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                maxLines = 2,
                                textAlign = TextAlign.Start,
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
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Text(
                            if (item.favorite) "★" else "☆",
                            fontSize = 21.sp,
                            color = if (item.favorite) {
                                if (LocalPharmacyDarkTheme.current) Color(0xFFFFD166) else Color(0xFFA87311)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }

                thumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Package photo of ${item.name}",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp),
                        contentScale = ContentScale.Fit,
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("OFFICIAL", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                formatPrice(item.official),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = if (large) 28.sp else 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                            )
                            Text(currency, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    item.discounted?.let { price ->
                        Column(Modifier.weight(0.8f)) {
                            Text(
                                if (price > item.official) "VERIFY" else "IF ASKED",
                                color = if (price > item.official) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                formatPrice(price),
                                color = if (LocalPharmacyDarkTheme.current) Color(0xFFE1B86C) else Color(0xFFA66C14),
                                fontSize = if (large) 26.sp else 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${category.label}  •  Added ${formatAddedDate(item.createdAt)}",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                    if (!item.hasPhoto || item.codes.isEmpty()) {
                        TextButton(
                            onClick = onCamera,
                            modifier = Modifier.size(48.dp).semantics {
                                contentDescription = "Add photo or code to ${item.name}"
                            },
                            contentPadding = PaddingValues(0.dp),
                        ) { Text("📷", fontSize = 20.sp) }
                    }
                }
            }
        }
    }
