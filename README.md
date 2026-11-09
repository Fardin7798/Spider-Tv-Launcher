# Spider TV Launcher for Android TV

<p align="center">
  <a href="https://github.com/Fardin7798/Spider-Tv-Launcher/releases/latest"><img src="https://img.shields.io/github/v/release/Fardin7798/Spider-Tv-Launcher?color=success&label=Release" alt="Release"></a>
  <a href="https://github.com/Fardin7798/Spider-Tv-Launcher/releases/latest/download/app-release.apk"><img src="https://img.shields.io/badge/Download-APK%20(~450%20KB)-brightgreen?logo=android" alt="Download APK"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green.svg" alt="License"></a>
  <a href="https://developer.android.com/about/versions/pie"><img src="https://img.shields.io/badge/Android%20TV-9.0%2B-blue.svg" alt="Android TV"></a>
  <img src="https://img.shields.io/badge/APK%20Size-~450%20KB-success.svg" alt="Size">
</p>


An ultra-lightweight, ad-free Android TV launcher with a fan-made Spider theme: a web-swing app carousel, live flip clock, quick controls, a native **Recent Apps Switcher**, and one-click debloat.

> 🕷️ **Fan-made, unofficial tribute.** All artwork, including the hero, is original and made for this project. Not affiliated with, endorsed or sponsored by Marvel or Sony.

---

<p align="center">
  <img src="docs/SpiderHome.gif" alt="Home Screen" width="850">
</p>
<p align="center"><em>Fluid Dynamic Carousel Launcher</em></p>

---

## ✨ Features

- 🕷️ **Fluid Spider Carousel:** Night skyline backdrop with original hanging-hero art on a silk thread, and directional spring physics as you move between apps.
- 🕰️ **Live Flip Clock:** On the home screen, plus a matching **Clock** screensaver.
- 🎛️ **Quick Controls:** A top pill for Inputs, Wi-Fi and Bluetooth (pair remotes and accessories straight from Home).
- 🔄 **Recent Apps Switcher:** Smooth horizontal carousel of running apps with live task switching and quick task controls.
- ⚡ **Zero Volume Lag (Hardware Preservation):** Unlike accessibility-based remappers that break Android's `InputDispatcher` repeat timer, our launcher preserves 100% hardware-speed volume hold acceleration.
- 🎮 **Remote Gesture Shortcuts:** Double-press the Home button from anywhere to immediately bring up the App Switcher.
- 🪶 **Ultra-Lightweight (~450 KB):** Pure Java, zero analytics, zero telemetry, zero background battery drain.
- 🧹 **Safe Debloat Ready:** Compatible with ADB debloat lists to disable manufacturer bloatware (PatchWall, telemetry, recommendation rows) without root.

---

## 📸 Screenshots

| | |
|---|---|
| ![Spider Home Carousel](docs/SpiderHome.gif) | ![Recent Apps Switcher](docs/SpiderRecents.gif) |
| *Fluid Dynamic Carousel (Directional Focus & Art)* | *Native App Switcher (Double-Press Home)* |
---

## 📺 Running On A Real TV

Recorded on my own TV: the home carousel, Recent Apps, Clear all and back to Home.

<p align="center">
  <img src="docs/SpiderOnMyTV.gif" alt="Spider TV running on a real Xiaomi Mi TV" width="640">
</p>

<p align="center"><a href="docs/SpiderOnMyTV.mp4">▶️ Watch the full clip (MP4)</a></p>

**Tested on:**

| | |
|---|---|
| TV | Xiaomi Mi TV (`MiTV-AXSO0`, codename `dangal`) |
| Android | Android TV 9 (API 28) |
| Chip | Amlogic quad-core (`txlx`) |
| RAM | 1 GB |
| Screen | 1280 × 720 (HD) |

Home stays on Spider TV after setup. Tested it on another TV? Open an issue or PR and tell us the model so we can add it here.

## 🚀 1-Click Universal TV Setup

Transform any Android TV or Google TV into an ultra-clean, ad-free experience with automated debloating, SmartTube installation, and the ~450 KB Spider TV Launcher.

### 📋 Prerequisites & Requirements

