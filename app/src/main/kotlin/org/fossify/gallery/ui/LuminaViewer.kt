package org.fossify.gallery.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
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
import org.fossify.gallery.ui.data.LuminaMediaItem
import org.fossify.gallery.ui.details.LuminaDetailsBottomSheet
import org.fossify.gallery.ui.theme.LuminaMotion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@SuppressLint("NonObservableLocale")
@Composable
internal fun LuminaViewer(
    items: List<LuminaMediaItem>,
    initialMedia: LuminaMediaItem,
    backProgress: Float,
    sourceRect: Rect? = null,
    onBack: () -> Unit = {},
    onMediaChanged: (LuminaMediaItem) -> Unit = {},
    isFavorite: (LuminaMediaItem) -> Boolean = { false },
    onToggleFavorite: (LuminaMediaItem) -> Unit = {},
    onSetWallpaper: (LuminaMediaItem) -> Unit = {},
    onOpenWith: (LuminaMediaItem) -> Unit = {},
    onUseAs: (LuminaMediaItem) -> Unit = {},
    isChromeVisible: Boolean = true,
    onToggleChrome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val initialIndex = remember(items, initialMedia.uri) {
        val idx = items.indexOfFirst { it.uri == initialMedia.uri }
        if (idx >= 0) idx else 0
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { items.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        if (items.isNotEmpty() && pagerState.currentPage in items.indices) {
            val current = items[pagerState.currentPage]
            if (current.uri != initialMedia.uri) {
                onMediaChanged(current)
            }
        }
    }

    val activeItem = items.getOrElse(pagerState.currentPage) { initialMedia }

    val openAnim = remember(initialMedia.uri) { Animatable(0f) }
    var mediaReady by remember(initialMedia.uri) { mutableStateOf(false) }
    var showDetailsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(initialMedia.uri) {
        openAnim.snapTo(0f)
        openAnim.animateTo(1f, LuminaMotion.gentle())
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

    val chromeAlpha by animateFloatAsState(
        targetValue = if (isChromeVisible) 1f else 0f,
        animationSpec = LuminaMotion.gentle(),
        label = "viewerChromeAlpha"
    )
    val chromeOffsetY by animateDpAsState(
        targetValue = if (isChromeVisible) 0.dp else (-80).dp,
        animationSpec = LuminaMotion.gentle(),
        label = "viewerChromeOffsetY"
    )

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
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { items[it].uri.toString() }
                ) { page ->
                    val pageItem = items[page]
                    val isCurrentPage = pagerState.currentPage == page

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onToggleChrome()
                            }
                    ) {
                        when {
                            pageItem.isVideo -> {
                                val player = remember(pageItem.uri) { ExoPlayer.Builder(context).build() }
                                DisposableEffect(player, pageItem.uri) {
                                    val listener = object : Player.Listener {
                                        override fun onRenderedFirstFrame() { mediaReady = true }
                                        override fun onPlayerError(error: PlaybackException) { mediaReady = true }
                                    }
                                    player.addListener(listener)
                                    player.setMediaItem(PlayerMediaItem.fromUri(pageItem.uri))
                                    player.prepare()
                                    player.playWhenReady = isCurrentPage
                                    onDispose {
                                        player.removeListener(listener)
                                        player.release()
                                    }
                                }

                                LaunchedEffect(isCurrentPage) {
                                    if (isCurrentPage) {
                                        player.play()
                                    } else {
                                        player.pause()
                                    }
                                }

                                var playing by remember(pageItem.uri) { mutableStateOf(isCurrentPage) }
                                var position by remember(pageItem.uri) { mutableFloatStateOf(0f) }
                                var duration by remember(pageItem.uri) { mutableFloatStateOf(0f) }
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
                                            setOnClickListener { onToggleChrome() }
                                        } },
                                        update = {
                                            it.player = player
                                            it.useController = false
                                        }
                                    )
                                    AnimatedVisibility(
                                        visible = isChromeVisible,
                                        enter = fadeIn(LuminaMotion.contentEnter),
                                        exit = fadeOut(LuminaMotion.contentExit),
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.Black.copy(alpha = 0.58f))
                                                .padding(horizontal = 20.dp, vertical = 12.dp)
                                                .padding(bottom = 76.dp)
                                        ) {
                                            Slider(
                                                value = position.coerceIn(0f, duration.coerceAtLeast(1f)),
                                                onValueChange = { next ->
                                                    position = next
                                                    player.seekTo(next.toLong())
                                                },
                                                valueRange = 0f..duration.coerceAtLeast(1f)
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(onClick = {
                                                    if (player.isPlaying) player.pause() else player.play()
                                                    playing = player.isPlaying
                                                }) {
                                                    Text(
                                                        text = if (playing) "Ⅱ" else "▶",
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.titleMedium
                                                    )
                                                }
                                                Text(
                                                    text = "${formatPlayerTime(position.toLong())} / ${formatPlayerTime(duration.toLong())}",
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            pageItem.supportsRegionZoom() -> {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { viewContext ->
                                        LuminaZoomableImageFrame(viewContext).apply {
                                            bind(pageItem, onTap = onToggleChrome) { mediaReady = true }
                                        }
                                    },
                                    update = { view -> view.bind(pageItem, onTap = onToggleChrome) { mediaReady = true } },
                                    onRelease = { view -> view.dispose() }
                                )
                            }

                            else -> {
                                var scale by remember(pageItem.uri) { mutableFloatStateOf(1f) }
                                var offset by remember(pageItem.uri) { mutableStateOf(Offset.Zero) }
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
                                            setOnClickListener { onToggleChrome() }
                                        }
                                    },
                                    update = { view ->
                                        Glide.with(view)
                                            .load(pageItem.uri)
                                            .signature(ObjectKey("${pageItem.dateAddedMillis}-${pageItem.sizeBytes}"))
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
                }
            }

            // Top Bar with Back navigation, metadata, and overflow actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .offset { IntOffset(0, chromeOffsetY.roundToPx()) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .graphicsLayer { alpha = effectiveProgress * chromeAlpha },
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
                            openAnim.animateTo(0f, LuminaMotion.gentle())
                            onBack()
                        }
                    }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = activeItem.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (activeItem.dateAddedMillis > 0L) {
                        val dateLabel = remember(activeItem.dateAddedMillis, locale) {
                            runCatching {
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                    .withLocale(locale)
                                    .format(Instant.ofEpochMilli(activeItem.dateAddedMillis).atZone(ZoneId.systemDefault()).toLocalDate())
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

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.52f),
                    contentColor = Color.White,
                    tonalElevation = 2.dp
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val favored = isFavorite(activeItem)
                        IconButton(onClick = { onToggleFavorite(activeItem) }) {
                            Icon(
                                imageVector = if (favored) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (favored) "Remove from favorites" else "Add to favorites",
                                tint = if (favored) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        IconButton(onClick = { showDetailsSheet = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = "Details")
                        }
                        Box {
                            var menuExpanded by remember { mutableStateOf(false) }
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                if (!activeItem.isVideo) {
                                    DropdownMenuItem(
                                        text = { Text("Set as wallpaper") },
                                        onClick = {
                                            menuExpanded = false
                                            onSetWallpaper(activeItem)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Open with") },
                                    onClick = {
                                        menuExpanded = false
                                        onOpenWith(activeItem)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Use as") },
                                    onClick = {
                                        menuExpanded = false
                                        onUseAs(activeItem)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDetailsSheet) {
        LuminaDetailsBottomSheet(
            item = activeItem,
            onDismissRequest = { showDetailsSheet = false }
        )
    }
}

/**
 * Full-resolution tiled viewer for formats handled reliably by Android's
 * BitmapRegionDecoder. Glide keeps a fast preview visible until the first
 * high-resolution tiles are ready; unsupported formats stay on the Compose
 * viewer's animated/decoder-aware fallback path.
 */
private class LuminaZoomableImageFrame(context: Context) : FrameLayout(context) {
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

    fun bind(item: LuminaMediaItem, onTap: () -> Unit, onPreviewReady: () -> Unit) {
        preview.setOnClickListener { onTap() }
        zoomView.setOnClickListener { onTap() }

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

private fun LuminaMediaItem.supportsRegionZoom(): Boolean {
    val name = displayName.lowercase(Locale.ROOT)
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
        "%d:%02d:%02d".format(Locale.US, hours, minutes, seconds)
    } else {
        "%d:%02d".format(Locale.US, minutes, seconds)
    }
}
