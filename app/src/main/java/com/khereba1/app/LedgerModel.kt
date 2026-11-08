package com.khereba1.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val COL_TEXT = 0
const val COL_NUMBER = 1
const val COL_MONEY = 2
const val COL_CALC = 3

/** A column. COL_CALC = refA x refB (two other columns of the same table). sumIt = show a total for it. */
data class ColumnDef(
    val id: Long,
    val name: String,
    val kind: Int,
    val sumIt: Boolean = true,
    val refA: Long = 0L,
    val refB: Long = 0L,
    val def: String = "",
)

data class TableDef(val id: Long, val name: String, val columns: List<ColumnDef>)

/** One line of the summary card. sign: +1 adds to the balance, -1 subtracts, 0 is information only. */
data class SummaryLine(
    val id: Long,
    val label: String,
    val tableId: Long,
    val colId: Long,
    val sign: Int,
    val money: Boolean,
    val row: Int = 0,
)

data class Entry(
    val id: Long,
    val tableId: Long,
    val batchId: Long,
    val date: Long,
    val values: Map<Long, String>,
)

/** A batch ("دفعة"): everything between opening and settling. closedAt = 0 means still open. */
data class Batch(
    val id: Long,
    val name: String,
    val openedAt: Long,
    val closedAt: Long = 0L,
    val opening: Double = 0.0,
    val closedText: String = "",
)

data class Ledger(
    val id: Long,
    val name: String,
    val unit: String,
    val balanceLabel: String,
    val tables: List<TableDef>,
    val lines: List<SummaryLine>,
    val batches: List<Batch>,
    val entries: List<Entry>,
)

