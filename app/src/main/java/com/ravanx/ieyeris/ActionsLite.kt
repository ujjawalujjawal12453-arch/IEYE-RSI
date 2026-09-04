package com.ravanx.ieyeris

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ⚡ ACTIONS LITE — Bubble ke liye
 *
 * Bubble ek Service hai, uske paas Activity nahi hoti. Par kuch
 * kaam Activity ke bina bhi ho jaate hain — torch, volume, lock,
 * battery, app kholna.
 *
 * Jo yahan nahi ho sakta, uske liye `null` laut'ta hai — phir
 * Bubble app khol kar wahan karwa deta hai.
 */
object ActionsLite {

    /** Return: jawab (ho gaya) ya null (Activity chahiye) */
    fun run(c: Context, cmd: Brain.Cmd): String? = try {
        when (cmd.action) {

            // ── torch ──
            "torch_on"  -> torch(c, true)
            "torch_off" -> torch(c, false)

            // ── volume ──
            "vol_up"   -> vol(c, 1)
            "vol_down" -> vol(c, -1)
            "vol_max"  -> volMax(c)
            "mute"     -> mute(c)

            // ── info ──
            "battery" -> battery(c)
            "time" -> "Abhi " + SimpleDateFormat("h:mm a", Locale.ENGLISH)
                .format(Date()) + " hue hain"
            "date" -> "Aaj " + SimpleDateFormat("d MMMM, EEEE",
                Locale.ENGLISH).format(Date()) + " hai"

            // ── screen (Eyes se) ──
            "lock"       -> eye { it.lockScreen(); "Lock kar diya" }
            "back"       -> eye { it.back(); "Wapas" }
            "home"       -> eye { it.home(); "Home" }
            "recents"    -> eye { it.recents(); "Recent apps" }
            "notif_panel"-> eye { it.notifPanel(); "Khol diya" }
            "screenshot" -> eye {
                if (it.screenshot()) "Screenshot le liya"
                else "Nahi ho paya" }
            "scroll"     -> eye {
                it.scroll(cmd.arg != "up"); "Scroll kiya" }
            "tap"        -> eye {
                if (it.tapText(cmd.arg)) "'${cmd.arg}' daba diya"
                else "'${cmd.arg}' nahi mila" }
            "read_notif" -> eye {
                val n = it.lastNotifs(4)
                if (n.isEmpty()) "Koi notification nahi"
                else n.joinToString(". ") }

            // ── memory ──
            "remember" -> remember(c, cmd.arg)
            "recall"   -> Memory(c).recall(cmd.arg)
                ?.let { "Aapka ${cmd.arg} $it hai sir" }
                ?: "Ye mujhe nahi pata sir"

            // ── app kholo ──
            "open_app" -> openApp(c, cmd.arg)

            // ── search (browser/app me) ──
            "yt_search" -> ytSearch(c, cmd.arg)
            "google"    -> web(c,
                "https://www.google.com/search?q=" + Uri.encode(cmd.arg),
                "'${cmd.arg}' dhoondh raha hoon")

            // ── call / message ──
            "call" -> dial(c, cmd.arg)

            // ── baat-cheet ──
            "chat" -> cmd.say.ifBlank { "Ji sir?" }
            "no_unlock" -> cmd.say

            // ── baaki ke liye Activity chahiye ──
            else -> null
        }
    } catch (e: Exception) {
        "Nahi ho paya sir"
    }

    // ═══════════════ helpers ═══════════════

    private fun eye(f: (Eyes) -> String): String {
        val e = Eyes.live ?: return "Iske liye Accessibility on " +
            "karni padegi sir — app me 👁 button dabaiye"
        return f(e)
    }

    private fun torch(c: Context, on: Boolean): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return "Purana Android hai sir"
        val cm = c.getSystemService(Context.CAMERA_SERVICE)
            as CameraManager
        val id = cm.cameraIdList.firstOrNull { cid ->
            cm.getCameraCharacteristics(cid).get(
                android.hardware.camera2.CameraCharacteristics
                    .FLASH_INFO_AVAILABLE) == true
        } ?: return "Flash nahi hai"
        cm.setTorchMode(id, on)
        return if (on) "Torch chalu" else "Torch band"
    }

    private fun am(c: Context) =
        c.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun vol(c: Context, d: Int): String {
        am(c).adjustStreamVolume(AudioManager.STREAM_MUSIC,
            if (d > 0) AudioManager.ADJUST_RAISE
            else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI)
        return if (d > 0) "Volume badha diya" else "Volume kam kiya"
    }

    private fun volMax(c: Context): String {
        val m = am(c)
        m.setStreamVolume(AudioManager.STREAM_MUSIC,
            m.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            AudioManager.FLAG_SHOW_UI)
        return "Full volume"
    }

    private fun mute(c: Context): String {
        am(c).setStreamVolume(AudioManager.STREAM_MUSIC, 0,
            AudioManager.FLAG_SHOW_UI)
        return "Silent kar diya"
    }

    private fun battery(c: Context): String {
        val bm = c.getSystemService(Context.BATTERY_SERVICE)
            as BatteryManager
        val p = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return "Battery $p% hai sir" +
            (if (bm.isCharging) ", charge ho rahi hai" else "")
    }

    private fun remember(c: Context, raw: String): String {
        val m = Memory(c)
        val p = raw.split("=", limit = 2)
        return if (p.size == 2) {
            m.remember(p[0].trim(), p[1].trim())
            "Yaad rakh liya sir"
        } else {
            m.remember("note", raw)
            "Yaad rakh liya"
        }
    }

    private fun go(c: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        c.startActivity(i)
    }

    private fun web(c: Context, url: String, msg: String): String {
        go(c, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return msg
    }

    private fun openApp(c: Context, name: String): String {
        val pkg = if (name.contains(".")) name
                  else Brain.APPS[name.lowercase()] ?: name
        val i = c.packageManager.getLaunchIntentForPackage(pkg)
        return if (i != null) {
            go(c, i); "Khol diya sir"
        } else "Ye app phone me nahi hai sir"
    }

    private fun ytSearch(c: Context, q: String): String {
        if (q.isBlank()) return openApp(c, "youtube")
        try {
            go(c, Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", q)
            })
            return "YouTube pe '$q' chala raha hoon"
        } catch (e: Exception) { }
        return web(c, "https://www.youtube.com/results?search_query=" +
            Uri.encode(q), "YouTube pe dhoondh raha hoon")
    }

    private fun dial(c: Context, who: String): String {
        val d = who.filter { it.isDigit() || it == '+' }
        if (d.length >= 10) {
            go(c, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$d")))
            return "Number laga diya"
        }
        // naam se contact dhoondhna — uske liye Activity chahiye
        throw IllegalStateException("need activity")
    }
}
