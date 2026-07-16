# Input Accessibility - 双屏输入辅助

## 项目简介

Input Accessibility 是一款为双屏 Android 设备设计的输入辅助应用，目前主要适配 AYANEO Pocket DS。应用通过无障碍服务监听主屏输入框，在副屏打开输入界面，并把文字和 Enter 操作同步回主屏目标控件。

应用不会替代系统输入法。用户仍可在副屏使用自己选择的系统键盘。

## 主要功能

- 双屏输入：主屏输入框获得焦点后，自动在副屏显示输入界面。
- 实时同步：副屏文字通过 `ACTION_SET_TEXT` 实时同步到主屏目标输入框。
- Enter 支持：同时处理软键盘 Editor Action、实体键盘 Enter、数字键盘 Enter，以及部分浏览器返回的 `IME_NULL + KeyEvent`。
- 输入类型适配：同步 hint、已有文字、input type 和推断后的 IME action，并区分提交型与多行输入框。
- Chrome / Edge 兼容：分别处理浏览器网址栏和网页内普通表单输入框。
- 常驻模式：勾选副屏标题栏的“常驻”后，主屏输入框失焦时副屏应用保持打开，等待下一个输入目标。
- 键盘自动恢复：常驻模式下提交内容后，会立即并分阶段重新请求副屏键盘，减少键盘消失时间。
- 焦点保护：忽略重复的浏览器焦点事件，避免输入内容被重复绑定、清空或失去同步目标。
- 深色模式：跟随系统浅色／深色主题。

## 2026-07 更新内容

### Enter 与浏览器处理

- 增加软键盘和实体键盘两条 Enter 监听路径，并对重复 Enter 做短时间去重。
- Enter 前刷新无障碍节点并再次同步最新文字，避免最后一个字符尚未写入目标框。
- 当 `ACTION_IME_ENTER` 失败时，会尝试重新聚焦／点击目标节点后重试。
- Chrome 和 Edge 的网址栏不再按多行输入处理，避免 Enter 变成换行。
- 网址栏提交使用浏览器导航 Intent：
  - 已有 URI scheme 的内容直接打开。
  - 类似网址的内容自动补上 `https://`。
  - 其他文字作为 Google 搜索关键词处理。
- 浏览器网页内的普通表单输入框不会被误判为网址栏；提交前保持或恢复网页字段焦点，再执行 `ACTION_IME_ENTER`。
- 浏览器字段只根据浏览器 package、网址栏 view ID、hint 和 content description 判断；普通网页内名为 “Search” 的字段仍按网页表单处理。

当前内置识别的浏览器 package 包括：

- Google Chrome：`com.android.chrome`、`com.google.android.apps.chrome`、`com.chrome`
- Microsoft Edge：`com.microsoft.emmx`、`com.microsoft.emmx.beta`、`com.microsoft.emmx.dev`、`com.microsoft.emmx.canary`

### 常驻模式与键盘

- 副屏输入界面新增“常驻”选项，选择结果会保存在本机。
- 常驻模式开启时，目标输入框失焦后只解除目标绑定，不关闭副屏 Activity。
- 主屏再次点击输入框时，会复用现有副屏 Activity、绑定新目标并重新显示键盘。
- 提交后通过立即及延迟重试，把副屏 Activity 带回前台并重新请求系统键盘。
- 浏览器目标会保留焦点供文字同步和 Enter 使用；非浏览器目标可清除焦点，让副屏重新取得 IME 控制权。
- 增加焦点丢失抑制时间窗、浏览器导航抑制时间窗及重复节点判断，降低关闭循环、重复启动和闪退风险。
- “关闭”按钮仍会强制关闭副屏输入界面，不受常驻选项影响。

### 验证

- 已在 AYANEO Pocket DS、Android 13 / API 33 实机安装测试。
- Chrome 网页普通表单已通过本地 HTTP 测试页验证，Enter 后服务器实际收到提交请求，而不只是依赖 `performAction()` 的返回值。
- Chrome 网址栏文字同步、浏览器导航和提交后的键盘恢复均经过设备日志验证。
- Debug 与 Release 构建均已通过；Release APK 已通过 APK Signature Scheme v3 验证。

用于回归验证的测试页和设备日志保存在 `logs/` 目录。

