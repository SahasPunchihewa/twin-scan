package com.sahas.twinscan.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val name: String,
    val size: Long,
    /** Seconds since epoch, as stored by MediaStore. */
    val dateModified: Long,
    val isVideo: Boolean,
) {
    /** Absolute path of the containing directory, without a trailing slash. */
    val dir: String = path.substringBeforeLast('/', "")
}

/** A set of files whose contents are byte-for-byte identical. */
data class DuplicateGroup(
    val hash: String,
    val size: Long,
    val items: List<MediaItem>,
) {
    val wastedBytes: Long get() = size * (items.size - 1)
}

data class ScanProgress(
    val stage: Stage,
    val fraction: Float,
    val done: Int,
    val total: Int,
    val currentName: String = "",
) {
    enum class Stage { QUICK_CHECK, FULL_CHECK }
}

class FolderNode(
    val path: String,
    val name: String,
    /** Media files directly inside this folder. */
    val directCount: Int,
    /** Media files inside this folder and all of its subfolders. */
    val totalCount: Int,
    val children: List<FolderNode>,
)

/** Tree of every folder that contains media, built from the MediaStore index. */
class FolderIndex(
    val root: FolderNode?,
    val byPath: Map<String, FolderNode>,
) {
    companion object {
        fun build(items: List<MediaItem>): FolderIndex {
            if (items.isEmpty()) return FolderIndex(null, emptyMap())

            val rootBuilder = Builder("", "")
            for ((dir, count) in items.groupingBy { it.dir }.eachCount()) {
                var node = rootBuilder
                for (segment in dir.split('/')) {
                    if (segment.isEmpty()) continue
                    val parent = node
                    node = parent.children.getOrPut(segment) {
                        Builder("${parent.path}/$segment", segment)
                    }
                }
                node.direct += count
            }

            // Skip the empty chain at the top (e.g. /storage/emulated/0) so browsing starts
            // at the first folder that offers a real choice.
            var root = freeze(rootBuilder)
            while (root.directCount == 0 && root.children.size == 1) {
                root = root.children[0]
            }

            val byPath = HashMap<String, FolderNode>()
            fun register(node: FolderNode) {
                byPath[node.path] = node
                node.children.forEach(::register)
            }
            register(root)
            return FolderIndex(root, byPath)
        }

        private fun freeze(builder: Builder): FolderNode {
            val children = builder.children.values.map(::freeze).sortedBy { it.name.lowercase() }
            return FolderNode(
                path = builder.path,
                name = builder.name,
                directCount = builder.direct,
                totalCount = builder.direct + children.sumOf { it.totalCount },
                children = children,
            )
        }

        private class Builder(val path: String, val name: String) {
            var direct = 0
            val children = HashMap<String, Builder>()
        }
    }
}
