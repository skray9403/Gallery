package org.fossify.gallery.ui

import android.content.Intent
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
import com.squareup.picasso.Picasso
import org.fossify.gallery.ui.data.FolioMediaItem

@Composable
internal fun FolioViewer(
    item: FolioMediaItem,
    isFavorite: Boolean,
    onClose: () -> Unit,
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
                    update = { Picasso.get().load(item.uri).fit().centerInside().into(it) }
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
