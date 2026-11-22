package org.fossify.gallery.ui.details

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoSizeSelectActual
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fossify.gallery.ui.data.FolioMediaItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

internal data class FolioExifDetails(
    val camera: String? = null,
    val lens: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val focalLength: String? = null,
    val iso: String? = null,
    val flash: String? = null,
    val location: Pair<Double, Double>? = null
)

@SuppressLint("NonObservableLocale")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FolioDetailsBottomSheet(
    item: FolioMediaItem,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    val clipboardManager = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val exifDetailsState = produceState<FolioExifDetails?>(initialValue = null, key1 = item.uri) {
        value = withContext(Dispatchers.IO) {
            loadExifDetails(context, item.uri)
        }
    }
    val exif = exifDetailsState.value

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Overview Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.dateAddedMillis > 0L) {
                        val formattedDate = remember(item.dateAddedMillis, locale) {
                            runCatching {
                                DateTimeFormatter.ofPattern("EEE, MMM d, yyyy • h:mm a", locale)
                                    .format(Instant.ofEpochMilli(item.dateAddedMillis).atZone(ZoneId.systemDefault()))
                            }.getOrDefault("")
                        }
                        if (formattedDate.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Media attributes chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // File size
                        val sizeText = remember(item.sizeBytes) {
                            if (item.sizeBytes > 0L) Formatter.formatFileSize(context, item.sizeBytes) else null
                        }
                        if (sizeText != null) {
                            DetailChip(label = sizeText)
                        }

                        // Dimensions & Megapixels
                        if (item.width > 0 && item.height > 0) {
                            val mp = (item.width.toLong() * item.height) / 1_000_000.0
                            val mpText = if (mp >= 0.1) " • %.1f MP".format(Locale.US, mp) else ""
                            DetailChip(label = "${item.width} × ${item.height}$mpText")
                        }

                        // Format / MIME
                        val formatText = remember(item.mimeType, item.displayName) {
                            when {
                                item.mimeType.contains("jpeg", ignoreCase = true) -> "JPEG"
                                item.mimeType.contains("png", ignoreCase = true) -> "PNG"
                                item.mimeType.contains("webp", ignoreCase = true) -> "WebP"
                                item.mimeType.contains("gif", ignoreCase = true) -> "GIF"
                                item.mimeType.contains("avif", ignoreCase = true) -> "AVIF"
                                item.mimeType.contains("heic", ignoreCase = true) -> "HEIC"
                                item.mimeType.contains("mp4", ignoreCase = true) -> "MP4"
                                item.mimeType.contains("mkv", ignoreCase = true) -> "MKV"
                                item.mimeType.contains("video", ignoreCase = true) -> "Video"
                                else -> item.displayName.substringAfterLast('.', item.mimeType).uppercase(Locale.US)
                            }
                        }
                        DetailChip(label = formatText)

                        // Video duration
                        if (item.isVideo && item.durationMillis > 0L) {
                            DetailChip(label = formatDuration(item.durationMillis))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Camera & Capture Details (if EXIF exists)
            if (exif != null && (exif.camera != null || exif.aperture != null || exif.shutterSpeed != null || exif.iso != null)) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Camera Details",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (exif.camera != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = exif.camera,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (exif.lens != null) {
                            Text(
                                text = exif.lens,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Capture settings chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (exif.focalLength != null) DetailChip(label = exif.focalLength)
                            if (exif.aperture != null) DetailChip(label = exif.aperture)
                            if (exif.shutterSpeed != null) DetailChip(label = exif.shutterSpeed)
                            if (exif.iso != null) DetailChip(label = exif.iso)
                            if (exif.flash != null) DetailChip(label = exif.flash)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Storage Path Card
            val displayPath = remember(item.relativePath, item.displayName, item.bucketName) {
                if (item.relativePath.isNotBlank()) {
                    "${item.relativePath.trim('/')}/${item.displayName}"
                } else if (item.bucketName.isNotBlank()) {
                    "${item.bucketName}/${item.displayName}"
                } else {
                    item.displayName
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Storage Location",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = displayPath,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(displayPath))
                        Toast.makeText(context, "Path copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy path",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Location Card (if EXIF GPS exists)
            if (exif?.location != null) {
                val (lat, lng) = exif.location
                Spacer(Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Map,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Location",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "%.4f, %.4f".format(Locale.US, lat, lng),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        AssistChip(
                            onClick = {
                                val mapUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(item.displayName)})")
                                val mapIntent = Intent(Intent.ACTION_VIEW, mapUri)
                                runCatching { context.startActivity(mapIntent) }.onFailure {
                                    Toast.makeText(context, "No map application found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text("Open map") }
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DetailChip(label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(Locale.US, minutes, seconds)
}

private fun loadExifDetails(context: Context, uri: Uri): FolioExifDetails? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val exif = ExifInterface(stream)
        val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
        val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
        val camera = when {
            make.isNotEmpty() && model.isNotEmpty() -> {
                if (model.startsWith(make, ignoreCase = true)) model else "$make $model"
            }
            model.isNotEmpty() -> model
            make.isNotEmpty() -> make
            else -> null
        }
        val lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifEmpty { null }

        val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
        val aperture = if (fNumber > 0.0) "f/%.1f".format(Locale.US, fNumber) else null

        val exposureTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
        val shutterSpeed = if (exposureTime > 0.0) {
            if (exposureTime < 1.0) "1/%d s".format(Locale.US, (1.0 / exposureTime).roundToInt())
            else "%.1f s".format(Locale.US, exposureTime)
        } else null

        val focal = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        val focalLength = if (focal > 0.0) "%.1f mm".format(Locale.US, focal) else null

        val isoVal = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            ?: exif.getAttribute(ExifInterface.TAG_ISO_SPEED)
        val iso = if (!isoVal.isNullOrBlank()) "ISO $isoVal" else null

        val flashVal = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1)
        val flash = if (flashVal >= 0) {
            if (flashVal and 1 != 0) "Flash fired" else "Flash off"
        } else null

        val latLon = FloatArray(2)
        val location = if (exif.getLatLong(latLon)) {
            Pair(latLon[0].toDouble(), latLon[1].toDouble())
        } else null

        FolioExifDetails(
            camera = camera,
            lens = lens,
            aperture = aperture,
            shutterSpeed = shutterSpeed,
            focalLength = focalLength,
            iso = iso,
            flash = flash,
            location = location
        )
    }
}.getOrNull()
