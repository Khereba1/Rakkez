package com.khereba1.app

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val OVERLAY_NONE = 0
const val OVERLAY_SETTINGS = 1
const val OVERLAY_TODAY = 2

// values for MainViewModel.plusRequest (what the center "+" asked for)
const val PLUS_TASK = 1
const val PLUS_IDEA_FOCUS = 2
const val PLUS_IDEA_NOTES = 3
const val PLUS_LEDGER = 4

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app: Application = application

    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set
    var sessions by mutableStateOf<List<FocusSession>>(emptyList())
        private set
    var ideas by mutableStateOf<List<Idea>>(emptyList())
        private set
    var settings by mutableStateOf(Settings())
        private set
    var timer by mutableStateOf(TimerState())
        private set
    var nowMs by mutableStateOf(System.currentTimeMillis())
        private set
    var tab by mutableStateOf(0)
        private set
    var overlay by mutableStateOf(OVERLAY_NONE)
        private set
    var plusRequest by mutableStateOf(0)
        private set

    var ledgers by mutableStateOf<List<Ledger>>(emptyList())
        private set
    var openLedgerId by mutableStateOf(0L)
        private set
    var openBatchId by mutableStateOf(0L)
        private set

    var backupMsg by mutableStateOf("")
        private set
    var backupBusy by mutableStateOf(false)
        private set
    var restoreUri by mutableStateOf<Uri?>(null)
        private set
    var restoreInfo by mutableStateOf<BackupInfo?>(null)
        private set

    var notes by mutableStateOf(NotesData(emptyList(), emptyList()))
        private set
    var noteQuery by mutableStateOf("")
        private set
    var noteType by mutableStateOf(NF_ALL)
        private set
    var noteScope by mutableStateOf(-1L)
        private set
    var editing by mutableStateOf<Note?>(null)
        private set
    var noteAction by mutableStateOf(0)
        private set
    private var editingNew = false
    private val sessionAdded = HashSet<Long>()
    private val removedOld = ArrayList<Media>()

    private var ticker: Job? = null

    init {
        // a task picked in an earlier run is not kept: every fresh start begins with "no specific task"
        val t0 = Engine.load(app)
        if (t0.phase == PH_IDLE && t0.taskId != 0L) Engine.selectTask(app, 0L)
        refreshAll(false)
        ledgers = LedgerStore.load(app)
        notes = NoteStore.load(app)
        viewModelScope.launch(Dispatchers.IO) { Backup.autoIfDue(app) }
    }

    // ---------------- lifecycle ----------------

    fun startTicking() {
        val ev = Engine.sync(app)
        if (ev != Engine.EV_NONE) Alerts.cancel(app)
        refreshAll(true)
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (true) {
                tick()
                delay(500L)
            }
        }
    }

    fun stopTicking() {
        ticker?.cancel()
        ticker = null
    }

    private fun tick() {
        if (!timer.running) return
        nowMs = System.currentTimeMillis()
        val ev = Engine.sync(app)
        if (ev != Engine.EV_NONE) {
            Alerts.ding(app)
            Alerts.cancel(app)
            refreshAll(true)
        }
    }

    private fun refreshAll(widgets: Boolean) {
        tasks = Store.loadTasks(app)
        sessions = Store.loadSessions(app)
        ideas = Store.loadIdeas(app)
        settings = Store.loadSettings(app)
        timer = Engine.load(app)
        nowMs = System.currentTimeMillis()
        if (widgets) Widgets.updateAll(app)
    }

    // ---------------- navigation ----------------

    fun openTab(index: Int) {
        if (editing != null) closeNote()
        tab = index
        overlay = OVERLAY_NONE
    }

    fun openOverlay(which: Int) {
        overlay = which
    }

    fun closeOverlay() {
        overlay = OVERLAY_NONE
    }

    fun requestPlus(code: Int) {
        plusRequest = code
    }

    fun consumePlus() {
        plusRequest = 0
    }

    // ---------------- tasks ----------------

    fun addTask(title: String) {
        val clean = title.trim().take(200)
        if (clean.isEmpty()) return
        Store.editTasks(app) { list -> list + Task(Store.newId(), clean, createdAt = System.currentTimeMillis()) }
        refreshAll(true)
    }

    fun editTask(id: Long, title: String, estimate: Int, planMin: Int, sessionMin: Int) {
        val clean = title.trim().take(200)
        if (clean.isEmpty()) return
        Store.editTasks(app) { list ->
            list.map { t -> if (t.id == id) t.copy(title = clean, estimate = estimate, planMin = planMin, sessionMin = sessionMin) else t }
        }
        refreshAll(true)
    }

    fun deleteTask(id: Long) {
        Store.editTasks(app) { list -> list.filter { t -> t.id != id } }
        if (timer.taskId == id) Engine.selectTask(app, 0L)
        refreshAll(true)
    }

    fun toggleTask(id: Long) {
        Store.toggleTask(app, id)
        refreshAll(true)
        val sel = tasks.firstOrNull { it.id == timer.taskId }
        if (sel != null && sel.done && timer.phase == PH_IDLE) {
            Engine.selectTask(app, 0L)
            refreshAll(true)
        }
    }

    fun selectTask(id: Long) {
        Engine.selectTask(app, id)
        refreshAll(true)
    }

    /** The play button on a task: select it, apply its own session length if it has one, and go to Focus. */
    fun playTask(id: Long) {
        Engine.selectTask(app, id)
        val t = tasks.firstOrNull { it.id == id }
        if (t != null && t.sessionMin > 0 && timer.phase == PH_IDLE) {
            Store.saveSettings(app, settings.copy(focusMin = t.sessionMin.coerceIn(1, 180)))
        }
        refreshAll(true)
        openTab(1)
    }

    // ---------------- timer ----------------

    fun startFocus() {
        Engine.startFocus(app, settings.focusMin, false)
        refreshAll(true)
    }

    fun startExtra(minutes: Int) {
        Engine.startFocus(app, minutes, true)
        refreshAll(true)
    }

    fun startBreak() {
        Engine.startBreak(app)
        refreshAll(true)
    }

    fun pause() {
        Engine.pause(app)
        refreshAll(true)
    }

    fun resume() {
        Engine.resume(app)
        refreshAll(true)
    }

    fun stop(keep: Boolean) {
        Engine.stop(app, keep)
        refreshAll(true)
    }

    fun finishAsk() {
        Engine.finishAsk(app)
        refreshAll(true)
    }

    // ---------------- timer settings (all editable from the Focus screen) ----------------

    fun adjustFocus(delta: Int) {
        val s = settings
        val v = (s.focusMin + delta).coerceIn(1, 180)
        if (v != s.focusMin) {
            Store.saveSettings(app, s.copy(focusMin = v))
            settings = Store.loadSettings(app)
        }
    }

    fun setFocusMinutes(v: Int) {
        Store.saveSettings(app, settings.copy(focusMin = v.coerceIn(1, 180)))
        settings = Store.loadSettings(app)
    }

    fun adjustShort(delta: Int) {
        val s = settings
        val v = (s.shortMin + delta).coerceIn(1, 60)
        if (v != s.shortMin) {
            Store.saveSettings(app, s.copy(shortMin = v))
            settings = Store.loadSettings(app)
        }
    }

    fun adjustLong(delta: Int) {
        val s = settings
        val v = (s.longMin + delta).coerceIn(1, 90)
        if (v != s.longMin) {
            Store.saveSettings(app, s.copy(longMin = v))
            settings = Store.loadSettings(app)
        }
    }

    fun adjustEvery(delta: Int) {
        val s = settings
        val v = (s.longEvery + delta).coerceIn(2, 8)
        if (v != s.longEvery) {
            Store.saveSettings(app, s.copy(longEvery = v))
            settings = Store.loadSettings(app)
        }
    }

    fun changeCycleMode(on: Boolean) {
        Store.saveSettings(app, settings.copy(cycleMode = on))
        settings = Store.loadSettings(app)
    }

    /** Back to 25 min, 5 / 15 min breaks, 4 sessions per cycle, single-session mode. */
    fun resetTimerDefaults() {
        val d = Settings()
        Store.saveSettings(
            app,
            settings.copy(focusMin = d.focusMin, shortMin = d.shortMin, longMin = d.longMin, longEvery = d.longEvery, cycleMode = d.cycleMode)
        )
        settings = Store.loadSettings(app)
    }

    fun applySettings(s: Settings) {
        Store.saveSettings(app, s)
        refreshAll(false)
    }

    fun changeLanguage(lang: String) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lang", lang).commit()
        Alerts.ensureChannel(app)
    }

    fun changeFont(key: String) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("font", key).commit()
    }

    // ---------------- parking lot ----------------

    fun addIdea(text: String) {
        val clean = text.trim().take(300)
        if (clean.isEmpty()) return
        Store.editIdeas(app) { list -> list + Idea(Store.newId(), clean, System.currentTimeMillis()) }
        refreshAll(false)
    }

    fun deleteIdea(id: Long) {
        Store.editIdeas(app) { list -> list.filter { i -> i.id != id } }
        refreshAll(false)
    }

    fun ideaToTask(id: Long) {
        val idea = ideas.firstOrNull { it.id == id } ?: return
        addTask(idea.text)
        deleteIdea(id)
    }

    fun todayFocusMs(): Long {
        val start = dayStart(System.currentTimeMillis())
        var sum = 0L
        for (s in sessions) {
            if (s.startAt >= start) sum += s.durationMs
        }
        return sum
    }

    // ---------------- ledgers ----------------

    private fun mutateLedger(id: Long, change: (Ledger) -> Ledger) {
        LedgerStore.update(app) { list -> list.map { l -> if (l.id == id) change(l) else l } }
        ledgers = LedgerStore.load(app)
    }

    private fun defaultBatchId(l: Ledger): Long {
        val open = l.batches.lastOrNull { it.closedAt == 0L }
        if (open != null) return open.id
        return l.batches.lastOrNull()?.id ?: 0L
    }

    fun openLedger(id: Long) {
        val l = ledgers.firstOrNull { it.id == id } ?: return
        openLedgerId = id
        openBatchId = defaultBatchId(l)
    }

    fun closeLedgerView() {
        openLedgerId = 0L
        openBatchId = 0L
    }

    fun selectBatch(id: Long) {
        openBatchId = id
    }

    fun createLedger(kind: Int, name: String) {
        val clean = name.trim().take(60)
        val finalName = if (clean.isEmpty()) LocaleHelper.wrap(app).getString(R.string.ledger_default_name) else clean
        val l = LedgerTemplates.create(app, kind, finalName)
        LedgerStore.update(app) { list -> list + l }
        ledgers = LedgerStore.load(app)
        openLedger(l.id)
    }

    fun deleteLedger(id: Long) {
        LedgerStore.update(app) { list -> list.filter { it.id != id } }
        ledgers = LedgerStore.load(app)
        closeLedgerView()
    }

    fun renameLedger(id: Long, name: String, unit: String, balanceLabel: String) {
        val n = name.trim().take(60)
        if (n.isEmpty()) return
        mutateLedger(id) { l -> l.copy(name = n, unit = unit.trim().take(8), balanceLabel = balanceLabel.trim().take(30)) }
    }

    fun saveEntry(ledgerId: Long, entryId: Long, tableId: Long, batchId: Long, date: Long, values: Map<Long, String>) {
        mutateLedger(ledgerId) { l ->
            if (entryId == 0L) {
                l.copy(entries = l.entries + Entry(Store.newId(), tableId, batchId, date, values))
            } else {
                l.copy(entries = l.entries.map { e -> if (e.id == entryId) e.copy(date = date, values = values) else e })
            }
        }
    }

    fun deleteEntry(ledgerId: Long, entryId: Long) {
        mutateLedger(ledgerId) { l -> l.copy(entries = l.entries.filter { it.id != entryId }) }
    }

    fun addTable(ledgerId: Long, name: String) {
        val n = name.trim().take(40)
        if (n.isEmpty()) return
        mutateLedger(ledgerId) { l -> l.copy(tables = l.tables + TableDef(Store.newId(), n, emptyList())) }
    }

    fun deleteTable(ledgerId: Long, tableId: Long) {
        mutateLedger(ledgerId) { l ->
            l.copy(
                tables = l.tables.filter { it.id != tableId },
                lines = l.lines.filter { it.tableId != tableId },
                entries = l.entries.filter { it.tableId != tableId },
            )
        }
    }

    fun renameTable(ledgerId: Long, tableId: Long, name: String) {
        val n = name.trim().take(40)
        if (n.isEmpty()) return
        mutateLedger(ledgerId) { l -> l.copy(tables = l.tables.map { t -> if (t.id == tableId) t.copy(name = n) else t }) }
    }

    fun addColumn(ledgerId: Long, tableId: Long, name: String, kind: Int, sumIt: Boolean, refA: Long, refB: Long, def: String) {
        val n = name.trim().take(30)
        if (n.isEmpty()) return
        if (kind == COL_CALC && (refA == 0L || refB == 0L)) return
        val col = ColumnDef(Store.newId(), n, kind, sumIt, refA, refB, if (kind == COL_CALC) "" else def.trim().take(40))
        mutateLedger(ledgerId) { l -> l.copy(tables = l.tables.map { t -> if (t.id == tableId) t.copy(columns = t.columns + col) else t }) }
    }

    fun deleteColumn(ledgerId: Long, tableId: Long, colId: Long) {
        mutateLedger(ledgerId) { l ->
            l.copy(
                tables = l.tables.map { t ->
                    if (t.id == tableId) {
                        t.copy(columns = t.columns.filter { c -> c.id != colId && !(c.kind == COL_CALC && (c.refA == colId || c.refB == colId)) })
                    } else {
                        t
                    }
                },
                lines = l.lines.filter { line -> !(line.tableId == tableId && line.colId == colId) },
            )
        }
    }

    fun addLine(ledgerId: Long, label: String, tableId: Long, colId: Long, sign: Int, money: Boolean, row: Int) {
        val n = label.trim().take(40)
        if (n.isEmpty() || tableId == 0L || colId == 0L) return
        mutateLedger(ledgerId) { l -> l.copy(lines = l.lines + SummaryLine(Store.newId(), n, tableId, colId, sign, money, row)) }
    }

    fun editLine(ledgerId: Long, lineId: Long, label: String, row: Int) {
        val n = label.trim().take(40)
        if (n.isEmpty()) return
        mutateLedger(ledgerId) { l -> l.copy(lines = l.lines.map { x -> if (x.id == lineId) x.copy(label = n, row = row) else x }) }
    }

    fun editColumn(ledgerId: Long, tableId: Long, colId: Long, name: String, def: String, sumIt: Boolean) {
        val n = name.trim().take(30)
        if (n.isEmpty()) return
        mutateLedger(ledgerId) { l ->
            l.copy(
                tables = l.tables.map { t ->
                    if (t.id == tableId) {
                        t.copy(columns = t.columns.map { c -> if (c.id == colId) c.copy(name = n, def = def.trim().take(40), sumIt = sumIt) else c })
                    } else {
                        t
                    }
                },
            )
        }
    }

    fun renameBatch(ledgerId: Long, batchId: Long, name: String) {
        val n = name.trim().take(40)
        if (n.isEmpty()) return
        mutateLedger(ledgerId) { l -> l.copy(batches = l.batches.map { b -> if (b.id == batchId) b.copy(name = n) else b }) }
    }

    private fun <T> moved(list: List<T>, idx: Int, dir: Int): List<T> {
        val j = idx + dir
        if (idx < 0 || j < 0 || j >= list.size) return list
        val m = list.toMutableList()
        val t = m[idx]
        m[idx] = m[j]
        m[j] = t
        return m
    }

    fun moveLine(ledgerId: Long, lineId: Long, dir: Int) {
        mutateLedger(ledgerId) { l -> l.copy(lines = moved(l.lines, l.lines.indexOfFirst { it.id == lineId }, dir)) }
    }

    fun moveTable(ledgerId: Long, tableId: Long, dir: Int) {
        mutateLedger(ledgerId) { l -> l.copy(tables = moved(l.tables, l.tables.indexOfFirst { it.id == tableId }, dir)) }
    }

    fun moveColumn(ledgerId: Long, tableId: Long, colId: Long, dir: Int) {
        mutateLedger(ledgerId) { l ->
            l.copy(
                tables = l.tables.map { t ->
                    if (t.id == tableId) t.copy(columns = moved(t.columns, t.columns.indexOfFirst { it.id == colId }, dir)) else t
                },
            )
        }
    }

    fun deleteLine(ledgerId: Long, lineId: Long) {
        mutateLedger(ledgerId) { l -> l.copy(lines = l.lines.filter { it.id != lineId }) }
    }

    /** Starts a new batch without settling (the old one stays open until you settle it). */
    fun newBatch(ledgerId: Long, name: String) {
        val n = name.trim().take(40)
        if (n.isEmpty()) return
        val b = Batch(Store.newId(), n, System.currentTimeMillis())
        mutateLedger(ledgerId) { l -> l.copy(batches = l.batches + b) }
        openBatchId = b.id
    }

    /** Settle and close a batch. If newName is not blank a new batch is opened (carrying the balance if asked). */
    fun settle(ledgerId: Long, batchId: Long, carry: Boolean, newName: String) {
        val l = ledgers.firstOrNull { it.id == ledgerId } ?: return
        val b = l.batches.firstOrNull { it.id == batchId } ?: return
        val text = LedgerCalc.fullText(l, b, LocaleHelper.wrap(app).getString(R.string.opening_balance))
        val bal = LedgerCalc.balance(l, b)
        val now = System.currentTimeMillis()
        val clean = newName.trim().take(40)
        val nb = if (clean.isNotEmpty()) Batch(Store.newId(), clean, now, 0L, if (carry) bal else 0.0) else null
        mutateLedger(ledgerId) { led ->
            val closed = led.batches.map { x -> if (x.id == batchId) x.copy(closedAt = now, closedText = text) else x }
            led.copy(batches = if (nb != null) closed + nb else closed)
        }
        openBatchId = nb?.id ?: batchId
    }

    fun reopenBatch(ledgerId: Long, batchId: Long) {
        mutateLedger(ledgerId) { l ->
            l.copy(batches = l.batches.map { x -> if (x.id == batchId) x.copy(closedAt = 0L, closedText = "") else x })
        }
    }

    fun deleteBatch(ledgerId: Long, batchId: Long) {
        mutateLedger(ledgerId) { l ->
            l.copy(batches = l.batches.filter { it.id != batchId }, entries = l.entries.filter { it.batchId != batchId })
        }
        val l = ledgers.firstOrNull { it.id == ledgerId }
        if (l != null) openBatchId = defaultBatchId(l)
    }

    fun copyToClipboard(text: String) {
        try {
            val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("rakkez", text))
        } catch (e: Throwable) {
            AppLog.log("COPY", "Clipboard failed", e)
        }
    }

    // ---------------- notes ----------------

    fun reloadNotes() {
        notes = NoteStore.load(app)
    }

    fun searchNotes(q: String) {
        noteQuery = q
    }

    fun filterNotes(t: Int) {
        noteType = t
    }

    fun scopeNotes(sc: Long) {
        noteScope = sc
    }

    fun visibleNotes(): List<Note> {
        val scope = noteScope
        val list = notes.notes.filter { n ->
            val inScope = when {
                scope == -2L -> n.archived
                n.archived -> false
                scope == -1L -> true
                else -> n.collectionId == scope
            }
            inScope && NoteTools.matches(n, noteType) && NoteTools.searchMatch(n, noteQuery)
        }
        return list.sortedWith(compareByDescending<Note> { it.pinned }.thenByDescending { it.updatedAt })
    }

    /** action: 0 nothing, 1 text, 2 voice, 3 photo, 4 video (used by the quick-capture widget). */
    fun newNote(action: Int) {
        if (editing != null) closeNote()
        sessionAdded.clear()
        removedOld.clear()
        val now = System.currentTimeMillis()
        val col = if (noteScope > 0L) noteScope else 0L
        editing = Note(Store.newId(), "", "", now, now, false, false, col, emptyList())
        editingNew = true
        noteAction = action
        tab = 3
        overlay = OVERLAY_NONE
    }

    fun consumeNoteAction() {
        noteAction = 0
    }

    fun openNote(n: Note) {
        sessionAdded.clear()
        removedOld.clear()
        editing = n
        editingNew = false
        noteAction = 0
    }

    fun editDraftText(title: String, body: String) {
        val e = editing ?: return
        editing = e.copy(title = title, body = body)
    }

    fun editDraftMeta(pinned: Boolean, archived: Boolean, col: Long) {
        val e = editing ?: return
        editing = e.copy(pinned = pinned, archived = archived, collectionId = col)
    }

    fun addDraftMedia(m: Media) {
        val e = editing ?: return
        sessionAdded.add(m.id)
        editing = e.copy(media = e.media + m)
    }

    fun removeDraftMedia(id: Long) {
        val e = editing ?: return
        val m = e.media.firstOrNull { it.id == id } ?: return
        if (sessionAdded.contains(id)) {
            NoteStore.deleteFiles(app, listOf(m))
            sessionAdded.remove(id)
        } else {
            removedOld.add(m)
        }
        editing = e.copy(media = e.media.filter { it.id != id })
    }

    fun addCapture(f: java.io.File, kind: Int, mime: String) {
        viewModelScope.launch {
            val dur = if (kind == MK_VIDEO) withContext(Dispatchers.IO) { NoteMedia.durationOf(f) } else 0L
            addDraftMedia(Media(Store.newId(), f.name, kind, mime, "", dur))
        }
    }

    fun attachUris(list: List<Uri>) {
        viewModelScope.launch {
            val ms = withContext(Dispatchers.IO) { list.mapNotNull { u -> NoteStore.copyIn(app, u) } }
            for (m in ms) addDraftMedia(m)
        }
    }

    private fun persistDraft(n: Note) {
        val saved = n.copy(updatedAt = System.currentTimeMillis())
        notes = NoteStore.update(app) { d ->
            if (d.notes.any { it.id == saved.id }) {
                NotesData(d.notes.map { x -> if (x.id == saved.id) saved else x }, d.collections)
            } else {
                NotesData(listOf(saved) + d.notes, d.collections)
            }
        }
    }

    /** Keeps what was typed if the app is sent to the background (or killed) while a note is open. */
    fun saveDraftSilently() {
        val e = editing ?: return
        if (NoteTools.isBlank(e)) return
        persistDraft(e)
        editingNew = false
    }

    fun closeNote() {
        val e = editing ?: return
        if (NoteTools.isBlank(e)) {
            if (!editingNew) removeNoteData(e.id)
        } else {
            persistDraft(e)
        }
        NoteStore.deleteFiles(app, removedOld)
        removedOld.clear()
        sessionAdded.clear()
        editing = null
        editingNew = false
        noteAction = 0
    }

    private fun removeNoteData(id: Long) {
        notes = NoteStore.update(app) { d -> NotesData(d.notes.filter { it.id != id }, d.collections) }
    }

    fun deleteNote(id: Long) {
        val stored = notes.notes.firstOrNull { it.id == id }
        if (stored != null) NoteStore.deleteFiles(app, stored.media)
        val e = editing
        if (e != null && e.id == id) {
            NoteStore.deleteFiles(app, e.media)
            NoteStore.deleteFiles(app, removedOld)
            removedOld.clear()
            sessionAdded.clear()
            editing = null
            editingNew = false
            noteAction = 0
        }
        removeNoteData(id)
    }

    fun addCollection(name: String) {
        val n = name.trim().take(30)
        if (n.isEmpty()) return
        val id = Store.newId()
        notes = NoteStore.update(app) { d -> NotesData(d.notes, d.collections + NoteCollection(id, n)) }
        noteScope = id
    }

    fun renameCollection(id: Long, name: String) {
        val n = name.trim().take(30)
        if (n.isEmpty()) return
        notes = NoteStore.update(app) { d ->
            NotesData(d.notes, d.collections.map { c -> if (c.id == id) c.copy(name = n) else c })
        }
    }

    fun deleteCollection(id: Long) {
        notes = NoteStore.update(app) { d ->
            NotesData(
                d.notes.map { x -> if (x.collectionId == id) x.copy(collectionId = 0L) else x },
                d.collections.filter { it.id != id },
            )
        }
        val e = editing
        if (e != null && e.collectionId == id) editing = e.copy(collectionId = 0L)
        if (noteScope == id) noteScope = -1L
    }

    // ---------------- backup / restore ----------------

    fun runBackup(basic: Boolean, ledger: Boolean, notesOn: Boolean, mediaOn: Boolean) {
        if (backupBusy) return
        if (!basic && !ledger && !notesOn && !mediaOn) {
            backupMsg = LocaleHelper.wrap(app).getString(R.string.backup_pick_something)
            return
        }
        backupBusy = true
        backupMsg = ""
        viewModelScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    val name = Backup.create(app, basic, ledger, notesOn, mediaOn, false)
                    LocaleHelper.wrap(app).getString(R.string.backup_done, name)
                } catch (e: Throwable) {
                    AppLog.log("BACKUP", "Backup failed", e)
                    LocaleHelper.wrap(app).getString(R.string.backup_failed)
                }
            }
            backupMsg = msg
            backupBusy = false
        }
    }

    fun prepareRestore(uri: Uri) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { Backup.readInfo(app, uri) }
            if (info == null) {
                backupMsg = LocaleHelper.wrap(app).getString(R.string.restore_bad_file)
            } else {
                restoreUri = uri
                restoreInfo = info
            }
        }
    }

    fun cancelRestore() {
        restoreUri = null
        restoreInfo = null
    }

    fun confirmRestore(basic: Boolean, ledger: Boolean, notesOn: Boolean, mediaOn: Boolean) {
        val uri = restoreUri ?: return
        restoreUri = null
        restoreInfo = null
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { Backup.restore(app, uri, basic, ledger, notesOn, mediaOn) }
            refreshAll(true)
            ledgers = LedgerStore.load(app)
            notes = NoteStore.load(app)
            editing = null
            closeLedgerView()
            backupMsg = LocaleHelper.wrap(app).getString(if (ok) R.string.restore_done else R.string.restore_failed)
        }
    }

    fun changeAutoBackup(on: Boolean) {
        Store.saveSettings(app, settings.copy(autoBackup = on))
        settings = Store.loadSettings(app)
    }

    fun clearBackupMsg() {
        backupMsg = ""
    }
}
