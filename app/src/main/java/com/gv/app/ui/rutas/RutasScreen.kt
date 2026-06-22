package com.gv.app.ui.rutas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gv.app.data.local.db.ConcelloMarkEntity
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val PROVINCES = listOf("A Coruña", "Lugo", "Ourense", "Pontevedra")

@Composable
fun RutasScreen(vm: RutasViewModel = viewModel()) {
    val geo by vm.geo.collectAsStateWithLifecycle()
    val marks by vm.marks.collectAsStateWithLifecycle()
    val activeProvince by vm.activeProvince.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GvColors.Bg),
    ) {
        when (val g = geo) {
            is GeoState.Loading -> CenteredLoader()
            is GeoState.Error -> CenteredMessage("Couldn't load the map")
            is GeoState.Loaded -> {
                val visible = remember(g.concellos, activeProvince) {
                    if (activeProvince == null) g.concellos else g.concellos.filter { it.province == activeProvince }
                }
                val visited = visible.count { marks.containsKey(it.name) }

                Header(visited = visited, total = visible.size)
                ProvinceFilter(active = activeProvince, onSelect = vm::setProvince)
                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    MapCanvas(
                        concellos = g.concellos,
                        marks = marks,
                        activeProvince = activeProvince,
                        onTapConcello = vm::select,
                    )
                }
            }
        }
    }

    selected?.let { name ->
        MarkSheet(
            name = name,
            existing = marks[name],
            onDismiss = vm::clearSelection,
            onSave = { date, desc -> vm.saveMark(name, date, desc) },
            onRemove = { vm.removeMark(name) },
        )
    }
}

@Composable
private fun Header(visited: Int, total: Int) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xl, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Routes", style = MaterialTheme.typography.titleLarge, color = GvColors.Text)
        Text(
            "$visited/$total",
            style = MaterialTheme.typography.titleMedium,
            color = GvColors.Primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProvinceFilter(active: String?, onSelect: (String?) -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.xl, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        ProvinceChip("All", active == null) { onSelect(null) }
        PROVINCES.forEach { p -> ProvinceChip(p, active == p) { onSelect(p) } }
    }
}

@Composable
private fun ProvinceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) GvColors.Primary.copy(alpha = 0.18f) else GvColors.BgLight)
            .border(1.dp, if (selected) GvColors.Primary else GvColors.BorderLight, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) GvColors.Primary else GvColors.TextMuted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun MapCanvas(
    concellos: List<Concello>,
    marks: Map<String, ConcelloMarkEntity>,
    activeProvince: String?,
    onTapConcello: (String) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val projection = remember(concellos, size) {
        if (size.width > 0 && size.height > 0) {
            GeoProjection.fit(concellos, size.width.toFloat(), size.height.toFloat())
        } else null
    }
    val paths = remember(projection) {
        projection?.let { proj -> concellos.map { it to buildPath(it, proj) } } ?: emptyList()
    }

    val visitedFill = GvColors.Primary
    val unvisitedFill = GvColors.Surface
    val stroke = GvColors.BorderLight
    val activeStroke = GvColors.Primary

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(projection) {
                if (projection == null) return@pointerInput
                detectTapGestures { offset ->
                    concellos.firstOrNull { it.containsScreenPoint(offset.x, offset.y, projection) }
                        ?.let { onTapConcello(it.name) }
                }
            },
    ) {
        paths.forEach { (concello, path) ->
            val visited = marks.containsKey(concello.name)
            val dim = activeProvince != null && concello.province != activeProvince
            val fill = (if (visited) visitedFill else unvisitedFill).copy(alpha = if (dim) 0.2f else 1f)
            drawPath(path, color = fill)
            val isActiveProv = activeProvince != null && concello.province == activeProvince
            drawPath(
                path,
                color = (if (isActiveProv) activeStroke else stroke).copy(alpha = if (dim) 0.2f else 1f),
                style = Stroke(width = if (isActiveProv) 1.5f else 0.8f),
            )
        }
    }
}

private fun buildPath(concello: Concello, projection: GeoProjection): Path {
    val path = Path()
    for (ring in concello.rings) {
        if (ring.isEmpty()) continue
        path.moveTo(projection.x(ring[0][0]), projection.y(ring[0][1]))
        for (i in 1 until ring.size) {
            path.lineTo(projection.x(ring[i][0]), projection.y(ring[i][1]))
        }
        path.close()
    }
    return path
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkSheet(
    name: String,
    existing: ConcelloMarkEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onRemove: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var date by remember(name) { mutableStateOf(existing?.visitedOn?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()) }
    var description by remember(name) { mutableStateOf(existing?.description.orEmpty()) }
    var showPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GvColors.BgLight,
        contentColor = GvColors.Text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.xl, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge, color = GvColors.Text)
            if (existing != null) {
                Text("Visited", style = MaterialTheme.typography.labelMedium, color = GvColors.Success)
            }

            // Visit date
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text("Visited on", style = MaterialTheme.typography.labelMedium, color = GvColors.TextMuted)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(GvColors.Bg)
                        .border(1.dp, GvColors.BorderLight, RoundedCornerShape(8.dp))
                        .clickable { showPicker = true }
                        .padding(horizontal = spacing.lg, vertical = spacing.lg),
                ) {
                    Text(date.toString(), style = MaterialTheme.typography.bodyMedium, color = GvColors.Text)
                }
            }

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GvColors.Text,
                    unfocusedTextColor = GvColors.Text,
                    focusedBorderColor = GvColors.Primary,
                    unfocusedBorderColor = GvColors.BorderLight,
                    focusedLabelColor = GvColors.Primary,
                    unfocusedLabelColor = GvColors.TextMuted,
                    cursorColor = GvColors.Primary,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (existing != null) {
                    OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = GvColors.Danger, modifier = Modifier.size(16.dp))
                        Text(" Remove", color = GvColors.Danger)
                    }
                }
                Button(
                    onClick = { onSave(date.toString(), description.trim()) },
                    colors = ButtonDefaults.buttonColors(containerColor = GvColors.Primary, contentColor = GvColors.Text),
                    modifier = Modifier.weight(if (existing != null) 1f else 2f),
                ) { Text(if (existing != null) "Update" else "Mark visited") }
            }
        }
    }

    if (showPicker) {
        val initMillis = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showPicker = false
                }) { Text("OK", color = GvColors.Primary) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel", color = GvColors.TextMuted) } },
            colors = DatePickerDefaults.colors(containerColor = GvColors.BgLight),
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun CenteredLoader() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = GvColors.Primary)
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = GvColors.TextMuted)
    }
}
