package com.ravanx.ieyeris

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 👁️ EYES — IEYE RIS ki aankhein aur haath
 *
 * Ye Accessibility Service hai. Isse IEYE RIS:
 *   • Screen pe kya likha hai — PADH sakta hai
 *   • Button khud DABA sakta hai
 *   • Text box me khud LIKH sakta hai
 *   • Scroll / swipe / back / home kar sakta hai
 *   • Notification padh sakta hai
 *
 * Isi ki wajah se "WhatsApp pe Papa ko message bhejo" me message
 * SACH ME BHEJ jaata hai — sirf app khulti nahi.
 *
 * ⚠️ User ko ye khud on karna padta hai:
 *    Settings → Accessibility → IEYE RIS → On
 *    Android ise force nahi karne deta (aur sahi hi karta hai —
 *    ye bahut taakatwar permission hai).
 */
class Eyes : AccessibilityService() {

    companion object {
        var live: Eyes? = null
        val notifs = ArrayDeque<String>()      // aakhri 20 notification

        fun on() = live != null

        /** Settings kholo taaki user on kar sake */
        fun openSettings(c: Context) {
            c.startActivity(Intent(
                Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private val h = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        live = this
    }

    override fun onDestroy() {
        live = null
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        // Notification pakdo
        if (e?.eventType ==
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            val app = e.packageName.toString()
            val txt = e.text.joinToString(" ")
            if (txt.isNotBlank()) {
                val nm = appLabel(app)
                notifs.addLast("$nm: $txt")
                while (notifs.size > 20) notifs.removeFirst()
            }
        }
    }

    private fun appLabel(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(
            pm.getApplicationInfo(pkg, 0)).toString()
    } catch (ex: Exception) { pkg.substringAfterLast('.') }

    // ═══════════════════════════════════════════
    //   NAVIGATION — back / home / recents
    // ═══════════════════════════════════════════

    fun back() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun home() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun recents() = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun notifPanel() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun lockScreen(): Boolean =
        if (Build.VERSION.SDK_INT >= 28)
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        else false

    fun screenshot(): Boolean =
        if (Build.VERSION.SDK_INT >= 28)
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        else false

    // ═══════════════════════════════════════════
    //   SCREEN PADHO
    // ═══════════════════════════════════════════

    /** Screen pe jo bhi text hai, sab nikalo */
    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        val out = StringBuilder()
        walk(root) { n ->
            val t = n.text?.toString()?.trim()
            if (!t.isNullOrBlank() && t.length < 200) {
                out.append(t).append("\n")
            }
        }
        return out.toString().trim().take(3000)
    }

    // ═══════════════════════════════════════════
    //   📸 SNAPSHOT — screen ka naksha AI ke liye
    //
    //   readScreen() sirf text deta tha. AI ko pata nahi chalta
    //   tha ki KYA DABAYA JA SAKTA HAI. Isliye wo andhere me
    //   teer chalata tha.
    //
    //   Ab har dabane-layak cheez number ke saath jaati hai:
    //     [3] School Friends
    //     [7] ✏️ Message likhne ka box
    //   AI bolta hai "tap 3" — pakka nishana.
    // ═══════════════════════════════════════════

    class Item(val idx: Int, val label: String,
               val node: AccessibilityNodeInfo,
               val editable: Boolean)

    private var shot = mutableListOf<Item>()

    /** Screen ka naksha — AI ko dene ke liye */
    fun snapshot(): String {
        val root = rootInActiveWindow ?: return "(screen khali hai)"
        shot = mutableListOf()
        val sb = StringBuilder()
        val app = try {
            appLabel(root.packageName?.toString() ?: "")
        } catch (e: Exception) { "" }
        sb.append("APP: ").append(app).append("\n")

        val seen = HashSet<String>()
        var i = 0
        var plain = 0
        walk(root) { n ->
            if (i >= 45) return@walk
            val t = (n.text?.toString() ?: "").trim()
            val d = (n.contentDescription?.toString() ?: "").trim()
            val lbl = (if (t.isNotBlank()) t else d).take(60)
            val tappable = n.isClickable || clickableParent(n) != null

            if (n.isEditable) {
                i++
                shot.add(Item(i, lbl.ifBlank { "text box" }, n, true))
                sb.append("[").append(i).append("] ✏️ ")
                    .append(lbl.ifBlank { "likhne ka box" })
                if (t.isNotBlank()) sb.append(" (abhi: ").append(t)
                    .append(")")
                sb.append("\n")
            } else if (tappable && lbl.isNotBlank()) {
                val k = lbl.lowercase()
                if (seen.add(k)) {
                    i++
                    shot.add(Item(i, lbl, n, false))
                    sb.append("[").append(i).append("] ")
                        .append(lbl).append("\n")
                }
            } else if (lbl.length in 2..60 && plain < 12 &&
                    seen.add("t:" + lbl.lowercase())) {
                // ⚠️ Sirf padhne wala text — isko 12 line tak
                //    hi rakho. Groq ki free limit 8000 token/min
                //    hai; poori screen bhejenge to 4 kadam me
                //    khatam ho jaayegi.
                plain++
                sb.append("    ").append(lbl).append("\n")
            }
        }
        return sb.toString().take(1500)
    }

    /** snapshot ke number pe tap */
    fun tapIdx(i: Int): Boolean {
        val it = shot.firstOrNull { x -> x.idx == i } ?: return false
        return clickIt(it.node)
    }

    /** snapshot ke number wale box me likho */
    fun typeIdx(i: Int, text: String): Boolean {
        val it = shot.firstOrNull { x -> x.idx == i } ?: return false
        it.node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo
                .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return it.node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /** Enter / send key bhejo */
    fun pressEnter(): Boolean {
        val root = rootInActiveWindow ?: return false
        var box: AccessibilityNodeInfo? = null
        walk(root) { n -> if (box == null && n.isEditable) box = n }
        return box?.performAction(
            AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    private fun walk(n: AccessibilityNodeInfo?,
                     f: (AccessibilityNodeInfo) -> Unit) {
        if (n == null) return
        f(n)
        for (i in 0 until n.childCount) walk(n.getChild(i), f)
    }

    // ═══════════════════════════════════════════
    //   BUTTON DABAO
    // ═══════════════════════════════════════════

    /**
     * Screen pe jo text/description mile, usko dabao.
     * Pehle poora match, phir aadha match.
     */
    fun tapText(want: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val w = want.lowercase().trim()

        // 1. Exact match
        root.findAccessibilityNodeInfosByText(want)
            ?.firstOrNull { it.isClickable || clickableParent(it) != null }
            ?.let { return clickIt(it) }

        // 2. Aadha match — poori screen chhaano
        var found: AccessibilityNodeInfo? = null
        walk(root) { n ->
            if (found != null) return@walk
            val t = (n.text?.toString() ?: "").lowercase()
            val d = (n.contentDescription?.toString() ?: "").lowercase()
            val id = (n.viewIdResourceName ?: "").lowercase()
            if (t.contains(w) || d.contains(w) ||
                (w.length > 3 && id.contains(w))) {
                if (n.isClickable || clickableParent(n) != null) found = n
            }
        }
        return found?.let { clickIt(it) } ?: false
    }

    private fun clickableParent(n: AccessibilityNodeInfo?):
            AccessibilityNodeInfo? {
        var p = n?.parent
        var depth = 0
        while (p != null && depth < 6) {
            if (p.isClickable) return p
            p = p.parent
            depth++
        }
        return null
    }

    private fun clickIt(n: AccessibilityNodeInfo): Boolean {
        if (n.isClickable)
            return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return clickableParent(n)?.performAction(
            AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    /** Screen pe X,Y pe tap karo */
    fun tapAt(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val p = Path().apply { moveTo(x, y) }
        return dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 60))
            .build(), null, null)
    }

    // ═══════════════════════════════════════════
    //   TEXT LIKHO
    // ═══════════════════════════════════════════

    /** Jo text box focus me hai (ya pehla mile) usme likho */
    fun type(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var box: AccessibilityNodeInfo? = null

        // Pehle focused dhoondo
        walk(root) { n ->
            if (box == null && n.isEditable && n.isFocused) box = n
        }
        // Na mile to koi bhi editable
        if (box == null) walk(root) { n ->
            if (box == null && n.isEditable) box = n
        }
        val b = box ?: return false

        b.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo
                .ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return b.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    // ═══════════════════════════════════════════
    //   SCROLL / SWIPE
    // ═══════════════════════════════════════════

    fun scroll(down: Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        var s: AccessibilityNodeInfo? = null
        walk(root) { n -> if (s == null && n.isScrollable) s = n }
        return s?.performAction(
            if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        ) ?: swipeGesture(down)
    }

    private fun swipeGesture(down: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val dm = resources.displayMetrics
        val x = dm.widthPixels / 2f
        val y1 = if (down) dm.heightPixels * 0.72f
                 else dm.heightPixels * 0.28f
        val y2 = if (down) dm.heightPixels * 0.28f
                 else dm.heightPixels * 0.72f
        val p = Path().apply { moveTo(x, y1); lineTo(x, y2) }
        return dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 320))
            .build(), null, null)
    }

    // ═══════════════════════════════════════════
    //   AUTO — app kholo, likho, bhejo
    // ═══════════════════════════════════════════

    /**
     * WhatsApp pe message BHEJO (sirf kholo nahi).
     *
     * ⚠️ Timing bahut zaroori hai — app khulne me waqt lagta hai.
     *    Isliye step-by-step delay ke saath.
     */
    fun sendWhatsApp(onDone: (String) -> Unit) {
        h.postDelayed({
            // Send button dhoondo — har language me alag naam
            val ok = tapText("Send") || tapText("भेजें") ||
                     tapText("send")
            onDone(if (ok) "Message bhej diya sir"
                   else "Send button nahi mila — aap dabaiye")
        }, 2600)
    }

    /** Kisi bhi app me: text likho aur bhejo */
    fun typeAndSend(text: String, sendLabel: String = "Send",
                    delay: Long = 2200, onDone: (String) -> Unit) {
        h.postDelayed({
            if (!type(text)) {
                onDone("Text box nahi mila sir"); return@postDelayed
            }
            h.postDelayed({
                val ok = tapText(sendLabel) || tapText("भेजें")
                onDone(if (ok) "Bhej diya sir"
                       else "Likh diya — send dabaiye")
            }, 700)
        }, delay)
    }

    /** Aakhri notifications */
    fun lastNotifs(n: Int = 5): List<String> =
        notifs.toList().takeLast(n).reversed()
}
