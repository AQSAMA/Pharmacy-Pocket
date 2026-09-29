package com.aqsama.pharmacypocket.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MedicinePhotoCropTest {
    @Test fun photoPreparationDecodesResizesAndReencodesOnMinSdk() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "api29-photo.jpg")
        val source = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        file.outputStream().use { output ->
            assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }

        try {
            val encoded = prepareMedicinePhoto(context, Uri.fromFile(file))
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size))

            assertTrue(decoded.width <= 1200)
            assertTrue(decoded.height <= 1200)
            assertEquals(decoded.width, decoded.height * 2)
            assertTrue(encoded.size <= MAX_MEDICINE_PHOTO_BYTES)
        } finally {
            file.delete()
        }
    }

    @Test fun finalEncoderKeepsLargeCropWithinMedicineStorageBudget() {
        val source = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(42, 117, 83))
        }

        val encoded = encodeMedicineBitmap(source)
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size))

        assertTrue(encoded.isNotEmpty())
        assertTrue(encoded.size <= MAX_MEDICINE_PHOTO_BYTES)
        assertTrue(decoded.width > 0)
        assertTrue(decoded.height > 0)
    }
}
