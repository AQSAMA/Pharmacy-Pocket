package com.aqsama.pharmacypocket.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
// Storage policy is API-independent; use a Robolectric-supported Android image.
@Config(sdk = [34])
class TrashStorageTest {
    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var storage: MedicineDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.filesDir, "SQLite/pharmacy-pocket.db")
        dbFile.delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
        storage = MedicineDatabase(context)
    }

    @After
    fun tearDown() {
        storage.close()
        dbFile.delete()
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }

    private fun medicine(
        id: String,
        name: String = id,
        category: String = "tablets",
        subcategory: String = "General",
        favorite: Boolean = false,
        createdAt: Long = 100L,
        price: Long = 1_000L,
    ) = Medicine(
        id = id,
        category = category,
        subcategory = subcategory,
        name = name,
        note = "note-$id",
        description = "description-$id",
        official = price,
        discounted = price - 100,
        revision = 1,
        favorite = favorite,
        createdAt = createdAt,
    )

    @Test
    fun migrationKeepsExistingRowsActiveAndPreservesFields() {
        dbFile.parentFile?.mkdirs()
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
        ).use { db ->
            db.execSQL(
                """
                CREATE TABLE medicines (
                  id TEXT PRIMARY KEY NOT NULL,
                  category TEXT NOT NULL,
                  subcategory TEXT NOT NULL,
                  name TEXT NOT NULL,
                  note TEXT NOT NULL,
                  description TEXT NOT NULL DEFAULT '',
                  official INTEGER NOT NULL,
                  discounted INTEGER,
                  revision INTEGER NOT NULL DEFAULT 0,
                  favorite INTEGER NOT NULL DEFAULT 0,
                  sort_order INTEGER NOT NULL,
                  created_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO medicines
                (id, category, subcategory, name, note, description, official, discounted, revision, favorite, sort_order, created_at)
                VALUES ('legacy', 'tablets', 'General', 'Legacy', 'keep', 'keep-desc', 3000, 2500, 7, 1, 42, 123456)
                """.trimIndent(),
            )
            db.execSQL("PRAGMA user_version = 1")
        }

        val item = storage.loadMedicines().single()

        assertEquals("legacy", item.id)
        assertEquals(emptyList<String>(), item.tags)
        assertEquals(emptyList<ChecklistItem>(), item.checklist)
        assertNull(item.reminderAt)
        assertEquals("Legacy", item.name)
        assertEquals("keep-desc", item.description)
        assertEquals(3_000L, item.official)
        assertEquals(2_500L, item.discounted)
        assertEquals(7, item.revision)
        assertTrue(item.favorite)
        assertEquals(123_456L, item.createdAt)
        assertEquals(0, storage.trashCount())

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val version = db.rawQuery("PRAGMA user_version", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(4, version)
            val columns = mutableSetOf<String>()
            db.rawQuery("PRAGMA table_info(medicines)", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) columns += cursor.getString(nameIndex)
            }
            assertTrue("deleted_at" in columns)
            assertTrue("tags" in columns)
            assertTrue("checklist" in columns)
            assertTrue("reminder_at" in columns)
            assertTrue("reminder_day" in columns)
            assertTrue("reminder_delivered" in columns)
            val deletedAt = db.rawQuery(
                "SELECT deleted_at FROM medicines WHERE id = 'legacy'",
                null,
            ).use { cursor ->
                cursor.moveToFirst()
                if (cursor.isNull(0)) null else cursor.getLong(0)
            }
            assertNull(deletedAt)
        }

        storage.close()
        storage = MedicineDatabase(context)
        val reopened = storage.loadMedicines().single()
        assertEquals("legacy", reopened.id)
        assertEquals(123_456L, reopened.createdAt)
        assertTrue(reopened.favorite)
        assertEquals(0, storage.trashCount())
    }

    @Test
    fun softDeleteHidesMedicineAndRestorePreservesIdentityFavoriteDateAndOrder() {
        storage.saveMedicine(medicine("a", favorite = true, createdAt = 111L))
        storage.saveMedicine(medicine("b", createdAt = 222L))

        assertTrue(storage.moveMedicineToTrash("a", deletedAt = 1_000L))
        assertFalse(storage.moveMedicineToTrash("a", deletedAt = 2_000L))
        assertEquals(listOf("b"), storage.loadMedicines().map { it.id })

        storage.close()
        storage = MedicineDatabase(context)

        val trashed = storage.loadTrash().single()
        assertEquals("a", trashed.medicine.id)
        assertTrue(trashed.medicine.favorite)
        assertEquals(111L, trashed.medicine.createdAt)
        assertEquals(1_000L, trashed.deletedAt)

        assertTrue(storage.restoreMedicine("a"))
        assertFalse(storage.restoreMedicine("a"))
        val restored = storage.loadMedicines()
        assertEquals(listOf("a", "b"), restored.map { it.id })
        assertTrue(restored.first().favorite)
        assertEquals(111L, restored.first().createdAt)
    }

    @Test
    fun permanentDeleteOnlyRemovesRowsAlreadyInTrash() {
        storage.saveMedicine(medicine("active"))
        storage.saveMedicine(medicine("trashed"))

        assertFalse(storage.permanentlyDeleteMedicine("active"))
        assertTrue(storage.moveMedicineToTrash("trashed", deletedAt = 10L))
        assertTrue(storage.permanentlyDeleteMedicine("trashed"))

        assertEquals(listOf("active"), storage.loadMedicines().map { it.id })
        assertTrue(storage.loadTrash().isEmpty())
    }

    @Test
    fun emptyTrashNeverTouchesActiveRows() {
        storage.saveMedicine(medicine("active"))
        storage.saveMedicine(medicine("trash-1"))
        storage.saveMedicine(medicine("trash-2"))
        storage.moveMedicineToTrash("trash-1", deletedAt = 10L)
        storage.moveMedicineToTrash("trash-2", deletedAt = 20L)

        assertEquals(2, storage.emptyTrash())
        assertEquals(listOf("active"), storage.loadMedicines().map { it.id })
        assertEquals(0, storage.trashCount())
    }

    @Test
    fun mergeReactivatesMatchingTrashIdUsingCurrentMergeSemantics() {
        storage.saveMedicine(medicine("same", name = "Old", favorite = true, createdAt = 50L))
        storage.moveMedicineToTrash("same", deletedAt = 10L)

        storage.mergeMedicines(
            listOf(
                medicine(
                    id = "same",
                    name = "Imported",
                    favorite = false,
                    createdAt = 75L,
                    price = 2_000L,
                ),
            ),
        )

        val item = storage.loadMedicines().single()
        assertEquals("same", item.id)
        assertEquals("Imported", item.name)
        assertEquals(2_000L, item.official)
        assertTrue(item.favorite)
        assertEquals(75L, item.createdAt)
        assertEquals(0, storage.trashCount())
    }

    @Test
    fun replaceMovesOmittedActiveItemsToTrashAndKeepsUnrelatedTrash() {
        storage.saveMedicine(medicine("keep", name = "Before"))
        storage.saveMedicine(medicine("omit"))
        storage.saveMedicine(medicine("old-trash"))
        storage.moveMedicineToTrash("old-trash", deletedAt = 5L)

        storage.replaceMedicines(
            listOf(
                medicine("keep", name = "Imported keep", favorite = true, createdAt = 999L),
                medicine("new", name = "Imported new", createdAt = 1_000L),
            ),
        )

        assertEquals(listOf("keep", "new"), storage.loadMedicines().map { it.id })
        assertEquals("Imported keep", storage.loadMedicines().first().name)
        assertEquals(setOf("omit", "old-trash"), storage.loadTrash().map { it.medicine.id }.toSet())
    }

    @Test
    fun replaceKeepsSortOrderUniqueAcrossActiveAndTrash() {
        storage.saveMedicine(medicine("old-a"))
        storage.saveMedicine(medicine("old-b"))
        storage.saveMedicine(medicine("old-trash"))
        storage.moveMedicineToTrash("old-trash", deletedAt = 5L)

        storage.replaceMedicines(
            listOf(
                medicine("import-a"),
                medicine("import-b"),
            ),
        )

        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val counts = db.rawQuery(
                "SELECT COUNT(*), COUNT(DISTINCT sort_order) FROM medicines",
                null,
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0) to cursor.getInt(1)
            }
            assertEquals(counts.first, counts.second)
        }

        assertEquals(3, storage.restoreAll())
        assertEquals(
            listOf("old-a", "old-b", "old-trash", "import-a", "import-b"),
            storage.loadMedicines().map { it.id },
        )
    }

    @Test
    fun replaceReactivatesIncomingIdThatWasAlreadyInTrash() {
        storage.saveMedicine(medicine("same", name = "Old"))
        storage.moveMedicineToTrash("same", deletedAt = 20L)

        storage.replaceMedicines(
            listOf(medicine("same", name = "Reintroduced", favorite = true, createdAt = 777L)),
        )

        val item = storage.loadMedicines().single()
        assertEquals("same", item.id)
        assertEquals("Reintroduced", item.name)
        assertTrue(item.favorite)
        assertEquals(777L, item.createdAt)
        assertEquals(0, storage.trashCount())
    }

    @Test
    fun restoreAllRestoresEveryTrashItemWithoutChangingOrder() {
        storage.saveMedicine(medicine("a", createdAt = 10L))
        storage.saveMedicine(medicine("b", createdAt = 20L))
        storage.saveMedicine(medicine("c", createdAt = 30L))
        storage.moveMedicineToTrash("a", deletedAt = 100L)
        storage.moveMedicineToTrash("c", deletedAt = 200L)

        assertEquals(2, storage.restoreAll())
        assertEquals(listOf("a", "b", "c"), storage.loadMedicines().map { it.id })
        assertEquals(0, storage.trashCount())
    }

    @Test
    fun normalBackupExportExcludesTrashAndRestoredMedicineExportsAgain() {
        storage.saveMedicine(medicine("active"))
        storage.saveMedicine(medicine("trash"))
        storage.moveMedicineToTrash("trash", deletedAt = 30L)

        fun exportedIds(): Set<String> {
            val raw = BackupCodec.encode(
                storage.loadMedicines(),
                "IQD",
                PharmacyDefaults.categories,
                exportedAt = "2026-09-25T00:00:00Z",
            )
            val array = JSONObject(raw).getJSONArray("medicines")
            return buildSet {
                for (index in 0 until array.length()) add(array.getJSONObject(index).getString("id"))
            }
        }

        assertEquals(setOf("active"), exportedIds())
        storage.restoreMedicine("trash")
        assertEquals(setOf("active", "trash"), exportedIds())
    }

    @Test
    fun richMedicineSurvivesStorageAndSingleFileBackup() {
        val item = medicine("rich").copy(
            tags = listOf("Stock", "إبر"),
            checklist = listOf(ChecklistItem("Check shelf", true), ChecklistItem("Call supplier")),
            reminderAt = 1_900_000_000_000L,
            reminderRepeat = ReminderRepeat.WEEKLY,
        )
        storage.saveMedicine(item)
        assertEquals(item, storage.loadMedicines().single())

        val json = BackupCodec.encode(storage.loadMedicines(), "IQD", PharmacyDefaults.categories)
        val restored = BackupCodec.parse(json).medicines.single()
        assertEquals(item, restored)
        storage.moveMedicineToTrash(item.id)
        assertEquals(item, storage.loadTrash().single().medicine)
    }

    @Test
    fun legacyMergeKeepsLocalRichFieldsWhileExplicitEmptyBackupClearsThem() {
        val local = medicine("m").copy(
            tags = listOf("Stock"), checklist = listOf(ChecklistItem("Call supplier", true)),
            reminderAt = 1_900_000_000_000L, reminderRepeat = ReminderRepeat.WEEKLY,
        )
        val encoded = BackupCodec.encode(listOf(local), "IQD", PharmacyDefaults.categories)
        val old = JSONObject(encoded).apply {
            getJSONArray("medicines").getJSONObject(0).apply {
                remove("tags"); remove("checklist"); remove("reminderAt"); remove("reminderRepeat")
            }
        }
        val parsed = BackupCodec.parse(old.toString())
        val merged = mergeImportedRichFields(parsed.medicines.single(), local, parsed.richFields[local.id])
        assertEquals(local.tags, merged.tags)
        assertEquals(local.checklist, merged.checklist)
        assertEquals(local.reminderAt, merged.reminderAt)

        val cleared = BackupCodec.parse(BackupCodec.encode(listOf(local.copy(
            tags = emptyList(), checklist = emptyList(), reminderAt = null, reminderRepeat = ReminderRepeat.NONE,
        )), "IQD", PharmacyDefaults.categories))
        val result = mergeImportedRichFields(cleared.medicines.single(), local, cleared.richFields[local.id])
        assertEquals(emptyList<String>(), result.tags)
        assertEquals(emptyList<ChecklistItem>(), result.checklist)
        assertNull(result.reminderAt)
    }

    @Test
    fun trashIsOrderedNewestDeletedFirst() {
        storage.saveMedicine(medicine("older"))
        storage.saveMedicine(medicine("newer"))
        storage.moveMedicineToTrash("older", deletedAt = 100L)
        storage.moveMedicineToTrash("newer", deletedAt = 200L)

        assertEquals(listOf("newer", "older"), storage.loadTrash().map { it.medicine.id })
    }
}
