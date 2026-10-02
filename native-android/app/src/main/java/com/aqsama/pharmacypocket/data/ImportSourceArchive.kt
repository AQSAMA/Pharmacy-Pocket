package com.aqsama.pharmacypocket.data

import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Compact local source table; read only when the user opens import settings. */
internal object ImportSourceArchive {
    fun write(file: File, book: SpreadsheetWorkbook) {
        file.parentFile?.mkdirs()
        val staging = File(file.parentFile, "${file.name}.pending")
        try {
            DataOutputStream(GZIPOutputStream(staging.outputStream().buffered())).use { out ->
                out.writeInt(1); out.writeInt(book.sheets.size)
                book.sheets.forEach { sheet ->
                    out.writeUTF(sheet.name); out.writeInt(sheet.rows.size)
                    sheet.rows.forEach { row ->
                        out.writeInt(row.number); out.writeInt(row.cells.size)
                        row.cells.forEach(out::writeUTF)
                    }
                }
            }
            check(staging.renameTo(file)) { "Could not save the spreadsheet source." }
        } finally { staging.delete() }
    }
    fun read(file: File): SpreadsheetWorkbook = DataInputStream(GZIPInputStream(file.inputStream().buffered())).use { input ->
        require(input.readInt() == 1)
        val sheets = input.readInt().also { require(it in 1..64) }
        var cells = 0
        SpreadsheetWorkbook(List(sheets) {
            val name = input.readUTF()
            val rows = input.readInt().also { require(it in 0..SpreadsheetLimits.maxRows + 1) }
            SpreadsheetSheet(name, List(rows) {
                val number = input.readInt()
                val width = input.readInt().also { require(it in 0..SpreadsheetLimits.maxColumns) }
                cells += width; require(cells <= SpreadsheetLimits.maxCells)
                SpreadsheetRow(number, List(width) { input.readUTF().also { require(it.length <= SpreadsheetLimits.maxCellLength) } })
            })
        })
    }
}
