# KTS Launcher 🚀

[![Release](https://img.shields.io/github/v/release/KTShihab/KTSLauncher?color=blue&label=Latest%20Release)](https://github.com/KTShihab/KTSLauncher/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0+-green.svg)](https://developer.android.com)
[![Accessibility](https://img.shields.io/badge/Accessibility-TalkBack%20Optimized-orange.svg)](#accessibility--talkback-features)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**KTS Launcher** is a fast, lightweight, and clutter-free Android launcher meticulously engineered for **visually impaired users, screen-reader (TalkBack) users, and minimalist power users**. 

It prioritizes high tactile control, seamless multi-touch gestures, intelligent audio separation, and rich accessibility announcements while keeping resource usage to a minimum.

---

## ✨ Key Features

### 🎧 1. Accessible Audio Routing (Audio Separation)
Designed specifically to solve a common pain point for screen-reader users:
- **Separated Audio Streams**: Route TalkBack / TTS speech to your Bluetooth headset while allowing media playback (music, podcasts, videos) to play through the phone speaker.
- **One-Touch Toggle**: Easily turn audio separation on or off via a launcher button or a custom multi-touch gesture.
- **Smart Re-routing**: Seamlessly detects Bluetooth headset connections and keeps accessibility audio crisp and uninterrupted.

---

### 👆 2. Advanced Multi-Touch Gesture System
Navigate your phone without even looking at the screen:
- **1-Finger Gestures**: Swipe up, swipe down.
- **2-Finger Gestures**: Swipe up, down, left, right; single tap, double tap, triple tap.
- **3-Finger Gestures**: Swipe up, down, left, right; single tap, double tap, triple tap.
- **4-Finger Gestures**: Swipe up, down, left, right; single tap, double tap, triple tap.
- **Custom Bindings**: Map any gesture to launch favorite apps or trigger built-in actions:
  - Universal Search
  - App Drawer
  - Quick Settings / Notifications shade
  - Next / Previous Home Screen page
  - Launcher Settings
  - Audio Routing Toggle
  - In-App Update Check

---

### 🔊 3. Accessibility & TalkBack First
- **Detailed Announcements**: Real-time voice feedback on app selection, page changes, grid positions, and dialog actions.
- **Custom Speech Levels**: Choose between *Verbose*, *Balanced*, or *Minimal* voice feedback.
- **Row/Column Announcement**: Option to announce exact item position (e.g., *"Row 2, Column 1"*).
- **Speaking Order Customization**: Choose between row-first or column-first order.
- **Haptic Feedback**: Subtle vibration cues for swipes, taps, and page switches.
- **Bilingual Interface**: Full native support for **Malayalam (മലയാളം)** and **English**.

---

### 📐 4. Flexible Grid & Layout
- **Custom Grid Dimensions**: Adjust row count (1 to 10) and column count (1 to 8) to match your screen size and tactile preference.
- **Grid Alignment**: Align items to the Top, Center, or Bottom of the screen.
- **Multi-Page Support**: Add multiple clean home screens with easy navigation.
- **Customizable Appearance**: Toggle app label visibility and adjust icon sizes for optimal touch targets.

---

### 🎨 5. Themes & High Contrast
- **Accessible Color Presets**: High-contrast themes, AMOLED pure black, dark, and light styles.
- **Granular Color Customization**: Set custom colors for background, app cards, text, accents, and the app drawer.
- **Reduced Eye Strain**: Designed with optimal legibility and contrast ratios.

---

### 🔍 6. Universal Search & App Drawer
- **Quick Universal Search**: Search through installed apps, device contacts, and web directly from one search bar.
- **Alphabetical App Drawer**: Easy-to-browse list with tactile index, search filter, and instant launching.

---

### 💾 7. Backup & Restore
- **Export Configuration**: Save your entire launcher setup, including grid layouts, themes, and gesture mappings, to a portable JSON backup file.
- **Instant Restore**: Restore your preferred configuration on any device in seconds.

---

### 🚀 8. In-App Updates via GitHub
- **Direct GitHub Releases Integration**: Automatically checks for updates in the background or on-demand from settings.
- **One-Click Installation**: Download new releases directly within the app without depending on the Google Play Store.

---

## 📥 Installation

1. Go to the [**Latest Releases**](https://github.com/KTShihab/KTSLauncher/releases/latest) page.
2. Download the latest `KTSLauncher.apk` file.
3. Open the downloaded APK on your Android device and tap **Install** (allow installation from unknown sources if prompted).
4. Press the **Home** button on your device and select **KTS Launcher**, then choose **"Always"** to set it as your default home app.

---

## 🔒 Permissions Used

KTS Launcher requests only the permissions necessary to provide its core features:

| Permission | Purpose |
| :--- | :--- |
| `MODIFY_AUDIO_SETTINGS` | Required for managing audio stream routing and separation. |
| `BLUETOOTH_CONNECT` | Required on Android 12+ to detect connected Bluetooth headsets for audio routing. |
| `QUERY_ALL_PACKAGES` | Required to display installed applications in the app drawer and home grid. |
| `READ_CONTACTS` | Optional; used exclusively by Universal Search to find contacts locally on your phone. |
| `INTERNET` | Used solely for checking and downloading app updates via the GitHub API. |
| `REQUEST_INSTALL_PACKAGES` | Allows the launcher to prompt package installation when an update is downloaded. |

---

## 🛠️ Built With

- **Language**: Java
- **Target SDK**: Android 14 / 15 (API 34 / 35)
- **Minimum SDK**: Android 8.0 Oreo (API 26)
- **Architecture**: Lightweight Native Android (Zero heavy third-party tracking or bloated frameworks)

---

## 🤝 Feedback & Contributions

Contributions, bug reports, and feature requests are warmly welcomed!
- Open an [Issue](https://github.com/KTShihab/KTSLauncher/issues) to report a bug or request a feature.
- Submit a Pull Request if you'd like to contribute improvements.

---

## 👤 Author

Developed with ❤️ by **[KTShihab](https://github.com/KTShihab)**
