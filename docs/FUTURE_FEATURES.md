# Tidy — Future Features & Roadmap

Ideas and features planned for future iterations of **Tidy**, designed to keep the app **100% offline, zero-permission, minimal, and fast**.

---

## 1. Structured AI Plan Import ("Bring Your Own AI")

### Problem
When planning a month, week, study schedule, or project with an external AI assistant (ChatGPT, Claude, Gemini, etc.), manually entering dozens of generated tasks, dates, times, reminders, repeat rules, and subtasks into Tidy is repetitive and slow.

### Why Tidy's Raw Backup JSON Cannot Be Used Directly
- **Destructive restore:** `Repository.importJson()` (`Restore backup`) wipes all existing tasks, notes, and lists before restoring. AI plan import must **merge/append** into existing data.
- **Epoch math errors in LLMs:** Tidy stores `dueDate` as epoch days (e.g. `20780`) and `dueTime` as minutes from midnight (e.g. `990`). LLMs frequently miscalculate epoch-day integers.
- **Database IDs vs. names:** Backup JSON references `categoryId` (`1`, `2`) instead of human-readable list names (`"Work"`, `"Health"`).

### Proposed Architecture & Flow
1. **Placement (Minimalism-First):**
   - No new icons or buttons on `Today`, `Calendar`, `Notes`, or `Insights`.
   - Single row in **Settings → Backup**: **`Import AI plan`**.
2. **1-Tap `Copy AI prompt`:**
   - Copies a compact system instruction pre-filled with **today's ISO date**, the user's **existing list names & emojis**, and the **human-readable Tidy JSON schema**.
3. **Human/AI-Friendly JSON Schema:**
   ```json
   {
     "tasks": [
       {
         "title": "Submit project report",
         "notes": "Include Q4 metrics",
         "date": "2026-11-24",
         "time": "16:30",
         "reminderMinutesBefore": 15,
         "priority": "high",
         "list": "Work",
         "repeat": "NONE",
         "subtasks": ["Draft summary", "Export PDF"]
       }
     ],
     "notes": [
       {
         "title": "Weekly Focus",
         "checklist": true,
         "color": 1,
         "pinned": false,
         "items": ["Review lecture notes", "Practice problems"]
       }
     ]
   }
   ```
4. **Import via Clipboard Paste or `.json` File:**
   - Supports **1-tap Paste from Clipboard** (fastest on mobile AI apps) as well as **Pick `.json` file**.
   - **Confirmation preview before adding:** Displays how many tasks, reminders, lists, and notes were parsed before inserting them into Room and scheduling alarms via `Reminders.schedule()`.
