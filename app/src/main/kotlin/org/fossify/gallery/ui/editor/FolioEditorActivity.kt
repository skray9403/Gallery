package org.fossify.gallery.ui.editor

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.ui.theme.FolioTheme

class FolioEditorActivity : ComponentActivity() {
    private var sourceUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sourceUri = intent.data ?: intent.getParcelableExtra(Intent.EXTRA_STREAM)
        val uri = sourceUri
        if (uri == null) {
            finish()
            return
        }
        setContent {
            FolioTheme {
                FolioEditorScreen(
                    uri = uri,
                    onBack = { finish() },
                    onSave = { view, onBusy ->
                        saveEditedImage(uri, view, onBusy)
                    }
                )
            }
        }
    }

    private fun saveEditedImage(
        uri: Uri,
        view: CropImageView?,
        onBusy: (Boolean) -> Unit
    ) {
        if (view == null) return
        onBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { view.getCroppedImage() }
            }
            val bitmap = result.getOrNull()
            if (bitmap == null) {
                onBusy(false)
                Toast.makeText(this@FolioEditorActivity, "Could not crop this image", Toast.LENGTH_LONG).show()
                return@launch
            }
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        val format = when (contentResolver.getType(uri)?.lowercase()) {
                            "image/png" -> Bitmap.CompressFormat.PNG
                            "image/webp" -> Bitmap.CompressFormat.WEBP
                            else -> Bitmap.CompressFormat.JPEG
                        }
                        bitmap.compress(format, 95, output)
                    } ?: error("The selected image cannot be overwritten")
                }
            }
            onBusy(false)
            bitmap.recycle()
            if (saved.isSuccess) {
                Toast.makeText(this@FolioEditorActivity, "Image saved", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK, Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                finish()
            } else {
                Toast.makeText(this@FolioEditorActivity, saved.exceptionOrNull()?.message ?: "Could not save image", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolioEditorScreen(
    uri: Uri,
    onBack: () -> Unit,
    onSave: (CropImageView?, (Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var cropView by remember(uri) { mutableStateOf<CropImageView?>(null) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit photo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !saving,
                        onClick = { onSave(cropView) { saving = it } }
                    ) {
                        Icon(Icons.Outlined.Save, contentDescription = "Save")
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        enabled = !saving,
                        onClick = { cropView?.rotateImage(90) }
                    ) {
                        Icon(Icons.Outlined.RotateRight, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Rotate")
                    }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        enabled = !saving,
                        onClick = { cropView?.flipImageHorizontally() }
                    ) {
                        Icon(Icons.Outlined.Flip, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Flip")
                    }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        enabled = !saving,
                        onClick = { cropView?.flipImageVertically() }
                    ) {
                        Icon(Icons.Outlined.FlipCameraAndroid, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Vertical")
                    }
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    onClick = { onSave(cropView) { saving = it } }
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(10.dp))
                        Text("Saving")
                    } else {
                        Icon(Icons.Outlined.Save, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Text("Save changes")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    CropImageView(viewContext).apply {
                        guidelines = CropImageView.Guidelines.ON
                        setImageUriAsync(uri)
                        cropView = this
                    }
                },
                update = { cropView = it }
            )
        }
    }
}
