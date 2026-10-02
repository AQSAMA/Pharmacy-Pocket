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
}
