package com.aqsama.pharmacypocket.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.aqsama.pharmacypocket.R

enum class PocketIcon(val resource: Int) {
    LIBRARY(R.drawable.ic_library), SEARCH(R.drawable.ic_search), FAVORITE(R.drawable.ic_favorite), FAVORITE_FILLED(R.drawable.ic_favorite_filled),
    OVERVIEW(R.drawable.ic_overview), ADD(R.drawable.ic_add), CAMERA(R.drawable.ic_camera),
    FILTER(R.drawable.ic_filter), COMPARE(R.drawable.ic_compare), CLOSE(R.drawable.ic_close),
    BACK(R.drawable.ic_back), EDIT(R.drawable.ic_edit), CHECK(R.drawable.ic_check),
    SETTINGS(R.drawable.ic_settings), CODE(R.drawable.ic_code), CHEVRON(R.drawable.ic_chevron),
    COPY(R.drawable.ic_copy),
}

@Composable
fun PocketIcon(icon: PocketIcon, description: String? = null, modifier: Modifier = Modifier) {
    Icon(painterResource(icon.resource), contentDescription = description, modifier = modifier)
}

@Composable
fun PocketIconButton(icon: PocketIcon, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { PocketIcon(icon, description) }
}
