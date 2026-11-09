# CLAUDE.md — Operating Guidelines for Claude Code & AI Assistants

This repository contains **Spider TV Launcher**, an ultra-lightweight (~450 KB), fan-made Spider-themed Android TV / Google TV launcher and safety debloat suite.

## 🛠️ Agent-Native Execution Protocol (Native ADB, No Fragile Bash Wrappers)

When setting up or helping a user with their Android TV:

1. **Safety Baseline (Always First):**
   - Connect via Wi-Fi ADB (`adb connect <TV_IP>:5555`).
   - Capture a full-state safety snapshot directly in `~/Downloads/SpiderTv/Backup/<device>_<timestamp>/` (fallback: `backups/<device>_<timestamp>/`) (`pm list packages -e/-d/-s/-3`, `settings list global/secure/system`, `getprop`).

2. **Present 3 Clear Options in Chat:**
   - **[Option 1] Safe Launcher Setup Only:** Install Spider TV Launcher (`app-release.apk`), set default Home, leave all system packages untouched (100% risk-free).
   - **[Option 2] Debloat Only:** Scan TV packages, check against `tools/protected.txt`, present categorized list (Analytics, Ads, Unused OEM apps) for user approval, and safely disable approved packages via `pm disable-user`. Ask if they want launcher installed after.
   - **[Option 3] Full Transformation:** Interactive Debloat approval + Spider TV Launcher installation + 0.5x animation scales.

3. **Restoration Anytime:**
   - Re-enable packages: `adb shell pm enable <pkg>`
   - Restore from backup snapshot: Follow Agent-Native Restoration Playbook in PROMPT.md (re-enable packages via `pm enable --user 0 <pkg>`, restore animation scales, restore `ORIG_HOME_RES`).

## 🛡️ Critical Guardrails for AI Assistants

- **Autonomous Approval Ban:** You must NEVER disable any package autonomously without the user explicitly stating "Approved" or "Proceed" in chat.
- **Protected Packages SSOT:** Honor `tools/protected.txt` at all times (Chromecast, Assistant, Gboard, Bluetooth, Wi-Fi, Settings, System UI, GMS, Setup Wizards).
- **Stock Launcher Protection:** Stock launchers (`launcherx`, `tvlauncher`, `leanbacklauncher`, `mitv.tvhome`) must NEVER be disabled via generic debloat; to prevent recovery crash loops.
- **Preserve Remote Repeat Timers:** Volume keys must NEVER be intercepted by accessibility services to preserve Android's native Linux `InputDispatcher` repeat rate.
