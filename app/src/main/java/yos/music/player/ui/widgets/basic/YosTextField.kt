package yos.music.player.ui.widgets.basic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight

@Composable
fun SearchTextField(
    text: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    //val keyboardController = LocalSoftwareKeyboardController.current
    val fontSize = 17.sp
    Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground) {
        Row(
            modifier = modifier
                .background(
                    (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.25f),
                    RoundedCornerShape(10.dp)
                )
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        placeholder,
                        fontSize = fontSize,
                        modifier = Modifier.alpha(0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.Black withNight Color.White,
                        fontSize = fontSize
                    ),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSearch()
                        //keyboardController?.hide() // 隐藏软键盘
                    }),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

/** 相邻输入框的组内位置：决定外沿圆角与分割线归属 */
enum class YosDialogFieldPosition { Single, First, Middle, Last }

/**
 * 弹窗输入框：iOS 表单单元格风格——纯白底、外沿 0.5dp 细线，相邻字段
 * 以 [YosDialogFieldPosition] 拼成一组（首字段上圆角、末字段下圆角、
 * 中间收方，交界处的描边叠出细分隔线）。
 */
@Composable
fun YosDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    position: YosDialogFieldPosition = YosDialogFieldPosition.Single,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = when (position) {
        YosDialogFieldPosition.Single -> YosRoundedCornerShape(12.dp)
        YosDialogFieldPosition.First -> YosRoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        YosDialogFieldPosition.Middle -> YosRoundedCornerShape(0.dp)
        YosDialogFieldPosition.Last -> YosRoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
    }
    val fieldBackground = Color.White withNight Color(0xFF2C2C30)
    val hairline = (Color.Black withNight Color.White).copy(alpha = 0.15f)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(
            color = Color.Black withNight Color.White,
            fontSize = 16.sp
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(fieldBackground, shape)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 15.sp,
                            color = (Color.Black withNight Color.White).copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
                // 相邻字段间只以横向细线分割，不带外框。
                // 分割线画在未缩进的外层，直接全宽；不能用负 padding 抵消
                // 内层缩进（Compose padding 不接受负值，会直接崩溃）。
                if (position == YosDialogFieldPosition.First || position == YosDialogFieldPosition.Middle) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(hairline)
                    )
                }
            }
        }
    )
}
