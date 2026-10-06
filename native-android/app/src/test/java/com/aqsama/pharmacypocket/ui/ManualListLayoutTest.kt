package com.aqsama.pharmacypocket.ui

import android.os.Handler
import android.os.Looper
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp")
@LooperMode(LooperMode.Mode.PAUSED)
class ManualListLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Before fun bindMain() { Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }
    private val medicine = Medicine("m", "tablets", "General", "Medicine", "", official = 1500, discounted = null)
    private val snapshot = AppSnapshot(listOf(medicine), PharmacyDefaults.categories, false, "IQD", ThemePreference.LIGHT)
    private fun capture(name: String) {
        System.getenv("PHARMACY_SCREENSHOTS_DIR")?.let { folder ->
            java.io.File(folder, "$name.png").also { file ->
                file.parentFile?.mkdirs()
                file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun sidebarSeparatesListsAndSelectsAllWithTouch() {
        val lists = listOf(MedicationList(MAIN_LIST_KEY, "My medications", false), MedicationList("stock", "Stock", false), MedicationList("supplier", "Supplier", true))
        var selected by mutableStateOf(MAIN_LIST_KEY)
        var create = false
        compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) {
            LibraryDrawer(lists, selected, true, { selected = it }, { create = true }, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Manual lists").assertIsDisplayed()
        compose.onNodeWithText("Imported lists").assertIsDisplayed()
        compose.onNodeWithText("All manual lists").performTouchInput { click() }
        compose.runOnIdle { assertEquals(ALL_MANUAL_LISTS, selected) }
        compose.onNodeWithText("Stock").performTouchInput { click() }
        compose.runOnIdle { assertEquals("stock", selected) }
        compose.onNodeWithContentDescription("Create manual list").performClick()
        compose.runOnIdle { assertTrue(create) }
        capture("manual-list-sidebar")
    }

    @Test fun namedManualListCreatesOrdinaryMedicineWithSellingAndPurchasePrices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val list = runBlocking { ImportedListStore(context).createManual("Stock") }
        val base = PharmacyRepository(context)
        val stock = base.forList(list.id, imported = false)
        try {
            compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) { PharmacyApp(base) } }
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Lists and settings").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Lists and settings").performClick()
            compose.onNodeWithText("Stock").performClick()
            compose.onNodeWithContentDescription("Add medicine").performClick()
            compose.onNodeWithText("Medicine / brand").performTextInput("Test stock medicine")
            compose.onNodeWithText("Official price").performTextInput("1500")
            compose.onNodeWithText("Purchase price").performScrollTo().performTextInput("950.25")
            compose.onNodeWithText("Save medicine").performClick()
            compose.waitUntil(10000) { runBlocking { stock.loadSnapshot().items.isNotEmpty() } }
            val saved = runBlocking { stock.loadSnapshot().items.single() }
            assertEquals("Test stock medicine", saved.name)
            assertFalse(saved.imported)
            assertEquals(1500L, saved.official)
            assertEquals("950.25", saved.importedFields.single { it.field == ImportField.WHOLESALE_PRICE }.value)
            assertTrue(runBlocking { base.loadSnapshot().items.isEmpty() })
        } finally { stock.close(); base.close() }
    }

    @Test fun mainCanMergeIntoNamedManualListAndDestinationSwitchClearsTarget() {
        val lists = listOf(ImportedList("a", "Stock A", "", emptyList(), false), ImportedList("b", "Stock B", "", emptyList(), false))
        var destination: String? = null
        var merge: String? = null
        compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) {
            MoveToMainSheet(medicine, snapshot, lists, false, {}, onTransfer = { targetList, _, _, _, _, targetId -> destination = targetList; merge = targetId },
                mainAvailable = false, loadDestination = { key -> snapshot.copy(items = listOf(medicine.copy(id = "$key-target", name = "Target $key"))) })
        } }
        compose.onNodeWithText("Merge").performClick()
        compose.onNodeWithTag("transfer-target").performClick()
        compose.onNodeWithText("Target a").performClick()
        compose.onNodeWithTag("transfer-confirm").assertIsEnabled()
        compose.onNodeWithTag("transfer-destination").performClick()
        compose.onNodeWithText("Stock B").performClick()
        compose.onNodeWithTag("transfer-confirm").assertIsNotEnabled()
        compose.onNodeWithTag("transfer-target").performClick()
        compose.onNodeWithText("Target b").performClick()
        compose.onNodeWithTag("transfer-confirm").performClick()
        compose.runOnIdle { assertEquals("b", destination); assertEquals("b-target", merge) }
        capture("manual-transfer")
    }

    @Test fun purchasePriceAndAggregateCurrencyRemainReadableInRtlAtLargeText() {
        val cost = ImportedField("purchase", "Wholesale", "950.25", ImportField.WHOLESALE_PRICE, true)
        compose.setContent { PharmacyPocketTheme(ThemePreference.DARK) {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.width(320.dp).verticalScroll(rememberScrollState())) {
                    MedicineCard(medicine.copy(importedFields = listOf(cost), listLabel = "Stock", displayCurrency = "USD"), PharmacyDefaults.categories[2],
                        true, "IQD", false, true, {}, {}, {}, {}, { null }, 0)
                }
            }
        } }
        compose.onNodeWithText("Purchase price").assertExists()
        compose.onNodeWithText("950.25").assertExists()
        compose.onNodeWithText("USD", substring = true).assertExists()
        compose.onNodeWithText("Stock", substring = true).assertExists()
        capture("purchase-price-rtl")
    }

    @Test fun exportMedicinePickerKeepsOnlyChosenMedicine() {
        compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) {
            ListBackupScreen(listOf(MedicationList(MAIN_LIST_KEY, "Mine", false)), MAIN_LIST_KEY, {},
                loadSnapshot = { snapshot.copy(items = listOf(medicine, medicine.copy(id = "two", name = "Second"))) },
                exportList = { _, _ -> "{}" }, onImport = { _, _, _ -> })
        } }
        compose.onNodeWithContentDescription("Choose medicines in Mine").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Use selection").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Clear").performClick()
        compose.onNodeWithText("Second").performTouchInput { click() }
        compose.onNodeWithText("Use selection").performClick()
        compose.onNodeWithText("Manual · 1 selected").assertIsDisplayed()
        capture("selective-export")
    }
}
