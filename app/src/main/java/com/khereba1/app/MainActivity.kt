package com.khereba1.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            RakkezTheme {
                AppRoot(
                    vm = vm,
                    onLanguage = { lang ->
                        vm.changeLanguage(lang)
                        recreate()
                    },
                    onFont = { key ->
                        vm.changeFont(key)
                        recreate()
                    },
                    onBeforeStart = { askNotificationPermission() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        Foreground.on = true
        Alerts.cancel(this)
        vm.startTicking()
    }

    override fun onStop() {
        Foreground.on = false
        vm.stopTicking()
        super.onStop()
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val tab = i.getIntExtra("tab", -1)
        if (tab == 0 || tab == 1) vm.openTab(tab)
        if (tab == 2) vm.openOverlay(OVERLAY_TODAY)
        if (i.getBooleanExtra("add", false)) {
            vm.openTab(0)
            vm.requestPlus(PLUS_TASK)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            try {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Throwable) {
                AppLog.log("PERM", "Notification permission request failed", e)
            }
        }
    }
}

// tab indexes: 0 tasks, 1 focus, 3 notes, 4 ledger (2 is the center "+" button, not a screen)
@Composable
fun AppRoot(vm: MainViewModel, onLanguage: (String) -> Unit, onFont: (String) -> Unit, onBeforeStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val inLedgerDetail = vm.tab == 4 && vm.openLedgerId != 0L
    BackHandler(enabled = vm.overlay != OVERLAY_NONE) { vm.closeOverlay() }
    BackHandler(enabled = vm.overlay == OVERLAY_NONE && inLedgerDetail) { vm.closeLedgerView() }
    BackHandler(enabled = vm.overlay == OVERLAY_NONE && !inLedgerDetail && vm.tab != 0) { vm.openTab(0) }
    Surface(modifier = Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                if (vm.overlay == OVERLAY_SETTINGS) {
                    SettingsScreen(vm = vm, onLanguage = onLanguage, onFont = onFont)
                } else if (vm.overlay == OVERLAY_TODAY) {
                    TodayScreen(vm = vm)
                } else {
                    when (vm.tab) {
                        0 -> TasksScreen(vm = vm, onPlay = { id -> vm.playTask(id) })
                        1 -> FocusScreen(vm = vm, onBeforeStart = onBeforeStart)
                        3 -> NotesPlaceholder(vm)
                        else -> LedgerRoot(vm)
                    }
                }
            }
            NavBar(
                current = if (vm.overlay == OVERLAY_NONE) vm.tab else -1,
                onSelect = { vm.openTab(it) },
                onPlus = {
                    val code = when (vm.tab) {
                        0 -> PLUS_TASK
                        1 -> PLUS_IDEA_FOCUS
                        3 -> PLUS_IDEA_NOTES
                        else -> PLUS_LEDGER
                    }
                    if (vm.overlay != OVERLAY_NONE) vm.closeOverlay()
                    vm.requestPlus(code)
                },
            )
        }
    }
    if (vm.timer.phase == PH_ASK) AskDialog(vm)
    RestoreDialog(vm)
}

@Composable
fun NotesPlaceholder(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    // the "+" on this tab is not wired yet in this version
    androidx.compose.runtime.LaunchedEffect(vm.plusRequest) {
        if (vm.plusRequest == PLUS_IDEA_NOTES) vm.consumePlus()
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.tab_notes), color = cs.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(10.dp))
        Text(stringResource(R.string.notes_soon), color = cs.onSurfaceVariant, fontSize = 15.sp, lineHeight = 23.sp)
    }
}

@Composable
fun NavBar(current: Int, onSelect: (Int) -> Unit, onPlus: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(cs.surface)
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(Modifier.weight(1f), R.string.tab_tasks, current == 0) { onSelect(0) }
        NavItem(Modifier.weight(1f), R.string.tab_focus, current == 1) { onSelect(1) }
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .size(50.dp)
                .clip(CircleShape)
                .background(cs.primary)
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center,
        ) {
            val c = cs.onPrimary
            Canvas(Modifier.size(22.dp)) {
                val w = 3.dp.toPx()
                drawLine(c, Offset(size.width * 0.5f, size.height * 0.12f), Offset(size.width * 0.5f, size.height * 0.88f), w, StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.12f, size.height * 0.5f), Offset(size.width * 0.88f, size.height * 0.5f), w, StrokeCap.Round)
            }
        }
        NavItem(Modifier.weight(1f), R.string.tab_notes, current == 3) { onSelect(3) }
        NavItem(Modifier.weight(1f), R.string.tab_ledger, current == 4) { onSelect(4) }
    }
}

@Composable
private fun NavItem(modifier: Modifier, label: Int, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(if (selected) cs.primaryContainer else cs.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(label),
            color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
