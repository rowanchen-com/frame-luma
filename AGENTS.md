# FrameLuma development

- Keep applicationId `com.xiaomi.mitv.light`. Local signed builds use the existing external key; verify SHA-256 `1d94625a49044ae282919a401a99c9e83697a22e166df8cc337a871bf23ec4b2`. Preserve versionCode >=108. Never generate a replacement key, publish key material, or uninstall to work around an update failure.
- Device-specific code, match rules, capabilities, defaults, tests and docs belong in that model's `devices/` directory. New modules are discovered at build time, compiled into one APK and selected using verified identity. Shared core stays independent of vendor Android SDKs.
- Public source builds contain our source and documented licensed components, not extracted vendor APKs, firmware, proprietary assets or native libraries. Record upstream use in THIRD_PARTY_NOTICES.md.
- Match M01/G10/C3APRO as platform identifiers; exact TCL 27C2A-S selection additionally requires verified Project ID32. Unknown identities remain unsupported. A53/A55 are CPU cores, not SoC vendors.
- Persist user settings only when changed. Keep frame colors/status in memory, diagnostics bounded, UI updates lifecycle-scoped. Stop/force-stop/uninstall must release sampling and USB. Leaving settings keeps explicitly enabled follow running.
- Build and offline checks do not operate a device. Physical positioning, HDR and each DP/HDMI/internal input require real validation before capability claims. See docs/architecture.md and docs/contributing.md when extending modules.
