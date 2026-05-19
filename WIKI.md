# Flamingo 项目 Wiki

Flamingo 是一款基于 Kotlin + Jetpack Compose 构建的 Android 本地音乐播放器，采用 Material3 设计语言，同时融合了大量 iOS 风格交互元素。

---

## 目录

1. [项目概述](#1-项目概述)
2. [技术栈](#2-技术栈)
3. [项目结构](#3-项目结构)
4. [构建与运行](#4-构建与运行)
5. [应用入口与启动流程](#5-应用入口与启动流程)
6. [模块详解](#6-模块详解)
   - [app 主模块](#app-主模块)
   - [overscroll_core 弹性滚动模块](#overscroll_core-弹性滚动模块)
7. [导航系统](#7-导航系统)
8. [UI 页面清单](#8-ui-页面清单)
9. [数据层](#9-数据层)
10. [播放引擎](#10-播放引擎)
11. [设置系统](#11-设置系统)
12. [主题系统](#12-主题系统)
13. [可复用组件](#13-可复用组件)
14. [第三方依赖](#14-第三方依赖)
15. [资源文件](#15-资源文件)
16. [开发注意事项](#16-开发注意事项)
17. [项目完整性分析](#17-项目完整性分析)

---

## 1. 项目概述

Flamingo 是一个功能完整的本地音乐播放器 Android 应用，核心功能包括：

- **本地音乐扫描与管理**：自动扫描设备中的音频文件，按歌曲、专辑、艺术家、文件夹归类
- **完整播放控制**：基于 AndroidX Media3 (ExoPlayer) 实现音频播放、队列管理、随机/循环模式
- **桌面歌词**：支持状态栏歌词显示（通过 Lyric-Getter-Api 框架）
- **多语言支持**：简体中文、繁体中文、英文、日语
- **深色模式**：支持浅色/深色主题切换，可跟随系统
- **自定义音频渲染**：可选 FFmpeg 软解、系统解码或自动选择
- **可定制的用户界面**：圆角调节、模糊效果、主题色等

---

## 2. 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin | 2.0.0 |
| UI 框架 | Jetpack Compose + Material3 | 1.7.0-beta07 |
| 构建工具 | Android Gradle Plugin | 8.4.0 |
| 音频引擎 | AndroidX Media3 (ExoPlayer) | 1.4.0 |
| 持久化 | MMKV (腾讯) | 1.3.5 |
| 序列化 | Gson | 2.10.1 |
| 图片加载 | Coil (Compose) | 2.5.0 |
| 媒体扫描 | libPhonograph | AAR 本地依赖 |
| 歌词解析 | Lyric-Getter-Api | 6.0.0 |
| 拼音排序 | TinyPinyin | 2.0.2 |
| 触摸反馈 | Vibrator (自定义) | - |
| 状态管理 | Compose mutableStateOf + DataSaver | 1.1.9 |
| 模糊效果 | Haze | 0.9.0-alpha06 |

---

## 3. 项目结构

```
Flamingo/
├── app/                              # 主应用模块 (yos.music.player)
│   ├── src/main/java/yos/music/player/
│   │   ├── YosBasicApplication.kt    # Application 入口
│   │   ├── MainActivity.kt           # 唯一的 Activity (约1200行)
│   │   ├── BaseActivity.kt           # 空基类 (继承ComponentActivity)
│   │   ├── CrashActivity.kt          # 崩溃捕获展示页面
│   │   ├── code/                     # 播放核心逻辑
│   │   │   ├── MediaController.kt    # 播放控制器单例 + MediaSessionService
│   │   │   ├── AudioMetadataUtils.kt # 音频元数据提取
│   │   │   ├── SystemMediaControlResolver.kt # 厂商音频输出切换
│   │   │   ├── YosRenderFactory.kt   # 自定义ExoPlayer渲染器工厂
│   │   │   ├── VolumeChangeReceiver.kt # 音量变化广播接收器
│   │   │   └── utils/
│   │   │       ├── lrc/              # 歌词解析与配置
│   │   │       ├── others/           # 位图处理、振动、子字符串
│   │   │       └── player/           # FadeExo 淡入淡出效果
│   │   ├── data/                     # 数据层
│   │   │   ├── YosDataSaver.kt       # DataSaver 实例配置
│   │   │   ├── SettingOption.kt      # 设置选项数据类
│   │   │   ├── libraries/            # 数据仓库
│   │   │   │   ├── MusicLibrary.kt   # 音乐库 (扫描/持久化/查询)
│   │   │   │   ├── PlayListLibrary.kt # 播放列表管理
│   │   │   │   ├── SettingLibrary.kt  # 设置项管理
│   │   │   │   ├── MediaItemExtra.kt  # MediaItem 扩展属性
│   │   │   │   └── YosMediaItemExtra.kt # YosMediaItem 扩展
│   │   │   ├── models/               # ViewModel
│   │   │   └── objects/              # 全局响应式状态对象
│   │   └── ui/                       # UI层
│   │       ├── NavHost.kt            # 路由定义 + 导航扩展
│   │       ├── pages/                # 页面组件
│   │       ├── theme/                # 主题 (颜色/字体/形状)
│   │       └── widgets/              # 可复用组件
│   ├── src/main/res/                 # 资源文件
│   ├── src/test/                     # 单元测试
│   ├── src/androidTest/              # 设备测试
│   └── libs/                         # 本地AAR依赖
├── overscroll_core/                  # 弹性滚动效果库 (com.cormor.overscroll.core)
│   └── src/main/java/com/cormor/overscroll/core/
│       └── OverScroll.kt             # iOS风格越界回弹效果
├── gradle/                           # Gradle Wrapper
├── build.gradle                      # 根构建脚本
├── settings.gradle                   # 模块设置
├── gradle.properties                 # Gradle 全局属性
├── stability_config.conf             # Compose编译器稳定性配置
└── README.md
```

---

## 4. 构建与运行

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 34
- 设备或模拟器运行 Android 6.0+ (API 23+)

### 构建命令

```bash
# 编译 Debug 版本
./gradlew assembleDebug

# 编译 Release 版本
./gradlew assembleRelease

# 运行单元测试
./gradlew test

# 运行单个测试
./gradlew test --tests "yos.music.player.ExampleUnitTest"

# 运行设备测试
./gradlew connectedAndroidTest

# 清理构建
./gradlew clean
```

### Release 构建特性

- 启用代码混淆与压缩 (ProGuard + R8)
- 移除所有 `Log.d/e/i/v/w` 调用
- 移除 `System.out.println` 调用
- APK 命名格式：`Flamingo_{versionName}_{yyMMddHHmm}.ApK`
- 仅支持 `armeabi-v7a` 和 `arm64-v8a` ABI

---

## 5. 应用入口与启动流程

### 启动顺序

```
YosBasicApplication.onCreate()
  ├─ 设置全局未捕获异常处理器 → CrashActivity
  ├─ 初始化 MMKV
  ├─ 注册 Gson TypeAdapter (Uri, Folder, PlayList, YosMediaItem, etc.)
  ├─ 构建 Media3 SessionToken + MediaController
  │   └─ 连接成功后:
  │       ├─ 加载历史播放列表 (PlayListV1)
  │       └─ 加载历史播放状态 (PlayStatus)
  │           └─ 恢复上一次播放的歌曲和队列
  └─ super.onCreate()

MainActivity.onCreate()
  ├─ installSplashScreen()
  ├─ enableEdgeToEdge()  // 边到边显示
  └─ setContent { YosMusicTheme { ... } }
      ├─ 权限检查 (READ_MEDIA_AUDIO / BLUETOOTH / POST_NOTIFICATIONS)
      ├─ 媒体扫描 (MusicLibrary.scanMedia)
      ├─ 设置导航 (AnimatedNavHost)
      ├─ 底部导航栏 (BottomNavigator: 主页 / 资料库)
      └─ 迷你播放器 + 全屏播放页 (拖拽展开)
```

### 权限声明

| 权限 | 用途 |
|------|------|
| `READ_MEDIA_AUDIO` | Android 13+ 读取音频文件 |
| `READ_EXTERNAL_STORAGE` | 旧版系统读取存储 |
| `BLUETOOTH_CONNECT` | 蓝牙音频输出 |
| `POST_NOTIFICATIONS` | 媒体通知显示 |
| `FOREGROUND_SERVICE` | 播放服务前台运行 |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | 媒体播放前台服务类型 |
| `WAKE_LOCK` | 播放时保持CPU唤醒 |
| `INTERNET` | 网络访问 |
| `VIBRATE` | 触摸振动反馈 |

---

## 6. 模块详解

### app 主模块

**包名**: `yos.music.player`
**应用ID**: `yos.music.player`
**最低SDK**: 23 (Android 6.0)
**目标SDK**: 34 (Android 14)

#### 架构设计原则

- **单 Activity 架构**：`MainActivity` 是唯一的 UI 入口，所有页面通过 Compose Navigation 实现
- **MVVM 模式**：ViewModels (`MainViewModel`, `MediaViewModel`, `ImageViewModel`) 管理 UI 状态
- **单例数据仓库**：`MusicLibrary`、`SettingLibrary`、`PlayListLibrary` 为 Kotlin object 单例
- **全局响应式状态**：`MediaViewModelObject`、`MainViewModelObject` 等 object 持有 `mutableStateOf` 属性，多组件订阅

#### 目录职责

| 目录 | 职责 |
|------|------|
| `code/` | 播放控制、音频元数据、歌词解析、渲染器 |
| `code/utils/lrc/` | LRC 歌词格式解析、动画配置、UI 配置 |
| `code/utils/others/` | 位图压缩、振动反馈、字符串工具 |
| `code/utils/player/` | FadeExo 播放淡入淡出效果 |
| `data/libraries/` | 数据仓库：音乐库、播放列表、设置 |
| `data/models/` | ViewModel 定义 |
| `data/objects/` | 全局可观察状态对象 |
| `ui/pages/` | 页面级 Composable 组件 |
| `ui/pages/library/` | 资料库子页面 (专辑/艺术家/歌曲/播放列表) |
| `ui/pages/settings/` | 设置子页面 |
| `ui/theme/` | 主题定义 (颜色、字体、圆角形状) |
| `ui/widgets/` | 可复用的 UI 组件 |

### overscroll_core 弹性滚动模块

**包名**: `com.cormor.overscroll.core`
**最低SDK**: 21

独立的 Android Library 模块，提供 iOS 风格的自定义弹性滚动 (OverScroll) 效果。

#### 核心组件

- **`Modifier.overScrollVertical()`** / **`Modifier.overScrollHorizontal()`**：扩展修饰符，添加越界弹性回弹
- **`rememberOverscrollFlingBehavior()`**：自定义 Fling 行为，处理在边界时的惯性滚动
- **`parabolaScrollEasing()`**：抛物线缓动函数 (`p=50f` 时表现与 iOS 一致)

#### 工作原理

通过 `NestedScrollConnection` 拦截滚动事件，在内容到达边界时应用抛物线阻尼，手指释放后通过 `Animatable` + Spring 动画回弹到原位。

---

## 7. 导航系统

### 路由定义

所有路由常量定义在 `ui/NavHost.kt` 的 `UI` 接口中：

```kotlin
interface UI {
    HomePage          // 主页
    Library           // 资料库
    NormalMusic       // 歌曲列表
    PlayLists         // 播放列表管理
    LocalArtists      // 艺术家列表
    LocalAlbums       // 专辑列表
    AlbumInfo         // 专辑详情

    interface Settings {
        Main                    // 设置主页
        LibraryOverview         // 资料库设置
        LyricGetter            // 状态栏歌词设置
        ExoplayerSetting       // 音频引擎设置
        About                   // 关于页面
        MediaCodec             // 解码器列表
        LyricSetting           // 歌词设置
        UserInterfaceSetting   // 界面设置
        NotificationSetting    // 通知设置
    }
}
```

共 **16 个路由**，使用字符串键 (如 `"HomePage"`, `"Library"`, `"ExoplayerSetting"` 等)。

### 导航方式

使用 `AnimatedNavHost` (来自 Accompanist)，带有自定义的页面转场动画：
- 前进：右侧滑入 + 淡入
- 后退：左侧滑出 + 淡出
- 弹出：反向动画

API 为 `NavController.toUI(route, data?)`，内部通过 URI 参数传递数据 (如 `$route/$data`)。

---

## 8. UI 页面清单

### 主页 (HomePage)

**文件**: `ui/pages/Home.kt`, `ui/pages/HomeNav.kt`

- 使用 `HorizontalPager` 实现两页切换：**首页** + **资料库**
- 首页显示推荐歌曲卡片 (5 首随机歌曲)，带有 Coil 加载的模糊背景图
- 资料库提供四个入口：播放列表、艺术家、专辑、歌曲

### 播放中页面 (NowPlaying)

**文件**: `ui/pages/NowPlaying.kt` (~2020 行，最复杂的页面)

三个子页面通过 HorizontalPager 切换：

| 子页面 | 内容 |
|--------|------|
| Album | 专辑封面、歌曲信息、播放进度滑块 |
| Lyrics | 卡拉OK逐字高亮歌词、翻译、二重唱/交替歌词布局 |
| Queue | 当前播放队列，拖拽排序 |

**附加功能**：
- YosFloatingLight 动画背景 (KenBurnsView)
- 底部音量滑块
- AirPlay/音频输出切换
- 自定义滑块轨迹绘制

### 资料库 (Library)

**文件**: `ui/pages/library/Library.kt`

四个导航入口的网格布局：
- 播放列表 → PlayLists
- 艺术家 → LocalArtists
- 专辑 → LocalAlbums
- 歌曲 → NormalMusic

### 歌曲列表 (NormalMusic)

**文件**: `ui/pages/library/NormalMusic.kt` (~497 行)

- 搜索功能 (含拼音搜索，使用 TinyPinyin)
- 排序切换 (字母/日期等)
- 歌曲行 (52dp 专辑封面 + 标题 + 艺术家)
- 分组的字母列表视图

### 专辑列表 (LocalAlbums) & 专辑详情 (AlbumInfo)

- **LocalAlbums**: 2 列网格布局，搜索过滤
- **AlbumInfo**: 封面图、播放/随机播放按钮、曲目列表、歌曲数量和总时长统计

### 艺术家列表 (LocalArtists)

带有圆形图片的艺术家列表，搜索过滤。

### 播放列表 (PlayLists)

- 收藏列表 + 自定义播放列表
- 新建/删除/重命名播放列表
- URI 解析 (外部导入的歌单)

### 设置页面体系

#### 设置主页 (Settings)
六个设置分类入口：
1. **资料库** → LibraryOverview (文件夹可见性管理)
2. **性能** → 通知设置、歌词设置、界面设置
3. **音频** → ExoPlayer设置 (音频属性、解码器、硬件参数)
4. **播放** → 预留
5. **扩展** → LyricGetter (状态栏歌词)
6. **关于** → About

#### 音频设置 (ExoPlayerSettings)
- 音频属性 (Usage / ContentType)
- 编解码器选择：Auto / System / FFmpeg
- 音频浮点输出开关
- 硬件音轨播放参数开关
- MediaCodec 解码器列表展示
- ⚠️ 「淡入淡出播放」开关 — UI 切换无效，详见第 17 章

#### 播放设置
- ⚠️ 「使用播放历史」开关 — UI 切换无效，对应的 "Recently Played" 和 "Annual Memories" 播放列表未实现，详见第 17 章

#### 界面设置 (UserInterfaceSetting)
- 主题选择 (浅色/深色/随系统)
- 模糊效果开关
- 屏幕圆角大小 (0-130px，首次使用弹出设置对话框)
- 音量滑块显示开关
- 背景效果开关

#### 歌词设置 (LyricSetting)
- 歌词字体粗细
- 行平衡模式
- 模糊效果

#### 通知设置 (NotificationSetting)
- 通知栏控制图标 (自定义布局: 随机/循环切换)

#### 状态栏歌词 (LyricGetter)
- 启用/禁用
- 框架钩子状态检测
- 调试发送按钮

#### 关于 (About)
- 应用名称与版本
- 开发者信息
- Telegram 交流链接

---

## 9. 数据层

### 数据持久化方案

项目采用 **MMKV + DataSaver** 作为数据持久化方案：

- **MMKV**: 腾讯开源的 Key-Value 存储库，基于 mmap，高性能
- **DataSaver**: Compose 响应式数据持久化库，封装 MMKV，提供 `mutableDataSaverStateOf` / `mutableDataSaverListStateOf` 委托
- **Gson**: JSON 序列化，注册了 `UriTypeAdapter` 处理 Android Uri 类型

### 核心数据模型

#### YosMediaItem (~30个字段)

自定义的媒体项数据类，映射自 Media3 的 `MediaItem`/`MediaMetadata`：

```kotlin
@Parcelize
data class YosMediaItem(
    val uri: Uri?,           // 文件URI
    val mediaId: String?,    // 媒体ID
    val mimeType: String?,   // MIME类型
    val title: String?,      // 标题
    val writer: String?,     // 作词
    val compilation: String?, // 合辑
    val composer: String?,   // 作曲
    val artists: String?,    // 艺术家
    val album: String?,      // 专辑
    val albumArtists: String?, // 专辑艺术家
    val thumb: Uri?,         // 封面图URI
    val trackNumber: Int?,   // 音轨号
    val discNumber: Int?,    // 光盘号
    val genre: String?,      // 流派
    val recordingDay: Int?,  // 录制日
    val recordingMonth: Int?, // 录制月
    val recordingYear: Int?, // 录制年
    val releaseYear: Int?,   // 发行年
    val artistId: Long?,     // 艺术家ID
    val albumId: Long?,      // 专辑ID
    val genreId: Long?,      // 流派ID
    val author: String?,     // 作者
    val addDate: Long?,      // 添加日期
    val duration: Long,      // 时长(ms)
    val modifiedDate: Long?, // 修改日期
    val cdTrackNumber: Int?  // CD音轨号
) : Parcelable
```

#### 播放列表与播放状态

```kotlin
data class PlayListV1(
    val mainMusicList: List<YosMediaItem>?,      // 主歌曲列表
    val playingMusicList: List<YosMediaItem>?    // 当前播放队列
)

data class PlayStatus(
    val music: YosMediaItem?,     // 当前播放歌曲
    val position: Long,           // 播放位置
    val shuffleModeEnabled: Boolean, // 随机模式
    val repeatMode: Int           // 循环模式
)

data class Folder(
    val name: String,
    val path: String,
    val songs: List<YosMediaItem>
)
```

### MusicLibrary (核心数据仓库)

`MusicLibrary` 是 Kotlin object 单例，负责：

1. **媒体扫描** (`scanMedia`)：通过 `libPhonograph` 库读取系统 MediaStore，转换成 `YosMediaItem` 列表
2. **数据查询**：提供 `songs`、`artists`、`albums`、`folders` 等计算属性，自动过滤隐藏项
3. **可见性管理**：`hideSong()` / `unHideSong()` / `hideFolder()` / `unHideFolder()`
4. **播放列表持久化**：`updatePlayList()` / `loadPlayList()` (Key: `"yos_play_list_v1"`)
5. **播放状态持久化**：`updatePlayStatus()` / `loadPlayStatus()` (Key: `"yos_player_play_status"`)
6. **类型转换**：`MediaItem.toYosMediaItem()` 和 `YosMediaItem.toMediaItem()`

持久化存储结构：
- MMKV 实例 ID: `"yos_player_core"`
- 歌曲列表用 DataSaver 的 `mutableDataSaverListStateOf` 管理 (自动持久化)
- 隐藏列表、文件夹列表同样通过 DataSaver 自动同步

### 全局状态对象

| 对象 | 路径 | 内容 |
|------|------|------|
| `MediaViewModelObject` | `data/objects/` | 歌词条目、专辑封面 Bitmap URI、播放状态、杜比标志、采样率、比特率 |
| `MainViewModelObject` | `data/objects/` | 当前歌词行索引 |
| `LibraryObject` | `data/objects/` | 当前浏览的专辑名、目标歌曲列表 |
| `SettingOptionsPageObject` | `data/objects/` | 设置页面间通信的请求/响应模式 |

---

## 10. 播放引擎

### 架构层次

```
YosPlaybackService (MediaSessionService)
  └─ MediaSession
      └─ ForwardingPlayer (包装 ExoPlayer)
          ├─ ExoPlayer (使用 YosRenderFactory)
          │   ├─ DefaultRenderersFactory (系统解码器)
          │   └─ FFmpegAudioRenderer (FFmpeg 软解)
          └─ FadeExo (淡入淡出效果)
```

### MediaController 单例

`code/MediaController.kt` 中的顶层 `MediaController` object 持有：

- `mediaControl`: Media3 `MediaController` 实例
- `musicPlaying`: 当前播放歌曲的响应式状态
- `playingMusicList`: 当前播放队列的响应式状态
- `mainMusicList`: 计算属性，指向 `MusicLibrary.songs`

核心方法 `prepare()`：
1. 根据传入的歌曲和队列构建 `List<MediaItem>`
2. 通过 `mediaControl?.setMediaItems()` 设置播放队列
3. 处理列表切换与同列表内切歌的不同逻辑
4. 自动恢复随机/循环模式
5. 持久化播放列表到 MMKV

### YosPlaybackService

`MediaSessionService` 的实现类 (在 `MediaController.kt` 同一文件中)，负责：

1. **ExoPlayer 创建**：配置音频属性 (Usage/Category)、渲染器工厂、解码器策略
2. **ForwardingPlayer**：重写 `play()`/`pause()` 以使用 FadeExo，重写 `isPlaying()` 判断真实播放状态
3. **媒体通知**：通过 `DefaultMediaNotificationProvider`，支持自定义通知布局 (随机/循环按钮)
4. **播放状态持久化**：在 `onEvents()` 中监听播放状态变化，延迟 200ms 后保存
5. **歌词处理**：在 `onTracksChanged()` 中解析 LRC 文件，提取音频质量信息 (采样率、比特率、杜比)
6. **状态栏歌词**：通过 Handler 轮询 (每 70ms) 更新状态栏歌词显示

### FadeExo 淡入淡出

`code/utils/player/FadeExo.kt`：为 `Player` 添加 `fadePlay()` 和 `fadePause()` 扩展函数，200ms 音量渐变，避免播放/暂停的突兀感。

### 音频渲染器

`YosRenderFactory` 配置三种解码器模式：

| 模式 | 对应设置 | 行为 |
|------|----------|------|
| Auto | `EXTENSION_RENDERER_MODE_PREFER` | 优先使用 FFmpeg 扩展渲染器 |
| System | `EXTENSION_RENDERER_MODE_OFF` | 仅使用系统 MediaCodec |
| FFmpeg | `EXTENSION_RENDERER_MODE_ON` | 强制使用 FFmpeg 渲染器 |

音频属性可配置：
- Usage: Media / Alarm / Notification 等
- ContentType: Music / Speech / Movie 等
- 浮点音频输出：开关
- 硬件音轨参数：开关

### 系统音频输出切换

`SystemMediaControlResolver` 提供厂商适配的音频输出切换：
- Android 14+ 使用 MediaRouter2
- 小米设备使用 `MiuiBluetoothController`
- 三星设备使用 `SemAudioManager`

---

## 11. 设置系统

### SettingLibrary

`data/libraries/SettingLibrary.kt` 定义了所有应用设置项，每个设置项通过 `DataSaver` 自动持久化到 MMKV。

**26 个设置项清单**：

| 设置项 | 存储键 | 默认值 | 说明 |
|--------|-----|--------|------|
| `NowPlayingShowVolumeBar` | `settings_performance_ui_nowplaying_show_volume_bar` | `true` | 播放界面显示音量条 |
| `CustomTheme` | `settings_performance_ui_theme` | `"Auto"` | 主题: Auto/Light/Dark |
| `ScreenCornerSet` | `settings_performance_ui_corner_set` | `false` | 是否已设置过屏幕圆角 |
| `ScreenCorner` | `settings_performance_ui_corner` | `"30"` | 屏幕圆角值 (0-130px) |
| `SongSort` | `yos_player_song_sort` | `TITLE` | 歌曲排序 |
| `EnableDescending` | `yos_player_enable_descending` | `false` | 排序启用降序 |
| `NowPlayingTranslation` | `now_playing_translation` | `true` | 歌词界面翻译显示 |
| `RefreshEveryTime` | `settings_library_refresh_everytime` | `false` | 每次启动刷新媒体库 |
| `LyricFontWeight` | `settings_performance_lyric_font_weight` | `"ExtraBold"` | 歌词字体字重 |
| `LyricLineBalance` | `settings_performance_lyric_line_balance` | `false` | 歌词平衡行模式 |
| `LyricBlurEffect` | `settings_performance_lyric_blur_effect` | `false` | 歌词模糊效果 |
| `NowplayingBackgroundEffect` | `settings_performance_ui_nowplaying_background_effect` | `false` | 播放界面背景动态效果 |
| `BarBlurEffect` | `settings_performance_ui_blur_effect` | `false` | 导航栏模糊效果 |
| `NotificationEnableIcon` | `settings_performance_notification_enable_icon` | `true` | 通知栏显示媒体控制图标 |
| `NotificationSmallerIcon` | `settings_performance_notification_smaller_icon` | `false` | 通知栏小尺寸图标 |
| `FadePlay` | `settings_audio_fade_in_out` | `true` | 淡入淡出播放 ⚠️ |
| `ListenHistory` | `settings_play_history` | `true` | 播放历史 ⚠️ |
| `StatusBarLyricEnabled` | `statusBarLyricEnabled` | `false` | 状态栏歌词开关 |
| `StatusBarLyricHooked` | `statusBarLyricHooked` | `false` | 歌词框架 Hook 状态 |
| `AudioAttributes` | `settings_audio_exoplayer_audio_attributes` | `true` | ExoPlayer 音频属性 |
| `Codec` | `settings_audio_exoplayer_codec` | `"Auto"` | 解码器: Auto/System/FFmpeg |
| `HardwareAudioTrackPlayBackParams` | `settings_audio_exoplayer_hardware_audio_track_playback_params` | `false` | 硬件音频轨道播放参数 |
| `AudioFloatOutput` | `settings_audio_exoplayer_audio_float_output` | `false` | 音频浮点输出 |
| `EnableExcludeSongsUnderOneMinute` | `settings_library_enable_exclude_songs_under_one_minute` | `true` | 排除一分钟以内的歌曲 ⚠️ |

> ⚠️ 标记表示该设置项存在定义但功能未完整连线 (详见第 17 章完整性分析)

### 设置项变更通知

设置项变更后不通过全局事件总线，而是通过 Compose 响应式状态自动触发 UI 重组。部分设置 (如解码器、音频属性) 需要重建 ExoPlayer 才能生效。

---

## 12. 主题系统

### YosMusicTheme

`ui/theme/Theme.kt` 定义顶层主题 Composable，接收：
- `darkTheme`: 深色模式标志，默认调用 `isFlamingoInDarkMode()`
- `dynamicColor`: 动态颜色 (Android 12+)，默认关闭

**深色模式判断逻辑** (`isFlamingoInDarkMode()`):
1. 如果设置项 `Theme != "System"`：返回 `Theme == "Dark"`
2. 如果设置项 `Theme == "System"`：跟随系统 `isSystemInDarkTheme()`

### 配色方案

两个静态配色方案 (不使用 dynamicColor 时)：

**浅色模式**：
- primary: `#F54047` (火烈鸟红)
- background: `#FFFFFF`
- onBackground: `#121212`
- surface: `#FFFFFF`

**深色模式**：
- primary: `#E64366` (火烈鸟红深色)
- background: `#0E0E0E`
- onBackground: `#FFFFFF`
- surface: `#0E0E0E`

### 颜色工具

- `withNight` 中缀扩展：`Color.Black withNight Color.White` → 根据当前主题返回对应颜色
- 设置项二级颜色：`settingBack` / `settingContainerBack` / `settingBackDark` / `settingContainerBackDark`

### 字体

`ui/theme/Type.kt` 定义 Typography，bodyLarge 使用 16sp。

### 圆角形状

`ui/theme/YosRoundedCornerShape.kt`：自定义的 Bezier 贝塞尔圆角形状，模拟 iOS 的连续曲线圆角。

`ui/theme/DeviceRoundedCornerShape.kt`：从设备固件资源读取原生圆角半径。

### 稳定性配置

`stability_config.conf` 声明以下包中的类为 `@Stable`，使 Compose 编译器优化重组：
- `android.*` 系统包
- `java.*` / `kotlin.*` 标准库
- `yos.music.player.*` 项目包
- `yos.music.player.data.libraries.*` 数据模型

---

## 13. 可复用组件

### 基础组件 (widgets/basic/)

| 组件 | 说明 |
|------|------|
| `BottomNavigator` | iOS 风格底部标签栏，带颜色动画过渡 |
| `ShadowImage` / `ShadowImageWithCache` | 带圆角、描边和阴影的网络/本地图片 |
| `Title` | 通用页面脚手架 (~627行)：大标题、滚动模糊效果、OverScroll 弹性滚动 |
| `YosBottomSheetDialog` | 模态底部弹窗，支持圆角、振动反馈、CupertinoSlider |
| `YosRoundColumn` | 圆角分组容器 + LazyListScope 扩展 |
| `YosTextField` / `SearchTextField` | 搜索输入框，带占位符和键盘操作 |
| `YosWrapper` | 通过 `key(hashCode)` 隔离重组作用域 |

### 效果组件 (widgets/effects/)

| 组件 | 说明 |
|------|------|
| `DropShadow` | 基于 Canvas + BlurMaskFilter 的下拉阴影 |
| `OverlayEffect` | iOS 风格叠加混色效果 (加法混合模式) |
| `YosFloatingLightView` | KenBurnsView 动画背景：饱和度增强、高斯模糊、深色遮罩，支持生命周期感知暂停/恢复 |

### 歌词组件

`YosLyricView` (~1562 行)：
- 卡拉OK式逐字高亮歌词
- 自动滚动到当前播放行
- 翻译显示 (多行歌词)
- Android 12+ RenderEffect 模糊效果
- 二重唱/交替歌词布局模式
- 空歌词时三点倒计时动画
- 可配置字体粗细 (Thin → Black)
- SubcomposeLayout 精确文本测量

### 音频质量指示器

`MusicQualityIndicator`：显示杜比全景声 / Hi-Res / 无损标识，带交叉淡入淡出动画切换。

### 设置专用组件

`ui/pages/settings/` 下的专用组件：
- `SelectItem` / `SwitchItem` / `LabelItem`：设置项模板
- `Divider` / `ListHeader`：分组分隔
- `SettingBackground`：设置页面背景框
- `OptionDialog`：底部选择对话框 (支持 CupertinoSlider)

---

## 14. 第三方依赖

| 依赖 | 用途 |
|------|------|
| **AndroidX Media3** (1.4.0) | ExoPlayer 音频引擎 + MediaSession |
| **Jetpack Compose** (1.7.0-beta07) | UI 框架 |
| **Material3** (1.2.1) | Material Design 3 组件 |
| **Coil Compose** (2.5.0) | Compose 图片加载 |
| **MMKV** (1.3.5) | 高性能 KV 持久化存储 |
| **Gson** (2.10.1) | JSON 序列化/反序列化 |
| **Accompanist** | 系统栏控制、导航动画、Insets、Pager |
| **Haze** (0.9.0-alpha06) | Compose 模糊/毛玻璃效果 |
| **Cupertino** (0.1.0-alpha04) | iOS 风格组件 (开关、滑块等) |
| **DataSaver** (1.1.9) | Compose 响应式数据持久化 |
| **Lyric-Getter-Api** (6.0.0) | 状态栏歌词框架 API |
| **libPhonograph** (AAR) | 本地媒体扫描库 |
| **TagLib** (1.0.0-alpha22) | 音频文件元数据读取 |
| **TinyPinyin** (2.0.2) | 拼音转换 (用于中文排序/搜索) |
| **KenBurnsView** (1.0.7, AAR) | 图片 Ken Burns 动画效果 |
| **Renderscript Toolkit** (AAR) | 图像处理 (模糊/饱和度) |
| **FFmpeg Decoder** (AAR) | FFmpeg 音频软解 |
| **UtilCodeX** (1.31.1) | Android 工具类库 |
| **Core Splashscreen** (1.0.1) | Android 12+ SplashScreen API |

---

## 15. 资源文件

### 多语言支持

| 语言 | values 目录 | 翻译条目数 |
|------|-------------|-----------|
| 英语 (默认) | `values/strings.xml` | 123 |
| 简体中文 | `values-zh-rCN/strings.xml` | 123 |
| 繁体中文 | `values-zh-rTW/strings.xml` | 123 |
| 日语 | `values-ja/strings.xml` | 123 |

### 主题资源

- `values/themes.xml`：启动页透明主题 (SplashScreen API)
- `values-night/themes.xml`：深色启动主题
- `values/colors.xml`：基础调色板 (紫/青/黑/白)
- `values-night/colors.xml`：深色模式颜色覆盖 (空)

### 图标资源

58 个矢量可绘制对象，分类如下：
- **导航图标**：标签栏图标 (主页/资料库)、返回箭头
- **播放器图标**：播放/暂停/下一曲/上一曲/随机/循环 (多种尺寸)
- **资料库图标**：播放列表/艺术家/专辑图标
- **质量标识**：杜比/Hi-Res/无损徽章
- **通知图标**：媒体控制按钮 (小尺寸)
- **其他**：搜索、更多、添加、收藏、空状态占位图

### 启动器图标

自适应图标 (mipmap-anydpi-v26)：
- 背景：白色 (#FFFFFF)
- 前景：Flamingo 矢量图
- 单色：Flamingo 矢量图

### 备份规则

`xml/backup_rules.xml` 和 `xml/data_extraction_rules.xml` 为默认模板 (全部注释)，未配置自动备份。

---

## 16. 开发注意事项

### 状态管理约定

- 使用 `YosWrapper` 包裹组件以控制重组范围 (用于调试 println)
- 全局共享状态放在 `data/objects/` 下的 object 中
- 数据持久化使用 `mutableDataSaverStateOf` / `mutableDataSaverListStateOf` 委托 (自动同步 MMKV)
- 非持久化的 UI 状态使用 `rememberSaveable` 或 `mutableStateOf`

### ProGuard 规则

Release 构建会：
- 移除所有 `Log.d/e/i/v/w/wtf` 调用
- 移除所有 `System.out.println/print` 调用
- 保留 Gson TypeToken 和反射相关类
- 保留 `Lyric-Getter-Api` 数据类
- 保留 `OverScrollKt` (Compose 修饰符需要)
- 保留 `yos.music.player.data.libraries.**` 类名 (Gson 序列化)

### 已知限制

- Release 构建仅支持 `armeabi-v7a` 和 `arm64-v8a` ABI
- 动态颜色仅支持 Android 12+ 且默认关闭
- FFmpeg 软解需要预置的 `lib-decoder-ffmpeg-release.aar`
- AndroidManifest 中存在 `android:extractNativeLibs` 弃用警告 (AGP 提示可移除)

### 崩溃处理

`YosBasicApplication` 设置了全局未捕获异常处理器：
1. 打印异常堆栈
2. 启动 `CrashActivity` 显示错误信息 (设备信息/ABI/完整堆栈)
3. 提供「复制详细错误」「复制简要错误」「重启应用」三个按钮
4. 杀掉当前进程

### 代码风格

- 文件内多处使用 `YosWrapper {}` 包裹可组合函数调用以输出重组日志 (`println`)
- 被注释的旧代码较多 (保存了旧版实现逻辑作为参考)
- 使用中缀函数 `withNight` 和 `by` 委托模式较多
- 页面组件通常无显式返回类型声明

---

## 17. 项目完整性分析

本节详细记录对全部 80 个源文件逐一审查后发现的未完成功能、遗留代码和潜在问题。

### 17.1 问题总览

| 严重程度 | 问题数量 | 类别 |
|----------|----------|------|
| 🔴 功能缺失 | 3 | 空文件 / 死代码 |
| 🟡 UI 断连 | 3 | 开关无效 / 设置未接线 |
| 🟠 逻辑未完成 | 1 | 已定义但未使用的过滤规则 |
| 🔵 代码遗留 | 4 | 大量被注释的旧实现 |

### 17.2 详细问题

#### 🔴 功能缺失

**1. ArtistInfo.kt — 空文件 (死代码)**

- 文件: `app/src/main/java/yos/music/player/ui/pages/library/artists/ArtistInfo.kt`
- 内容: 仅 2 行 `package` 声明，无任何类或函数
- 影响: 艺术家详情页功能完全不存在。导航路由中也没有 `ArtistInfo` 路由，因此该文件属于未清理的死代码。点击艺术家列表项后不会跳转到任何详情页。

**2. MainViewModel / MediaViewModel — 空 ViewModel**

- 文件: `data/models/MainViewModel.kt`、`data/models/MediaViewModel.kt`
- 内容: 
  - `MainViewModel` 仅含一个被注释掉的 `blurEffect` 状态声明，无任何活跃成员
  - `MediaViewModel` 为空类，仅继承 `ViewModel()`
- 影响: 两个 ViewModel 均在 `MainActivity` 中通过 `by viewModels()` 实例化，但均无实际功能。所有实际的状态管理通过 `data/objects/` 下的全局 object 完成，ViewModel 未起到应有的作用。

**3. BaseActivity — 空抽象类**

- 文件: `BaseActivity.kt`
- 内容: 继承 `ComponentActivity` 的空抽象类，`onCreate()` 中的 `WindowCompat.setDecorFitsSystemWindows(window, false)` 已被注释
- 影响: `MainActivity` 中使用 `enableEdgeToEdge()` 替代了此逻辑。该抽象类无实际价值，可安全删除。

#### 🟡 UI 断连 (设置开关无效)

**4. FadePlay 开关 — onClick 为空**

- 文件: `ui/pages/settings/Settings.kt:121`
- 代码: `SwitchItem(... onClick = { }, checkedLambda = { SettingsLibrary.FadePlay })`
- 现象: 音频设置中的「淡入淡出播放」开关可以显示当前状态，但点击**没有任何效果** — `onClick` 是空 lambda
- 根因: `SettingsLibrary.FadePlay` 设置项存在且能读写，但 UI 开关没有绑定修改逻辑。同时，`FadeExo.kt` 中的 `fadePlay()`/`fadePause()` **从不检查** `FadePlay` 设置值，始终执行淡入淡出，意味着该开关即使修复也只是一个 UI 装饰而无实际控制效果。

**5. ListenHistory 开关 — onClick 为空**

- 文件: `ui/pages/settings/Settings.kt:133`
- 代码: `SwitchItem(... onClick = { }, checkedLambda = { SettingsLibrary.ListenHistory })`
- 现象: 播放设置中的「使用播放历史」开关点击无效果
- 影响: 字符串资源 `settings_play_history_desc` 描述中提到的 "Recently Played"、"Annual Memories" 播放列表在代码中**完全不存在**。`SettingsLibrary.ListenHistory` 仅定义了存储键，没有任何读取或使用该值的代码。

**6. EnableExcludeSongsUnderOneMinute — 设置未使用**

- 文件: `data/libraries/SettingLibrary.kt:249`
- 现象: 该设置项已被定义 (`initialValue = true`)，但在 `MusicLibrary.scanMedia()` 和 `MusicLibrary.songs` 的过滤逻辑中**没有任何引用**
- 影响: 一分钟以内的短歌曲不会被排除，该设置项完全无作用

#### 🟠 逻辑未完成

**7. MediaController 中旧播放器监听器被注释**

- 文件: `code/MediaController.kt:548-568`
- 被注释的监听器: `onIsPlayingChanged`、`onRepeatModeChanged`、`onShuffleModeEnabledChanged`、`onPlaybackStateChanged`
- 现状: 被替换为单一的 `onEvents()` 方法 + `saveDataWithDelay()` 延迟保存
- 影响: 功能上已由新实现覆盖，无功能缺失。但新旧代码并存增加了维护困惑。

#### 🔵 代码遗留 (被注释的旧实现)

**8. MainActivity 权限对话框 (~90 行被注释)**

- 文件: `MainActivity.kt:1115-1184`
- 内容: 使用 `ModalBottomSheet` 的权限授予对话框（包含详细的接受/拒绝交互）
- 现状: 被简化为 `CheckAndRequestPermission()` 直接调用系统权限弹窗
- 影响: 无功能缺失，旧代码可作为参考

**9. YosBasicApplication Gson 类型适配器**

- 文件: `YosBasicApplication.kt`
- 内容: 被注释的 `UriSerializer`/`UriDeserializer`、`ImmutableListTypeAdapter`
- 现状: 已统一替换为 `UriTypeAdapter`
- 影响: 无功能缺失

**10. MusicLibrary 旧数据模型**

- 文件: `data/libraries/MusicLibrary.kt:29-46`
- 内容: 被注释的旧 `Music` 数据类（更简单的字段结构）和 `removeSong()` 方法
- 现状: 已迁移到 `YosMediaItem` (31 字段)
- 影响: 无功能缺失

**11. 未使用的设置项 `DarkWallpaper`、`VibratorClick`、`IgnoreSystemAnimationScale`**

- 这三个字符串原先被记录为设置项，但在当前的 `SettingLibrary.kt` 中**不存在**
- 实际的默认可定制振动 (`Vibrator.click`/`Vibrator.longPress`) 是硬编码的，不由设置项控制

### 17.3 未被使用的文件和代码

| 文件 | 行数 | 状态 |
|------|------|------|
| `ui/pages/library/artists/ArtistInfo.kt` | 2 | 仅有 package，死代码 |
| `data/SettingOption.kt` | 2 | 定义了 `SettingOption(String)` 但整个项目中无任何引用 |
| `BaseActivity.kt` | 19 | 空抽象类，`onCreate` 逻辑已被注释 |

### 17.4 建议修复优先级

| 优先级 | 问题 | 建议操作 |
|--------|------|----------|
| P0 | FadePlay 开关无效 | 修复 `onClick` 绑定，或移除该开关并用 `if(FadePlay)` 包裹 FadeExo 逻辑 |
| P0 | ListenHistory 开关无效 | 实现播放历史功能，或移除该设置项及其 UI |
| P1 | EnableExcludeSongsUnderOneMinute 未生效 | 在 `scanMedia()` 或 `songs` getter 中添加时长过滤逻辑 |
| P2 | ArtistInfo.kt 死代码 | 删除文件，或实现艺术家详情页并在导航中注册 |
| P2 | 空 ViewModel (MainViewModel/MediaViewModel) | 将 `data/objects/` 中的状态迁移到 ViewModel，或删除空 ViewModel |
| P2 | BaseActivity 空抽象类 | 删除或合并到 MainActivity |
| P3 | 大量注释的旧代码 | 清理以提升可维护性 |

---

> 文档基于 Flamingo 项目 master 分支生成，最后更新: 2026-05-19。包含完整项目完整性审查结果 (第 17 章)。
