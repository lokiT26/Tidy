# Tidy — Minimal To-Dos, Notes & Widgets for Android

**Tidy** is a fast, 100% offline Android To-Do and Notes app built with **Kotlin**, **Jetpack Compose**, **Room**, and **Jetpack Glance**.

## Features

- **Today** — Greeting header, progress ring & streak card, category filter chips, swipe right to complete/reopen, 2-step swipe left to delete with 8-second swipeable Undo, long-press drag-to-reorder within sections & dates, expandable subtasks, and light/dark-adaptive confetti.
- **Task Editor** — Due dates & times, exact reminders (plus custom offsets), recurring schedules (`Daily`, `Weekdays`, `Weekly`, `Monthly`, `Yearly`), priorities, custom lists, and inline editable subtasks.
- **Calendar** — Expandable week/month grid with horizontal swipe navigation and vertical timeline.
- **Notes** — Google Keep-style staggered masonry grid, pinned notes, pastel colours, checklists with multi-line item support, per-section box height presets (`Auto`, `Small`, `Medium`, `Large`), colour & type filtering, sorting, and real Gaussian blur to hide sensitive notes.
- **Insights** — Streaks, weekly completion chart, 18-week activity heatmap, and per-list progress.
- **5 Customisable Home-Screen Widgets** — To-Do List, responsive Today Progress (`3×1`, `2×1`, `1×1`), Sticky Note (with interactive checklists), interactive Week + Tasks, and 1×1 Quick Add — each with live-preview customisation for background style, opacity, corner radius, theme (System/Light/Dark/AMOLED), accent palette, font size & style, and density.
- **Private & Offline** — Zero internet permission, no account, and full JSON Export / Restore backups.

## Building from Source

```bash
./gradlew testDebugUnitTest assembleRelease
```

To sign release builds with your own key, create a local `keystore.properties` (never commit it):

```properties
storeFile=tidy-release.jks
storePassword=your-store-password
keyAlias=tidy
keyPassword=your-key-password
```

Without `keystore.properties`, `assembleRelease` falls back to the local Android debug keystore.
