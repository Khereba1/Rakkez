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
    val cs = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Task?>(null) }
    var showDone by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val open = vm.tasks.filter { !it.done }
    val done = vm.tasks.filter { it.done }.sortedByDescending { it.doneAt }
    val dateText = remember { SimpleDateFormat("EEEE d MMMM", dateLocale()).format(Date()) }

    LaunchedEffect(vm.addRequest) {
        if (vm.addRequest > 0) {
            try {
                requester.requestFocus()
            } catch (e: Throwable) {
                // field not ready yet: ignore
            }
            vm.consumeAdd()
        }
    }

    fun submit() {
        if (draft.isNotBlank()) {
            vm.addTask(draft)
            draft = ""
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 10.dp)) {
            Text(dateText, color = cs.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.tasks_summary, open.size.toString(), done.size.toString()),
                color = cs.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
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
                    textStyle = TextStyle(color = cs.onSurface, fontSize = 16.sp),
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
                Text("+", color = cs.onPrimary, fontSize = 28.sp, fontWeight = FontWeight.Medium)
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
            onSave = { title, est ->
                vm.editTask(current.id, title, est)
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
            if (task.estimate > 0 || task.spent > 0) {
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(cs.primary)
                    )
                    Spacer(Modifier.size(6.dp))
                    val label = if (task.estimate > 0) task.spent.toString() + "/" + task.estimate.toString() else task.spent.toString()
                    Text(label, color = cs.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        if (!task.done) PlayButton(onPlay)
    }
}

@Composable
fun TaskDialog(task: Task, onDismiss: () -> Unit, onSave: (String, Int) -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var title by remember { mutableStateOf(task.title) }
    var est by remember { mutableStateOf(task.estimate) }
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
                Spacer(Modifier.height(10.dp))
                Stepper(
                    label = stringResource(R.string.task_estimate),
                    value = if (est == 0) "—" else est.toString(),
                    onMinus = { if (est > 0) est -= 1 },
                    onPlus = { if (est < 20) est += 1 },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (title.isNotBlank()) onSave(title, est) else onDismiss() }) {
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
