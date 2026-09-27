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
    @Test fun rectangleAndSquareProduceTheSelectedFraming() {
        val source = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        source.setPixel(0, 0, Color.RED)
        source.setPixel(200, 0, Color.BLUE)

        val rectangle = cropMedicineBitmap(source, square = false, horizontal = 0f, vertical = 0f)
        assertEquals(800, rectangle.width)
        assertEquals(600, rectangle.height)
        assertEquals(Color.RED, rectangle.getPixel(0, 0))

        val leftSquare = cropMedicineBitmap(source, square = true, horizontal = 0f, vertical = 0f)
        val rightSquare = cropMedicineBitmap(source, square = true, horizontal = 1f, vertical = 0f)
        assertEquals(600, leftSquare.width)
        assertEquals(600, leftSquare.height)
        assertEquals(Color.RED, leftSquare.getPixel(0, 0))
        assertEquals(Color.BLUE, leftSquare.getPixel(200, 0))
        assertEquals(Color.BLUE, rightSquare.getPixel(0, 0))
    }

    @Test fun portraitPackageUsesPortraitRectangle() {
        val source = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888)
        val cropped = cropMedicineBitmap(source, square = false, horizontal = 0.5f, vertical = 0.5f)
        assertEquals(600, cropped.width)
        assertEquals(800, cropped.height)
    }
    @Test fun photoPreparationWorksOnMinSdk29() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "api29-photo.jpg")
        val source = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        file.outputStream().use { output ->
            assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }

        val encoded = prepareMedicinePhoto(context, Uri.fromFile(file))
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size))

        assertTrue(maxOf(decoded.width, decoded.height) <= 1200)
        file.delete()
    }

}
