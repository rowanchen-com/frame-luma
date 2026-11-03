# Compatibility

**English** · [简体中文](compatibility.md)

## Installation and platform

| Item | Configuration or status |
| --- | --- |
| Minimum Android version | Android 9, API 28 |
| Compile and target version | Android 17, API 37 |
| APK | One universal APK, without ABI splits |
| ARM32 / ARM64 | DEX plus both armeabi-v7a and arm64-v8a implementations of the Compose path library |
| Release identity | Package and certificate are configured in `gradle/config.json` |
| Screen capture | Selected through available interfaces; system models are selection hints |

The Kotlin compiler version does not determine the minimum Android version. The project uses current Kotlin, AGP and SDK configuration, while `minSdk` and API-level conditions determine compatibility with older systems.

## Display adapters and observations

| Display adapter | Hardware observations | Not fully established |
| --- | --- | --- |
| TCL C3APRO | DP follow, a 2 m strip and short screen-off recovery | Complete input coverage, HDR and long standby |
| Xiaomi MiTV-MFTR0 (freeguy) | Internal content, a 5 m strip, background capture and authorized binding recovery | HDMI, HDR and long standby |

These observations do not establish support for every device from the same brand. Model names help select a configuration; the capture interface and light protocol determine whether a device can be integrated.

The TCL profile uses C3APRO as a model hint and captures through the device's TTVS host and factory pixel interface. Discovery first checks that the host exists; opening a capture session checks the factory interface and caller identity.

The current TCL configuration supplies a 3840×2160 sampling coordinate domain. Its factory implementation does not yet discover the domain for other panels automatically. Finding the interface does not establish that these coordinates work at every resolution. Other panels need their actual coordinate domain. This is not a model or firmware-version allowlist.

New platforms need an adapter and supporting observations. Vendor package names, SDK calls and host checks belong to that adapter; shared runtime code uses interfaces. Display and controller discovery run automatically, and length is parsed from a real handshake rather than entered manually. Other models and platforms still require corresponding implementations and device observations.

Compose includes `libandroidx.graphics.path.so`, packaged for ARM32 and ARM64. Additional JNI libraries need both architectures and appropriate page-size compatibility.

Toolchain references: [Kotlin releases](https://kotlinlang.org/docs/releases.html), [AGP 9.5 compatibility](https://developer.android.com/build/releases/agp-9-5-0-release-notes), [Android 17 SDK](https://developer.android.com/about/versions/17/setup-sdk).

## Background operation and authorization

TCL uses a host session and foreground service, without the Xiaomi recovery grants below. The selected mode is shown under **About and open source → Background and recovery**.

The Xiaomi/Redmi profile uses MiTV-MFTR0 as a model hint and queries TvService capabilities and coordinates. Background operation uses the user-enabled **FrameLuma background follow** accessibility binding. Capture retains the normal application UID. It requires neither root nor a vendor diagnostic proxy, and does not modify the system foreground-service allowlist.

Accessibility binding can be enabled in system settings. For automatic recovery, run these two ADB commands in PowerShell, bash or zsh. From Windows cmd, run `powershell` first. Keep the single quotes and backslash so neither shell interprets `$` as a variable.

```sh
adb shell pm grant com.rowanchen.frameluma android.permission.WRITE_SECURE_SETTINGS
adb shell 'cmd notification allow_listener com.rowanchen.frameluma/frameluma.runtime.Recovery\$Listener'
```

The accessibility component is `com.rowanchen.frameluma/frameluma.runtime.BackgroundService`. Automatic recovery follows the user's follow and recovery switches, preserving other accessibility entries. Notification access supplies a system binding; notification contents are not read. The local sync account wakes only this application, uploads no data and does not change the master sync setting. Firmware may restrict notification binding or sync scheduling, so these mechanisms do not establish identical recovery behavior on every model.

## Lights and sampling

Display and light modules are independent. The table describes observations for strip3.

| Strip3 length | Control units | Coverage |
| --- | --- | --- |
| 2 m | 20 | Physical follow and short screen-off recovery observed on TCL |
| 3 m | 30 | Offline protocol, encoding and layout coverage; no physical controller observation |
| 4 m | 40 | Offline protocol, encoding and layout coverage; no physical controller observation |
| 5 m | 50 | Physical follow on Xiaomi; more than one minute of background capture and USB output observed with system binding |

TCL uses TTVS and factory pixel capture, seeding nine regions before sending updates after every three samples. Xiaomi uses TvService and submits all nine regions together. Both sample near the screen edges. Output transitions run independently, and only new samples confirm short black frames.

The strip3 implementation connects through CH340 and reads the controller's actual length. The 2 m and 5 m paths have hardware observations; 3 m and 4 m retain protocol implementations.
