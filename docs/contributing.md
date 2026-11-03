# 贡献指南

**简体中文** · [English](contributing.en.md)

## 先收集设备信息

不需要先会反编译或写代码。一份可读的设备报告就能帮助维护者判断下一步需要哪些接口资料。

1. 电脑安装[Android SDK Platform-Tools](https://developer.android.com/tools/adb)，让`adb`在命令行可用。电视打开ADB调试，首次连接时在电视上允许调试。ADB入口因系统而异，不需要修改其它工程选项。
2. 在项目根目录运行下列命令，把`TV_IP:PORT`换成电视实际地址。已有USB连接时，直接使用`adb devices`列出的设备ID。

```sh
adb connect TV_IP:PORT
adb devices
python docs/collect-device.py --serial TV_IP:PORT
```

采集器需要Python 3.9或更新版本，无第三方依赖。Windows也可使用`py -3`代替`python`；ADB不在PATH时追加`--adb`和adb可执行文件的路径。`--serial`始终填写`adb devices`中的完整设备ID。

只有无线调试配对入口的设备，先执行`adb pair TV_IP:PAIR_PORT`并输入电视显示的配对码，再连接无线调试页面的连接端口。配对端口与连接端口可能不同，不统一假定为5555。`unauthorized`时在电视上允许连接，再运行采集器。

报告保存在电脑的`.local/reports/`，路径会打印出来。脚本不启动App、不授予权限、不改变系统设置、不发灯控命令，也不自动提取文件或上传报告；运行中的随影保持原状。某条命令缺失、超时或被拒绝时会记录原因并继续，不将失败当作没有硬件。

| 报告内容 | 用途 |
| --- | --- |
| 系统型号、Android版本、构建与ABI | 确认平台与运行环境；固件信息是诊断依据，不是适配白名单 |
| Android窗口尺寸、显示模式线索与内存 | 区分界面缩放、物理显示与性能条件；不能直接当作厂商取色坐标域 |
| Binder／HIDL接口、相关系统包与framework文件名 | 找到可能提供取色或灯控能力的服务与SDK |
| USB的VID／PID、接口与端点数字字段 | 判断连接方式，帮助定位对应传输实现 |
| 已运行FrameLuma的状态与软件耗时 | 判断识别、权限、采集、输出和后台状态；未运行时跳过 |

默认不收集第三方应用清单或日志，USB只保存数字描述字段，不保存设备序列号。需要排错时可追加`--logs`，读取近期FrameLuma告警；脚本会遮盖常见地址和凭据格式，分享前仍请检查文件。基本构建信息中的四段数字版本会保留。

打开[设备适配Issue](https://github.com/rowanchen-com/frame-luma/issues/new?template=device.yml)，附上已检查的报告，并写明控制盒名称与长度、实际接线、当前DP／HDMI／内置画面，以及预期和实际结果。无法使用Python时，可先提供下列输出，再按维护者要求补充：

```sh
adb -s DEVICE_ID shell getprop ro.product.model
adb -s DEVICE_ID shell getprop ro.build.version.release
adb -s DEVICE_ID shell getprop ro.build.display.id
adb -s DEVICE_ID shell wm size
adb -s DEVICE_ID shell wm density
adb -s DEVICE_ID shell service list
```

将`DEVICE_ID`替换为完整设备ID。不要公开`adb devices`里的连接地址或USB序列号。普通ADB无法读到某些接口是正常的权限边界，不需要为了提供报告尝试root。

<details>
<summary>进一步分析：提取指定APK或framework，再定位调用链</summary>

先根据报告中的候选服务，由维护者确认需要分析的系统包或SDK。只提取相关文件，不需要上传整个系统或固件。在电脑创建`.local/`目录后运行下面的命令；运行过采集器时该目录已存在。

```sh
adb -s DEVICE_ID shell pm path PACKAGE_NAME
adb -s DEVICE_ID pull APK_PATH .local/vendor-base.apk
jadx -d .local/vendor-analysis .local/vendor-base.apk
```

`PACKAGE_NAME`是确认后的包名，`APK_PATH`取自上一条返回的`package:`后面的实际路径，不要包含`package:`前缀。多APK包会返回多个路径，按所分析代码需要保留对应split；framework文件同样从报告的实际目录中读取。`jadx`需另行安装，无法还原的Java逻辑可用JEB或IDA继续分析。

重点记录实际的服务名、接口描述符、方法或事务、参数与返回值、坐标单位、调用身份及生命周期。普通Android截图不包含某个DP／HDMI画面时，不能据此断定厂商没有其它取色链路。只读报告不调用未知Binder事务，也不猜测写入参数。

提取的文件保留在本地用于分析，不提交厂商APK、固件或专有库。公开PR提交自己的适配实现及必要的接口说明；逆向线索本身不代表已有调用权限或已经验证画面正确。

</details>

## 选择修改位置

| 要做的事 | 位置 | 现有示例 |
| --- | --- | --- |
| 接入电视或显示器的取色接口 | `modules/displays/` | [小米](../modules/displays/xiaomi-freeguy/Adapter.java)、[TCL](../modules/displays/tcl-c3apro/Adapter.java) |
| 接入新的灯光协议或控制盒 | `modules/lights/` | [strip3](../modules/lights/philips-strip3/config.json) |
| 接入其它串口、USB、HID或网络传输 | `modules/transports/` | [CH34x串口](../modules/transports/usb-serial/Adapter.java)、[USB bulk](../modules/transports/usb/Adapter.java) |
| 增加贴装布局和采样区域 | `modules/layouts/` | [布局配置](../modules/layouts/config.json)、[几何实现](../modules/layouts/FourEdgeNine.java) |
| 修改界面与遥控交互 | `app/src/ui/` | [控件](../app/src/ui/Controls.kt)、[弹窗](../app/src/ui/DialogScreen.kt) |

电视取色、灯光协议与传输分别实现。新增灯带通常只修改灯光模块；新增串口驱动放在传输模块，共享运行层不按品牌或芯片名称分支。[架构说明](architecture.md)介绍接口与会话关系。

## 新增适配

1. 在对应目录新建以设备或传输命名的文件夹。内部使用`Adapter.java`、`config.json`等职责名称，不再重复品牌前缀。相关实现可以合并，不要求每个适配具有相同文件数量。
2. 参考同种类的现有配置，填写独立`id`、`kind`、`entry_class`、中英文`display_name`及实现实际使用的参数。`id`在模块之间保持唯一。
3. 实现对应接口。显示和传输入口接收`Adapter(ModuleConfig config)`；灯光入口接收`Adapter(ModuleConfig config, TransportModule transport)`。接口位于[device-api](../modules/device-api/)。
4. 构建会扫描配置，生成注册表并打包资源；无需手动维护设备名单或为每个适配复制Gradle脚本。增加库依赖时在配置的`dependencies`声明。
5. 在[兼容范围](compatibility.md)记录实际完成的验证。未接实物、未验证信号源和推测支持应明确区分。

Android组件放在适配自己的`AndroidManifest.xml`；反射入口需要保留时添加标准名称`consumer-rules.pro`，共用构建自动加载。没有这些需求时无需创建文件。

### 配置归属

| 参数 | 放在哪里 |
| --- | --- |
| 型号选择提示、实际接口、坐标域、后台模式 | 显示模块的`config.json` |
| 采样带与分批策略 | 显示配置的`capabilities.sampling_strategy` |
| 灯光身份、长度、编码与平滑参数 | 灯光配置的`detection`、`capabilities` |
| 连接实现与USB／串口参数 | 灯光配置的`transport`；`provider`引用传输模块ID |
| 贴装区域、名称与方向翻译 | `modules/layouts/config.json`及对应几何实现 |

型号、品牌用于选择适配，不作为排除其它兼容设备的白名单；多个可用接口可通过`detection.priority`确定优先级。宿主调用身份与签名校验是独立的代码加载约定。

当前`usb-serial`实现CH34x，`usb-bulk`直接读写USB端点。修改VID／PID或波特率不能替代新芯片的初始化或新灯光协议。USB配置至少提供一种真实设备选择条件：VID、PID或设备类信息，再由灯光握手确认身份；不要把U盘、鼠标等任意USB设备作为默认控制盒。

灯带长度由控制盒识别。亮度、速度、方向和贴装配置在线更新；采样颜色保留在内存中，用户设置变化时才持久化。后台模式与取色权限独立，接入系统绑定不会自动获得厂商取色权限。

## 构建与提交

贡献者可先构建未签名APK，无需维护者的私钥：

```sh
./gradlew :app:exportReleaseApk -x :app:lintVitalRelease -PunsignedBuild=true
```

Windows使用`gradlew.bat`。输出为`.local/dist/frame-luma-unsigned.apk`；安装前按[签名说明](releases.md)配置自己的密钥。普通构建不重新生成图标或背景资源。

PR说明修改前后的行为、涉及模块和实际执行的验证。适配PR记录系统型号、Android版本、控制盒与信号源；界面PR关注遥控焦点、返回恢复及文字显示。没有实机也可提交，但应注明验证范围。纯文档修改无需构建或操作设备。

保持改动集中；不顺带修改版本号、发布身份或无关适配。源码与配置提交到仓库，APK、缓存和私钥留在本地。复制或移植第三方代码时保留必要许可声明；公开源码不包含提取的厂商APK、固件或专有库。

## 反馈问题

Issue提供：系统型号与Android版本、控制盒型号和长度、当前信号源、复现步骤及预期／实际结果。日志截取相关错误与状态即可，去除SN、MAC、账号、IP及密码等个人信息；不需要上传整份固件。
