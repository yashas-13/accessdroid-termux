#!/data/data/com.termux/files/usr/bin/bash
#==============================================================================
# install.sh — One-shot installer for AccessDroid + amctl
# Builds (or re-uses) AccessDroid APK, installs it, opens Accessibility
# settings, enables termux-am socket, and verifies everything works.
#
# Usage: bash install.sh [--skip-build]
#==============================================================================

set -euo pipefail

PROJ_ROOT="/data/data/com.termux/files/home/bin/accessdroid"
APK="$PROJ_ROOT/build/AccessDroid.apk"
SECRET="termux-accessdroid-2025"

# Colors
R='\033[0;31m'; G='\033[0;32m'; Y='\033[1;33m'; B='\033[0;34m'; N='\033[0m'

log()   { echo -e "${G}[+]${N} $*"; }
warn()  { echo -e "${Y}[!]${N} $*"; }
err()   { echo -e "${R}[x]${N} $*"; }
info()  { echo -e "${B}[i]${N} $*"; }

# ── check for --skip-build ──
SKIP_BUILD=false
if [[ "${1:-}" == "--skip-build" ]]; then
    SKIP_BUILD=true
    log "Skipping build (using existing APK at $APK)"
fi

# ── 1. Build APK if needed ──
if [[ "$SKIP_BUILD" == "false" ]]; then
    log "Building AccessDroid APK..."
    cd "$PROJ_ROOT"
    ./build.sh 2>&1 | tail -10
    [[ -f "$APK" ]] || { err "Build failed: $APK not found"; exit 1; }
    log "APK ready: $APK"
else
    [[ -f "$APK" ]] || { err "APK not found at $APK (run without --skip-build)"; exit 1; }
fi

# ── 2. Install APK ──
log "Installing APK (opens Android package installer)..."
termux-open "$APK"

info "Waiting for install to complete..."
info "  → Tap 'Install' on the dialog that opened"
info "  → Tap 'Open' or 'Done' when finished"
sleep 2

# Give user time to interact
for i in {10..1}; do
    echo -ne "\r${Y}Waiting for install... $i sec ${N}"
    sleep 1
done
echo ""

# ── 3. Open Accessibility settings ──
log "Opening Accessibility settings (enable AccessDroid)..."
termux-open-url "android.settings.ACCESSIBILITY_SETTINGS" 2>/dev/null || \
am start -n com.android.settings/.Settings 2>/dev/null || \
am start -a android.settings.ACCESSIBILITY_SETTINGS 2>/dev/null || \
warn "Could not auto-open Accessibility settings"

echo ""
info "Please manually enable AccessDroid:"
echo "  Settings → Accessibility → Downloaded services"
echo "  → AccessDroid → toggle ON → confirm"

# Wait for user
read -p $'\nPress ENTER when AccessDroid is ENABLED in Accessibility settings... '

# ── 4. Enable termux-am socket ──
log "Checking termux-am socket setting..."
TERMUX_PROPS="$HOME/.termux/termux.properties"
mkdir -p "$(dirname "$TERMUX_PROPS")"

if grep -q 'run-termux-am-socket-server=false' "$TERMUX_PROPS" 2>/dev/null; then
    log "Enabling termux-am socket in termux.properties..."
    sed -i 's/run-termux-am-socket-server=false/run-termux-am-socket-server=true/' "$TERMUX_PROPS"
elif ! grep -q 'run-termux-am-socket-server=true' "$TERMUX_PROPS" 2>/dev/null; then
    echo "run-termux-am-socket-server=true" >> "$TERMUX_PROPS"
    log "Added termux-am socket setting to termux.properties"
else
    log "termux-am socket already enabled in termux.properties"
fi

termux-reload-settings 2>/dev/null || true

# ── 5. Verify installation ──
log "Verifying termux-am works..."
if termux-am broadcast -p com.accessdroid.termux -a com.accessdroid.termux.ACTION_BACK --es secret "$SECRET" 2>/dev/null; then
    log "✓ termux-am can reach AccessDroid service"
else
    warn "termux-am broadcast failed — Accessibility service may not be running"
    warn "  Try: killall com.termux && reopen Termux"
fi

# ── 6. Setup PATH for amctl ──
if [[ ":$PATH:" != *":$HOME/bin:"* ]]; then
    log "Adding ~/bin to PATH in .bashrc..."
    echo 'export PATH="$HOME/bin:$PATH"' >> ~/.bashrc
fi
export PATH="$HOME/bin:$PATH"

# ── 7. Final test ──
log "Running quick amctl test..."
if amctl list 2>/dev/null | head -1 | grep -q '.'; then
    log "✓ amctl is working"
    amctl list 2>/dev/null | head -3
else
    warn "amctl list returned empty (need Wireless Debugging or re-enable am socket)"
fi

echo ""
echo -e "${G}=== INSTALL COMPLETE ===${N}"
echo "Try these commands:"
echo "  amctl tap 540 960          # tap center screen"
echo "  amctl type \"hello world\"   # type into focused field"
echo "  amctl click \"Login\"        # click button by text"
echo "  amctl scroll down          # scroll down"
echo "  amctl launch chrome        # launch an app"
echo "  amctl help                 # all commands"
echo ""
echo "Secret (if needed): $SECRET"
echo ""
warn "If termux-am still fails: killall com.termux && reopen Termux"