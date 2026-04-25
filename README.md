# SyncList 🚀
**Powered by Gemini AI Assistance**

SyncList is a high-performance, professional task management application built with **Kotlin Multiplatform** and **Jetpack Compose**. It features real-time Firebase synchronization, a robust Undo/Redo "Time Machine," and an industry-grade Color Studio for deep UI personalization.

---

## 🌟 Key Features

### 🔄 Real-time Synchronization
- Powered by **Firebase Firestore** with custom `position` logic for stable, manual list ordering.
- Instant updates across all connected devices (Android, iOS, Web).

### ⏳ Advanced Undo/Redo System
- A comprehensive "Time Machine" that tracks every action—from checkbox toggles to item reordering.
- Uses **Explicit State Snapshots** to ensure perfect restoration even with network latency.

### 🎨 Color Studio Pro
- **Dynamic Theming**: Personalize both the Top Bar and the List Background.
- **High-Precision Control**: Features a 10-color preset grid, a vertical Brightness (Value) slider, and Hex code input.
- **Smart Contrast Engine**: Text and icons automatically flip between Black and White based on background luminance for 100% readability.

### ⚡ High-Performance Reordering
- **Instant Snap**: Teleporting item movement for a zero-latency feel.
- **Pro Autoscroll**: Continuous, velocity-scaled scrolling when dragging items to viewport edges.

---

## 🤖 AI Disclosure & Development Process

### The AI Integration
This project utilized **Gemini AI** as a sophisticated co-pilot to accelerate the development lifecycle. AI was leveraged for:
- Initial scaffolding and boilerplate generation.
- Rapid prototyping of complex mathematical UI components (like the Color Wheel).
- Continuous debugging and performance optimization.

### Human-in-the-Loop Architecture
While AI assisted in high-velocity output, the **Human Lead Architect** acted as the primary auditor, editor, and logical engineer. All core logic, security protocols, and UI refinements were manually inspected and polished to ensure production-grade quality.

#### 🛠️ Examples of Human-Led Refinement:
1.  **Explicit State Snapshots**: Manually refactored the Undo system to use fixed state values rather than relative toggles, fixing a critical synchronization bug.
2.  **Scroll-Aware Reordering**: Engineered a manual offset compensation logic to keep items "glued" to the finger during high-speed autoscrolling.
3.  **Luminance Engine**: Specifically requested and audited the dynamic contrast logic to ensure accessibility across the entire color spectrum.
4.  **Two-Tier Dashboard**: Redesigned the Top Bar hierarchy to prioritize title visibility and ergonomic touch targets.
5.  **Rigid Palette Geometry**: Diagnosed and fixed an oblong rendering bug in the custom color palette by enforcing strict aspect ratio constraints.
6.  **Gesture Loop Refactor**: Rewrote unstable pointer input blocks into a continuous `awaitEachGesture` loop to fix "sticky" sliders.
7.  **Auto-Focus Entry**: Optimized the UX by manually integrating focus requesters and sentence-case keyboards into all input dialogs.

---

## 🛠️ Tech Stack
- **Framework**: Compose Multiplatform
- **Language**: Kotlin
- **Backend**: Firebase Firestore
- **State Management**: Kotlin Flow & ViewModel
- **AI Co-pilot**: Gemini AI

---

### Build and Run Android Application
- Windows: `.\gradlew.bat :composeApp:assembleDebug`
- macOS/Linux: `./gradlew :composeApp:assembleDebug`

---
*SyncList is a showcase of AI-Literacy in modern software engineering—using advanced tools to achieve maximum velocity without compromising on architectural integrity.*