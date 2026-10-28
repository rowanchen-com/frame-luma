# FrameLuma · 映光

面向Android电视与智能显示器的模块化随影灯光项目。机型专属取色适配与灯带／控制盒适配独立组合，发布为一个APK。

**当前是源码重构的alpha版本。** 首期模块为TCL 27C2A-S（M01/G10、V064、Project32）和米家`philips.light.strip3`的2米灯带。已同签名覆盖安装并在线加载，DP正常画面随影得到现场确认。流畅档九区分批输出，实测软件输出更新间隔约90ms；完整九区轮换仍约270ms。四源色块对照、物理贴装与HDR验证仍待完成，其它品牌和长度尚未声明支持。

## 构建

环境：JDK17、Android SDK34／Build Tools34.0.0；Gradle Wrapper会按锁定版本初始化。源码使用Kotlin界面、Java核心／机型模块和JSON配置，不依赖原厂APK底座或随包的厂商SO。

```sh
python scripts/check_modules.py
./gradlew test :app:assembleRelease -PunsignedBuild=true
```

这是无需私钥的未签名贡献者构建。Windows也可运行`./build.ps1 unsigned`。

已有固定签名的本地开发环境运行：

```powershell
./build.ps1 apk
python scripts/verify_apk.py
```

签名通过环境变量`FRAME_LUMA_KEYSTORE`、`FRAME_LUMA_KEYSTORE_PASSWORD`或被忽略的`local.properties`配置，alias／公钥指纹以[签名策略](signing/policy.json)为准。缺密钥会停止签名，不生成替代密钥。保留`com.xiaomi.mitv.light`和递增versionCode，支持后续同签名覆盖；构建不连接或修改电视。

## 目录与开发

| 目录 | 职责 |
| --- | --- |
| app | Kotlin原生电视界面、轻量Canvas预览 |
| core | 不依赖Android的采样契约、布局和调度 |
| device-api | Android设备模块接口 |
| runtime | 模块选择、前台服务、受控IPC、后台引擎 |
| devices/displays | 品牌组／芯片厂商／平台／机型独立模块 |
| devices/lights | 灯光厂商／设备或控制盒型号独立模块 |
| docs | 架构、扩展规范和验证说明；配图放images |

新增模块放入`devices/`，提供`module.json`、代码、配置、测试和说明。构建自动发现并生成注册表；新代码需重新构建APK后才能启用。接口和配置有版本号，未知／冲突的机型不会被当成已支持。

- [架构与资源生命周期](docs/architecture.md)
- [添加机型或灯光模块](docs/contributing.md)
- [首次重构验证状态](docs/validation.md)
- [延迟、速度档与测量方法](docs/performance.md)
- [代码及设计来源](THIRD_PARTY_NOTICES.md)

## 许可

原创部分采用[Apache-2.0](LICENSE)。引用／移植内容保留原许可及署名，详见第三方说明。签名私钥、设备账号／标识、固件和提取的厂商文件不属于公开源码内容。
