package com.sahas.twinscan.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sahas.twinscan.MediaAccess
import com.sahas.twinscan.MediaPermissions
import com.sahas.twinscan.R
import com.sahas.twinscan.ScanViewModel
import com.sahas.twinscan.Screen
import com.sahas.twinscan.data.ScanProgress
import com.sahas.twinscan.ui.theme.ThemeMode
import com.sahas.twinscan.ui.theme.TwinScanTheme

private enum class Destination { PERMISSION, PICKER, SCANNING, RESULTS }

@Composable
fun TwinScanApp(
    viewModel: ScanViewModel,
    access: MediaAccess,
    onPermissionResult: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onPermissionResult() }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK) }

    LaunchedEffect(access) {
        if (access != MediaAccess.NONE) viewModel.loadIndex()
    }
    LaunchedEffect(state.pendingConsent) {
        state.pendingConsent?.let { sender ->
            consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
            viewModel.onConsentLaunched()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    val requestPermissions = { permissionLauncher.launch(MediaPermissions.required()) }

    val destination = when {
        access == MediaAccess.NONE -> Destination.PERMISSION
        state.screen == Screen.SCANNING -> Destination.SCANNING
        state.screen == Screen.RESULTS -> Destination.RESULTS
        else -> Destination.PICKER
    }

    AnimatedContent(
        targetState = destination,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
        label = "screen",
    ) { target ->
        when (target) {
            Destination.PERMISSION -> PermissionScreen(
                onGrant = requestPermissions,
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                    )
                },
            )

            Destination.SCANNING -> ScanningScreen(
                progress = state.progress,
                fileCount = state.scannedCount,
                onCancel = viewModel::cancelScan,
            )

            Destination.RESULTS -> ResultsScreen(state, viewModel, snackbarHostState)

            Destination.PICKER -> FolderPickerScreen(
                state = state,
                viewModel = viewModel,
                partialAccess = access == MediaAccess.PARTIAL,
                onManageAccess = requestPermissions,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            IconBadge(icon = R.drawable.ic_image, size = 104.dp, shape = CircleShape)
            Spacer(Modifier.height(28.dp))
            Text("Welcome to TwinScan", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Find and remove duplicate photos and videos to free up space.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Promise("Files are compared on your device. Nothing is uploaded.")
                    Promise("You choose exactly which folders are scanned.")
                    Promise("Nothing is deleted until you confirm it.")
                }
            }
            Spacer(Modifier.weight(1f))
            Button(onClick = onGrant, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Allow access to photos and videos")
            }
            TextButton(onClick = onOpenSettings) { Text("Open app settings") }
        }
    }
}

@Composable
private fun Promise(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(
            icon = R.drawable.ic_check,
            size = 28.dp,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ScanningScreen(progress: ScanProgress?, fileCount: Int, onCancel: () -> Unit) {
    BackHandler(onBack = onCancel)

    val fraction by animateFloatAsState(
        targetValue = progress?.fraction?.coerceIn(0f, 1f) ?: 0f,
        label = "scanProgress",
    )
    val step = if (progress?.stage == ScanProgress.Stage.FULL_CHECK) 2 else 1

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                val ringModifier = Modifier.size(200.dp)
                if (progress == null) {
                    CircularProgressIndicator(
                        modifier = ringModifier,
                        strokeWidth = 12.dp,
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { fraction },
                        modifier = ringModifier,
                        strokeWidth = 12.dp,
                        strokeCap = StrokeCap.Round,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.displaySmall)
                        Text(
                            "Step $step of 2",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
            Text(
                text = when (progress?.stage) {
                    ScanProgress.Stage.QUICK_CHECK -> "Finding likely matches"
                    ScanProgress.Stage.FULL_CHECK -> "Confirming exact duplicates"
                    null -> "Getting ready"
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (progress == null) "Comparing ${plural(fileCount, "file")}"
                else "${formatCount(progress.done)} of ${plural(progress.total, "file")} checked",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = progress?.currentName.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Large videos can take a while. Nothing is changed during a scan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Cancel scan")
            }
        }
    }
}

// region Previews

@ThemePreviews
@Composable
private fun PermissionScreenPreview() {
    TwinScanTheme { PermissionScreen(onGrant = {}, onOpenSettings = {}) }
}

@ThemePreviews
@Composable
private fun ScanningScreenPreview() {
    TwinScanTheme {
        ScanningScreen(
            progress = ScanProgress(
                stage = ScanProgress.Stage.FULL_CHECK,
                fraction = 0.64f,
                done = 38,
                total = 112,
                currentName = "VID_20260612_181204.mp4",
            ),
            fileCount = 1258,
            onCancel = {},
        )
    }
}

// endregion
