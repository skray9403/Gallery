package org.fossify.gallery.ui.editor

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix as ComposeColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LuminaEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri == null) { finish(); return }
        setContent {
            org.fossify.gallery.ui.theme.LuminaTheme {
                LuminaEditor(uri, onBack = { finish() }, onSaved = { result ->
                    setResult(Activity.RESULT_OK, Intent().setData(result).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    finish()
                })
            }
        }
    }
}

private enum class Tool(val title: String) { LIGHT("Light"), CONTRAST("Contrast"), COLOUR("Colour"), CROP("Crop") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LuminaEditor(source: Uri, onBack: () -> Unit, onSaved: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(source) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(Tool.LIGHT) }
    var light by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var colour by remember { mutableFloatStateOf(1f) }
    var crop by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableIntStateOf(0) }
    var flip by remember { mutableStateOf(false) }

    LaunchedEffect(source) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(source)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOptions)
                }
                val maxDim = 2048
                var sampleSize = 1
                while (boundsOptions.outWidth / sampleSize > maxDim || boundsOptions.outHeight / sampleSize > maxDim) {
                    sampleSize *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(source)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                }
            }.getOrNull()
        }
        loading = false
    }

    val previewMatrix = remember(light, contrast, colour) {
        val c = contrast
        val t = (1f - c) * 128f + light * 255f
        val s = colour
        val r = 0.213f * (1f - s); val g = 0.715f * (1f - s); val b = 0.072f * (1f - s)
        ComposeColorMatrix(floatArrayOf(
            (r+s)*c,g*c,b*c,0f,t, r*c,(g+s)*c,b*c,0f,t,
            r*c,g*c,(b+s)*c,0f,t, 0f,0f,0f,1f,0f
        ))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit photo", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") } },
                actions = {
                    IconButton(enabled = bitmap != null && !saving, onClick = {
                        val original = bitmap ?: return@IconButton
                        saving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                saveImage(context, source, original, rotation, flip, light, contrast, colour, crop)
                            }
                            saving = false
                            if (result != null) onSaved(result)
                            else Toast.makeText(context, "Could not save image", Toast.LENGTH_LONG).show()
                        }
                    }) { Icon(Icons.Outlined.Check, "Save") }
                }
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(when(tool) {
                    Tool.LIGHT -> "Brightness"
                    Tool.CONTRAST -> "Contrast"
                    Tool.COLOUR -> "Saturation"
                    Tool.CROP -> "Crop"
                }, style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = when(tool) { Tool.LIGHT -> light; Tool.CONTRAST -> contrast; Tool.COLOUR -> colour; Tool.CROP -> crop },
                    onValueChange = { v -> when(tool) { Tool.LIGHT -> light=v; Tool.CONTRAST -> contrast=v; Tool.COLOUR -> colour=v; Tool.CROP -> crop=v } },
                    valueRange = when(tool) { Tool.LIGHT -> -0.5f..0.5f; Tool.CONTRAST, Tool.COLOUR -> 0f..2f; Tool.CROP -> 0f..0.3f }
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tool.entries.forEach { FilledTonalButton(onClick = { tool=it }) { Text(it.title) } }
                    FilledTonalButton(onClick = { rotation=(rotation+90)%360 }) { Icon(Icons.Outlined.RotateRight, null); Text("Rotate") }
                    FilledTonalButton(onClick = { flip=!flip }) { Text("Flip") }
                }
                TextButton(onClick = { light=0f; contrast=1f; colour=1f; crop=0f; rotation=0; flip=false }) { Text("Reset") }
            }
        }
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).background(Color.Black), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator()
                bitmap == null -> Text("Unable to open this image", color=Color.White)
                else -> Image(bitmap!!.asImageBitmap(), "Photo preview", Modifier.fillMaxSize().padding(12.dp), contentScale=ContentScale.Fit, colorFilter=ColorFilter.colorMatrix(previewMatrix))
            }
            if (saving) Surface(Modifier.align(Alignment.Center), shape=RoundedCornerShape(24.dp)) { Text("Saving…", Modifier.padding(20.dp)) }
        }
    }
}

private fun saveImage(
    context: android.content.Context, source: Uri, preview: Bitmap, rotation: Int, flip: Boolean,
    light: Float, contrast: Float, saturation: Float, crop: Float
): Uri? = runCatching {
    val original = context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) } ?: preview
    val transform = Matrix().apply { postScale(if(flip) -1f else 1f,1f); postRotate(rotation.toFloat()) }
    val rotated = Bitmap.createBitmap(original,0,0,original.width,original.height,transform,true)
    val dx=(rotated.width*crop).toInt().coerceIn(0,rotated.width/3)
    val dy=(rotated.height*crop).toInt().coerceIn(0,rotated.height/3)
    val clipped=Bitmap.createBitmap(rotated,dx,dy,(rotated.width-2*dx).coerceAtLeast(1),(rotated.height-2*dy).coerceAtLeast(1))
    val c=contrast; val offset=(1f-c)*128f+light*255f
    val s=saturation; val r=0.213f*(1f-s); val g=0.715f*(1f-s); val b=0.072f*(1f-s)
    val cm=ColorMatrix(floatArrayOf((r+s)*c,g*c,b*c,0f,offset,r*c,(g+s)*c,b*c,0f,offset,r*c,g*c,(b+s)*c,0f,offset,0f,0f,0f,1f,0f))
    val out=Bitmap.createBitmap(clipped.width,clipped.height,Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(clipped,0f,0f,Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter=ColorMatrixColorFilter(cm) })
    val values=ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME,"Lumina_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg")
        if(Build.VERSION.SDK_INT>=29) put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Lumina")
    }
    val target=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("Insert failed")
    context.contentResolver.openOutputStream(target)?.use { out.compress(Bitmap.CompressFormat.JPEG,95,it) } ?: error("Write failed")
    target
}.getOrNull()
