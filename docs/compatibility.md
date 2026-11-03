# 兼容范围

**简体中文** · [English](compatibility.en.md)

## 安装与平台

| 项目 | 配置／状态 |
| --- | --- |
| 最低安装版本 | Android9，API28 |
| 编译与目标版本 | Android17，API37 |
| APK | 单个通用APK，不按ABI拆包 |
| ARM32／ARM64 | 同一APK包含DEX及Compose路径库的armeabi-v7a／arm64-v8a两种实现 |
| 发布身份 | 包名与证书由gradle/config.json定义 |
| 画面采集 | 按实际接口发现，系统型号仅作为选择提示 |

Kotlin编译器版本不等于安卓最低版本。使用新Kotlin、AGP与SDK，同时由minSdk和代码中的API条件控制旧系统兼容。

## 显示适配与验证

| 显示适配 | 已有实物使用记录 | 尚未完整确认 |
| --- | --- | --- |
| TCL C3APRO | DP画面随影、2米灯带、短时息屏恢复 | 各输入的完整覆盖、HDR与长期待机 |
| 小米 MiTV-MFTR0（freeguy） | 内置画面、5米灯带、后台取色及授权后的绑定恢复 | HDMI、HDR与长期待机 |

这些是已有观察范围，不代表同品牌的所有设备均兼容。型号用于选择配置，实际取色接口和灯光协议决定能否接入。

TCL以C3APRO为型号提示，使用当前设备的TTVS宿主和factory像素接口取色。发现阶段先确认宿主存在，factory接口及调用身份在打开采集会话时确认。

当前TCL配置提供3840×2160的采样坐标域，factory取色实现尚未自动查询其它面板的坐标域。接口可发现不代表该坐标可直接用于所有分辨率；适配其它面板时应提供其真实坐标域。这不是型号或固件版本的白名单。

新平台需要提交独立模块和验证依据。厂商包名、SDK调用与宿主校验放在机型模块，共享运行层只使用接口。显示接口和控制盒由运行时自动发现；长度始终在真实握手后解析，不提供手动段数入口。其它型号和平台仍需对应实现及实测。

Compose引入AndroidX路径库`libandroidx.graphics.path.so`，ARM32／ARM64成对打包。后续新增JNI同样需要双架构实现及页大小兼容。

工具链依据：[Kotlin发布记录](https://kotlinlang.org/docs/releases.html)、[AGP9.5兼容表](https://developer.android.com/build/releases/agp-9-5-0-release-notes)、[Android17 SDK](https://developer.android.com/about/versions/17/setup-sdk)。

## 后台运行与授权

TCL使用宿主会话与前台服务，不使用下面的小米恢复授权。两种方式均可在“关于与开源 → 后台与恢复”查看。

小米／Redmi以MiTV-MFTR0为型号提示，通过TvService能力和坐标查询接入。后台使用用户开启的“FrameLuma 后台随影”无障碍系统绑定，采集仍运行在普通应用UID。该模式不要求root或厂商诊断代理，不修改系统前台服务白名单。

可以在系统无障碍页面手动开启。启用自动恢复时，可在PowerShell、bash或zsh中执行以下两条ADB命令；Windows的cmd可先运行`powershell`。组件名包含`$`，命令中的单引号和反斜杠均需保留，避免电脑或电视端的shell将其解释为变量。

```sh
adb shell pm grant com.rowanchen.frameluma android.permission.WRITE_SECURE_SETTINGS
adb shell 'cmd notification allow_listener com.rowanchen.frameluma/frameluma.runtime.Recovery\$Listener'
```

无障碍入口是`com.rowanchen.frameluma/frameluma.runtime.BackgroundService`。自动恢复按用户的随影开关和恢复设置运行，保留其它无障碍服务。通知访问只用于系统绑定，不读取通知内容；本地同步账户只唤醒本应用，不上传数据，不修改系统全局同步设置。通知绑定与同步调度可能受电视固件限制，不代表所有型号都能以同样方式恢复。

## 灯光与采样

显示适配与灯光模块独立；下表描述strip3灯带的使用记录。

| strip3长度 | 控制单元 | 验证范围 |
| --- | --- | --- |
| 2米 | 20 | 此前TCL上已确认实体跟随与短暂息屏恢复 |
| 3米 | 30 | 协议、编码及布局离线覆盖；未接实物 |
| 4米 | 40 | 协议、编码及布局离线覆盖；未接实物 |
| 5米 | 50 | 小米已确认实体跟随；系统绑定模式已完成超过一分钟的后台取色与USB输出观察 |

TCL适配使用TTVS宿主和factory像素接口，首轮采齐九区，运行中每采三处更新；小米适配使用TvService，九区采齐后更新。两者采样点均靠近屏幕边缘，灯光输出独立平滑，短黑帧只由新采样确认。strip3通过CH340连接并从控制盒读取实际长度；2米和5米有实体使用记录，3米和4米保留协议实现。