Before running the setup, ensure:
1. 📶 **Same Wi-Fi Network:** Your computer (laptop/PC) and Android TV must be connected to the exact same Wi-Fi router / network.
2. 📺 **Android TV 9.0+:** Compatible with any TV, box, or stick running Android TV 9, 10, 11, 12, or 14 (Google TV).
3. ⚙️ **Enable USB Debugging on TV:**
   - Go to **Settings ➔ Device Preferences ➔ About**.
   - Scroll down to **Build** and click OK on your remote **7 times** (until you see *"You are now a developer"*).
   - Go back, open **Developer options**, and turn **USB debugging** ON (also turn **Network debugging** ON if present).
4. 💻 **ADB Tool:** Standard Android Platform Tools (`sudo apt install adb` on Linux, `brew install android-platform-tools` on macOS, or pre-bundled in PowerShell for Windows).

---

### 🤖 1-Click Setup with Any AI Agent (Recommended)

You can give this repository to any AI coding assistant (**Antigravity, Cursor, Claude Code, Devin, Roo Code, Windsurf, ChatGPT**). The assistant will autonomously connect to your TV, take a safety backup, and let you choose between 3 setup modes:

| Mode | What It Does | Best For | Risk Level |
|---|---|---|:---:|
| **Option 1: Safe Launcher Setup** | Installs Spider TV Launcher & sets default Home. **Zero system apps touched.** | Users who want the aesthetic Spider-Man launcher safely without touching system firmware. | **Zero Risk (100% Safe)** |
| **Option 2: Debloat Only** | Scans and lists OEM ads, telemetry, and sponsored rows for your approval before safely disabling them via `pm disable-user`. Existing launcher untouched. | Users who want to speed up their TV and remove OEM ads while keeping their current launcher. | **Safe (Reversible)** |
| **Option 3: Full Transformation** | Complete makeover: performs interactive debloating first, then installs Spider TV Launcher, sets Home, and enables snappy 0.5x animation scales. | Complete clean-slate TV makeover. | **Safe (Snapshot Protected)** |

#### How to run with an AI Agent:
1. **On your TV:** Enable USB Debugging (**Settings ➔ Device Preferences ➔ About ➔ Click 'Build' 7 times**, then open **Developer options ➔ USB Debugging ON**).
2. **In your AI Agent:** Copy and paste [`PROMPT.md`](PROMPT.md) into the assistant chat.
3. **Select Mode:** The agent will connect over Wi-Fi ADB, capture a snapshot in `backups/`, and ask whether you want Option 1, 2, or 3!

---

### 💻 Manual Terminal Setup (Standalone Launcher Installation)

If you prefer running terminal commands yourself without an AI assistant:
```bash
# 1. Connect to your TV over Wi-Fi
adb connect <TV_IP>:5555

# 2. (Recommended) Capture an instant safety backup of current packages
mkdir -p backups
adb shell pm list packages -e | sed 's/package://' | sort > backups/packages_enabled.txt

# 3. Install the pre-built release APK (or build with ./gradlew assembleRelease)
adb install -r app/build/outputs/apk/release/app-release.apk

# 4. Set as default Home launcher
adb shell cmd package set-home-activity --user 0 com.spidertv.launcher/.SpiderHomeActivity

# 5. Grant usage access for Recent Apps Switcher
adb shell appops set com.spidertv.launcher GET_USAGE_STATS allow
```

Then on the TV, open **Settings ➔ Accessibility** and turn on **Home button** (double-press Home for Recents) and **Spider TV Launcher** (remote shortcut keys). Pick **Clock** under Screensaver if you want the flip-clock screensaver.

---

## 🛡️ Multi-Brand Debloat Profiles

Pre-configured, verified debloat profiles are organized in [`profiles/`](profiles/):

