<div align="center">

# ☀️ Morning Peace

**Reclaim your mornings and find deep focus without digital distractions.**

[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge&logo=githubactions&logoColor=white)](#)
[![Android](https://img.shields.io/badge/Android-7.0%2B%20(API%2024%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34-blue?style=for-the-badge)](#)

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-how-it-works">How It Works</a> •
  <a href="#-architecture--tech-stack">Architecture</a> •
  <a href="#-permissions">Permissions</a> •
  <a href="#-building--running">Building</a> •
  <a href="#-contributing">Contributing</a>
</p>

</div>

---

## 📖 Overview

**Morning Peace** is a lightweight, privacy-first Android application crafted to protect your morning routine and create distraction-free focus blocks. By detecting sleep cycles through non-invasive screen-off heuristics and offering flexible custom timers, it shields you from doomscrolling and digital fatigue before your day even begins.

---

## ✨ Features

- **☀️ Smart Morning Block** — Automatically detects when you wake up (based on overnight inactivity) and prevents distracting apps from opening during your chosen morning window (15m to 2h).
- **🎯 Focus Lock (Peace Time)** — Quick-launch deep focus sessions throughout your workday with customized durations.
- **📱 Custom App Whitelist** — Keep indispensable utilities (Phone, Maps, Alarm, Camera) accessible while locking non-essential social and feed apps.
- **🔒 Mindful Lock Screen** — Elegant, calm full-screen overlay displaying remaining peace time and soothing visuals instead of jarring blocks.
- **🔄 In-App Auto Update** — Seamlessly checks for new releases directly via the GitHub Releases API and handles in-app updates.
- **🔋 Battery-Efficient & Offline** — Zero background battery drain; relies on event-driven Android Broadcast Receivers and Accessibility events without constant polling.

---

## 🚀 How It Works

```mermaid
flowchart LR
    A[Screen Inactive Overnight] -->|User Unlocks Phone| B{Sleep Detected?}
    B -->|Yes (> Inactivity Threshold)| C[Activate Morning Peace Block]
    B -->|No| D[Normal Phone Usage]
    C --> E[Show Mindful Overlay on Non-Whitelisted Apps]
    E -->|Timer Expires / Auto-Unlock| F[Phone Restored & Unlocked]
```

1. **Sleep Detection**: Tracks device idle duration. When you unlock your phone after sleeping, Morning Peace automatically engages the morning block.
2. **Whitelist Protection**: Configure essential communication and productivity apps that bypass the block.
3. **Peaceful Focus**: During an active block, attempting to open a restricted app brings up a calming countdown screen.
4. **Auto Unlock**: When the timer concludes, normal device functionality is restored automatically with zero user intervention required.

---

## 🛠️ Architecture & Tech Stack

- **Language**: [Kotlin](https://kotlinlang.org/)
- **Min SDK**: API 24 (Android 7.0 Nougat)
- **Target SDK / Compile SDK**: API 34 (Android 14)
- **UI & Layout**: ViewBinding, Material 3 Design Components, ConstraintLayout, Dynamic Light/Dark support
- **System Integrations**:
  - `AccessibilityService` — Real-time detection of foreground package transitions.
  - `AlarmManager` (`RTC_WAKEUP`) — Exact timer triggers and state management.
  - `BroadcastReceiver` — Device boot persistence and power state monitoring.
  - `FileProvider` — Secure APK downloading and installation for auto-updates.

---

## 🔐 Required Permissions & Privacy

Morning Peace is strictly local and values user privacy. No telemetry or personal usage data is collected or transmitted.

| Permission | Purpose |
| :--- | :--- |
| **Accessibility Service** | Detects foreground app switches to display the lock overlay when a restricted app is opened. |
| **System Alert Window (`Overlay`)** | Renders the calm countdown screen over blocked applications. |
| **Exact Alarm (`SCHEDULE_EXACT_ALARM`)** | Ensures precise timer countdowns and automatic session unlocking. |
| **Receive Boot Completed** | Re-schedules alarms and ensures sleep detection persists across device restarts. |
| **Post Notifications** | Provides download and progress status when updating the application. |

---

## 🔨 Building & Running

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17
- Android SDK 34

### Build via Command Line
```bash
# Clone the repository
git clone https://github.com/leo-BWL/MorningPeace.git
cd MorningPeace

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

The compiled APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 📂 Project Structure

```text
MorningPeace/
├── .github/
│   ├── workflows/
│   │   └── build.yml               # GitHub Actions CI build & test workflow
│   ├── ISSUE_TEMPLATE/             # Bug report & feature request templates
│   └── pull_request_template.md    # PR submission template
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/morningpeace/
│   │   │   │   ├── AppBlockerService.kt     # Accessibility service
│   │   │   │   ├── AppPickerActivity.kt     # Whitelist manager UI
│   │   │   │   ├── BlockStateManager.kt     # Core state machine & preferences
│   │   │   │   ├── LockScreenActivity.kt    # Fullscreen overlay UI
│   │   │   │   ├── MainActivity.kt          # Primary settings dashboard
│   │   │   │   ├── UpdateManager.kt         # GitHub Release update client
│   │   │   │   └── WhitelistManager.kt      # Whitelisted package storage
│   │   │   └── res/                         # UI layouts, drawables & themes
│   │   └── test/                            # Unit tests
│   └── build.gradle.kts
├── CONTRIBUTING.md
├── CHANGELOG.md
├── LICENSE
└── README.md
```

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to check the [issues page](https://github.com/leo-BWL/MorningPeace/issues) or read [CONTRIBUTING.md](CONTRIBUTING.md) to get started.

---

## 📄 License

This project is open source and available under the [MIT License](LICENSE).
