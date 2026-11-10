package com.khereba1.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun TasksScreen(vm: MainViewModel, onPlay: (Long) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SegPill(stringResource(R.string.tab_tasks), vm.tasksSub == 0, { vm.openTasksSub(0) })
            SegPill(stringResource(R.string.sub_habits), vm.tasksSub == 1, { vm.openTasksSub(1) })
            SegPill(stringResource(R.string.sub_courses), vm.tasksSub == 2, { vm.openTasksSub(2) })
        }
        Box(Modifier.weight(1f)) {
            when (vm.tasksSub) {
                1 -> HabitsPane(vm)
                2 -> CoursesPane(vm)
                else -> TasksPane(vm, onPlay)
            }
        }
    }
}

@Composable
fun TasksPane(vm: MainViewModel, onPlay: (Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Task?>(null) }
    var showDone by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val open = vm.tasks.filter { !it.done }
    val done = vm.tasks.filter { it.done }.sortedByDescending { it.doneAt }
    val dateText = remember { SimpleDateFormat("EEEE d MMMM", dateLocale()).format(Date()) }

    LaunchedEffect(vm.plusRequest) {
        if (vm.plusRequest == PLUS_TASK) {
            try {
                requester.requestFocus()
            } catch (e: Throwable) {
                // field not ready yet: ignore
            }
            vm.consumePlus()
        }
    }

    fun submit() {
        if (draft.isNotBlank()) {
            vm.addTask(draft)
            draft = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 22.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(dateText, color = cs.onSurfaceVariant, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.tasks_summary, open.size.toString(), done.size.toString()),
                    color = cs.onBackground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            GearButton { vm.openOverlay(OVERLAY_SETTINGS) }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .panel(cs.surfaceVariant, 28.dp)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                if (draft.isEmpty()) {
                    Text(stringResource(R.string.tasks_add_hint), color = cs.onSurfaceVariant, fontSize = 16.sp)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, fontSize = 16.sp),
                    cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(requester),
                )
            }
            Box(
                Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(cs.primary)
                    .clickable { submit() },
                contentAlignment = Alignment.Center,
            ) {
                RkIcon(RkIc.PLUS, cs.onPrimary, 22.dp)
            }
        }

        if (vm.tasks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.tasks_empty),
                    color = cs.onSurfaceVariant,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            ) {
                items(open, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onToggle = { vm.toggleTask(task.id) },
                        onEdit = { editing = task },
                        onPlay = { onPlay(task.id) },
                    )
                }
                if (done.isNotEmpty()) {
                    item(key = "done_header") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showDone = !showDone }
                                .padding(vertical = 14.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.tasks_done_header, done.size.toString()),
                                color = cs.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(if (showDone) "▴" else "▾", color = cs.onSurfaceVariant, fontSize = 16.sp)
                        }
                    }
                    if (showDone) {
                        items(done, key = { it.id }) { task ->
                            TaskRow(
                                task = task,
                                onToggle = { vm.toggleTask(task.id) },
                                onEdit = { editing = task },
                                onPlay = { },
                            )
                        }
                    }
                }
            }
        }
    }

    val current = editing
    if (current != null) {
        TaskDialog(
            task = current,
            onDismiss = { editing = null },
            onSave = { title, est, plan, smin ->
                vm.editTask(current.id, title, est, plan, smin)
                editing = null
            },
            onDelete = {
                vm.deleteTask(current.id)
                editing = null
            },
        )
    }
}

@Composable
fun TaskRow(task: Task, onToggle: () -> Unit, onEdit: () -> Unit, onPlay: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .panel(cs.surface)
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RkCheck(task.done, onToggle, Modifier.padding(start = 6.dp))
        Column(
            Modifier
                .weight(1f)
                .clickable(onClick = onEdit)
                .padding(vertical = 12.dp, horizontal = 6.dp),
        ) {
            Text(
                task.title,
                color = if (task.done) cs.onSurfaceVariant else cs.onSurface,
                fontSize = 16.sp,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
            )
            val ctx = LocalContext.current
            val spentMin = task.spentMs / 60000L
            val parts = ArrayList<String>()
            if (task.planMin > 0) {
                parts.add(fmtMinutes(ctx, spentMin) + " / " + fmtMinutes(ctx, task.planMin.toLong()))
            } else if (task.estimate > 0) {
                parts.add(task.spent.toString() + "/" + task.estimate.toString() + " " + stringResource(R.string.sessions_word))
            } else if (task.spentMs >= 60000L) {
                parts.add(fmtMinutes(ctx, spentMin))
            }
            if (task.sessionMin > 0) parts.add(stringResource(R.string.session_len_short, task.sessionMin.toString()))
            if (parts.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(cs.primary)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(parts.joinToString(" \u00B7 "), color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        if (!task.done) PlayButton(onPlay)
    }
}

@Composable
fun TaskDialog(task: Task, onDismiss: () -> Unit, onSave: (String, Int, Int, Int) -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var title by remember { mutableStateOf(task.title) }
    var byTime by remember { mutableStateOf(task.planMin > 0) }
    var est by remember { mutableStateOf(task.estimate) }
    var plan by remember { mutableStateOf(task.planMin) }
    var smin by remember { mutableStateOf(task.sessionMin) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.task_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.plan_by_sessions), !byTime, { byTime = false })
                    Pill(stringResource(R.string.plan_by_time), byTime, { byTime = true })
                }
                if (byTime) {
                    Stepper(
                        label = stringResource(R.string.plan_time_label),
                        value = if (plan == 0) "\u2014" else stringResource(R.string.unit_min, plan.toString()),
                        onChange = { d -> plan = (plan + d * 5).coerceIn(0, 720) },
                    )
                } else {
                    Stepper(
                        label = stringResource(R.string.task_estimate),
                        value = if (est == 0) "\u2014" else est.toString(),
                        onChange = { d -> est = (est + d).coerceIn(0, 40) },
                    )
                }
                Stepper(
                    label = stringResource(R.string.task_session_len),
                    value = if (smin == 0) stringResource(R.string.default_word) else stringResource(R.string.unit_min, smin.toString()),
                    onChange = { d -> smin = (smin + d).coerceIn(0, 180) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) {
                    onSave(title, if (byTime) 0 else est, if (byTime) plan else 0, smin)
                } else {
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = cs.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
