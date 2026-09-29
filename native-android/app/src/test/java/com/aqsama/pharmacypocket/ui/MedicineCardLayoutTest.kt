package com.aqsama.pharmacypocket.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aqsama.pharmacypocket.data.Category
import com.aqsama.pharmacypocket.data.Medicine
import com.aqsama.pharmacypocket.data.formatPrice
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.PAUSED)
class MedicineCardLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Before fun bindMain() {
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher())
    }

    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun portraitStaysCompact() = checkCard(false, LayoutDirection.Ltr, 1f)
    @Test fun portraitStaysCompactInRtl() = checkCard(false, LayoutDirection.Rtl, 1f)
    @Test fun largeTextAndRtlKeepMediaBounded() = checkCard(true, LayoutDirection.Rtl, 1.3f)

    @Test fun landscapeStaysCompact() = checkCard(false, LayoutDirection.Ltr, 1f, 800, 80)

    private fun checkCard(large: Boolean, direction: LayoutDirection, fontScale: Float, width: Int = 80, height: Int = 800) {
        var hasPhoto by mutableStateOf(false)
        val photoResult = CompletableDeferred<ByteArray?>()
        var opens = 0
        var edits = 0
        var favorites = 0
        var captures = 0
        val item = Medicine(
            id = "layout", category = "tablets", subcategory = "General",
            name = "thyro ثيروكسين", note = "", official = 1_000_000,
            discounted = 900_000,
        )
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalLayoutDirection provides direction,
                LocalDensity provides Density(density, fontScale),
            ) {
                MaterialTheme {
                    Box(Modifier.width(328.dp).testTag("card")) { // 360dp phone minus Home margins.
                        MedicineCard(
                            item = item.copy(hasPhoto = hasPhoto),
                            category = Category("tablets", "Tablets & strips", "حبوب", "#758790"),
                            large = large, currency = "IQD", first = true, last = true,
                            onOpen = { opens++ }, onEdit = { edits++ },
                            onFavorite = { favorites++ }, onCamera = { captures++ },
                            loadPhoto = { photoResult.await() }, photoVersion = 0,
                        )
                    }
                }
            }
        }
        val withoutPhoto = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { hasPhoto = true }
        val pendingPhoto = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        // Ordinary text must stay as compact as the card without a photo.
        if (!large) assertEquals(withoutPhoto.height, pendingPhoto.height, 0.5f)

        // A 10:1 portrait used to determine the intrinsic row height.
        val portrait = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        val bytes = ByteArrayOutputStream().also { portrait.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        portrait.recycle()
        photoResult.complete(bytes)
        compose.waitUntil(10_000) {
            ShadowLooper.idleMainLooper()
            runCatching {
                compose.onNodeWithContentDescription("Package photo of ${item.name}", useUnmergedTree = true)
                    .fetchSemanticsNode()
            }.isSuccess
        }
        val loadedCard = compose.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot
        val photo = compose.onNodeWithContentDescription("Package photo of ${item.name}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(pendingPhoto.height, loadedCard.height, 0.5f)
        assertEquals(loadedCard.left, photo.left, 0.5f) // Physical left, even with RTL.
        assertEquals(loadedCard.top, photo.top, 0.5f)
        assertEquals(loadedCard.bottom, photo.bottom, 0.5f)
        assertTrue(photo.width < loadedCard.width / 3f)

        for (price in listOf(item.official, requireNotNull(item.discounted))) {
            val node = compose.onNodeWithText(formatPrice(price), useUnmergedTree = true)
            node.assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
            assertFalse("Price digits must be fully visible", layouts.single().hasVisualOverflow)
        }
        compose.onNodeWithContentDescription("Package photo of ${item.name}").performClick()
        compose.onNodeWithContentDescription("Full image of ${item.name}").assertIsDisplayed()
        compose.runOnIdle { assertEquals("Photo tap must not open medicine", 0, opens) }
        compose.onNodeWithContentDescription("Close full photo").performTouchInput { click(Offset(1f, 1f)) }
        compose.onNodeWithContentDescription("Full image of ${item.name}").assertDoesNotExist()
        compose.onNodeWithContentDescription("Package photo of ${item.name}").performClick()
        compose.runOnIdle { ShadowDialog.getLatestDialog().onBackPressed() }
        compose.onNodeWithContentDescription("Full image of ${item.name}").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit ${item.name}").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Add or replace photo or code for ${item.name}").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Add ${item.name} to favorites").assertIsDisplayed().performClick()
        compose.onNodeWithText(item.name).performClick()
        compose.runOnIdle {
            assertEquals(1, edits)
            assertEquals(1, captures)
            assertEquals(1, favorites)
            assertEquals(1, opens)
        }
    }
}
