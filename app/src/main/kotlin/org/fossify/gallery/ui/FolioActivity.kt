package org.fossify.gallery.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Compose entry point for Folio and Android's image/video picker intents.
 *
 * The old media activities remain as compatibility services while their
 * behavior is migrated; normal launch and picker flows now enter Compose.
 */
class FolioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val incomingAction = intent?.action
        val isPickerMode = incomingAction == Intent.ACTION_PICK ||
            incomingAction == Intent.ACTION_GET_CONTENT
        val isViewAction = incomingAction == Intent.ACTION_VIEW
        val initialUri = intent?.data.takeIf { isViewAction }
        val requestedMimeType = intent?.type

        setContent {
            FolioApp(
                pickerMode = isPickerMode,
                pickerMimeType = requestedMimeType,
                initialUri = initialUri,
                onMediaPicked = { uri ->
                    setResult(
                        Activity.RESULT_OK,
                        Intent().apply {
                            data = uri
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    )
                    finish()
                }
            )
        }
    }
}
