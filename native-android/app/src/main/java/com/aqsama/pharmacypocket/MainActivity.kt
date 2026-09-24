package com.aqsama.pharmacypocket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aqsama.pharmacypocket.data.PharmacyRepository
import com.aqsama.pharmacypocket.ui.PharmacyApp
import com.aqsama.pharmacypocket.ui.PharmacyPocketTheme

class MainActivity : ComponentActivity() {
    private val repository by lazy { PharmacyRepository(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PharmacyPocketTheme {
                PharmacyApp(repository)
            }
        }
    }
}
