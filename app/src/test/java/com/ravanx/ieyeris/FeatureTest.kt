package com.ravanx.ieyeris

import org.junit.Test
import org.junit.Assert.assertEquals
import java.util.Calendar

/**
 * v2.2 — spec section 26/27/28/29 ki jaanch
 *
 * Reminder ka waqt samajhna, mode pehchanna, translate pakadna.
 * Ye asli Reminders.kt aur Modes.kt ka code chalata hai.
 */
class FeatureTest {

    // ── asli Reminders.parse() ──
    val NORMAL = "normal"; val STUDY = "study"; val CODING = "coding"
    fun parse(raw: String): Triple<Long, String, String> {
        var t = raw.lowercase().trim()
            .replace(Regex("[?!,]"), " ")
            .replace(Regex("\\s+"), " ")

        // hukum wale shabd hata do
        for (w in listOf("yaad dila do", "yaad dilana", "yaad dilao",
            "reminder laga do", "reminder lagao", "reminder set karo",
            "reminder", "yaad rakhna", "alarm laga do", "mujhe",
            "ek", "please")) {
            t = t.replace(w, " ")
        }
        t = t.replace(Regex("\\s+"), " ").trim()

        var repeat = ""
        if (Regex("(^|\\s)(roz|rozana|daily|har din|hamesha)(\\s|$)")
                .containsMatchIn(t)) {
            repeat = "daily"
            t = t.replace(Regex("(roz|rozana|daily|har din|hamesha)"), " ")
        }
        val wk = Regex("har (somwar|mangalwar|budhwar|guruwar|" +
                       "shukrawar|shanivar|ravivar|monday|tuesday|" +
                       "wednesday|thursday|friday|saturday|sunday)")
            .find(t)
        if (wk != null) {
            repeat = "weekly"
            t = t.replace(wk.value, " ")
        }

        val cal = Calendar.getInstance()
        var found = false

        // ── "X minute/ghante baad" ──
        Regex("(\\d{1,3})\\s*(minute|min|mint|मिनट)\\b").find(t)?.let {
            cal.add(Calendar.MINUTE, it.groupValues[1].toInt())
            t = t.replace(it.value, " ")
            t = t.replace(Regex("\\b(baad|bad|me|mein|ke baad)\\b"), " ")
            found = true
        }
        if (!found) Regex("(\\d{1,2})\\s*(ghante|ghanta|hour|hr)\\b")
            .find(t)?.let {
                cal.add(Calendar.HOUR_OF_DAY, it.groupValues[1].toInt())
                t = t.replace(it.value, " ")
                t = t.replace(Regex("\\b(baad|bad|me|mein)\\b"), " ")
                found = true
            }

        // ── "kal" ──
        var tomorrow = false
        if (!found && Regex("(^|\\s)(kal|tomorrow)(\\s|$)")
                .containsMatchIn(t)) {
            tomorrow = true
            t = t.replace(Regex("(kal|tomorrow)"), " ")
        }

        // ── "subah 7 baje" / "7:30 pm" / "19:30" ──
        if (!found) {
            val part = when {
                Regex("subah|morning|savere").containsMatchIn(t) -> "am"
                Regex("dopahar|afternoon").containsMatchIn(t) -> "pm"
                Regex("shaam|sham|evening").containsMatchIn(t) -> "pm"
                Regex("raat|rat|night").containsMatchIn(t) -> "pm"
                Regex("\\bam\\b").containsMatchIn(t) -> "am"
                Regex("\\bpm\\b").containsMatchIn(t) -> "pm"
                else -> ""
            }
            val m = Regex("(\\d{1,2})\\s*[:.]?\\s*(\\d{2})?\\s*" +
                          "(baje|bje|am|pm|o'clock)?").find(t)
            if (m != null && m.groupValues[1].isNotBlank()) {
                var hh = m.groupValues[1].toIntOrNull() ?: -1
                val mm = m.groupValues[2].toIntOrNull() ?: 0
                if (hh in 0..23) {
                    // 12-ghante ko 24 me badlo
                    if (part == "pm" && hh < 12) hh += 12
                    if (part == "am" && hh == 12) hh = 0
                    // "raat 10 baje" par abhi 11 baj rahe -> kal
                    cal.set(Calendar.HOUR_OF_DAY, hh)
                    cal.set(Calendar.MINUTE, mm)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    if (tomorrow) cal.add(Calendar.DAY_OF_YEAR, 1)
                    else if (cal.timeInMillis <=
                             System.currentTimeMillis() + 5000)
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    t = t.replace(m.value, " ")
                    found = true
                }
            }
        }

        if (!found) return Triple(0L, repeat, raw.trim())

        // bacha hua = kaam ka naam
        var task = t
        for (w in listOf("subah", "savere", "morning", "dopahar",
            "afternoon", "shaam", "sham", "evening", "raat", "rat",
            "night", "baje", "bje", "am", "pm", "ko", "par", "pe",
            "baad", "bad", "me", "mein", "ka", "ki", "hai")) {
            task = task.replace(Regex("(^|\\s)$w(\\s|$)"), " ")
        }
        task = task.replace(Regex("\\s+"), " ").trim()
            .trim('.', '-', ':')
        if (task.length < 2) task = "Reminder"

        return Triple(cal.timeInMillis, repeat, task)
    }

