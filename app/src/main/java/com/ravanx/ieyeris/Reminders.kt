package com.ravanx.ieyeris

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * ⏰ REMINDERS — spec section 26
 *
 * Kya kar sakta hai:
 *   • ek baar wala      "kal 5 baje meeting"
 *   • roz wala          "roz subah 7 baje uthao"
 *   • hafte wala        "har somwar 9 baje"
 *   • edit / delete
 *   • asli notification + awaaz
 *   • bol kar banana
 *
 * ⚠️ ASLI ALARM hai — AlarmManager use karta hai. App band ho,
 *    phone so raha ho, tab bhi bajega. Sirf list me likh dena
 *    (jo bahut app karte hain) bekaar hota hai.
 *
 * ⚠️ Android 12+ me "exact alarm" ki alag permission chahiye.
 *    Na mile to hum inexact pe gir jate hain — thoda late baj
 *    sakta hai par bajta zaroor hai. Chup-chaap fail nahi hote.
 */
object Reminders {

    const val CH = "ieyeris_remind"
    private const val REQ_BASE = 40000

    data class Rem(
        val id: Int,
        val text: String,
        val at: Long,
        /** "" = ek baar · "daily" = roz · "weekly" = har hafte */
        val repeat: String = "",
        val on: Boolean = true
    )

    private fun file(c: Context) = File(c.filesDir, "reminders.json")

    // ═══════════════════════════════════════════
    //   list padho / likho
    // ═══════════════════════════════════════════