| Brand / Platform | Profile File | Target Capabilities Cleaned |
|---|---|---|
| **Xiaomi / Redmi** | [`profiles/xiaomi.txt`](profiles/xiaomi.txt) | PatchWall, Glance lockscreen ads, Mi Video, Mi Music, Mi Analytics |
| **Sony BRAVIA** | [`profiles/sony-bravia.txt`](profiles/sony-bravia.txt) | Samba TV ACR tracking, Sony Select promos, Sony Discover, Demo mode |
| **TCL** | [`profiles/tcl.txt`](profiles/tcl.txt) | TCL Channel, TCL TTVS tracker, TCL Assistant, recommendation feeds |
| **OnePlus TV** | [`profiles/oneplus.txt`](profiles/oneplus.txt) | OxygenPlay, OnePlus Store, HeyTap telemetry, preloaded browser |
| **Hisense** | [`profiles/hisense.txt`](profiles/hisense.txt) | Hisense telemetry, partner ad services, sponsored home rows |
| **NVIDIA SHIELD** | [`profiles/nvidia-shield.txt`](profiles/nvidia-shield.txt) | Shield recommendation rows, telemetry, partner promotions |
| **Chromecast with Google TV** | [`profiles/chromecast-gtv.txt`](profiles/chromecast-gtv.txt) | Google TV sponsored rows, partner auto-installs, feedback sender |
| **Amazon Fire TV** | [`profiles/amazon-fire-tv.txt`](profiles/amazon-fire-tv.txt) | Fire TV sponsored banners, forced promotions, metrics |
| **Universal Fallback** | [`profiles/universal-safe.txt`](profiles/universal-safe.txt) | Safe for any Android TV 9.0+ device |

> [!NOTE]
> **Safety Whitelist:** None of our profiles ever disable Chromecast built-in (`com.google.android.apps.mediashell`), Google Assistant voice search (`com.google.android.katniss`), Wi-Fi/Bluetooth stacks, or Gboard.

---

## ⏪ Instant Rollback & True Full-State Restore

Every modification is 100% reversible. A complete snapshot of your TV's original state (enabled packages, original default Home launcher, animation scales, and system settings) is preserved in `backups/<device>_original/` (Day-1 baseline) and `backups/<device>_<timestamp>/` (per-run).

> [!NOTE]
> **What is restored:** System packages, stock home launcher, animation scales, and system display settings are fully restored. (Private app data directories and saved Wi-Fi credentials cannot be accessed or modified over standard ADB without root access).

### 🔄 Reverting / Restoring to Factory State Anytime
You can ask your AI assistant anytime:
> *"Please restore my TV back to its original state using backups/<folder>."*

Your AI assistant will automatically:
- 🔄 Re-enable all original system packages via `pm enable --user 0 <pkg>`.
- 📺 Restore your original stock launcher (`ORIG_HOME_RES` from `metadata.env`) as default Home.
- ⚡ Revert window and transition animation scales back to original values.
- 🕷️ Optionally cleanly uninstall Spider TV Launcher and SmartTube if requested.

Or manually run via ADB:
```bash
# 1. Re-enable all original packages
while IFS= read -r pkg; do [ -n "$pkg" ] && adb shell pm enable --user 0 "$pkg"; done < backups/<device_folder>/packages_enabled.txt

# 2. Reset animation scales
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings put global animator_duration_scale 1.0
```
---

## 🕸️ What's Inside

- 🕷️ Spider night carousel theme with original hanging-hero art, silk thread and live flip clock
- 🔄 Spider Recents task switcher (`RecentAppsActivity`) with 16:9 TV banners
- 🎮 TV Bluetooth remotes & accessories pairing from the top pill
- ⚡ Zero-lag volume hold acceleration
- 🎮 Remote double-press Home gesture
- 🧹 Automated Agent-Native debloat, safety backup snapshot and full restoration engine

## 🤝 Contributing

Contributions are welcome! Report a bug, suggest a new suit, add a debloat profile for your TV, or pick up a `good first issue`. Read the [Contributing guide](CONTRIBUTING.md) to get started, and please follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## ⚖️ Fan-Made Project & Legal Disclaimer

Spider TV Launcher is an **unofficial, fan-made tribute**: an independent, non-commercial, open-source project for personal TV customization.

- **Original artwork:** All art in this project, including the hero, the night backdrop, the app icon and the TV banner, was made by the author for this project.
- **No affiliation:** This project is not affiliated with, endorsed, sponsored or approved by Marvel Entertainment, LLC, Sony Pictures Entertainment Inc., or any related company. "Spider-Man" and related names and characters are trademarks of their respective owners.
- **Third-party trademarks:** App names and logos shown (such as YouTube, Netflix, Prime Video, Disney+ Hotstar, SmartTube, Google Play / Android) belong to their respective owners.
- Distributed free of charge under the MIT License.

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
