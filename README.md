# 智能药物浓度预测平台 · Android / Drug Gradient Chip Android

面向微流控芯片浓度预测的 Android 应用源码。提供 Forward、Reverse 和 Conversational Assistant，支持简体中文、英语以及跟随系统语言。推理通过设备本地 ExecuTorch 执行，不依赖远端推理服务。

Android application source for microfluidic chip concentration prediction, with Forward Prediction, Reverse Prediction and a Conversational Assistant. English, Simplified Chinese and follow-system language selection are supported. Inference runs locally through ExecuTorch without a remote inference service.

## 版本 / Version

| Item | Value |
| --- | --- |
| Application ID | `com.druggradientchip.android` |
| Version name | `0.4.0-rc1` |
| Version code | `4` |
| Release channel | Debug-signed pre-release / Debug 签名测试版 |
| Android | min SDK 29, target SDK 36, compile SDK 36.1 |

这是从历史 Android 工程整理的独立源码快照，不继承原科研仓库的 Git 历史。来源提交为 `f46b1c826521b52937e9995e257d85028ef4027e`；此处的初始提交和 `v0.4.0-rc1` 标签属于新的独立历史。版本来源信息不等于既有 APK 的可复现构建证明。

This is a curated, independent source snapshot derived from the historical Android project. The original commit above is provenance only, not a parent of this repository. Its new initial commit and `v0.4.0-rc1` tag do not establish a reproducible source-to-APK relationship.

## 功能 / Features

- **Forward Prediction**：支持 4–10 个芯片；按芯片数量选择模型，输出每个芯片的浓度。Model routing supports 4–10 chips and returns one concentration per chip.
- **Reverse Prediction**：输入固定六个目标浓度及明确的高浓度入口，以本地搜索和 Forward 复核求解几何参数；支持进度和取消。A fixed-six local search uses an explicit high-concentration inlet, reports progress, supports cancellation and rechecks the selected candidate through Forward inference.
- **Conversational Assistant**：确定性的双语指令解析、草稿确认与执行控制，调用同一 Forward/Reverse 运行时。A deterministic bilingual parser, draft confirmation and execution controller share the Forward/Reverse runtimes; no hosted language model is required.
- **语言与界面 / Language and UI**：支持中英切换、跟随系统及横屏工作界面。Supports English/Chinese switching, follow-system locale and a landscape workspace.
- **ExecuTorch 离线推理 / Offline inference**：运行模型位于设备本地。Local inference requires the private model assets described below; dependency downloads during development are a separate build-time requirement.

## 下载 / Downloads