## 使用方法

### 安装与授权

1. 把 APK 安装到双屏设备。
2. 打开应用并点击“打开无障碍设置”。
3. 在无障碍设置中开启“副屏输入辅助”。
4. 如果系统提示“出于安全考虑，此设置目前不可用”，进入“系统设置 > 应用 > 副屏输入辅助 > 右上角菜单”，选择“允许受限制的设置”，再重新开启无障碍服务。

### 基本输入流程

1. 在主屏应用中点击输入框。
2. 副屏输入界面自动显示并弹出系统键盘。
3. 在副屏输入文字，内容会实时同步到主屏。
4. 点击键盘上的完成、搜索、发送或 Enter，应用会向主屏目标执行对应操作。
5. 主屏输入框失焦后，默认关闭副屏输入界面。

### 常驻模式

1. 在副屏标题栏勾选“常驻”。
2. 主屏输入框失焦后，副屏应用保持打开并进入等待状态。
3. 主屏点击另一个输入框后，副屏会自动绑定新目标并恢复键盘。
4. 如需退出常驻状态，可取消勾选，或直接点击“关闭”。

## 编译

### 环境要求

- JDK 17
- Android SDK Platform 36
- Android Build Tools 36.0.0
- Android Platform Tools（如需使用 `adb`）

项目配置：

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 33`
- Kotlin JVM target 11

### Windows PowerShell

根据本机安装位置设置环境变量：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk17"
$env:ANDROID_SDK_ROOT = "C:\path\to\android-sdk"
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:ANDROID_SDK_ROOT\build-tools\36.0.0;$env:Path"
```

编译 Debug APK：

```powershell
.\gradlew.bat assembleDebug --no-parallel
```

输出位置：

```text
app\build\outputs\apk\debug\app-debug.apk
```

编译 Release APK：

```powershell
.\gradlew.bat assembleRelease --no-parallel
```

默认输出为未签名 APK：

```text
app\build\outputs\apk\release\app-release-unsigned.apk
```

### 对 Release APK 对齐及签名

```powershell
$releaseDir = "app\build\outputs\apk\release"

zipalign -p -f 4 `
  "$releaseDir\app-release-unsigned.apk" `
  "$releaseDir\app-release-aligned.apk"

apksigner sign `
  --ks "C:\path\to\keystore.jks" `
  --ks-key-alias "your-key-alias" `
  --out "$releaseDir\app-release-signed.apk" `
  "$releaseDir\app-release-aligned.apk"

apksigner verify --verbose "$releaseDir\app-release-signed.apk"
```

安装到已连接的设备：

```powershell
adb install -r app\build\outputs\apk\release\app-release-signed.apk
```

## 项目结构

```text
app/src/main/
|-- java/com/brucewang/inputaccessibility/
|   |-- MainActivity.kt                    # 主界面及无障碍设置入口
|   |-- InputAccessibilityService.kt       # 主屏焦点检测及目标分类
|   `-- InputActivity.kt                   # 副屏输入、同步、Enter 与常驻逻辑
|-- res/
|   |-- layout/
|   |   |-- activity_main.xml              # 主界面布局
|   |   `-- activity_input.xml             # 副屏输入及“常驻”控件
|   |-- values/                            # 浅色主题、颜色及字符串
|   `-- values-night/                      # 深色主题资源
`-- AndroidManifest.xml
```

## 已知限制

- Chrome / Edge 在网址栏导航时可能由系统短暂隐藏键盘；常驻模式会尽快恢复，但无法保证完全没有收起再弹出的动画。
- 部分应用或网页使用未暴露标准无障碍动作的自定义输入控件，可能无法检测、同步或提交。
- IME action 由 hint、文字、content description、view ID 和 input type 推断，第三方控件提供的信息不足时可能不准确。
- 浏览器更新可能更改网址栏的 view ID 或无障碍结构，届时需要更新识别规则。
- AYANEO Pocket DS 只启用主屏时，应用可能仍然检测到系统登记的副屏并尝试启动副屏输入界面。
- 本项目最低支持 Android 13 / API 33。

## 隐私说明

应用需要无障碍服务权限来读取当前输入目标并写入用户在副屏输入的内容。应用本身不会收集、保存或上传输入内容。

## 作者

Bruce Wang
