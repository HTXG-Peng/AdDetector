# AdDetector

一个带简单界面的 Android 前台应用记录器。核心是一个 `AccessibilityService`：
监听所有应用的 `TYPE_WINDOW_STATE_CHANGED`，把“时间 + 从哪个应用跳到哪个应用 +
在来源应用停留了多久”实时显示在 App 里，同时写入本地日志。

当来源应用停留时间很短、且最近没有用户点击时，会标记为 **“疑似自动跳转”**，
并发送一条通知。

不联网，不读取窗口内容，只使用包名、事件时间和点击时间。

## 功能

- 实时列表：最新记录在最上面，显示 `MM-dd HH:mm:ss`、`应用 A → 应用 B`、停留时长。
  只有打开 AdDetector 时列表才可见；切到别的应用时服务仍在后台记录，
  回来后会从内存和日志补齐，不会丢记录。
- 疑似自动跳转：红色标记，并触发通知（5 秒内最多一条）。
- 本地日志：`/sdcard/AdDetector.log`。
- 清空按钮：同时清空界面和日志。
- 一个界面内可直接跳转“无障碍设置”和“所有文件访问权限”。

> “疑似自动跳转”只是启发式判断，不是系统级证明。判断条件：
> 来源应用停留 < 2.5 秒，且最近 3 秒内没有收到来源应用内的点击事件，
> 且两端都不是桌面/系统 UI。桌面和系统 UI 里的点击不会计入“用户刚刚操作过”。

## 项目结构

```text
AdDetector/
├── .github/
│   └── workflows/
│       └── build-apk.yml        # GitHub Actions 自动构建 APK
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── README.md
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/addetector/
        │   ├── MainActivity.kt          # 列表界面
        │   ├── AdDetectorService.kt     # 无障碍服务
        │   ├── JumpEvent.kt             # 一条跳转记录
        │   ├── EventStore.kt            # 最近 300 条内存记录
        │   ├── LogWriter.kt             # 文件读写
        │   ├── AppLabels.kt             # 包名 → 应用名
        │   ├── JumpAdapter.kt           # 列表适配器
        │   └── Notifications.kt         # 疑似跳转通知
        └── res/
            ├── layout/activity_main.xml
            ├── layout/item_jump.xml
            ├── values/strings.xml
            ├── values/colors.xml
            ├── values/themes.xml
            ├── values-v27/themes.xml
            ├── values/ic_launcher_background.xml
            ├── drawable/ic_stat_alert.xml
            ├── drawable/ic_launcher_foreground.xml
            ├── mipmap-anydpi-v26/ic_launcher.xml
            ├── mipmap-anydpi-v26/ic_launcher_round.xml
            └── xml/accessibility_service_config.xml
```


## 环境

- Android Studio（AGP 8.2.2 / Gradle 8.2+ / JDK 17）
- `minSdk 26`，`targetSdk 34`

## 构建

用 Android Studio 打开本目录，等待 Gradle Sync 完成后，选择
**Build → Build Bundle(s) / APK(s) → Build APK(s)**。

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

> 本目录只包含源码，没有预编译 APK；需要按上面的步骤在本机构建。

> 项目未附带 `gradlew` 二进制 wrapper。用 Android Studio 打开后，如果提示缺少
> Gradle，按提示生成 wrapper（选 Gradle 8.2+）或在设置里指定本机 Gradle 安装；
> 也可以装好 Gradle 8.2+ 后执行 `gradle wrapper --gradle-version 8.2.1`。

## 没有 Android Studio：用 GitHub Actions 生成 APK

源码不能直接拷到手机上安装，手机需要的是 `.apk`。如果本机没有 Android Studio，
可以用项目里自带的 GitHub Actions 工作流，让 GitHub 的服务器帮你编译：

1. 新建一个 GitHub 仓库，把本目录的**全部内容**上传（注意包含 `.github` 目录）。
2. 打开仓库的 **Actions** 页，选择 **Build debug APK**，点 **Run workflow**。
   推送到 `main` 或 `master` 分支也会自动触发。
3. 等构建完成（约几分钟），在该次运行页面底部下载 artifact
   `AdDetector-debug-apk`。
4. 解压后得到 `app-debug.apk`，传到手机安装。debug 包使用调试签名，
   安装时需要在手机上允许“安装未知来源应用”。

> 这条路需要一个 GitHub 账号；构建发生在 GitHub 服务器上，不需要本机装 SDK。
> 仓库不要放日志文件或包含个人信息的文件。

## 安装与授权

> 直接把源码目录拷到手机不能安装；请先按上面任一方式得到 `app-debug.apk`。

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

打开 App 后按界面按钮操作：

1. **开启无障碍服务**：
   设置 → 无障碍 → 已安装的服务 → AdDetector 前台应用记录 → 开启。
   Android 13+ 侧载安装后，如果开关是灰色的，先进入
   “应用信息 → 右上角菜单 → 允许受限设置”。

2. **授予“所有文件访问权限”**（Android 11+，用于写 `/sdcard/AdDetector.log`）：
   点界面上的“文件权限”按钮，或：
   设置 → 应用 → AdDetector → 特殊应用权限 → 所有文件访问。

   也可以：

   ```bash
   adb shell appops set com.example.addetector MANAGE_EXTERNAL_STORAGE allow
   ```

3. **允许通知**（Android 13+）：首次打开 App 会请求；拒绝也不影响列表和日志。

## 日志

- 有权限：`/sdcard/AdDetector.log`
- 无权限时自动回退：`/sdcard/Android/data/com.example.addetector/files/AdDetector.log`

每行格式：

```text
2026-11-23 21:20:01.123	com.example.appa	com.example.appb	850	AUTO
```

字段依次为：时间、来源包名、目标包名、在来源应用停留的毫秒数、是否疑似自动跳转
（`AUTO` 或 `-`）。以 `#` 开头的行是服务启动/停止标记。

查看：

```bash
adb shell cat /sdcard/AdDetector.log
adb pull /sdcard/AdDetector.log
```

## 说明与限制

- Android 11+ 不允许普通应用直接写 `/sdcard` 根目录，必须授予“所有文件访问权限”；
  否则会写到应用专属目录，App 内查看和记录功能不受影响。
- Android 10 及以下写根目录需要 `WRITE_EXTERNAL_STORAGE` 运行时权限。点界面上的
  “文件权限”按钮即可申请；也可以执行
  `adb shell pm grant com.example.addetector android.permission.WRITE_EXTERNAL_STORAGE`。
  未授权时自动写到应用专属目录，不影响界面记录。
- 无障碍服务无法知道跳转是否 100% 由广告触发，因此使用“短停留 + 无点击”的启发式。
  有些 App 不暴露点击事件，可能产生误报；有些广告等待较久再跳，可能漏报。
- 记录会包含桌面、系统 UI、输入法等包名，这是正常的。
- 日志是明文，包含应用包名和时间；请自行注意隐私。
