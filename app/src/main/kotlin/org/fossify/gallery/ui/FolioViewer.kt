package org.fossify.gallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import com.davemorrissey.labs.subscaleview.DecoderFactory
import com.davemorrissey.labs.subscaleview.ImageDecoder
import com.davemorrissey.labs.subscaleview.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import org.fossify.gallery.helpers.MyGlideImageDecoder
import org.fossify.gallery.helpers.PicassoRegionDecoder
import org.fossify.gallery.ui.data.FolioMediaItem

@Composable
internal fun FolioViewer(
    item: FolioMediaItem,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(Modifier.fillMaxSize()) {
            if (item.isVideo) {
                val player = remember(item.uri) { ExoPlayer.Builder(context).build() }
                DisposableEffect(player, item.uri) {
                    player.setMediaItem(PlayerMediaItem.fromUri(item.uri))
                    player.prepare()
                    player.playWhenReady = true
                    onDispose { player.release() }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { PlayerView(it).apply { this.player = player } },
                    update = { it.player = player }
                )
            } else if (item.supportsRegionZoom()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        FolioZoomableImageFrame(viewContext).apply { bind(item) }
                    },
                    update = { view -> view.bind(item) },
                    onRelease = { view -> view.dispose() }
                )
            } else {
                var scale by remember(item.uri) { mutableFloatStateOf(1f) }
                var offset by remember(item.uri) { mutableStateOf(Offset.Zero) }
                val transformState = rememberTransformableState { zoom, pan, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(transformState)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    factory = { ImageView(it).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setBackgroundColor(android.graphics.Color.BLACK)
                    } },
                    update = { view ->
                        Glide.with(view)
                            .load(item.uri)
                            .signature(ObjectKey("${item.dateAddedMillis}-${item.sizeBytes}"))
                            .fitCenter()
                            .into(view)
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = Color.White)
                }
                Row {
                    IconButton(onClick = {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = item.mimeType
                            putExtra(Intent.EXTRA_STREAM, item.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Share media"))
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Color.White)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = Color.White)
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}


/**
 * Full-resolution tiled viewer for formats handled reliably by Android's
 * BitmapRegionDecoder. Glide keeps a fast preview visible until the first
 * high-resolution tiles are ready; unsupported formats stay on the Compose
 * viewer's animated/decoder-aware fallback path.
 */
private class FolioZoomableImageFrame(context: Context) : FrameLayout(context) {
    private val preview = ImageView(context)
    private val zoomView = SubsamplingScaleImageView(context)
    private var boundUri: Uri? = null

    init {
        clipChildren = true
        clipToPadding = true
        preview.scaleType = ImageView.ScaleType.FIT_CENTER
        preview.setBackgroundColor(android.graphics.Color.BLACK)
        addView(
            preview,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        zoomView.visibility = View.INVISIBLE
        addView(
            zoomView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun bind(item: FolioMediaItem) {
        if (boundUri == item.uri) return
        boundUri = item.uri

        preview.visibility = View.VISIBLE
        Glide.with(preview)
            .load(item.uri)
            .signature(ObjectKey("${item.uri}-${item.dateAddedMillis}-${item.sizeBytes}"))
            .fitCenter()
            .into(preview)

        val signature = ObjectKey("${item.uri}-${item.dateAddedMillis}-${item.sizeBytes}")
        val metrics = resources.displayMetrics
        zoomView.apply {
            setMaxTileSize(4096)
            setMinimumTileDpi(metrics.densityDpi)
            maxScale = 10f
            bitmapDecoderFactory = object : DecoderFactory<ImageDecoder> {
                override fun make(): ImageDecoder = MyGlideImageDecoder(0, signature)
            }
            regionDecoderFactory = object : DecoderFactory<ImageRegionDecoder> {
                override fun make(): ImageRegionDecoder = PicassoRegionDecoder(
                    showHighestQuality = false,
                    screenWidth = metrics.widthPixels,
                    screenHeight = metrics.heightPixels,
                    minTileDpi = metrics.densityDpi
                )
            }
            onImageEventListener = object : SubsamplingScaleImageView.OnImageEventListener {
                override fun onReady() {
                    zoomView.visibility = View.VISIBLE
                    preview.visibility = View.GONE
                }

                override fun onImageLoadError(e: Exception) {
                    zoomView.visibility = View.GONE
                    preview.visibility = View.VISIBLE
                }

                override fun onImageRotation(degrees: Int) = Unit

                override fun onUpEvent() = Unit
            }
            setImage(item.uri.toString())
        }
    }

    fun dispose() {
        Glide.with(preview).clear(preview)
        runCatching { zoomView.recycle() }
    }
}

private fun FolioMediaItem.supportsRegionZoom(): Boolean {
    val name = displayName.lowercase()
    return mimeType.equals("image/jpeg", ignoreCase = true) ||
        mimeType.equals("image/png", ignoreCase = true) ||
        name.endsWith(".jpg") ||
        name.endsWith(".jpeg") ||
        name.endsWith(".png")
}
