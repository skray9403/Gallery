package org.fossify.gallery.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Compose entry point for Folio and Android's image/video picker intents.
 *
 * The old media activities remain as compatibility services while their
 * behavior is migrated; normal launch and picker flows now enter Compose.
 */
class FolioActivity : ComponentActivity() {
    private var launchRequest by mutableStateOf(FolioLaunchRequest())
    private var launchRevision = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updateLaunchRequest(intent)

        setContent {
            val request = launchRequest
            FolioApp(
                pickerMode = request.pickerMode,
                pickerMimeType = request.mimeType,
                initialUri = request.initialUri,
                launchRevision = request.revision,
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateLaunchRequest(intent)
    }

    private fun updateLaunchRequest(intent: Intent?) {
        launchRevision += 1
        val action = intent?.action
        val pickerMode = action == Intent.ACTION_PICK || action == Intent.ACTION_GET_CONTENT
        launchRequest = FolioLaunchRequest(
            pickerMode = pickerMode,
            mimeType = if (pickerMode) intent?.type else null,
            initialUri = intent?.data.takeIf { action == Intent.ACTION_VIEW },
            revision = launchRevision
        )
    }
}

private data class FolioLaunchRequest(
    val pickerMode: Boolean = false,
    val mimeType: String? = null,
    val initialUri: Uri? = null,
    val revision: Int = 0
)
