package com.aqsama.pharmacypocket.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class ImportedFieldsLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Before fun bindMain() { Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher()) }
    @After fun resetMain() { Dispatchers.resetMain() }
    private val fields = listOf(
        ImportedField("scientific", "Scientific name", "اسم علمي طويل · Methyl prednisolone 125 mg/ml", ImportField.SCIENTIFIC, true),
        ImportedField("pharmacy", "Pharmacy price", "14,500.25", ImportField.PHARMACY_PRICE, true),
        ImportedField("wholesale", "Wholesale price", "9,179.28", ImportField.WHOLESALE_PRICE, true),
        ImportedField("manufacturer", "Manufacturer", "Details-only manufacturer", ImportField.CUSTOM, false),
    )

    @Test fun cardAtLargeTextAndRtlKeepsExactPricesAndExistingActions() {
        val item = Medicine("import", "Pain", "General", "دواء", "", official = 14500, discounted = 9179, imported = true, importedFields = fields)
        compose.setContent {
            PharmacyPocketTheme(ThemePreference.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 1.7f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.width(300.dp).verticalScroll(rememberScrollState())) {
                        MedicineCard(item, Category("Pain", "Pain", "ألم", "#758790"), true, "IQD", true, true,
                            {}, {}, {}, {}, { null }, 0)
                    }
                }
            }
        }
        compose.onNodeWithText("14,500.25").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("9,179.28").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Details-only manufacturer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit دواء").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Add or replace photo or code for دواء").performScrollTo().assertIsDisplayed()
        capture("imported-card-rtl")
    }

    private fun capture(name: String) {
        System.getenv("PHARMACY_SCREENSHOTS_DIR")?.let { directory ->
            val file = java.io.File(directory, "$name.png")
            file.parentFile?.mkdirs()
            file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun sourceScreenExplainsPrivateSheetsAndEnablesLinkImportAfterInput() {
        compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) { SpreadsheetImportScreen(false, {}, { _, _, _, _, _, _ -> }) } }
        compose.onNodeWithText("Load Google Sheet").assertIsNotEnabled()
        compose.onNodeWithText("Google Sheets link").performTextInput("https://docs.google.com/spreadsheets/d/example/edit")
        compose.onNodeWithText("Load Google Sheet").assertIsEnabled()
        compose.onNodeWithText("Choose XLSX or CSV file").assertIsDisplayed()
        capture("spreadsheet-source")
    }

    @Test fun longDecimalPriceWrapsWithoutTruncationAtLargeTextScale() {
        val value = "9,007,199,254,740,990.25"
        compose.setContent {
            PharmacyPocketTheme(ThemePreference.LIGHT) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.width(180.dp).verticalScroll(rememberScrollState())) {
                        ImportedFields(listOf(fields[1].copy(value = value)), compact = true, large = true)
                    }
                }
            }
        }
        val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        val node = compose.onNodeWithText(value).performScrollTo().fetchSemanticsNode()
        assertTrue(node.config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult].action!!.invoke(results))
        assertFalse(results.single().hasVisualOverflow)
    }

    @Test fun detailsDisplayFieldsOmittedFromCards() {
        compose.setContent { PharmacyPocketTheme(ThemePreference.LIGHT) { ImportedFields(fields, compact = false) } }
        compose.onNodeWithText("Details-only manufacturer").assertIsDisplayed()
    }
}
