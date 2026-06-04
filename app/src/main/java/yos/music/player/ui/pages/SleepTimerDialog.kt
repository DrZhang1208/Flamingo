package yos.music.player.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.alexzhirkevich.cupertino.CupertinoSlider
import io.github.alexzhirkevich.cupertino.theme.CupertinoTheme
import yos.music.player.code.SleepTimerManager
import yos.music.player.code.utils.others.Vibrator
import yos.music.player.ui.pages.settings.SwitchItem
import yos.music.player.ui.theme.YosRoundedCornerShape
import yos.music.player.ui.theme.withNight
import yos.music.player.ui.widgets.basic.OptionDialog

/**
 * 定时关闭选择对话框
 * 使用拖动条选择时长,并支持"延长到整首歌曲"开关
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerDialog(
    currentSongUri: String? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // 拖动条状态 (1-120分钟)
    val minutes = remember { mutableFloatStateOf(15f) }
    val extendToSongEnd = rememberSaveable { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    
    OptionDialog(
        icon = {
            Icon(
                if (SleepTimerManager.isActive.value) Icons.Filled.Timer else Icons.Outlined.Timer,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = "定时关闭",
        content = { dismiss ->
            // 显示当前定时状态(如果已开启)
            if (SleepTimerManager.isActive.value) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF2196F3).copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (SleepTimerManager.isActive.value) Icons.Filled.Timer else Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (SleepTimerManager.isExtendToSongEnd.value && SleepTimerManager.remainingSeconds.intValue == 0) {
                                "倒计时结束，等待歌曲播完后退出"
                            } else {
                                val remaining = SleepTimerManager.remainingSeconds.intValue
                                val min = remaining / 60
                                val sec = remaining % 60
                                "剩余 ${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
                            },
                            fontSize = 14.sp,
                            color = Color(0xFF2196F3)
                        )
                    }
                    TextButton(
                        onClick = {
                            SleepTimerManager.cancelTimer(context)
                            dismiss()
                        }
                    ) {
                        Text("取消", color = Color(0xFFFF5252))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            
            // 时长标签行
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "时长",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${minutes.floatValue.toInt()} 分钟",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // 带背景的拖动条(复用调整圆角页面的样式,包含加减号)
            CupertinoTheme {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            color = (Color.LightGray withNight Color.DarkGray).copy(alpha = 0.15f),
                            shape = YosRoundedCornerShape(14.dp)
                        )
                        .padding(vertical = 15.dp, horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 减号按钮
                    Icon(
                        painter = painterResource(id = yos.music.player.R.drawable.ic_tips_minus),
                        contentDescription = null,
                        modifier = Modifier
                            .size(12.dp)
                            .alpha(0.45f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (minutes.floatValue > 1f) {
                                        Vibrator.click(context)
                                        minutes.floatValue -= 1f
                                    }
                                }
                            )
                    )
                    
                    // 拖动条(包含自定义thumb)
                    CupertinoSlider(
                        value = minutes.floatValue,
                        onValueChange = { minutes.floatValue = it },
                        thumb = {
                            Spacer(
                                Modifier
                                    .size(23.dp)
                                    .hoverable(interactionSource = interactionSource)
                                    .shadow(
                                        8.dp,
                                        androidx.compose.foundation.shape.CircleShape,
                                        clip = false,
                                        spotColor = Color.Black.copy(alpha = 0.55f)
                                    )
                                    .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                            )
                        },
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(end = 8.dp, start = 12.dp),
                        valueRange = 1f..120f
                    )
                    
                    // 加号按钮
                    Icon(
                        painter = painterResource(id = yos.music.player.R.drawable.ic_tips_plus),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 2.dp)
                            .size(14.dp)
                            .alpha(0.45f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    if (minutes.floatValue < 120f) {
                                        Vibrator.click(context)
                                        minutes.floatValue += 1f
                                    }
                                }
                            )
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // 延长到整首歌曲开关
            Row(
                Modifier.fillMaxWidth().height(48.dp)
                    .clip(YosRoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.onSecondary)
                    .clickable { extendToSongEnd.value = !extendToSongEnd.value }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("延长到整首歌曲播完", fontSize = 16.sp, modifier = Modifier.weight(1f))
                io.github.alexzhirkevich.cupertino.CupertinoSwitch(
                    checked = extendToSongEnd.value,
                    onCheckedChange = { extendToSongEnd.value = !extendToSongEnd.value },
                    modifier = Modifier.height(25.dp)
                )
            }
        },
        // 高亮确认按钮(复用调整圆角页面的样式)
        positiveContent = "开始定时",
        onPositive = {
            SleepTimerManager.startTimer(
                context,
                minutes.floatValue.toInt(),
                currentSongUri,
                extendToSongEnd.value
            )
        },
        horizontalTitle = true,
        onDismissRequest = onDismiss
    )
}

/**
 * 定时关闭即将到期警告对话框
 * 剩余1分钟时弹出,允许用户继续或取消
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerWarningDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    
    OptionDialog(
        icon = {
            Icon(
                if (SleepTimerManager.isActive.value) Icons.Filled.Timer else Icons.Outlined.Timer,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color(0xFFFF9800)
            )
        },
        title = "定时关闭提醒",
        content = { dismiss ->
            Text(
                text = "定时关闭将在 1 分钟后生效\n\n是否继续倒计时?",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 取消按钮
                TextButton(
                    onClick = {
                        SleepTimerManager.cancelTimer(context)
                        onCancel()
                        dismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            Color(0xFFFF5252).copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .height(44.dp)
                ) {
                    Text("取消定时", color = Color(0xFFFF5252), fontWeight = FontWeight.Medium)
                }
                
                // 继续按钮
                TextButton(
                    onClick = {
                        SleepTimerManager.shouldShowWarning.value = false
                        onContinue()
                        dismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp)
                        )
                        .height(44.dp)
                ) {
                    Text("继续", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        horizontalTitle = true,
        onDismissRequest = onDismiss
    )
}
