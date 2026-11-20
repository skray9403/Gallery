package org.fossify.gallery.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.davemorrissey.labs.subscaleview.DecoderFactory
import com.davemorrissey.labs.subscaleview.ImageDecoder
import com.davemorrissey.labs.subscaleview.ImageRegionDecoder
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fossify.gallery.helpers.MyGlideImageDecoder
import org.fossify.gallery.helpers.PicassoRegionDecoder
import org.fossify.gallery.ui.data.FolioMediaItem
import org.fossify.gallery.ui.theme.FolioMotion

@Composable
internal fun FolioViewer(
    item: FolioMediaItem,
    backProgress: Float,
    sourceRect: Rect? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val openAnim = remember(item.uri) { Animatable(0f) }
    var mediaReady by remember(item.uri) { mutableStateOf(false) }

    LaunchedEffect(item.uri) {
        openAnim.snapTo(0f)
        openAnim.animateTo(1f, FolioMotion.gentle())
        mediaReady = true
    }

    val effectiveProgress = (openAnim.value * (1f - backProgress.coerceIn(0f, 1f))).coerceIn(0f, 1f)

    // Compute source-to-fullscreen transform (Google Photos style)
    val startScale = if (sourceRect != null && screenWidthPx > 0f && screenHeightPx > 0f) {
        maxOf(sourceRect.width / screenWidthPx, sourceRect.height / screenHeightPx).coerceIn(0.08f, 1f)
    } else 0.85f

    val startTransX = if (sourceRect != null) {
        sourceRect.center.x - (screenWidthPx / 2f)
    } else 0f

    val startTransY = if (sourceRect != null) {
        sourceRect.center.y - (screenHeightPx / 2f)
    } else 0f

    val currentScale = lerp(startScale, 1f, effectiveProgress)
    val currentTransX = lerp(startTransX, 0f, effectiveProgress)
    val currentTransY = lerp(startTransY, 0f, effectiveProgress)
    val currentCorner = androidx.compose.ui.unit.lerp(16.dp, 0.dp, effectiveProgress)

    Surface(modifier = modifier, color = Color.Black.copy(alpha = effectiveProgress)) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = currentScale
                        scaleY = currentScale
                        translationX = currentTransX
                        translationY = currentTransY
                        alpha = lerp(0.6f, 1f, effectiveProgress)
                        clip = true
                        shape = RoundedCornerShape(currentCorner)
                    }
            ) {
                when {
                    item.isVideo -> {
                        val player = remember(item.uri) { ExoPlayer.Builder(context).build() }
                        DisposableEffect(player, item.uri) {
                            val listener = object : Player.Listener {
                                override fun onRenderedFirstFrame() { mediaReady = true }
                                override fun onPlayerError(error: PlaybackException) { mediaReady = true }
                            }
                            player.addListener(listener)
                            player.setMediaItem(PlayerMediaItem.fromUri(item.uri))
                            player.prepare()
                            player.playWhenReady = true
                            onDispose {
                                player.removeListener(listener)
                                player.release()
                            }
                        }
                        var playing by remember(item.uri) { mutableStateOf(true) }
                        var position by remember(item.uri) { mutableFloatStateOf(0f) }
                        var duration by remember(item.uri) { mutableFloatStateOf(0f) }
                        LaunchedEffect(player) {
                            while (true) {
                                position = player.currentPosition.coerceAtLeast(0L).toFloat()
                                duration = player.duration.takeIf { it > 0L }?.toFloat() ?: 0f
                                playing = player.isPlaying
                                delay(300)
                            }
                        }
                        Box(Modifier.fillMaxSize()) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { PlayerView(it).apply {
                                    this.player = player
                                    useController = false
                                } },
                                update = {
                                    it.player = player
                                    it.useController = false
                                }
                            )
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(androidx.compose.ui.Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.58f))
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                androidx.compose.material3.Slider(
                                    value = position.coerceIn(0f, duration.coerceAtLeast(1f)),
                                    onValueChange = { next ->
                                        position = next
                                        player.seekTo(next.toLong())
                                    },
                                    valueRange = 0f..duration.coerceAtLeast(1f)
                                )
                                androidx.compose.foundation.layout.Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.IconButton(onClick = {
                                        if (player.isPlaying) player.pause() else player.play()
                                        playing = player.isPlaying
                                    }) {
                                        androidx.compose.material3.Text(
                                            text = if (playing) "Ⅱ" else "▶",
                                            color = Color.White,
                                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                                        )
                                    }
                                    androidx.compose.material3.Text(
                                        text = "${formatPlayerTime(position.toLong())} / ${formatPlayerTime(duration.toLong())}",
                                        color = Color.White,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }

                    item.supportsRegionZoom() -> {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                FolioZoomableImageFrame(viewContext).apply { bind(item) { mediaReady = true } }
                            },
                            update = { view -> view.bind(item) { mediaReady = true } },
                            onRelease = { view -> view.dispose() }
                        )
                    }

                    else -> {
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
                            factory = {
                                ImageView(it).apply {
                                    scaleType = ImageView.ScaleType.FIT_CENTER
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                }
                            },
                            update = { view ->
                                Glide.with(view)
                                    .load(item.uri)
                                    .signature(ObjectKey("${item.dateAddedMillis}-${item.sizeBytes}"))
                                    .fitCenter()
                                    .listener(object : RequestListener<Drawable> {
                                        override fun onLoadFailed(
                                            e: GlideException?, model: Any?, target: Target<Drawable>?,
                                            isFirstResource: Boolean
                                        ): Boolean {
                                            mediaReady = true
                                            return false
                                        }

                                        override fun onResourceReady(
                                            resource: Drawable, model: Any, target: Target<Drawable>?,
                                            dataSource: DataSource, isFirstResource: Boolean
                                        ): Boolean {
                                            mediaReady = true
                                            return false
                                        }
                                    })
                                    .into(view)
                            }
                        )
                    }
                }
            }

            // Top Bar with Back navigation & metadata (Google Photos style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .graphicsLayer { alpha = effectiveProgress },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.52f),
                    contentColor = Color.White,
                    tonalElevation = 2.dp
                ) {
                    IconButton(onClick = {
                        scope.launch {
                            openAnim.animateTo(0f, FolioMotion.gentle())
                            onBack()
                        }
                    }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.dateAddedMillis > 0L) {
                        val dateLabel = remember(item.dateAddedMillis) {
                            runCatching {
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                    .withLocale(Locale.getDefault())
                                    .format(Instant.ofEpochMilli(item.dateAddedMillis).atZone(ZoneId.systemDefault()).toLocalDate())
                            }.getOrNull()
                        }
                        if (dateLabel != null) {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
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

    fun bind(item: FolioMediaItem, onPreviewReady: () -> Unit) {
        if (boundUri == item.uri) return
        boundUri = item.uri

        preview.visibility = View.VISIBLE
        Glide.with(preview)
            .load(item.uri)
            .signature(ObjectKey("${item.uri}-${item.dateAddedMillis}-${item.sizeBytes}"))
            .fitCenter()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: Target<Drawable>?,
                    isFirstResource: Boolean
                ): Boolean {
                    onPreviewReady()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?,
                    dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    onPreviewReady()
                    return false
                }
            })
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


private fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
