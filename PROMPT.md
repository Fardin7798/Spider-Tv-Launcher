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
Before touching anything or making any changes (especially before Debloat in Option 2 or 3), capture a full system state snapshot directly on my computer:
1. Designated User Location: `~/Downloads/SpiderTv/Backup/<TV_IP>_<TIMESTAMP>/` (e.g., `~/Downloads/SpiderTv/Backup/192.168.1.50_5555_20261109_185000/`; fallback: `backups/<TV_IP>_<TIMESTAMP>/`)
   - Also create `~/Downloads/SpiderTv/Backup/<TV_IP>_original/` if this is the very first baseline run.
2. Run these non-destructive read commands to save state into that directory:
   - Create folder & capture hardware/OS metadata:
     `BACKUP_DIR="${HOME}/Downloads/SpiderTv/Backup/<TV_IP>_<TIMESTAMP>"`
     `mkdir -p "$BACKUP_DIR"`
     `echo "TARGET_DEV=<TV_IP>:5555" > "$BACKUP_DIR/metadata.env"`
     `echo "MFR=$(adb -s <TV> shell getprop ro.product.manufacturer | tr -d '\r')" >> "$BACKUP_DIR/metadata.env"`
     `echo "MODEL=$(adb -s <TV> shell getprop ro.product.model | tr -d '\r')" >> "$BACKUP_DIR/metadata.env"`
     `echo "SDK=$(adb -s <TV> shell getprop ro.build.version.sdk | tr -d '\r')" >> "$BACKUP_DIR/metadata.env"`
     `echo "RELEASE=$(adb -s <TV> shell getprop ro.build.version.release | tr -d '\r')" >> "$BACKUP_DIR/metadata.env"`
     `HOME_ACT=$(adb -s <TV> shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1 | tr -d '\r')`
     `echo "ORIG_HOME_RES=$HOME_ACT" >> "$BACKUP_DIR/metadata.env"`
     `echo "ORIG_HOME_PKG=$(echo "$HOME_ACT" | cut -d/ -f1)" >> "$BACKUP_DIR/metadata.env"`
     `echo "WINDOW_ANIMATION_SCALE=$(adb -s <TV> shell settings get global window_animation_scale | tr -d '\r')" >> "$BACKUP_DIR/metadata.env"`
     `echo "TRANSITION_ANIMATION_SCALE=$(adb -s <TV> shell settings get global transition_animation_scale | tr -d '\r')" >> "$BACKUP_DIR/metadata.env"`
     `echo "ANIMATOR_DURATION_SCALE=$(adb -s <TV> shell settings get global animator_duration_scale | tr -d '\r')" >> "$BACKUP_DIR/metadata.env"`
     `echo "SNAPSHOT_TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%SZ")" >> "$BACKUP_DIR/metadata.env"`
   - Package lists:
     `adb -s <TV> shell pm list packages -e | sed 's/package://' | sort > "$BACKUP_DIR/packages_enabled.txt"`
     `adb -s <TV> shell pm list packages -d | sed 's/package://' | sort > "$BACKUP_DIR/packages_disabled.txt"`
     `adb -s <TV> shell pm list packages -s | sed 's/package://' | sort > "$BACKUP_DIR/packages_system.txt"`
     `adb -s <TV> shell pm list packages -3 | sed 's/package://' | sort > "$BACKUP_DIR/packages_user.txt"`
   - System settings dumps:
     `adb -s <TV> shell settings list global | sort > "$BACKUP_DIR/settings_global.txt"`
     `adb -s <TV> shell settings list secure | sort > "$BACKUP_DIR/settings_secure.txt"`
     `adb -s <TV> shell settings list system | sort > "$BACKUP_DIR/settings_system.txt"`
   - Initialize changelog:
     `touch "$BACKUP_DIR/changelog.txt"`
3. Tell me: *"✅ Safety snapshot captured in ~/Downloads/SpiderTv/Backup/<folder>/. All changes are 100% reversible anytime."*

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
5. Once approved, disable only the approved packages and log them into `backups/<folder>/changelog.txt`:
   `adb -s <TV> shell pm disable-user --user 0 <pkg>`
   `echo "<pkg>" >> "backups/<folder>/changelog.txt"`
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
   - **How to Revert Anytime:** Remind me that I can ask you to "Restore my TV" anytime, and you will restore everything from the backup automatically.

### Step 6: Restoration & Rollback Playbook (Agent-Native)
Whenever I ask to **"Restore my TV"**, **"Revert changes"**, **"Undo debloat"**, or **"Go back to original state"**, execute this automated restoration without needing any shell scripts:

1. **Locate Backup Snapshot:**
   - Scan backup directories: `ls -d ~/Downloads/SpiderTv/Backup/* backups/* 2>/dev/null`
   - If multiple snapshots exist, ask me which one to restore (recommend the latest or `*_original`).
   - Read `metadata.env` from that folder:
     `cat "<selected_backup_folder>/metadata.env"`

2. **Re-Enable All Original Packages:**
   - Read `packages_enabled.txt` from the selected backup folder and re-enable each package:
     ```bash
     while IFS= read -r pkg || [ -n "$pkg" ]; do
       [ -n "$pkg" ] && adb -s <TV> shell pm enable --user 0 "$pkg" >/dev/null 2>&1
     done < "<selected_backup_folder>/packages_enabled.txt"
     ```
   - Confirm to me: *"Re-enabled all original system packages."*

3. **Restore System Animation Scales:**
   - Restore animation scale values recorded in `metadata.env`:
     `adb -s <TV> shell settings put global window_animation_scale "${WINDOW_ANIMATION_SCALE:-1.0}"`
     `adb -s <TV> shell settings put global transition_animation_scale "${TRANSITION_ANIMATION_SCALE:-1.0}"`
     `adb -s <TV> shell settings put global animator_duration_scale "${ANIMATOR_DURATION_SCALE:-1.0}"`

4. **Restore Original Default Home Launcher:**
   - If `ORIG_HOME_RES` is in `metadata.env`:
     `adb -s <TV> shell cmd package set-home-activity --user 0 "${ORIG_HOME_RES}"`

5. **Optional App Cleanup (Ask Me):**
   - Ask me: *"Would you also like to uninstall Spider TV Launcher or SmartTube from your TV? (Yes / No)"*
   - If Yes:
     `adb -s <TV> shell pm uninstall com.spidertv.launcher`
     `adb -s <TV> shell pm uninstall com.teamsmart.videomanager.tv`
     `adb -s <TV> shell pm uninstall com.liskovsoft.videomanager`
     `adb -s <TV> shell pm uninstall org.smarttube.stable`

6. **Verify Restored TV State:**
   - Press Home (`adb -s <TV> shell input keyevent 3`).
   - Confirm focused window is the original OEM launcher.
   - Tell me: *"✅ Restoration complete! TV is 100% restored to its original factory state."*
```
