package com.aqsama.pharmacypocket.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import androidx.core.content.FileProvider
import org.robolectric.util.ReflectionHelpers
import org.robolectric.shadows.ShadowLog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34])
@LooperMode(LooperMode.Mode.PAUSED)
class MedicinePhotoCropTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context

    @Before fun setup() {
        // Dispatchers.Main is cached across Robolectric sandboxes; bind the cropper callbacks
        // to this test's looper instead of the first test's now-inactive looper.
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher())
        context = ApplicationProvider.getApplicationContext()
        // FileProvider caches absolute roots by authority, while Robolectric gives every
        // test a new data directory under the same application authority.
        ReflectionHelpers.getStaticField<MutableMap<String, Any>>(
            FileProvider::class.java, "sCache",
        ).clear()
        ShadowLog.stream = System.out
    }

    @After fun resetMainDispatcher() {
        Dispatchers.resetMain()
        ShadowLog.stream = null
    }

    @Test fun photoPreparationDecodesResizesAndReencodesOnMinSdkCompatiblePath() {
        val file = sourceFile("api29-photo.jpg", width = 1600, height = 800)

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

    @Test fun cropUiUsesOriginalSourceResolutionInsteadOf1200pxPreview() {
        val file = sourceFile("full-resolution-crop.jpg", width = 2400, height = 1200)
        try {
            val result = saveThroughCropUi(file)
            // CropImageView is asked for up to 1600px. A 1200px predecode would cap this at 1200.
            assertTrue("Expected crop to retain source detail, got ${result.width}px", result.width > 1200)
            assertTrue(result.width > result.height)
        } finally {
            file.delete()
        }
    }

    @Test fun squareControlProducesSquareResult() {
        val file = sourceFile("square-control.jpg", width = 1200, height = 600)
        try {
            val result = saveThroughCropUi(file) {
                compose.onNodeWithText("Square").performClick()
            }
            assertEquals(result.width, result.height)
        } finally {
            file.delete()
        }
    }

    @Test fun rectangleControlKeepsLandscapeFraming() {
        val file = sourceFile("rectangle-control.jpg", width = 1200, height = 600)
        try {
            val result = saveThroughCropUi(file) {
                compose.onNodeWithText("Rectangle").performClick()
            }
            assertTrue("Rectangle crop should remain landscape", result.width > result.height)
        } finally {
            file.delete()
        }
    }

    @Test fun rotateControlProducesRotatedPortraitResult() {
        val file = sourceFile("rotate-control.jpg", width = 1200, height = 600)
        try {
            val result = saveThroughCropUi(file) {
                compose.onNodeWithText("Rotate ↷").performClick()
            }
            assertTrue("90-degree rotation should produce a portrait crop", result.height > result.width)
        } finally {
            file.delete()
        }
    }

    private fun saveThroughCropUi(
        file: File,
        beforeSave: () -> Unit = {},
    ): Bitmap {
        var accepted: ByteArray? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalMedicineCropBitmapLoader provides { source ->
                    BitmapFactory.decodeFile(source.absolutePath)
                },
            ) {
                MedicinePhotoCrop(
                    file = file,
                    saving = false,
                    onAccept = { accepted = it },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        waitUntilWithAndroidMain {
            runCatching {
                compose.onNodeWithText("Save photo").assertIsEnabled()
                true
            }.getOrDefault(false)
        }
        beforeSave()
        compose.onNodeWithText("Save photo").assertIsEnabled().performClick()
        waitUntilWithAndroidMain { accepted != null }

        return requireNotNull(
            BitmapFactory.decodeByteArray(requireNotNull(accepted), 0, requireNotNull(accepted).size),
        )
    }

    private fun waitUntilWithAndroidMain(
        timeoutMillis: Long = 10_000,
        condition: () -> Boolean,
    ) {
        compose.waitUntil(timeoutMillis) {
            ShadowLooper.idleMainLooper()
            condition()
        }
    }

    private fun sourceFile(name: String, width: Int, height: Int): File {
        val file = File(context.cacheDir, name)
        val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(37, 111, 182))
        }
        file.outputStream().use { output ->
            assertTrue(source.compress(Bitmap.CompressFormat.JPEG, 96, output))
        }
        source.recycle()
        return file
    }
}
