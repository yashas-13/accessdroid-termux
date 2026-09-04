# AccessDroid + amctl — Direct App Control from Termux

Control apps on your Android device **directly from Termux** (no wireless ADB,
no USB cable, no root) using an AccessibilityService + `termux-am`.

> 📺 **Newer/complete: for on-screen input (clicks, scrolls, typing into
> fields) Android REQUIRES an AccessibilityService.** `termux-am` alone can
> manage apps (start/stop/grant-perms) but **cannot** inject gestures — see the
> table below.

## What it is
- **AccessDroid** — a minimal Android app exposing an
  `AccessibilityService` that accepts broadcast commands from Termux and turns
  them into real taps / swipes / scrolls / long-presses / key events /
  type-into-field actions.
- **amctl** — a Termux CLI (`/data/data/com.termux/files/home/bin/amctl`)
  that sends those commands and wraps `termux-am` for app lifecycle tasks.

## Capabilities

| Need | Method | Command |
|---|---|---|
| Launch an app | `termux-am` (ActivityManager) | `amctl launch <name\|pkg>` |
| Force-stop / clear data | `termux-am pm` | `amctl stop\|clear <pkg>` |
| Grant/revoke permission | `termux-am pm` | `amctl grant\|revoke <pkg> <perm>` |
| List apps | `termux-am pm` | `amctl list [q]` / `amctl find <name>` |
| **Tap at X,Y** | AccessDroid gesture | `amctl tap 540 960` |
| **Long-press** | AccessDroid gesture | `amctl longpress 540 960` |
| **Swipe / drag** | AccessDroid gesture | `amctl swipe x1 y1 x2 y2` |
| **Scroll up/down** | AccessDroid gesture | `amctl scroll up\|down` |
| **Type into focused field** | AccessDroid node action | `amctl type "hello world"` |
| **Click UI element by text** | AccessDroid node action | `amctl click "Login"` |
| **Click element by resource-id** | AccessDroid node action | `amctl clickid com.app:id/btn` |
| **Scroll-to text** | AccessDroid node action | `amctl scrollto "Sign in"` |
| **Back / Home / Recents** | global action | `amctl back\|home\|recents` |
| **Screenshot** (live monitor) | `takeScreenshot` API | `amctl screenshot` |
| **Inspect UI tree** | dump nodes to log | `amctl tree` then `log ... AccessDroid` |

## Compatibility
- Minimum Android: **7.0** (API 24 / `minSdkVersion 24`)
- Target Android: **16** (API 36 / `targetSdkVersion 36`)
- CPU architecture: **arm64-v8a** (aarch64). No native code — pure Java/Kotlin
  DEX, so it runs on any ABI.
