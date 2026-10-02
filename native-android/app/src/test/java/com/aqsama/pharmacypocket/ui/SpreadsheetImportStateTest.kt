package com.aqsama.pharmacypocket.ui

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.aqsama.pharmacypocket.data.ImportField
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpreadsheetImportStateTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun main() { Dispatchers.setMain(dispatcher) }
    @After fun reset() { Dispatchers.resetMain() }

    private suspend fun awaitState(model: SpreadsheetImportViewModel, predicate: (SpreadsheetImportState) -> Boolean): SpreadsheetImportState = withTimeout(30_000) {
        while (!predicate(model.state.value)) {
            dispatcher.scheduler.advanceUntilIdle()
            delay(10)
        }
        model.state.value
    }

    @Test fun newMappingInvalidatesOldPreviewAndRecoversWithoutLosingSource() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "import-state.csv").apply { writeText("Name,Store Price,Other\nBrand,1500.25,Retained") }
        val model = SpreadsheetImportViewModel()
        try {
            model.loadFile(context, Uri.fromFile(file))
            val loaded = awaitState(model) { it.prepared != null && !it.working }
            assertEquals("Brand", loaded.prepared!!.medicines.single().name)
            model.updateMapping(loaded.mappings[0].copy(field = ImportField.CUSTOM))
            assertNull(model.state.value.prepared)
            val invalid = awaitState(model) { it.error != null && !it.working }
            assertTrue(invalid.error!!.contains("Name"))
            assertNotNull(invalid.workbook)
            model.updateMapping(invalid.mappings[0].copy(field = ImportField.NAME))
            val ready = awaitState(model) { it.prepared != null && !it.working }
            assertEquals("1500.25", ready.prepared!!.medicines.single().importedFields.first { it.field == ImportField.PHARMACY_PRICE }.value)
            model.reset()
            assertNull(model.state.value.workbook)
        } finally { model.reset(); file.delete() }
    }
    @Test fun assigningNameMovesItAndRangeSelectionUsesActualRowNumbers() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "role-range.csv").apply { writeText("Name,Alternate,Price\nBrand,Common name,100\nSecond,Other name,200") }
        val model = SpreadsheetImportViewModel()
        try {
            model.loadFile(context, Uri.fromFile(file))
            val loaded = awaitState(model) { it.prepared != null && !it.working }
            model.updateMapping(loaded.mappings[1].copy(field = ImportField.NAME))
            val reassigned = awaitState(model) { it.prepared != null && !it.working }
            assertEquals(1, reassigned.mappings.count { it.field == ImportField.NAME })
            assertEquals(ImportField.CUSTOM, reassigned.mappings[0].field)
            assertEquals("Common name", reassigned.prepared!!.medicines.first().name)
            model.selectRange(3, 3)
            val limited = awaitState(model) { it.prepared != null && !it.working }
            assertEquals(listOf("Other name"), limited.prepared!!.medicines.map { it.name })
            assertEquals(limited.idPrefix + "-3", limited.prepared.medicines.single().id)
            model.selectRange(4, 2)
            assertTrue(awaitState(model) { it.error != null && !it.working }.error!!.contains("row range"))
        } finally { model.reset(); file.delete() }
    }

    @Test fun choosingAnotherHeaderPreservesCustomMappingsAndRowRange() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "headers.csv").apply { writeText("Title,\nName,Store Price\nFirst,100\nSecond,200") }
        val model = SpreadsheetImportViewModel()
        try {
            model.loadFile(context, Uri.fromFile(file))
            val loaded = awaitState(model) { it.prepared != null && !it.working }
            assertEquals(1, loaded.headerRow)
            model.updateMapping(loaded.mappings[0].copy(label = "Common name"))
            model.selectRange(4, 4)
            awaitState(model) { it.prepared != null && !it.working }
            model.selectHeader(0)
            val retained = awaitState(model) { it.prepared != null && !it.working }
            assertEquals("Common name", retained.mappings[0].label)
            assertEquals(ImportField.NAME, retained.mappings[0].field)
            assertEquals(4, retained.firstRow); assertEquals(4, retained.lastRow)
            assertEquals(listOf("Second"), retained.prepared!!.medicines.map { it.name })
        } finally { model.reset(); file.delete() }
    }

}
