package com.aqsama.pharmacypocket.ui

import com.aqsama.pharmacypocket.data.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistReconciliationTest {
    @Test fun insertionReorderAndRenameKeepCompletion() {
        val previous = listOf(ChecklistItem("First", true), ChecklistItem("Second", false))
        assertEquals(listOf(
            ChecklistItem("New", false), ChecklistItem("Second", false), ChecklistItem("First", true),
        ), reconcileChecklist(listOf("New", "Second", "First"), previous))
        assertEquals(listOf(ChecklistItem("Renamed", true), ChecklistItem("Second", false)),
            reconcileChecklist(listOf("Renamed", "Second"), previous))
    }
}
