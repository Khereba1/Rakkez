package com.khereba1.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: MainViewModel, onLanguage: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val st = vm.settings
    val lang = remember { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lang", "system") ?: "system" }
    var showLog by remember { mutableStateOf(false) }
    var logText by remember { mutableStateOf("") }
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Text(stringResource(R.string.tab_settings), color = cs.onBackground, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .panel(cs.surface, 20.dp)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.language_label), color = cs.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(stringResource(R.string.lang_system), lang == "system", { onLanguage("system") })
                Pill(stringResource(R.string.lang_arabic), lang == "ar", { onLanguage("ar") })
                Pill(stringResource(R.string.lang_english), lang == "en", { onLanguage("en") })
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .panel(cs.surface, 20.dp)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.set_timer), color = cs.onSurfaceVariant, fontSize = 13.sp)
            Stepper(
                stringResource(R.string.focus_len),
                stringResource(R.string.unit_min, st.focusMin.toString()),
                { if (st.focusMin > 1) vm.applySettings(st.copy(focusMin = st.focusMin - 1)) },
                { if (st.focusMin < 180) vm.applySettings(st.copy(focusMin = st.focusMin + 1)) },
            )
            Stepper(
                stringResource(R.string.short_len),
                stringResource(R.string.unit_min, st.shortMin.toString()),
                { if (st.shortMin > 1) vm.applySettings(st.copy(shortMin = st.shortMin - 1)) },
                { if (st.shortMin < 60) vm.applySettings(st.copy(shortMin = st.shortMin + 1)) },
            )
            Stepper(
                stringResource(R.string.long_len),
                stringResource(R.string.unit_min, st.longMin.toString()),
                { if (st.longMin > 1) vm.applySettings(st.copy(longMin = st.longMin - 1)) },
                { if (st.longMin < 90) vm.applySettings(st.copy(longMin = st.longMin + 1)) },
            )
            Stepper(
                stringResource(R.string.long_every),
                st.longEvery.toString(),
                { if (st.longEvery > 2) vm.applySettings(st.copy(longEvery = st.longEvery - 1)) },
                { if (st.longEvery < 8) vm.applySettings(st.copy(longEvery = st.longEvery + 1)) },
            )
            Stepper(
                stringResource(R.string.set_goal),
                stringResource(R.string.unit_min, st.goalMin.toString()),
                { if (st.goalMin > 15) vm.applySettings(st.copy(goalMin = st.goalMin - 15)) },
                { if (st.goalMin < 720) vm.applySettings(st.copy(goalMin = st.goalMin + 15)) },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.set_keep_screen), Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
                Switch(checked = st.keepOn, onCheckedChange = { v -> vm.applySettings(st.copy(keepOn = v)) })
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .panel(cs.surface, 20.dp)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.error_log), Modifier.weight(1f), color = cs.onSurface, fontSize = 15.sp)
                TextButton(onClick = {
                    showLog = !showLog
                    if (showLog) logText = AppLog.read()
                }) { Text(stringResource(if (showLog) R.string.hide else R.string.show)) }
                if (showLog) {
                    TextButton(onClick = {
                        AppLog.clear()
                        logText = ""
                    }) { Text(stringResource(R.string.clear_log)) }
                }
            }
            if (showLog) {
                SelectionContainer {
                    Text(
                        if (logText.isEmpty()) stringResource(R.string.log_empty) else logText.takeLast(6000),
                        color = cs.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.developer_label) + ": " + stringResource(R.string.developer_name) + "   ·   v" + version,
            color = cs.onSurfaceVariant,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(12.dp))
    }
}
