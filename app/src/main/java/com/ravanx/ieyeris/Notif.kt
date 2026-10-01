package com.ravanx.ieyeris

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🔔 NOTIFICATION — padhna aur jawab dena
 *
 * Do kaam:
 *   1. Aane wali notification yaad rakhna
 *      ("kya notification aaye?" pe bata sake)
 *   2. AUTO-REPLY — WhatsApp/SMS ka jawab khud de dena
 *
 * ═══ AUTO-REPLY KAISE KAAM KARTA HAI — SAAF BAAT ═══
 *
 * IEYE RIS WhatsApp ke andar nahi ghusta. Wo notification ke
 * saath jo "Reply" ka box aata hai (RemoteInput), usi ka
 * istemal karta hai — bilkul waise jaise aap notification se
 * hi jawab likh dete hain.
 *
 * Isliye ye SIRF unhi app pe chalega jo notification me reply
 * ka box dete hain (WhatsApp, SMS, Telegram, Instagram). Jo
 * nahi dete, unpe kuch nahi ho sakta.
 *
 * ⚠️ DEFAULT OFF hai — aur rehna bhi chahiye. Aapki taraf se
 *    khud message chala jaana bahut badi baat hai. User khud
 *    Settings me chalu karega, tabhi chalega.
 *
 * ⚠️ Ye "Notification access" permission maangta hai. Ye
 *    Android ki sabse taakatwar permission me se ek hai —
 *    isse app SAARI notification padh sakta hai. Isliye
 *    hum saaf batate hain aur zabardasti nahi karte.
 */
class Notif : NotificationListenerService() {

