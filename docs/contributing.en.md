# Contributing

**English** · [简体中文](contributing.md)

## Collect device information first

You do not need to reverse-engineer an APK or write code first. A readable device report helps maintainers identify the interface information needed for an adapter.

1. Install [Android SDK Platform-Tools](https://developer.android.com/tools/adb) so `adb` is available in your terminal. Enable ADB debugging on the television and authorize the computer when prompted. Debugging settings vary by firmware; no other factory settings need to be changed.
2. Run these commands from the project root, replacing `TV_IP:PORT` with the television's address. For an existing USB connection, use its device ID from `adb devices`.

```sh
adb connect TV_IP:PORT
adb devices
python docs/collect-device.py --serial TV_IP:PORT
```

The collector requires Python 3.9 or newer and has no third-party dependencies. On Windows, `py -3` can replace `python`. If adb is not on PATH, add `--adb` followed by its executable path. Always give `--serial` the complete ID shown by `adb devices`.

Devices with wireless pairing only need `adb pair TV_IP:PAIR_PORT` first, using the code shown on the television. Then connect to the separate connection port on its wireless-debugging screen. The ports can differ; do not assume port 5555. If the device is `unauthorized`, allow the connection on the television before collecting.

The collector prints the report's local path under `.local/reports/`. It does not start apps, grant permissions, change settings, send light-control commands, pull files or upload reports. Running follow remains unchanged. Missing commands, timeouts and denied access are recorded rather than interpreted as absent hardware.

| Report section | Purpose |
| --- | --- |
| System model, Android version, build and ABI | Establish the platform and runtime environment; firmware information is diagnostic evidence, not an adapter allowlist |
| Window size, display-mode hints and memory | Distinguish UI scaling, display modes and performance conditions; these do not establish the vendor capture API's coordinate domain |
| Binder/HIDL interfaces, relevant system packages and framework filenames | Locate services and SDKs that might provide capture or light control |
| Numeric USB VID/PID, interface and endpoint fields | Identify connection candidates and the required transport |
| Running FrameLuma state and software timings | Inspect identification, access, capture, output and background state; skipped if the application is not running |

Third-party application lists and logs are excluded by default. USB output contains numeric descriptor fields rather than device serial numbers. Add `--logs` when troubleshooting to include recent FrameLuma warnings. Common address and credential formats are masked, but review the report before sharing. Numeric firmware versions are preserved in the selected build-property section.

Open a [device-support issue](https://github.com/rowanchen-com/frame-luma/issues/new?template=device.yml), attach the reviewed report, and describe the controller, length, wiring, DP/HDMI/internal input, and expected versus actual behavior. If Python is unavailable, start with these outputs and provide more details when requested:

```sh
adb -s DEVICE_ID shell getprop ro.product.model
adb -s DEVICE_ID shell getprop ro.build.version.release
adb -s DEVICE_ID shell getprop ro.build.display.id
adb -s DEVICE_ID shell wm size
adb -s DEVICE_ID shell wm density
adb -s DEVICE_ID shell service list
```

Replace `DEVICE_ID` with the full ADB ID. Do not publish the connection address or USB serial shown by `adb devices`. Ordinary ADB may be unable to read protected interfaces; root is not a prerequisite for providing a report.

<details>
<summary>Further analysis: extract a selected APK or framework and trace its calls</summary>

Use candidate services from the report to agree with maintainers which system package or SDK needs analysis. Extract only relevant files. The entire system or firmware is not needed.

Create `.local/` on the computer first; it already exists after running the collector.

```sh
adb -s DEVICE_ID shell pm path PACKAGE_NAME
adb -s DEVICE_ID pull APK_PATH .local/vendor-base.apk
jadx -d .local/vendor-analysis .local/vendor-base.apk
```

`PACKAGE_NAME` is the selected package. Use the actual path following `package:` in the first command's output as `APK_PATH`, without the `package:` prefix. Split packages return multiple paths; retain the splits needed for the code being analyzed. Framework files can be pulled from their actual directories in the report.

JADX is installed separately. JEB or IDA can help inspect code that Java decompilation does not recover clearly. Record actual service names, descriptors, methods or transactions, arguments, results, coordinate units, caller identity and lifecycle. A normal Android screenshot missing DP/HDMI does not prove that no vendor capture interface exists. The report collector does not invoke unknown Binder transactions or guess write parameters.

Keep extracted files local. A public PR contains your adapter implementation and relevant interface notes, not vendor APKs, firmware or proprietary libraries. Reverse-engineering evidence does not establish caller permission or correct screen capture by itself.

</details>

## Choose where to make a change

| Task | Directory | Existing example |
| --- | --- | --- |
| Add a television or monitor capture interface | `modules/displays/` | [Xiaomi](../modules/displays/xiaomi-freeguy/Adapter.java), [TCL](../modules/displays/tcl-c3apro/Adapter.java) |
| Add a light protocol or controller | `modules/lights/` | [Strip3](../modules/lights/philips-strip3/config.json) |
| Add serial, USB, HID or network transport | `modules/transports/` | [CH34x serial](../modules/transports/usb-serial/Adapter.java), [USB bulk](../modules/transports/usb/Adapter.java) |
| Add installation geometry and sample regions | `modules/layouts/` | [Configuration](../modules/layouts/config.json), [geometry](../modules/layouts/FourEdgeNine.java) |
| Change UI or remote interaction | `app/src/ui/` | [Controls](../app/src/ui/Controls.kt), [dialogs](../app/src/ui/DialogScreen.kt) |

Display capture, light protocols and transports are separate implementations. A new light normally changes only its light module. A new serial driver belongs to a transport module; shared runtime code does not branch on vendor or chip names. See [architecture](architecture.en.md) for interfaces and sessions.

## Add an adapter

1. Add a device- or transport-named folder in the corresponding directory. Use role names such as `Adapter.java` and `config.json` inside it, without repeated brand prefixes. Related implementations may share a file; adapters need not all have the same file count.
2. Use an existing configuration of the same kind as a starting point. Supply a unique `id`, `kind`, `entry_class`, translated `display_name` and parameters actually read by the implementation.
3. Implement the corresponding interface from [device-api](../modules/device-api/). Display and transport constructors receive `Adapter(ModuleConfig config)`; light constructors receive `Adapter(ModuleConfig config, TransportModule transport)`.
4. The build discovers configurations and generates the registry and assets. No hand-maintained device list or copied per-adapter Gradle script is needed. Declare additional library dependencies in `dependencies`.
5. Record observations in [compatibility](compatibility.en.md), distinguishing physical hardware observations, unverified inputs and inferred support.

Keep required Android entries in the adapter's `AndroidManifest.xml`. If reflection requires retained code, provide `consumer-rules.pro`; the common build loads it automatically. Omit these files when they are unnecessary.

### Configuration ownership

| Parameter | Location |
| --- | --- |
| Model hints, actual interface, coordinate domain and background mode | Display module's `config.json` |
| Edge band and partial-update policy | Display configuration's `capabilities.sampling_strategy` |
| Light identity, lengths, encoding and transitions | Light configuration's `detection` and `capabilities` |
| Connection implementation and USB/serial parameters | Light configuration's `transport`; `provider` references a transport ID |
| Installation regions, names and direction translations | `modules/layouts/config.json` and its geometry implementation |

Model and brand hints select among adapters rather than excluding compatible devices. `detection.priority` can resolve multiple available interfaces. Host caller identity and signature checks are separate code-loading requirements.

The current `usb-serial` implementation supports CH34x, while `usb-bulk` transfers directly through USB endpoints. Changing VID/PID or baud rate does not implement another chip's initialization or another light protocol. USB configuration needs a real selector—VID, PID or device-class fields—followed by a light-controller handshake. Arbitrary USB devices such as storage or mice must not become the default controller.

Length is identified from the controller. Brightness, speed, direction and installation settings update while running. Sample colors remain in memory; user settings are persisted only when changed. Background mode and capture permission are independent: system binding does not automatically grant vendor capture access.

## Build and submit

Contributors can build an unsigned APK without the maintainer's private key:

```sh
./gradlew :app:exportReleaseApk -x :app:lintVitalRelease -PunsignedBuild=true
```

Use `gradlew.bat` on Windows. The output is `.local/dist/frame-luma-unsigned.apk`; configure your own key using the [signing guide](releases.en.md) before installation. Normal builds use the existing generated icon and background resources.

Describe the behavior before and after your change, affected modules and checks actually performed. Adapter PRs identify the system model, Android version, controller and input source. UI PRs should consider remote focus, returning from dialogs and text display. Contributions without hardware are welcome when their verification scope is stated. Documentation-only changes need no build or device run.

Keep changes focused. Do not incidentally change versions, release identity or unrelated adapters. Commit source and configuration; keep APKs, caches and private keys local. Preserve required notices when copying or adapting third-party code. Public source excludes extracted vendor packages, firmware and proprietary libraries.

## Report an issue

Include the system model and Android version, controller and length, input source, reproduction steps, and expected versus actual behavior. Provide relevant errors and state rather than entire logs. Remove serial numbers, MAC addresses, accounts, IP addresses and passwords. A full firmware upload is unnecessary.
