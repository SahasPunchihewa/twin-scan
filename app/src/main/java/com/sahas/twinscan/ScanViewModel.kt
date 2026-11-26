package com.sahas.twinscan

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sahas.twinscan.data.DuplicateFinder
import com.sahas.twinscan.data.DuplicateGroup
import com.sahas.twinscan.data.FolderIndex
import com.sahas.twinscan.data.MediaItem
import com.sahas.twinscan.data.MediaRepository
import com.sahas.twinscan.data.ScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { PICKER, SCANNING, RESULTS }

data class UiState(
    val screen: Screen = Screen.PICKER,

    // Folder picker
    val loadingIndex: Boolean = false,
    /** Null until the first MediaStore load finishes. */
    val folders: FolderIndex? = null,
    val currentPath: String? = null,
    val selectedFolders: Set<String> = emptySet(),
    val includeSubfolders: Boolean = true,
    val scanImages: Boolean = true,
    val scanVideos: Boolean = true,
    /** Number of files the current selection would scan. */
    val matchCount: Int = 0,

    // Scan + results
    val progress: ScanProgress? = null,
    val scannedCount: Int = 0,
    val groups: List<DuplicateGroup> = emptyList(),
    val selectedForDelete: Set<Uri> = emptySet(),

    /** System dialog the UI must launch before a delete can proceed. */
    val pendingConsent: IntentSender? = null,
    val message: String? = null,
)

/**
 * What the folder picker can ask for. The screens depend on these interfaces rather than on
 * [ScanViewModel] so they can be rendered in Compose previews with no-op actions.
 */
interface PickerActions {
    fun loadIndex(force: Boolean = false) {}
    fun openFolder(path: String) {}
    fun navigateUp() {}
    fun toggleFolder(path: String) {}
    fun clearFolderSelection() {}
    fun setIncludeSubfolders(include: Boolean) {}
    fun setScanImages(enabled: Boolean) {}
    fun setScanVideos(enabled: Boolean) {}
    fun startScan() {}
}

/** What the results screen can ask for. */
interface ResultsActions {
    fun backToPicker() {}
    fun toggleItem(uri: Uri) {}
    fun selectAllButOne(keepNewest: Boolean) {}
    fun clearItemSelection() {}
    fun deleteSelected() {}
}

