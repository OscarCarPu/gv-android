package com.gv.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gv.app.ui.theme.GvColors
import com.gv.app.ui.theme.LocalSpacing

// Small pieces every feature screen shares, so a button or a chip looks and sizes the same
// whichever tab it sits on.

/** The compact action button every row uses; a card must not grow taller for its buttons. */
@Composable
internal fun SmallButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = GvColors.Primary,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(32.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = GvColors.Text,
            disabledContainerColor = color.copy(alpha = 0.25f),
            disabledContentColor = GvColors.Text.copy(alpha = 0.5f),
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Box(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
internal fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) GvColors.Primary.copy(alpha = 0.18f) else GvColors.BgLight)
            .border(1.dp, if (active) GvColors.Primary else GvColors.BorderLight, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) GvColors.Primary else GvColors.TextMuted,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
internal fun EmptyHint(text: String) {
    val spacing = LocalSpacing.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(GvColors.BgLight)
            .border(1.dp, GvColors.BorderLight, RoundedCornerShape(10.dp))
            .padding(spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = GvColors.TextMuted)
    }
}

@Composable
internal fun gvFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = GvColors.Text,
    unfocusedTextColor = GvColors.Text,
    focusedBorderColor = GvColors.Primary,
    unfocusedBorderColor = GvColors.BorderLight,
    focusedLabelColor = GvColors.Primary,
    unfocusedLabelColor = GvColors.TextMuted,
    cursorColor = GvColors.Primary,
)
