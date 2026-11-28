# Tidy — Known Issues & Fixes Log

Tracks UX issues, edge cases, and fixes across versions.

---

## Resolved in `v1.7.3`

### 1. [Fixed] AI Plan Import List Name & Leading Emoji Matching
- **Location:** `app/src/main/java/com/lokesh/tidy/data/Repository.kt` (`splitLeadingEmojiAndName`, `importAiPlan`, `buildAiPrompt`)
- **Resolution:**
  - `buildAiPrompt` now lists clean category names without prepending emojis into the name string.
  - `importAiPlan` splits any leading emoji prefix from the list name (`splitLeadingEmojiAndName`) so `"🌿 Personal"` and `"Personal"` both match the existing `🌿 Personal` list instead of creating a duplicate `📌 🌿 Personal` list, and new lists with leading emojis (e.g. `"🍎 Diet"`) extract `"🍎"` into `Category.emoji` and `"Diet"` into `Category.name`.

### 2. [Fixed] Batch List Selector & Rename Option in AI Import Preview
- **Location:** `app/src/main/java/com/lokesh/tidy/ui/screens/SettingsScreen.kt` (`AiPlanImportSheet`)
- **Resolution:**
  - When all tasks in an imported AI plan share the same list name, a **`LIST FOR ALL TASKS`** pill selector appears above the tasks preview so the user can reassign the entire batch to `Inbox`, any existing list, or edit the AI's suggested new list name/emoji before importing.

### 3. [Fixed] `done/total` Task Completion Count & Drag-to-Reorder in `Settings → Lists`
- **Location:** `app/src/main/java/com/lokesh/tidy/ui/screens/SettingsScreen.kt`, `Repository.kt`, `Model.kt`
- **Resolution:**
  - Each list row in `Settings → Lists` now displays a `done/total` badge (`x/y`) beside the list name.
  - Added long-press vertical drag-to-reorder on the list rows inside `Settings → Lists` (`vm.reorderCategories`), automatically updating the order of the horizontal capsules on `Today`, the `By list` progress card in `Insights`, and the `Task Editor`.

### 4. [Fixed] Smart Caps & `"Completed today"` Window on `Today` Screen
- **Location:** `app/src/main/java/com/lokesh/tidy/ui/screens/TodayScreen.kt`
- **Resolution:**
  - `Overdue` and `Upcoming` (next 7 days) now show up to 3 tasks by default with an inline `Show all (N)` / `Show less` toggle in the section header.
  - `Completed today` on the Today screen now shows only tasks completed today (while preserving 100% permanent database history of all completed tasks for Insights, Calendar, and Search).

---

## Resolved in `v1.7.1`

### 1. [Fixed] AI Plan Prompt Guidance for Smart Structural Decisions & Mobile-Friendly Lengths
- **Location:** `app/src/main/java/com/lokesh/tidy/data/Repository.kt` (`Repository.buildAiPrompt`)
- **Resolution:**
  - Updated the copied AI prompt so the AI knows it has full creative and structural flexibility across Tidy's features (`tasks`, `subtasks`, `details`, `dates`, `times`, `reminders`, `repeat`, `priorities`, `lists`, `text notes`, and `checklist notes`) and should choose the best structure for the user's specific request, context, and scenario.
  - Enforced mobile-screen length guidelines: **task titles 2–7 words (under ~45 chars)**, **subtasks under 15–16 words**, and **note titles 1–5 words (under ~32 chars)**.

### 2. [Fixed] Single-Line Horizontal Subtask Cursor-Handle Drag Auto-Scrolling in Task Editor
- **Location:** `app/src/main/java/com/lokesh/tidy/ui/screens/TaskEditor.kt` (`SingleLineSubtaskField`)
- **Resolution:**
  - Migrated subtask fields to single-line `BasicTextField(state = TextFieldState, lineLimits = TextFieldLineLimits.SingleLine, scrollState = scrollState)` with edge-pan and continuous auto-scrolling when dragging the cursor teardrop handle (`|`) toward the right or left end of the subtask box.

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
