package com.aqsama.pharmacypocket.ui

import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.util.UUID

internal const val MAX_MEDICINE_PHOTO_BYTES = 256_000
internal const val MAX_MEDICINE_CROP_SOURCE_BYTES = 64L * 1024L * 1024L

private fun medicineDraftDirectory(context: Context): File =
    File(context.noBackupFilesDir, "medicine_drafts")

private fun medicineCropSourceDirectory(context: Context): File =
    File(context.noBackupFilesDir, "medicine_crop_sources")

internal fun createMedicinePhotoCropSource(context: Context): File {
    val directory = medicineCropSourceDirectory(context)
    check(directory.isDirectory || directory.mkdirs()) {
        "Could not prepare photo crop storage."
    }
    return File(directory, "source-${UUID.randomUUID()}.image")
}

internal fun writeMedicinePhotoDraft(context: Context, bytes: ByteArray): String {
    require(bytes.size in 1..MAX_MEDICINE_PHOTO_BYTES) {
        "The photo must be 256 KB or less."
    }

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0) {
        "The selected photo could not be decoded."
    }

    val directory = medicineDraftDirectory(context)
    check(directory.isDirectory || directory.mkdirs()) {
        "Could not prepare photo storage."
    }

    val draft = File(directory, "draft-${UUID.randomUUID()}.jpg")
    try {
        draft.writeBytes(bytes)
        check(draft.isFile && draft.length() == bytes.size.toLong()) {
            "Could not save the photo draft."
        }
        return draft.absolutePath
    } catch (error: Throwable) {
        draft.delete()
        throw error
    }
}

internal fun readMedicinePhotoDraft(path: String?): ByteArray? {
    if (path == null) return null
    val file = File(path)
    if (!file.isFile || file.length() !in 1L..MAX_MEDICINE_PHOTO_BYTES.toLong()) return null
    return runCatching { file.readBytes() }.getOrNull()
}

internal fun deleteMedicinePhotoDraft(path: String?) {
    if (path == null) return
    runCatching { File(path).delete() }
}

internal fun cleanupMedicinePhotoDrafts(
    context: Context,
    retainedPaths: Set<String>,
    staleBefore: Long = System.currentTimeMillis() - 24L * 60L * 60L * 1000L,
) {
    listOf(medicineDraftDirectory(context), medicineCropSourceDirectory(context)).forEach { directory ->
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in retainedPaths && file.lastModified() < staleBefore) {
                file.delete()
            }
        }
    }
}
