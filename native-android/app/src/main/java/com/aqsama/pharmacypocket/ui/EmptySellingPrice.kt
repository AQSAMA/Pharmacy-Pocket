package com.aqsama.pharmacypocket.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Each placeholder owns its dialog; cancelling never writes a draft to storage. */
@Composable
internal fun EmptySellingPrice(currency: String, onSave: suspend (Long) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier.testTag("empty-selling-price")) {
        Text("Add price")
    }
    if (open) SellingPriceDialog(currency, onSave, onDismiss = { open = false })
}

@Composable
internal fun SellingPriceDialog(currency: String, onSave: suspend (Long) -> Unit, onDismiss: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val value = input.trim().toLongOrNull()?.takeIf { it in 0..9_007_199_254_740_991L }
    fun save() {
        val price = value ?: return
        if (saving) return
        saving = true
        scope.launch {
            try {
                onSave(price)
                keyboard?.hide()
                onDismiss()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Could not save price."
            } finally { saving = false }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Selling price") },
        text = {
            OutlinedTextField(
                value = input, onValueChange = { input = it; error = null },
                label = { Text("Price") }, suffix = { Text(currency) }, singleLine = true,
                enabled = !saving,
                isError = error != null || input.isNotBlank() && value == null,
                supportingText = { if (error != null) Text(error!!) else Text("Nonnegative whole number; zero is a price.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("selling-price-input"),
            )
            LaunchedEffect(Unit) {
                // Run after the dialog's field has been attached to its own window.
                withFrameNanos { }
                focus.requestFocus()
                keyboard?.show()
            }
        },
        confirmButton = { TextButton(onClick = { save() }, enabled = value != null && !saving, modifier = Modifier.testTag("selling-price-save")) { Text(if (saving) "Saving…" else "Save") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.testTag("selling-price-cancel")) { Text("Cancel") } },
    )
}
