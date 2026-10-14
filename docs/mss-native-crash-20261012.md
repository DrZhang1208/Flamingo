# 播放中原生闪退排查（2026-10-12）

## 已取得的设备证据

荣耀 BVL-AN00 的 crash buffer 和 ActivityManager exit-info 记录了三次 MSS / LiteRT 原生崩溃：

| 时间 | 位置 | 说明 |
| --- | --- | --- |
| 13:33 | `libLiteRtClGlAccelerator.so + 0x29cb94` | GPU 编译模型路径 |
| 17:16 | `libLiteRt.so + 0x508380` | `LiteRtCreateModelFromBuffer` 路径，进程已运行 1123 秒 |
| 18:06 | `libLiteRt.so + 0x508380` | 相同模型加载路径，进程已运行 3020 秒 |

三次都是 `ExoPlayer:Playb` 名称的原生线程收到 `SIGABRT`。17:16、18:06 的应用退出记录均为 `APP CRASH(NATIVE)`，不属于系统低内存回收。线程名继承自创建者；堆栈尾部是 MSS 创建的 pthread 工作线程，不能仅凭名称认定崩溃发生在 Media3 的主播放线程。

崩溃库的 Build ID 与当前仓库匹配：

- `libLiteRt.so`: `b71e946f793ac133578c60c16104673c`
- `libLiteRtClGlAccelerator.so`: `bca2373136a3716bedb54be5e630b357`

两处失败位置均已反汇编确认：调用 `pthread_key_create`，返回非零时直接调用 `abort()`。这是线程局部存储 key 申请失败，Kotlin `runCatching` 无法拦截原生进程终止。[Android bionic 实现](https://android.googlesource.com/platform/bionic/+/main/libc/bionic/pthread_key.cpp)在 key 表耗尽时返回 `EAGAIN`。

## 资源生命周期与修复

`MssAudioProcessor.onReset()` 会释放 bridge；下次配置重新创建。预编译的 `libmss_engine.so` 在创建时 `dlopen` LiteRT，在释放时 `dlclose` LiteRT 和 GPU 库（GPU 路径释放点为 `0x313a0`、`0x313ac`）。

这些库内含为进程生命周期设计的 TLS 支持：例如 LiteRT 中 `0x4e987c` 为 libc++ 的线程数据申请 key，而其唯一的 `pthread_key_delete` 调用只处理另一个 emutls key。LLVM [libc++ 的实现说明](https://github.com/llvm/llvm-project/blob/main/libcxx/include/__thread/thread.h)也明确保留线程数据的 key。因此，反复卸载、重载库是资源耗尽的具体嫌疑路径。注意：emutls 自身有卸载时的 key 清理，不能将问题简单归因于 emutls 未释放。

修复在 `MssNativeBridge` 的类加载器中保留 LiteRT 库引用；启用 GPU 时同样保留 GPU 库引用，让引擎的 `dlclose` 不再卸载底层运行库。GPU 库加载失败时继续使用 CPU。bridge、模型、线程和音频缓冲仍按原有方式释放，没有保留全部引擎实例。

这针对已确认的 TLS 申请失败及上述重复加载路径；尚未在设备上统计 key 增长或完成修复前后的长时间对照，不能据此宣称所有偶发闪退都已消除。

## 验证

- 187 项 JVM 测试通过，无失败、错误或跳过。
- Release APK 与 Android 测试 APK 构建成功。
- Release APK 的 v1 / v2 签名验证通过，并检查了 R8 后的 DEX，确认两处 `System.loadLibrary` 保留在最终安装包中。
- 新增两项原生设备回归：CPU / GPU 分别创建、配置、释放 24 次，核验运行库映射保持稳定，且 bridge 释放后状态正确、重复关闭安全。
- 设备在取证后断开；按用户“先修改代码，我自己测试”的要求，未执行新增设备测试，也未安装新版。真机连续播放、切歌、CPU / GPU 模式及人声分离开关的回归由用户进行。
- 原始取证文件本机暂存于 `/tmp/flamingo-crash-buffer.log`、`/tmp/flamingo-exit-info.log`、`/tmp/flamingo-recent-logcat.log`，未加入 Git。
- 安装包已上传 WebDAV 的 `FnOS/下载/Flamingo-latest-release.apk`，下载远端文件后 SHA-256 与本地一致：`9eac5e27148441327239fa717e0b853b976ed549913c62815255f570f1835f8d`。
