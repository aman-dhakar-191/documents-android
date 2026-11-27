package io.github.amandhakar.documents.sheets

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Shows stored numbers the way the cell's format asks, close enough to Excel for reading: decimals, thousands separators,
 * percentages, scientific notation, currency signs and text around the number, and dates and times (written as
 * 2026-11-27 and 14:30 so they read the same in every country). Fractions, conditions such as [>100] and colours are
 * ignored; such cells fall back to Excel's General look. Free of Android calls so it can be unit tested.
 */
object XlsxNumbers {
    private val DATE_IDS = (14..22).toSet() + setOf(27, 30, 36, 45, 46, 47, 50, 57)
    private val BUILT_IN = mapOf(
        1 to "0", 2 to "0.00", 3 to "#,##0", 4 to "#,##0.00", 9 to "0%", 10 to "0.00%", 11 to "0.00E+00",
        37 to "#,##0 ;(#,##0)", 38 to "#,##0 ;(#,##0)", 39 to "#,##0.00;(#,##0.00)", 40 to "#,##0.00;(#,##0.00)",
        48 to "##0.0E+0", 49 to "@",
        14 to "yyyy-mm-dd", 15 to "d-mmm-yy", 16 to "d-mmm", 17 to "mmm-yy", 18 to "h:mm AM/PM", 19 to "h:mm:ss AM/PM",
        20 to "h:mm", 21 to "h:mm:ss", 22 to "yyyy-mm-dd h:mm", 45 to "mm:ss", 46 to "[h]:mm:ss", 47 to "mm:ss.0",
    )

    private fun codeOf(format: NumberFormat): String? = format.code ?: BUILT_IN[format.id]

    /** True when the format shows dates or times (so typing "2026-11-27" there stores a date). */
    fun isDate(format: NumberFormat): Boolean {
        if (format.id in DATE_IDS && format.code == null) return true
        val code = codeOf(format) ?: return false
        return dateTokens(firstSection(code)).let { it.hasDate || it.hasTime }
    }

    /** What a number cell shows. [raw] is the number as stored in the file. */
    fun format(raw: String, format: NumberFormat, date1904: Boolean = false): String {
        val value = raw.trim().toBigDecimalOrNull() ?: return raw
        val code = codeOf(format)
        if (code == null || code.equals("General", ignoreCase = true)) return general(value)
        if (code == "@") return raw
        if (isDate(format)) return date(value, code, date1904) ?: general(value)
        val sections = splitSections(code)
        val negative = value.signum() < 0
        val section = when {
            negative && sections.size >= 2 -> sections[1]
            value.signum() == 0 && sections.size >= 3 -> sections[2]
            else -> sections[0]
        }
        val shown = if (negative && sections.size >= 2) value.abs() else value
        return number(shown, section) ?: general(value)
    }

    /** Excel's General look: up to 11 significant digits, no trailing zeros, scientific notation for very big or small numbers. */
    fun general(value: BigDecimal): String {
        if (value.signum() == 0) return "0"
        val rounded = value.round(MathContext(11, RoundingMode.HALF_UP)).stripTrailingZeros()
        val magnitude = rounded.precision() - rounded.scale() - 1 // the power of ten of the first digit
        if (magnitude >= 11 || magnitude < -9) {
            val mantissa = rounded.movePointLeft(magnitude).round(MathContext(6, RoundingMode.HALF_UP)).stripTrailingZeros()
            val sign = if (magnitude < 0) "-" else "+"
            return mantissa.toPlainString() + "E" + sign + String.format("%02d", kotlin.math.abs(magnitude))
        }
        return rounded.toPlainString()
    }

    // ---- numbers ----

