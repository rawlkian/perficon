package com.kian.perficon.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun VerticalScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val layoutInfo = gridState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems == 0) return

    val firstVisibleItem = gridState.firstVisibleItemIndex
    val visibleItems = layoutInfo.visibleItemsInfo.size
    
    if (visibleItems >= totalItems) return

    val scrollPercent = firstVisibleItem.toFloat() / (totalItems - visibleItems).coerceAtLeast(1)
    val thumbHeightPercent = (visibleItems.toFloat() / totalItems).coerceIn(0.1f, 1f)

    BoxWithConstraints(modifier = modifier.fillMaxHeight().width(4.dp)) {
        val maxHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightDp = with(density) { (maxHeightPx * thumbHeightPercent).toDp() }
        val thumbOffsetDp = with(density) { ((maxHeightPx - (maxHeightPx * thumbHeightPercent)) * scrollPercent).toDp() }

        Box(
            modifier = Modifier
                .offset(y = thumbOffsetDp)
                .height(thumbHeightDp)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        )
    }
}