    fun all(c: Context): MutableList<Rem> {
        val f = file(c)
        if (!f.exists()) return mutableListOf()
        return try {
            val a = JSONArray(f.readText())
            val out = mutableListOf<Rem>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Rem(o.getInt("id"), o.getString("t"),
                    o.getLong("at"), o.optString("r", ""),
                    o.optBoolean("on", true)))
            }
            out.sortedBy { it.at }.toMutableList()
        } catch (e: Exception) { mutableListOf() }
    }

    private fun write(c: Context, list: List<Rem>) {
        try {
            val a = JSONArray()
            list.forEach {
                a.put(JSONObject().apply {
                    put("id", it.id); put("t", it.text)
                    put("at", it.at); put("r", it.repeat)
                    put("on", it.on)
                })
            }
            file(c).writeText(a.toString())
        } catch (e: Exception) {}
    }

    // ═══════════════════════════════════════════
    //   banao / hatao
    // ═══════════════════════════════════════════

    fun add(c: Context, text: String, at: Long,
            repeat: String = ""): Rem {
        val list = all(c)
        val id = (list.maxOfOrNull { it.id } ?: 0) + 1
        val r = Rem(id, text.trim(), at, repeat)
        list.add(r)
        write(c, list)
        schedule(c, r)
        Brain.log("⏰ reminder #$id — ${fmt(at)} — ${text.take(30)}")
        return r
    }

    fun delete(c: Context, id: Int): Boolean {
        val list = all(c)
        val r = list.find { it.id == id } ?: return false
        cancel(c, r)
        list.remove(r)
        write(c, list)
        return true
    }

    fun deleteAll(c: Context): Int {
        val list = all(c)
        list.forEach { cancel(c, it) }
        write(c, emptyList())
        return list.size
    }

    fun edit(c: Context, id: Int, text: String? = null,
             at: Long? = null): Boolean {
        val list = all(c)
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return false
        cancel(c, list[i])
        val n = list[i].copy(
            text = text ?: list[i].text,
            at = at ?: list[i].at)
        list[i] = n
        write(c, list)
        schedule(c, n)
        return true
    }

    /** Guzar chuke ek-baar wale hata do */
    fun cleanup(c: Context) {
        val now = System.currentTimeMillis()
        val list = all(c)
        val keep = list.filter { it.repeat.isNotBlank() || it.at > now }
        if (keep.size != list.size) write(c, keep)
    }

    // ═══════════════════════════════════════════
    //   ASLI ALARM
    // ═══════════════════════════════════════════

    private fun pi(c: Context, r: Rem): PendingIntent {
        val i = Intent(c, Fire::class.java).apply {
            action = "ieyeris.REMIND"
            putExtra("id", r.id)
            putExtra("text", r.text)
            putExtra("repeat", r.repeat)
        }
        return PendingIntent.getBroadcast(c, REQ_BASE + r.id, i,
            PendingIntent.FLAG_IMMUTABLE or
            PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun schedule(c: Context, r: Rem) {
        if (!r.on) return
        try {
            val am = c.getSystemService(Context.ALARM_SERVICE)
                as AlarmManager
            val p = pi(c, r)

            // ⚠️ Android 12+ — exact alarm ki ijazat chahiye.
            //    Na ho to inexact — thoda late par bajega zaroor.
            val exact = if (Build.VERSION.SDK_INT >= 31)
                am.canScheduleExactAlarms() else true

            if (exact) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, r.at, p)
            } else {
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, r.at, p)
                Brain.log("⏰ exact alarm ki ijazat nahi — " +
                          "thoda late baj sakta hai")
            }
        } catch (e: Exception) {
            Brain.log("⏰ alarm fail: ${e.message}")
        }
    }

    private fun cancel(c: Context, r: Rem) {
        try {
            (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .cancel(pi(c, r))
        } catch (e: Exception) {}
    }

    /** Phone restart ke baad sab dobara lagao */
    fun rescheduleAll(c: Context) {
        cleanup(c)
        all(c).filter { it.on }.forEach { schedule(c, it) }
    }

    fun canExact(c: Context): Boolean = try {
        if (Build.VERSION.SDK_INT >= 31)
            (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .canScheduleExactAlarms()
        else true
    } catch (e: Exception) { true }

    // ═══════════════════════════════════════════
    //   🔔 ALARM BAJA — Receiver
    // ═══════════════════════════════════════════

    class Fire : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val id = i.getIntExtra("id", 0)
            val text = i.getStringExtra("text") ?: "Reminder"
            val rep = i.getStringExtra("repeat") ?: ""

            notify(c, id, text)

            // bol kar bhi batao (chup mode me nahi)
            try {
                if (!Voice.isMuted(c)) {
                    Voice(c).say("Sir, yaad dila raha hoon — $text")
                }
            } catch (e: Exception) {}

            // dohrane wala? agla waqt lagao
            if (rep.isNotBlank()) {
                val cal = Calendar.getInstance()
                when (rep) {
                    "daily" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    "weekly" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                    else -> return
                }
                // aaj ka jo waqt tha, wahi kal ke liye
                val old = all(c).find { it.id == id } ?: return
                val n = Calendar.getInstance().apply {
                    timeInMillis = old.at
                    when (rep) {
                        "daily" -> add(Calendar.DAY_OF_YEAR, 1)
                        "weekly" -> add(Calendar.WEEK_OF_YEAR, 1)
                    }
                }
                val list = all(c)
                val idx = list.indexOfFirst { it.id == id }
                if (idx >= 0) {
                    list[idx] = list[idx].copy(at = n.timeInMillis)
                    write(c, list)
                    schedule(c, list[idx])
                }
            } else {
                delete(c, id)
            }
        }

        private fun notify(c: Context, id: Int, text: String) {
            try {
                val nm = c.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
                if (Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(NotificationChannel(
                        CH, "IEYE RIS Reminders",
                        NotificationManager.IMPORTANCE_HIGH))
                }
                val open = PendingIntent.getActivity(c, 0,
                    Intent(c, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or
                    PendingIntent.FLAG_UPDATE_CURRENT)
                val b = if (Build.VERSION.SDK_INT >= 26)
                    Notification.Builder(c, CH)
                else @Suppress("DEPRECATION") Notification.Builder(c)
                nm.notify(90000 + id, b
                    .setContentTitle("⏰ IEYE RIS — Yaad dila raha hoon")
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build())
            } catch (e: Exception) {}
        }
    }

    // ═══════════════════════════════════════════
    //   🗣 BOL KAR BANAO — waqt samajhna
    // ═══════════════════════════════════════════

    /**
     * Aam boli se waqt nikaalo.
     *
     * Chalte hain:
     *   "5 minute baad"      "10 min me"
     *   "2 ghante baad"
     *   "subah 7 baje"       "shaam 6 baje"
     *   "raat 10 baje"       "dopahar 2 baje"
     *   "kal subah 8 baje"
     *   "roz subah 7 baje"   (repeat = daily)
     *   "har somwar 9 baje"  (repeat = weekly)
     *   "7:30 pm"            "19:30"
     *
     * @return Triple(waqt, repeat, bacha hua kaam ka text)
     *         waqt = 0 matlab samajh nahi aaya
     */
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

    // ═══════════════════════════════════════════
    //   dikhane ke liye
    // ═══════════════════════════════════════════

    fun fmt(at: Long): String {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = at }
        val time = SimpleDateFormat("h:mm a", Locale.US).format(c.time)
        val sameDay = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return "aaj $time"
        now.add(Calendar.DAY_OF_YEAR, 1)
        val tom = now.get(Calendar.YEAR) == c.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == c.get(Calendar.DAY_OF_YEAR)
        if (tom) return "kal $time"
        return SimpleDateFormat("d MMM, h:mm a", Locale.US).format(c.time)
    }

    fun listText(c: Context): String {
        cleanup(c)
        val l = all(c)
        if (l.isEmpty()) return "Koi reminder nahi hai sir."
        val sb = StringBuilder("⏰ REMINDERS (${l.size})\n\n")
        l.forEachIndexed { i, r ->
            sb.append("${i + 1}. ${r.text}\n")
            sb.append("   ${fmt(r.at)}")
            when (r.repeat) {
                "daily" -> sb.append("  · roz")
                "weekly" -> sb.append("  · har hafte")
            }
            sb.append("\n")
        }
        if (!canExact(c)) {
            sb.append("\n⚠️ Exact alarm ki ijazat nahi hai — ")
            sb.append("reminder thoda late baj sakta hai. ")
            sb.append("Settings me \"Alarms & reminders\" on kar dijiye.")
        }
        return sb.toString().trim()
    }
}
