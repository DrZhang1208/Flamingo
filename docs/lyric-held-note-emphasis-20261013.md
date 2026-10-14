# 长音歌词强调（2026-10-13）

目标是接近 Apple Music 长音中的柔和膨胀、逐字符错峰、外发光和回落；这是独立实现，Apple 未公开原生引擎的精确曲线或参数。

## 行为

- 有逐字时间的歌词中，持续至少 1 秒的字/词参与强调；英文按整词聚合，即使源时间轴把同一单词拆成音节。
- 等比放大约 3.5% 至 8%，由时长和句尾位置控制，同时限制整词横向增长。字符依次错开，最大错峰 260ms，轻微展开而不改变测量宽度。
- 放大和柔光在长音约 45% 处达到峰值，然后逐渐回落。额外上浮提前 240ms 开始、结束后 220ms 内收尾，峰值仍为原先减小后的 1.5dp。
- 全部相位直接由播放时间求值，暂停不会继续走动画，反向拖动不会遗留旧强调。
- 逐行高亮保持原有颜色过渡与固定基线；翻译保持未高亮样式；背景与 80% 转速不变。
- RTL 连写文字保留原有整段塑形路径，避免逐字符变换破坏连写。

## 绘制与开销

NativeLyricSurface 将已塑形字形的 Alpha8 遮罩分区，再缓存柔光遮罩。绘制时移动、缩放缓存，不逐帧重建位图或执行模糊。柔光仅叠在外部，不替代白色实心字形。额外遮罩缓存每段最多 2,097,152 像素；过大的异常段落回退原有文字路径。布局固定预留柔光和放大的空间，动画不触发行高变化。

## 参考

- [Apple Music Sing 官方介绍](https://www.apple.com/newsroom/2022/12/apple-introduces-apple-music-sing/)：官方公开的是体验描述，不是动画源代码。
- [AMLL 强调实现](https://github.com/Steve-xmh/applemusic-like-lyrics/blob/86200dead453bb067e554e989110cbadca8d4756/packages/core/src/lyric-player/dom/animation/emphasize/index.ts)：参考错峰、膨胀、柔光和独立上浮的组合。
- [Android BlurMaskFilter](https://developer.android.com/reference/android/graphics/BlurMaskFilter.Blur)：柔光遮罩使用 OUTER。

## 验证

- JVM：216 项测试全部通过；Debug、测试 APK、Release 构建成功。
- HONOR BVL-AN00（API 37）：NativeLyricRendererTest 16 项、拖动预览 5 项、返回页面 1 项、歌词页面 2 项最终均通过。歌词页面一项首次出现测试 Activity 未挂载，单独复跑 2 项通过；没有修改生产逻辑掩盖失败。
- 分帧图覆盖 800/1300/1900/2400/3200/3600ms；检查了字形实心部分、柔光扩散与结束回落。设备测试断言反向 seek 的像素一致性、缓存复用与像素上限。
- 取消模糊的测试改为同一视口、同一指针时间比较，避免两个堆叠视口被窗口裁切；设置模式切换等待正常 180ms 模糊过渡，但拖动首帧的检查仍只推进一帧。
- 未测量长时间 GPU 帧率，不据此宣称整机稳定帧率。
- Release SHA-256：`0adc67935e3548c4eabec22934d3a741b7eee01f95995b183e7cdb32c158e1b6`。
- 本地副本：`/Users/zhangdd/Downloads/Flamingo-latest-release.apk`。
