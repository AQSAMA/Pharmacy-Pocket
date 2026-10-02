package com.aqsama.pharmacypocket.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpreadsheetImportTest {
    private val headers = listOf("Trading Name", "Scientific Name", "Store Price", "Cost Price", "Category", "Subcategory", "Level 3", "Level 4", "Manufacturer")
    private fun mappings() = suggestMappings(headers).map {
        when (it.column) {
            6 -> it.copy(field = ImportField.CATEGORY_3)
            7 -> it.copy(field = ImportField.CATEGORY_4)
            else -> it
        }
    }
    private fun prepared() = prepareSpreadsheet(SpreadsheetSheet("Prices", listOf(
        SpreadsheetRow(1, headers),
        SpreadsheetRow(2, listOf("Brand", "Generic", "1,500.25", "825.50", "Pain", "Tablets", "Adult", "Pack", "Manufacturer A")),
        SpreadsheetRow(3, listOf("Brand", "Different strength", "1,500.15", "", "Pain", "Tablets", "Child", "Pack", "Manufacturer B")),
        SpreadsheetRow(4, listOf("", "Footer")),
    )), 0, mappings())

    @Test fun mappingPreservesDuplicatesExactPricesHierarchyAndSearch() {
        val result = prepared()
        assertEquals(2, result.medicines.size)
        assertEquals(1, result.skippedRows)
        assertEquals(0, result.errorCount)
        assertNotEquals(result.medicines[0].id, result.medicines[1].id)
        val first = result.medicines.first()
        assertEquals("Tablets › Adult › Pack", first.subcategory)
        assertEquals("1,500.25", first.importedFields.first { it.field == ImportField.PHARMACY_PRICE }.value)
        assertEquals(listOf(result.medicines[1], first), filterSortedMedicines(sortSearchIndex(buildSearchIndex(result.medicines), MedicineSort.PRICE_ASC), MedicineFilters(), ""))
        assertEquals(listOf(first), filterSortedMedicines(buildSearchIndex(result.medicines), MedicineFilters(), "manufacturer a"))
    }

    @Test fun invalidMappingsAndPricesAreActionable() {
        assertThrows(IllegalArgumentException::class.java) { validateMappings(mappings().map { if (it.column == 0) it.copy(field = ImportField.CUSTOM) else it }) }
        assertThrows(IllegalArgumentException::class.java) { validateMappings(mappings().map { if (it.column == 6) it.copy(field = ImportField.CUSTOM) else it }) }
        assertThrows(IllegalArgumentException::class.java) { validateMappings(mappings().map { it.copy(onCard = true) }) }
        val sheet = SpreadsheetSheet("Bad", listOf(SpreadsheetRow(1, listOf("Name", "Store Price")), SpreadsheetRow(2, listOf("Bad", "N/A"))))
        val result = prepareSpreadsheet(sheet, 0, suggestMappings(sheet.rows[0].cells))
        assertEquals(1, result.errorCount)
        assertTrue(result.errors.single().contains("Row 2"))
        assertEquals("1234.50", spreadsheetPrice("١٬٢٣٤٫٥٠")!!.toPlainString())
        assertThrows(IllegalArgumentException::class.java) { spreadsheetPrice("-1") }
        assertEquals("1234.50", spreadsheetPrice("1.234,50", PriceFormat.COMMA_DECIMAL)!!.toPlainString())
        assertEquals("12.50", spreadsheetPrice("12,50", PriceFormat.COMMA_DECIMAL)!!.toPlainString())
        assertThrows(IllegalArgumentException::class.java) { spreadsheetPrice("12,50") }
    }

    @Test fun csvHandlesQuotesNewlinesBomUnicodeAndSparseRows() {
        val csv = "\uFEFFName,Notes,Store Price\r\n\"دواء\",\"first line\nsecond \"\"quoted\"\"\",1500.25\r\nSecond,,\r\n"
        val sheet = SpreadsheetReader.readCsv(csv.reader()).sheets.single()
        assertEquals(3, sheet.rows.size)
        assertEquals("first line\nsecond \"quoted\"", sheet.rows[1].cells[1])
        assertEquals("دواء", sheet.rows[1].cells[0])
        assertEquals(listOf("Name", "Price"), SpreadsheetReader.readCsv("Name;Price\nBrand;23.50".reader()).sheets.single().rows.first().cells)
        assertThrows(IllegalArgumentException::class.java) { SpreadsheetReader.readCsv("Name\n\"unfinished".reader()) }
        assertThrows(IllegalArgumentException::class.java) { SpreadsheetReader.readCsv(("Name\n" + "x".repeat(4097)).reader()) }
    }

    @Test fun xlsxResolvesWorksheetsSharedStringsInlineRichTextAndCachedFormulas() {
        val file = File.createTempFile("sheet-", ".xlsx")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                fun entry(path: String, xml: String) { zip.putNextEntry(ZipEntry(path)); zip.write(xml.toByteArray()); zip.closeEntry() }
                entry("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Prices" r:id="rId9"/><sheet name="Other" r:id="rId10"/></sheets></workbook>""")
                entry("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId9" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/prices.xml"/><Relationship Id="rId10" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="/xl/worksheets/other.xml"/></Relationships>""")
                entry("xl/sharedStrings.xml", """<sst><si><t>Name</t></si><si><r><t>دو</t></r><r><t>اء</t></r></si></sst>""")
                entry("xl/worksheets/prices.xml", """<worksheet><sheetData><row r="3"><c r="A3" t="s"><v>0</v></c><c r="C3" t="inlineStr"><is><t>Store Price</t></is></c></row><row r="4"><c r="A4" t="s"><v>1</v></c><c r="C4"><f>2*3</f><v>6.25</v></c></row></sheetData></worksheet>""")
                entry("xl/worksheets/other.xml", """<worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>Another sheet</t></is></c></row></sheetData></worksheet>""")
            }
            val workbook = SpreadsheetReader.read(file)
            assertEquals(2, workbook.sheets.size)
            assertEquals(listOf("دواء", "", "6.25"), workbook.sheets[0].rows[1].cells)
            assertEquals(3, workbook.sheets[0].rows[0].number)
        } finally { file.delete() }
    }

    @Test fun googleLinksOnlyAcceptSheetsAndPreserveSelectedTab() {
        assertEquals("https://docs.google.com/spreadsheets/d/abc_123/export?format=csv&gid=456", SpreadsheetReader.sheetsExportUrl("https://docs.google.com/spreadsheets/d/abc_123/edit#gid=456").toString())
        assertEquals("https://docs.google.com/spreadsheets/d/id/export?format=csv", SpreadsheetReader.sheetsExportUrl("https://docs.google.com/spreadsheets/u/1/d/id/edit").toString())
        listOf("http://docs.google.com/spreadsheets/d/id/edit", "https://evil.example/spreadsheets/d/id", "https://docs.google.com.evil.example/spreadsheets/d/id").forEach {
            assertThrows(IllegalArgumentException::class.java) { SpreadsheetReader.sheetsExportUrl(it) }
        }
    }

    @Test fun importedListsAreIsolatedAndFieldsSurviveEditTrashAndBackup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manual = PharmacyRepository(context)
        manual.saveMedicine(Medicine("manual", "tablets", "General", "Existing medicine", "My note", official = 1000, discounted = null, favorite = true,
            codes = listOf(MedicineCode(CodeKind.BARCODE, "manual-code"))))
        val manualBefore = manual.loadSnapshot()
        val store = ImportedListStore(context)
        val input = prepared()
        val one = store.create("Syndicate", "Prices.xlsx", mappings(), input)
        val two = store.create("Second list", "Prices.xlsx", mappings(), input)
        val repo = manual.forList(one.id)
        val other = manual.forList(two.id)
        try {
            val item = repo.loadSnapshot().items.first()
            assertEquals(2, repo.loadSnapshot().items.size)
            repo.toggleFavorite(item.id)
            assertTrue(repo.loadSnapshot().items.first().favorite)
            assertFalse(other.loadSnapshot().items.first().favorite)
            val changed = syncImportedFields(item.copy(name = "Edited", importedFields = item.importedFields.map { if (it.field == ImportField.PHARMACY_PRICE) it.copy(value = "750.15") else it }))
            repo.saveMedicine(changed)
            repo.saveMedicine(changed.copy(codes = listOf(MedicineCode(CodeKind.BARCODE, "shared-code"))))
            other.saveMedicine(other.loadSnapshot().items.first().copy(codes = listOf(MedicineCode(CodeKind.BARCODE, "shared-code"))))
            repo.moveMedicineToTrash(item.id)
            assertEquals(changed.importedFields, repo.loadTrash().single().medicine.importedFields)
            assertEquals(0, other.loadSnapshot().trashCount)
            repo.restoreMedicine(item.id)
            val parsed = BackupCodec.parse(repo.exportBackup())
            assertEquals(changed.importedFields, parsed.medicines.first { it.id == item.id }.importedFields)
            assertTrue(parsed.medicines.all { it.imported })
            repo.importBackup(parsed, ImportMode.REPLACE)
            assertEquals(changed.importedFields, repo.loadSnapshot().items.first { it.id == item.id }.importedFields)
            assertEquals(manualBefore.items, manual.loadSnapshot().items)
            assertThrows(IllegalArgumentException::class.java) { runBlocking { manual.importBackup(parsed, ImportMode.REPLACE) } }
            repo.setCurrency("USD")
            assertEquals(manualBefore.currency, manual.loadSnapshot().currency)
            assertEquals("IQD", other.loadSnapshot().currency)
            assertTrue(store.lists().any { it.id == one.id })
        } finally { repo.close(); other.close(); manual.close() }
    }

    @Test fun largeImportedBackupExceedsLegacyLimitWithoutLosingFields() {
        val first = prepared().medicines.first()
        val items = List(5001) { first.copy(id = "large-$it") }
        val raw = BackupCodec.encode(items, "IQD", listOf(Category("Pain", "Pain", "Pain", "#758790")), importedList = true)
        assertEquals(5001, BackupCodec.parse(raw).medicines.size)
    }

    @Test fun suppliedSyndicateWorkbookImportsEveryRecord() {
        val path = System.getenv("PHARMACY_SPREADSHEET_FIXTURE")
        assumeTrue("Supply PHARMACY_SPREADSHEET_FIXTURE for the user workbook integration test", path != null)
        val workbook = SpreadsheetReader.read(File(path!!))
        val sheet = workbook.sheets.single()
        assertEquals(21465, sheet.rows.size)
        assertEquals(14, sheet.rows[0].cells.size)
        val prepared = prepareSpreadsheet(sheet, 0, suggestMappings(sheet.rows[0].cells))
        assertEquals(21464, prepared.medicines.size)
        assertEquals(0, prepared.errorCount)
        assertEquals(0, prepared.skippedRows)
        assertEquals("Methyl prednisolone (as sod. Succinate) 125 mg/2 ml ACT-O vial  I.V,I.M use + diluent in the same vial", prepared.medicines.first().importedFields.first { it.field == ImportField.SCIENTIFIC }.value)
        assertEquals("14,500.00", prepared.medicines.first().importedFields.first { it.field == ImportField.PHARMACY_PRICE }.value)
        assertEquals(12, prepared.medicines.first().importedFields.size)
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = ImportedListStore(context)
            val imported = store.create("Iraqi syndicate", "products-price.xlsx", suggestMappings(sheet.rows[0].cells), prepared)
            val repository = PharmacyRepository(context, imported.id)
            try { assertEquals(21464, repository.loadSnapshot().items.size) } finally { repository.close() }
        }
    }
}
