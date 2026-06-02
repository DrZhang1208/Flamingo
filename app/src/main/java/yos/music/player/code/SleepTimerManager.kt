package yos.music.player.code

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import yos.music.player.data.libraries.SettingsLibrary

/**
 * 定时关闭管理器
 * 
 * 设计决策:
 * 1. 使用 AlarmManager 而非 Handler/Coroutine,确保应用退到后台后定时器仍然生效
 * 2. 不持久化定时状态,进程被杀后自动清空(冷启动时默认无定时)
 * 3. 手动暂停音乐时,定时器继续计时(最佳实践:用户明确设置定时,应严格遵守)
 * 4. 固定时长模式下切歌不影响定时; "播完当前歌曲"模式下切歌会取消定时
 * 5. "延长到整首歌曲"模式: 倒计时结束后,如果正在播放,则等待当前歌曲播完再暂停
 */
@Stable
object SleepTimerManager {
    
    /** 剩余秒数(0表示未开启) */
    val remainingSeconds = mutableIntStateOf(0)
    
    /** 是否为"延长到整首歌曲"模式 */
    val isExtendToSongEnd = mutableStateOf(false)
    
    /** 定时器是否激活 */
    val isActive = mutableStateOf(false)
    
    /** 是否应该显示"即将关闭"提示(剩余1分钟时) */
    val shouldShowWarning = mutableStateOf(false)
    
    /** 延长模式下：原始倒计时的剩余秒数（归0后开始追踪） */
    val originalRemainingSeconds = mutableIntStateOf(0)
    
    private var alarmManager: AlarmManager? = null
    private var pendingIntent: PendingIntent? = null
    private var countdownHandler: Handler? = null
    private var countdownRunnable: Runnable? = null
    
