package com.aqsama.pharmacypocket.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.aqsama.pharmacypocket.data.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class LibraryWorkflowTest {
    @get:Rule val compose = createComposeRule()
    @Before fun bindMain() { Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }

    private val medicines = listOf(
        Medicine("a", "tablets", "Pain relief", "Paracetamol", "500 mg · 20 tablets", official = 3000, discounted = 2500, favorite = true, createdAt = 1790586000000, hasPhoto = true),
        Medicine("b", "tablets", "Pain relief", "Ibuprofen", "200 mg · 24 tablets", official = 4000, discounted = 3500, createdAt = 1790499600000),
        Medicine("c", "syrups", "Allergy", "Cetirizine سيتريزين", "60 ml", official = 5000, discounted = 6000, createdAt = 1790413200000,
            codes = listOf(MedicineCode(CodeKind.BARCODE, "123456789"))),
    )
    private var snapshot by mutableStateOf(AppSnapshot(medicines, PharmacyDefaults.categories, false, "IQD", ThemePreference.LIGHT))
    private var opened: String? = null

    private fun show(dark: Boolean = false, rtl: Boolean = false, scale: Float = 1f) {
        val photo = packagePhoto()
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                LocalDensity provides Density(density, scale)) {
                PharmacyPocketTheme(if (dark) ThemePreference.DARK else ThemePreference.LIGHT) {
                    HomeScreen(snapshot, onSettings = {}, onAddMedicine = { _, _ -> }, onOpenMedicine = { opened = it },
                        onEditMedicine = { opened = it }, onToggleFavorite = { item -> snapshot = snapshot.copy(items = snapshot.items.map { if (it.id == item.id) it.copy(favorite = !it.favorite) else it }) },
                        onSetLargeText = { snapshot = snapshot.copy(largeText = it) }, onQuickCapture = {},
                        loadPhoto = { photo }, photoVersions = emptyMap())
                }
            }
        }
        compose.waitUntil(10_000) {
            ShadowLooper.idleMainLooper()
            compose.onAllNodesWithContentDescription("Package photo of Paracetamol").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun favoritesAndFiltersRemainIndependentAndResetWorks() {
        show()
        compose.onNodeWithContentDescription("Favorites").performClick()
        compose.onNodeWithText("1 medicine").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filters and display").performClick()
        compose.onNodeWithText("Missing photo").performClick()
        compose.onNodeWithText("Show medicines").performClick()
        compose.onNodeWithText("No matching medicines").assertIsDisplayed()
        compose.onNodeWithText("Reset filters").performClick()
        compose.onNodeWithText("1 medicine").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Paracetamol from favorites").performClick()
        compose.onNodeWithText("No favorites yet").assertIsDisplayed()
    }

    @Test fun overviewLinksClearOldSearchAndOpenTheRelevantRecords() {
        show()
        compose.onNode(hasSetTextAction()).performTextInput("not-a-medicine")
        compose.onNodeWithContentDescription("Overview").performClick()
        compose.onNodeWithContentDescription("View medicines missing photos").performClick()
        compose.onNodeWithText("2 medicines").assertIsDisplayed()
        compose.onNodeWithText("Missing photo").assertIsDisplayed()
    }

    @Test fun comparisonKeepsSelectionAndDoesNotNavigateOnSelectionTap() {
        show()
        compose.onNodeWithContentDescription("Compare medicines").performClick()
        compose.onNodeWithText("Paracetamol").performClick()
        compose.onNodeWithText("Ibuprofen").performScrollTo().performClick()
        assertNull(opened)
        compose.onNodeWithText("2/3 selected").assertIsDisplayed()
        compose.onNodeWithText("Compare", substring = false).performClick()
        compose.onNodeWithText("Compare medicines").assertIsDisplayed()
        compose.onAllNodesWithText("3,000").onLast().assertIsDisplayed()
        compose.onAllNodesWithText("4,000").onLast().assertIsDisplayed()
        screenshot("comparison")
        compose.onNodeWithText("Done").performClick()
        compose.runOnIdle { snapshot = snapshot.copy(items = snapshot.items.filterNot { it.id == "a" }) }
        compose.onNodeWithText("1/3 selected").assertIsDisplayed()
        compose.onNodeWithText("Compare", substring = false).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Cancel comparison").performClick()
        compose.onNodeWithText("2/3 selected").assertDoesNotExist()
    }

    @Test fun renderLibraryAndOverview() {
        show()
        screenshot("library-light")
        compose.onNodeWithContentDescription("Overview").performClick()
        screenshot("overview-light")
    }

    @Test fun renderDarkRtlWithLargeText() {
        show(dark = true, rtl = true, scale = 1.3f)
        screenshot("library-dark-rtl")
        compose.onNodeWithContentDescription("Filters and display").performClick()
        compose.onNodeWithText("With discount").assertIsDisplayed()
        screenshot("filters-dark-rtl")
        compose.onNodeWithText("Show medicines").performClick()
        compose.onNodeWithContentDescription("Add medicine").assertIsDisplayed()
    }

    @Test fun detailKeepsEditReachableAndCodesCopyable() {
        val photo = packagePhoto()
        compose.setContent {
            PharmacyPocketTheme(ThemePreference.LIGHT) {
                MedicineDetailScreen(snapshot, "c", false, onBack = {}, onEdit = {},
                    onToggleFavorite = {}, onMoveToTrash = {}, loadPhoto = { photo }, photoVersion = 0)
            }
        }
        compose.onNodeWithText("Edit medicine").assertIsDisplayed()
        screenshot("detail-light")
        compose.onNodeWithText("Copy code").performScrollTo().performClick()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        assertEquals("123456789", clipboard.primaryClip!!.getItemAt(0).text.toString())
        compose.onNodeWithText("Share medicine details").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Edit medicine").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val folder = File("build/reports/ui").apply { mkdirs() }
        compose.waitForIdle()
        val bitmap = if (name == "comparison" || name == "filters-dark-rtl") {
            // PixelCopy under Robolectric samples the activity for dialog roots. Draw the
            // actual dialog decor instead so sheet screenshots show their own window.
            val decor = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also {
                decor.draw(Canvas(it))
            }
        } else compose.onRoot().captureToImage().asAndroidBitmap()
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

    }

    /** Synthetic package artwork used only in UI tests. */
    private fun packagePhoto(): ByteArray {
        val bitmap = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(244, 246, 241))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(31, 85, 80)
        canvas.drawRect(12f, 28f, 228f, 96f, paint)
        paint.color = Color.WHITE; paint.textSize = 19f; paint.isFakeBoldText = true
        canvas.drawText("PARACETAMOL", 22f, 67f, paint)
        paint.color = Color.rgb(31, 85, 80); paint.textSize = 36f
        canvas.drawText("500 mg", 28f, 169f, paint)
        paint.textSize = 17f; paint.isFakeBoldText = false
        canvas.drawText("20 tablets", 28f, 207f, paint)
        canvas.drawRect(12f, 267f, 228f, 287f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); bitmap.recycle() }.toByteArray()
    }
}
