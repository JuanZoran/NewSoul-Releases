# New Soul

Soul 的 LSPosed API 102 插件，包含聊天工具、隐私控制、广场筛选、无水印下载、主页插眼和 AI 回复。
原公开版与内部 AI 版已合并，统一包名为 `io.github.juanzoran.newsoul`。

## 安装

在 [Releases](https://github.com/JuanZoran/NewSoul-Releases/releases/latest) 下载正式 APK，安装后在 LSPosed 中启用并勾选 Soul，重启 Soul。
长按 Soul 底部导航图标打开插件设置。AI 回复支持 DeepSeek、小米 MiMo 和自定义 OpenAI 兼容接口，密钥在插件内自行配置。

公开版可覆盖升级。旧内部版用户安装统一版后，在 LSPosed 中停用旧内部模块、启用统一版并重启 Soul。
插件设置保存在 Soul 数据中：统一版保留公开版设置，首次使用时补入旧内部版中尚未设置的项目及 AI 密钥；冲突以公开版为准。请保留 Soul 数据。

## 构建与验证

依赖 JDK 17、Python 3、curl、unzip、Android SDK platform 35 / build-tools 35.0.0。
SDK 脚本的 `ANDROID_HOME` 默认 `/opt/android-sdk`；Gradle 需设置该环境变量或本机 `local.properties`。源码和资源统一位于 `app/src/main/`。

```sh
bash tests/run-regression-tests.sh
# 自备 PKCS12 签名文件与密码文件
export NEWSOUL_SIGNING_KEYSTORE=/path/to/release.p12
export NEWSOUL_SIGNING_PASSWORD_FILE=/path/to/password
export NEWSOUL_SIGNING_ALIAS=newsoul-release
bash build-sdk.sh
```

产物：`build/sdk/public/new-soul-<versionName>.apk` 及同名 ZIP。
Gradle 使用 `:app:assembleRelease`；调试构建使用本机标准 Android debug 签名。
正式包的映射按 APK 哈希保存在 `build/protection-evidence/`，由构建者私下保存。
自行签名的 APK 需与手机上已有 APK 的签名一致才能覆盖安装。

## 下载与反馈

蓝奏云：https://wwbnj.lanzoux.com/b007utki2d ，提取码 `3gin`。
反馈请私聊 [@JuanZoranNewSoulReleaseBot](https://t.me/JuanZoranNewSoulReleaseBot)，并附日志与截图。

## 许可证

New Soul 采用 GPL-3.0-only，全文见 [LICENSE](LICENSE)。
第三方组件的许可证和来源见 [NOTICE](app/src/main/resources/newsoul/third-party/NOTICE.txt)。