    fun detect(t: String): String? {
        fun p(vararg w: String) = w.any { x ->
            Regex("(^|\\s)" + Regex.escape(x) + "($|\\s)")
                .containsMatchIn(t)
        }
        // ⚠️ TARTEEB ZAROORI HAI — "band" wala SABSE PEHLE.
        //
        //    Test ne ye pakda: "study mode band" ko pehle STUDY
        //    samajh liya jata tha, kyunki usme "study mode" bhi
        //    hai. User mode BAND karna chah raha tha par ULTA
        //    chalu ho jata tha.
        if (p("normal mode", "normal mode on", "mode band karo",
              "wapas normal", "study mode band", "coding mode band",
              "study mode band karo", "coding mode band karo",
              "mode hatao", "normal ho jao", "mode off",
              "study mode off", "coding mode off"))
            return NORMAL
        if (p("study mode", "padhai mode", "teacher mode",
              "study mode on", "padhai shuru", "padhao mujhe",
              "teacher ban jao", "study mode chalu karo"))
            return STUDY
        if (p("coding mode", "code mode", "programming mode",
              "coding mode on", "developer mode",
              "coding mode chalu karo", "coder ban jao"))
            return CODING
        return null
    }

    fun translation(raw: String): Pair<String, String>? {
        val t = raw.trim()
        val low = t.lowercase()

        // "<text> ko <bhasha> me translate/anuvaad karo"
        Regex("^(.{2,300}?)\\s+(?:ko|ka)\\s+" +
              "(english|angrezi|hindi|hindee|marathi|gujarati|" +
              "tamil|telugu|kannada|bengali|punjabi|urdu|" +
              "spanish|french|german|japanese|chinese|arabic)" +
              "\\s*(?:me|mein|m|main)?\\s*" +
              "(?:translate|anuvaad|tarjuma|convert|badlo|karo|kar do)",
              RegexOption.IGNORE_CASE).find(low)?.let {
            return Pair(lang(it.groupValues[2]),
                        cut(t, it.groupValues[1]))
        }

        // "translate karo <text> in english"
        Regex("^(?:translate|anuvaad|tarjuma)\\s+(?:karo|kar do)?\\s*" +
              "(.{2,300}?)\\s+(?:in|to|me|mein)\\s+" +
              "(english|angrezi|hindi|marathi|gujarati|tamil|telugu|" +
              "kannada|bengali|punjabi|urdu|spanish|french|german|" +
              "japanese|chinese|arabic)$",
              RegexOption.IGNORE_CASE).find(low)?.let {
            return Pair(lang(it.groupValues[2]),
                        cut(t, it.groupValues[1]))
        }

        // "<bhasha> me kya kehte hain <text>"
        Regex("^(english|angrezi|hindi|marathi|gujarati|tamil|telugu|" +
              "kannada|bengali|punjabi|urdu|spanish|french|german|" +
              "japanese|chinese|arabic)\\s*(?:me|mein|m)\\s*" +
              "(?:kya|kaise)\\s*(?:kehte|bolte|likhte)\\s*" +
              "(?:hain|hai|h)\\s*(.{2,300})$",
              RegexOption.IGNORE_CASE).find(low)?.let {
            return Pair(lang(it.groupValues[1]),
                        cut(t, it.groupValues[2]))
        }

        // sirf "translate karo" — pichhli baat translate karni hai
        if (Regex("^(?:isko|ise|ye|yeh)?\\s*" +
                  "(?:english|angrezi|hindi)\\s*(?:me|mein)?\\s*" +
                  "(?:translate|anuvaad|tarjuma|bolo|likho|karo)" +
                  "\\s*(?:karo|kar do)?$")
                .containsMatchIn(low)) {
            val l = if (low.contains("hindi")) "Hindi" else "English"
            return Pair(l, "")     // khali = pichhla message
        }
        return null
    }

