# Build and release

**English** · [简体中文](releases.md)

## Local build

See the [README](../README.en.md#build) for JDK, Android SDK and Build Tools requirements. All modules read SDK and Java targets from `gradle.properties`.

```sh
./gradlew :app:exportReleaseApk -x :app:lintVitalRelease -PunsignedBuild=true
```

Use `gradlew.bat` on Windows. The contributor build exports `.local/dist/frame-luma-unsigned.apk`; sign it before installing it on a device.

Release signing uses an existing key, supplied through `FRAME_LUMA_KEYSTORE` and `FRAME_LUMA_KEYSTORE_PASSWORD`, or through the Git-ignored `local.properties`. `gradle/config.json` defines the alias and certificate fingerprint. Once configured, omit `-PunsignedBuild=true` to export `.local/dist/frame-luma.apk` and `build-info.json`.

Forks can use their own signing key. Update the alias and certificate fingerprint in `gradle/config.json`, and set the application ID there if changing the package. The original fingerprint cannot be combined with another signing key: the host would reject the APK. This check identifies the application code being loaded; it does not restrict television models, firmware or developer computers. Keep private keys and passwords out of the repository.

The build directly creates the APK without additional test suites or artifact-hash checks. Signing uses the configured existing key.

## GitHub Actions

The workflow is named `frame-luma`. It runs only when a Release is published or when manually dispatched.

Set the repository's `FRAME_LUMA_KEYSTORE_BASE64` and `FRAME_LUMA_KEYSTORE_PASSWORD` Actions secrets using the existing release key. The runner restores the key temporarily and removes it when the job ends. Missing signing secrets cause the build to fail.

- A published Release builds its event's commit.
- A manual run builds the selected branch. `release_tag` selects an existing published release whose assets will be replaced; an empty value uses Latest Release.
- The download is named `frame-luma-<version>.apk`. Actions and Releases provide the APK without a separate build-information JSON asset.
- Superseded FrameLuma APKs and old build-information assets are removed only after the new APK uploads successfully. Immutable releases cannot have their assets replaced.

The Actions artifact is saved before release replacement. A failed replacement does not delete that saved artifact. Temporary Actions artifacts follow the repository retention policy; Release assets are unaffected by that expiry. The Actions run records the manual build's commit, which may differ from the release tag's original commit.

## Versioning

Version names have three numeric components. Version changes require the project owner's explicit approval. Routine fixes, manual builds and asset replacements do not automatically increment the version.