- Screen sizes: **phone + tablet** (small/normal/large/**xlarge**) — verified
  installable on POCO tablets (e.g. `24074PCD2I`, Android 16).
- No root, no USB cable, no wireless debugging.
- AccessibilityService must be **manually enabled** in system Settings (one-time).
- For app-lifecycle commands (`launch`, `stop`, `perms`…), enable the
  **termux-am socket** in *Termux → Settings → Termux:API*.

## Install (one-step)
```sh
bash <(curl -sL https://raw.githubusercontent.com/yashas-13/accessdroid-termux/master/accessdroid/install.sh)
```
The script builds (or reuses) the APK, installs it via `termux-open`, opens
Accessibility settings, enables the termux-am socket, and runs a quick test.

## Install & Enable (one-time)

### 1. Build the APK (inside Termux)
```sh
cd ~/bin/accessdroid
./build.sh
# → produces ~/bin/accessdroid/build/AccessDroid.apk
```

### 2. Install the APK
```sh
# Opens the Android package installer
termux-open ~/bin/accessdroid/build/AccessDroid.apk
```
(Or `adb install -r …` if you have ADB.)

### 3. Enable the AccessibilityService
- **Settings → Accessibility → Downloaded services → AccessDroid → ON**
- (Required once per boot.)

### 4. Enable termux-am socket (for app lifecycle commands only)
- **Termux app → ⋮ → Settings → Termux:API → “Allow am command socket” → ON**
- Restart Termux.

## Verify

```sh
amctl help          # show all commands
amctl list          # list 3rd-party apps
amctl launch chrome # launch an app
amctl tap 540 960   # tap center screen
amctl type "hi"     # type into a focused field
amctl screenshot    # capture screen
```

## Live screen monitoring
`amctl screenshot` triggers `AccessibilityService.takeScreenshot()` (API 33+).
For **continuous** live monitoring, run alongside a foreground loop:

```sh
# monitor.sh — capture 1 frame/sec for N seconds
for i in $(seq 1 10); do
    amctl screenshot
    sleep 1
done
```
(Frames are produced asynchronously inside the service; watch the result via
`logcat -s AccessDroid`.)

## Security
- Service only accepts commands carrying a shared secret
  (`termux-accessdroid-2025`) — embedded in `amctl`.
- Broadcasts are explicit (`-p com.accessdroid.termux`), not broadcast to all
  apps.

## Onboarding & Status (Settings UI)
Since v1.0.3, AccessDroid ships an **on-device onboarding app** (a launcher
activity). Tap the **AccessDroid icon** in your app drawer to open
`SettingsActivity`, which provides:

- **Status dashboard** — live indicators for:
  - AccessibilityService (ENABLED / DISABLED)
  - Termux `am` socket (ENABLED / DISABLED)
  - VLM config (CONFIGURED / NOT CONFIGURED)
- **Run Onboarding Setup** button — jumps to
  `Settings → Accessibility` so you can flip the service toggle.
- **VLM Provider** fields — save an API endpoint, key, and model for the VLM
  agent (persisted in app prefs).

> The UI is built programmatically (no XML layouts) so it compiles with the
> minimal `aapt2`+`javac` chain in `build.sh` — no Android Studio needed.

## VLM Vision Agent (v1.0.3+)
AccessDroid can now act as a **visual (VLM) agent** — the AI reads the screen
like a human and clicks at the exact pixels, using the AccessibilityService
(instead of ADB) for both silent capture and input injection.

**Pipeline (mirrors OmniParser / Pix2Struct / SeeClick style loops):**

```
[Screen Snapshot] → [takeScreenshot API] → [VLM inference]
       ▲                                            │
       │                                            ▼
[Wait for UI Render] ← [dispatchGesture(X,Y)] ← [parse {action, coordinates}]
```

### How it works
1. **Silent screen capture** — `AccessibilityService.takeScreenshot(Display.DEFAULT_DISPLAY…)`
   (API 33+) captures the screen with **no permission popup**.
2. **VLM prompt** — the screenshot (base64) is sent to your configured
   OpenAI-compatible chat-completions endpoint with a system prompt forcing
   JSON output.
3. **Coordinate mapping** — the model returns normalized `[0..1000]`
   coordinates, which `vlmClick()` maps back to physical pixels and injects
   via `dispatchGesture()`.

### Configure the VLM provider
1. Open the **AccessDroid** app → **VLM Provider**.
2. Enter:
   - **API Endpoint** — e.g. `https://api.openai.com/v1/chat/completions`
   - **API Key** — your key (e.g. `sk-...`)
   - **Model** — a vision model, e.g. `gpt-4o`, `qwen2.5-vl-72b`.
3. Tap **Save Config**.

> The same config is readable from the app (via `SharedPreferences`) and can
> be driven headlessly from Termux once you add a launch action to `amctl`.

### Drive from the VLM Agent screen
Tap the **AccessDroid VLM Agent** tile (or launch `.VlmAgentActivity`):

| Button | Action |
|---|---|
| **Screenshot** | Capture + show capture info (no VLM call) |
| **Run 1 step** | Capture → VLM → dispatch action |
| **Auto-loop** | Run 5 sequential VLM agent steps (~2.5s apart) |

Enter a task like `Click the search bar and type 'weather'` and tap **Run 1
step**. The model returns `{action, coordinates, text, reason}` and AccessDroid
performs the gesture. Auto-loop chains steps for multi-step automation.

### VLM action schema
```json
{
  "action": "CLICK|TYPE|SCROLL|BACK|HOME",
  "coordinates": [500, 800],        // normalized 0..1000
  "text": "...",                    // only for TYPE
  "reason": "why this action"
}
```

> ⚠️ VLM calls require a network connection and a valid API key. For an
> on-device SLM, point the endpoint at a local server (e.g. Ollama)
> exposing a compatible `/v1/chat/completions` route.

## Releases
Latest APK is the **[latest release](https://github.com/yashas-13/accessdroid-termux/releases/latest)** (`v1.0.3`).

```
# SHA256  3caf05e5373c68542d58520d181fba2b7bde0c79cce54975e29aab41bf838829
```
```sh
curl -L https://github.com/yashas-13/accessdroid-termux/releases/latest/download/AccessDroid.apk -o AccessDroid.apk
```

## Troubleshooting

### AccessDroid not visible in Accessibility settings
1. **Uninstall first** if you previously installed an older version:
   ```sh
   am uninstall com.accessdroid.termux
   ```
2. Install the **latest APK** (v1.0.2+ uses `android.permission.BIND_ACCESSIBILITY_SERVICE`):
   ```sh
   termux-open ~/bin/accessdroid/build/AccessDroid.apk
   ```
3. Open Accessibility settings:
   ```sh
   am start -a android.settings.ACCESSIBILITY_SETTINGS
   ```
4. Look under **Downloaded services** (or **Installed services**).
   - On Android 11+ it may be under **Installed apps** → **Downloaded apps**.
   - If you don't see it, force-stop Termux and try again:
     ```sh
     am force-stop com.termux && am start -n com.termux/.app.TermuxActivity
     ```

### `amctl tap/swipe` returns "Is AccessDroid's accessibility service enabled?"
- The AccessibilityService is **not running**. Re-enable:
  ```sh
  am start -a android.settings.ACCESSIBILITY_SETTINGS
  ```
  Toggle **AccessDroid** OFF → wait 2s → toggle ON again.

### `amctl` commands fail with "Could not connect to socket"
- The termux-am socket is disabled. Enable it:
  ```sh
  sed -i 's/run-termux-am-socket-server=false/run-termux-am-socket-server=true/' ~/.termux/termux.properties
  termux-reload-settings
  ```
- If still not working, force-stop Termux and reopen.

### Service crashes or stops after app kill
- Android can kill AccessibilityServices under memory pressure.
- Enable **Ignore battery optimizations** for Termux:
  ```sh
  am start -a android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS
  ```
  Find Termux → toggle **Allow**.

### Verify service is running
```sh
settings get secure enabled_accessibility_services | grep accessdroid
```
If empty, the service is not enabled.

## Files
| File | Purpose |
|---|---|
| `~/bin/accessdroid/AccessibilityServiceImpl.java` | AccessibilityService source |
| `~/bin/accessdroid/SettingsActivity.java` | Onboarding + settings + status UI |
| `~/bin/accessdroid/VlmAgentActivity.java` | Visual (VLM) agent loop |
| `~/bin/accessdroid/build.sh` | builds signed APK from Termux |
| `~/bin/accessdroid/build/AccessDroid.apk` | compiled APK |
| `~/bin/amctl` | Termux CLI control script (on `$PATH`) |
