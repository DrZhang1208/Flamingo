# 菜单与歌词选择弹窗优化

## 修改

- 公共菜单上拉仍为整体平移，不拉伸内容。初始跟手比例由 0.32 调为 0.5，阻尼采用逐渐增强的有理曲线，位移上限由 24dp 调为 48dp；保留可打断的临界阻尼回弹，新手势从当前可见位置接续。
- 消耗上拉手势剩余 fling，避免弹性回弹之外再把速度交给 Material 底部弹窗。列表本次手势滚动到顶部后继续下拉仍不收起，松手后的下一次下拉可以收起。
- 当前 Material 3 1.2.1 的 ModalBottomSheet 使用独立 WindowManager Popup。公共弹窗对该窗口设置当前分辨率下最高受支持刷新率，Android 15 及以上同时对实际绘制 View 设置帧率请求；卸载时恢复原请求。
- 歌词选择列表取消外层重复纵向滚动，用覆盖渐变融入不透明弹窗背景，保留上下淡出，去掉整块列表的离屏纹理合成。复用行圆角形状、设置 item key/contentType，并在分享选择期间暂停背后的歌词逐帧更新。
- 新增公共 `DialogDismissAction.after`，先等待弹窗隐藏，再执行导航或打开下一弹窗。歌曲菜单、歌手选择、歌单选择入口、歌曲详情、定时关闭及歌单重命名入口接入顺序关闭，避免导航先卸载菜单或两个弹窗同时动画。连续点击只接受第一次关闭请求；隐藏被拒绝或协程取消时不执行导航。

## 验证

- `:app:testDebugUnitTest`：142 项通过，0 失败、0 跳过。新增导航等待、重复点击、隐藏拒绝、取消、阻尼长距离响应和上拉 fling 边界回归。确认按钮另保留原有“隐藏完成 → 提交表单 → 清理弹窗”的顺序，覆盖删除服务器确认所需的状态读取。
- `:app:assembleDebugAndroidTest`：通过。新增/扩展弹窗隐藏与导航顺序、独立窗口刷新率请求、渐变像素和无外层滚动时的手势边界设备测试。
- `git diff --check`：通过。
- 当前 `adb devices` 无连接设备：以上设备测试仅完成编译，未执行；没有真机帧耗时数据，不能据此保证持续满刷新率。

Release 构建与 WebDAV 上传结果以本次构建日志为准：`/tmp/flamingo-dialog-performance-release.log`。

- `:app:assembleRelease`：通过（含 Release vital lint），耗时 1m17s。
- WebDAV 上传成功，远端回读 SHA-256 与 APK 一致；本地副本已校验并在 Finder 显示：`~/Downloads/Flamingo-latest-release.apk`。
