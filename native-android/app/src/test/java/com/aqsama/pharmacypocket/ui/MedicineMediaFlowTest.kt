package com.aqsama.pharmacypocket.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.aqsama.pharmacypocket.data.Medicine
import com.aqsama.pharmacypocket.data.PharmacyRepository
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class MedicineMediaFlowTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var repository: PharmacyRepository
    private val medicine = Medicine(id = "flow", category = "tablets", subcategory = "General", name = "Photo flow", note = "", official = 1000, discounted = null)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "SQLite").deleteRecursively()
        File(context.noBackupFilesDir, "medicine_drafts").deleteRecursively()
        File(context.noBackupFilesDir, "medicine_crop_sources").deleteRecursively()
        repository = PharmacyRepository(context)
    }

    @Test fun existingMedicineCameraCropSaveEditorDatabaseHomeDetailAndRestart() = exerciseFlow(gallery = false, newMedicine = false)
    @Test fun existingMedicineGalleryCropSaveEditorDatabaseHomeDetailAndRestart() = exerciseFlow(gallery = true, newMedicine = false)
    @Test fun newMedicineCameraCropSaveEditorDatabaseHomeDetailAndRestart() = exerciseFlow(gallery = false, newMedicine = true)
    @Test fun newMedicineGalleryCropSaveEditorDatabaseHomeDetailAndRestart() = exerciseFlow(gallery = true, newMedicine = true)

    @Test fun galleryCropWriteFailureStaysInsideCropInsteadOfOpeningEditorAlert() {
        val snapshot = runBlocking { repository.saveMedicine(medicine) }
        val media = MedicineMedia()
        val source = File(context.cacheDir, "gallery-failure-source.jpg")
        val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }

        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(source)))
                }
            }
        }

        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                MedicineEditorScreen(
                    snapshot = snapshot,
                    medicineId = medicine.id,
                    initialCategory = medicine.category,
                    busy = false,
                    onBack = {},
                    onManageCategories = {},
                    onMoveToTrash = {},
                    onSave = {},
                    loadPhoto = repository::loadPhoto,
                    media = media,
                )
            }
        }

        compose.onNodeWithText("Photo & codes").performScrollTo().performClick()
        compose.onNodeWithText("Gallery").performScrollTo().performClick()
        compose.waitUntil(10_000) { media.cropSource != null }

        val draftDirectory = File(context.noBackupFilesDir, "medicine_drafts")
        draftDirectory.deleteRecursively()
        draftDirectory.writeText("block draft directory creation")
        assertTrue(draftDirectory.isFile)

        try {
            compose.onNodeWithText("Save photo").assertIsEnabled().performClick()
            compose.waitUntil(10_000) { media.phase == MedicineMedia.Phase.CROPPING }
            compose.onNodeWithText("Could not prepare photo storage.").assertIsDisplayed()
            compose.onNodeWithText("Check the details").assertDoesNotExist()
            compose.onNodeWithText("Save photo").assertIsEnabled()
        } finally {
            draftDirectory.delete()
            media.cancelCrop()
            source.delete()
        }
    }

    private fun exerciseFlow(gallery: Boolean, newMedicine: Boolean) {
        var snapshot by mutableStateOf(runBlocking {
            if (newMedicine) repository.loadSnapshot() else repository.saveMedicine(medicine)
        })
        val media = MedicineMedia()
        val source = File(context.cacheDir, "flow-source.jpg")
        val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
        if (!gallery) {
            media.openCamera()
            val capture = requireNotNull(media.captureState)
            assertTrue(capture.capture(source))
            capture.captured(source) // CameraX's completed output-file callback.
        }
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(source)))
                }
            }
        }
        var screen by mutableStateOf("editor")
        var savedMedicineId: String? = if (newMedicine) null else medicine.id
        var saved = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
            when (screen) {
                "editor" -> MedicineEditorScreen(
                    snapshot = snapshot, medicineId = if (newMedicine) null else medicine.id, initialCategory = medicine.category,
                    busy = false, onBack = {}, onManageCategories = {}, onMoveToTrash = {},
                    loadPhoto = repository::loadPhoto, media = media,
                    onSave = { item -> scope.launch {
                        savedMedicineId = item.id
                        snapshot = media.save(repository, item)
                        media.discard()
                        saved = true
                        screen = "home"
                    } },
                )
                "home" -> {
                    val id = requireNotNull(savedMedicineId)
                    MedicineCard(
                        item = snapshot.items.single { it.id == id },
                        category = snapshot.categories.first { it.id == medicine.category },
                        large = false, currency = snapshot.currency, first = true, last = true,
                        onOpen = { screen = "detail" }, onEdit = {}, onFavorite = {}, onCamera = {},
                        loadPhoto = repository::loadPhoto, photoVersion = 1,
                    )
                }
                "detail" -> {
                    val id = requireNotNull(savedMedicineId)
                    MedicineDetailScreen(
                        snapshot = snapshot, medicineId = id, busy = false,
                        onBack = {}, onEdit = {}, onToggleFavorite = {}, onMoveToTrash = {},
                        loadPhoto = repository::loadPhoto, photoVersion = 1,
                    )
                }
            }
            }
        }
        try {
        if (gallery) {
            compose.onNodeWithText("Photo & codes").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("Gallery").performScrollTo().performClick()
            compose.waitUntil(10_000) { media.cropSource != null }
        }
        compose.onNodeWithContentDescription("Take package photo").assertDoesNotExist()
        compose.onNodeWithText("Save photo").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { media.preview != null && media.cropSource == null }
        compose.waitUntil(10_000) { media.captureState == null }
        compose.onNodeWithContentDescription("Medicine photo").performScrollTo().assertExists()
        if (newMedicine) {
            compose.onNode(
                hasSetTextAction() and hasAnyDescendant(hasText("Medicine / brand")),
                useUnmergedTree = true,
            ).performTextInput(medicine.name)
            compose.onNode(
                hasSetTextAction() and hasAnyDescendant(hasText("Official price")),
                useUnmergedTree = true,
            ).performTextInput(medicine.official.toString())
        }
        val accepted = requireNotNull(media.preview)
        val draft = requireNotNull(media.photoPath)
        compose.onNodeWithText("Save medicine").performClick()
        compose.waitUntil(10_000) { saved }
        val id = requireNotNull(savedMedicineId)
        assertFalse(File(draft).exists())
        assertTrue(snapshot.items.single { it.id == id }.hasPhoto)
        assertArrayEquals(accepted, runBlocking { repository.loadPhoto(id) })
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Package photo of ${medicine.name}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Package photo of ${medicine.name}").assertExists()
        compose.runOnIdle { screen = "detail" }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Photo of ${medicine.name}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Photo of ${medicine.name}").performScrollTo().assertExists()
        val reopened = PharmacyRepository(context)
        assertTrue(runBlocking { reopened.loadSnapshot() }.items.single { it.id == id }.hasPhoto)
        assertArrayEquals(accepted, runBlocking { reopened.loadPhoto(id) })
        } catch (failure: Throwable) {
            println("Media phase=${media.phase}, capture=${media.captureState?.phase}, preview=${media.preview?.size}, crop=${media.cropSource}, error=${media.error}")
            val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
            repeat(roots.fetchSemanticsNodes().size) { println(roots[it].printToString()) }
            throw failure
        }

    }
}
