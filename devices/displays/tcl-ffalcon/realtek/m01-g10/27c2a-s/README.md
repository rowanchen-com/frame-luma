# TCL 27C2A-S

首期显示设备模块，适配M01/G10、Realtek RTD2885P、V8-M001T01-LF1V064、Project ID32。系统型号C3APRO被多款设备共用，不单独作为匹配依据。

`src/`包含本机专属识别与像素调用，以及TCL TTVS宿主入口。`module.json`声明条件和能力；`config/`保存面板参数。该机型的取色实现不放进公共核心。

当前输入为逐点RGB，真实面板3840×2160。原型DP链路已验证；本仓库新版本的上机验证及HDMI1／HDMI2／内置系统／HDR矩阵仍待完成。没有批量截图或高速全帧能力的声明。

- [链路及验证](docs/validation.md)
- [调用示意图](docs/images/capture-flow.svg)
- [本机模块测试](tests/com/xiaomi/mitv/light/devices/tcl/model27c2as/TclModuleTest.java)
