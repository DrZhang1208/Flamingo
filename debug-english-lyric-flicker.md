# Debug Session: english-lyric-flicker
- **Status**: [OPEN]
- **Issue**: 英文歌词动画偶发闪烁，且部分英文字母（如 j、g）左下角出现缺角/被裁切
- **Debug Server**: http://10.1.12.35:7778/event
- **Log File**: .dbg/trae-debug-log-english-lyric-flicker.ndjson

## Reproduction Steps
1. 打开播放页并进入歌词页。
2. 播放含英文逐字歌词的歌曲，重点观察英文单词高亮与上浮过程。
3. 重点留意包含 `j`、`g`、`y` 等下行字母的英文行。
4. 观察是否出现偶发闪烁，或字母左下角/下行区域被裁掉。

## Hypotheses & Verification
| ID | Hypothesis | Likelihood | Effort | Evidence |
|----|------------|------------|--------|----------|
| A | 英文逐字动画闪烁来自同一帧内的布局/绘制边界切换，`charLayout` 在关键帧附近表现不稳定 | High | Medium | Pending |
| B | `j/g/y` 缺角由 `visualLineClip` 或字符裁剪边界过紧造成，descender 区域被 clip 掉 | High | Low | Pending |
| C | 英文高亮/上浮单元在空格、折行或字形溢出处切换时，`clipRect` 左右边界抖动，导致偶发闪烁 | Medium | Medium | Pending |
| D | `getBoundingBox()` 返回的字符盒对 descender 支持不足，当前裁剪逻辑直接信任它，导致下行字母被截断 | Medium | Medium | Pending |

## Log Evidence
- Pending
- Instrumentation added in `app/src/main/java/yos/music/player/ui/widgets/YosLyricView.kt`
  - A: 记录 `j/g/y/p/q` 等字符的布局盒与时间片
  - B: 记录纯色 pass 中 descender 字母是否被 clipRect 裁切
  - C: 记录英文字符在高亮边界起止帧附近的 mask 推进
  - D: 记录渐变/光晕 pass 中 descender 字母是否被 clipRect 裁切

## Verification Conclusion
- Pending
