# 构建与发布

**简体中文** · [English](releases.en.md)

## 本地构建

JDK、Android SDK与Build Tools要求见README；各模块的SDK与Java目标统一读取`gradle.properties`。

```sh
./gradlew :app:exportReleaseApk -x :app:lintVitalRelease -PunsignedBuild=true
```

Windows用`gradlew.bat`。贡献者未签名包输出到`.local/dist/frame-luma-unsigned.apk`，安装到设备前需要签名。

正式签名使用现有密钥，通过`FRAME_LUMA_KEYSTORE`和`FRAME_LUMA_KEYSTORE_PASSWORD`，或被Git忽略的`local.properties`提供。alias和证书指纹由`gradle/config.json`定义。配置后去掉`-PunsignedBuild=true`，得到`.local/dist/frame-luma.apk`和`build-info.json`。

Fork自用时可以使用自己的密钥，在`gradle/config.json`中同步设置自己的alias和证书指纹；修改包名时也在该文件设置。原项目的证书指纹不能与另一把签名密钥混用，否则宿主会拒绝加载。该校验用于确认加载的是本应用代码，不是电视型号、固件或开发者电脑的限制。私钥和密码不提交到仓库。

构建直接生成APK，不运行附加测试或产物哈希校验；签名使用已有密钥。

## GitHub Actions

工作流名称为`frame-luma`，只在发布Release或手动运行时触发。

仓库Actions Secrets需要设置现有密钥的`FRAME_LUMA_KEYSTORE_BASE64`和`FRAME_LUMA_KEYSTORE_PASSWORD`。密钥在运行器临时恢复，任务结束清除；缺失时构建失败。

- 发布Release：构建该事件对应提交。
- 手动运行：构建选定分支；`release_tag`指定要替换附件的已发布版本，留空使用Latest Release。
- 下载文件为`frame-luma-<版本号>.apk`。Actions使用直接APK产物，保留30天。
- 所有新附件上传成功后，才移除同一Release中过时的FrameLuma APK。不可变Release不能替换附件。

Actions下载产物先于Release替换保存；替换失败不会删除已保存的构建产物。手动构建的提交记录在构建信息中，可能与原发布标签不同。

## 版本

版本名只用三段数字，未经项目所有者明确允许不得修改；日常修复、手动构建和附件替换不自动加版本。
