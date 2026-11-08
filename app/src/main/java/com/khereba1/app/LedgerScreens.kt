package com.khereba1.app

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun LedgerRoot(vm: MainViewModel) {
    val l = vm.ledgers.firstOrNull { it.id == vm.openLedgerId }
    if (l == null) LedgerList(vm) else LedgerDetail(vm, l)
}

private fun currentBatch(l: Ledger, id: Long): Batch? =
    l.batches.firstOrNull { it.id == id } ?: l.batches.lastOrNull { it.closedAt == 0L } ?: l.batches.lastOrNull()

private fun isMoneyCol(t: TableDef, c: ColumnDef): Boolean {
    if (c.kind == COL_MONEY) return true
    if (c.kind == COL_CALC) {
        val a = t.columns.firstOrNull { it.id == c.refA }
        val b = t.columns.firstOrNull { it.id == c.refB }
        return (a != null && a.kind == COL_MONEY) || (b != null && b.kind == COL_MONEY)
    }
    return false
}

// ======================================================================
// list of ledgers
// ======================================================================

@Composable
fun LedgerList(vm: MainViewModel) {
    val cs = MaterialTheme.colorScheme
    var showNew by remember { mutableStateOf(false) }

    LaunchedEffect(vm.plusRequest) {
        if (vm.plusRequest == PLUS_LEDGER) {
            showNew = true
            vm.consumePlus()
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.tab_ledger),
                modifier = Modifier.weight(1f),
                color = cs.onBackground,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            GearButton { vm.openOverlay(OVERLAY_SETTINGS) }
        }
        if (vm.ledgers.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.ledger_empty),
                    color = cs.onSurfaceVariant,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { showNew = true },
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                ) { Text(stringResource(R.string.new_ledger)) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            ) {
                items(vm.ledgers, key = { it.id }) { l ->
                    val b = currentBatch(l, 0L)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .panel(cs.surface, 22.dp)
                            .clickable { vm.openLedger(l.id) }
                            .padding(16.dp),
                    ) {
                        Text(l.name, color = cs.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (b != null) {
                            Text(
                                b.name + (if (b.closedAt != 0L) " · " + stringResource(R.string.batch_closed) else ""),
                                color = cs.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                l.balanceLabel + ": " + LedgerCalc.withUnit(l, LedgerCalc.balance(l, b), true),
                                color = cs.primary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNew) {
        NewLedgerDialog(
            onDismiss = { showNew = false },
            onCreate = { kind, name ->
                vm.createLedger(kind, name)
                showNew = false
            },
        )
    }
}

@Composable
fun NewLedgerDialog(onDismiss: () -> Unit, onCreate: (Int, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(LedgerTemplates.FACTORY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_ledger)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text(stringResource(R.string.ledger_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                for (k in listOf(LedgerTemplates.FACTORY, LedgerTemplates.HOME, LedgerTemplates.BLANK)) {
                    val tn = when (k) {
                        LedgerTemplates.FACTORY -> stringResource(R.string.tpl_factory)
                        LedgerTemplates.HOME -> stringResource(R.string.tpl_home)
                        else -> stringResource(R.string.tpl_blank)
                    }
                    val td = when (k) {
                        LedgerTemplates.FACTORY -> stringResource(R.string.tpl_factory_desc)
                        LedgerTemplates.HOME -> stringResource(R.string.tpl_home_desc)
                        else -> stringResource(R.string.tpl_blank_desc)
                    }
                    val sel = kind == k
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .panel(if (sel) cs.primaryContainer else cs.surfaceVariant, 16.dp)
                            .clickable { kind = k }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(tn, color = if (sel) cs.onPrimaryContainer else cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(td, color = if (sel) cs.onPrimaryContainer else cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(kind, name) }) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ======================================================================
// one ledger (current batch)
// ======================================================================

@Composable
fun LedgerDetail(vm: MainViewModel, l: Ledger) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val batch = currentBatch(l, vm.openBatchId)
    if (batch == null) {
        vm.closeLedgerView()
        return
    }
    val closed = batch.closedAt != 0L
    var tableIdx by remember(l.id) { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var showBatches by remember { mutableStateOf(false) }
    var showSettle by remember { mutableStateOf(false) }
    var showSetup by remember(l.id) { mutableStateOf(false) }
    var entryTarget by remember { mutableStateOf<Entry?>(null) }
    var newEntry by remember { mutableStateOf(false) }

    val tbl = if (l.tables.isEmpty()) null else l.tables[tableIdx.coerceIn(0, l.tables.size - 1)]
    val openingLabel = stringResource(R.string.opening_balance)

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800L)
            copied = false
        }
    }
    LaunchedEffect(vm.plusRequest) {
        if (vm.plusRequest == PLUS_LEDGER) {
            if (!closed && tbl != null && !showSetup) newEntry = true
            vm.consumePlus()
        }
    }

    if (showSetup) {
        LedgerSetup(vm, l, onBack = { showSetup = false })
        return
    }

    fun copyText(text: String) {
        vm.copyToClipboard(text)
        copied = true
        showMenu = false
    }

    Column(Modifier.fillMaxSize()) {
        // header
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackChevron { vm.closeLedgerView() }
            Column(Modifier.weight(1f)) {
                Text(l.name, color = cs.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    batch.name + (if (closed) " · " + stringResource(R.string.batch_closed) else ""),
                    color = cs.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                )
            }
            Box {
                Button(
                    onClick = { showMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text(stringResource(if (copied) R.string.copied else R.string.copy_label), fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_full)) },
                        onClick = { copyText(LedgerCalc.fullText(l, batch, openingLabel)) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.copy_summary)) },
                        onClick = { copyText(LedgerCalc.summaryText(l, batch, openingLabel)) },
                    )
                    if (tbl != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_table, tbl.name)) },
                            onClick = { copyText(LedgerCalc.tableText(l, tbl, batch)) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.copy_csv)) },
                            onClick = { copyText(LedgerCalc.csv(l, tbl, batch)) },
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(stringResource(R.string.batches_title) + " ▾", false, { showBatches = true })
            Pill(stringResource(R.string.ledger_setup), false, { showSetup = true })
        }

        // totals (always visible)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .panel(cs.surface, 22.dp)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            val cells = ArrayList<Triple<String, String, Boolean>>()
            if (batch.opening != 0.0) cells.add(Triple(openingLabel, LedgerCalc.withUnit(l, batch.opening, true), false))
            for (line in l.lines) {
                cells.add(Triple(line.label, LedgerCalc.withUnit(l, LedgerCalc.lineValue(l, line, batch.id), line.money), false))
            }
            val rows = cells.chunked(3)
            for (r in rows) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    for (cell in r) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(cell.first, color = cs.onSurfaceVariant, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(cell.second, color = cs.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    for (k in r.size until 3) Spacer(Modifier.weight(1f))
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(l.balanceLabel, color = cs.onSurfaceVariant, fontSize = 12.sp)
                Text(
                    LedgerCalc.withUnit(l, LedgerCalc.balance(l, batch), true),
                    color = cs.primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (tbl == null) {
            Text(stringResource(R.string.no_tables), color = cs.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        } else {
            Wrap(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                for ((i, t) in l.tables.withIndex()) {
                    val n = l.entries.count { it.batchId == batch.id && it.tableId == t.id }
                    Pill(t.name + " · " + n.toString(), i == tableIdx.coerceIn(0, l.tables.size - 1), { tableIdx = i })
                }
            }

            val entries = l.entries
                .filter { it.batchId == batch.id && it.tableId == tbl.id }
                .sortedWith(compareBy({ it.date }, { it.id }))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            ) {
                if (entries.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(if (closed) R.string.entries_none_closed else R.string.entries_none),
                            color = cs.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 20.dp, horizontal = 8.dp),
                        )
                    }
                }
                items(entries, key = { it.id }) { e ->
                    EntryRow(l, tbl, e) { if (!closed) entryTarget = e }
                }
                if (!closed) {
                    item(key = "addbtn") {
                        Button(
                            onClick = { newEntry = true },
                            colors = ButtonDefaults.buttonColors(containerColor = cs.primaryContainer, contentColor = cs.onPrimaryContainer),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(46.dp),
                        ) { Text(stringResource(R.string.add_entry, tbl.name), fontWeight = FontWeight.Bold) }
                    }
                }
                item(key = "totalbar") {
                    val parts = ArrayList<String>()
                    for (c in tbl.columns) {
                        if (c.kind != COL_TEXT && c.sumIt) {
                            val v = LedgerCalc.colTotal(tbl, c, entries)
                            parts.add(c.name + " " + LedgerCalc.withUnit(l, v, isMoneyCol(tbl, c)))
                        }
                    }
                    if (parts.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .panel(cs.surfaceVariant, 16.dp)
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.table_total), color = cs.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.size(10.dp))
                            Text(
                                parts.joinToString(" · "),
                                color = cs.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }

        if (closed) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.batch_closed_note), color = cs.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.reopenBatch(l.id, batch.id) }) { Text(stringResource(R.string.reopen)) }
            }
        } else {
            OutlinedButton(
                onClick = { showSettle = true },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(23.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(48.dp),
            ) { Text(stringResource(R.string.settle_button), fontWeight = FontWeight.SemiBold) }
        }
    }

    // dialogs
    if (newEntry && tbl != null) {
        EntryDialog(
            ledger = l,
            table = tbl,
            entry = null,
            onDismiss = { newEntry = false },
            onSave = { date, values ->
                vm.saveEntry(l.id, 0L, tbl.id, batch.id, date, values)
                newEntry = false
            },
            onDelete = null,
        )
    }
    val et = entryTarget
    if (et != null) {
        val t2 = l.tables.firstOrNull { it.id == et.tableId }
        if (t2 != null) {
            EntryDialog(
                ledger = l,
                table = t2,
                entry = et,
                onDismiss = { entryTarget = null },
                onSave = { date, values ->
                    vm.saveEntry(l.id, et.id, t2.id, batch.id, date, values)
                    entryTarget = null
                },
                onDelete = {
                    vm.deleteEntry(l.id, et.id)
                    entryTarget = null
                },
            )
        }
    }
    if (showBatches) {
        BatchesDialog(vm, l, batch, onDismiss = { showBatches = false })
    }
    if (showSettle) {
        SettleDialog(
            ledger = l,
            batch = batch,
            onDismiss = { showSettle = false },
            onConfirm = { carry, newName ->
                vm.settle(l.id, batch.id, carry, newName)
                showSettle = false
            },
        )
    }
}

