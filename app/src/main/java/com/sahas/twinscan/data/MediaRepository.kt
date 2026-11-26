package com.sahas.twinscan.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the device's image and video index from MediaStore. */
class MediaRepository(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    suspend fun loadAll(): List<MediaItem> = withContext(Dispatchers.IO) {
        query(video = false) + query(video = true)
    }

    private fun query(video: Boolean): List<MediaItem> {
        val collection = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

            video -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )

        val result = ArrayList<MediaItem>()
        try {
            resolver.query(collection, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val size = cursor.getLong(sizeCol)
                    if (size <= 0) continue
                    val id = cursor.getLong(idCol)
                    result += MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        path = path,
                        name = cursor.getString(nameCol) ?: path.substringAfterLast('/'),
                        size = size,
                        dateModified = cursor.getLong(dateCol),
                        isVideo = video,
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission was revoked while we were querying; treat as "nothing visible".
        }
        return result
    }
}