object LedgerCalc {
    private fun latin(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0' + (ch - '٠')))
                in '۰'..'۹' -> sb.append(('0' + (ch - '۰')))
                '٫', '،', ',' -> sb.append('.')
                '٬', ' ' -> {}
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun num(s: String?): Double {
        if (s == null) return 0.0
        return latin(s.trim()).toDoubleOrNull() ?: 0.0
    }

    fun cell(t: TableDef, col: ColumnDef, e: Entry): Double {
        if (col.kind == COL_CALC) {
            return num(e.values[col.refA]) * num(e.values[col.refB])
        }
        return num(e.values[col.id])
    }

    fun colTotal(t: TableDef, col: ColumnDef, entries: List<Entry>): Double {
        var sum = 0.0
        for (e in entries) {
            if (e.tableId == t.id) sum += cell(t, col, e)
        }
        return sum
    }

    fun lineValue(l: Ledger, line: SummaryLine, batchId: Long): Double {
        val t = l.tables.firstOrNull { it.id == line.tableId } ?: return 0.0
        val col = t.columns.firstOrNull { it.id == line.colId } ?: return 0.0
        return colTotal(t, col, l.entries.filter { it.batchId == batchId })
    }

    fun balance(l: Ledger, batch: Batch): Double {
        var b = batch.opening
        for (line in l.lines) {
            if (line.sign != 0) b += line.sign * lineValue(l, line, batch.id)
        }
        return b
    }

    fun fmt(v: Double): String {
        val r = Math.round(v * 100.0) / 100.0
        return if (r == Math.floor(r) && Math.abs(r) < 1e15) {
            String.format(Locale.US, "%,d", r.toLong())
        } else {
            String.format(Locale.US, "%,.2f", r)
        }
    }

    /** Plain number for CSV (no thousands separators). */
    fun plain(v: Double): String {
        val r = Math.round(v * 100.0) / 100.0
        return if (r == Math.floor(r) && Math.abs(r) < 1e15) r.toLong().toString() else r.toString()
    }

    fun withUnit(l: Ledger, v: Double, money: Boolean): String =
        if (money && l.unit.isNotEmpty()) fmt(v) + " " + l.unit else fmt(v)

    fun dayText(ms: Long): String = SimpleDateFormat("d/M", Locale.US).format(Date(ms))

    /** The main number shown on the right of an entry row. */
    fun primaryColumn(t: TableDef): ColumnDef? {
        var best: ColumnDef? = null
        for (c in t.columns) {
            if (c.kind == COL_CALC) best = c
        }
        if (best != null) return best
        for (c in t.columns) {
            if (c.kind == COL_MONEY || c.kind == COL_NUMBER) best = c
        }
        return best
    }

    // ---------------- copy texts (WhatsApp friendly) ----------------

    private fun entryLine(t: TableDef, e: Entry, l: Ledger): String {
        val parts = ArrayList<String>()
        parts.add(dayText(e.date))
        for (c in t.columns) {
            if (c.kind == COL_TEXT) {
                val v = (e.values[c.id] ?: "").trim()
                if (v.isNotEmpty()) parts.add(v)
            } else {
                parts.add(fmt(cell(t, c, e)))
            }
        }
        return parts.joinToString(" | ")
    }

    fun tableText(l: Ledger, t: TableDef, batch: Batch): String {
        val sb = StringBuilder()
        sb.append("*").append(t.name).append("*\n")
        sb.append(t.columns.joinToString(" | ") { it.name }).append("\n")
        val entries = l.entries.filter { it.batchId == batch.id && it.tableId == t.id }.sortedWith(compareBy({ it.date }, { it.id }))
        for (e in entries) sb.append(entryLine(t, e, l)).append("\n")
        val totals = ArrayList<String>()
        for (c in t.columns) {
            if (c.kind != COL_TEXT && c.sumIt) totals.add(c.name + ": " + fmt(colTotal(t, c, entries)))
        }
        if (totals.isNotEmpty()) sb.append("= ").append(totals.joinToString(" | ")).append("\n")
        return sb.toString().trimEnd()
    }

    fun summaryText(l: Ledger, batch: Batch, openingLabel: String): String {
        val sb = StringBuilder()
        sb.append("*").append(l.name).append(" - ").append(batch.name).append("*\n")
        if (batch.opening != 0.0) sb.append(openingLabel).append(": ").append(withUnit(l, batch.opening, true)).append("\n")
        for (line in l.lines) {
            sb.append(line.label).append(": ").append(withUnit(l, lineValue(l, line, batch.id), line.money)).append("\n")
        }
        sb.append("*").append(l.balanceLabel).append(": ").append(withUnit(l, balance(l, batch), true)).append("*")
        return sb.toString()
    }

    fun fullText(l: Ledger, batch: Batch, openingLabel: String): String {
        val sb = StringBuilder()
        sb.append(summaryText(l, batch, openingLabel)).append("\n")
        for (t in l.tables) {
            sb.append("\n").append(tableText(l, t, batch)).append("\n")
        }
        return sb.toString().trimEnd()
    }

    fun csv(l: Ledger, t: TableDef, batch: Batch): String {
        fun q(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""
        val sb = StringBuilder()
        sb.append(q("date"))
        for (c in t.columns) sb.append(",").append(q(c.name))
        sb.append("\n")
        val entries = l.entries.filter { it.batchId == batch.id && it.tableId == t.id }.sortedWith(compareBy({ it.date }, { it.id }))
        for (e in entries) {
            sb.append(q(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(e.date))))
            for (c in t.columns) {
                if (c.kind == COL_TEXT) sb.append(",").append(q(e.values[c.id] ?: ""))
                else sb.append(",").append(plain(cell(t, c, e)))
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}

/** Ledgers live in one JSON file (files/ledger.json), written atomically. */
object LedgerStore {
    private fun file(c: Context) = File(c.filesDir, "ledger.json")

    @Synchronized
    fun load(c: Context): List<Ledger> {
        val f = file(c)
        if (!f.exists()) return emptyList()
        return try {
            val root = JSONObject(f.readText())
            val arr = root.optJSONArray("ledgers") ?: JSONArray()
            val out = ArrayList<Ledger>()
            for (i in 0 until arr.length()) out.add(readLedger(arr.getJSONObject(i)))
            out
        } catch (e: Throwable) {
            AppLog.log("LEDGER", "Could not read ledger.json", e)
            emptyList()
        }
    }

    @Synchronized
    fun save(c: Context, list: List<Ledger>) {
        try {
            val root = JSONObject()
            val arr = JSONArray()
            for (l in list) arr.put(writeLedger(l))
            root.put("v", 1)
            root.put("ledgers", arr)
            val tmp = File(c.filesDir, "ledger.json.tmp")
            tmp.writeText(root.toString())
            val dst = file(c)
            if (!tmp.renameTo(dst)) {
                dst.writeText(root.toString())
                tmp.delete()
            }
        } catch (e: Throwable) {
            AppLog.log("LEDGER", "Could not save ledger.json", e)
        }
    }

    @Synchronized
    fun update(c: Context, change: (List<Ledger>) -> List<Ledger>) {
        save(c, change(load(c)))
    }

    fun fileSize(c: Context): Long = if (file(c).exists()) file(c).length() else 0L

    private fun readLedger(o: JSONObject): Ledger {
        val tables = ArrayList<TableDef>()
        val ta = o.optJSONArray("tables") ?: JSONArray()
        for (i in 0 until ta.length()) {
            val to = ta.getJSONObject(i)
            val cols = ArrayList<ColumnDef>()
            val ca = to.optJSONArray("cols") ?: JSONArray()
            for (j in 0 until ca.length()) {
                val co = ca.getJSONObject(j)
                cols.add(
                    ColumnDef(
                        id = co.getLong("id"),
                        name = co.optString("name", ""),
                        kind = co.optInt("kind", COL_TEXT),
                        sumIt = co.optBoolean("sum", true),
                        refA = co.optLong("a", 0L),
                        refB = co.optLong("b", 0L),
                        def = co.optString("d", ""),
                    )
                )
            }
            tables.add(TableDef(to.getLong("id"), to.optString("name", ""), cols))
        }
        val lines = ArrayList<SummaryLine>()
        val la = o.optJSONArray("lines") ?: JSONArray()
        for (i in 0 until la.length()) {
            val lo = la.getJSONObject(i)
            lines.add(
                SummaryLine(
                    id = lo.getLong("id"),
                    label = lo.optString("label", ""),
                    tableId = lo.optLong("t", 0L),
                    colId = lo.optLong("c", 0L),
                    sign = lo.optInt("sign", 0),
                    money = lo.optBoolean("money", false),
                    row = lo.optInt("r", 0),
                )
            )
        }
        val batches = ArrayList<Batch>()
        val ba = o.optJSONArray("batches") ?: JSONArray()
        for (i in 0 until ba.length()) {
            val bo = ba.getJSONObject(i)
            batches.add(
                Batch(
                    id = bo.getLong("id"),
                    name = bo.optString("name", ""),
                    openedAt = bo.optLong("opened", 0L),
                    closedAt = bo.optLong("closed", 0L),
                    opening = bo.optDouble("opening", 0.0),
                    closedText = bo.optString("text", ""),
                )
            )
        }
        val entries = ArrayList<Entry>()
        val ea = o.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until ea.length()) {
            val eo = ea.getJSONObject(i)
            val vals = HashMap<Long, String>()
            val vo = eo.optJSONObject("v")
            if (vo != null) {
                val keys = vo.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val kl = k.toLongOrNull()
                    if (kl != null) vals[kl] = vo.optString(k, "")
                }
            }
            entries.add(Entry(eo.getLong("id"), eo.optLong("t", 0L), eo.optLong("b", 0L), eo.optLong("date", 0L), vals))
        }
        return Ledger(
            id = o.getLong("id"),
            name = o.optString("name", ""),
            unit = o.optString("unit", ""),
            balanceLabel = o.optString("balance", ""),
            tables = tables,
            lines = lines,
            batches = batches,
            entries = entries,
        )
    }

    private fun writeLedger(l: Ledger): JSONObject {
        val o = JSONObject()
        o.put("id", l.id)
        o.put("name", l.name)
        o.put("unit", l.unit)
        o.put("balance", l.balanceLabel)
        val ta = JSONArray()
        for (t in l.tables) {
            val to = JSONObject()
            to.put("id", t.id)
            to.put("name", t.name)
            val ca = JSONArray()
            for (c in t.columns) {
                val co = JSONObject()
                co.put("id", c.id)
                co.put("name", c.name)
                co.put("kind", c.kind)
                co.put("sum", c.sumIt)
                co.put("a", c.refA)
                co.put("b", c.refB)
                co.put("d", c.def)
                ca.put(co)
            }
            to.put("cols", ca)
            ta.put(to)
        }
        o.put("tables", ta)
        val la = JSONArray()
        for (line in l.lines) {
            val lo = JSONObject()
            lo.put("id", line.id)
            lo.put("label", line.label)
            lo.put("t", line.tableId)
            lo.put("c", line.colId)
            lo.put("sign", line.sign)
            lo.put("money", line.money)
            lo.put("r", line.row)
            la.put(lo)
        }
        o.put("lines", la)
        val ba = JSONArray()
        for (b in l.batches) {
            val bo = JSONObject()
            bo.put("id", b.id)
            bo.put("name", b.name)
            bo.put("opened", b.openedAt)
            bo.put("closed", b.closedAt)
            bo.put("opening", b.opening)
            bo.put("text", b.closedText)
            ba.put(bo)
        }
        o.put("batches", ba)
        val ea = JSONArray()
        for (e in l.entries) {
            val eo = JSONObject()
            eo.put("id", e.id)
            eo.put("t", e.tableId)
            eo.put("b", e.batchId)
            eo.put("date", e.date)
            val vo = JSONObject()
            for ((k, v) in e.values) vo.put(k.toString(), v)
            eo.put("v", vo)
            ea.put(eo)
        }
        o.put("entries", ea)
        return o
    }
}

/** Ready-made ledgers. All names come from string resources (so they follow the app language). */
object LedgerTemplates {
    const val FACTORY = 0
    const val HOME = 1
    const val BLANK = 2

    fun create(c: Context, kind: Int, name: String): Ledger {
        val now = System.currentTimeMillis()
        val batch = Batch(Store.newId(), LocaleHelper.wrap(c).getString(R.string.batch_first), now)
        val unit = LocaleHelper.wrap(c).getString(R.string.currency_unit)
        when (kind) {
            FACTORY -> {
                val cBags = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_bags), COL_NUMBER)
                val cNote1 = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_note), COL_TEXT)
                val tWool = TableDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.tbl_wool), listOf(cBags, cNote1))

                val cType = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_type), COL_TEXT)
                val cQty = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_pieces), COL_NUMBER)
                val cPrice = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_price), COL_MONEY, sumIt = false)
                val cTotal = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_total), COL_CALC, true, cQty.id, cPrice.id)
                val tDeliver = TableDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.tbl_deliver), listOf(cType, cQty, cPrice, cTotal))

                val cAmount = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_amount), COL_MONEY)
                val cNote2 = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_note), COL_TEXT)
                val tMoney = TableDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.tbl_money), listOf(cAmount, cNote2))

                val lines = listOf(
                    SummaryLine(Store.newId(), LocaleHelper.wrap(c).getString(R.string.line_bags), tWool.id, cBags.id, 0, false),
                    SummaryLine(Store.newId(), LocaleHelper.wrap(c).getString(R.string.line_pieces), tDeliver.id, cQty.id, 0, false),
                    SummaryLine(Store.newId(), LocaleHelper.wrap(c).getString(R.string.line_due), tDeliver.id, cTotal.id, 1, true),
                    SummaryLine(Store.newId(), LocaleHelper.wrap(c).getString(R.string.line_received), tMoney.id, cAmount.id, -1, true, 1),
                )
                return Ledger(Store.newId(), name, unit, LocaleHelper.wrap(c).getString(R.string.balance_remaining), listOf(tWool, tDeliver, tMoney), lines, listOf(batch), emptyList())
            }
            HOME -> {
                val cSrc = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_source), COL_TEXT)
                val cInc = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_amount), COL_MONEY)
                val tInc = TableDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.tbl_income), listOf(cSrc, cInc))
                val cItem = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_item), COL_TEXT)
                val cExp = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_amount), COL_MONEY)
                val tExp = TableDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.tbl_expense), listOf(cItem, cExp))
                val lines = listOf(
                    SummaryLine(Store.newId(), LocaleHelper.wrap(c).getString(R.string.line_income), tInc.id, cInc.id, 1, true, 1),
                    SummaryLine(Store.newId(), LocaleHelper.wrap(c).getString(R.string.line_expense), tExp.id, cExp.id, -1, true, 1),
                )
                return Ledger(Store.newId(), name, unit, LocaleHelper.wrap(c).getString(R.string.balance_left), listOf(tInc, tExp), lines, listOf(batch), emptyList())
            }
            else -> {
                val cItem = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_item), COL_TEXT)
                val cAmt = ColumnDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.col_amount), COL_MONEY)
                val t = TableDef(Store.newId(), LocaleHelper.wrap(c).getString(R.string.tbl_default), listOf(cItem, cAmt))
                val lines = listOf(SummaryLine(Store.newId(), LocaleHelper.wrap(c).getString(R.string.line_total), t.id, cAmt.id, 1, true, 1))
                return Ledger(Store.newId(), name, unit, LocaleHelper.wrap(c).getString(R.string.balance_total), listOf(t), lines, listOf(batch), emptyList())
            }
        }
    }
}
