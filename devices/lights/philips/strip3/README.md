# 米家追光氛围灯带 · philips.light.strip3

独立灯光型号模块。使用Android USB Host连接CH340（1a86:7523），验证型号、固件2.1.8_0039和实际长度；首期仅启用已验证2m／20控制单元。

`src/`负责USB初始化、握手、协议组帧和会话释放。`config/lengths/`保存长度规格，`config/layouts/`保存初始安装映射。目录名philips来自设备协议型号，不把商品名等同于Philips Hue或其它协议。

控制单元是协议寻址粒度，不保证等于每颗实体LED。四边、底部中央先向左的默认分配为3/2/2/2/2/2/2/2/3；用户尚未完成贴装与物理校准。

- [协议与验证](docs/validation.md)
- [初始四边布局](docs/images/layout.svg)
- [独立协议测试](tests/com/xiaomi/mitv/light/devices/philips/strip3/Strip3FramesTest.java)