@Composable
fun BackChevron(onClick: () -> Unit) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (rtl) "›" else "‹", color = MaterialTheme.colorScheme.onSurface, fontSize = 30.sp)
    }
}

@Composable
fun EntryRow(l: Ledger, t: TableDef, e: Entry, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dateText = remember(e.date) { SimpleDateFormat("d MMM", dateLocale()).format(Date(e.date)) }
    val title = t.columns
        .filter { it.kind == COL_TEXT }
        .map { (e.values[it.id] ?: "").trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
    val primary = LedgerCalc.primaryColumn(t)
    val subParts = ArrayList<String>()
    for (c in t.columns) {
        if (c.kind != COL_TEXT && (primary == null || c.id != primary.id)) {
            subParts.add(c.name + " " + LedgerCalc.fmt(LedgerCalc.cell(t, c, e)))
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .panel(cs.surface, 16.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                dateText + (if (title.isNotEmpty()) " · " + title else ""),
                color = cs.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subParts.isNotEmpty()) {
                Text(subParts.joinToString(" · "), color = cs.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (primary != null) {
            Text(
                LedgerCalc.withUnit(l, LedgerCalc.cell(t, primary, e), isMoneyCol(t, primary)),
                color = cs.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ======================================================================
// dialogs
// ======================================================================

@Composable
fun EntryDialog(
    ledger: Ledger,
    table: TableDef,
    entry: Entry?,
    onDismiss: () -> Unit,
    onSave: (Long, Map<Long, String>) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var date by remember { mutableStateOf(entry?.date ?: System.currentTimeMillis()) }
    val values = remember {
        val m = mutableStateMapOf<Long, String>()
        if (entry != null) {
            for ((k, v) in entry.values) m[k] = v
        }
        m
    }
    val dateText = SimpleDateFormat("EEE d MMM yyyy", dateLocale()).format(Date(date))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(table.name) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .panel(cs.surfaceVariant, 14.dp)
                        .clickable {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = date
                            DatePickerDialog(
                                ctx,
                                { _, y, m, d ->
                                    val c2 = Calendar.getInstance()
                                    c2.timeInMillis = date
                                    c2.set(y, m, d)
                                    date = c2.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH),
                            ).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.date_label), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(dateText, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                }
                for (c in table.columns) {
                    if (c.kind == COL_CALC) {
                        val v = LedgerCalc.num(values[c.refA]) * LedgerCalc.num(values[c.refB])
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(c.name, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(LedgerCalc.fmt(v), color = cs.primary, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedTextField(
                            value = values[c.id] ?: "",
                            onValueChange = { nv -> values[c.id] = nv },
                            label = { Text(c.name) },
                            singleLine = c.kind != COL_TEXT,
                            keyboardOptions = if (c.kind == COL_TEXT) KeyboardOptions.Default else KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val out = HashMap<Long, String>()
                for (c in table.columns) {
                    if (c.kind != COL_CALC) out[c.id] = (values[c.id] ?: "").trim()
                }
                onSave(date, out)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = cs.error) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
fun ConfirmDialog(text: String, onYes: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onYes) { Text(stringResource(R.string.confirm_yes), color = cs.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotBlank()) onSave(text)
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun BatchesDialog(vm: MainViewModel, l: Ledger, current: Batch, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var newName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Batch?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.batches_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (b in l.batches.reversed()) {
                    val state = if (b.closedAt == 0L) stringResource(R.string.batch_open) else stringResource(R.string.batch_closed)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .panel(if (b.id == current.id) cs.primaryContainer else cs.surfaceVariant, 14.dp)
                            .clickable {
                                vm.selectBatch(b.id)
                                onDismiss()
                            }
                            .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                            Text(
                                state + " · " + LedgerCalc.withUnit(l, LedgerCalc.balance(l, b), true),
                                color = cs.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        }
                        TextButton(onClick = { deleting = b }) { Text("✕", color = cs.onSurfaceVariant) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text(stringResource(R.string.batch_new_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            vm.newBatch(l.id, newName)
                            onDismiss()
                        },
                        enabled = newName.isNotBlank(),
                    ) { Text(stringResource(R.string.create)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    val d = deleting
    if (d != null) {
        ConfirmDialog(
            text = stringResource(R.string.batch_delete_confirm, d.name),
            onYes = {
                vm.deleteBatch(l.id, d.id)
                deleting = null
                onDismiss()
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun SettleDialog(ledger: Ledger, batch: Batch, onDismiss: () -> Unit, onConfirm: (Boolean, String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var carry by remember { mutableStateOf(true) }
    var newName by remember { mutableStateOf("") }
    val summary = LedgerCalc.summaryText(ledger, batch, stringResource(R.string.opening_balance)).replace("*", "")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settle_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(summary, color = cs.onSurface, fontSize = 14.sp, lineHeight = 22.sp)
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.settle_new_batch)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settle_carry), color = cs.onSurface, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = carry, onCheckedChange = { v -> carry = v })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(carry, newName) }) { Text(stringResource(R.string.settle_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ======================================================================
// structure editor (tables, columns, summary lines)
// ======================================================================

@Composable
fun LedgerSetup(vm: MainViewModel, l: Ledger, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var name by remember(l.id) { mutableStateOf(l.name) }
    var unit by remember(l.id) { mutableStateOf(l.unit) }
    var balLabel by remember(l.id) { mutableStateOf(l.balanceLabel) }
    var addTable by remember { mutableStateOf(false) }
    var renameTable by remember { mutableStateOf<TableDef?>(null) }
    var addColumnTo by remember { mutableStateOf<TableDef?>(null) }
    var addLine by remember { mutableStateOf(false) }
    var confirmDeleteLedger by remember { mutableStateOf(false) }
    var confirmDeleteTable by remember { mutableStateOf<TableDef?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackChevron(onBack)
            Text(stringResource(R.string.ledger_setup), color = cs.onBackground, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.fillMaxWidth().panel(cs.surface, 20.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.ledger_name_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(stringResource(R.string.ledger_unit_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = balLabel, onValueChange = { balLabel = it }, label = { Text(stringResource(R.string.ledger_balance_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = { vm.renameLedger(l.id, name, unit, balLabel) },
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                ) { Text(stringResource(R.string.save)) }
            }

            Text(stringResource(R.string.setup_tables), color = cs.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            for (t in l.tables) {
                Column(Modifier.fillMaxWidth().panel(cs.surface, 20.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.name,
                            color = cs.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { renameTable = t }
                                .padding(vertical = 6.dp),
                        )
                        TextButton(onClick = { confirmDeleteTable = t }) { Text(stringResource(R.string.delete), color = cs.error) }
                    }
                    for (c in t.columns) {
                        val kindText = when (c.kind) {
                            COL_TEXT -> stringResource(R.string.kind_text)
                            COL_NUMBER -> stringResource(R.string.kind_number)
                            COL_MONEY -> stringResource(R.string.kind_money)
                            else -> stringResource(R.string.kind_calc)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name + " · " + kindText, color = cs.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.deleteColumn(l.id, t.id, c.id) }) { Text("✕", color = cs.onSurfaceVariant) }
                        }
                    }
                    TextButton(onClick = { addColumnTo = t }) { Text(stringResource(R.string.add_column)) }
                }
            }
            OutlinedButton(onClick = { addTable = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_table)) }

            Text(stringResource(R.string.setup_lines), color = cs.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Column(Modifier.fillMaxWidth().panel(cs.surface, 20.dp).padding(14.dp)) {
                for (line in l.lines) {
                    val t = l.tables.firstOrNull { it.id == line.tableId }
                    val c = t?.columns?.firstOrNull { it.id == line.colId }
                    val signText = if (line.sign > 0) "+" else if (line.sign < 0) "−" else "·"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            signText + "  " + line.label + "  ←  " + (t?.name ?: "?") + " / " + (c?.name ?: "?"),
                            color = cs.onSurface,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { vm.deleteLine(l.id, line.id) }) { Text("✕", color = cs.onSurfaceVariant) }
                    }
                }
                TextButton(onClick = { addLine = true }) { Text(stringResource(R.string.add_line)) }
                Text(stringResource(R.string.setup_lines_hint), color = cs.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
            }

            TextButton(onClick = { confirmDeleteLedger = true }) {
                Text(stringResource(R.string.delete_ledger), color = cs.error)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (addTable) {
        NameDialog(stringResource(R.string.add_table), "", { addTable = false }, { n -> vm.addTable(l.id, n) })
    }
    val rt = renameTable
    if (rt != null) {
        NameDialog(stringResource(R.string.rename_table), rt.name, { renameTable = null }, { n -> vm.renameTable(l.id, rt.id, n) })
    }
    val ac = addColumnTo
    if (ac != null) {
        AddColumnDialog(
            table = ac,
            onDismiss = { addColumnTo = null },
            onAdd = { n, kind, sumIt, a, b ->
                vm.addColumn(l.id, ac.id, n, kind, sumIt, a, b)
                addColumnTo = null
            },
        )
    }
    if (addLine) {
        AddLineDialog(
            ledger = l,
            onDismiss = { addLine = false },
            onAdd = { label, tid, cid, sign, money ->
                vm.addLine(l.id, label, tid, cid, sign, money)
                addLine = false
            },
        )
    }
    if (confirmDeleteLedger) {
        ConfirmDialog(
            text = stringResource(R.string.delete_ledger_confirm, l.name),
            onYes = {
                confirmDeleteLedger = false
                vm.deleteLedger(l.id)
            },
            onDismiss = { confirmDeleteLedger = false },
        )
    }
    val dt = confirmDeleteTable
    if (dt != null) {
        ConfirmDialog(
            text = stringResource(R.string.delete_table_confirm, dt.name),
            onYes = {
                vm.deleteTable(l.id, dt.id)
                confirmDeleteTable = null
            },
            onDismiss = { confirmDeleteTable = null },
        )
    }
}

@Composable
fun AddColumnDialog(table: TableDef, onDismiss: () -> Unit, onAdd: (String, Int, Boolean, Long, Long) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(COL_TEXT) }
    var sumIt by remember { mutableStateOf(true) }
    var refA by remember { mutableStateOf(0L) }
    var refB by remember { mutableStateOf(0L) }
    val numeric = table.columns.filter { it.kind == COL_NUMBER || it.kind == COL_MONEY }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_column)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.column_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Wrap {
                    Pill(stringResource(R.string.kind_text), kind == COL_TEXT, { kind = COL_TEXT })
                    Pill(stringResource(R.string.kind_number), kind == COL_NUMBER, { kind = COL_NUMBER })
                    Pill(stringResource(R.string.kind_money), kind == COL_MONEY, { kind = COL_MONEY })
                    Pill(stringResource(R.string.kind_calc), kind == COL_CALC, { kind = COL_CALC })
                }
                if (kind == COL_CALC) {
                    Text(stringResource(R.string.calc_hint), color = cs.onSurfaceVariant, fontSize = 13.sp)
                    if (numeric.size < 2) {
                        Text(stringResource(R.string.calc_need_two), color = cs.error, fontSize = 13.sp)
                    } else {
                        Wrap {
                            for (c in numeric) Pill(c.name, refA == c.id, { refA = c.id })
                        }
                        Text("×", color = cs.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Wrap {
                            for (c in numeric) Pill(c.name, refB == c.id, { refB = c.id })
                        }
                    }
                }
                if (kind != COL_TEXT) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.column_sum), color = cs.onSurface, modifier = Modifier.weight(1f), fontSize = 14.sp)
                        Switch(checked = sumIt, onCheckedChange = { v -> sumIt = v })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, kind, kind != COL_TEXT && sumIt, refA, refB) },
                enabled = name.isNotBlank() && (kind != COL_CALC || (refA != 0L && refB != 0L)),
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun AddLineDialog(ledger: Ledger, onDismiss: () -> Unit, onAdd: (String, Long, Long, Int, Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var label by remember { mutableStateOf("") }
    var tableId by remember { mutableStateOf(0L) }
    var colId by remember { mutableStateOf(0L) }
    var sign by remember { mutableStateOf(1) }
    var money by remember { mutableStateOf(true) }
    val table = ledger.tables.firstOrNull { it.id == tableId }
    val numeric = table?.columns?.filter { it.kind != COL_TEXT } ?: emptyList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_line)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.line_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.line_pick_table), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Wrap {
                    for (t in ledger.tables) Pill(t.name, tableId == t.id, {
                        tableId = t.id
                        colId = 0L
                    })
                }
                if (table != null) {
                    Text(stringResource(R.string.line_pick_column), color = cs.onSurfaceVariant, fontSize = 13.sp)
                    Wrap {
                        for (c in numeric) Pill(c.name, colId == c.id, { colId = c.id })
                    }
                }
                Text(stringResource(R.string.line_sign), color = cs.onSurfaceVariant, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(stringResource(R.string.sign_plus), sign == 1, { sign = 1 })
                    Pill(stringResource(R.string.sign_minus), sign == -1, { sign = -1 })
                    Pill(stringResource(R.string.sign_info), sign == 0, { sign = 0 })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.line_money), color = cs.onSurface, modifier = Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = money, onCheckedChange = { v -> money = v })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(label, tableId, colId, sign, money) },
                enabled = label.isNotBlank() && tableId != 0L && colId != 0L,
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