    private fun cut(orig: String, lowPart: String): String {
        val i = orig.lowercase().indexOf(lowPart.trim())
        return if (i >= 0) orig.substring(i, i + lowPart.trim().length)
               else lowPart.trim()
    }

    private fun lang(s: String) = when (s.lowercase()) {
        "angrezi", "english" -> "English"
        "hindi", "hindee" -> "Hindi"
        else -> s.replaceFirstChar { it.uppercase() }
    }

    private val fails = mutableListOf<String>()

    // ═══ REMINDER ═══
    private fun rem(say: String, wantMin: Int?, wantRep: String,
                    wantTaskHas: String) {
        val (at, rep, task) = parse(say)
        if (at == 0L) {
            fails.add("\"$say\" -> waqt samajh nahi aaya"); return
        }
        if (rep != wantRep)
            fails.add("\"$say\" -> repeat chahiye=$wantRep mila=$rep")
        if (wantTaskHas.isNotBlank() &&
            !task.contains(wantTaskHas, true))
            fails.add("\"$say\" -> task me \"$wantTaskHas\" chahiye, mila \"$task\"")
        if (wantMin != null) {
            val diff = (at - System.currentTimeMillis()) / 60000.0
            if (Math.abs(diff - wantMin) > 2)
                fails.add("\"$say\" -> ~$wantMin min chahiye, mila ${diff.toInt()} min")
        }
        // har reminder AAGE ka hona chahiye — warna kabhi bajega hi nahi
        if (at <= System.currentTimeMillis())
            fails.add("\"$say\" -> guzra hua waqt! kabhi nahi bajega")
    }

    private fun remFail(say: String) {
        val (at, _, _) = parse(say)
        if (at != 0L) fails.add("\"$say\" -> waqt nahi hona chahiye tha")
    }

    // ═══ MODE ═══
    private fun md(say: String, want: String?) {
        val got = detect(say)
        if (got != want)
            fails.add("mode \"$say\" chahiye=$want mila=$got")
    }

    // ═══ TRANSLATE ═══
    private fun tr(say: String, wantLang: String?, wantText: String) {
        val got = translation(say)
        if (wantLang == null) {
            if (got != null) fails.add("tr \"$say\" -> null chahiye tha, mila $got")
            return
        }
        if (got == null) { fails.add("tr \"$say\" -> null mila"); return }
        if (got.first != wantLang)
            fails.add("tr \"$say\" bhasha chahiye=$wantLang mila=${got.first}")
        if (wantText.isNotBlank() && !got.second.contains(wantText, true))
            fails.add("tr \"$say\" text me \"$wantText\" chahiye, mila \"${got.second}\"")
    }

