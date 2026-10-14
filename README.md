# CalendarWidget2x2 (Kotlin)

原生 Android 桌面日历小组件，使用 Kotlin 编写，默认浅色圆角背景，并高亮今天。

## 功能
- 当前年月与整月日期
- 周日到周六排列
- 蓝色圆形高亮今天
- 原生 Android App Widget，可放在桌面并调整尺寸
- GitHub Actions 自动构建 Debug APK 并上传 Artifact

## 上传 GitHub 编译
1. 解压本项目，将里面的所有文件上传到 GitHub 仓库根目录（不要只上传 ZIP）。
2. 打开仓库的 **Actions** 页面。
3. 选择 **Build Android APK**，点击 **Run workflow**；推送到 `main` 或 `master` 也会自动触发。
4. 等待任务完成，在运行记录底部的 **Artifacts** 下载 `CalendarWidget2x2-debug`。
5. 解压 Artifact，得到 `app-debug.apk`，安装到 Android 手机上。

项目内的 `gradlew` 会在首次运行时下载 Gradle 8.7，因此无需先手动生成标准 Gradle Wrapper 文件。GitHub Actions 运行环境需要能访问 Gradle 官方分发站点。

## 安装桌面小组件
安装 APK 后，长按手机桌面空白处，进入“小组件”，找到“桌面日历”，添加后调整到 2×2。

## 说明
- 当前生成的是 Debug APK，适合个人测试；正式发布需要配置签名的 Release 构建。
- 不需要第三方 API 或网络权限。
- 日期、时区或系统时间变化时会触发更新；不同桌面启动器对组件实际尺寸的呈现可能略有差异。

### 关于 Gradle 文件夹

项目包含 `gradle/wrapper/gradle-wrapper.properties`，用于记录 Gradle 8.7 的发行版地址。当前根目录的 `gradlew` 是轻量引导脚本，会在 GitHub Actions 环境中下载并调用 Gradle 8.7，因此此版本不依赖标准 Wrapper 的 `gradle-wrapper.jar`。
