# AI Transition: Remaining Tasks and System Context

## Context
The Password Manager and Power-User features for Ziaistan Keyboard have been significantly overhauled. Most requirements are met, including sequential autofill, custom icons, favicons, multi-select, and no-auth Quick Notes.

## Remaining Issues to Fix

### 1. Termux Commands JSON Loading Bug
- **Status:** Partially fixed. The parser in `TermuxCommandsController.java` has been improved to handle arrays, objects, and root `commands` keys.
- **Problem:** Some users report that after editing `termux_commands.json` via the Manual File Editor, the command palette still only shows the default 3 fallback items.
- **Task:** Investigate why the file is either not being found or failing to parse after a manual edit. Check for character encoding issues (BOM), illegal JSON syntax created by the editor, or permission issues between internal/external storage.

### 2. Automatic Icon Population (Favicons)
- **Status:** `FaviconHelper` and UI integration implemented.
- **Task:** Implement a background worker (e.g., using `androidx.work.WorkManager` or a simple one-time thread) that scans all `PasswordEntry` items. If an entry has a valid URL but NO `customIcon` and no cached favicon, it should fetch the favicon from Google's service and save it to the `customIcon` field in the database. This ensures the vault becomes visually rich automatically without manual assignment.

### 3. Termux Command Palette Grouping
- **Task:** Ensure that grouping by tags in `TermuxCommandsController` is case-insensitive and that items without tags are grouped under a default "General" category consistently.

## Technical Details for Next Agent
- **Database:** Room (v4). Schema includes `passwords`, `secure_notes`, and `pending_notes`.
- **Autofill Logic:** Sequential pasting uses `postDelayed` sequence in `KeyEventHandler.autofillReceiver`.
- **Icon Helper:** `FaviconHelper` handles the Google API calls and basic in-memory caching.
- **Navigation:** `PasswordManagerActivity` uses an `isLaunchingExternalActivity` flag to manage lifecycle during file/image selection.

## Next Step Prompt
"Fix the Termux Commands JSON parsing issue where user-defined commands from `termux_commands.json` are not loading correctly. Additionally, implement a background sync process that automatically populates missing icons for website password entries using the existing `FaviconHelper`."
