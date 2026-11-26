package com.sahas.twinscan.ui

import android.content.Context
import android.os.Environment
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sahas.twinscan.R
import com.sahas.twinscan.ui.theme.ThemeMode

/** An icon on a tinted, rounded background. */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = MaterialTheme.shapes.small,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/** Round check mark used for "this is selected" in lists. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(colors.primary)
                else Modifier.border(2.dp, colors.outline, CircleShape)
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Full-screen message with an icon, used for empty and finished states. */
@Composable
fun MessageState(
    @DrawableRes icon: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(
            icon = icon,
            size = 96.dp,
            shape = CircleShape,
            containerColor = containerColor,
            contentColor = contentColor,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

/** Top-bar button that lets the user pick system, light or dark appearance. */
@Composable
fun ThemeMenuButton(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(themeIcon(mode)), contentDescription = "Appearance")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ThemeMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = { Icon(painterResource(themeIcon(option)), contentDescription = null) },
                    trailingIcon = {
                        if (option == mode) {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onModeChange(option)
                    },
                )
            }
        }
    }
}

@DrawableRes
private fun themeIcon(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.drawable.ic_theme_auto
    ThemeMode.LIGHT -> R.drawable.ic_light_mode
    ThemeMode.DARK -> R.drawable.ic_dark_mode
}

/** Path of primary shared storage; falls back to the usual location where it can't be read (previews). */
val storageRoot: String by lazy {
    runCatching { Environment.getExternalStorageDirectory().path }.getOrNull() ?: "/storage/emulated/0"
}

fun formatSize(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

fun formatCount(count: Int): String = "%,d".format(count)

/** "1 file", "2,340 files". */
fun plural(count: Int, noun: String): String = "${formatCount(count)} $noun${if (count == 1) "" else "s"}"
