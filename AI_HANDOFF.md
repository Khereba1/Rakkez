# Rakkez: handoff notes for an AI assistant

Paste this file together with the project zip when asking an AI to change the app.
The family-wide rules are in STANDARD.md (ask the owner to attach it if missing).

## What this is
Rakkez (ركّز) is an offline productivity / anti-distraction app: To-do list, Pomodoro timer with several presets and custom lengths,
daily focus total + goal + 7-day chart, a "parking lot" for distracting thoughts, and two home-screen widgets.
After every finished focus session a dialog asks whether to continue with a few more minutes (+5/+10/+15/+20 or any number), take a break, or stop.
Android app, Kotlin + Jetpack Compose Material3. Source package `com.khereba1.app`, applicationId `com.khereba1.rakkez`.
minSdk 29, compileSdk 35. UI languages: Arabic (Egyptian wording) + English, in-app switch (system/ar/en), RTL supported. Developer name: Khereba1.
No INTERNET permission unless the owner asked for it.

## How it is built (IMPORTANT)
The owner has only a phone. There is NO local build. Code is pushed from Termux to GitHub and GitHub Actions
(.github/workflows/build.yml) builds the APK with Gradle 8.10.2 (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01).
You cannot compile: be careful and conservative. A mistake shows up only as a failed Actions run.
- Avoid Kotlin JVM signature clashes (`var x` with `private set` plus `fun setX(...)` breaks the build).
- Every new string needs an entry in every language file the app has (values/strings.xml, and values-ar/strings.xml if Arabic exists).
- Do not bump library versions without checking that the exact version exists.

## Files (app/src/main/java/com/khereba1/app)
- App.kt: Application (starts AppLog + notification channel), Foreground flag, AppLog, LocaleHelper, helpers `tr`, `dateLocale`, `fmtMinutes`
- Model.kt: data classes Task / FocusSession / Idea / Settings, `dayStart`, and `Store` (all persistence = JSON inside SharedPreferences, no DB)
- Engine.kt: timer state machine (`TimerState`, `Engine`), phases PH_IDLE/FOCUS/SHORT/LONG/ASK; time is absolute (endAt) so it survives process death;
  exact alarm via AlarmManager -> `TimerReceiver`; `Alerts` (notification channel, notification, in-app sound/vibration)
- Widgets.kt: `Widgets` (RemoteViews builders + update), `TasksWidget` (4x3, tap circle = complete task), `FocusWidget` (2x2, live Chronometer, start/pause button)
- MainViewModel.kt: UI state (tasks, sessions, ideas, settings, timer), 500 ms ticker only while the activity is started
- MainActivity.kt: activity (language wrap, notification permission asked on first Start, widget deep links via extras "tab"/"add"), AppRoot, NavBar, AskDialog host
- Theme.kt: "ink and saffron" colors (amber = focus, mint = break), light + dark
- UiParts.kt: hand-drawn checkbox/play button, Pill, Stepper, DotRing (60-tick timer face)
- TasksScreen.kt / FocusScreen.kt: tabs 'المهام' and 'تركيز' (AskDialog, presets 15/25/45 and hold-to-repeat stepper live in FocusScreen.kt)
- TodayScreen.kt / SettingsScreen.kt: overlays opened from the Focus footer / the gear button (SettingsScreen.kt also has RestoreDialog and the backup UI)
- LedgerModel.kt / LedgerScreens.kt: generic 'حسابات' feature (ledgers -> tables/columns, summary lines, batches with settle/close, copy-as-text); data in files/ledger.json
- NotesModel.kt / NotesMedia.kt / NotesScreen.kt: 'مفكرة' (notes): notes.json + files/media; Inbox + collections + automatic type filters (text/links/photos/audio/video) + search + pin/archive; photo/video through the phone camera app (FileProvider, no CAMERA permission), voice via MediaRecorder (RECORD_AUDIO asked at runtime)
- ShareReceiver.kt: share target (saves to Inbox without opening the app); NotesWidget.kt: quick-capture home widget (text / voice / photo / video, opens MainActivity with extra "note")
- Backup.kt (now also notes text and notes media, chosen separately): selective backup zip to Download/Rakkez/backups via MediaStore, restore via file picker, optional daily auto backup on app open
- Navigation: bottom bar tasks | focus | center + (context-aware, MainViewModel.plusRequest) | notes (placeholder until the notes phase) | accounts
- res/layout/widget_*.xml, res/xml/widget_*_info.xml, res/drawable (icon, widget shapes), values + values-ar strings (every string exists in both)

## Design rules used in this app
- No icon library (no material-icons): icons are drawn with Canvas or plain glyphs. No Room/ksp/kapt. No experimental Compose APIs.
- All numbers are formatted as Western digits and passed to strings as `%1$s` strings (avoid `%d`, which gives Arabic digits in ar).
- Timer: never keep a countdown in memory only; always persist `endAt` and call `Engine.sync(context)` (idempotent) from UI/receiver/widget.
- Permissions: POST_NOTIFICATIONS, VIBRATE, USE_EXACT_ALARM, SCHEDULE_EXACT_ALARM (max SDK 32). No INTERNET.
- `lint { checkReleaseBuilds = false }` was added in app/build.gradle.kts so lint can't fail the build.
(Keep this list updated when you add files.)

