package com.aqsama.pharmacypocket.ui

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
}
