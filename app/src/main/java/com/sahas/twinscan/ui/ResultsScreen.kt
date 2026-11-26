package com.sahas.twinscan.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sahas.twinscan.R
import com.sahas.twinscan.ResultsActions
import com.sahas.twinscan.UiState
import com.sahas.twinscan.data.DuplicateGroup
import com.sahas.twinscan.data.MediaItem
import com.sahas.twinscan.ui.theme.TwinScanTheme
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    state: UiState,
    viewModel: ResultsActions,
    snackbarHostState: SnackbarHostState,
) {
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    BackHandler { viewModel.backToPicker() }

    val selectedItems = remember(state.groups, state.selectedForDelete) {
        state.groups.flatMap { it.items }.filter { it.uri in state.selectedForDelete }
    }
    val selectedBytes = selectedItems.sumOf { it.size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Results") },
                navigationIcon = {
                    IconButton(onClick = viewModel::backToPicker) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back to folders")
                    }
                },
            )
        },
        bottomBar = {
            if (state.groups.isNotEmpty()) {
                DeleteBar(
                    count = selectedItems.size,
                    sizeText = formatSize(context, selectedBytes),
                    onDelete = { confirmDelete = true },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.groups.isEmpty()) {
            MessageState(
                icon = R.drawable.ic_check,
                title = "No duplicates here",
                body = "${plural(state.scannedCount, "file")} compared. Every remaining file is unique.",
                modifier = Modifier.padding(padding),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::backToPicker) { Text("Scan other folders") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "summary") { SummaryCard(state, context) }
                item(key = "quick") { QuickSelect(state, viewModel) }
                items(state.groups, key = { it.hash }) { group ->
                    GroupCard(
                        group = group,
                        selected = state.selectedForDelete,
                        onToggle = { viewModel.toggleItem(it.uri) },
                        onOpen = { openItem(context, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        val groupsLosingAllCopies = state.groups.count { group ->
            group.items.all { it.uri in state.selectedForDelete }
        }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text("Delete ${plural(selectedItems.size, "file")}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This frees ${formatSize(context, selectedBytes)}. Deleted files can't be recovered.")
                    if (groupsLosingAllCopies > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                text = "In ${plural(groupsLosingAllCopies, "group")} every copy is selected, " +
                                    "so no copy of those files will be kept.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SummaryCard(state: UiState, context: Context) {
    val wasted = state.groups.sumOf { it.wastedBytes }
    val extraCopies = state.groups.sumOf { it.items.size - 1 }
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Text("You can free up", style = MaterialTheme.typography.bodyLarge)
            Text(formatSize(context, wasted), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${formatCount(extraCopies)} extra ${if (extraCopies == 1) "copy" else "copies"} in " +
                    "${plural(state.groups.size, "group")} · ${plural(state.scannedCount, "file")} compared",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun QuickSelect(state: UiState, viewModel: ResultsActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Tap a file to mark it for deletion, or long-press to open it. " +
                "The buttons below mark every extra copy and keep one per group.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { viewModel.selectAllButOne(keepNewest = false) }) { Text("Keep oldest") }
            FilledTonalButton(onClick = { viewModel.selectAllButOne(keepNewest = true) }) { Text("Keep newest") }
            if (state.selectedForDelete.isNotEmpty()) {
                TextButton(onClick = viewModel::clearItemSelection) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: DuplicateGroup,
    selected: Set<Uri>,
    onToggle: (MediaItem) -> Unit,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val marked = group.items.count { it.uri in selected }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${group.items.size} identical copies", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${formatSize(context, group.size)} each",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (marked > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = if (marked == group.items.size) "All marked" else "$marked to delete",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            LazyRow(
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(group.items, key = { it.uri.toString() }) { item ->
                    DuplicateCell(
                        item = item,
                        selected = item.uri in selected,
                        onToggle = { onToggle(item) },
                        onOpen = { onOpen(item) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DuplicateCell(
    item: MediaItem,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Column(Modifier.width(140.dp)) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(shape)
                .background(colors.surfaceVariant)
                .then(if (selected) Modifier.border(3.dp, colors.error, shape) else Modifier)
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = onOpen,
                    onClickLabel = if (selected) "Keep this file" else "Mark for deletion",
                    onLongClickLabel = "Open",
                ),
        ) {
            MediaThumbnail(item, Modifier.fillMaxSize())
            if (selected) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
            }
            if (item.isVideo) {
                Icon(
                    painterResource(R.drawable.ic_play),
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .padding(8.dp),
                )
            }
            // Status marker: red bin when marked, hollow ring when kept.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .then(
                        if (selected) Modifier.background(colors.error)
                        else Modifier
                            .background(Color.Black.copy(alpha = 0.3f))
                            .border(2.dp, Color.White, CircleShape)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = "Marked for deletion",
                        tint = colors.onError,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Surface(
                color = if (selected) colors.error else colors.tertiaryContainer,
                contentColor = if (selected) colors.onError else colors.onTertiaryContainer,
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                Text(
                    text = if (selected) "Delete" else "Keep",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = displayDir(item),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.dateModified * 1000)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeleteBar(count: Int, sizeText: String, onDelete: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Button(
            onClick = onDelete,
            enabled = count > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp),
        ) {
            if (count > 0) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Delete ${plural(count, "file")} · $sizeText")
            } else {
                Text("Mark files to delete")
            }
        }
    }
}

private fun displayDir(item: MediaItem): String =
    item.dir.removePrefix(storageRoot).ifEmpty { "/" }

private fun openItem(context: Context, item: MediaItem) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(item.uri, if (item.isVideo) "video/*" else "image/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No viewer installed; nothing useful to do.
    }
}

// region Previews

@Composable
private fun ResultsPreview(state: UiState) {
    TwinScanTheme {
        ResultsScreen(
            state = state,
            viewModel = object : ResultsActions {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@ThemePreviews
@Composable
private fun ResultsWithDuplicatesPreview() = ResultsPreview(PreviewData.results)

@ThemePreviews
@Composable
private fun ResultsNoDuplicatesPreview() = ResultsPreview(PreviewData.resultsEmpty)

// endregion
