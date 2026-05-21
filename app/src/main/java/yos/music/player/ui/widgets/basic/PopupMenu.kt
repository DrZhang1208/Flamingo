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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
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
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
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

    // Get screen height for positioning
    val screenHeightPx = with(density) { android.content.res.Resources.getSystem().displayMetrics.heightPixels.toFloat() }
    val menuHeightEstimate = with(density) { 160.dp.toPx() } // ~3 items
    val showAbove = (buttonPosition.y + menuHeightEstimate) > screenHeightPx

    // Horizontal: right-aligned if button near right edge
    val alignRight = with(density) { buttonPosition.x > 200.dp.toPx() }
    val hPad = if (alignRight) Modifier.padding(end = 12.dp) else Modifier.padding(start = 12.dp)

    Popup(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
        ) {
            if (showAbove) {
                // Show menu above button: align to bottom of screen
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
                    Box(Modifier.height(with(density) { (screenHeightPx - buttonPosition.y + 20.dp.toPx()).toDp() }))
                    AnimatedVisibility(
                        modifier = Modifier.fillMaxWidth().then(hPad),
                        visible = showPopup.value,
                        enter = fadeIn(tween(200)) + scaleIn(
                            initialScale = 0.8f, animationSpec = tween(250),
                            transformOrigin = TransformOrigin(0.95f, if (showAbove) 1f else 0f)
                        ),
                        exit = fadeOut(tween(150)) + scaleOut(
                            targetScale = 0.8f, animationSpec = tween(150),
                            transformOrigin = TransformOrigin(0.95f, if (showAbove) 1f else 0f)
                        )
                    ) {
                        PopupMenuContent(shadow.value, alignRight, items)
                    }
                }
            } else {
                // Show menu below button
                AnimatedVisibility(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = with(density) { (buttonPosition.y + 4.dp.toPx()).toDp() })
                        .then(hPad),
                    visible = showPopup.value,
                    enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.8f, animationSpec = tween(250), transformOrigin = TransformOrigin(0.95f, 0f)),
                    exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.8f, animationSpec = tween(150), transformOrigin = TransformOrigin(0.95f, 0f))
                ) {
                    PopupMenuContent(shadow.value, alignRight, items)
                }
            }
        }
    }
}

@Composable
private fun PopupMenuContent(shadowValue: Float, alignRight: Boolean, items: List<PopupMenuItem>) {
    val shape = RoundedCornerShape(10.dp)
    val menuAlign = if (alignRight) Alignment.TopEnd else Alignment.TopStart
    Box(Modifier.fillMaxWidth(), contentAlignment = menuAlign) {
        Column(
            Modifier
                .graphicsLayer { this.shape = shape; shadowElevation = shadowValue; clip = true }
                .background(Color(0xF2E9E9E9) withNight Color(0xFA161616), shape)
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) PopupMenuDivider()
                PopupMenuItemRow(item.label, item.icon, item.onClick)
            }
        }
    }
}

@Composable
private fun PopupMenuItemRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth(0.618f)
            .height(48.dp)
            .background((Color.White withNight Color.Black).copy(alpha = 0.68f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, fontSize = 17.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).alpha(0.9f).padding(end = 18.dp)
        )
        Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun PopupMenuDivider() = Spacer(
    Modifier
        .fillMaxWidth(0.618f)
        .alpha(0.1f)
        .height(0.5.dp)
        .background((Color.Black withNight Color.White).copy(alpha = 0.1f))
)