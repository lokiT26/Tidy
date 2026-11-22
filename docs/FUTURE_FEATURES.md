# Tidy — Features Roadmap & New Additions (`v1.7`)

Features designed to make **Tidy** faster and more useful while keeping it **100% offline, zero-permission, and minimal**.

---

## 1. Structured AI Plan Import ("Bring Your Own AI" — Tasks + Notes + Undo)
- **Single Adaptive AI Prompt (`Copy AI prompt`):**
  - Pre-filled with **today's date**, the user's **existing list names**, and a unified JSON schema supporting both **`tasks`** (with dates, times, reminder offsets, repeat rules, priorities, lists, and subtasks) and **`notes`** (regular notes or checklist notes, pastel colors `0..7`, pin & hidden flags).
  - Includes strict anti-bloat instructions so the AI generates *only* what the user asks for (tasks only, notes only, or both) without inventing filler items.
- **Clipboard Paste, File Import & Preview:**
  - Accepts raw JSON or AI responses wrapped in ` ```json ... ``` ` code blocks via **Paste from clipboard**, **Pick `.json` file**, or **Android Share Sheet**.
  - Shows a confirmation preview listing the parsed tasks and notes before merging them into Tidy.
- **1-Tap `Undo last import`:**
  - Records the exact IDs of the last imported batch (`tasks`, `notes`, and any newly created `categories`) so the user can roll back the entire AI import in one tap if they want to tweak their prompt.

---

## 2. "Share to Tidy" (Android System Share Sheet)
- Registers Tidy as a share target for `text/plain` (`Intent.ACTION_SEND`):
  - **AI Plan JSON shared from ChatGPT / Gemini / Claude:** Automatically opens Tidy's **Import AI Plan preview** sheet.
  - **Regular text shared from any app:** Opens a compact prompt letting the user turn the shared text into a **New Task** or **New Note** in one tap.

---

## 3. Convert Note → Task (Unobtrusive in Note Editor `⋮` Menu)
- Tucked inside a subtle `⋮` overflow menu in the Note Editor top bar so it never clutters daily note-taking:
  - Converts a **Checklist Note** into a **Task** with all checklist items automatically mapped to **Subtasks**.
  - Converts a **Text Note** into a **Task** with the note body placed into the task's **Details**.
  - Opens `TaskEditorSheet` pre-filled with the converted task and subtasks so the user can pick a due date/reminder before saving.

---

## 4. Smarter Reminder Notifications (`+1 hr` Action + Subtasks Preview)
- Expands reminder notifications with:
  - **3 Action Buttons:** `✓ Done`, `Snooze 10m`, and `+1 hour`.
  - **Subtasks in Expanded Notification:** Shows the task's subtasks (`☐` / `☑`) directly inside the notification body so steps are visible from the lock screen without opening the app.

---

## 5. Home-Screen App Icon Shortcuts (Long-Press Tidy Icon)
- Native Android static launcher shortcuts (`res/xml/shortcuts.xml`) when long-pressing the Tidy app icon:
  - **`+ New Task`**
  - **`+ New Note`**
  - **`+ Checklist`**
