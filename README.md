# SpeakSheet 📊 🔊

> **An Accessible, Voice-Enabled Spreadsheet Application for Android**  
> Designed with care for visually impaired users, accessibility enthusiasts, and mobile power users.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## 🌟 Overview

**SpeakSheet** transforms the traditional spreadsheet experience into an inclusive, highly accessible tool. By combining a customized hardware-accelerated canvas grid with integrated Text-to-Speech (TTS), haptic feedback, and multi-modal navigation, SpeakSheet empowers users to effortlessly navigate, inspect, and edit grid data without visual strain.

---

## ✨ Key Features

### 🎙️ Smart Text-to-Speech (TTS) Integration
- **Context-Aware Cell Announcements**: Automatically announces cell coordinates, column headers, and content upon selection.
- **Configurable Speech Order**: Choose between *Header First* (e.g., *"Price: $14.99 at B4"*) or *Value First* (e.g., *"$14.99, Price, Cell B4"*).
- **Audio & Haptic Feedback**: Subtle vibration cues accompany cell selection and edge bounces for reassuring physical feedback.
- **Customizable Speech Engine**: Adjust speech rate, pitch, and voice preferences directly in settings.

### 🔍 Dynamic Multi-Gesture Zoom & Scaled Typography
- **Crisp Vector Typography**: Text is rendered cleanly at all zoom levels (70% to 300%) without pixelation or blur.
- **Pinch-to-Zoom & Pan**: Natural two-finger gestures scale the entire spreadsheet—headers, cell borders, selection outlines, and typography—in perfect synchronization.
- **Top-Right Quick Zoom Reset**: Instant **Reset (100%)** pill button right in the top app bar whenever zoom is active.
- **Options Menu Zoom Controls**: Access **Reset Zoom (100%)**, **Zoom In (+20%)**, **Zoom Out (-20%)**, and rapid presets (80%, 100%, 125%, 150%, 200%) under the top-right three-dot menu.

### 🕹️ Ergonomic Directional Navigation (D-Pad)
- **4-Way Navigational Controls**: Dedicated, tactile **Left**, **Up**, **Down**, and **Right** buttons located in the bottom action bar.
- **Smart Viewport Landing**: Automatically scrolls with generous padding margins so active cells never clip against the edges of the screen.

### ✏️ Complete Spreadsheet Tooling
- **In-Place Cell Editing**: Double-tap any cell or use the quick Edit action to modify cell formulas and values.
- **Context Actions**: One-tap Copy, Paste, Delete, and Speak actions for active cells.
- **Sample Templates**: Pre-loaded with Household Budget, Class Attendance, Inventory Tracker, and Gradebook templates.
- **Recent Files & Local Database**: Powered by Android Room for lightning-fast offline persistence and autosave.

### ♿ Accessibility & Customization
- **Large Touch Target Mode**: Expands touch boundaries and cell dimensions to exceed accessibility standards.
- **Row & Column Headers Toggle**: Toggle numeric row headers and alphabet column headers based on preference.
- **Material Design 3**: Full support for system Light and Dark themes with high-contrast active borders.

---

## 🏗️ Architecture & Technology Stack

SpeakSheet follows modern Android development best practices and Clean Architecture / MVVM principles:

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Toolkit**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3
- **Data Persistence**: [Room Database](https://developer.android.com/training/data-storage/room)
- **Speech Synthesis**: Android `TextToSpeech` API with lifecycle management
- **Concurrency & State**: Kotlin Coroutines, `StateFlow`, `collectAsStateWithLifecycle`
- **Graphics & Rendering**: Low-overhead virtualized Compose `Canvas` with optimized bounds-clipping

```text
app/src/main/java/com/example/
├── MainActivity.kt               # Entry point & edge-to-edge setup
├── SpeakSheetApp.kt              # App-level navigation & screen router
├── data/
│   ├── AppDatabase.kt            # Room database configuration
│   ├── RecentFile.kt             # Recent spreadsheet entity
│   ├── RecentFileDao.kt          # DAO for file operations
│   ├── SampleSheets.kt           # Built-in spreadsheet templates
│   └── SettingsRepository.kt     # Preferences & accessibility settings
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt         # Recent files, template gallery, search
│   │   ├── SpreadsheetScreen.kt  # Virtualized grid canvas & action bars
│   │   └── SettingsScreen.kt     # TTS & accessibility configuration
│   └── theme/                    # ColorScheme, Typography, Material Theme
├── utils/
│   ├── SpreadsheetEngine.kt      # Core calculation & virtual grid layout engine
│   └── TtsManager.kt             # Text-to-Speech lifecycle & speech synthesis
└── viewmodel/
    └── MainViewModel.kt          # Central state management & business logic
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug / Meerkat or later
- Android SDK 35 (compileSdk 35, minSdk 26)
- JDK 17+

### Building the Project
Clone the repository and build via Gradle:

```bash
git clone https://github.com/your-username/SpeakSheet.git
cd SpeakSheet
gradle assembleDebug
```

### Running Tests
```bash
gradle :app:testDebugUnitTest
```

---

## 📄 License

This project is licensed under the Apache License 2.0. See the [LICENSE](LICENSE) file for details.
