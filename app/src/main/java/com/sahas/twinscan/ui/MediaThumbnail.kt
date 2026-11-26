package com.sahas.twinscan.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.sahas.twinscan.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object ThumbnailLoader {
    private val cache = object : LruCache<Uri, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: Uri, value: Bitmap): Int = value.byteCount
    }

    fun cached(uri: Uri): Bitmap? = cache.get(uri)

    suspend fun load(context: Context, item: MediaItem): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(item.uri) ?: decode(context, item)?.also { cache.put(item.uri, it) }
    }

    @Suppress("DEPRECATION")
    private fun decode(context: Context, item: MediaItem): Bitmap? = try {
        val resolver = context.contentResolver
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                resolver.loadThumbnail(item.uri, Size(320, 320), null)

            item.isVideo -> MediaStore.Video.Thumbnails.getThumbnail(
                resolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null
            )

            else -> MediaStore.Images.Thumbnails.getThumbnail(
                resolver, item.id, MediaStore.Images.Thumbnails.MINI_KIND, null
            )
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun MediaThumbnail(item: MediaItem, modifier: Modifier = Modifier) {
    // Previews have no media provider; leave the placeholder background showing.
    if (LocalInspectionMode.current) return

    val context = LocalContext.current.applicationContext
    val bitmap by produceState(ThumbnailLoader.cached(item.uri), item.uri) {
        if (value == null) value = ThumbnailLoader.load(context, item)
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = item.name,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
