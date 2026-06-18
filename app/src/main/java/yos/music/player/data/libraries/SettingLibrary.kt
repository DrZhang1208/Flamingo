package yos.music.player.data.libraries

import androidx.compose.runtime.Stable
import com.funny.data_saver.core.mutableDataSaverStateOf
import yos.music.player.data.SettingsSaver

@Stable
object SettingsLibrary {

    /**
     * 一次性预加载所有设置值到内存。
     * 在 Application 的后台线程中调用，避免 UI 线程首次访问时触发 MMKV 磁盘 IO。
     */
    fun preload() {
        try {
            // 触发所有 mutableDataSaverStateOf 的 lazy 初始化
            NowPlayingShowVolumeBar
            CustomTheme
            ScreenCornerSet
            ScreenCorner
            SongSort
            EnableDescending
            NowPlayingTranslation
            RefreshEveryTime
            LyricFontWeight
            LyricFontSize
            TranslationFontSize
            LyricLineBalance
            LyricBlurEffect
            NowplayingBackgroundEffect
            BarBlurEffect
            LyricsHideControls
            NotificationEnableIcon
            NotificationSmallerIcon
            FadePlay
            ListenHistory
            StatusBarLyricEnabled
            StatusBarLyricHooked
            AudioAttributes
            Codec
            HardwareAudioTrackPlayBackParams
            AudioFloatOutput
            RemoteCacheSizeMB
            EnableExcludeSongsUnderOneMinute
            DebugLyricEnableHighlight
            DebugLyricEnableGlow
            DebugLyricEnableUplift
            DebugLyricEnableSmoothVelocity
            DebugLyricEnableEmaGlow
        } catch (_: Exception) {}
    }

