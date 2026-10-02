package com.aqsama.pharmacypocket.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.zip.ZipFile

/** Bounded OOXML and CSV readers. Formulas use cached values and are never evaluated. */
object SpreadsheetReader {
    fun read(file: File): SpreadsheetWorkbook {
        require(file.length() in 1..SpreadsheetLimits.maxBytes.toLong()) { "Choose a file smaller than 32 MB." }
        val zip = file.inputStream().use { it.read() == 'P'.code && it.read() == 'K'.code }
        return if (zip) readXlsx(file) else file.reader(Charsets.UTF_8).use { readCsv(it) }
    }

    fun copyBounded(input: InputStream, file: File) {
        file.outputStream().use { output ->
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val size = input.read(buffer)
                if (size == -1) break
                total += size
                require(total <= SpreadsheetLimits.maxBytes) { "The file exceeds 32 MB." }
                output.write(buffer, 0, size)
            }
        }
    }

    internal fun sheetsExportUrl(link: String): URL {
        val uri = runCatching { URI(link.trim()) }.getOrElse { throw IllegalArgumentException("Enter a Google Sheets link.") }
        require(uri.scheme == "https" && uri.host == "docs.google.com" && uri.userInfo == null && uri.port == -1) { "Use an https://docs.google.com/spreadsheets/ link." }
        val id = Regex("^/spreadsheets/(?:u/\\d+/)?d/([A-Za-z0-9_-]+)(?:/.*)?$").find(uri.path)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("Enter a Google Sheets document link.")
        val params = "${uri.rawQuery.orEmpty()}&${uri.rawFragment.orEmpty()}"
        val gid = Regex("(?:^|&)gid=(\\d+)(?:&|$)").find(params)?.groupValues?.get(1)
        return URL("https://docs.google.com/spreadsheets/d/$id/export?format=csv" + (gid?.let { "&gid=$it" } ?: ""))
    }

    fun readGoogleSheet(link: String, file: File): SpreadsheetWorkbook {
        var url = sheetsExportUrl(link)
        repeat(6) {
            require(url.protocol == "https" && (url.host == "docs.google.com" || url.host.endsWith(".googleusercontent.com"))) { "Unexpected Google Sheets download destination." }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = false
            }
            try {
                when (connection.responseCode) {
                    in 300..399 -> {
                        val location = connection.getHeaderField("Location") ?: error("Google Sheets did not provide a download.")
                        url = URL(url, location)
                    }
                    200 -> {
                        val type = connection.contentType.orEmpty()
                        require(!type.contains("text/html", ignoreCase = true)) { "This sheet is private. Export it as XLSX, or share it for viewing with anyone who has the link." }
                        connection.inputStream.use { copyBounded(it, file) }
                        return read(file)
                    }
                    401, 403 -> error("This sheet is private. Export it as XLSX, or share it for viewing with anyone who has the link.")
                    else -> error("Could not download this sheet (HTTP ${connection.responseCode}). Check the link and sharing settings.")
                }
            } finally { connection.disconnect() }
        }
        error("Too many redirects from Google Sheets. Try an XLSX export.")
    }

    private fun readXlsx(file: File): SpreadsheetWorkbook = ZipFile(file).use { zip ->
        var expandedBytes = 0L
        fun parse(path: String, block: (XmlPullParser) -> Unit) {
            val entry = zip.getEntry(path) ?: error("The XLSX file is missing $path.")
            require(entry.size <= SpreadsheetLimits.maxExpandedBytes) { "The workbook is too large when expanded." }
            zip.getInputStream(entry).use { stream ->
                val limited = object : java.io.FilterInputStream(stream) {
                    private val declaration = "<!DOCTYPE".toByteArray(Charsets.US_ASCII)
                    private var matched = 0
                    private fun inspect(byte: Int) {
                        matched = if (byte == declaration[matched].toInt()) matched + 1 else if (byte == declaration[0].toInt()) 1 else 0
                        require(matched != declaration.size) { "XLSX document declarations and custom XML entities are not supported." }
                    }
                    override fun read(): Int = super.read().also { if (it >= 0) { count(1); inspect(it) } }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = `in`.read(buffer, offset, length).also {
                        if (it > 0) { count(it); for (index in offset until offset + it) inspect(buffer[index].toInt() and 0xff) }
                    }
                    private fun count(size: Int) { expandedBytes += size; require(expandedBytes <= SpreadsheetLimits.maxExpandedBytes) { "The workbook exceeds the expanded size limit." } }
                }
                try {
                    val parser = Xml.newPullParser().apply {
                        setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
                        setInput(limited, "UTF-8")
                    }
                    block(parser)
                } catch (error: XmlPullParserException) {
                    val cause = error.detail
                    if (cause is IllegalArgumentException) throw cause
                    throw IllegalArgumentException("The XLSX file contains invalid XML.", error)
                }
            }
        }
        val strings = mutableListOf<String>()
        if (zip.getEntry("xl/sharedStrings.xml") != null) parse("xl/sharedStrings.xml") { parser ->
            var current: StringBuilder? = null
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                    "si" -> current = StringBuilder()
                    "t" -> current?.append(parser.nextText())
                }
                if (parser.eventType == XmlPullParser.END_TAG && parser.name == "si") {
                    require(strings.size < SpreadsheetLimits.maxCells) { "Too many shared strings." }
                    strings += checkedCell(current.toString())
                    current = null
                }
                parser.next()
            }
        }
        val relationships = mutableMapOf<String, String>()
        parse("xl/_rels/workbook.xml.rels") { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val type = parser.getAttributeValue(null, "Type").orEmpty()
                    if (type.endsWith("/worksheet") && parser.getAttributeValue(null, "TargetMode") != "External") {
                        val target = parser.getAttributeValue(null, "Target")
                        val path = if (target.startsWith("/")) target.removePrefix("/") else URI("xl/").resolve(target).normalize().path
                        require(path.startsWith("xl/worksheets/") && !path.contains("..")) { "Invalid worksheet path." }
                        relationships[parser.getAttributeValue(null, "Id")] = path
                    }
                }
                parser.next()
            }
        }
        val sheets = mutableListOf<Pair<String, String>>()
        parse("xl/workbook.xml") { parser ->
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val id = (0 until parser.attributeCount).firstOrNull { parser.getAttributeName(it).substringAfter(':') == "id" }
                        ?.let { parser.getAttributeValue(it) }
                    relationships[id]?.let { sheets += parser.getAttributeValue(null, "name") to it }
                }
                parser.next()
            }
        }
        require(sheets.isNotEmpty() && sheets.size <= 64) { "Choose a workbook with 1–64 worksheets." }
        var cells = 0
        var rowCount = 0
        SpreadsheetWorkbook(sheets.map { (name, path) ->
            val rows = mutableListOf<SpreadsheetRow>()
            parse(path) { parser ->
                var values = mutableMapOf<Int, String>()
                var rowNumber = 0
                var column = 0
                var type = ""
                var text = StringBuilder()
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                        "row" -> { values = mutableMapOf(); rowNumber = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: rowNumber + 1 }
                        "c" -> {
                            val reference = parser.getAttributeValue(null, "r").orEmpty()
                            column = reference.takeWhile { it.isLetter() }.fold(0) { result, char -> result * 26 + (char.uppercaseChar() - 'A' + 1) } - 1
                            if (column < 0) column = (values.keys.maxOrNull() ?: -1) + 1
                            require(column < SpreadsheetLimits.maxColumns) { "A worksheet has more than 128 columns." }
                            type = parser.getAttributeValue(null, "t").orEmpty(); text = StringBuilder()
                        }
                        "v", "t" -> text.append(parser.nextText())
                    }
                    if (parser.eventType == XmlPullParser.END_TAG) when (parser.name) {
                        "c" -> {
                            cells++; require(cells <= SpreadsheetLimits.maxCells) { "The workbook has too many cells." }
                            val raw = text.toString()
                            val value = when (type) {
                                "s" -> strings.getOrNull(raw.toIntOrNull() ?: -1) ?: error("Invalid XLSX shared string.")
                                "b" -> if (raw == "1") "TRUE" else "FALSE"
                                "e" -> raw // Visible error values can be retained as custom fields.
                                else -> raw
                            }
                            values[column] = checkedCell(value)
                        }
                        "row" -> if (values.values.any { it.isNotBlank() }) {
                            rowCount++; require(rowCount <= SpreadsheetLimits.maxRows + 1) { "The workbook has more than 100,000 rows." }
                            rows += SpreadsheetRow(rowNumber, List((values.keys.maxOrNull() ?: -1) + 1) { values[it].orEmpty() })
                        }
                    }
                    parser.next()
                }
            }
            SpreadsheetSheet(name, rows)
        })
    }

    private fun checkedCell(value: String): String {
        require(value.length <= SpreadsheetLimits.maxCellLength) { "A cell contains more than 4,096 characters." }
        return value
    }

    internal fun readCsv(reader: Reader): SpreadsheetWorkbook {
        val input = java.io.PushbackReader(reader.buffered(), 1)
        val rows = mutableListOf<SpreadsheetRow>()
        var row = mutableListOf<String>()
        var value = StringBuilder()
        var quoted = false
        var afterQuote = false
        var number = 1
        var cellCount = 0
        var characters = 0
        // Google exports use commas. Local CSV may use tabs or semicolons.
        val buffered = StringBuilder()
        var probeQuoted = false
        val delimiters = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        while (buffered.length < 8192) {
            val next = input.read(); if (next == -1) break
            val char = next.toChar(); buffered.append(char)
            if (char == '"') probeQuoted = !probeQuoted
            if (!probeQuoted && char in delimiters) delimiters[char] = delimiters.getValue(char) + 1
            if (!probeQuoted && char == '\n') break
        }
        val delimiter = delimiters.maxByOrNull { it.value }?.key ?: ','
        // Replay the probe as characters to preserve Unicode.
        val sequence = object : Reader() {
            var index = 0
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (index < buffered.length) { val count = minOf(length, buffered.length - index); buffered.toString().toCharArray(buffer, offset, index, index + count); index += count; return count }
                return input.read(buffer, offset, length)
            }
            override fun close() = Unit
        }
        val csv = java.io.PushbackReader(sequence.buffered(), 1)
        fun cell() {
            require(row.size < SpreadsheetLimits.maxColumns) { "The sheet has more than 128 columns." }
            cellCount++; require(cellCount <= SpreadsheetLimits.maxCells) { "The sheet has too many cells." }
            row += checkedCell(value.toString()); value = StringBuilder(); afterQuote = false
        }
        fun finishRow() {
            cell()
            if (row.any { it.isNotBlank() }) {
                require(rows.size <= SpreadsheetLimits.maxRows) { "The sheet has more than 100,000 rows." }
                rows += SpreadsheetRow(number, row.toList())
            }
            number++; row = mutableListOf()
        }
        while (true) {
            val next = csv.read(); if (next == -1) break
            characters++; require(characters <= SpreadsheetLimits.maxBytes) { "The sheet exceeds the size limit." }
            val char = next.toChar()
            if (characters == 1 && char == '\uFEFF') continue
            if (quoted) {
                if (char == '"') {
                    val following = csv.read()
                    if (following == '"'.code) value.append('"') else { quoted = false; afterQuote = true; if (following != -1) csv.unread(following) }
                } else value.append(char)
            } else when (char) {
                '"' -> { require(value.isEmpty() && !afterQuote) { "Invalid CSV quoting." }; quoted = true }
                delimiter -> cell()
                '\n', '\r' -> {
                    if (char == '\r') { val following = csv.read(); if (following != '\n'.code && following != -1) csv.unread(following) }
                    finishRow()
                }
                else -> { require(!afterQuote || char.isWhitespace()) { "Invalid CSV quoting." }; if (!afterQuote) value.append(char) }
            }
            require(value.length <= SpreadsheetLimits.maxCellLength) { "A cell contains more than 4,096 characters." }
        }
        require(!quoted) { "A quoted CSV cell is incomplete." }
        if (value.isNotEmpty() || row.isNotEmpty() || afterQuote) finishRow()
        require(rows.isNotEmpty()) { "This sheet is empty." }
        return SpreadsheetWorkbook(listOf(SpreadsheetSheet("Sheet", rows)))
    }
}
