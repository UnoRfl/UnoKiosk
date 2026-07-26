# Uno Kiosk

Barebones Android WebView kiosk. Loads one URL, locks the phone into it, hidden PIN exit. No backend. All settings on-device.

## What you get

- **First launch** → setup screen: paste URL, set a PIN (4+ digits), tap Save → Launch Kiosk.
- **Kiosk mode** → fullscreen WebView. Home/recents/back are neutralized when device owner is enabled.
- **Hidden exit** → tap the top-left corner 5 times within 3 seconds → PIN prompt → back to settings.
- **Boot** → app auto-launches after the phone restarts.
- **Cache** → cleared from settings.

Two operating modes:

| Mode | How | What it does |
|---|---|---|
| **Soft kiosk** | Just install the APK | Fullscreen webview, back button swallowed, but user can still swipe home. Fine for casual use. |
| **Hard kiosk** | Run the ADB command below once | Phone is fully locked into the app. Home, recents, notifications — all disabled. This is what a real POS kiosk needs. |

## Getting the APK

You have two options.

### Option A — Let GitHub build it for you (recommended, no PC needed to build)

1. Create a new empty repo on GitHub (e.g. `UnoRfl/UnoKiosk`).
2. Upload every file in this project into that repo (drag-and-drop in the GitHub web UI works).
3. Go to the **Actions** tab — the workflow runs automatically.
4. When it finishes (~3 min), scroll to **Releases** in the sidebar → the APK is attached to the latest release.
5. Download the APK on your phone → tap to install (allow "install from unknown sources").

### Option B — Build locally with Android Studio

1. Open the project folder in Android Studio.
2. Let it sync Gradle.
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`.
4. APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Enabling HARD kiosk mode (device owner)

Do this **once** per phone. Requires a PC and a USB cable.

**Prep the phone:**
1. Factory reset the phone.
2. During setup, **skip signing in with a Google Account**. Skip Wi-Fi if it asks you to sign in.
3. Finish setup, then enable Developer Options (tap Build Number 7 times in Settings → About).
4. Turn on **USB debugging**.
5. Sideload the APK (any way — email it to yourself, USB copy, Dropbox, whatever).
6. Install the APK. Do **not** open it yet.

**On your PC (needs [ADB installed](https://developer.android.com/tools/releases/platform-tools)):**
```bash
adb devices                                                       # confirm the phone shows up
adb shell dpm set-device-owner com.uno.kiosk/.AdminReceiver
```

You should see: `Success: Device owner set to package ComponentInfo{com.uno.kiosk/com.uno.kiosk.AdminReceiver}`.

That's it. Open the app, set your URL + PIN, tap **Launch Kiosk**. The phone is now locked into it.

## Emergency access — you forgot the PIN

Plug the phone into your PC with USB debugging on:

```bash
adb shell pm clear com.uno.kiosk
```

This wipes the app's saved URL and PIN. Next launch → setup screen. Nothing else on the phone is touched.

## Fully unlocking the phone (undoing device owner)

```bash
adb shell dpm remove-active-admin com.uno.kiosk/.AdminReceiver
```

Then you can uninstall the app normally.

## Package / customization

- Package name: `com.uno.kiosk` (in `app/build.gradle` under `applicationId`, and in the manifest namespace). Change it if you want, but then update every `adb shell` command above with the new name.
- App name: `res/values/strings.xml` → `app_name`.
- Icon: `res/mipmap-*/ic_launcher.png`.

## What this does not do (yet)

- No file uploads through the webview (would need a `WebChromeClient.onShowFileChooser` override — easy to add later).
- No camera/microphone permissions surfaced (also an easy add if the site needs them).
- No auto-update of the APK. Rebuild on GitHub Actions and re-sideload.

Ping me if you need any of those.
