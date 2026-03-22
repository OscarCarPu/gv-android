package com.gv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.gv.app.AppColors
import com.gv.app.domain.model.ActiveTreeNode

@Composable
fun TreeNodeRow(
    node: ActiveTreeNode,
    depth: Int,
    expandedIds: Set<Int>,
    onToggleExpand: (Int) -> Unit,
    onNodeClick: (ActiveTreeNode) -> Unit,
    onStartTimer: (ActiveTreeNode) -> Unit
) {
    val isExpanded = node.id in expandedIds
    val hasChildren = !node.children.isNullOrEmpty()
    val rotation by animateFloatAsState(if (isExpanded) 90f else 0f, label = "chevron")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNodeClick(node) }
                .padding(start = (depth * 20).dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Expand/collapse
            if (hasChildren) {
                IconButton(
                    onClick = { onToggleExpand(node.id) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                        modifier = Modifier.rotate(rotation)
                    )
                }
            } else {
                Spacer(Modifier.size(28.dp))
            }

            // Type indicator
            Text(
                text = if (node.type == "project") "P" else "T",
                style = MaterialTheme.typography.labelSmall,
                color = if (node.type == "project") AppColors.secondary else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 4.dp)
            )

            // Name
            Column(modifier = Modifier.weight(1f)) {
                Text(node.name, style = MaterialTheme.typography.bodyMedium)
                StatusBadge(startedAt = node.startedAt, finishedAt = null)
            }

            // Play button for tasks
            if (node.type == "task") {
                IconButton(
                    onClick = { onStartTimer(node) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Iniciar timer",
                        tint = AppColors.success
                    )
                }
            }
        }

        // Render children if expanded
        if (isExpanded && hasChildren) {
            node.children?.forEach { child ->
                TreeNodeRow(
                    node = child,
                    depth = depth + 1,
                    expandedIds = expandedIds,
                    onToggleExpand = onToggleExpand,
                    onNodeClick = onNodeClick,
                    onStartTimer = onStartTimer
                )
            }
        }
    }
}
