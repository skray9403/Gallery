package org.fossify.gallery.ui.data

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.content.ContentUris
import android.database.Cursor
import androidx.core.content.ContextCompat

data class FolioMediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val dateAddedMillis: Long,
    val bucketId: String?,
    val bucketName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val durationMillis: Long
)

data class FolioAlbum(
    val id: String,
    val name: String,
    val count: Int,
    val cover: FolioMediaItem
)

object FolioMediaRepository {
    fun hasMediaAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val images = granted(context, Manifest.permission.READ_MEDIA_IMAGES)
            val videos = granted(context, Manifest.permission.READ_MEDIA_VIDEO)
            val selected = Build.VERSION.SDK_INT >= 34 &&
                granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            return images || videos || selected
        }
        return granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun permissionsToRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun loadMedia(context: Context): List<FolioMediaItem> {
        val resolver = context.contentResolver
        val images = queryCollection(
            resolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            video = false
        )
        val videos = queryCollection(
            resolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            video = true
        )
        return (images + videos).sortedByDescending(FolioMediaItem::dateAddedMillis)
    }

    fun buildAlbums(items: List<FolioMediaItem>): List<FolioAlbum> =
        items.groupBy { it.bucketId ?: it.bucketName.ifBlank { "unknown" } }
            .mapNotNull { (id, contents) ->
                val cover = contents.maxByOrNull(FolioMediaItem::dateAddedMillis)
                    ?: return@mapNotNull null
                FolioAlbum(
                    id = id,
                    name = cover.bucketName.ifBlank { "Other" },
                    count = contents.size,
                    cover = cover
                )
            }
            .sortedByDescending { it.cover.dateAddedMillis }

    private fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun queryCollection(
        resolver: ContentResolver,
        collection: Uri,
        video: Boolean
    ): List<FolioMediaItem> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(if (video) MediaStore.Video.Media.BUCKET_ID else MediaStore.Images.Media.BUCKET_ID)
            add(if (video) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            add(if (video) MediaStore.Video.Media.DURATION else MediaStore.Images.Media.DATE_TAKEN)
        }.toTypedArray()

        return try {
            resolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor -> cursor.readFolioItems(collection, video) }.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun Cursor.readFolioItems(
        collection: Uri,
        video: Boolean
    ): List<FolioMediaItem> {
        val idColumn = getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val nameColumn = getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val mimeColumn = getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val dateColumn = getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
        val sizeColumn = getColumnIndex(MediaStore.MediaColumns.SIZE)
        val widthColumn = getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val heightColumn = getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val bucketIdColumn = getColumnIndex(
            if (video) MediaStore.Video.Media.BUCKET_ID else MediaStore.Images.Media.BUCKET_ID
        )
        val bucketNameColumn = getColumnIndex(
            if (video) MediaStore.Video.Media.BUCKET_DISPLAY_NAME else MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val durationColumn = if (video) getColumnIndex(MediaStore.Video.Media.DURATION) else -1
        val dateTakenColumn = if (video) -1 else getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)

        return buildList {
            while (moveToNext()) {
                val id = getLong(idColumn)
                val dateAdded = if (dateColumn >= 0) getLong(dateColumn) * 1000L else 0L
                val dateTaken = if (dateTakenColumn >= 0 && !isNull(dateTakenColumn)) {
                    getLong(dateTakenColumn)
                } else {
                    0L
                }
                add(
                    FolioMediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = if (nameColumn >= 0) getString(nameColumn).orEmpty() else "",
                        mimeType = if (mimeColumn >= 0) {
                            getString(mimeColumn) ?: if (video) "video/*" else "image/*"
                        } else if (video) "video/*" else "image/*",
                        dateAddedMillis = maxOf(dateAdded, dateTaken),
                        bucketId = if (bucketIdColumn >= 0) getString(bucketIdColumn) else null,
                        bucketName = if (bucketNameColumn >= 0) {
                            getString(bucketNameColumn).orEmpty().ifBlank { "Other" }
                        } else {
                            "Other"
                        },
                        width = if (widthColumn >= 0) getInt(widthColumn) else 0,
                        height = if (heightColumn >= 0) getInt(heightColumn) else 0,
                        sizeBytes = if (sizeColumn >= 0) getLong(sizeColumn) else 0L,
                        isVideo = video,
                        durationMillis = if (durationColumn >= 0) getLong(durationColumn) else 0L
                    )
                )
            }
        }
    }
}