## REQUIRED answer format (the owner has only a phone + Termux)
1. Briefly say what you changed and why (plain language; the owner writes Egyptian Arabic mixed with English terms, reply in the same).
2. Deliver ONE zip containing only the changed/new files, complete (never snippets or diffs), with paths relative to the project root.
   Name it `fixN.zip` (fix1, fix2, ...). If you cannot create a zip, give each file in full in its own code block with its path, and say so.
3. Raise `versionCode` (+1) and `versionName` in `app/build.gradle.kts` for every functional change, and include that file.
4. Give the install command, ready to copy, with the real folder name:
   `cd ~/rakkez && unzip -o "$(ls -t ~/storage/downloads/fixN*.zip | head -1)" -d . && git add . && git commit -m "short message" && git push`
   (GitHub repo: Khereba1/Rakkez, APK: github.com/Khereba1/Rakkez/releases/tag/latest)
5. Say honestly that nothing was compiled or tested, and what to check first on the phone.
6. If the build fails, the owner sends lines containing `e: ` from the Actions log; fix only those.

## Signing and sharing
The stable signing key lives in GitHub Secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD).
Never print, commit or ask for them, and never ask for the backup folder.
`bash share_zip.sh` builds a clean `<folder>-for-AI.zip` (no keys, env files, build output or APKs) for sharing.

## fix13 (v14 / 2.3): AudioPlayer has a seek bar (Slider, forced LTR), pause keeps position, time pos/total.

## fix18 (v19 / 2.8): viewer video uses custom controls (center play/pause, SeekBar, Latin digits, auto-hide), speed x0.5..x2 in the viewer ⋮ menu for audio+video (resets on close), SeekBar composable replaces Slider.

## fix16 (v17 / 2.6): list audio cards are non-seekable (play + thin progress while playing), seek bar only in viewer/editor; smooth progress via withFrameNanos; visibleEntries memoized; group card = tiles for photos/videos + compact rows for voice/files.

## fix12 (v13 / 2.2): RkIcons.kt (drawn icons, no emoji), selection bar w/ units logic, VoiceSheet(visible,onClose,onSaved) with pause/resume used by list and editor, TextDirection.Content in editor, title lineHeight/cursor fix, nav items 50dp concentric, typography lineHeightStyle center.

## fix11 (v12 / 2.1): missed-days prompt for build habits (Habit.res = settled-through day; answer kept/slipped), 9pm HabitReminder (AlarmManager inexact, rescheduled on app open, not on reboot), settings switch.

## fix10 (v11 / 2.0): habits redesign (build 21d / quit 90d, today-only tick, relapse, achieved section), courses new-first + finished section, VoiceSheet drawer, per-kind note cards (photo/video cover, audio player row, file row, group collage).

## fix9 (v10 / 1.9): settings order, shorter backup labels, notes "+" is now a bottom drawer (NotesSheet.kt) with device photo/video grid (READ_MEDIA_* permission, multi-select, "as group"), camera/video/voice/text/file tiles.

## fix8 (v9 / 1.8) additions
- Ledger: single `ColumnDialog` (add + edit; kind change, default, sum, A×B refs); `vm.editColumn(..., kind, sumIt, refA, refB, def)`. Kind can't become text/calc while referenced.
- Notes: text wins classification; media-only notes are standalone items (filters Photos/Audio/Video/Files). `Note.groupId` ("grp") groups items; long-press = selection (group/ungroup/move/delete). Share target makes one item per file. `NoteViewer.kt` = in-app pager viewer (image double-tap zoom, VideoView, audio, ⋮ open-with/share/ungroup). Center "+" on Notes opens an add menu (text/voice/photo/video/system photo picker/file).
- Tasks tab has sub-tabs Tasks | Habits | Courses (`Trackers.kt`, `TrackersScreen.kt`, prefs "habits"/"courses", in basic backup). Settings: theme (prefs "theme": system/light/dark).

## fix22 (versionCode 23)
- Video viewer: controls now sit on the video frame itself (BoxWithConstraints sized by video ratio), accent-colored like audio; times forced LTR.
- Alert sound: `Alerts` plays on USAGE_ALARM (audible on silent); one notification channel per sound (`timer_done_<key>`); sounds = res/raw snd_bell/chime/beep/marimba + system + custom (ringtone picker). Settings panel + `vm.changeAlertSound`.

## fix23 (versionCode 24)
- Video viewer: live scrub preview (exact seek, one at a time via OnSeekCompleteListener, pause while dragging); play/pause icon without circle, smaller; controls don't auto-hide while dragging.

## fix24 (versionCode 25)
- Default alert sound = marimba (pref key alert_sound2). Habit reminder channel `habit_remind_<tag>` uses the same sound with USAGE_NOTIFICATION (follows silent mode); only focus/break end uses USAGE_ALARM.
- Video viewer: play icon in accent color; seek bar slightly lower.
