package com.notely.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notely.app.ui.theme.LocalNotelyColors

/**
 * Premium soft claymorphism primitives: soft shadows, rounded surfaces, subtle borders,
 * and a gentle press-scale so every tappable surface feels physical.
 */

@Composable
fun Modifier.clayPress(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 900f), label = "clayPress")
    return this
        .scale(scale)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun Modifier.claySurface(radius: Int = 20, elevated: Boolean = true, color: Color? = null): Modifier {
    val c = LocalNotelyColors.current
    val shape = RoundedCornerShape(radius.dp)
    return this
        .then(
            if (elevated) Modifier.shadow(
                elevation = 8.dp, shape = shape,
                spotColor = Color.Black.copy(alpha = if (c.isDark) 0.35f else 0.10f),
                ambientColor = Color.Black.copy(alpha = if (c.isDark) 0.20f else 0.05f)
            ) else Modifier
        )
        .clip(shape)
        .background(color ?: c.surface)
        .border(1.dp, c.border, shape)
}

@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color? = null,
    padding: Int = 16,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier.claySurface(20, color = color)
    if (onClick != null) {
        Box(modifier = base.clayPress(onClick)) {
            Column(Modifier.padding(padding.dp), content = content)
        }
    } else {
        Box(modifier = base) {
            Column(Modifier.padding(padding.dp), content = content)
        }
    }
}

@Composable
fun ClayIconTile(
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 44,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = color.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Clay primary button.
 *
 * Two overloads on purpose: Compose/Kotlin only allows a trailing lambda when the
 * *last* parameter is a function type. Call sites in this app are written as
 * `ClayPrimaryButton("Save") { save() }`, so the second overload exists to keep
 * that natural syntax compiling while `enabled` and `modifier` stay available.
 */
@Composable
fun ClayPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val c = LocalNotelyColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 900f), label = "primary")
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.height(52.dp).scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.primary, contentColor = c.onPrimary,
            disabledContainerColor = c.primary.copy(alpha = 0.4f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun ClayPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) = ClayPrimaryButton(text = text, modifier = modifier, enabled = enabled, onClick = onClick)

@Composable
fun ClaySoftButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalNotelyColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.border),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = c.text, containerColor = c.surface
        )
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SectionLabel(text: String) {
    val c = LocalNotelyColors.current
    Text(
        text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        color = c.text2, letterSpacing = 1.sp
    )
}

@Composable
fun ClaySoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    ClaySoftButton(text = text, modifier = modifier, onClick = onClick)

@Composable
fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    val c = LocalNotelyColors.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(color)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.border, CircleShape)
            .clayPress(onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Text("✓", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TagChip(text: String, onRemove: () -> Unit) {
    val c = LocalNotelyColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.primaryContainer)
            .border(1.dp, c.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#$text", color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(6.dp))
        Text("✕", color = c.primary, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onRemove))
    }
}

/** Small pill used for filters / sort options with a selected state. */
@Composable
fun ClayPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalNotelyColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.primary else c.surface)
            .border(1.dp, if (selected) c.primary else c.border, RoundedCornerShape(14.dp))
            .clayPress(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) c.onPrimary else c.text
        )
    }
}

/** The clay bottom sheet container used by every editor sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHeader(title: String, subtitle: String? = null, onClose: () -> Unit) {
    val c = LocalNotelyColors.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = c.text)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = c.text2)
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(c.surface2)
                .clayPress(onClose),
            contentAlignment = Alignment.Center
        ) { Text("✕", fontSize = 14.sp, color = c.text2) }
    }
}

@Composable
fun EmptyState(title: String, message: String, icon: String = "🗂") {
    val c = LocalNotelyColors.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
        Text(message, fontSize = 13.sp, color = c.text2, textAlign = TextAlign.Center)
    }
}
