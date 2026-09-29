package com.aqsama.pharmacypocket.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
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

/** A medicine card whose photo is part of the composition rather than a thumbnail. */
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
    var photo by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.id, item.hasPhoto, photoVersion) {
        photo = if (item.hasPhoto) {
            loadPhoto(item.id)?.let { bytes ->
                withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }
        } else {
            null
        }
    }

    val shape = RoundedCornerShape(
        topStart = if (first) 20.dp else 0.dp,
        topEnd = if (first) 20.dp else 0.dp,
        bottomStart = if (last) 20.dp else 0.dp,
        bottomEnd = if (last) 20.dp else 0.dp,
    )
    val accent = colorFromHex(category.color)
    val originalDirection = LocalLayoutDirection.current
    val railWidth = if (large) 124.dp else 112.dp
    val minCardHeight = if (large) 204.dp else 176.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onOpen),
        color = tintCategoryColor(category.color, 0.075f),
        shape = shape,
        border = BorderStroke(
            0.5.dp,
            accent.copy(alpha = if (LocalPharmacyDarkTheme.current) 0.32f else 0.18f),
        ),
        tonalElevation = 1.dp,
    ) {
        // The media rail is intentionally physical-left, matching package imagery in the
        // visual baseline. Restore the user's layout direction for all textual content.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .defaultMinSize(minHeight = minCardHeight)
                    .then(
                        if (photo == null) {
                            Modifier.drawBehind {
                                drawRect(
                                    color = accent,
                                    size = Size(4.dp.toPx(), size.height),
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                photo?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Package photo of ${item.name}",
                        modifier = Modifier
                            .width(railWidth)
                            .fillMaxHeight(),
                        contentScale = ContentScale.Crop,
                    )
                }

                CompositionLocalProvider(LocalLayoutDirection provides originalDirection) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Text(
                                    text = item.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = if (large) 3 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = if (large) 25.sp else 20.sp,
                                    lineHeight = if (large) 33.sp else 27.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Start,
                                    style = TextStyle(textDirection = TextDirection.Content),
                                )
                                if (!large && item.note.isNotBlank()) {
                                    Text(
                                        text = item.note,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        lineHeight = 17.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Start,
                                        style = TextStyle(textDirection = TextDirection.Content),
                                    )
                                }
                            }

                            TextButton(
                                onClick = onFavorite,
                                modifier = Modifier
                                    .size(44.dp)
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

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            MedicineCardPrice(
                                label = "OFFICIAL",
                                price = item.official,
                                currency = currency,
                                large = large,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            item.discounted?.let { price ->
                                MedicineCardPrice(
                                    label = if (price > item.official) "VERIFY" else "IF ASKED",
                                    price = price,
                                    currency = null,
                                    large = large,
                                    color = if (price > item.official) {
                                        MaterialTheme.colorScheme.error
                                    } else if (LocalPharmacyDarkTheme.current) {
                                        Color(0xFFE1B86C)
                                    } else {
                                        Color(0xFFA66C14)
                                    },
                                    modifier = Modifier.weight(0.82f),
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "${category.label}  •  Added ${formatAddedDate(item.createdAt)}",
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(textDirection = TextDirection.Content),
                            )
                            TextButton(
                                onClick = onEdit,
                                modifier = Modifier
                                    .size(44.dp)
                                    .semantics { contentDescription = "Edit ${item.name}" },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text("✎", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(
                                onClick = onCamera,
                                modifier = Modifier
                                    .size(44.dp)
                                    .semantics { contentDescription = "Add or replace photo or code for ${item.name}" },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text("📷", fontSize = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicineCardPrice(
    label: String,
    price: Long,
    currency: String?,
    large: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                formatPrice(price),
                color = color,
                fontSize = if (large) 26.sp else 21.sp,
                lineHeight = if (large) 30.sp else 25.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            currency?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}
