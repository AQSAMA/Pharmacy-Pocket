package com.aqsama.pharmacypocket.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.compose.ui.graphics.asAndroidBitmap
import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class ImportedFollowupLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Before fun main() { Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher()) }
    @After fun reset() { Dispatchers.resetMain() }
    private fun capture(name: String) {
        System.getenv("PHARMACY_SCREENSHOTS_DIR")?.let { folder ->
            java.io.File(folder, "$name.png").also { file ->
                file.parentFile?.mkdirs()
                file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
    @Test fun movedCardShowsCommonNameOriginalNameAndChosenPriceWithSourceFieldsInDetails() {
        val item = Medicine("moved", "tablets", "General", "My common name", "Source notes stay in details", official = 1700, discounted = null,
            importedFields = listOf(ImportedField("source-name", "Original name", "Actual brand name", ImportField.CUSTOM, false),
                ImportedField("pharmacy", "Source price", "1,500.25", ImportField.PHARMACY_PRICE, false)))
        compose.setContent {
            PharmacyPocketTheme(ThemePreference.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.7f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.width(300.dp).verticalScroll(rememberScrollState())) {
                        MedicineCard(item, PharmacyDefaults.categories[2], true, "IQD", true, true, {}, {}, {}, {}, { null }, 0)
                    }
                }
            }
        }
        compose.onNodeWithText("My common name").assertIsDisplayed()
        compose.onNodeWithText("Actual brand name").assertIsDisplayed()
        compose.onNodeWithText("1,700", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1,500.25").assertDoesNotExist()
        compose.onNodeWithText(item.note).assertDoesNotExist()
        capture("moved-main-card")
    }
    @Test fun savedSettingsOfferRowsColumnsPreviewAndSearchableRolesAtLargeText() {
        val sheet = SpreadsheetSheet("Saved sheet", listOf(SpreadsheetRow(1, listOf("Name", "Scientific name")), SpreadsheetRow(2, listOf("Brand", "Generic"))))
        val list = ImportedList("list", "Prices", "Prices.xlsx", suggestMappings(sheet.rows[0].cells))
        compose.setContent {
            PharmacyPocketTheme(ThemePreference.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.5f)) {
                    SpreadsheetImportScreen(false, {}, { _, _, _, _, _, _ -> }, editList = list,
                        loadSource = { ImportSource(SpreadsheetWorkbook(listOf(sheet)), ImportSelection(firstRow = 2, lastRow = 2), "import-ui") })
                }
            }
        }
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Worksheet: Saved sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Header row: 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Columns").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Scientific name"))
        compose.onNodeWithText("Scientific name").performClick()
        compose.onNodeWithText("Find a role").performTextInput("Category")
        compose.onAllNodes(hasScrollToNodeAction()).onLast().performScrollToNode(hasText("Category · level 4"))
        compose.onNodeWithText("Category · level 4").assertIsDisplayed()
        capture("mapping-role-options")
        compose.onNodeWithText("Find a role").performTextReplacement("Skip")
        compose.onNodeWithText("Skip column").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
        compose.onNodeWithText("Preview").performClick()
        compose.waitUntil(30_000) { compose.onAllNodes(hasText("Save settings · 1 rows") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("1 items ready", substring = true))
        compose.onNodeWithText("1 items ready", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Save settings · 1 rows").assertIsEnabled()
    }
}
