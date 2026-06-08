  #!/bin/bash

  # 配置已移到 gradle.properties，这里只需一句
  echo "🚀 开始编译 Release 包..."

  JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
  if [ -z "$JAVA_HOME" ]; then
      echo "❌ 未找到 JDK 21，请先安装 Temurin/OpenJDK 21"
      exit 1
  fi
  export JAVA_HOME
  ./gradlew assembleRelease

  if [ $? -ne 0 ]; then
      echo "❌ 编译失败！"
      exit 1
  fi

  APK_DIR="app/build/outputs/apk/release/"

  if [ "$1" = "install" ]; then
      echo "📲 安装到设备..."
      APK_FILE=$(find "$APK_DIR" -iname "Flamingo*.apk" | head -n 1)
      if [ -n "$APK_FILE" ]; then
          adb install "$APK_FILE"
      else
          echo "❌ 未找到 APK"
          exit 1
      fi
  else
      open "$APK_DIR"
  fi

  echo "✅ 完成！"
