package org.fossify.gallery.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Compose entry point for Folio.
 *
 * The old activities remain registered for media intents and for reference while
 * their behaviour is migrated feature-by-feature into Compose.
 */
class FolioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FolioApp()
        }
    }
}
