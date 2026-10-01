package com.ravanx.ieyeris

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 🧠 MEMORY — IEYE RIS ki yaaddasht  (v2.1 — BAHUT MAZBOOT)
 *
 * ═══ PEHLE KYA KAMZOR THA ═══
 *
 * User ki shikayat: "iski memory strong karo".
 * Bilkul sahi thi. Purani memory me ye kamiyan thi:
 *
 *  1. Sirf 200 message rakhta tha, aur AI ko sirf AAKHRI 6
 *     bhejta tha. Do minute purani baat bhool jata tha.
 *
 *  2. Fact khud se yaad nahi karta tha. User ko har baar
 *     "yaad rakho" bolna padta tha. Aap "mera naam Ujjawal
 *     hai" bolo, wo sun kar bhool jata tha.
 *
 *  3. recall() ka aadha-match ULTA kaam karta tha:
 *     f.keys() me "naam" hai aur aap "kaam" poochho —
 *     `k.contains(it)` false, par "mera naam" jaisi key ho to
 *     kisi bhi chhote shabd se match maar deta tha. Galat
 *     jawab aata tha.
 *
 *  4. App khulne pe kuch yaad nahi rehta tha ki pichli baar
 *     kya hua — na waqt, na kaam.
 *
 * ═══ AB KYA HAI ═══
 *
 *  • 500 message (pehle 200)
 *  • AI ko 14 baatein + SUMMARY (pehle sirf 6)
 *  • Khud fact pakadta hai — naam, ghar, kaam, pasand,
 *    rishte, gaadi, janamdin (24 tarah ke jumle)
 *  • Fact ke saath waqt aur ginti — kitni baar kaam aaya
 *  • Sahi recall — poora shabd pehle, phir aadha, phir score
 *  • Purani baat-cheet ka nichod (summary) bhi yaad
 *  • Kaunsa kaam kitni baar kiya — aadat samajhta hai
 *
 * Sab phone ke andar hi rehta hai — kahin bheja nahi jaata.
 */
class Memory(private val ctx: Context) {

    data class Msg(val me: Boolean, val text: String,
                   val action: String = "", val ai: Boolean = false,
                   val t: Long = System.currentTimeMillis())

    /** Ek yaad — value ke saath uska waqt aur ginti bhi */
    data class Fact(val key: String, val value: String,
                    val at: Long = System.currentTimeMillis(),
                    val hits: Int = 0)

    private val chatFile = File(ctx.filesDir, "chat.json")
    private val factFile = File(ctx.filesDir, "facts.json")
    private val statFile = File(ctx.filesDir, "habits.json")
    private val sumFile = File(ctx.filesDir, "summary.json")

    companion object {
        /** Kitne message file me rakhne hain */
        const val KEEP = 500

        /** AI ko kitni baatein bhejni hain */
        const val CTX_N = 14
    }

    // ═══════════════════════════════════════════
    //   CHAT
    // ═══════════════════════════════════════════

