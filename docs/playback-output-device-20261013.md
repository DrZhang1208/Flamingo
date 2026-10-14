# 播放设备选择

播放页底部设备按钮之前只有图标和蓝牙名称显示，没有点击事件。本次绑定点击操作并增加无障碍按钮标签与触感反馈。

Android 14 及以上优先使用 MediaRouter2.showSystemOutputSwitcher，让系统显示当前支持切换的输出设备。厂商入口和旧版系统面板作为兼容路径；入口不可用或调用失败时返回 false，由播放页显示应用内 OptionDialog。应用内列表仅包含 MediaRouter 当前提供的可用 LIVE_AUDIO 路由，标记当前输出，并通过 selectRoute 切换；设备断开后会从列表移除，选择前再次确认路由仍然存在。不同系统能暴露给应用的路由数量可能不同。

设备名称改为跟随当前选中的媒体路由，不再通过已配对蓝牙列表和反射猜测。路由回调更新名称、当前选中项和设备列表，界面销毁时注销回调。兼容对话框状态保留在自动隐藏控制区外，控制区隐藏不会销毁对话框。未修改播放器、音频焦点或通信音频模式。

验证：211 项 JVM 测试通过，失败/错误/跳过均为 0。Release 和 Android 测试 APK 构建成功，git diff --check 通过。ADB 未连接，系统选择框、实体输出切换和厂商兼容路径尚未真机验证。

本次构建后额外检查 Java 进程：Gradle 状态为 IDLE；5 秒内 Gradle/Kotlin 进程 CPU 时间分别仅增加约 0.02/0.01 秒，RSS 合计约 4.2GiB，符合空闲编译缓存。本次收尾停止本轮启动的 Gradle 守护进程，并向本轮 Kotlin 编译守护进程发送 TERM；未修改项目或全局守护进程配置。此前用户已强制退出的进程无法根据这些结果追溯判定。

Release 已复制到 `/Users/zhangdd/Downloads/Flamingo-latest-release.apk`。
SHA-256：`1b0325e58963d6c56cf6346a86d218e0d33a4c2a880edf9b19b1f107f1d801a6`。
WebDAV 只启动上传，不轮询或宣称远端完成。

参考：[系统输出选择器](https://developer.android.com/reference/android/media/MediaRouter2#showSystemOutputSwitcher())、[媒体路由选择](https://developer.android.google.cn/reference/android/media/MediaRouter#selectRoute(int,%20android.media.MediaRouter.RouteInfo))。
