package com.gv.app.ui.money

import com.gv.app.ui.common.EmptyHint
import com.gv.app.ui.common.SmallButton
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gv.app.domain.model.Category
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

/**
 * Categories as on the web: grouped by type (Income, Expenses, Transfers), each a tree that
 * starts **collapsed** — tap a chevron to open a branch. A header with **New**; delete is immediate.
 */
@Composable
internal fun CategoriesTab(
    categories: List<Category>,
    onNew: () -> Unit,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit,
) {
    val spacing = LocalSpacing.current
    val groups = listOf(
        Triple("income", "Income", GvColors.Success),
        Triple("expense", "Expenses", GvColors.Danger),
        Triple("transfer", "Transfers", GvColors.Secondary),
    )
    val expanded = rememberSaveable(saver = expandedSetSaver()) { mutableStateOf(emptySet<Int>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        item(key = "categories-header") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Categories", style = MaterialTheme.typography.titleMedium, color = GvColors.Text, modifier = Modifier.weight(1f))
                SmallButton("New", onNew, icon = Icons.Filled.Add)
            }
        }
        if (categories.isEmpty()) item(key = "categories-empty") { EmptyHint("No categories") }
        groups.forEach { (type, label, accent) ->
            val rows = buildCategoryTreeRows(categories.filter { it.type == type })
            if (rows.isEmpty()) return@forEach
            item(key = "header-$type") { GroupHeader(label = label, accent = accent, count = rows.size) }
            items(items = visibleCategoryRows(rows, expanded.value), key = { "cat-${it.category.id}" }) { row ->
                CategoryTreeNode(
                    row = row,
                    accent = accent,
                    isCollapsed = row.category.id !in expanded.value,
                    onToggle = {
                        expanded.value = expanded.value.toMutableSet().apply {
                            if (!add(row.category.id)) remove(row.category.id)
                        }
                    },
                    onEdit = { onEdit(row.category) },
                    onDelete = { onDelete(row.category) },
                )
            }
        }
    }
}

private fun expandedSetSaver(): androidx.compose.runtime.saveable.Saver<androidx.compose.runtime.MutableState<Set<Int>>, ArrayList<Int>> =
    androidx.compose.runtime.saveable.Saver(
        save = { ArrayList(it.value) },
        restore = { androidx.compose.runtime.mutableStateOf(it.toSet()) },
    )

@Composable
private fun GroupHeader(label: String, accent: Color, count: Int) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.lg, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "($count)",
            style = MaterialTheme.typography.labelMedium,
            color = GvColors.TextMuted,
        )
    }
}

@Composable
private fun CategoryTreeNode(
    row: CategoryTreeRow,
    accent: Color,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val rowBg = if (row.depth == 0) GvColors.BgLight else GvColors.BgLight.copy(alpha = 0.6f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBg)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
            .padding(vertical = spacing.sm, horizontal = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        TreeConnectors(
            depth = row.depth,
            isLast = row.isLast,
            ancestorHasMore = row.ancestorHasMore,
            accent = accent,
        )

        if (row.hasChildren) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = if (isCollapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
                    contentDescription = if (isCollapsed) "Expand" else "Collapse",
                    tint = GvColors.TextMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Box(modifier = Modifier.size(28.dp))
        }

        Text(
            text = row.category.name,
            style = if (row.depth == 0) MaterialTheme.typography.bodyMedium
                else MaterialTheme.typography.bodySmall,
            color = GvColors.Text,
            fontWeight = if (row.depth == 0) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Edit",
                tint = GvColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = GvColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TreeConnectors(
    depth: Int,
    isLast: Boolean,
    ancestorHasMore: BooleanArray,
    accent: Color,
) {
    if (depth == 0) return
    val columnWidth = 18.dp
    val lineColor = accent.copy(alpha = 0.35f)
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .size(width = columnWidth * depth, height = 40.dp),
    ) {
        val colPx = columnWidth.toPx()
        val midY = size.height / 2f
        val strokeWidth = 1.5f.dp.toPx()
        // vertical guide lines for ancestors
        for (i in 0 until depth - 1) {
            if (i < ancestorHasMore.size && ancestorHasMore[i]) {
                val x = colPx * (i + 0.5f)
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = strokeWidth,
                )
            }
        }
        // branch at this row's column
        val xBranch = colPx * (depth - 1 + 0.5f)
        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(xBranch, 0f),
            end = androidx.compose.ui.geometry.Offset(xBranch, if (isLast) midY else size.height),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = lineColor,
            start = androidx.compose.ui.geometry.Offset(xBranch, midY),
            end = androidx.compose.ui.geometry.Offset(colPx * depth, midY),
            strokeWidth = strokeWidth,
        )
    }
}