    // ═══ v4.0 — GAANA ═══
    private fun musCmd(t0: String): String? {
        val t = strip(t0)
        if (Regex("(gaana|gana|song|music|track)").containsMatchIn(t) ||
            Regex("(^|\\s)(play|pause)(\\s|$)").containsMatchIn(t)) {
            when {
                Regex("(agla|next|aage wala|skip)").containsMatchIn(t) ->
                    return "next"
                Regex("(pichla|previous|prev|peeche wala|wapas wala)")
                    .containsMatchIn(t) -> return "prev"
                Regex("(rok|pause|thehr)").containsMatchIn(t) -> return "pause"
                Regex("(gaana band|music band|song band|gana band)")
                    .containsMatchIn(t) -> return "stop"
                Regex("(chala|play|shuru|bajao|laga)").containsMatchIn(t) &&
                    !Regex("(youtube|yt|video)").containsMatchIn(t) ->
                    return "play"
            }
        }
        return null
    }
    private fun mus(say: String, want: String) {
        val g = musCmd(say)
        if (g != want) fails.add("music \"$say\" chahiye=$want mila=$g")
    }
    private fun musNone(say: String) {
        val g = musCmd(say)
        if (g != null) fails.add("music \"$say\" -> null chahiye tha, mila $g")
    }

    // ═══ v4.0 — NOTE ═══
    private fun noteCmd(t0: String): Pair<String, String>? {
        val t = strip(t0)
        if (Regex("(^|\\s)(note|notes)\\s*(dikhao|batao|padho|list|" +
                  "kya hai|sunao|suna do|dekhao)").containsMatchIn(t))
            return Pair("notes", "")
        Regex("^(?:note|likh|likh lo|likh do|note karo|yaad kar lo)" +
              "\\s+(.{2,200})$").find(t)?.let {
            return Pair("note", it.groupValues[1].trim())
        }
        return null
    }
    private fun nt(say: String, want: String) {
        val g = noteCmd(say)
        if (g == null || g.first != "note" || !g.second.contains(want, true))
            fails.add("note \"$say\" chahiye=\"$want\" mila=$g")
    }
    private fun ntList(say: String) {
        val g = noteCmd(say)
        if (g?.first != "notes") fails.add("notes \"$say\" mila=$g")
    }

    @Test fun featureTest() {
        // ── REMINDER waqt ──
        rem("10 minute baad chai yaad dilana", 10, "", "chai")
        rem("5 min me dawai yaad dila do", 5, "", "dawai")
        rem("2 ghante baad meeting yaad dilana", 120, "", "meeting")
        rem("kal subah 7 baje uthana", null, "", "uthana")
        rem("shaam 6 baje call karna yaad dilana", null, "", "call")
        rem("roz subah 7 baje uthana", null, "daily", "uthana")
        rem("har somwar 9 baje report yaad dilana", null, "weekly", "report")
        rem("raat 10 baje dawai yaad dilana", null, "", "dawai")
        remFail("youtube kholo")
        remFail("kaise ho")

        // ── MODE ──
        md("study mode", STUDY)
        md("study mode chalu karo", STUDY)
        md("padhai mode", STUDY)
        md("coding mode", CODING)
        md("developer mode", CODING)
        md("normal mode", NORMAL)
        md("study mode band", NORMAL)
        md("coding mode band karo", NORMAL)
        md("mode off", NORMAL)
        md("youtube kholo", null)
        md("torch on karo", null)

        // ── TRANSLATE ──
        tr("good morning ko hindi me translate karo", "Hindi", "good morning")
        tr("namaste ko english me translate karo", "English", "namaste")
        tr("english me kya kehte hain paani", "English", "paani")
        tr("youtube kholo", null, "")
        tr("kaise ho", null, "")

        // ═══ v4.0 — naye local command ═══
        mus("agla gaana", "next");      mus("gaana next karo", "next")
        mus("pichla gaana", "prev");    mus("gaana rok do", "pause")
        mus("gaana chalao", "play");    mus("gaana band karo", "stop")
        musNone("youtube pe gaana chalao")
        nt("note doodh lena hai", "doodh lena hai")
        nt("likh lo kal meeting hai", "kal meeting hai")
        ntList("notes dikhao");  ntList("note batao")

        val total = 39
        if (fails.isNotEmpty()) {
            println("\n\u274c FAIL:")
            fails.forEach { println("   " + it) }
        }
        println("\n\u2705 FEATURE PASS " + (total - fails.size) + " / " + total)
        assertEquals("sab feature sahi", 0, fails.size)
    }
}
