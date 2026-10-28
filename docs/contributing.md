# 添加设备模块

一个PR优先覆盖一个明确型号或控制盒，目录以其厂商／平台归类。模块是编译时扩展，不在用户电视上执行外部源码目录。

## 提交内容

1. 在`devices/displays/品牌组/芯片厂商/平台/型号`或`devices/lights/厂商/型号`建立目录，参考已有完整模块。
2. 提供`module.json`（schema_version=1、api_version=1、唯一id、入口类、kind、匹配规则与能力），及Android库`build.gradle.kts`。
3. `src/`实现`DisplayModule`或`LightModule`。显示设备明确坐标面和采样类型；灯光设备明确连接、完整帧约束、会话开始结束和可重复关闭。`LightOutput.identity()`仅返回握手校验后的控制盒型号／固件；USB串口芯片匹配不能代替控制盒识别。
4. `config/`存放该型号默认值；机型资料、接口依据、支持版本和实机步骤放`README.md`及`docs/validation.md`，图片放`docs/images/`。
5. `tests/`验证识别误匹配、布局／协议和边界；提交脱敏的必要测试数据。仅有服务名称、状态0或编译成功，不属于实际画面取色证据。

核心不应新增按品牌／型号堆叠的大分支；新模块由构建自动纳入。共享驱动／算法可以显式复用，但入口和能力声明仍归具体型号。规则冲突要修正，不能用顺序碰巧选中。

## 本地验证

```sh
python scripts/check_modules.py
./gradlew test :app:assembleRelease -PunsignedBuild=true
```

测试代码只用于开发／CI，不随安装后自动对设备运行。实机测试单独执行；按DP、HDMI1、HDMI2、内置系统分别记录已验证／待验证／不支持，并记录关闭、后台、待机、USB重连、更新与卸载。

## 来源与私密材料

复制或移植代码时保留版权、许可证、适用NOTICE和修改说明；README致谢不替代许可文件。来源表记录项目URL、固定版本和使用范围。厂商APK、提取JAR／SO、私钥、账号和未经脱敏的设备数据留在本地；模块应调用设备自身已安装的系统接口。

既有发布身份固定；贡献者无需也不会获得发布私钥，可以构建未签名APK。不要用自动生成密钥或卸载旧App的方式处理同包更新问题。