    companion object {
        @Volatile var live: Notif? = null
        fun on() = live != null

        /** Permission mili hai? */
        fun allowed(c: Context): Boolean = try {
            val s = Settings.Secure.getString(c.contentResolver,
                "enabled_notification_listeners") ?: ""
            s.contains(c.packageName)
        } catch (e: Exception) { false }

        fun openSettings(c: Context) {
            try {
                c.startActivity(Intent(
                    "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {}
        }

        // ── auto-reply on/off ──
        fun autoOn(c: Context) = try {
            Keys(c).flag("notif_auto", false)
        } catch (e: Exception) { false }

        fun setAuto(c: Context, v: Boolean) {
            try { Keys(c).setFlag("notif_auto", v) } catch (e: Exception) {}
        }

        /** Auto-reply me kya bhejna hai */
        fun replyText(c: Context) = try {
            Keys(c).get("notif_msg", "").ifBlank { DEFAULT_MSG }
        } catch (e: Exception) { DEFAULT_MSG }

        fun setReplyText(c: Context, t: String) {
            try { Keys(c).set("notif_msg", t) } catch (e: Exception) {}
        }

        const val DEFAULT_MSG =
            "Main abhi busy hoon, thodi der me baat karta hoon."

        /** Sirf inhi app pe auto-reply — baaki chhod do */
        val REPLY_APPS = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms"
        )

        private fun file(c: Context) = File(c.filesDir, "notifs.json")

        /** Aakhri notification (padh kar sunane ke liye) */
        fun recent(c: Context, n: Int = 10): List<Triple<String, String, String>> =
            try {
                val a = JSONArray(file(c).readText())
                val out = mutableListOf<Triple<String, String, String>>()
                val from = maxOf(0, a.length() - n)
                for (i in from until a.length()) {
                    val o = a.getJSONObject(i)
                    out.add(Triple(o.optString("app"),
                        o.optString("title"), o.optString("text")))
                }
                out.reversed()
            } catch (e: Exception) { emptyList() }

        fun add(c: Context, app: String, title: String, text: String) {
            try {
                val f = file(c)
                val a = if (f.exists()) JSONArray(f.readText())
                        else JSONArray()
                a.put(JSONObject().apply {
                    put("app", app); put("title", title)
                    put("text", text)
                    put("at", System.currentTimeMillis())
                })
                // sirf aakhri 60
                val keep = JSONArray()
                val from = maxOf(0, a.length() - 60)
                for (i in from until a.length()) keep.put(a.get(i))
                f.writeText(keep.toString())
            } catch (e: Exception) {}
        }

        fun clear(c: Context) {
            try { file(c).delete() } catch (e: Exception) {}
        }

        /** "kya notification aaye" ka jawab */
        fun readOut(c: Context): String {
            if (!allowed(c))
                return "Sir, notification padhne ki ijazat nahi hai. " +
                       "Settings me \"Notification access\" me IEYE RIS " +
                       "ko on kar dijiye."
            val l = recent(c, 8)
            if (l.isEmpty()) return "Koi nayi notification nahi hai sir."
            val sb = StringBuilder("🔔 ${l.size} notification\n\n")
            l.forEachIndexed { i, (app, title, text) ->
                sb.append("${i + 1}. $app")
                if (title.isNotBlank()) sb.append(" — $title")
                sb.append("\n")
                if (text.isNotBlank())
                    sb.append("   ${text.take(90)}\n")
            }
            return sb.toString().trim()
        }

        /**
         * 🕐 Auto-reply kis-kis ko bheja
         *
         * ⚠️ Ek hi bande ko baar-baar auto-reply nahi bhejte.
         *    Warna wo har message pe wahi jawab paata rahega
         *    aur pareshan ho jayega. 30 minute me ek baar bas.
         */
        private val lastReply = HashMap<String, Long>()
        private const val GAP = 30 * 60 * 1000L

        fun canReply(who: String): Boolean {
            val now = System.currentTimeMillis()
            val last = lastReply[who] ?: 0L
            if (now - last < GAP) return false
            lastReply[who] = now
            return true
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        live = this
        Brain.log("🔔 notification service jud gayi")
    }

    override fun onListenerDisconnected() {
        live = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try { handle(sbn) } catch (e: Exception) {}
    }

    private fun handle(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return          // apni notification chhod

        val ex = sbn.notification?.extras ?: return
        val title = ex.getCharSequence("android.title")?.toString()
            ?.trim().orEmpty()
        val text = ex.getCharSequence("android.text")?.toString()
            ?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        // app ka naam
        val app = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) { pkg.substringAfterLast('.') }

        add(this, app, title, text)

        // ── AUTO-REPLY ──
        if (!autoOn(this)) return
        if (pkg !in REPLY_APPS) return
        if (sbn.isOngoing) return               // "chal raha hai" wali chhod
        if (title.isBlank()) return
        // group message pe auto-reply nahi — sabko dikh jayega
        if (title.contains(":") || text.startsWith("$title:")) return
        if (!canReply("$pkg|$title")) return

        val sent = tryReply(sbn, replyText(this))
        if (sent) {
            Brain.log("🔔 auto-reply -> $title ($app)")
            add(this, "IEYE RIS", "Auto-reply bheja",
                "$title ko: ${replyText(this)}")
        }
    }

    /**
     * Notification ke "Reply" box me likh kar bhej do.
     *
     * ⚠️ Har notification me reply box nahi hota. Jo pehla
     *    RemoteInput wala action mile, wahi use karte hain.
     *    Na mile to chup-chaap chhod dete hain (jhooth nahi
     *    bolte ki bhej diya).
     */
    private fun tryReply(sbn: StatusBarNotification,
                         msg: String): Boolean {
        val actions = sbn.notification?.actions ?: return false
        for (a in actions) {
            val ris = a.remoteInputs ?: continue
            if (ris.isEmpty()) continue
            try {
                val intent = Intent()
                val b = Bundle()
                for (ri in ris) b.putCharSequence(ri.resultKey, msg)
                RemoteInput.addResultsToIntent(ris, intent, b)
                a.actionIntent?.send(this, 0, intent)
                return true
            } catch (e: Exception) {
                Brain.log("🔔 reply fail: ${e.message}")
            }
        }
        return false
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {}
}
