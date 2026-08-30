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

## Files
| File | Purpose |
|---|---|
| `~/bin/accessdroid/AccessibilityServiceImpl.java` | AccessibilityService source |
| `~/bin/accessdroid/build.sh` | builds signed APK from Termux |
| `~/bin/accessdroid/build/AccessDroid.apk` | compiled APK |
| `~/bin/amctl` | Termux CLI control script (on `$PATH`) |
