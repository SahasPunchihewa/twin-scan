package com.sahas.twinscan.ui

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.ui.tooling.preview.Preview
import com.sahas.twinscan.Screen
import com.sahas.twinscan.UiState
import com.sahas.twinscan.data.DuplicateGroup
import com.sahas.twinscan.data.FolderIndex
import com.sahas.twinscan.data.FolderNode
import com.sahas.twinscan.data.MediaItem

/** Renders a preview in both the light and the dark theme. */
@Preview(name = "Light", showBackground = true, showSystemUi = true)
@Preview(name = "Dark", showBackground = true, showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

/** Sample data for previews only. */
internal object PreviewData {
    private const val ROOT = "/storage/emulated/0"

    private fun leaf(parent: String, name: String, files: Int) =
        FolderNode("$parent/$name", name, files, files, emptyList())

    private val dcim = FolderNode(
        path = "$ROOT/DCIM", name = "DCIM", directCount = 0, totalCount = 1284,
        children = listOf(leaf("$ROOT/DCIM", "Camera", 1210), leaf("$ROOT/DCIM", "Screenshots", 74)),
    )
    private val pictures = FolderNode(
        path = "$ROOT/Pictures", name = "Pictures", directCount = 12, totalCount = 356,
        children = listOf(leaf("$ROOT/Pictures", "WhatsApp Images", 344)),
    )
    private val download = leaf(ROOT, "Download", 48)
    private val movies = leaf(ROOT, "Movies", 9)

    private val root = FolderNode(ROOT, "0", 0, 1697, listOf(dcim, download, movies, pictures))

    private val folders = FolderIndex(
        root = root,
        byPath = (listOf(root, dcim, pictures, download, movies) + dcim.children + pictures.children)
            .associateBy { it.path },
    )

    val pickerEmptySelection = UiState(folders = folders, currentPath = ROOT)

    val pickerWithSelection = UiState(
        folders = folders,
        currentPath = ROOT,
        selectedFolders = setOf("$ROOT/DCIM/Camera", "$ROOT/Download"),
        matchCount = 1258,
    )

    val pickerSubfolder = UiState(
        folders = folders,
        currentPath = "$ROOT/DCIM",
        selectedFolders = setOf("$ROOT/DCIM"),
        matchCount = 1284,
    )

    val pickerNoMedia = UiState(folders = FolderIndex(null, emptyMap()))

    private fun item(id: Long, dir: String, name: String, size: Long, video: Boolean = false) = MediaItem(
        id = id,
        uri = Uri.parse("content://media/external/${if (video) "video" else "images"}/media/$id"),
        path = "$ROOT/$dir/$name",
        name = name,
        size = size,
        dateModified = 1_780_000_000L + id * 86_400,
        isVideo = video,
    )

    private val groups = listOf(
        DuplicateGroup(
            hash = "a", size = 48_500_000,
            items = listOf(
                item(1, "DCIM/Camera", "VID_20260612_181204.mp4", 48_500_000, video = true),
                item(2, "Download", "VID_20260612_181204 (1).mp4", 48_500_000, video = true),
            ),
        ),
        DuplicateGroup(
            hash = "b", size = 3_400_000,
            items = listOf(
                item(3, "DCIM/Camera", "IMG_20260501_101500.jpg", 3_400_000),
                item(4, "Pictures/WhatsApp Images", "IMG-20260501-WA0003.jpg", 3_400_000),
                item(5, "Download", "IMG_20260501_101500.jpg", 3_400_000),
            ),
        ),
    )

    val results = UiState(
        screen = Screen.RESULTS,
        scannedCount = 1258,
        groups = groups,
        selectedForDelete = setOf(groups[0].items[1].uri, groups[1].items[1].uri),
    )

    val resultsEmpty = UiState(screen = Screen.RESULTS, scannedCount = 1258)
}
