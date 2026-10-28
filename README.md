# 引力弹弓 · 星驿快递（GravitySling）

PICO Spatial SDK 空间游戏。捏住探测器向身前拉并松手发射，借天体引力弯曲航线，把包裹送往空间站。

版本 1.5 / versionCode 6，包名 `com.vrgames.gravitysling`。

## 内容与操作

- 月港、赤色航线、深空邮路三章；15 个主线关卡和 3 个挑战关。
- 手势拉弓、摆放引力球、飞行中点火；立体选关星图、三星记录和盲射挑战。
- 20 张航天图鉴，支持轨迹辅助、自动存档与星空/房间透视背景切换。
- 房间模式只提供透视背景，不识别家具或环境平面。

## 构建

工程根目录即本仓库根。需要 Android SDK 35、JDK 17 或兼容版本；Gradle Wrapper 8.13、AGP 8.13.2、Kotlin 2.1.20、PICO Spatial SDK 6.1.9。Java/Kotlin 字节码目标为 11。

```sh
./gradlew assembleRelease --no-daemon --max-workers=1
./gradlew testDebugUnitTest --no-daemon --max-workers=1
```

Windows 使用 `gradlew.bat`。可通过 `ANDROID_HOME` 指定本地 Android SDK；本仓库不包含本机路径、签名密钥或访问令牌。唯一发布产物为 `app/build/outputs/apk/release/app-release-unsigned.apk`，由 PICO VibeLab 构建服务签名。

源码、运行模型、纹理、音频和图标均随仓库提供，构建不需要 Node.js 或在线生成素材。应用的调试测试接口仅在调试版启用。

## 素材与许可

代码采用 Apache License 2.0。游戏美术来源与背景音乐说明见 `THIRD_PARTY_NOTICES.md`。本作品使用虚构载具和公司名，与任何航天机构无关。
