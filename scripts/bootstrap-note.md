# Bootstrap / 构建探测

本仓库是 **Android 客户端**，不包含 Go 运行时。构建前需要本机具备：

1. **JDK 17+**（`java -version`）
2. **Android SDK**（API 35 / build-tools；Android Studio 安装最省事）
3. **Gradle Wrapper**（`gradle/wrapper/gradle-wrapper.jar`）

## 探测环境

```bash
bash scripts/check-env.sh
```

## 生成 wrapper（有系统 Gradle 时）

```bash
gradle wrapper --gradle-version 8.11.1
```

若无系统 Gradle，用 Android Studio 打开本目录，它会补齐 wrapper 并下载 SDK。

## 编译

```bash
cp local.properties.example local.properties
# 编辑 sdk.dir=...

./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## 真机联调要点

- 电脑：`lh serve` 监听 `0.0.0.0:9090`，配置 `server.api_keys`
- 手机与电脑同网，Settings 填 `http://<LAN-IP>:9090` + API Key
- 模拟器宿主机：`http://10.0.2.2:9090`
- 只走 HTTP(S)+WS，不走未加固 gRPC
