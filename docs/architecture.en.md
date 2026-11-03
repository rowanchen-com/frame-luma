# Architecture

**English** · [简体中文](architecture.md)

The UI, screen capture, light protocol, transport and installation geometry communicate through interfaces. Display adapters and light implementations share one runtime engine; the common core does not depend on vendor SDKs.

## Source layout

- `app/`: Compose UI, system fonts, focus interaction and video backgrounds.
- `modules/core/` and `device-api/`: shared data, interfaces and sampling rules.
- `modules/runtime/`: discovery, sessions, state, resource cleanup and settings persistence.
- `modules/displays/` and `lights/`: device-specific configuration and implementations.
- `modules/transports/` and `layouts/`: byte transport, screen regions and physical mapping.

Each adapter has one `config.json` containing its ID, entry class, discovery hints, capabilities and parameters. Display, light and transport modules are registered separately. Configuration is loaded from the APK at runtime. Light constructors receive configuration and a transport implementation; protocol code does not instantiate a particular USB or serial driver.

Folders identify devices; source files are named by responsibility, and related implementations can share a file. Common adapter build rules live in the root `build.gradle.kts`, which generates the registry and module assets.

## Discovery and sessions

Discovery selects display adapters using their available interfaces. System model, brand and manufacturer provide selection hints, without excluding other compatible devices. Names, layout labels and direction translations come from configuration; the UI has no device-ID lookup table.

A light-controller handshake establishes identity and physical length before output and installation mapping are created. An unidentified controller receives no default driver or invented length. Opening the UI may query controller identity and length without enabling follow or changing power. There is no manual device-combination selector.

Discovery selects candidates. Opening a session then checks the capture interface, caller identity and controller handshake. Discovery alone does not establish compatibility for every resolution, input or HDR mode.

The light configuration's `transport.provider` selects the transport implementation. `usb-serial` performs CH34x initialization; `usb-bulk` transfers directly through USB endpoints without setting a baud rate. They share USB discovery and permission handling. Other chips, HID or network connections can add implementations under `modules/transports/`. Sharing a transport does not make light protocols interchangeable.

TCL uses the TTVS host and factory pixel interface. Xiaomi uses the capabilities and coordinate domain returned by TvService. Both paths run the same FollowEngine. Host calls are checked against the actual UID and application signature; the shared engine has no vendor-brand branches.

Brightness, speed, direction and calibration for the same physical length update while running. Connection or physical-length changes release the old session before creating another. Calibration is stored per display adapter, light type and unit count. Per-frame colors and status remain in memory; settings are persisted only when changed.

Stopping, screen off, disconnection and process termination release capture and USB resources. Leaving the UI keeps explicitly enabled follow active. Each display adapter chooses its background lifecycle: foreground service by default, or user-enabled accessibility binding for the Xiaomi profile. Both entries share FollowSession and one capture engine, handing over the heartbeat when ownership changes. Binding does not change the application UID, read UI content or modify vendor allowlists.

The Xiaomi profile's `recovery` policy combines accessibility-disconnection events, notification-listener binding, a local sync account and bounded rebind attempts. Restoring its own accessibility authorization requires the user's ADB grant of `WRITE_SECURE_SETTINGS`; notification access is authorized separately. Recovery edits only this application's accessibility entry, preserves other services and leaves the master sync setting unchanged. Turning follow off removes the local recovery account and stops notification recovery binding. Notification contents are not read. These entries request recovery of the same session, rather than creating additional engines or a permanent polling watchdog.

**About and open source → Background and recovery** shows the mechanism selected by the display profile. Accessibility mode provides recovery switches; host and foreground-service modes explain their lifecycle without showing irrelevant grants. Key filtering is off by default. When enabled, it handles configured playback keys only while FrameLuma is in the foreground. Force-stop recovery is also off by default. Whether the system permits restart depends on the firmware. Background binding and capture permission remain separate concerns. The TCL profile does not enable these recovery grants or accounts.

## Sampling and light transitions

Both display profiles currently use an edge band with a depth of 4% of the corresponding screen dimension. Each region's point is centered in the band, about 2% inward from its edge. Sampling policy belongs to the display configuration; the common engine does not contain device-specific pixel coordinates.

TCL seeds all nine regions before its first output, then submits updates after every three samples during steady operation. Xiaomi submits all nine regions together.

A separate output thread uses fixed-size buffers and retains only the latest target, without queuing old frames. Each physical unit has its own transition clock. Updating another region does not restart an existing transition. Sample-freshness flags follow the layout mapping into output; retained regions in a partial update do not count as another black-frame confirmation.

The light module configures transition duration, black-frame hold, dark threshold, minimum send interval and idle keepalive interval. The current strip3 profile uses a 70 ms transition, 120 ms short-black hold and 50 ms minimum send interval. Static content reduces output frequency. Persistent black still fades to darkness.

Capture and output are measured separately. An interpolated output roughly every 50 ms does not mean a new screen sample is available every 50 ms. TCL's nine serial queries still take about 250–270 ms. `capture_elapsed_ms` and `actual_period_ms` describe capture; `output_interval_ms` and `output_frames` describe actual USB output.

## UI and background

Controls and dialogs share one Compose tree. Labels and values align on text baselines. Focus, pressing and selection are separate states, and closing a dialog restores the original focus.

An original silent video loop provides the background. The display profile selects its compositing path; shared performance tiers control UI cost only. Low-memory devices use a lightweight video, while text and controls retain the system window resolution. Playback failure falls back to a GPU color field.

Homepage sampling follows actual playback progress, compositing the same background and UI into a small target for covered areas. This UI source does not replace real capture of other apps, HDMI or DP. Players, GPU resources and listeners follow the page lifecycle. Opening an in-app dialog does not recreate the player.

## Host updates

The TCL entry discovers and loads the currently installed APK. A complete HostRuntime session runs in an isolated class loader. On update, it stops the old engine, releases USB and listeners, clears the old control object, then lets the application request a new session.

The class loader's parent is the actual TTVS host, not a chain of older APK loaders. Initialization runs in the background; the entry does not wait for resource release on the main thread.