class ScanViewModel(application: Application) :
    AndroidViewModel(application), PickerActions, ResultsActions {

    private val repository = MediaRepository(application)
    private val resolver = application.contentResolver

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var allItems: List<MediaItem> = emptyList()
    private var scanJob: Job? = null

    // Delete bookkeeping across system consent dialogs.
    private var pendingDelete: List<MediaItem> = emptyList()
    private val legacyDeleted = ArrayList<MediaItem>()
    private var legacyFailed = 0

    // region Folder picker

    override fun loadIndex(force: Boolean) {
        val current = _state.value
        if (current.loadingIndex || (!force && current.folders != null)) return
        _state.update { it.copy(loadingIndex = true) }
        viewModelScope.launch {
            val items = repository.loadAll()
            val index = withContext(Dispatchers.Default) { FolderIndex.build(items) }
            allItems = items
            _state.update { it.copy(loadingIndex = false).withIndex(index) }
        }
    }

    override fun openFolder(path: String) {
        _state.update { if (it.folders?.byPath?.containsKey(path) == true) it.copy(currentPath = path) else it }
    }

    override fun navigateUp() {
        _state.update { s ->
            val current = s.currentPath ?: return@update s
            if (current == s.folders?.root?.path) return@update s
            val parent = current.substringBeforeLast('/')
            if (s.folders?.byPath?.containsKey(parent) == true) s.copy(currentPath = parent) else s
        }
    }

    override fun toggleFolder(path: String) {
        _state.update { s ->
            val selected = if (path in s.selectedFolders) s.selectedFolders - path else s.selectedFolders + path
            s.copy(selectedFolders = selected).withMatchCount()
        }
    }

    override fun clearFolderSelection() {
        _state.update { it.copy(selectedFolders = emptySet()).withMatchCount() }
    }

    override fun setIncludeSubfolders(include: Boolean) {
        _state.update { it.copy(includeSubfolders = include).withMatchCount() }
    }

    override fun setScanImages(enabled: Boolean) {
        _state.update { it.copy(scanImages = enabled).withMatchCount() }
    }

    override fun setScanVideos(enabled: Boolean) {
        _state.update { it.copy(scanVideos = enabled).withMatchCount() }
    }

    private fun UiState.withIndex(index: FolderIndex): UiState = copy(
        folders = index,
        currentPath = currentPath?.takeIf { index.byPath.containsKey(it) } ?: index.root?.path,
        selectedFolders = selectedFolders.filterTo(HashSet()) { index.byPath.containsKey(it) },
    ).withMatchCount()

    private fun UiState.withMatchCount(): UiState = copy(matchCount = matchingItems(this).size)

    private fun matchingItems(s: UiState): List<MediaItem> {
        if (s.selectedFolders.isEmpty()) return emptyList()
        val prefixes = if (s.includeSubfolders) s.selectedFolders.map { "$it/" } else emptyList()
        return allItems.filter { item ->
            (if (item.isVideo) s.scanVideos else s.scanImages) &&
                (item.dir in s.selectedFolders || prefixes.any { item.dir.startsWith(it) })
        }
    }

    // endregion

    // region Scan

    override fun startScan() {
        val targets = matchingItems(_state.value)
        if (targets.size < 2 || scanJob?.isActive == true) return
        val finder = DuplicateFinder(resolver, MediaPermissions.canReadOriginals(getApplication()))
        _state.update {
            it.copy(
                screen = Screen.SCANNING,
                progress = null,
                groups = emptyList(),
                selectedForDelete = emptySet(),
                scannedCount = targets.size,
            )
        }
        scanJob = viewModelScope.launch {
            val groups = withContext(Dispatchers.IO) {
                finder.find(targets) { progress -> _state.update { it.copy(progress = progress) } }
            }
            _state.update { it.copy(screen = Screen.RESULTS, groups = groups, progress = null) }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(screen = Screen.PICKER, progress = null) }
    }

    override fun backToPicker() {
        _state.update { it.copy(screen = Screen.PICKER, groups = emptyList(), selectedForDelete = emptySet()) }
    }

    // endregion

    // region Result selection

    override fun toggleItem(uri: Uri) {
        _state.update { s ->
            s.copy(selectedForDelete = if (uri in s.selectedForDelete) s.selectedForDelete - uri else s.selectedForDelete + uri)
        }
    }

    /** Marks every copy except one per group; the kept copy is the oldest or newest. */
    override fun selectAllButOne(keepNewest: Boolean) {
        _state.update { s ->
            val selection = HashSet<Uri>()
            for (group in s.groups) {
                val keep = if (keepNewest) group.items.maxBy { it.dateModified } else group.items.minBy { it.dateModified }
                group.items.forEach { if (it !== keep) selection += it.uri }
            }
            s.copy(selectedForDelete = selection)
        }
    }

    override fun clearItemSelection() {
        _state.update { it.copy(selectedForDelete = emptySet()) }
    }

    // endregion

    // region Delete

    override fun deleteSelected() {
        val s = _state.value
        val items = s.groups.flatMap { it.items }.filter { it.uri in s.selectedForDelete }
        if (items.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // One system dialog covers the whole batch; the system performs the delete.
            pendingDelete = items
            val sender = MediaStore.createDeleteRequest(resolver, items.map { it.uri }).intentSender
            _state.update { it.copy(pendingConsent = sender) }
        } else {
            legacyDeleted.clear()
            legacyFailed = 0
            runLegacyDelete(items, firstHasConsent = false)
        }
    }

    fun onConsentLaunched() {
        _state.update { it.copy(pendingConsent = null) }
    }

    fun onConsentResult(granted: Boolean) {
        val pending = pendingDelete
        pendingDelete = emptyList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (granted) finishDelete(pending, failed = 0)
        } else if (granted) {
            runLegacyDelete(pending, firstHasConsent = true)
        } else {
            finishDelete(legacyDeleted.toList(), legacyFailed)
        }
    }

    /**
     * Pre-Android 11 path: delete one by one. On Android 10 a file we don't own may need
     * per-file user consent, in which case we pause and resume after the dialog.
     */
    private fun runLegacyDelete(queue: List<MediaItem>, firstHasConsent: Boolean) {
        viewModelScope.launch {
            val consent: Pair<IntentSender, List<MediaItem>>? = withContext(Dispatchers.IO) {
                for ((index, item) in queue.withIndex()) {
                    try {
                        if (resolver.delete(item.uri, null, null) > 0) legacyDeleted += item else legacyFailed++
                    } catch (e: SecurityException) {
                        val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            (e as? RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                        } else {
                            null
                        }
                        val alreadyAsked = index == 0 && firstHasConsent
                        if (sender != null && !alreadyAsked) {
                            return@withContext sender to queue.subList(index, queue.size)
                        }
                        legacyFailed++
                    }
                }
                null
            }
            if (consent != null) {
                pendingDelete = consent.second
                _state.update { it.copy(pendingConsent = consent.first) }
            } else {
                finishDelete(legacyDeleted.toList(), legacyFailed)
            }
        }
    }

    private fun finishDelete(removed: List<MediaItem>, failed: Int) {
        val gone = removed.mapTo(HashSet()) { it.uri }
        if (gone.isNotEmpty()) {
            allItems = allItems.filterNot { it.uri in gone }
        }
        val index = FolderIndex.build(allItems)
        val message = buildString {
            if (removed.isNotEmpty()) append("Deleted ${removed.size} file${if (removed.size == 1) "" else "s"}")
            if (failed > 0) {
                if (isNotEmpty()) append(" · ")
                append("$failed could not be deleted")
            }
        }.ifEmpty { null }

        _state.update { s ->
            val groups = s.groups.mapNotNull { group ->
                val left = group.items.filterNot { it.uri in gone }
                if (left.size > 1) group.copy(items = left) else null
            }
            val remaining = groups.flatMapTo(HashSet()) { g -> g.items.map { it.uri } }
            s.copy(
                groups = groups,
                selectedForDelete = s.selectedForDelete.filterTo(HashSet()) { it in remaining },
                message = message,
            ).withIndex(index)
        }
    }

    fun onMessageShown() {
        _state.update { it.copy(message = null) }
    }

    // endregion
}
