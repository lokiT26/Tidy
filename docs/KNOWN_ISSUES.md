# Tidy — Known Issues & Fixes Log

Tracks UX issues, edge cases, and fixes across versions.

---

## Resolved in `v1.6.1`

### 1. [Fixed] Distinction Between `Add time` (When) and `Remind me` in Task Editor
- **Location:** `app/src/main/java/com/lokesh/tidy/ui/screens/TaskEditor.kt` & `AppViewModel.kt` (`Dates.reminderPreview`)
- **Resolution:**
  - When a task is All-day (`dueTime == null`), **Remind me** no longer shows ambiguous relative offsets (`15 min`, `1 hour`, `1 day`); instead it offers `Off`, `Remind at 9:00 AM`, or `Pick time…`.
  - Once a due time is set (`Due · 4:30 PM`), **Remind me** shows the offset chips (`Off`, `At due time`, `15 min before`, `1 hour before`, `1 day before`, `Custom…`) along with a live notification preview in the section header (`🔔 Notifies Today at 4:15 PM`).

### 2. [Fixed] Unified `Days + Hours + Minutes` Custom Reminder Interface
- **Location:** `app/src/main/java/com/lokesh/tidy/ui/screens/TaskEditor.kt` (`DurationStepperColumn`) & `AppViewModel.kt` (`Dates.reminderLabel`)
- **Resolution:**
  - Replaced the single number input + separate unit chips with a unified 3-column **`Days` / `Hours` / `Minutes`** row (supporting both `+`/`−` steppers and direct number entry) plus a live preview card showing the exact combined offset and notification timestamp.
  - Updated `Dates.reminderLabel()` to format composite offsets (e.g. `1h 30m before`, `1d 2h 15m before`).

### 3. [Fixed] Checklist Note: Backspace on an Empty Item Jumps to End of Previous Item
- **Location:** `app/src/main/java/com/lokesh/tidy/ui/screens/NotesScreen.kt` (`EditableCheckItem`, `applyChecklistValueChange`)
- **Resolution:**
  - Added per-item `FocusRequester` and `TextFieldValue` tracking with a leading zero-width sentinel (`\u200B`) plus hardware `Key.Backspace` handling.
  - Pressing Backspace on an empty item (or at the start of an item) deletes that item, transfers focus immediately to `i - 1`, and positions the cursor at the very end of the previous item's last word—allowing a single long-press of Backspace to continuously erase across multiple items.

### 4. [Fixed] Checklist Note: Enter on an Empty Line (Double-Enter) Moves to Next/New Item Below
- **Location:** `app/src/main/java/com/lokesh/tidy/ui/screens/NotesScreen.kt` (`applyChecklistValueChange`)
- **Resolution:**
  - Single Enter at the end of a non-empty line continues to start a new line within the current checklist item.
  - Pressing Enter when the current line is blank strips the blank line and automatically inserts/focuses the checklist item below (`i + 1`).

### 5. [Fixed] Live Device Home-Screen Wallpaper in Widget Customiser Preview
- **Location:** `app/src/main/java/com/lokesh/tidy/widget/WidgetConfigActivity.kt`, `AndroidManifest.xml`, `res/values/themes.xml`
- **Resolution:**
  - Added `Theme.Tidy.Wallpaper` (`android:windowShowWallpaper="true"`, transparent window background) and `WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER` on `WidgetConfigActivity`.
  - Used `clipPath(..., ClipOp.Difference)` on `AmbientBackground` around the `30.dp` rounded preview card so the Android system renders the user's real live home-screen wallpaper directly behind the widget preview with zero permissions required.