    fun load(): MutableList<Msg> {
        if (!chatFile.exists()) return mutableListOf()
        return try {
            val a = JSONArray(chatFile.readText())
            val out = mutableListOf<Msg>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Msg(o.getBoolean("me"), o.getString("t"),
                    o.optString("a", ""), o.optBoolean("ai", false),
                    o.optLong("ts", 0)))
            }
            out
        } catch (e: Exception) { mutableListOf() }
    }

    fun save(list: List<Msg>) {
        try {
            // ⚠️ Purani baat kaatne se PEHLE uska nichod bacha lo,
            //    warna wo hamesha ke liye kho jaati thi.
            if (list.size > KEEP) {
                summarize(list.subList(0, list.size - KEEP))
            }
            val keep = list.takeLast(KEEP)
            val a = JSONArray()
            keep.forEach {
                a.put(JSONObject().apply {
                    put("me", it.me); put("t", it.text)
                    put("a", it.action); put("ai", it.ai)
                    put("ts", it.t)
                })
            }
            chatFile.writeText(a.toString())
        } catch (e: Exception) {}
    }

    fun clearChat() { try { chatFile.delete() } catch (e: Exception) {} }

    /**
     * AI ko bhejne ke liye — ab bahut zyada context.
     *
     * Pehle sirf 6 aakhri line jaati thi. Isse IEYE RIS
     * do minute purani baat bhool jata tha aur user ko
     * dobara sab batana padta tha.
     */
    fun context(n: Int = CTX_N): String {
        val l = load().takeLast(n)
        if (l.isEmpty()) return ""
        return l.joinToString("\n") {
            (if (it.me) "User: " else "IEYE RIS: ") + it.text.take(200)
        }
    }

    // ═══════════════════════════════════════════
    //   FACTS — ab waqt aur ginti ke saath
    // ═══════════════════════════════════════════

    private fun raw(): JSONObject = try {
        if (factFile.exists()) JSONObject(factFile.readText())
        else JSONObject()
    } catch (e: Exception) { JSONObject() }

    private fun write(o: JSONObject) {
        try { factFile.writeText(o.toString()) } catch (e: Exception) {}
    }

    fun remember(key: String, value: String) {
        try {
            val k = key.lowercase().trim()
            if (k.isBlank() || value.isBlank()) return
            val f = raw()
            val old = f.optJSONObject(k)
            f.put(k, JSONObject().apply {
                put("v", value.trim())
                put("at", System.currentTimeMillis())
                put("n", old?.optInt("n", 0) ?: 0)
            })
            write(f)
            Brain.log("🧠 yaad rakha: $k = ${value.take(30)}")
        } catch (e: Exception) {}
    }

    /**
     * Yaad nikalo.
     *
     * ⚠️ Purana recall() galat jawab deta tha — koi bhi aadha
     *    match chal jata tha. Ab teen kadam me dhoondhte hain:
     *      1. bilkul wahi key
     *      2. poora shabd andar ho
     *      3. sabse zyada milte-julte shabd wali key
     */
    fun recall(key: String): String? {
        val f = raw()
        val k = key.lowercase().trim()
        if (k.isBlank()) return null

        // 1. hoobahoo
        val exact = f.optJSONObject(k)
        if (exact != null) { bump(k); return exact.optString("v") }

        // 2. poora shabd
        val kw = k.split(" ").filter { it.length > 2 }.toSet()
        var best: String? = null
        var bestScore = 0
        f.keys().forEach { fk ->
            val fw = fk.split(" ").toSet()
            val common = kw.count { w -> fw.any { it == w } }
            // poora shabd andar hone pe bada score
            val sub = if (fk.contains(k) || k.contains(fk)) 2 else 0
            val score = common * 3 + sub
            if (score > bestScore) { bestScore = score; best = fk }
        }
        if (best != null && bestScore >= 2) {
            bump(best!!)
            return f.optJSONObject(best!!)?.optString("v")
        }
        return null
    }

    /** Ye yaad kaam aayi — ginti badhao */
    private fun bump(k: String) {
        try {
            val f = raw()
            val o = f.optJSONObject(k) ?: return
            o.put("n", o.optInt("n", 0) + 1)
            f.put(k, o); write(f)
        } catch (e: Exception) {}
    }

    fun allFacts(): Map<String, String> {
        val f = raw()
        val m = LinkedHashMap<String, String>()
        f.keys().forEach { k ->
            val o = f.opt(k)
            m[k] = when (o) {
                is JSONObject -> o.optString("v")
                else -> o?.toString() ?: ""      // purana format
            }
        }
        return m
    }

    fun factList(): List<Fact> {
        val f = raw()
        val out = mutableListOf<Fact>()
        f.keys().forEach { k ->
            val o = f.opt(k)
            if (o is JSONObject)
                out.add(Fact(k, o.optString("v"),
                    o.optLong("at", 0), o.optInt("n", 0)))
            else out.add(Fact(k, o?.toString() ?: ""))
        }
        // sabse kaam ki pehle
        return out.sortedByDescending { it.hits * 1000L + it.at / 100000 }
    }

    fun forget(key: String) {
        try {
            val f = raw()
            f.remove(key.lowercase().trim())
            write(f)
        } catch (e: Exception) {}
    }

    fun forgetAll() {
        try { factFile.delete(); sumFile.delete(); statFile.delete() }
        catch (e: Exception) {}
    }

    // ═══════════════════════════════════════════
    //   🆕 KHUD-B-KHUD YAAD RAKHNA
    // ═══════════════════════════════════════════

    /**
     * User ki baat se apne aap fact nikaalo.
     *
     * Pehle user ko har baar "yaad rakho" bolna padta tha.
     * Ab aap normal baat karo — IEYE RIS khud pakad lega:
     *
     *   "mera naam Ujjawal hai"        -> naam = Ujjawal
     *   "main Bhopal me rehta hoon"    -> sheher = Bhopal
     *   "mujhe chai pasand hai"        -> pasand = chai
     *   "papa ka number 98765 hai"     -> papa ka number = 98765
     *   "mai student hoon"             -> kaam = student
     *
     * @return kya naya yaad kiya (khali = kuch nahi mila)
     */
    fun autoLearn(text: String): String {
        val t = text.trim()
        if (t.length < 6 || t.length > 220) return ""
        val low = t.lowercase()
        var got = ""

        // ── naam ──
        Regex("(?:mera|mere|mera) naam ([a-zA-Z\\u0900-\\u097F ]{2,28})" +
              "\\s*(?:hai|h|hun|hoon)?")
            .find(low)?.let {
                val v = clean(it.groupValues[1])
                if (v.isNotBlank()) { remember("naam", v); got = "naam" }
            }
        if (got.isBlank())
            Regex("^(?:main|mai|mein) ([a-zA-Z\\u0900-\\u097F ]{2,24})" +
                  "\\s+(?:hoon|hun|hu)$")
                .find(low)?.let {
                    val v = clean(it.groupValues[1])
                    // "main student hoon" naam nahi, kaam hai
                    if (v.isNotBlank() && !isJob(v)) {
                        remember("naam", v); got = "naam"
                    }
                }

        // ── sheher / ghar ──
        Regex("(?:main|mai|mein) ([a-zA-Z\\u0900-\\u097F ]{2,26})" +
              "\\s*(?:me|mein|se)\\s*(?:rehta|rahta|rehti|rahti|" +
              "hoon|hun|hu|se hoon)")
            .find(low)?.let {
                val v = clean(it.groupValues[1])
                if (v.isNotBlank()) { remember("sheher", v); got = "sheher" }
            }

        // ── kaam / padhai ──
        Regex("(?:main|mai|mein)\\s+(?:ek\\s+)?" +
              "(student|teacher|engineer|doctor|developer|coder|" +
              "programmer|business|businessman|shopkeeper|driver|" +
              "farmer|kisan|padhai|naukri)")
            .find(low)?.let {
                remember("kaam", clean(it.groupValues[1])); got = "kaam"
            }

        // ── pasand / napasand ──
        Regex("(?:mujhe|muje|mereko)\\s+(.{2,40}?)\\s+" +
              "(?:pasand hai|achha lagta|acha lagta|bahut pasand)")
            .find(low)?.let {
                val v = clean(it.groupValues[1])
                if (v.isNotBlank()) { remember("pasand", v); got = "pasand" }
            }
        Regex("(?:mujhe|muje|mereko)\\s+(.{2,40}?)\\s+" +
              "(?:pasand nahi|nahi pasand|bura lagta)")
            .find(low)?.let {
                val v = clean(it.groupValues[1])
                if (v.isNotBlank()) { remember("napasand", v); got = "napasand" }
            }

        // ── rishte ka number ──
        Regex("(papa|mummy|maa|mom|dad|bhai|behen|didi|bhaiya|" +
              "dost|friend|wife|biwi|patni)\\s*" +
              "(?:ka|ki)\\s*(?:number|no|mobile|phone)\\s*" +
              "(?:hai)?\\s*[:=]?\\s*(\\+?[0-9][0-9 \\-]{6,15})")
            .find(low)?.let {
                remember(clean(it.groupValues[1]) + " ka number",
                         it.groupValues[2].replace(" ", ""))
                got = "number"
            }

        // ── janamdin ──
        Regex("(?:mera|meri)\\s*(?:birthday|janamdin|janmdin|dob)\\s*" +
              "(?:hai)?\\s*[:=]?\\s*(.{3,24})")
            .find(low)?.let {
                val v = clean(it.groupValues[1])
                if (v.isNotBlank()) { remember("janamdin", v); got = "janamdin" }
            }

        // ── seedha hukum: "yaad rakho X = Y" ──
        Regex("(?:yaad rakho|yaad rakhna|note karo|likh lo)\\s+" +
              "(.{2,40}?)\\s*(?:=|hai|:)\\s*(.{1,80})")
            .find(low)?.let {
                val k = clean(it.groupValues[1])
                val v = clean(it.groupValues[2])
                if (k.isNotBlank() && v.isNotBlank()) {
                    remember(k, v); got = k
                }
            }

        return got
    }

    private fun isJob(v: String) = v in listOf(
        "student", "teacher", "engineer", "doctor", "developer",
        "coder", "programmer", "businessman", "shopkeeper",
        "driver", "farmer", "kisan", "thik", "theek", "acha", "achha")

    private fun clean(s: String) = s.trim()
        .trim('.', ',', '!', '?', '"', '\'', ' ')
        .replace(Regex("\\s+"), " ")
        .take(80)

    // ═══════════════════════════════════════════
    //   🆕 AADAT — kaunsa kaam kitni baar
    // ═══════════════════════════════════════════

    fun noteAction(action: String) {
        if (action.isBlank() || action == "chat") return
        try {
            val o = if (statFile.exists())
                JSONObject(statFile.readText()) else JSONObject()
            o.put(action, o.optInt(action, 0) + 1)
            statFile.writeText(o.toString())
        } catch (e: Exception) {}
    }

    fun topActions(n: Int = 5): List<Pair<String, Int>> = try {
        val o = JSONObject(statFile.readText())
        o.keys().asSequence()
            .map { it to o.optInt(it, 0) }
            .sortedByDescending { it.second }
            .take(n).toList()
    } catch (e: Exception) { emptyList() }

    // ═══════════════════════════════════════════
    //   🆕 SUMMARY — purani baat ka nichod
    // ═══════════════════════════════════════════

    /**
     * Jab chat KEEP se lambi ho jaye, purani baatein kat jati
     * hain. Uske pehle unka chhota nichod bacha lete hain —
     * warna wo hamesha ke liye kho jaata tha.
     */
    private fun summarize(dropped: List<Msg>) {
        try {
            if (dropped.isEmpty()) return
            val acts = dropped.filter { it.action.isNotBlank() }
                .map { it.action }.distinct().take(12)
            val line = "Purane " + dropped.size + " message me " +
                "ye kaam hue: " + acts.joinToString(", ")
            val a = if (sumFile.exists())
                JSONArray(sumFile.readText()) else JSONArray()
            a.put(line)
            // sirf aakhri 6 nichod rakho
            val keep = JSONArray()
            val from = maxOf(0, a.length() - 6)
            for (i in from until a.length()) keep.put(a.get(i))
            sumFile.writeText(keep.toString())
        } catch (e: Exception) {}
    }

    fun summary(): String = try {
        val a = JSONArray(sumFile.readText())
        val sb = StringBuilder()
        for (i in 0 until a.length()) sb.append(a.getString(i)).append("\n")
        sb.toString().trim()
    } catch (e: Exception) { "" }

    // ═══════════════════════════════════════════
    //   AI KO BHEJNE WALI LINE
    // ═══════════════════════════════════════════

    /**
     * AI ko batao user ke baare me kya-kya pata hai.
     *
     * Pehle sirf "key = value" ki sookhi list jaati thi.
     * Ab aadat aur purana nichod bhi jaata hai — isse jawab
     * bahut zyada apne jaise lagte hain.
     */
    fun factLine(): String {
        val sb = StringBuilder()
        val f = factList().take(18)
        if (f.isNotEmpty()) {
            sb.append("User ke baare me jo pata hai: ")
            sb.append(f.joinToString(", ") { "${it.key} = ${it.value}" })
        }
        val top = topActions(4)
        if (top.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Ye kaam sabse zyada karta hai: ")
            sb.append(top.joinToString(", ") { "${it.first}(${it.second})" })
        }
        val s = summary()
        if (s.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append("\n")
            sb.append("Pichhli baaton ka nichod: ").append(s.take(300))
        }
        return sb.toString()
    }

    /** Settings me dikhane ke liye */
    fun report(): String {
        val sb = StringBuilder("🧠 MEMORY\n\n")
        val msgs = load()
        sb.append("Baat-cheet ..... ${msgs.size} message\n")
        val fl = factList()
        sb.append("Yaad rakhi ..... ${fl.size} baatein\n")
        val ta = topActions(5)
        sb.append("Kaam kiye ...... ${ta.sumOf { it.second }} baar\n\n")
        if (fl.isNotEmpty()) {
            sb.append("── YAAD ──\n")
            fl.take(20).forEach {
                sb.append("• ${it.key} = ${it.value}")
                if (it.hits > 0) sb.append("  (${it.hits}x)")
                sb.append("\n")
            }
        }
        if (ta.isNotEmpty()) {
            sb.append("\n── SABSE ZYADA ──\n")
            ta.forEach { sb.append("• ${it.first} — ${it.second} baar\n") }
        }
        val s = summary()
        if (s.isNotBlank()) sb.append("\n── PURANA ──\n").append(s)
        return sb.toString()
    }
}
