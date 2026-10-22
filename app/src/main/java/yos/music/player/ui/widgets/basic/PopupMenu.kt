package yos.music.player.ui.widgets.basic

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import yos.music.player.ui.theme.withNight

data class PopupMenuItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun PopupMenu(
    items: List<PopupMenuItem>,
    buttonPosition: Offset,
    expanded: Boolean,
    onDismiss: () -> Unit,
    dark: Boolean = false
) {
    val showPopup = remember { mutableStateOf(false) }
    val shadow = animateFloatAsState(
        targetValue = if (showPopup.value) 225f else 0f,
        animationSpec = tween(200)
    )
    showPopup.value = expanded

    if (!expanded) return

    BackHandler(enabled = expanded) {
        showPopup.value = false
        onDismiss()
    }

    Popup(onDismissRequest = onDismiss) {
        // 半透明遮罩
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = showPopup.value,
                enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(250)),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150))
            ) {
                PopupMenuContent(shadow.value, items, dark)
            }
        }
    }
}

@Composable
private fun PopupMenuContent(shadowValue: Float, items: List<PopupMenuItem>, dark: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    val menuBackground = if (dark) Color(0xFA161616) else Color(0xF2E9E9E9) withNight Color(0xFA161616)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer { this.shape = shape; shadowElevation = shadowValue; clip = true }
                .background(menuBackground, shape)
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) PopupMenuDivider(dark)
                PopupMenuItemRow(item.label, item.icon, item.onClick, dark)
            }
        }
    }
}

@Composable
private fun PopupMenuItemRow(label: String, icon: ImageVector, onClick: () -> Unit, dark: Boolean) {
    val rowBackground = if (dark) Color.Black.copy(alpha = 0.68f) else (Color.White withNight Color.Black).copy(alpha = 0.68f)
    val iconTint = if (dark) Color.White else MaterialTheme.colorScheme.onBackground
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(rowBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, fontSize = 17.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (dark) Color.White.copy(alpha = 0.9f) else Color.Unspecified,
            modifier = Modifier.weight(1f).alpha(0.9f).padding(end = 18.dp)
        )
        Icon(icon, null, Modifier.size(24.dp), tint = iconTint)
    }
}

@Composable
private fun PopupMenuDivider(dark: Boolean = false) = Spacer(
    Modifier
        .fillMaxWidth()
        .alpha(0.1f)
        .height(0.5.dp)
        .background(if (dark) Color.White.copy(alpha = 0.1f) else (Color.Black withNight Color.White).copy(alpha = 0.1f))
)