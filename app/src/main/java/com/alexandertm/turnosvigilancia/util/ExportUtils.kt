package com.alexandertm.turnosvigilancia.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.alexandertm.turnosvigilancia.data.AppSnapshot
import com.alexandertm.turnosvigilancia.data.Assignment
import com.alexandertm.turnosvigilancia.data.Guard
import com.alexandertm.turnosvigilancia.data.monthAssignments
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportUtils {
    fun exportPdf(context: Context, snapshot: AppSnapshot, yearMonth: YearMonth): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "cronograma_${yearMonth}.pdf")
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(1191, 842, 1).create()
        val page = document.startPage(pageInfo)
        drawSchedule(page.canvas, snapshot, yearMonth, 1191, 842, compact = true)
        document.finishPage(page)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
    }

    fun exportPng(context: Context, snapshot: AppSnapshot, yearMonth: YearMonth): File {
        val guards = guardsForMonth(snapshot, yearMonth)
        val width = 3000
        val height = (220 + guards.size * 90 + 180).coerceAtLeast(700)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawSchedule(canvas, snapshot, yearMonth, width, height, compact = false)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "cronograma_${yearMonth}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    fun exportXlsx(context: Context, snapshot: AppSnapshot, yearMonth: YearMonth): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "cronograma_${yearMonth}.xlsx")
        val guards = guardsForMonth(snapshot, yearMonth)
        val assignments = monthAssignments(snapshot.assignments, yearMonth.year, yearMonth.monthValue)
            .associateBy { it.guardId to it.date }

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            fun entry(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypesXml())
            entry("_rels/.rels", rootRelsXml())
            entry("xl/workbook.xml", workbookXml())
            entry("xl/_rels/workbook.xml.rels", workbookRelsXml())
            entry("xl/styles.xml", stylesXml())
            entry("xl/worksheets/sheet1.xml", sheetXml(snapshot, yearMonth, guards, assignments))
        }
        return file
    }

    fun createBackupFile(context: Context, json: String): File {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        return File(dir, "turnos_backup_${System.currentTimeMillis()}.json").apply { writeText(json) }
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir archivo"))
    }

    private fun guardsForMonth(snapshot: AppSnapshot, ym: YearMonth): List<Guard> {
        val idsWithData = monthAssignments(snapshot.assignments, ym.year, ym.monthValue).map { it.guardId }.toSet()
        return snapshot.guards.filter { it.active || it.id in idsWithData }.sortedBy { it.name.lowercase() }
    }

    private fun drawSchedule(canvas: Canvas, snapshot: AppSnapshot, ym: YearMonth, width: Int, height: Int, compact: Boolean) {
        canvas.drawColor(Color.WHITE)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42); textSize = if (compact) 30f else 54f; typeface = Typeface.DEFAULT_BOLD
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 23, 42); textSize = if (compact) 10f else 28f
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(71, 85, 105); textSize = if (compact) 8f else 24f
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(226, 232, 240); strokeWidth = if (compact) 1f else 2f }
        val locale = Locale("es", "PE")
        val monthName = ym.month.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.uppercase(locale) }
        canvas.drawText("Cronograma de Turnos - $monthName ${ym.year}", 30f, if (compact) 48f else 80f, titlePaint)

        val guards = guardsForMonth(snapshot, ym)
        val assignments = monthAssignments(snapshot.assignments, ym.year, ym.monthValue).associateBy { it.guardId to it.date }
        val left = if (compact) 25f else 40f
        val top = if (compact) 80f else 130f
        val nameWidth = if (compact) 170f else 520f
        val rightMargin = if (compact) 25f else 40f
        val dayWidth = (width - left - rightMargin - nameWidth) / ym.lengthOfMonth().toFloat()
        val rowHeight = if (compact) 40f else 78f

        canvas.drawText("Vigilante", left + 6, top + rowHeight * .68f, textPaint)
        for (day in 1..ym.lengthOfMonth()) {
            val x = left + nameWidth + (day - 1) * dayWidth
            canvas.drawText(day.toString(), x + dayWidth * .2f, top + rowHeight * .68f, smallPaint)
        }
        canvas.drawLine(left, top + rowHeight, width - rightMargin, top + rowHeight, linePaint)

        guards.forEachIndexed { index, guard ->
            val y = top + rowHeight * (index + 1)
            canvas.drawText(guard.name.take(if (compact) 24 else 36), left + 6, y + rowHeight * .65f, textPaint)
            for (day in 1..ym.lengthOfMonth()) {
                val date = ym.atDay(day)
                val a = assignments[guard.id to date.toString()]
                val x = left + nameWidth + (day - 1) * dayWidth
                if (a != null) {
                    val color = statusColor(snapshot, a)
                    val fill = Paint().apply { this.color = color; alpha = 55 }
                    canvas.drawRect(x + 1, y + 2, x + dayWidth - 1, y + rowHeight - 2, fill)
                    val code = a.summary().replace(" + ", "/")
                    canvas.drawText(code.take(if (compact) 5 else 8), x + 2, y + rowHeight * .65f, smallPaint)
                }
                canvas.drawLine(x, y, x, y + rowHeight, linePaint)
            }
            canvas.drawLine(left, y + rowHeight, width - rightMargin, y + rowHeight, linePaint)
        }

        val legendY = (top + rowHeight * (guards.size + 2) + if (compact) 20 else 50).coerceAtMost(height - 60f)
        canvas.drawText("Leyenda: D Día | N Noche | DES Descanso | VAC Vacaciones | PER Permiso | FAL Falta | REP Reemplazo", left, legendY, smallPaint)
    }

    private fun statusColor(snapshot: AppSnapshot, a: Assignment): Int {
        val key = when {
            a.situation != null -> a.situation.name
            a.primaryShift != null -> a.primaryShift.name
            else -> "DAY"
        }
        val hex = snapshot.settings.colors[key] ?: "#9AA5B1"
        return runCatching { Color.parseColor(hex) }.getOrDefault(Color.LTGRAY)
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun columnName(index1: Int): String {
        var n = index1
        val sb = StringBuilder()
        while (n > 0) {
            n--
            sb.insert(0, ('A'.code + (n % 26)).toChar())
            n /= 26
        }
        return sb.toString()
    }

    private fun sheetXml(
        snapshot: AppSnapshot,
        ym: YearMonth,
        guards: List<Guard>,
        assignments: Map<Pair<String, String>, Assignment>
    ): String {
        val rows = StringBuilder()
        var rowNum = 1
        fun cell(ref: String, value: String, style: Int = 0) =
            "<c r=\"$ref\" t=\"inlineStr\" s=\"$style\"><is><t>${xmlEscape(value)}</t></is></c>"

        rows.append("<row r=\"$rowNum\">")
        rows.append(cell("A$rowNum", "Vigilante", 1))
        for (d in 1..ym.lengthOfMonth()) rows.append(cell("${columnName(d + 1)}$rowNum", d.toString(), 1))
        rows.append("</row>")
        rowNum++

        guards.forEach { guard ->
            rows.append("<row r=\"$rowNum\">")
            rows.append(cell("A$rowNum", guard.name, 1))
            for (d in 1..ym.lengthOfMonth()) {
                val a = assignments[guard.id to ym.atDay(d).toString()]
                val style = styleFor(a)
                rows.append(cell("${columnName(d + 1)}$rowNum", a?.summary()?.replace(" + ", "/") ?: "", style))
            }
            rows.append("</row>")
            rowNum++
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetViews><sheetView workbookViewId="0"/></sheetViews>
  <cols><col min="1" max="1" width="28" customWidth="1"/><col min="2" max="32" width="6" customWidth="1"/></cols>
  <sheetData>$rows</sheetData>
</worksheet>"""
    }

    private fun styleFor(a: Assignment?): Int = when {
        a == null -> 0
        a.situation?.name == "REST" -> 4
        a.situation?.name == "VACATION" -> 5
        a.situation?.name == "PERMISSION" -> 6
        a.situation?.name == "ABSENCE" -> 7
        a.situation?.name == "REPLACEMENT" -> 8
        a.primaryShift?.name == "NIGHT" -> 3
        else -> 2
    }

    private fun contentTypesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Cronograma" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

    private fun workbookRelsXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun stylesXml() = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
<fills count="9">
<fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFDCEEFF"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFEDE3FF"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFE5E7EB"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFD9F7E8"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFE8B2"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFDADA"/><bgColor indexed="64"/></patternFill></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFFFD9EC"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="9">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="0" fillId="2" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="0" fillId="4" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="0" fillId="5" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="0" fillId="6" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="0" fillId="7" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="0" fillId="8" borderId="0" xfId="0"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""
}
