# 独立字母的整行高亮坐标（2026-10-13）

## 问题

独立字母修复使用 Alpha8 遮罩加 Paint.shader 绘制小位图。Android/Skia 的透明位图 CPU 和 GPU 路径存在坐标处理差异，位图平移可能把高亮场带入每个字母自己的坐标，出现一行中先变暗、后面又变亮的断层。纯白字形测试无法覆盖这种错误。

## 修复

- 使用显式 BitmapShader 描述字形遮罩的位置，并以 ComposeShader 的 DST_IN 将整行渐变限制在字形内。绘制段落坐标中的矩形，只有遮罩应用位图位置，不让颜色跟随位图局部原点。
- 保留独立字母遮罩和上浮，不回退到会携带邻字笔画的整句矩形裁切。
- 段落整体和独立字符采用同一渐变坐标规则；超出缓存限制的独立文字向量回退也补偿局部原点，并在绘制后恢复共享渐变矩阵。
- 每行缓存固定的渐变 Shader，通过矩阵更新扫色位置；每份遮罩缓存 BitmapShader 和 ComposeShader，避免按字符每帧创建组合 Shader。纯色端点与辉光仍使用原有快速路径。
- 波浪曲线、字体、行距、翻译和背景不变。

## 回归

新增 CPU 绘制回归检查多行英文的高亮不能越往后再次变亮；新增硬件画布回归使用截图中的英文句子自动换行，并以红蓝渐变的理论颜色对每个实心字形像素检查段落坐标，避免纯色测试遗漏 Shader 错位。

- JVM：223 项全部通过；Android 测试 APK 与 Release 构建成功；git diff --check 通过。
- 新增英文换行亮暗顺序和硬件画布渐变颜色测试，已完成编译。ADB 无连接，因此本版没有执行设备测试或确认截图中的视觉问题已在真机消失。
- Android 33+ 显式指定遮罩 BitmapShader 的线性过滤，保留分数像素上浮的平滑采样。
- [AOSP ComposeShader 实现](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/graphics/java/android/graphics/ComposeShader.java)确认子 Shader 矩阵改变会使缓存更新；组合模式以颜色场为目标、遮罩为源，使用 DST_IN 保留字形内的颜色。
- Release：`Flamingo_2.3.2_261013105528.apk`，SHA-256：`6e2e89feea08f975ce6674746c4e6cef2db2e7b663bbda6c211cda17d7e1ea91`。
- 本地副本：`/Users/zhangdd/Downloads/Flamingo-latest-release.apk`。
- 按约定启动 WebDAV 上传后不轮询，不将启动视为远端校验完成。