    /**
     * 启动定时器
     * @param context Context
     * @param minutes 分钟数(>0为固定时长)
     * @param currentSongUri 当前歌曲URI(用于"播完当前歌曲"模式)
     * @param extendToSongEnd 是否延长到当前歌曲播完
     */
    fun startTimer(context: Context, minutes: Int, currentSongUri: String? = null, extendToSongEnd: Boolean = false) {
        // 取消旧定时器
        cancelTimer(context, silent = true)
        
        // 固定时长模式
        isExtendToSongEnd.value = extendToSongEnd
        isActive.value = true
        shouldShowWarning.value = false
        remainingSeconds.intValue = minutes * 60
        
        // 使用 AlarmManager 设置精确定时(即使应用休眠也能触发)
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
        
        val intent = Intent(context, SleepTimerReceiver::class.java)
        pendingIntent = PendingIntent.getBroadcast(
            context,
            1145,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 设置精确闹钟(Android 12+ 需要权限)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager?.canScheduleExactAlarms() == true) {
            alarmManager?.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent!!
            )
        } else {
            // 低版本或没有精确闹钟权限,使用普通闹钟
            alarmManager?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent!!
            )
        }
        
        // 启动UI倒计时(仅用于显示,不影响实际定时)
        startCountdown(context)
        
        val extendMsg = if (extendToSongEnd) "(将延长到歌曲播完)" else ""
        Toast.makeText(context, "将于 $minutes 分钟后暂停播放 $extendMsg", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 取消定时器
     */
    fun cancelTimer(context: Context, silent: Boolean = false) {
        // 取消 AlarmManager
        pendingIntent?.let {
            alarmManager?.cancel(it)
            it.cancel()
        }
        pendingIntent = null
        
        // 停止UI倒计时
        stopCountdown()
        
        // 重置状态
        isActive.value = false
        isExtendToSongEnd.value = false
        shouldShowWarning.value = false
        originalRemainingSeconds.intValue = 0
        remainingSeconds.intValue = 0
        
        if (!silent) {
            Toast.makeText(context, "定时已取消", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 检查是否应该在歌曲播放完毕后退出
     * 由播放服务在歌曲播放完毕时调用
     * 仅在延长模式下且倒计时已归 0 时生效
     */
    fun checkFinishCurrentSong(currentSongUri: String?) {
        // 只在延长模式 + 倒计时归0 + 定时器激活 时触发退出
        if (isExtendToSongEnd.value && isActive.value && remainingSeconds.intValue == 0) {
            Handler(Looper.getMainLooper()).post {
                val context = yos.music.player.code.MediaController.appContext
                
                // 暂停播放
                yos.music.player.code.MediaController.mediaControl?.pause()
                context?.let { cancelTimer(it, silent = true) }
                context?.let { 
                    Toast.makeText(it, "歌曲已播放完毕，应用即将退出", Toast.LENGTH_SHORT).show()
                }
                
                // 延迟退出，让 Toast 显示
                Handler(Looper.getMainLooper()).postDelayed({
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(0)
                }, 1500)
            }
        }
    }
    
    /**
     * 手动暂停时调用：如果是延长模式且倒计时已归 0，取消定时
     */
    fun onManualPause() {
        if (isExtendToSongEnd.value && isActive.value && remainingSeconds.intValue == 0) {
            yos.music.player.code.MediaController.appContext?.let {
                cancelTimer(it, silent = true)
                Toast.makeText(it, "手动暂停，定时关闭已取消", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 切歌时调用：如果是延长模式且倒计时已归 0，则取消定时
     * @param reason ExoPlayer 的 transition reason，TRANSITION_REASON_AUTO(0) 表示歌曲自然结束
     */
    fun onTrackChanged(newSongUri: String?, reason: Int = -1) {
        // 歌曲自然播放结束（reason == 0）不算切歌，跳过
        // TRANSITION_REASON_AUTO = 0: 歌曲自然结束/列表自动切换
        if (reason == androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            return
        }
        
        if (isExtendToSongEnd.value && isActive.value && remainingSeconds.intValue == 0) {
            // 延长模式 + 倒计时归 0 后用户手动切歌 → 取消退出
            yos.music.player.code.MediaController.appContext?.let {
                cancelTimer(it, silent = true)
                Toast.makeText(it, "已切换歌曲，定时关闭已取消", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 启动UI倒计时(每秒更新)
     */
    private fun startCountdown(context: Context) {
        countdownHandler = Handler(Looper.getMainLooper())
        countdownRunnable = object : Runnable {
            override fun run() {
                if (isActive.value) {
                    if (remainingSeconds.intValue > 0) {
                        remainingSeconds.intValue -= 1
                        
                        // 剩余1分钟时显示警告(只在应用前台时)
                        if (remainingSeconds.intValue == 60 && !shouldShowWarning.value) {
                            shouldShowWarning.value = true
                        }
                    } else if (isExtendToSongEnd.value && remainingSeconds.intValue == 0) {
                        // 延长模式下倒计时归0，开始显示 +xx:xx（当前歌曲剩余时长）
                        val mediaControl = yos.music.player.code.MediaController.mediaControl
                        val duration = mediaControl?.duration ?: 0L
                        val position = mediaControl?.currentPosition ?: 0L
                        val remaining = ((duration - position) / 1000).toInt().coerceAtLeast(0)
                        originalRemainingSeconds.intValue = remaining
                    }
                    
                    countdownHandler?.postDelayed(this, 1000)
                }
            }
        }
        countdownHandler?.post(countdownRunnable!!)
    }
    
    /**
     * 停止UI倒计时
     */
    private fun stopCountdown() {
        countdownRunnable?.let { countdownHandler?.removeCallbacks(it) }
        countdownHandler = null
        countdownRunnable = null
    }
    
    /**
     * 获取格式化的剩余时间字符串 (如 "14:59")
     * 延长模式下归0后返回 "+xx:xx" (当前歌曲剩余时长)
     */
    fun getFormattedRemainingTime(): String {
        if (isExtendToSongEnd.value && remainingSeconds.intValue == 0 && isActive.value) {
            // 延长模式 + 倒计时归0：显示当前歌曲剩余时长
            val mediaControl = yos.music.player.code.MediaController.mediaControl
            val duration = mediaControl?.duration ?: 0L
            val position = mediaControl?.currentPosition ?: 0L
            val remaining = ((duration - position) / 1000).toInt().coerceAtLeast(0)
            val min = remaining / 60
            val sec = remaining % 60
            return "+${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
        }
        
        val seconds = remainingSeconds.intValue
        if (seconds <= 0) return ""
        val min = seconds / 60
        val sec = seconds % 60
        return "${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    }
}

/**
 * 定时器广播接收器
 * 当 AlarmManager 触发时（倒计时归 0），执行动作
 */
class SleepTimerReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Handler(Looper.getMainLooper()).post {
            val manager = SleepTimerManager
            
            if (manager.isExtendToSongEnd.value) {
                // 延长模式：不暂停，等待当前歌曲自然播完后退出
                // 标记等待歌曲结束（remainingSeconds 已为 0，UI 开始显示 +xx:xx）
                // checkFinishCurrentSong 会在歌曲结束时触发退出
                Toast.makeText(context, "倒计时结束，将等待当前歌曲播完后退出", Toast.LENGTH_SHORT).show()
            } else {
                // 非延长模式：直接退出软件
                val mediaControl = yos.music.player.code.MediaController.mediaControl
                mediaControl?.pause()
                
                manager.cancelTimer(context, silent = true)
                Toast.makeText(context, "定时关闭时间已到，应用即将退出", Toast.LENGTH_SHORT).show()
                
                // 延迟退出，让 Toast 显示
                Handler(Looper.getMainLooper()).postDelayed({
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(0)
                }, 1500)
            }
        }
    }
}
