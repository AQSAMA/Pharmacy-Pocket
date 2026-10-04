package com.aqsama.pharmacypocket.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import org.robolectric.shadows.ShadowDialog
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class LibraryFeaturesLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Before fun bindMain() { Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }
    private val categories = listOf(Category("all", "All", "All", "#2f856d"), Category("root", "Medicines", "أدوية", "#2f856d"), Category("child", "Pain", "ألم", "#596aab"))
    private val item = Medicine("m", "child", "General", "My medicine", "", official = 1_000_000, discounted = null)
    private val snapshot = AppSnapshot(listOf(item), categories, false, "IQD", ThemePreference.SYSTEM)

    @Test fun manualCardHasFieldsInEachPositionAndKeepsFullPrice() {
        val fields = listOf(
            ImportedField("source-name", "Top label", "Top value", ImportField.CUSTOM, true, color = "#2f856d", placement = FieldPlacement.TOP),
            ImportedField("custom-body", "Body label", "Body value", ImportField.CUSTOM, true),
            ImportedField("custom-bottom", "Bottom label", "Bottom value", ImportField.CUSTOM, true, placement = FieldPlacement.FOOTER),
            ImportedField("custom-hidden", "Hidden label", "Hidden value", ImportField.CUSTOM, false),
        )
        compose.setContent {
            PharmacyPocketTheme(ThemePreference.LIGHT) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                        MedicineCard(item.copy(importedFields = fields), categories.last(), false, "IQD", false, true, {}, {}, {}, {}, { null }, 0)
                    }
                }
            }
        }
        compose.onNodeWithText("1,000,000").assertExists()
        compose.onNodeWithText("Hidden value").assertDoesNotExist()
        compose.onAllNodesWithText("Top value", useUnmergedTree = true).assertCountEquals(1)
        val top = compose.onNodeWithText("Top value", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val name = compose.onNodeWithText("My medicine", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val body = compose.onNodeWithText("Body value", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val bottom = compose.onNodeWithText("Bottom value", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertTrue("Expected top < name < body < bottom; got $top, $name, $body, $bottom", top < name && name < body && body < bottom)
        capture("custom-fields-rtl")
    }

    @Test fun draggingFieldAcrossZonesChangesItsPersistedPlacement() {
        var fields by mutableStateOf(listOf(ImportedField("custom-drag", "Dose", "Once daily", ImportField.CUSTOM, true)))
        compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) { CustomFieldsDesigner(fields) { fields = it } } }
        compose.onNodeWithText("Card fields").performClick()
        compose.onNodeWithText("Preview").performClick()
        val from = compose.onNodeWithTag("card-field:custom-drag").fetchSemanticsNode().boundsInRoot.topLeft + Offset(20f, 20f)
        val to = compose.onNodeWithTag("card-zone:TOP").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("card-field:custom-drag").performTouchInput {
            down(Offset(20f, 20f))
            advanceEventTime(700)
            moveTo(Offset(20f, 20f) + (to - from), delayMillis = 300)
            up()
        }
        compose.waitUntil(3000) { fields.single().placement == FieldPlacement.TOP }
        compose.runOnIdle { assertTrue(fields.single().onCard) }
        capture("card-field-drag")
    }


    @Test fun addFieldColorAndVisibilityAreDirectlyEditable() {
        var fields by mutableStateOf(emptyList<ImportedField>())
        compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) { CustomFieldsDesigner(fields) { fields = it } } }
        compose.onNodeWithText("Card fields").performClick()
        compose.onNodeWithText("Add field").performClick()
        compose.onNodeWithText("Label").performTextInput("Dose")
        compose.onNodeWithText("Value").performTextInput("Once daily")
        compose.onNodeWithContentDescription("Color #2f856d").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.runOnIdle { assertEquals("Dose", fields.single().label); assertEquals("#2f856d", fields.single().color) }
        compose.onNodeWithContentDescription("Show Dose on card").performClick()
        compose.runOnIdle { assertFalse(fields.single().onCard) }
        capture("field-designer")
    }

    @Test fun copyToAnotherListUsesTheChosenDestination() {
        var selected: String? = null
        var remove = true
        compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) {
            MoveToMainSheet(item.copy(imported = true), snapshot, listOf(ImportedList("other", "Second list", "CSV", emptyList())), false, {},
                onTransfer = { destination, delete, _, _, _, merge -> selected = destination; remove = delete; assertNull(merge) })
        } }
        compose.onNodeWithTag("transfer-destination").performClick()
        compose.onNodeWithText("Second list").performClick()
        compose.onNodeWithTag("transfer-confirm").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("other", selected); assertFalse(remove) }
        capture("copy-medication")
    }

    @Test fun mergeShowsPreservedNameAndPricesAndNeedsATarget() {
        var mergeId: String? = null
        var remove = false
        compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(1f, 1.5f)) {
                MoveToMainSheet(item.copy(id = "source", name = "Imported brand", imported = true), snapshot.copy(items = listOf(item.copy(discounted = 900_000))), emptyList(), false, {},
                    onTransfer = { _, delete, _, _, _, merge -> mergeId = merge; remove = delete })
            }
        } }
        compose.onNodeWithText("Merge").performClick()
        compose.onNodeWithTag("transfer-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("transfer-target").performClick()
        compose.onNodeWithText("Search").performTextInput("My medicine")
        compose.onNodeWithTag("picker-medication:m").performClick()
        compose.onNodeWithText("Name & prices kept").assertExists()
        compose.onNodeWithText("1,000,000 IQD").assertExists()
        compose.onNodeWithText("Move source to Trash").performScrollTo().performClick()
        compose.onNodeWithTag("transfer-confirm").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(item.id, mergeId); assertTrue(remove) }
        capture("merge-medication-rtl-large")
    }

    @Test fun transferCannotSubmitTwiceWhileSaving() {
        compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) { MoveToMainSheet(item.copy(imported = true), snapshot, emptyList(), true, {}, { _, _, _, _, _, _ -> fail("Busy transfer submitted") }) } }
        compose.onNodeWithTag("transfer-confirm").assertIsNotEnabled().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close transfer").assertIsNotEnabled()
    }

    @Test fun sortAndDisplayChoicesUseNativeControls() {
        var sort: MedicineSort? = null
        compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) { HomeDisplaySheet(MedicineSort.DEFAULT, false, true, { sort = it }, {}, {}, {}) } }
        compose.onNodeWithText(MedicineSort.PRICE_ASC.label).performClick()
        compose.runOnIdle { assertEquals(MedicineSort.PRICE_ASC, sort) }
        capture("sort-display")
    }

    private fun capture(name: String) {
        System.getenv("PHARMACY_SCREENSHOTS_DIR")?.let { dir ->
            val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
            val decor = dialog?.window?.decorView
            val bitmap = if (decor != null && decor.width > 0 && decor.height > 0) {
                Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { decor.draw(Canvas(it)) }
            } else compose.onRoot().captureToImage().asAndroidBitmap()
            java.io.File(dir, "$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
