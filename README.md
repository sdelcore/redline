# Redline

Android GitHub PR diff viewer. Top/bottom split (old over new), synced
vertical & horizontal scroll, swipe between old / split / new, tap
any code line to comment, submit a full review (approve / request
changes / comment).

Built with Jetpack Compose. Sample data baked in; login is real GitHub
OAuth (device flow) so the token persists, but the screens render
fixtures rather than live API calls.

## Requirements

- NixOS or Nix with flakes enabled
- `direnv` + `nix-direnv` (optional but recommended)

## Setup

1. Register a GitHub OAuth App at
   <https://github.com/settings/developers>, enable **Device Flow** in
   the app settings.
2. Drop the Client ID into `local.properties` (gitignored):

   ```
   GITHUB_CLIENT_ID=Ov23liXXXXXXXXXX
   ```

3. Enter the dev shell. With direnv:

   ```sh
   direnv allow
   ```

   Without:

   ```sh
   nix develop
   ```

## Build

```sh
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk

./gradlew installDebug   # if a device or emulator is attached
```

## Layout

- `flake.nix` — Android SDK 35, JDK 17, Gradle, Kotlin pinned via nixpkgs
- `app/src/main/java/com/redline/viewer/`
  - `MainActivity.kt`, `RedlineApp.kt`, `AppViewModel.kt`
  - `data/` — models + sample data + GitHub device-flow auth + encrypted token store
  - `ui/theme/` — colors, fonts (JetBrains Mono + Inter variable), Material 3 dark
  - `ui/login/`, `ui/prlist/`, `ui/files/`, `ui/diff/`, `ui/review/`
  - `ui/components/` — buttons, check icon, GitHub mark