    private fun splitSections(code: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                c == '"' -> { quoted = !quoted; sb.append(c) }
                c == '\\' && i + 1 < code.length -> { sb.append(c).append(code[i + 1]); i++ }
                c == ';' && !quoted -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    private fun firstSection(code: String) = splitSections(code)[0]

    /** The number in [value] laid out by one section of a format code, or null when the section isn't understood. */
    private fun number(value: BigDecimal, section: String): String? {
        if (section.contains("general", ignoreCase = true)) return null
        val prefix = StringBuilder()
        val suffix = StringBuilder()
        var pattern = StringBuilder()
        var seenDigits = false
        var i = 0
        while (i < section.length) {
            val c = section[i]
            val target = if (seenDigits) suffix else prefix
            when {
                c == '"' -> {
                    val end = section.indexOf('"', i + 1).let { if (it < 0) section.length else it }
                    target.append(section, i + 1, end)
                    i = end
                }
                c == '\\' && i + 1 < section.length -> { target.append(section[i + 1]); i++ }
                c == '_' && i + 1 < section.length -> { target.append(' '); i++ }
                c == '*' && i + 1 < section.length -> i++
                c == '[' -> {
                    val end = section.indexOf(']', i).let { if (it < 0) section.length else it }
                    val inside = section.substring(i + 1, end)
                    // [$€-407] is a currency sign; [Red], [>100] and the like are ignored.
                    if (inside.startsWith("$")) target.append(inside.drop(1).substringBefore('-'))
                    i = end
                }
                c in "0#?.,%" || ((c == 'E' || c == 'e') && seenDigits && i + 1 < section.length && section[i + 1] in "+-") -> {
                    if (suffix.isNotEmpty() && c != '%') return null // digits on both sides of text: not understood
                    if (c == '%') {
                        if (seenDigits) suffix.append('%') else prefix.append('%')
                        pattern.append('%')
                    } else {
                        seenDigits = seenDigits || c in "0#?"
                        pattern.append(c)
                        if (c == 'E' || c == 'e') {
                            pattern.append(section[i + 1])
                            i++
                            while (i + 1 < section.length && section[i + 1] in "0#") { pattern.append(section[i + 1]); i++ }
                        }
                    }
                }
                c == '/' -> return null // fractions
                c == '@' -> return null
                else -> target.append(c)
            }
            i++
        }
        if (!seenDigits) return prefix.toString().takeIf { it.isNotEmpty() } // a section of text only, like "zero"
        val p = pattern.toString()
        var v = value
        repeat(p.count { it == '%' }) { v = v.movePointRight(2) }
        val core = p.replace("%", "")
        val expAt = core.indexOfFirst { it == 'E' || it == 'e' }
        val body = if (expAt >= 0) core.substring(0, expAt) else core
        val dot = body.indexOf('.')
        val intPart = if (dot >= 0) body.substring(0, dot) else body
        val fracPart = if (dot >= 0) body.substring(dot + 1) else ""
        // Trailing commas after the digits divide by 1000 each ("#,##0," shows thousands).
        val trailingCommas = intPart.length - intPart.trimEnd(',').length
        repeat(trailingCommas) { v = v.movePointLeft(3) }
        val grouping = intPart.trimEnd(',').contains(',')
        val decimals = fracPart.count { it == '0' || it == '#' || it == '?' }
        val minDecimals = fracPart.count { it == '0' }
        val minInt = intPart.count { it == '0' }
        val text = if (expAt >= 0) {
            scientific(v, decimals, core.substring(expAt + 2).length.coerceAtLeast(1))
        } else {
            val scaled = v.setScale(decimals, RoundingMode.HALF_UP)
            var digits = scaled.abs().toPlainString()
            // Optional decimals (#) drop their trailing zeros, down to the required ones (0).
            if (decimals > minDecimals && digits.contains('.')) {
                var d = digits.trimEnd('0')
                val have = d.length - d.indexOf('.') - 1
                if (have < minDecimals) d += "0".repeat(minDecimals - have)
                digits = d.trimEnd('.')
            }
            var whole = digits.substringBefore('.')
            val frac = if (digits.contains('.')) "." + digits.substringAfter('.') else ""
            if (whole == "0" && minInt == 0) whole = ""
            if (whole.length < minInt) whole = "0".repeat(minInt - whole.length) + whole
            if (grouping) whole = group(whole)
            (if (scaled.signum() < 0) "-" else "") + whole + frac
        }
        return prefix.toString() + text + suffix.toString()
    }

    private fun group(digits: String): String {
        val sb = StringBuilder()
        for ((n, c) in digits.reversed().withIndex()) {
            if (n > 0 && n % 3 == 0) sb.append(',')
            sb.append(c)
        }
        return sb.reverse().toString()
    }

    private fun scientific(v: BigDecimal, decimals: Int, expDigits: Int): String {
        if (v.signum() == 0) return "0" + (if (decimals > 0) "." + "0".repeat(decimals) else "") + "E+" + "0".repeat(expDigits)
        val magnitude = v.precision() - v.scale() - 1
        var mantissa = v.movePointLeft(magnitude).setScale(decimals, RoundingMode.HALF_UP)
        var exp = magnitude
        if (mantissa.abs() >= BigDecimal.TEN) {
            mantissa = mantissa.movePointLeft(1).setScale(decimals, RoundingMode.HALF_UP)
            exp++
        }
        val sign = if (exp < 0) "-" else "+"
        return mantissa.toPlainString() + "E" + sign + kotlin.math.abs(exp).toString().padStart(expDigits, '0')
    }

    // ---- dates ----

    private class DateTokens(val hasDate: Boolean, val hasTime: Boolean, val hasSeconds: Boolean, val elapsedHours: Boolean, val ampm: Boolean)

    private fun dateTokens(code: String): DateTokens {
        var date = false
        var time = false
        var seconds = false
        var elapsed = false
        var i = 0
        val lower = code.lowercase()
        while (i < lower.length) {
            val c = lower[i]
            when {
                c == '"' -> i = lower.indexOf('"', i + 1).let { if (it < 0) lower.length else it }
                c == '\\' -> i++
                c == '[' -> {
                    val end = lower.indexOf(']', i).let { if (it < 0) lower.length else it }
                    val inside = lower.substring(i + 1, end)
                    if (inside.isNotEmpty() && inside.all { it == 'h' || it == 'm' || it == 's' }) {
                        elapsed = true
                        time = true
                    }
                    i = end
                }
                c == 'y' || c == 'd' -> date = true
                c == 'h' -> time = true
                c == 's' -> { time = true; seconds = true }
                c == 'm' -> { /* month or minute: decided by its neighbours, either way a date or time format */ if (!time) date = true }
                c == '0' || c == '#' -> if (!date && !time) return DateTokens(false, false, false, false, false)
            }
            i++
        }
        // "mm:ss" or "h:mm" alone is a time, not a month.
        if (date && !lower.contains('y') && !lower.contains('d') && lower.contains(':')) date = false
        return DateTokens(date, time, seconds, elapsed, lower.contains("am/pm") || lower.contains("a/p"))
    }

    private val BASE_1900: LocalDateTime = LocalDateTime.of(1899, 12, 30, 0, 0)
    private val BASE_1904: LocalDateTime = LocalDateTime.of(1904, 1, 1, 0, 0)

    /** The date and time for a stored serial number (days since the workbook's start date, with Excel's 1900 leap-year quirk). */
    fun dateTime(serial: BigDecimal, date1904: Boolean): LocalDateTime? {
        if (serial.signum() < 0 || serial > BigDecimal(2_958_466)) return null
        val days = serial.toLong()
        val millis = serial.subtract(BigDecimal(days)).multiply(BigDecimal(86_400_000)).setScale(0, RoundingMode.HALF_UP).toLong()
        val base = when {
            date1904 -> BASE_1904
            days < 61 -> BASE_1900.plusDays(1) // before Excel's made-up 29 Feb 1900
            else -> BASE_1900
        }
        return base.plusDays(days).plus(millis, ChronoUnit.MILLIS)
    }

    private fun date(value: BigDecimal, code: String, date1904: Boolean): String? {
        val tokens = dateTokens(firstSection(code))
        if (tokens.elapsedHours) {
            val totalSeconds = value.multiply(BigDecimal(86_400)).setScale(0, RoundingMode.HALF_UP).toLong()
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return if (tokens.hasSeconds) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", h, m)
        }
        val dt = dateTime(value, date1904) ?: return null
        val time = when {
            !tokens.hasTime -> ""
            tokens.ampm -> dt.format(DateTimeFormatter.ofPattern(if (tokens.hasSeconds) "h:mm:ss a" else "h:mm a", java.util.Locale.ENGLISH))
            else -> dt.format(DateTimeFormatter.ofPattern(if (tokens.hasSeconds) "HH:mm:ss" else "HH:mm"))
        }
        val day = if (tokens.hasDate) dt.toLocalDate().toString() else ""
        return listOf(day, time).filter { it.isNotEmpty() }.joinToString(" ")
    }

    // ---- what the person types ----

    private val NUMBER = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?")
    private val PERCENT = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)%")
    private val ISO_DATE = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})(?:[ T](\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?")
    private val CLOCK = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")

    /**
     * What typed [text] is stored as in a cell with [format]: a number ("12.5", "-3", "1e3"; "12%" as 0.12), a date or
     * time when the cell shows dates ("2026-11-27", "2026-11-27 14:30", "14:30"), nothing when empty, otherwise text.
     * Formulas aren't typed here: "=SUM(A1:A3)" is kept as text.
     */
    fun interpret(text: String, format: NumberFormat, date1904: Boolean = false): CellInput {
        if (text.isEmpty()) return CellInput.Empty
        val t = text.trim()
        if (t != text) return CellInput.Text(text) // spaces around it: the person means text
        if (NUMBER.matches(t)) return CellInput.Number(canonical(BigDecimal(t)))
        if (PERCENT.matches(t)) return CellInput.Number(canonical(BigDecimal(t.dropLast(1)).movePointLeft(2)))
        if (isDate(format)) {
            ISO_DATE.matchEntire(t)?.let { m ->
                val date = runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
                    ?: return CellInput.Text(text)
                val time = if (m.groupValues[4].isNotEmpty()) {
                    runCatching { LocalTime.of(m.groupValues[4].toInt(), m.groupValues[5].toInt(), m.groupValues[6].ifEmpty { "0" }.toInt()) }.getOrNull()
                        ?: return CellInput.Text(text)
                } else {
                    LocalTime.MIDNIGHT
                }
                val serial = serialOf(LocalDateTime.of(date, time), date1904) ?: return CellInput.Text(text)
                return CellInput.Number(serial)
            }
            CLOCK.matchEntire(t)?.let { m ->
                val time = runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].ifEmpty { "0" }.toInt()) }.getOrNull()
                    ?: return CellInput.Text(text)
                return CellInput.Number(canonical(BigDecimal(time.toSecondOfDay()).divide(BigDecimal(86_400), MathContext(15))))
            }
        }
        if (t.equals("TRUE", ignoreCase = true) || t.equals("FALSE", ignoreCase = true)) return CellInput.Bool(t.equals("TRUE", ignoreCase = true))
        return CellInput.Text(text)
    }

    private fun serialOf(dt: LocalDateTime, date1904: Boolean): String? {
        val base = if (date1904) BASE_1904 else BASE_1900
        var days = ChronoUnit.DAYS.between(base.toLocalDate(), dt.toLocalDate())
        if (!date1904 && days < 61) days -= 1
        if (days < (if (date1904) 0 else 1)) return null
        val fraction = BigDecimal(dt.toLocalTime().toSecondOfDay()).divide(BigDecimal(86_400), MathContext(15))
        return canonical(BigDecimal(days).add(fraction))
    }

    /** A number as Excel writes it: no exponent, no trailing zeros, no "+". */
    private fun canonical(v: BigDecimal): String {
        val s = v.stripTrailingZeros()
        return if (s.signum() == 0) "0" else s.toPlainString()
    }
}

/** What a typed cell value is stored as. */
sealed interface CellInput {
    data object Empty : CellInput
    data class Number(val value: String) : CellInput
    data class Bool(val value: Boolean) : CellInput
    data class Text(val value: String) : CellInput
}
