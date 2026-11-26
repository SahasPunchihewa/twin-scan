package com.sahas.twinscan.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val TouchWidth = 28.dp
private val MinThumbHeight = 56.dp

/**
 * A draggable scrollbar for long lists. Lay it over the list, aligned to the end edge:
 * it follows the list as it scrolls, and dragging the thumb jumps through the list quickly.
 * It draws nothing when the whole list fits on screen.
 *
 * @param label text shown beside the thumb while dragging, given the first visible item index.
 */
@Composable
fun FastScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    label: ((firstVisibleIndex: Int) -> String?)? = null,
) {
    val metrics by remember(state) { derivedStateOf { scrollMetrics(state) } }
    val m = metrics ?: return
    if (m.maxScroll <= 0f) return

    val density = LocalDensity.current
    var trackPx by remember { mutableFloatStateOf(0f) }
    // Non-null while the user is dragging; the thumb then follows the finger exactly.
    var dragOffset by remember { mutableStateOf<Float?>(null) }
    val dragging = dragOffset != null

    val minThumbPx = with(density) { MinThumbHeight.toPx() }
    val thumbPx = (trackPx * m.visibleItems / m.total).coerceAtLeast(minThumbPx).coerceAtMost(trackPx)
    val travel = (trackPx - thumbPx).coerceAtLeast(1f)
    val thumbOffset = dragOffset ?: (m.progress * travel)

    val draggableState = rememberDraggableState { delta ->
        val newOffset = ((dragOffset ?: thumbOffset) + delta).coerceIn(0f, travel)
        dragOffset = newOffset
        val target = newOffset / travel * m.maxScroll
        val index = target.toInt().coerceIn(0, m.total - 1)
        state.requestScrollToItem(index, ((target - index) * m.averageItemPx).roundToInt())
    }

    val active = dragging || state.isScrollInProgress
    val colors = MaterialTheme.colorScheme
    val thumbColor by animateColorAsState(
        targetValue = when {
            dragging -> colors.primary
            active -> colors.onSurfaceVariant.copy(alpha = 0.75f)
            else -> colors.onSurfaceVariant.copy(alpha = 0.4f)
        },
        label = "thumbColor",
    )
    val thumbWidth by animateDpAsState(if (dragging) 10.dp else 6.dp, label = "thumbWidth")

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(TouchWidth)
            .onSizeChanged { trackPx = it.height.toFloat() }
            // Purely a touch affordance; the list itself is already scrollable for accessibility.
            .clearAndSetSemantics {},
    ) {
        if (trackPx <= 0f) return@Box

        // Wide invisible touch target with a slim visible thumb at its edge.
        Box(
            modifier = Modifier
                .offset { IntOffset(0, thumbOffset.roundToInt()) }
                .width(TouchWidth)
                .height(with(density) { thumbPx.toDp() })
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { dragOffset = thumbOffset },
                    onDragStopped = { dragOffset = null },
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .background(thumbColor, CircleShape)
            )
        }

        val text = if (dragging) label?.invoke(state.firstVisibleItemIndex) else null
        if (text != null) {
            Surface(
                color = colors.inverseSurface,
                contentColor = colors.inverseOnSurface,
                shape = CircleShape,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .offset { IntOffset(-TouchWidth.roundToPx(), (thumbOffset + thumbPx / 2).roundToInt() - 18.dp.roundToPx()) }
                    .wrapContentWidth(align = Alignment.End, unbounded = true),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Scroll position measured in items, which stays meaningful when item heights differ. */
private class ScrollMetrics(
    val total: Int,
    val visibleItems: Float,
    val scrolledItems: Float,
    val averageItemPx: Float,
) {
    val maxScroll: Float get() = (total - visibleItems).coerceAtLeast(0f)
    val progress: Float get() = if (maxScroll <= 0f) 0f else (scrolledItems / maxScroll).coerceIn(0f, 1f)
}

private fun scrollMetrics(state: LazyListState): ScrollMetrics? {
    val layout = state.layoutInfo
    val items = layout.visibleItemsInfo
    if (items.isEmpty() || layout.totalItemsCount == 0) return null

    var visible = 0f
    var sizeSum = 0
    for (item in items) {
        if (item.size <= 0) continue
        val top = maxOf(item.offset, layout.viewportStartOffset)
        val bottom = minOf(item.offset + item.size, layout.viewportEndOffset)
        visible += (bottom - top).coerceAtLeast(0).toFloat() / item.size
        sizeSum += item.size
    }
    val first = items.first()
    val hiddenPartOfFirst = if (first.size > 0) {
        (layout.viewportStartOffset - first.offset).coerceAtLeast(0).toFloat() / first.size
    } else 0f

    return ScrollMetrics(
        total = layout.totalItemsCount,
        visibleItems = visible,
        scrolledItems = first.index + hiddenPartOfFirst,
        averageItemPx = sizeSum.toFloat() / items.size,
    )
}
