package com.sahas.twinscan

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sahas.twinscan.ui.TwinScanApp
import com.sahas.twinscan.ui.theme.ThemeMode
import com.sahas.twinscan.ui.theme.ThemeSettings
import com.sahas.twinscan.ui.theme.TwinScanTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ScanViewModel by viewModels()
    private var access by mutableStateOf(MediaAccess.NONE)
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        access = MediaPermissions.currentAccess(this)
        themeMode = ThemeSettings.load(this)
        setContent {
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Keep status/navigation bar icons readable when the app theme differs from the system's.
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            TwinScanTheme(darkTheme = darkTheme) {
                TwinScanApp(
                    viewModel = viewModel,
                    access = access,
                    onPermissionResult = {
                        access = MediaPermissions.currentAccess(this)
                        // The visible set may have changed (e.g. a new partial selection).
                        if (access != MediaAccess.NONE) viewModel.loadIndex(force = true)
                    },
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        themeMode = mode
                        ThemeSettings.save(this, mode)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Access can be changed in system settings while we're in the background.
        access = MediaPermissions.currentAccess(this)
    }
}
