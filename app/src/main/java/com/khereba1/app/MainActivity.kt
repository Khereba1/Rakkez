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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
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
        if (tab in 0..3) vm.openTab(tab)
        if (i.getBooleanExtra("add", false)) vm.requestAdd()
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

@Composable
fun AppRoot(vm: MainViewModel, onLanguage: (String) -> Unit, onBeforeStart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    BackHandler(enabled = vm.tab != 0) { vm.openTab(0) }
    Surface(modifier = Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (vm.tab) {
                    0 -> TasksScreen(vm = vm, onPlay = { id ->
                        vm.selectTask(id)
                        vm.openTab(1)
                    })
                    1 -> FocusScreen(vm = vm, onBeforeStart = onBeforeStart)
                    2 -> TodayScreen(vm = vm)
                    else -> SettingsScreen(vm = vm, onLanguage = onLanguage)
                }
            }
            NavBar(current = vm.tab, onSelect = { vm.openTab(it) })
        }
    }
    if (vm.timer.phase == PH_ASK) AskDialog(vm)
}

@Composable
fun NavBar(current: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val labels = listOf(R.string.tab_tasks, R.string.tab_focus, R.string.tab_today, R.string.tab_settings)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(cs.surface)
            .padding(5.dp),
    ) {
        for (i in labels.indices) {
            val sel = i == current
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (sel) cs.primaryContainer else cs.surface)
                    .clickable { onSelect(i) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(labels[i]),
                    color = if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}
