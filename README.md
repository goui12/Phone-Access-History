# Phone Access History

Personal Android app built for Cameron on November 8, 2026. It records phone unlock timestamps locally and presents history by date. This repository preserves the recovered original version 1.0.0 source; no app behavior was changed while preparing the repository.

See [README.txt](README.txt) for installation, service behavior, data storage, limitations, and build instructions.

## Project

- Application ID: `com.cameron.phoneaccess`
- Version: `1.0.0` (version code `1`)
- Android minimum: 8.0 / API 26
- Compile and target SDK: API 36
- Native Java; no third-party app libraries or network permission

## Build and signing

Install JDK 17 or newer, Python 3, Android SDK platform 36, and Android build-tools 36.0.0. Run from Linux, macOS, or WSL:

```bash
ANDROID_SDK_ROOT=/absolute/path/to/android-sdk bash build.sh
```

Output is `dist/Phone_Access_History.apk`. Build output and `.signing/` are excluded from Git. The local Goggins copy retains the original `.signing/release.p12` and `.signing/password.txt` for compatible updates; these are not stored in GitHub.

**Restore the original signing files before building an update for the installed app.** The original build script generates a new identity if `.signing/release.p12` is absent. Such a newly signed package cannot update the original app in place. Uninstalling the original app would delete its stored history.

## Recovered artifact

The latest saved source archive contains the same APK as the separately saved APK artifact and the existing APK on Goggins’ desktop:

- APK size: 33,417 bytes
- APK SHA-256: `52a37670953730f5703d13d710794a6044e406d59ba252f7b204469686b1a020`
- Signing certificate SHA-256: `7edb6a4070b4d51f01b9f95b0daac8d8b745c2a4a167b26368d2bf745b66998b`

APK signature verification and a separate clean source build passed during recovery. The recovered original APK is retained unchanged in the local `dist/` folder. The repository preparation did not test phone behavior or change the existing desktop APK.
