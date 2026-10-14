# 按 Android 规范处理播放中断

移除 MainActivity.onResume 调用的焦点丢失恢复逻辑，以及仅用于前台恢复的 AudioFocusResumePolicy。回到 Flamingo 界面不再主动抢回音频焦点或启动播放。

保持现有音乐音频属性（USAGE_MEDIA、CONTENT_TYPE_MUSIC）、音频焦点设置开关和耳机拔出暂停行为。开启音频焦点处理时，统一由 Media3 管理：

- 临时焦点丢失：保留播放意图，暂时抑制播放；重新获得焦点后后台恢复，不依赖 Activity 生命周期。
- 允许降低音量的临时中断：降低音量，焦点恢复后恢复音量。
- 长期焦点丢失：暂停，等待用户从界面、通知或媒体按键明确播放；对方停止播放或返回 Flamingo 都不主动恢复。
- 临时中断期间用户主动暂停：保持暂停，不在对方释放焦点后恢复。

不监听其他应用是否静音或轮询抢回焦点。行为依据 Android 官方音频焦点建议：
https://developer.android.com/media/optimize/audio-focus

删除旧的七项前台恢复策略单元测试，新增三项设备回归，使用真实 AudioManager 和 Media3 验证临时中断恢复、手动暂停阻止恢复、长期中断等待主动播放。设备用例需要可用真机或模拟器。

验证：183 项 JVM 测试通过，Release 与 Android 测试 APK 构建成功。ADB 未连接，新增三项设备回归未执行。

本地 Release：`Flamingo_2.3.2_261012222454.apk`。
SHA-256：`9694f95e18ba863f7d9291adc068ef798ce6fb226f2ce1371157322591b839e1`。

WebDAV 上传启动后按用户要求交还，不轮询或宣称远端完成。