    /**
     * 是否显示音量条
     */
    @Stable
    var NowPlayingShowVolumeBar by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_nowplaying_show_volume_bar",
        initialValue = true
    )

    /**
     * 应用主题
     */
    @Stable
    var CustomTheme by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_theme",
        initialValue = "Auto"
    )

    /**
     * 是否已设置过屏幕圆角大小
     */
    @Stable
    var ScreenCornerSet by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_corner_set",
        initialValue = false
    )

    /**
     * 屏幕圆角大小
     */
    @Stable
    var ScreenCorner by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_corner",
        initialValue = "30"
    )

    /**
     * 歌曲排序
     */
    @Stable
    var SongSort by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "yos_player_song_sort",
        initialValue = SongSortEnum.MUSIC_TITLE.ordinal
    )

    @Stable
    enum class SongSortEnum {
        MUSIC_TITLE, MUSIC_DURATION, ARTIST_NAME, MODIFIED_DATE, MUSIC_ADD_DATE, MUSIC_ALBUM, PLAY_COUNT
    }

    /**
     * 启用降序
     */
    @Stable
    var EnableDescending by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "yos_player_enable_descending",
        initialValue = false
    )

    /**
     * 逐字歌词
     */
    @Stable
    var EnableWordByWordLyric by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "yos_player_word_by_word_lyric",
        initialValue = true
    )

    /**
     * 歌词界面 - 翻译
     */
    @Stable
    var NowPlayingTranslation by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "now_playing_translation",
        initialValue = true
    )

    /**
     * 每次启动时刷新媒体库
     */
    @Stable
    var RefreshEveryTime by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_library_refresh_everytime",
        initialValue = false
    )

    /**
     * 歌词字体字重
     */
    @Stable
    var LyricFontWeight by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_font_weight",
        initialValue = "Bold"
    )

    /**
     * 歌词字体大小
     */
    @Stable
    var LyricFontSize by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_font_size",
        initialValue = 32
    )

    /**
     * 翻译字体大小
     */
    @Stable
    var TranslationFontSize by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_translation_font_size",
        initialValue = 20
    )

    /**
     * 歌词平衡行模式
     */
    @Stable
    var LyricLineBalance by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_line_balance",
        initialValue = false
    )

    /**
     * 歌词模糊效果
     */
    @Stable
    var LyricBlurEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_lyric_blur_effect",
        initialValue = false
    )

    /**
     * 播放界面背景动态效果
     */
    @Stable
    var NowplayingBackgroundEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_nowplaying_background_effect",
        initialValue = false
    )

    /**
     * 界面工具栏模糊效果
     */
    @Stable
    var BarBlurEffect by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_blur_effect",
        initialValue = false
    )

    /**
     * 歌词页自动隐藏控制按钮
     */
    @Stable
    var LyricsHideControls by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_ui_lyrics_hide_controls",
        initialValue = true
    )

    /**
     * 媒体通知-额外的媒体图标
     */
    @Stable
    var NotificationEnableIcon by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_notification_enable_icon",
        initialValue = true
    )

    /**
     * 媒体通知-小一号图标
     */
    @Stable
    var NotificationSmallerIcon by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_performance_notification_smaller_icon",
        initialValue = false
    )

    /**
     * 渐入渐出播放
     */
    @Stable
    var FadePlay by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_fade_in_out",
        initialValue = true
    )

    /**
     * 播放历史
     */
    @Stable
    var ListenHistory by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_play_history",
        initialValue = true
    )

    /**
     * 状态栏歌词
     */
    @Stable
    var StatusBarLyricEnabled by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "statusBarLyricEnabled",
        initialValue = false
    )

    /**
     * 状态栏歌词 Hook 状态
     */
    @Stable
    var StatusBarLyricHooked by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "statusBarLyricHooked",
        initialValue = false
    )

    /**
     * ExoPlayer行为 - 音频属性
     */
    @Stable
    var AudioAttributes by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_audio_attributes",
        initialValue = true
    )

    /**
     * ExoPlayer解码 - 编解码器
     */
    @Stable
    var Codec by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_codec",
        initialValue = "Auto"
    )

    /**
     * ExoPlayer解码 - 硬件音频轨道播放参数
     */
    @Stable
    var HardwareAudioTrackPlayBackParams by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_hardware_audio_track_playback_params",
        initialValue = false
    )

    /**
     * ExoPlayer解码 - 音频浮点输出
     */
    @Stable
    var AudioFloatOutput by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_audio_exoplayer_audio_float_output",
        initialValue = false
    )

    @Stable
    var RemoteCacheSizeMB by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "remote_cache_size_mb",
        initialValue = 512
    )

    /**
     * 排除一分钟以内的歌曲
     */
    @Stable
    var EnableExcludeSongsUnderOneMinute by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "settings_library_enable_exclude_songs_under_one_minute",
        initialValue = true
    )

    // ── 逐字歌词调试开关 ─────────────────────────────────────────────
    // 全部默认 true，与重构前行为一致。逐项关闭可以隔离每个 pass，
    // 用于定位闪烁等渲染问题。

    /**
     * Debug: 启用逐字高光绘制。关闭则当前行只显示暗色底图，便于观察上浮/EMA 等基础层。
     */
    @Stable
    var DebugLyricEnableHighlight by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "debug_lyric_enable_highlight",
        initialValue = true
    )

    /**
     * Debug: 启用高光右侧光晕羽化。关闭则只剩硬边遮罩，无渐变拖尾。
     */
    @Stable
    var DebugLyricEnableGlow by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "debug_lyric_enable_glow",
        initialValue = true
    )

    /**
     * Debug: 启用逐字上浮。关闭则全行不再做 Y 位移（仍走 per-char 裁剪逻辑）。
     */
    @Stable
    var DebugLyricEnableUplift by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "debug_lyric_enable_uplift",
        initialValue = true
    )

    /**
     * Debug: 启用 Hermite 平滑变速。关闭则字内进度走线性，便于核对原始时间映射。
     */
    @Stable
    var DebugLyricEnableSmoothVelocity by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "debug_lyric_enable_smooth_velocity",
        initialValue = true
    )

    /**
     * Debug: 启用 EMA 平滑光晕宽度。关闭则光晕宽度直接采用瞬时值。
     */
    @Stable
    var DebugLyricEnableEmaGlow by mutableDataSaverStateOf(
        dataSaverInterface = SettingsSaver,
        key = "debug_lyric_enable_ema_glow",
        initialValue = true
    )
}
