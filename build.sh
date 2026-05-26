#!/bin/bash

# 1. 设置环境变量并执行打包（加入了加速参数和内存优化）
echo "🚀 开始编译 Release 包 (开启并行与缓存优化)..."
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home && \
./gradlew assembleRelease \
    --parallel \
    --build-cache \
    --configuration-cache \
    --configure-on-demand \
    -Dorg.gradle.jvmargs="-Xmx4096m"

# 检查上一步打包是否成功，失败则退出
if [ $? -ne 0 ]; then
    echo "❌ 编译失败，请检查错误日志！"
    exit 1
fi

APK_DIR="app/build/outputs/apk/release/"

# 2. 根据参数判断后续行为
if [ "$1" = "install" ]; then
    echo "📲 检测到 install 参数，开始安装到设备..."
    
    # 寻找匹配的 APK 文件（忽略大小写）
    APK_FILE=$(find "$APK_DIR" -iname "Flamingo*.apk" | head -n 1)
    
    if [ -n "$APK_FILE" ]; then
        echo "Found APK: $APK_FILE"
        adb install "$APK_FILE"
    else
        echo "❌ 未找到匹配的 Flamingo*.apk 文件，请检查路径！"
        exit 1
    fi
else
    echo "📂 编译完成，正在打开输出目录..."
    open "$APK_DIR"
fi

echo "✅ 任务完成！"
