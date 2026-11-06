package com.khereba1.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var addRequest by mutableStateOf(0)
        private set

    private var ticker: Job? = null

    init {
        refreshAll(false)
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
        tab = index
    }

    fun requestAdd() {
        addRequest += 1
    }

    fun consumeAdd() {
        addRequest = 0
    }

    // ---------------- tasks ----------------

    fun addTask(title: String) {
        val clean = title.trim().take(200)
        if (clean.isEmpty()) return
        Store.editTasks(app) { list -> list + Task(Store.newId(), clean, createdAt = System.currentTimeMillis()) }
        refreshAll(true)
    }

    fun editTask(id: Long, title: String, estimate: Int) {
        val clean = title.trim().take(200)
        if (clean.isEmpty()) return
        Store.editTasks(app) { list -> list.map { t -> if (t.id == id) t.copy(title = clean, estimate = estimate) else t } }
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
    }

    fun selectTask(id: Long) {
        Engine.selectTask(app, id)
        refreshAll(true)
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

    fun stop() {
        Engine.stop(app)
        refreshAll(true)
    }

    fun finishAsk() {
        Engine.finishAsk(app)
        refreshAll(true)
    }

    // ---------------- settings ----------------

    fun applySettings(s: Settings) {
        Store.saveSettings(app, s)
        refreshAll(false)
    }

    fun changeLanguage(lang: String) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("lang", lang).commit()
        Alerts.ensureChannel(app)
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
}
