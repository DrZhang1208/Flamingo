# 缓冲按钮与切页性能验证（2026-10-09）

## 改动

- 播放服务通过 Player.onEvents 发布 STATE_BUFFERING 与 playWhenReady 的组合状态。播放按钮显示白色进度环，保持原来的暂停交互；暂停时回到播放图标，服务退出时清理缓冲状态。
- 歌词组件保留已测量的布局，但在歌词页完全隐藏后停止帧时钟；退出淡出期间继续绘制，不截断过渡。
- 专辑图使用硬件图层阴影，避免大尺寸共享元素缩放期间的软件 BlurMaskFilter 绘制。
- Compose View 请求屏幕实际最高刷新率。本机显示配置的 HIGH 类别为 90Hz，而最高支持模式为 120Hz。
- Espresso 测试依赖由 3.6.1 更新为 3.7.0，修复设备上的 InputManager.getInstance 反射兼容性错误。参考 https://developer.android.com/jetpack/androidx/releases/test 。

## 验证

- HONOR BVL-AN00，实际 SDK 37，ADB 真机连接。
- 67 个 JVM 单元测试通过；8 个设备测试通过，包括新缓冲图标测试、暂停共享封面无超调、播放动画打断、歌词文档替换、间奏高度、预览空闲计时、高亮绘制与单层轮廓。
- 最终 Release 构建成功，并通过 adb install -r 安装，未清除应用数据。
- Release 页面往返采样（8 次点击）：修复前总帧数 662，P50/P90/P95 为 16/17/18ms，GPU P90 12ms；修复后总帧数 746，P50/P90/P95 为 10/12/14ms，GPU P90 7ms。
- 新版仍有 6 帧 deadline missed（0.80%），旧版为 3 帧（0.45%）；不能声称 deadline missed 指标改善或始终跑满 120fps。两次歌曲不同，且新旧刷新率请求不同，采样用于定位和观察，不是严格同场景基准。
- 本次测试后的 crash buffer 没有新增崩溃记录。不代表长时间播放的偶发退出已彻底排除。

## 边界

缓冲 UI 的设备测试验证状态切换与图标显示；没有使用故意限速的远程服务器做真实网络缓冲端到端测试。
