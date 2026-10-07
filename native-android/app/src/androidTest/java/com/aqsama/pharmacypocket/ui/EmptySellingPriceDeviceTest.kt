package com.aqsama.pharmacypocket.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aqsama.pharmacypocket.MainActivity
import com.aqsama.pharmacypocket.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the actual app/activity and Android IME, not Robolectric shadows. */
@RunWith(AndroidJUnit4::class)
class EmptySellingPriceDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    @Before fun seed() {
        runBlocking {
            val repository = PharmacyRepository(context)
            try {
                repository.importBackup(ParsedBackup(
                    medicines = listOf(Medicine("empty", "tablets", "General", "Empty price test", "", official = null, discounted = null)),
                    sections = emptyList(), categories = emptyList(), currency = "IQD", hasCurrency = true, sourceVersion = 3), ImportMode.REPLACE)
            } finally { repository.close() }
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("empty-selling-price").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun tapAndVerifyKeyboard() {
        compose.onNodeWithTag("empty-selling-price").performTouchInput { click() }
        compose.onNodeWithTag("selling-price-input").assertIsFocused()
        compose.waitUntil(10_000) {
            val state = instrumentation.uiAutomation.executeShellCommand("dumpsys input_method").use { descriptor ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText()
            }
            (state.contains("mInputShown=true") || state.contains("mIsInputViewShown=true")) &&
                Regex("inputType=0x[0-9a-fA-F]*2\\b").containsMatchIn(state)
        }
    }

    @Test fun tapFocusNumericKeyboardCancelSaveAndRoundTrip() {
        tapAndVerifyKeyboard()
        compose.onNodeWithTag("selling-price-input").performTextInput("900")
        compose.onNodeWithTag("selling-price-cancel").performTouchInput { click() }
        compose.onNodeWithTag("empty-selling-price").assertIsDisplayed()
        val repository = PharmacyRepository(context)
        try {
            assertNull(runBlocking { repository.loadSnapshot() }.items.single().official)
            tapAndVerifyKeyboard()
            compose.onNodeWithTag("selling-price-input").assertTextContains("")
            compose.onNodeWithTag("selling-price-input").performTextInput("1250")
            compose.onNodeWithTag("selling-price-save").performTouchInput { click() }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("1,250").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("1,250").assertIsDisplayed()
            assertEquals(1250L, runBlocking { repository.loadSnapshot() }.items.single().official)
            val parsed = BackupCodec.parse(runBlocking { repository.exportBackup() })
            assertEquals(1250L, parsed.medicines.single().official)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("1,250").fetchSemanticsNodes().isNotEmpty() }
        } finally { repository.close() }
    }

    @Test fun detailPlaceholderAlsoOpensFocusedKeyboardAndZeroIsSaved() {
        compose.onNodeWithText("Empty price test").performTouchInput { click() }
        tapAndVerifyKeyboard()
        compose.onNodeWithTag("selling-price-input").performTextInput("0")
        compose.onNodeWithTag("selling-price-save").performTouchInput { click() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("0").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("empty-selling-price").assertDoesNotExist()
        val repository = PharmacyRepository(context)
        try { assertEquals(0L, runBlocking { repository.loadSnapshot() }.items.single().official) }
        finally { repository.close() }
    }
}
