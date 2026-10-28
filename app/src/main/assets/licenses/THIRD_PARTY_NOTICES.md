# 来源与许可

新工程不包含官方幻彩随影APK底座、提取资源或`libgetmaincolor.so`。从本地原型迁入的部分是本项目自己编写的Java实现；TCL SDK通过设备原有系统路径加载，不在仓库或APK重新分发。

| 来源 | 固定参考版本 | 使用方式／许可 |
| --- | --- | --- |
| [tv-ambient](https://github.com/Mihaivich/tv-ambient) | cc0fcdd3abbf48622dfd6fc1b13fe0a1ebf9d3ed | 原生电视交互、九区细色框、低分辨率多团柔光设计参考；本项目重新实现预建调色板、顺时针轨迹及配置驱动边框；MIT |
| [screen_strip_sync](https://github.com/losewight/screen_strip_sync) | f4e263b7ad7cf68e65df57c8efbf71fbd58ece40 | PC协议格式与约束资料参考；未引入Windows/C++实现；Apache-2.0 |
| [Hyperion](https://github.com/hyperion-project/hyperion.ng) | 9a73e822637ed9e6c5c1fa1bc84e9adc527dc604 | 采集／处理／输出分层与配置设计参考；MIT |
| [LumiSync](https://github.com/Minlor/LumiSync) | 92e1fcec79c215b2752a693b3e60f40caa8b1559 | 驱动能力、区域映射和生命周期契约参考；MIT |
| [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) | 4cb9e82cdcfa095eb2fe0b0bea9605f50bd8e2f2 | CH340初始化核对依据，未整库引入；主库MIT，个别文件另有Apache声明 |
| Kotlin标准库 | Gradle锁定的Kotlin版本 | 编译时引入的运行库；Apache-2.0 |
| [CC Switch](https://github.com/farion1231/cc-switch) | 6d6b7162f122ac0ce1356a14688c8c4f712db3c8 | 中性色层次、细边框分组、开关及分段选项的设计参考；使用Android原生实现，未引入React／Radix源码；MIT |

各参考项目的许可文本及适用通知放在`licenses/`。上表区分设计／协议参考与实际代码依赖，后续移植源文件需进一步记录文件与修改范围，保留其原始头部；不能只写“灵感来自”就省略许可。