在 [GitHub Releases](https://github.com/ZZH-0427/drug-gradient-chip-android/releases) 查找 `v0.4.0-rc1` 预发布测试版。发布审批完成后，预期附件为 `智能药物浓度预测平台-Android-v0.4.0-rc1-debug.apk` 和同名 `.apk.sha256`。本次源码准备未创建 Release 或上传附件；若页面尚无 Release，表示尚未发布。

After publication approval, obtain the Debug APK and its `.apk.sha256` file from the Releases page. No Release or asset upload was performed during source preparation; an empty page means publication is still pending.

Existing APK: **59,657,335 bytes**; SHA256:

```text
3ced38ae1b725c02d4b088c3d1fd0afa4f16763dce1e212b3881f78b301dc464
```

这是 Debug 自签名测试版，不是生产签名稳定版。现有签名保持原样，没有重新签名。APK 作为 Release 附件分发，不放入 Git。

This is a Debug self-signed testing build, not a production-signed stable release. Its original signature is preserved. The APK belongs in Release attachments, not Git history.

## 工程结构 / Project structure

```text
app/
  build.gradle.kts
  proguard-rules.pro
  src/main/AndroidManifest.xml
  src/main/java/com/druggradientchip/android/
    assistant/  forward/  reverse/  i18n/  ui/
  src/main/res/                 # layouts, icons, English / Chinese strings
  src/test/                    # selected unit tests and synthetic parser corpus
  src/androidTest/             # selected UI / localization test source
gradle/
  libs.versions.toml
  gradle-daemon-jvm.properties
  wrapper/                     # audited Wrapper JAR and properties
gradlew / gradlew.bat
settings.gradle.kts / build.gradle.kts / gradle.properties
local.properties.example
release-manifest.json
```

原 `android/` 已规范化为本仓库根目录，Gradle 相对路径保持有效；不再依赖原父目录中的 staging 脚本。正常 Gradle Wrapper JAR 经静态 ZIP 完整性检查后保留，它不是应用模型或构建缓存。

The original `android/` directory is now the repository root. Gradle relative paths are retained; the old parent-directory staging script is not part of this repository. The standard Gradle Wrapper JAR is retained after static archive inspection.

## 模型资产与公开范围 / Model assets and publication scope

**本仓库不包含任何 PTE/PT/PTH 权重、训练数据、实验 Golden 张量 JSON、模型 Bundle、研究报告、签名密钥或本地 SDK 配置。**

**No PTE/PT/PTH model files, training datasets, experimental Golden tensor JSON, model bundles, research reports, signing keys or local SDK configuration are included.**

模型加载器要求以下七个私有文件位于 `app/src/main/assets/`；它们必须由有权使用这些资产的人另行提供，本仓库不提供下载地址，也不自动从旧实验目录复制：

The loader expects seven private files in `app/src/main/assets/`. An authorized developer must supply them separately; this repository provides no model download or automatic import from research directories.

| Chips | Required private asset |
| --- | --- |
| 4 | `dynamic_forward_n4.pte` |
| 5 | `dynamic_forward_n5.pte` |
| 6 | `fixed_six_tensor_only.pte` |
| 7 | `dynamic_forward_n7.pte` |
| 8 | `dynamic_forward_n8.pte` |
| 9 | `dynamic_forward_n9.pte` |
| 10 | `dynamic_forward_n10.pte` |

缺少这些文件时，本源码不能独立提供功能完整的离线推理 APK；即使 Gradle 能打出不含模型的 APK，预测功能仍不可用。既有 Debug APK 已内置七个模型；发布 APK 会分发这些模型，不能把它们视为对 APK 接收者保密。

Without these assets, this repository cannot independently reproduce a fully functional offline-inference APK. A package built without models would not provide working prediction. The existing Debug APK embeds all seven models, which are distributable/extractable as part of that APK.

推理源码保留必要的特征编码、张量构造、归一化均值/标准差及搜索算法，也保留模型来源校验摘要。这些是方法及运行契约，不是训练样本或权重文件；公开源码将公开这些常量和方法，须由项目所有者在推送前确认。

The source retains the feature/tensor contract, preprocessing means and standard deviations, search algorithms and a source-checkpoint digest. These are required method/runtime parameters rather than training samples or model binaries. Publication exposes them and requires the owner's review before pushing.

为避免以 Kotlin 形式夹带实验数据，已移除含模型 Golden 向量的 helper、对应依赖测试和研究案例设备测试，共十个源文件。其余保留 18 个单元测试类及 2 个设备测试类。`assistant_golden_corpus.json` 是经单独白名单批准的 18 条双语解析器合成测试输入，只有 id/message/command 字段，不含模型输出张量，区别于七份实验 Golden JSON。两处表单案例默认值替换为通用合成输入；未改变推理或搜索算法。

Ten files containing model Golden vectors or depending on research fixtures were excluded. Eighteen unit-test classes and two instrumentation-test classes remain. The explicitly allowlisted `assistant_golden_corpus.json` contains 18 synthetic bilingual parsing cases with only id/message/command fields, not experimental model-output tensors. Two form defaults now use generic synthetic inputs; inference/search algorithms were not changed. The retained test suite is not the complete historical regression suite.

## 构建准备 / Build preparation

此说明列出静态配置要求；没有在迁移后的新电脑运行以下命令，也未安装或恢复开发环境。

These are documented requirements, not commands executed or validated on the migrated computer.

1. 准备 JDK 21、Android SDK 36.1、可用的 Android 构建工具及 Gradle/Maven 下载网络。Gradle Wrapper 固定 9.3.1，AGP 固定 9.1.1，daemon toolchain 为 21，Java 源/目标兼容级别为 11。项目未单独声明 Kotlin 插件版本。Prepare JDK 21, Android SDK 36.1 and compatible build tools. Gradle is pinned to 9.3.1, AGP to 9.1.1; daemon toolchain 21 and Java source/target 11 are configured. No separate Kotlin plugin version is declared.
2. 按 `local.properties.example` 创建仅本机使用的 `local.properties`，填入真实 SDK 路径，勿提交。Create an ignored local SDK configuration from the example.
3. 经授权另行放入七个兼容模型。ExecuTorch Android Maven 依赖固定为 `1.4.0`。Supply the seven authorized, compatible model assets separately. The ExecuTorch Android dependency is `1.4.0`.
4. 在本仓库根目录，未来可按需执行以下命令；Windows 使用 `gradlew.bat`。From this repository root, a prepared environment may run the commands below; use `gradlew.bat` on Windows.

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew lintDebug
# A suitable connected device and private models are needed:
./gradlew connectedDebugAndroidTest
```

没有配置生产签名。`app/proguard-rules.pro` 是新添加的空规则说明文件，用于补齐原 Gradle 引用；release minification 仍为关闭。

Production signing is not configured. `app/proguard-rules.pro` is a newly added comment-only file completing the existing Gradle reference; release minification remains disabled.

历史验证面向现代 arm64-v8a Android 设备；较旧设备可能不满足 ExecuTorch 原生运行时所需的 CPU 特性。这里不对未经测试的设备兼容性作保证。

Historical validation targeted modern arm64-v8a Android devices. Older devices may lack CPU features required by the native ExecuTorch runtime; untested device compatibility is not established.

## 验证状态与许可 / Validation status and licensing

此前的构建、安装、设备回归和语言切换测试属于旧电脑历史记录。本轮只做源码静态审查、独立 Git 历史准备和现有 APK 哈希核对，没有在新电脑重新构建或运行测试。源码整理及元数据更新不构成既有 APK 的重新构建证明。

Build/install/device/localization results belong to historical work on the old computer. This preparation performed static source review, new-history preparation and an existing-APK hash check only. It did not rebuild or rerun tests, and does not establish a new build provenance claim for the existing APK.

尚未为本项目选择开源许可证，未自动添加 MIT、Apache 等许可证。代码可见不等于已授予开源许可；第三方组件原有许可及声明仍适用。

No open-source license has been selected or added for this project. Public source visibility does not by itself grant an open-source license. Existing third-party component notices and licenses continue to apply.
