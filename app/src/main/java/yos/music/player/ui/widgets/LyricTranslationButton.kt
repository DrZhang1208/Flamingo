package yos.music.player.ui.widgets

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild
import yos.music.player.R
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.effects.overlayEffect

@Composable
internal fun LyricTranslationButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    // 封面双重模糊色场：磨砂底（无则回退半透明白）
    frosted: ImageBitmap? = null
) {
    val backgroundAlpha = animateFloatAsState(if (checked) 0.92f else 0f, label = "TranslationBackground")
    val tint = animateColorAsState(if (checked) Color.Black else Color.White, label = "TranslationTint")
    val shape = remember { YosRoundedCornerShape(10.dp) }
    // 普通混合的磨砂底：不用 Add 层（会把模糊「叠加」在背景上而非透出背景）
    Box(modifier.size(34.dp).clip(shape)
        .drawBehind {
            if (frosted != null) {
                drawImage(frosted,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(frosted.width, frosted.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    alpha = 0.85f,
                    filterQuality = FilterQuality.Medium)
                drawRect(Color.White.copy(alpha = 0.08f))
            } else {
                drawRect(Color.White.copy(alpha = 0.10f))
            }
        }
        .drawBehind { drawRect(Color.White.copy(alpha = backgroundAlpha.value)) }
        .toggleable(checked, interactionSource = remember { MutableInteractionSource() },
            indication = null, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Translate,
                contentDescription = stringResource(if (checked) R.string.lyric_translation_hide else R.string.lyric_translation_show),
                tint = tint.value,
                modifier = Modifier.size(22.dp))
    }
}

/** 歌词工具按钮共用的磨砂样式：透玻璃——实时模糊按钮背后的播放页背景，
 *  tint 只留一丝保图标可辨，不加噪点，玻璃本身不改变底色。
 *  mask 把玻璃层裁成平滑圆角：haze 玻璃层画在 hazeChild 内部、按钮自身的
 *  clip/Clear 镂空都够不到它（人声控件外层还不能加 clip——会裁掉按压缩放动画），
 *  只能走 hazeChild 的 RenderEffect mask（SRC_IN） */
@Composable
internal fun toolButtonHazeStyle(hazeState: HazeState): Modifier {
    val density = LocalDensity.current
    val mask = remember(density) {
        RoundedMaskBrush(YosRoundedCornerShape(roundSize = 10.dp), density)
    }
    return Modifier.hazeChild(
        hazeState,
        HazeStyle(
            // haze 的 RenderEffect 路径要求垫底色非 Unspecified，否则首绘崩溃；
            // 必须透明：不透明垫底会把 blur 采样超界的小按钮填成直角实色块
            backgroundColor = Color.Transparent,
            blurRadius = 20.dp,
            tint = (Color.White withNight Color.Black).copy(alpha = 0.10f),
            noiseFactor = 0f
        ),
        mask = mask
    )
}

/** 圆角白形 BitmapShader：haze RenderEffect 链的 SRC_IN 遮罩，白=保留 */
private class RoundedMaskBrush(private val shape: Shape, private val density: Density) : ShaderBrush() {

    // 放大动画每帧尺寸滚动：按像素尺寸缓存，往返复用小位图
    private val cache = HashMap<Int, Shader>()

    override fun createShader(size: Size): Shader {
        val w = kotlin.math.ceil(size.width).toInt().coerceAtLeast(1)
        val h = kotlin.math.ceil(size.height).toInt().coerceAtLeast(1)
        val key = w shl 16 or h
        cache[key]?.let { return it }
        // 圆角路径必须画满整个位图（用整数像素尺寸生成 outline）：
        // 若按传入的 float 尺寸画，右/下边会留 <1px 透明缝，SRC_IN 把它当
        // "裁掉"，放大动画中尺寸小数滚动，边缘就持续闪裁切细线
        val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
        }
        when (val outline = shape.createOutline(Size(w.toFloat(), h.toFloat()), LayoutDirection.Ltr, density)) {
            is Outline.Generic -> canvas.drawPath(outline.path.asAndroidPath(), paint)
            is Outline.Rounded -> {
                val r = outline.roundRect
                canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, r.topLeftCornerRadius.x, r.topLeftCornerRadius.y, paint)
            }
            else -> canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        return android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
            .also {
                if (cache.size > 8) cache.clear()
                cache[key] = it
            }
    }
}
