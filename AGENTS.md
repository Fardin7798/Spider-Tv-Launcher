# Agent Runbook: Autonomous Android TV Transformation

This document is the single source of truth for AI agents (Antigravity, Claude Code, Cursor, Devin, Roo Code, Aider, Windsurf) executing tasks in this repository.

---

## 🎯 Primary Goal
Transform any connected Android TV or Google TV (Sony, TCL, Xiaomi, OnePlus, Hisense, Shield, Chromecast, Fire TV) into an ultra-clean, ad-free experience with:
1. Fluid Spider-Man night carousel theme with hanging hero art, flip clock, and squircle icons.
2. Native Recent Apps Switcher (`RecentAppsActivity`) with double-press Home gesture.
3. 100% hardware-speed volume hold acceleration preserved (zero accessibility lag).
4. Official Ad-Free SmartTube replacing stock YouTube.
5. Safe, reversible multi-brand debloating.

---

## ⚡ Standard Execution Workflow (Audit First)

When a user asks you to set up their TV at an IP address:

```bash
# Step 1: Run read-only audit (ZERO changes to TV)
./tools/audit.sh <TV_IP>

# Step 2: Present summary from reports/report_<ts>.md to user and get approval.

# Step 3: Run approved apply script
bash reports/apply_<ts>.sh
```

## 🛠️ Agent-Native Execution Protocol (Native ADB, No Fragile Bash Wrappers)

When setting up or helping a user with their Android TV:

1. **Safety Baseline (Always First):**
   - Connect via Wi-Fi ADB (`adb connect <TV_IP>:5555`).
   - Capture a full-state safety snapshot directly in `backups/<device>_<timestamp>/` (`pm list packages -e/-d/-s/-3`, `settings list global/secure/system`, `getprop`).

2. **Present 3 Clear Options in Chat:**
   - **[Option 1] Safe Launcher Setup Only:** Install Spider TV Launcher (`app-release.apk`), set default Home, leave all system packages untouched (100% risk-free).
   - **[Option 2] Debloat Only:** Scan TV packages, check against `tools/protected.txt`, present categorized list (Analytics, Ads, Unused OEM apps) for user approval, and safely disable approved packages via `pm disable-user`. Ask if they want launcher installed after.
   - **[Option 3] Full Transformation:** Interactive Debloat approval + Spider TV Launcher installation + 0.5x animation scales.

3. **Restoration Anytime:**
   - Re-enable packages: `adb shell pm enable <pkg>`
   - Restore from backup snapshot: Follow Agent-Native Restoration Playbook in PROMPT.md (re-enable packages via `pm enable --user 0 <pkg>`, restore animation scales, restore `ORIG_HOME_RES`).
## 🛡️ Critical Guardrails for Agents

1. **NEVER Disable Protected System Services:**
   - Always honor `tools/protected.txt` (Chromecast, Assistant, Gboard, Wi-Fi, System UI, GMS, setup wizards).
   - Never disable stock launchers via generic debloat; to prevent recovery crash loops.
2. **Preserve Linux `InputDispatcher` Repeat Rate (Volume Keys):**
   - Accessibility services (`SpiderHomeKeyService`, `SpiderInputKeyService`) exist exclusively for Home double-press and TV input switching.
   - Volume and navigation keys must NEVER be intercepted or consumed in accessibility services to preserve Android's native repeat queue and hold acceleration.
3. **Always Ensure Reversibility:**
   - Always verify that a snapshot exists before touching the TV.
   - To restore: Follow Agent-Native Restoration Playbook in PROMPT.md (read snapshot in `backups/<folder>`, re-enable `packages_enabled.txt`, reset scales, and set stock home).
4. **AI Agent Autonomous Approval Ban:**
   - AI agents must NEVER disable any package autonomously without explicit user approval ("Approved" / "Proceed") in chat.
