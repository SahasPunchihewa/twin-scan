package com.sahas.twinscan.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sahas.twinscan.R
import com.sahas.twinscan.PickerActions
import com.sahas.twinscan.UiState
import com.sahas.twinscan.data.FolderNode
import com.sahas.twinscan.ui.theme.ThemeMode
import com.sahas.twinscan.ui.theme.TwinScanTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    state: UiState,
    viewModel: PickerActions,
    partialAccess: Boolean,
    onManageAccess: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val index = state.folders
    val current = index?.byPath?.get(state.currentPath)
    val atRoot = current == null || current.path == index.root?.path

    BackHandler(enabled = !atRoot) { viewModel.navigateUp() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (atRoot) "TwinScan" else folderLabel(current.path, current.name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (atRoot) "Duplicate photo & video finder" else breadcrumb(current.path),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    if (!atRoot) {
                        IconButton(onClick = viewModel::navigateUp) {
                            Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back to parent folder")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadIndex(force = true) }, enabled = !state.loadingIndex) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh folders")
                    }
                    ThemeMenuButton(themeMode, onThemeModeChange)
                },
            )
        },
        bottomBar = { if (current != null) ScanBar(state, viewModel) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when {
            index == null || (state.loadingIndex && current == null) -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(
                    "Looking for photos and videos…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            current == null -> MessageState(
                icon = R.drawable.ic_image,
                title = "Nothing to scan yet",
                body = "No photos or videos were found on this device.",
                modifier = Modifier.padding(padding),
            ) {
                TextButton(onClick = { viewModel.loadIndex(force = true) }) { Text("Check again") }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (partialAccess) {
                    item(key = "partial") { PartialAccessCard(onManageAccess) }
                }
                if (atRoot && state.selectedFolders.isEmpty()) {
                    item(key = "intro") { IntroCard() }
                }
                item(key = "options") { ScanOptions(state, viewModel) }

                val selfCount = if (state.includeSubfolders) current.totalCount else current.directCount
                if (selfCount > 0) {
                    item(key = "self") {
                        val label = folderLabel(current.path, current.name)
                        FolderCard(
                            icon = R.drawable.ic_folder,
                            title = if (state.includeSubfolders) "Everything in $label" else "Files directly in $label",
                            subtitle = plural(selfCount, "file"),
                            note = coveredNote(state, current.path),
                            selected = isChecked(state, current.path),
                            enabled = !isCoveredByAncestor(state, current.path),
                            highlighted = true,
                            onToggle = { viewModel.toggleFolder(current.path) },
                            onOpen = null,
                        )
                    }
                }

                if (current.children.isNotEmpty()) {
                    item(key = "header") {
                        Text(
                            text = "Folders",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
                        )
                    }
                }
                items(current.children, key = { it.path }) { child ->
                    FolderCard(
                        icon = R.drawable.ic_folder,
                        title = child.name,
                        subtitle = folderSubtitle(state, child),
                        note = coveredNote(state, child.path) ?: selectedInsideNote(state, child.path),
                        selected = isChecked(state, child.path),
                        enabled = !isCoveredByAncestor(state, child.path),
                        highlighted = false,
                        onToggle = { viewModel.toggleFolder(child.path) },
                        onOpen = if (child.children.isNotEmpty()) {
                            { viewModel.openFolder(child.path) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroCard() {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Choose where to look", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Tap a folder to select it. Use the arrow to pick specific subfolders inside it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.width(16.dp))
            IconBadge(
                icon = R.drawable.ic_search,
                size = 56.dp,
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ScanOptions(state: UiState, viewModel: PickerActions) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OptionChip("Photos", R.drawable.ic_image, state.scanImages) { viewModel.setScanImages(!state.scanImages) }
        OptionChip("Videos", R.drawable.ic_videocam, state.scanVideos) { viewModel.setScanVideos(!state.scanVideos) }
        OptionChip("Include subfolders", R.drawable.ic_folder, state.includeSubfolders) {
            viewModel.setIncludeSubfolders(!state.includeSubfolders)
        }
    }
}

@Composable
private fun OptionChip(label: String, @DrawableRes icon: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                painterResource(if (selected) R.drawable.ic_check else icon),
                contentDescription = null,
                modifier = Modifier.size(FilterChipDefaults.IconSize),
            )
        },
    )
}

@Composable
private fun FolderCard(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    note: String?,
    selected: Boolean,
    enabled: Boolean,
    highlighted: Boolean,
    onToggle: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = when {
            selected -> colors.secondaryContainer
            highlighted -> colors.surfaceContainerHigh
            else -> colors.surfaceContainerLow
        },
        label = "folderCard",
    )
    Surface(
        onClick = onToggle,
        enabled = enabled,
        color = container,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionMark(selected)
            Spacer(Modifier.width(14.dp))
            IconBadge(
                icon = icon,
                containerColor = if (selected) colors.primary else colors.primaryContainer,
                contentColor = if (selected) colors.onPrimary else colors.onPrimaryContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note != null) {
                    Text(note, style = MaterialTheme.typography.labelMedium, color = colors.primary)
                }
            }
            if (onOpen != null) {
                FilledTonalIconButton(onClick = onOpen) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = "Open $title")
                }
            } else {
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun ScanBar(state: UiState, viewModel: PickerActions) {
    val folders = state.selectedFolders.size
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (folders == 0) "No folders selected yet" else "${plural(folders, "folder")} selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (folders > 0) {
                    TextButton(onClick = viewModel::clearFolderSelection) { Text("Clear") }
                }
            }
            Button(
                onClick = viewModel::startScan,
                enabled = state.matchCount >= 2,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        folders == 0 -> "Select a folder to scan"
                        state.matchCount < 2 -> "Not enough files to compare"
                        else -> "Scan ${plural(state.matchCount, "file")}"
                    },
                )
            }
        }
    }
}

