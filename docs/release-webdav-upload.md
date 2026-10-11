# Release 自动上传

运行 `:app:assembleRelease` 时，成功打包 APK 后自动运行 `:app:uploadReleaseToWebDav`，读取 AGP 的 `output-metadata.json` 定位真实产物，上传到按 APK SHA-256 命名的临时文件，回读并校验 SHA-256 后，通过 WebDAV MOVE 替换目标目录的 `Flamingo-latest-release.apk`。成功发布后临时文件通过 MOVE 移走，不保留额外副本。支持 SabreDAV partial-update 的服务器使用 1 MiB 分块 PUT/PATCH，避免整包上传触发公网代理的请求超时。其他服务器回退为整包 PUT。

凭据仅保存在机器本地的 `~/.config/flamingo/release-webdav.json`，目录权限 700、文件权限 600，不加入仓库。可通过 `FLAMINGO_WEBDAV_CONFIG` 指定其他配置路径。配置字段为 `url`、`username`、`password`、`directory`；仅支持 HTTPS，不携带凭据跟随重定向，不输出账号或密码。

手动检查（创建、读取并删除一个随机命名的临时小文件）：

```sh
python3 scripts/upload_release_webdav.py --check
```

手动重传已构建的 APK：

```sh
python3 scripts/upload_release_webdav.py
```

没有本机配置时跳过上传。上传失败时 Gradle 明确警告，保留本地构建成功的结果；不能把 `BUILD SUCCESSFUL` 当作上传成功，需要看到 `Release uploaded; downloaded SHA-256 matches local APK.`。独立运行上传脚本失败时退出码为 1。

2026-10-11 初次验证：认证和 FnOS/下载 目录的 PROPFIND 成功（207），PUT 临时小文件返回 403，GET/DELETE 该探针均为 404，确认没有创建成功。需要服务器放行写入后再验证 APK 上传与校验。无关服务器文件未修改。

用户开启写权限后复测：临时文件上传、下载内容比对与删除均成功。

整包 PUT 验证遇到公网代理 524 超时，不能仅通过增加客户端超时解决。小文件 PATCH 探针返回 204，随后已清理。按 [SabreDAV HTTP PATCH 规范](https://sabre.io/dav/http-patch/) 使用明确的字节范围和 Content-Length 分块写入；只有下载散列通过后才替换正式文件。传输错误时保留当前 APK 对应的临时文件，下次重传先下载校验已传前缀，再从正确位置继续。不操作其他文件。字节范围 PATCH 支持安全重试；读写连接错误和临时服务端错误会重试最多 3 次。

最终验证：新网络下断点续传完成，临时文件完整 SHA-256 校验通过，MOVE 发布成功；再次回读正式路径，文件大小 15,083,748 字节且 SHA-256 与本地 Release 一致。assembleRelease 的自动上传任务已实际触发；Gradle 配置缓存复用及任务顺序检查通过。


## 可复用 Skill 与文件上传

本机 Skill 安装在 `~/.codex/skills/webdav-upload`。下次可直接请求“使用 $webdav-upload 上传最新 Release”，或指定需要上传的文件。Skill 自带独立脚本，凭据不包含在 Skill 中。

项目脚本也支持任意文件，默认使用本地文件名：

```sh
python3 scripts/upload_release_webdav.py --file /absolute/path/file.apk
python3 scripts/upload_release_webdav.py --file /absolute/path/file.apk --remote-name custom.apk --directory 'FnOS/下载'
```

可用 `--config PATH` 指定私有配置。指定同名目标会在校验成功后覆盖该文件；临时文件同时按目标名称和内容散列区分。脚本将完整本地文件读入内存，上传大文件时需要相应内存。原有无参数 Release 上传及 Gradle 自动上传保持不变。
