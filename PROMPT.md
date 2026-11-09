# Automated Android TV Transformation Prompt

> **For Beginners:** Copy and paste the prompt below directly into your AI coding assistant (Claude Code, Cursor, Devin, Antigravity, Roo Code, Windsurf, etc.). Your assistant will handle the entire setup for you step-by-step.

---

### Copy Everything Below This Line:

```markdown
I want to transform my Android TV / Google TV into an ultra-clean, ad-free experience using this repository (https://github.com/Fardin7798/Spider-Tv-Launcher).

Please act as an autonomous Android TV deployment engineer and guide me through the setup step-by-step.

> **Prerequisite:** Confirm that my computer and TV are connected to the same Wi-Fi network.

### Step 1: Connect to My TV
1. If you don't know my TV's IP address:
   - Check if an ADB device is already connected (`adb devices`).
   - Try local mDNS scan (`adb mdns services`).
   - If not found, ask me for my TV's IP address and tell me where to find it on my TV:
     "On your TV: Go to Settings ⚙️ → Network & Internet → Click your connected Wi-Fi → Look for 'IP address' (e.g., 192.168.1.XX)".
2. Ensure my TV has USB Debugging turned ON:
   - "Settings → Device Preferences → About → Press OK on 'Build' 7 times → Back → Developer options → Turn ON 'USB debugging' (and 'Network debugging' if visible)".
3. Connect over Wi-Fi ADB (`adb connect <TV_IP>:5555`).
4. Remind me to look at my TV screen to accept the "Allow USB debugging?" dialog and check "Always allow from this computer".

### Step 2: Automatic Baseline Safety Snapshot (Zero-Risk)
Before touching anything or making any changes, always capture a full system state snapshot directly on my computer:
1. Create a timestamped backup folder: `backups/<TV_IP>_<TIMESTAMP>/`
2. Run these non-destructive read commands:
   - `adb -s <TV> shell getprop ro.product.manufacturer > metadata.env` (append model, SDK, release, and current Home)
   - `adb -s <TV> shell pm list packages -e | sed 's/package://' | sort > packages_enabled.txt`
   - `adb -s <TV> shell pm list packages -d | sed 's/package://' | sort > packages_disabled.txt`
   - `adb -s <TV> shell pm list packages -s | sed 's/package://' | sort > packages_system.txt`
   - `adb -s <TV> shell pm list packages -3 | sed 's/package://' | sort > packages_user.txt`
   - `adb -s <TV> shell settings list global | sort > settings_global.txt`
   - `adb -s <TV> shell settings list secure | sort > settings_secure.txt`
   - `adb -s <TV> shell settings list system | sort > settings_system.txt`
3. Tell me: *"✅ Safety snapshot captured in backups/<folder>/. All changes are 100% reversible anytime."*

### Step 3: Present 3 Clear Setup Options
Display a clean choice menu in our chat and wait for my selection:

> "Welcome to Spider TV Setup! Please choose which mode you want to run:"
> - **[Option 1] Safe Launcher Setup Only (100% Risk-Free)**
>   *Installs the ultra-lightweight Spider TV Launcher (+ optional SmartTube), sets it as default Home, and leaves all stock apps & launchers completely untouched. Zero risk of bricking or losing OEM features.*
>
> - **[Option 2] Debloat Only (TV Cleaner & Speedup)**
>   *Scans and identifies manufacturer bloatware, telemetry, and sponsored rows on your TV. Shows you a categorized list for your approval before safely disabling them via `pm disable-user`. Leaves your existing launcher untouched. (After cleaning, asks if you'd like to install Spider TV Launcher).*
>
> - **[Option 3] Full Transformation (Debloat + Spider TV Launcher)**
>   *Complete makeover: performs an interactive debloat first, then installs Spider TV Launcher, sets Home, and configures optional 0.5x snappy animation scales.*

**Wait for my answer (1, 2, or 3) before proceeding.**

### Step 4: Execution Rules (Based on My Choice)

#### If I Choose Option 1 (Safe Launcher Setup Only):
1. Install release APK from `app/build/outputs/apk/release/app-release.apk` (or build with `./gradlew assembleRelease`):
   `adb -s <TV> install -r app/build/outputs/apk/release/app-release.apk`
2. Start the launcher:
   `adb -s <TV> shell am start -n com.spidertv.launcher/.SpiderHomeActivity`
3. Set as default Home:
   `adb -s <TV> shell cmd package set-home-activity --user 0 com.spidertv.launcher/.SpiderHomeActivity`
4. Ask if I want official Ad-Free SmartTube installed:
   - If yes: download via `./tools/get_smarttube.sh` and `adb install -r <apk>`.
5. Proceed to Step 5 (Verify).

#### If I Choose Option 2 (Debloat Only):
1. Read the list of installed packages from my TV.
2. Read `tools/protected.txt` from this repo — **NEVER** disable any package listed in `tools/protected.txt`, and **NEVER** disable active stock launchers (`launcherx`, `tvlauncher`, `mitv.tvhome`) to prevent recovery crash loops.
3. Categorize non-critical bloatware found on my TV:
   - **OEM Analytics & Telemetry:** e.g., Mi Analytics, tracking daemons.
   - **Sponsored Recommendations & Ad Rows:** e.g., PatchWall ad services, showcase rows.
   - **Unused Pre-installed OEM Apps:** e.g., OEM Gallery, unused stock players.
4. Present the list clearly in our chat with checkboxes and wait for my explicit approval:
   *"Do you approve disabling these specific packages? Reply 'Approved' or tell me which ones to uncheck."*
5. Once approved, disable only the approved packages:
   `adb -s <TV> shell pm disable-user --user 0 <pkg>`
6. Once debloat is complete, ask me:
   *"TV has been cleaned and bloatware disabled! Would you also like to install the Spider TV Launcher now? (Yes / No)"*
   - If Yes: execute Option 1 installation steps.
   - If No: proceed to Step 5 (Verify).

#### If I Choose Option 3 (Full Transformation):
1. Execute Option 2 (Interactive Debloat with approval).
2. Execute Option 1 (Spider TV Launcher installation & Home set).
3. Set 0.5x animation scales for instant TV snappiness:
   `adb -s <TV> shell settings put global window_animation_scale 0.5`
   `adb -s <TV> shell settings put global transition_animation_scale 0.5`
   `adb -s <TV> shell settings put global animator_duration_scale 0.5`
4. Proceed to Step 5 (Verify).

### Step 5: Verification & Remote Shortcuts Handover
1. Press Home key via ADB: `adb -s <TV> shell input keyevent 3`.
2. Confirm the focused window on TV screen.
3. Explain the remote shortcuts to me:
   - **Double-Press Home Button:** Instantly opens the native Recent Apps Card Switcher from any app.
   - **Volume Key Hold:** 100% hardware-speed volume acceleration (zero accessibility lag).
   - **How to Revert Anytime:** Remind me that any disabled app can be re-enabled instantly with `adb shell pm enable <pkg>`, or restored using `./tools/restore.sh backups/<folder>`.
```