@Composable
private fun PartialAccessCard(onManageAccess: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Limited access: only the photos and videos you picked can be scanned.",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onManageAccess) { Text("Change") }
        }
    }
}

private fun isCoveredByAncestor(state: UiState, path: String): Boolean =
    state.includeSubfolders && state.selectedFolders.any { path.startsWith("$it/") }

private fun isChecked(state: UiState, path: String): Boolean =
    path in state.selectedFolders || isCoveredByAncestor(state, path)

private fun coveredNote(state: UiState, path: String): String? =
    if (isCoveredByAncestor(state, path)) "Included with a parent folder" else null

private fun selectedInsideNote(state: UiState, path: String): String? {
    if (isChecked(state, path)) return null
    val inside = state.selectedFolders.count { it.startsWith("$path/") }
    return if (inside > 0) "${plural(inside, "subfolder")} selected inside" else null
}

private fun folderSubtitle(state: UiState, node: FolderNode): String {
    val files = plural(if (state.includeSubfolders) node.totalCount else node.directCount, "file")
    return if (node.children.isEmpty()) files else "$files · ${plural(node.children.size, "subfolder")}"
}

private fun folderLabel(path: String, name: String): String =
    if (path == storageRoot) "Internal storage" else name.ifEmpty { "Storage" }

/** "Internal storage › DCIM › Camera" */
private fun breadcrumb(path: String): String {
    val relative = if (path.startsWith(storageRoot)) "Internal storage" + path.removePrefix(storageRoot) else path
    return relative.split('/').filter { it.isNotEmpty() }.joinToString(" › ")
}

// region Previews

@Composable
private fun PickerPreview(state: UiState, partialAccess: Boolean = false) {
    TwinScanTheme {
        FolderPickerScreen(
            state = state,
            viewModel = object : PickerActions {},
            partialAccess = partialAccess,
            onManageAccess = {},
            themeMode = ThemeMode.SYSTEM,
            onThemeModeChange = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@ThemePreviews
@Composable
private fun PickerNothingSelectedPreview() = PickerPreview(PreviewData.pickerEmptySelection)

@ThemePreviews
@Composable
private fun PickerWithSelectionPreview() = PickerPreview(PreviewData.pickerWithSelection, partialAccess = true)

@ThemePreviews
@Composable
private fun PickerSubfolderPreview() = PickerPreview(PreviewData.pickerSubfolder)

@ThemePreviews
@Composable
private fun PickerNoMediaPreview() = PickerPreview(PreviewData.pickerNoMedia)

// endregion
