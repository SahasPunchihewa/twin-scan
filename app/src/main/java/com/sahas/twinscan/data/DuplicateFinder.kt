package com.sahas.twinscan.data

import android.content.ContentResolver
import android.os.Build
import android.provider.MediaStore
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Finds byte-for-byte identical files in three passes, each one only looking at what the
 * previous pass could not rule out:
 *
 * 1. group by file size (free, comes from the MediaStore index)
 * 2. hash the first [HEAD_BYTES] of each remaining file
 * 3. hash the full contents of whatever still matches
 *
 * Call from a background dispatcher; all I/O is blocking.
 */
class DuplicateFinder(
    private val resolver: ContentResolver,
    /** True when ACCESS_MEDIA_LOCATION is granted, so unredacted bytes can be read. */
    private val requireOriginal: Boolean,
) {
    private val buffer = ByteArray(BUFFER_BYTES)

    suspend fun find(
        items: List<MediaItem>,
        onProgress: (ScanProgress) -> Unit,
    ): List<DuplicateGroup> {
        val context = currentCoroutineContext()

        // Pass 1: only files sharing a size can be duplicates.
        val sameSizeGroups = items
            .distinctBy { it.path }
            .groupBy { it.size }
            .values
            .filter { it.size > 1 }
        val candidateCount = sameSizeGroups.sumOf { it.size }

        // Pass 2: cheap hash of the head of each file.
        val headGroups = ArrayList<Pair<String, List<MediaItem>>>()
        var done = 0
        for (group in sameSizeGroups) {
            val byHead = HashMap<String, MutableList<MediaItem>>()
            for (item in group) {
                context.ensureActive()
                onProgress(
                    ScanProgress(
                        stage = ScanProgress.Stage.QUICK_CHECK,
                        fraction = done.toFloat() / candidateCount,
                        done = done,
                        total = candidateCount,
                        currentName = item.name,
                    )
                )
                hash(item, HEAD_BYTES)?.let { byHead.getOrPut(it) { ArrayList() }.add(item) }
                done++
            }
            for ((head, matches) in byHead) {
                if (matches.size > 1) headGroups += head to matches
            }
        }

        // Pass 3: full hash. Files no larger than the head were already hashed completely.
        val needsFullHash = headGroups.filter { (_, group) -> group[0].size > HEAD_BYTES }
        val fullTotal = needsFullHash.sumOf { it.second.size }
        val totalBytes = needsFullHash.sumOf { (_, group) -> group[0].size * group.size }
        var bytesDone = 0L
        done = 0

        val result = ArrayList<DuplicateGroup>()
        for ((head, group) in headGroups) {
            val size = group[0].size
            if (size <= HEAD_BYTES) {
                result += DuplicateGroup(head, size, group)
                continue
            }
            val byFull = HashMap<String, MutableList<MediaItem>>()
            for (item in group) {
                context.ensureActive()
                val startBytes = bytesDone
                val full = hash(item, Long.MAX_VALUE) { read ->
                    bytesDone += read
                    onProgress(
                        ScanProgress(
                            stage = ScanProgress.Stage.FULL_CHECK,
                            fraction = if (totalBytes > 0) bytesDone.toFloat() / totalBytes else 0f,
                            done = done,
                            total = fullTotal,
                            currentName = item.name,
                        )
                    )
                }
                // Keep the progress bar honest even if the file was unreadable or shorter.
                bytesDone = startBytes + size
                full?.let { byFull.getOrPut(it) { ArrayList() }.add(item) }
                done++
            }
            for ((full, matches) in byFull) {
                if (matches.size > 1) result += DuplicateGroup(full, size, matches)
            }
        }

        return result
            .map { group -> group.copy(items = group.items.sortedBy { it.dateModified }) }
            .sortedByDescending { it.wastedBytes }
    }

    /** SHA-256 of up to [limit] bytes, or null if the file can't be read. */
    private suspend fun hash(
        item: MediaItem,
        limit: Long,
        onBytesRead: (Int) -> Unit = {},
    ): String? {
        val context = currentCoroutineContext()
        return try {
            open(item)?.use { input ->
                val digest = MessageDigest.getInstance("SHA-256")
                var remaining = limit
                while (remaining > 0) {
                    context.ensureActive()
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    remaining -= read
                    onBytesRead(read)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private fun open(item: MediaItem): InputStream? {
        if (requireOriginal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return resolver.openInputStream(MediaStore.setRequireOriginal(item.uri))
            } catch (_: SecurityException) {
                // Fall through to the (possibly redacted) default stream.
            } catch (_: UnsupportedOperationException) {
            }
        }
        return resolver.openInputStream(item.uri)
    }

    private companion object {
        const val HEAD_BYTES = 64L * 1024
        const val BUFFER_BYTES = 256 * 1024
    }
}
