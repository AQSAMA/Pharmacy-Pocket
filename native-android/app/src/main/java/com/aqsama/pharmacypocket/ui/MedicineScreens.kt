package com.aqsama.pharmacypocket.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqsama.pharmacypocket.data.AppSnapshot
import com.aqsama.pharmacypocket.data.Medicine
import com.aqsama.pharmacypocket.data.MedicineCode
import com.aqsama.pharmacypocket.data.CodeKind
import com.aqsama.pharmacypocket.data.validateCodes
import com.aqsama.pharmacypocket.data.buildSearchIndex
import com.aqsama.pharmacypocket.data.categoryById
import com.aqsama.pharmacypocket.data.formatAddedDate
import com.aqsama.pharmacypocket.data.formatPrice
import com.aqsama.pharmacypocket.data.listSubcategories
import com.aqsama.pharmacypocket.data.subcategoryKey
import com.aqsama.pharmacypocket.data.subcategoryLabel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

@Composable
internal fun MedicineEditorScreen(
    snapshot: AppSnapshot,
    medicineId: String?,
    initialCategory: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onSave: (Medicine) -> Unit,
    onMoveToTrash: (Medicine) -> Unit,
    loadPhoto: suspend (String) -> ByteArray?,
    initialCapture: String? = null,
    media: MedicineMedia,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existing = remember(snapshot.items, medicineId) { snapshot.items.firstOrNull { it.id == medicineId } }
    val fallbackCategory = initialCategory
        ?.takeIf { candidate -> candidate != "all" && snapshot.categories.any { it.id == candidate } }
        ?: snapshot.categories.firstOrNull { it.id != "all" }?.id
        ?: "syrups"

    val editorMedicineId = rememberSaveable(medicineId) { medicineId ?: "med-${UUID.randomUUID()}" }
    val editorCreatedAt = rememberSaveable(medicineId) { existing?.createdAt ?: System.currentTimeMillis() }
    var name by rememberSaveable(medicineId) { mutableStateOf(existing?.name ?: "") }
    var category by rememberSaveable(medicineId) { mutableStateOf(existing?.category ?: fallbackCategory) }
    var subcategory by rememberSaveable(medicineId) { mutableStateOf(subcategoryLabel(existing?.subcategory)) }
    var official by rememberSaveable(medicineId) { mutableStateOf(existing?.official?.toString() ?: "") }
    var discounted by rememberSaveable(medicineId) { mutableStateOf(existing?.discounted?.toString() ?: "") }
    var note by rememberSaveable(medicineId) { mutableStateOf(existing?.note ?: "") }
    var description by rememberSaveable(medicineId) { mutableStateOf(existing?.description ?: "") }
    val codes = media.codes
    var codeDraft by rememberSaveable(medicineId) { mutableStateOf("") }
    var codeKind by rememberSaveable(medicineId) { mutableStateOf(CodeKind.BARCODE) }
    var savedPhoto by remember(medicineId) { mutableStateOf<ByteArray?>(null) }
    val draftPhoto = media.preview
    val draftPhotoPath = media.photoPath
    val draftLoading = draftPhotoPath != null && draftPhoto == null && media.error == null
    val photoProcessing = media.locked
    val removePhoto = media.removePhoto
    val gallerySource = media.cropSource?.let(::File)
    var initialCaptureStarted by rememberSaveable { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var confirmTrash by remember { mutableStateOf(false) }
    var mediaExpanded by rememberSaveable(medicineId) { mutableStateOf(media.photoPath != null || existing?.hasPhoto == true) }
    var detailsExpanded by rememberSaveable(medicineId) { mutableStateOf(false) }
    var manualCodeExpanded by rememberSaveable(medicineId) { mutableStateOf(false) }
    var revealPhotoAfterLoad by remember { mutableStateOf(false) }
    val photoBringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(existing?.id, existing?.hasPhoto) {
        savedPhoto = if (existing?.hasPhoto == true) loadPhoto(existing.id) else null
    }
    LaunchedEffect(media, draftPhotoPath) { media.restorePreview() }
    LaunchedEffect(draftPhoto, mediaExpanded, revealPhotoAfterLoad) {
        if (draftPhoto != null && mediaExpanded && revealPhotoAfterLoad) {
            photoBringIntoViewRequester.bringIntoView()
            revealPhotoAfterLoad = false
        }
    }

    fun acceptPhoto(uri: Uri) {
        scope.launch {
            try {
                // Copy the picker result while its URI grant is available. The shared
                // crop component then submits the same final JPEG as the camera.
                media.pickPhoto(context, uri)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                validationError = error.message ?: "Could not open this photo."
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) acceptPhoto(uri)
    }
    fun launchGallery() {
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    fun openCamera() { media.openCamera() }
    LaunchedEffect(initialCapture) {
        if (!initialCaptureStarted) {
            initialCaptureStarted = true
            when (initialCapture) {
                "barcode", "sticker", "photo" -> openCamera()
                "gallery" -> launchGallery()
            }
        }
    }

    fun proposeCode(value: String, kind: CodeKind, label: String = ""): MediaSaveResult {
        if (busy || media.locked) return MediaSaveResult(false, "Finish the current media operation first")
        val proposed = runCatching { validateCodes(listOf(MedicineCode(kind, value, label))).single() }
            .getOrElse { validationError = it.message; return MediaSaveResult(false, it.message ?: "Invalid code") }
        val owner = snapshot.items.firstOrNull { it.id != existing?.id && it.codes.any { code -> code.value == proposed.value } }
        return when {
            codes.any { it.value == proposed.value } -> MediaSaveResult(true, "Code already added")
            codes.size >= 20 -> MediaSaveResult(false, "A medicine can have up to 20 codes")
            owner != null -> MediaSaveResult(false, "This code belongs to ${owner.name}")
            else -> {
                media.updateCodes(media.codes + proposed)
                codeDraft = ""
                MediaSaveResult(true, "Added ${if (kind == CodeKind.BARCODE) "barcode" else "QR"} to medicine draft")
            }
        }
    }

    val existingSubcategories = remember(snapshot.items, category) {
        listSubcategories(buildSearchIndex(snapshot.items), category)
    }

    fun submit() {
        val officialNumber = official.trim().toLongOrNull()
        val discountedNumber = discounted.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
        val discountWasInvalid = discounted.trim().isNotEmpty() && discountedNumber == null
        if (
            name.trim().isEmpty() ||
            officialNumber == null ||
            officialNumber !in 0..MAX_SAFE_INTEGER ||
            discountWasInvalid ||
            (discountedNumber != null && discountedNumber !in 0..MAX_SAFE_INTEGER)
        ) {
            Haptics.reject(view)
            validationError = "Enter a medicine name and whole-number ${snapshot.currency} prices."
            return
        }
        Haptics.action(view)
        onSave(
            Medicine(
                id = editorMedicineId,
                name = name.trim(),
                category = category,
                subcategory = subcategoryLabel(subcategory),
                official = officialNumber,
                discounted = discountedNumber,
                note = note.trim(),
                description = description.trim(),
                revision = existing?.revision ?: 0,
                favorite = existing?.favorite ?: false,
                createdAt = editorCreatedAt,
                codes = codes,
            ),
        )
    }

    Scaffold(
        topBar = { ScreenTopBar(if (existing == null) "Add medicine" else "Edit medicine", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Button(
                    enabled = !busy && !photoProcessing && !draftLoading && (draftPhotoPath == null || draftPhoto != null),
                    onClick = ::submit,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 8.dp).heightIn(min = 54.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
                ) { Text(if (busy) "Saving…" else "Save medicine", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
            }
        },
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(
                    Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Field("Medicine / brand", name, { name = it })

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Category", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            TextButton(onClick = {
                                Haptics.action(view)
                                onManageCategories()
                            }) { Text("Manage categories") }
                        }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            snapshot.categories.filter { it.id != "all" }.forEach { item ->
                                SoftChip(
                                    label = item.arabic,
                                    selected = category == item.id,
                                    accent = colorFromHex(item.color),
                                    onClick = {
                                        Haptics.selection(view)
                                        category = item.id
                                    },
                                )
                            }
                        }
                    }

                    Field("Subcategory", subcategory, { subcategory = it })
                    if (existingSubcategories.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Or select an existing subcategory", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                existingSubcategories.forEach { option ->
                                    SoftChip(
                                        label = option.label,
                                        selected = subcategoryKey(subcategory) == option.key,
                                        onClick = {
                                            Haptics.selection(view)
                                            subcategory = option.label
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        OutlinedTextField(
                            value = official,
                            onValueChange = { official = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Official price") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            value = discounted,
                            onValueChange = { discounted = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("Discounted") },
                            placeholder = { Text("Optional") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    Text(
                        "${snapshot.currency} · Leave Discounted blank if there is no second price.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )

                    EditorSectionButton(
                        icon = "▣",
                        title = "Photo & codes",
                        summary = buildString {
                            append("${codes.size} code")
                            if (codes.size != 1) append("s")
                            append(" · ")
                            append(if (draftPhoto != null || (!removePhoto && existing?.hasPhoto == true)) "Photo added" else "No photo")
                        },
                        expanded = mediaExpanded,
                        onClick = { mediaExpanded = !mediaExpanded },
                    )

                    if (mediaExpanded) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Button(
                                        enabled = !busy && !photoProcessing,
                                        onClick = ::openCamera,
                                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                    ) { Text("Camera", fontWeight = FontWeight.Bold) }

                                    OutlinedButton(
                                        enabled = !busy && !photoProcessing,
                                        onClick = { launchGallery() },
                                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                    ) { Text("Gallery", fontWeight = FontWeight.Bold) }
                                }

                                Text(
                                    "QR sticker data is kept exactly as scanned; price stays manual.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )

                                if (codes.isEmpty()) {
                                    Text(
                                        "No saved codes yet.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                    )
                                } else {
                                    codes.forEach { code ->
                                        Surface(
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        when (code.kind) {
                                                            CodeKind.PRICE_STICKER_QR -> "Sticker QR"
                                                            CodeKind.QR -> "QR"
                                                            CodeKind.BARCODE -> "Barcode"
                                                        },
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                    Text(
                                                        code.value,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        fontSize = 13.sp,
                                                    )
                                                    if (code.label.isNotBlank()) {
                                                        Text(
                                                            code.label,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 12.sp,
                                                        )
                                                    }
                                                }
                                                TextButton(enabled = !busy && !media.locked, onClick = { media.updateCodes(media.codes.filterNot { it == code }) }) {
                                                    Text("Remove")
                                                }
                                            }
                                        }
                                    }
                                }

                                TextButton(
                                    onClick = { manualCodeExpanded = !manualCodeExpanded },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (manualCodeExpanded) "Hide manual entry  ⌃" else "Enter a code manually  ⌄")
                                }

                                if (manualCodeExpanded) {
                                    Field("Code", codeDraft, { codeDraft = it })
                                    Row(
                                        Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                        SoftChip(
                                            label = "Barcode",
                                            selected = codeKind == CodeKind.BARCODE,
                                            onClick = { codeKind = CodeKind.BARCODE },
                                        )
                                        SoftChip(
                                            label = "Sticker QR",
                                            selected = codeKind == CodeKind.PRICE_STICKER_QR,
                                            onClick = { codeKind = CodeKind.PRICE_STICKER_QR },
                                        )
                                        SoftChip(
                                            label = "QR",
                                            selected = codeKind == CodeKind.QR,
                                            onClick = { codeKind = CodeKind.QR },
                                        )
                                    }
                                    Button(
                                        onClick = { proposeCode(codeDraft, codeKind) },
                                        enabled = codeDraft.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(15.dp),
                                    ) { Text("Add code", fontWeight = FontWeight.Bold) }
                                }

                                (draftPhoto ?: if (removePhoto) null else savedPhoto)?.let { bytes ->
                                    val bitmap = remember(bytes) {
                                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                    }
                                    bitmap?.let {
                                        Image(
                                            it,
                                            contentDescription = "Medicine photo",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 220.dp)
                                                .bringIntoViewRequester(photoBringIntoViewRequester),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                    TextButton(
                                        enabled = !busy,
                                        onClick = {
                                            media.remove()
                                        },
                                    ) { Text("Remove photo") }
                                }

                                if (draftPhotoPath != null && draftPhoto == null && !draftLoading) {
                                    Text(
                                        "Photo preview could not be loaded. Choose the photo again.",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                    TextButton(
                                        enabled = !busy,
                                        onClick = {
                                            media.discardUnavailableDraft()
                                        },
                                    ) { Text("Discard unavailable draft") }
                                }

                            }
                        }
                    }

                    EditorSectionButton(
                        icon = "≡",
                        title = "More details",
                        summary = if (note.isNotBlank() || description.isNotBlank()) "Notes added" else "Notes & description",
                        expanded = detailsExpanded,
                        onClick = { detailsExpanded = !detailsExpanded },
                    )

                    if (detailsExpanded) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Field("Supplied note", note, { note = it }, minLines = 3)
                                Field(
                                    "Description",
                                    description,
                                    { description = it },
                                    minLines = 5,
                                    placeholder = "Details shown on the medicine page",
                                )
                                if (existing != null) {
                                    TextButton(
                                        enabled = !busy,
                                        onClick = {
                                            Haptics.action(view)
                                            confirmTrash = true
                                        },
                                    ) {
                                        Text("Move to Trash", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    validationError?.let { error ->
        AlertDialog(
            onDismissRequest = { validationError = null },
            title = { Text("Check the details") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = { validationError = null }) { Text("OK") } },
        )
    }

    media.captureState?.let { captureState -> MedicineCameraScreen(
        state = captureState,
        title = existing?.name ?: "New medicine",
        existingCodes = codes.mapTo(mutableSetOf()) { it.value },
        onCode = { code, acknowledge -> acknowledge(proposeCode(code.value, code.kind, code.label)) },
        onPhoto = { bytes, acknowledge ->
            scope.launch {
                try {
                    media.acceptPhoto(context, bytes)
                    revealPhotoAfterLoad = true
                    mediaExpanded = true
                    acknowledge(MediaSaveResult(true, "Added photo to medicine draft"))
                    media.closeCamera()
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    acknowledge(MediaSaveResult(false, error.message ?: "Could not save photo"))
                }
            }
        },
        onDismiss = media::closeCamera,
    ) }

    gallerySource?.let { source ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (!media.processing) media.cancelCrop() },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                    TextButton(
                        enabled = !media.processing,
                        onClick = { media.cancelCrop() },
                    ) { Text("Cancel") }
                    validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    MedicinePhotoCrop(
                        file = source,
                        saving = media.processing,
                        onAccept = { bytes ->
                            scope.launch {
                                try {
                                    media.acceptPhoto(context, bytes)
                                    mediaExpanded = true
                                    revealPhotoAfterLoad = true
                                } catch (error: Exception) {
                                    if (error is CancellationException) throw error
                                    validationError = error.message ?: "Could not save photo"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (confirmTrash && existing != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmTrash = false },
            title = { Text("Move “${existing.name}” to Trash?") },
            text = { Text("You can restore it later from Settings > Trash.") },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { confirmTrash = false },
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmTrash = false
                        onMoveToTrash(existing)
                    },
                ) {
                    Text(
                        "Move to Trash",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }
}

@Composable
private fun EditorSectionButton(
    icon: String,
    title: String,
    summary: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(icon, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                if (expanded) "⌃" else "⌄",
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    minLines: Int = 1,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { text -> { Text(text) } },
        minLines = minLines,
        maxLines = if (minLines > 1) minLines + 3 else 1,
        singleLine = minLines == 1,
    )
}

@Composable
fun MedicineDetailScreen(
    snapshot: AppSnapshot,
    medicineId: String,
    busy: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: (Medicine) -> Unit,
    onMoveToTrash: (Medicine) -> Unit,
    loadPhoto: suspend (String) -> ByteArray?,
    photoVersion: Int,
) {
    val view = LocalView.current
    val item = snapshot.items.firstOrNull { it.id == medicineId }
    var confirmTrash by remember { mutableStateOf(false) }
    var photo by remember(medicineId) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(medicineId, item?.hasPhoto, photoVersion) {
        photo = if (item?.hasPhoto == true) loadPhoto(medicineId) else null
    }

    Scaffold(
        topBar = { ScreenTopBar("Medicine", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (item == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Medicine not found", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Button(onClick = onBack, modifier = Modifier.padding(top = 12.dp)) { Text("Go back") }
            }
        } else {
            val category = categoryById(item.category, snapshot.categories)
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Column(
                        Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                        ) {
                            Column(
                                Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.08f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colorFromHex(category.color)),
                                    ) {
                                        Text(
                                            category.label,
                                            color = MaterialTheme.colorScheme.onTertiary,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        )
                                    }
                                    TextButton(onClick = {
                                        Haptics.selection(view)
                                        onToggleFavorite(item)
                                    }) {
                                        Text(
                                            if (item.favorite) "★ Favorite" else "☆ Favorite",
                                            color = if (item.favorite) Color(0xFFFFD166) else MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.82f),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                Text(
                                    item.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontSize = 30.sp,
                                    lineHeight = 40.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(textDirection = TextDirection.Content),
                                )
                                if (item.note.isNotBlank()) {
                                    Text(
                                        item.note,
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.78f),
                                        textAlign = TextAlign.Center,
                                    )
                                }

                                Surface(
                                    color = Color.Black.copy(alpha = 0.11f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                ) {
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            "OFFICIAL PRICE · ${snapshot.currency}",
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color(0xFFA3CFB9),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                        )
                                        Text(
                                            formatPrice(item.official),
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color(0xFFB8F0CB),
                                            fontSize = 58.sp,
                                            fontWeight = FontWeight.Black,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                        )
                                    }
                                }

                                item.discounted?.let { price ->
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(
                                            if (price > item.official) "VERIFY THIS PRICE" else "IF CUSTOMER ASKS",
                                            modifier = Modifier.fillMaxWidth(),
                                            color = if (price > item.official) Color(0xFFFFB09C) else Color(0xFFDFC68C),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                        Text(
                                            "${formatPrice(price)} ${snapshot.currency}",
                                            modifier = Modifier.fillMaxWidth(),
                                            color = Color(0xFFFFE2A2),
                                            fontSize = 27.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }

                        InfoCard("Medicine info") {
                            InfoValue("CATEGORY", category.label, snapshot.largeText)
                            InfoValue("SUBCATEGORY", subcategoryLabel(item.subcategory), snapshot.largeText)
                            InfoValue("DATE ADDED", formatAddedDate(item.createdAt), snapshot.largeText)
                            Text(
                                "Older records may show the date they were imported or migrated.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }

                        photo?.let { bytes ->
                            val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
                            bitmap?.let {
                                InfoCard("Photo") {
                                    Image(it, contentDescription = "Photo of ${item.name}",
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp), contentScale = ContentScale.Fit)
                                }
                            }
                        }
                        if (item.codes.isNotEmpty()) InfoCard("Package codes") {
                            item.codes.forEach { code ->
                                InfoValue(when (code.kind) { CodeKind.PRICE_STICKER_QR -> "PRICE STICKER QR"; CodeKind.QR -> "QR"; CodeKind.BARCODE -> "BARCODE" },
                                    "${code.label.takeIf { it.isNotEmpty() }?.let { "$it · " } ?: ""}${code.value}", snapshot.largeText)
                            }
                        }

                        if (item.description.isNotBlank()) {
                            InfoCard("Description") {
                                Text(
                                    item.description,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp,
                                    lineHeight = 25.sp,
                                    textAlign = TextAlign.Start,
                                    style = TextStyle(textDirection = TextDirection.Content),
                                )
                            }
                        }

                        Button(
                            onClick = {
                                Haptics.action(view)
                                onEdit()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ),
                        ) {
                            Text("✎ Edit medicine", fontWeight = FontWeight.ExtraBold)
                        }
                        Button(
                            enabled = !busy,
                            onClick = {
                                Haptics.action(view)
                                confirmTrash = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Text("Move to Trash", fontWeight = FontWeight.ExtraBold)
                        }
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp),
                        ) { Text("Done", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    if (confirmTrash && item != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmTrash = false },
            title = { Text("Move “${item.name}” to Trash?") },
            text = { Text("You can restore it later from Settings > Trash.") },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { confirmTrash = false },
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        confirmTrash = false
                        onMoveToTrash(item)
                    },
                ) {
                    Text(
                        "Move to Trash",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

@Composable
private fun InfoValue(label: String, value: String, large: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = if (large) 21.sp else 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
