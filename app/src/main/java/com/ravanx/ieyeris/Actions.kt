package com.ravanx.ieyeris

import android.Manifest
import android.app.Activity
import android.app.SearchManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 🎬 ACTIONS — asli kaam yahan hota hai
 *
 * Har function saaf jawab deta hai (Hinglish me) jo IEYE RIS bolega.
 */
object Actions {

    private var torchOn = false

    fun run(act: Activity, c: Brain.Cmd): String {
        // 🧠 v2.1 — kaunsa kaam kitni baar hua, yaad rakho.
        //    Isse IEYE RIS user ki aadat samajhta hai aur AI ko
        //    bhi batata hai ("ye sabse zyada karta hai").
        try { Memory(act).noteAction(c.action) } catch (e: Exception) {}

        // 🛑 v4.1 — STOP daba hai to naya kaam mat karo.
        //    "stop" khud ko chhod kar — warna stop hi na chale.
        if (Halt.stopped() && c.action !in
            setOf("stop", "shutdown", "sleep", "status")) {
            return "Ruka hua hoon sir. Naya hukum dijiye."
        }

        return try {
            when (c.action) {
                // ═══ IEYE RIS ke apne hukum (spec section 7) ═══
                "wake"       -> wakeUp(act)
                "sleep"      -> sleepMode(act)
                "stop"       -> stopAll(act)
                "status"     -> systemStatus(act)
                "shutdown"   -> shutdownSelf(act)
                // ═══ v2.0 — jo pehle kaam nahi karte the ═══
                "mic_off"     -> micOff(act)
                "mic_on"      -> micOn(act)
                "mute_voice"  -> muteVoice(act)
                "unmute_voice"-> unmuteVoice(act)

                // ═══ v2.2 — spec section 26/27/28/29 ═══
                "remind"      -> addRemind(act, c.arg)
                "auto_reply"  -> autoReply(act, c.arg)
                // ═══ v4.0 — naye kaam ═══
                "music"       -> music(act, c.arg)
                "battery_saver" -> panel(act,
                                    Settings.ACTION_BATTERY_SAVER_SETTINGS,
                                    "Battery saver ki setting khol di")
                "data"        -> panel(act,
                                    Settings.ACTION_DATA_ROAMING_SETTINGS,
                                    "Mobile data ki setting khol di")
                "airplane"    -> panel(act,
                                    Settings.ACTION_AIRPLANE_MODE_SETTINGS,
                                    "Airplane mode ki setting khol di")
                "location"    -> panel(act,
                                    Settings.ACTION_LOCATION_SOURCE_SETTINGS,
                                    "Location ki setting khol di")
                "uninstall"   -> uninstall(act, c.arg)
                "app_info"    -> appInfo(act, c.arg)
                "share"       -> shareText(act, c.arg)
                "note"        -> quickNote(act, c.arg)
                "notes"       -> readNotes(act)
                "remind_list" -> Reminders.listText(act)
                "remind_del"  -> delRemind(act, c.arg)
                "mode"        -> setMode(act, c.arg)
                "translate"   -> translate(act, c.arg)

                "open_app"   -> openApp(act, c.arg)
                "yt_search"  -> ytSearch(act, c.arg)
                "google"     -> google(act, c.arg)
                "call"       -> call(act, c.arg)
                "sms"        -> sms(act, c.arg)
                "whatsapp"   -> whatsapp(act, c.arg)
                "torch_on"   -> torch(act, true)
                "torch_off"  -> torch(act, false)
                "lock"       -> lock(act)
                "no_unlock"  -> c.say
                "wifi"       -> panel(act, Settings.ACTION_WIFI_SETTINGS,
                                    "WiFi settings khol di")
                "bluetooth"  -> panel(act,
                                    Settings.ACTION_BLUETOOTH_SETTINGS,
                                    "Bluetooth settings khol di")
                "data"       -> panel(act,
                                    Settings.ACTION_DATA_ROAMING_SETTINGS,
                                    "Data settings khol di")
                "airplane"   -> panel(act,
                                    Settings.ACTION_AIRPLANE_MODE_SETTINGS,
                                    "Airplane settings")
                "vol_up"     -> vol(act, 1)
                "vol_down"   -> vol(act, -1)
                "vol_max"    -> volMax(act)
                "mute"       -> mute(act)
                "bright_up"  -> bright(act, true)
                "bright_down"-> bright(act, false)
                "screenshot" -> shot(act)
                "battery"    -> battery(act)
                "time"       -> "Abhi " + SimpleDateFormat(
                                    "h:mm a", Locale.ENGLISH)
                                    .format(java.util.Date()) + " hue hain"
                "date"       -> "Aaj " + SimpleDateFormat(
                                    "d MMMM yyyy, EEEE", Locale.ENGLISH)
                                    .format(java.util.Date()) + " hai"
                "alarm"      -> alarm(act, c.arg)
                "alarm_app"  -> panel(act, AlarmClock.ACTION_SHOW_ALARMS,
                                    "Alarm khol diya")
                "timer"      -> timer(act, c.arg)
                "chat"       -> chat(act, c)

                // ═══ EYES wale (Accessibility) ═══
                "back"       -> eye(act) { it.back(); "Wapas" }
                "home"       -> eye(act) { it.home(); "Home" }
                "recents"    -> eye(act) { it.recents(); "Recent apps" }
                "notif_panel"-> eye(act) { it.notifPanel()
                                    "Notifications khol diye" }
                "read_notif" -> readNotif(act)
                "read_screen"-> readScreen(act)
                "scroll"     -> eye(act) {
                                    it.scroll(c.arg != "up")
                                    "Scroll kiya" }
                "tap"        -> tap(act, c.arg)
                "type"       -> eye(act) {
                                    if (it.type(c.arg)) "Likh diya"
                                    else "Text box nahi mila" }
                "close_app"  -> eye(act) { it.back(); it.home()
                                    "Band kar diya" }

                // ═══ MEMORY ═══
                "remember"   -> remember(act, c.arg)
                "recall"     -> recall(act, c.arg)
                "all_facts"  -> allFacts(act)

                // ═══ AUR ═══
                "calc"       -> calc(c.arg)
                "copy"       -> copy(act, c.arg)
                "copy_screen"-> copyScreen(act)
                "share"      -> share(act, c.arg)
                "dnd"        -> panel(act,
                                    Settings.ACTION_SOUND_SETTINGS,
                                    "Sound settings khol di")
                "ringer"     -> ringer(act, c.arg)
                "rotate"     -> rotate(act)
                "uninstall"  -> uninstall(act, c.arg)

                else         -> c.say.ifBlank {
                                    "Ye kaam abhi nahi kar sakta sir" }
            }
        } catch (e: Exception) {
            "Dikkat aa gayi sir: ${e.message?.take(60)}"
        }
    }

    // ═══════════════ APP ═══════════════

    private fun openApp(a: Activity, name: String): String {
        // special wale
        when (name) {
            "camera" -> {
                a.startActivity(Intent(
                    MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Camera khol diya"
            }
            "settings" -> {
                a.startActivity(Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Settings khol di"
            }
            "gallery" -> {
                a.startActivity(Intent(Intent.ACTION_VIEW)
                    .setType("image/*")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Gallery khol di"
            }
            "clock" -> {
                a.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Clock khol diya"
            }
            "calculator" -> {
                val i = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try { a.startActivity(i); "Calculator khol diya" }
                catch (e: Exception) { "Calculator nahi mila" }
            }
            "files" -> {
                a.startActivity(Intent(Intent.ACTION_GET_CONTENT)
                    .setType("*/*")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Files khol diye"
            }
        }

        // ═══ APP DHOONDHO — teen jagah ═══
        //
        // ⚠️ Pehle sirf Brain.APPS ki 40-app wali hardcoded list
        //    thi. Uske bahar ka koi bhi app ("Zomato kholo",
        //    "PhonePe kholo") kabhi nahi khulta tha — seedha
        //    Play Store chala jaata tha, chahe app phone me
        //    pehle se ho.
        //
        //    Ab AppRegistry poore phone ko scan karta hai, to
        //    HAR app khulta hai.
        var pkg = if (name.contains(".")) name
                  else Brain.APPS[name.lowercase()] ?: ""

        // registry se — asli phone ke app
        if (pkg.isBlank() || a.packageManager
                .getLaunchIntentForPackage(pkg) == null) {
            AppRegistry.find(a, name)?.let { pkg = it.pkg }
        }
        if (pkg.isBlank()) pkg = name

        val i = a.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            a.startActivity(i)
            val nm = AppRegistry.all(a)
                .find { it.pkg == pkg }?.name ?: ""
            return if (nm.isNotBlank()) "$nm khol diya"
                   else "Khol diya sir"
        }
        // App nahi hai — Play Store pe le jao
        return try {
            a.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Ye app phone me nahi hai — Play Store khol diya"
        } catch (e: Exception) {
            "Ye app aapke phone me nahi hai sir"
        }
    }

    // ═══════════════ SEARCH ═══════════════

    private fun ytSearch(a: Activity, q: String): String {
        if (q.isBlank()) return openApp(a, "youtube")
        val enc = Uri.encode(q)
        // Pehle YouTube app try karo
        try {
            val i = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", q)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            a.startActivity(i)
            return "YouTube pe '$q' dhoondh raha hoon"
        } catch (e: Exception) { }
        // App nahi — browser me
        a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://www.youtube.com/results?search_query=$enc"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "YouTube pe '$q' dhoondh raha hoon"
    }

    private fun google(a: Activity, q: String): String {
        if (q.isBlank()) return "Kya search karun sir?"
        try {
            val i = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, q)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            a.startActivity(i)
            return "'$q' search kar raha hoon"
        } catch (e: Exception) { }
        a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://www.google.com/search?q=${Uri.encode(q)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "'$q' search kar raha hoon"
    }

    // ═══════════════ CALL / SMS ═══════════════

    /** Contact ke naam se number dhoondho */
    private fun findNumber(a: Activity, name: String): String? {
        if (ContextCompat.checkSelfPermission(a,
                Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
            Uri.encode(name))
        a.contentResolver.query(uri, arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun call(a: Activity, who: String): String {
        if (who.isBlank()) return "Kis ko call karna hai sir?"
        // Number hi bola ho to
        val digits = who.filter { it.isDigit() || it == '+' }
        val num = if (digits.length >= 10) digits
                  else findNumber(a, who)
        if (num == null) return "'$who' naam ka contact nahi mila sir"

        val perm = ContextCompat.checkSelfPermission(a,
            Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val act = if (perm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        a.startActivity(Intent(act, Uri.parse("tel:$num"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return if (perm) "$who ko call laga raha hoon"
               else "$who ka number laga diya — dial dabaiye"
    }

    private fun sms(a: Activity, arg: String): String {
        val p = arg.split("|")
        val who = p.getOrNull(0)?.trim() ?: ""
        val msg = p.getOrNull(1)?.trim() ?: ""
        val digits = who.filter { it.isDigit() || it == '+' }
        val num = if (digits.length >= 10) digits else findNumber(a, who)
        if (num == null) return "'$who' ka contact nahi mila sir"
        a.startActivity(Intent(Intent.ACTION_SENDTO,
            Uri.parse("smsto:$num")).apply {
            putExtra("sms_body", msg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return "Message taiyaar hai — send dabaiye"
    }

    private fun whatsapp(a: Activity, arg: String): String {
        val p = arg.split("|")
        val who = p.getOrNull(0)?.trim() ?: ""
        val msg = p.getOrNull(1)?.trim() ?: ""
        val digits = who.filter { it.isDigit() }
        val num = if (digits.length >= 10) digits
                  else findNumber(a, who)?.filter { it.isDigit() }
        if (num == null) return "'$who' ka number nahi mila sir"
        val n = if (num.length == 10) "91$num" else num
        return try {
            a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://wa.me/$n?text=${Uri.encode(msg)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // ✅ Eyes chalu hai to SEND bhi khud daba dega
            val e = Eyes.live
            if (e != null && msg.isNotBlank()) {
                e.sendWhatsApp { /* IEYE RIS baad me bolega */ }
                "WhatsApp khol diya — send bhi daba raha hoon"
            } else if (msg.isNotBlank()) {
                "WhatsApp khol diya — send dabaiye " +
                "(Eyes on karo to main khud daba dunga)"
            } else "WhatsApp khol diya"
        } catch (e: Exception) {
            "WhatsApp nahi khul paya sir"
        }
    }

    // ═══════════════ TORCH ═══════════════

    private fun torch(a: Activity, on: Boolean): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return "Aapka Android purana hai sir"
        val cm = a.getSystemService(Context.CAMERA_SERVICE)
            as CameraManager
        val id = cm.cameraIdList.firstOrNull { cid ->
            cm.getCameraCharacteristics(cid).get(
                android.hardware.camera2.CameraCharacteristics
                    .FLASH_INFO_AVAILABLE) == true
        } ?: return "Is phone me flash nahi hai"
        cm.setTorchMode(id, on)
        torchOn = on
        return if (on) "Torch chalu" else "Torch band"
    }

    // ═══════════════ LOCK ═══════════════

    private fun lock(a: Activity): String {
        // Pehle Eyes try karo — usme permission dobara nahi maangni
        val e = Eyes.live
        if (e != null && Build.VERSION.SDK_INT >= 28 && e.lockScreen())
            return "Lock kar diya"
        val dpm = a.getSystemService(Context.DEVICE_POLICY_SERVICE)
            as DevicePolicyManager
        val admin = ComponentName(a, LockAdmin::class.java)
        if (!dpm.isAdminActive(admin)) {
            a.startActivity(Intent(
                DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Phone lock karne ke liye IEYE RIS ko ye " +
                    "permission chahiye. Ek baar hi deni hai.")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return "Ek baar permission de dijiye sir, phir hamesha " +
                   "lock kar paunga"
        }
        dpm.lockNow()
        return "Lock kar diya"
    }

    // ═══════════════ VOLUME / BRIGHTNESS ═══════════════

    private fun am(a: Activity) =
        a.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun vol(a: Activity, dir: Int): String {
        am(a).adjustStreamVolume(AudioManager.STREAM_MUSIC,
            if (dir > 0) AudioManager.ADJUST_RAISE
            else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI)
        return if (dir > 0) "Volume badha diya" else "Volume kam kar diya"
    }

    private fun volMax(a: Activity): String {
        val m = am(a)
        m.setStreamVolume(AudioManager.STREAM_MUSIC,
            m.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            AudioManager.FLAG_SHOW_UI)
        return "Full volume kar diya"
    }

    private fun mute(a: Activity): String {
        am(a).setStreamVolume(AudioManager.STREAM_MUSIC, 0,
            AudioManager.FLAG_SHOW_UI)
        return "Silent kar diya"
    }

    private fun bright(a: Activity, up: Boolean): String {
        if (!Settings.System.canWrite(a)) {
            a.startActivity(Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${a.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Brightness badalne ki permission de dijiye sir"
        }
        val cur = Settings.System.getInt(a.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS, 128)
        val next = (cur + if (up) 50 else -50).coerceIn(10, 255)
        Settings.System.putInt(a.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS, next)
        return if (up) "Brightness badha di" else "Brightness kam ki"
    }

    // ═══════════════ AUR ═══════════════

    private fun panel(a: Activity, action: String, ok: String): String {
        a.startActivity(Intent(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ok
    }

    @Suppress("UNUSED_PARAMETER")
    private fun shot(a: Activity): String {
        // ✅ Ab Eyes (Accessibility) se ho jata hai — Android 9+
        val e = Eyes.live
        if (e != null && Build.VERSION.SDK_INT >= 28) {
            return if (e.screenshot()) "Screenshot le liya"
                   else "Nahi ho paya — Power + Volume Down dabaiye"
        }
        return "Sir, screenshot ke liye IEYE RIS ko Accessibility " +
               "permission chahiye (Settings me). Ya Power + " +
               "Volume Down ek saath dabaiye."
    }

    private fun battery(a: Activity): String {
        val bm = a.getSystemService(Context.BATTERY_SERVICE)
            as BatteryManager
        val p = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val ch = bm.isCharging
        return "Battery $p% hai sir" + if (ch) ", charge ho rahi hai"
               else ""
    }

    private fun alarm(a: Activity, hm: String): String {
        val p = hm.split(":")
        var h = p.getOrNull(0)?.toIntOrNull() ?: 7
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        // "7 baje" bola to subah 7 maano agar abhi raat hai
        val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (h in 1..11 && now >= 12) { /* subah ka hi rakho */ }
        a.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, h)
            putExtra(AlarmClock.EXTRA_MINUTES, m)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return "Alarm laga diya — $h:${m.toString().padStart(2, '0')}"
    }

    // ═══════════════ EYES (Accessibility) ═══════════════

    /**
     * Eyes chalu hai to kaam karo, warna user ko batao.
     * ⚠️ Ye permission user ko KHUD deni padti hai — Android
     *    ise programmatically on nahi karne deta.
     */
    private fun eye(a: Activity, f: (Eyes) -> String): String {
        val e = Eyes.live ?: return needEyes(a)
        return try { f(e) } catch (ex: Exception) {
            "Nahi ho paya sir"
        }
    }

    private fun needEyes(a: Activity): String {
        Eyes.openSettings(a)
        return "Sir, iske liye ek permission chahiye. Settings khol " +
               "di hai — list me IEYE RIS dhoondh kar ON kar dijiye. " +
               "Ek baar hi karna hai."
    }

    private fun tap(a: Activity, what: String): String {
        val e = Eyes.live ?: return needEyes(a)
        return if (e.tapText(what)) "'$what' daba diya"
               else "Screen pe '$what' nahi mila sir"
    }

    private fun readScreen(a: Activity): String {
        val e = Eyes.live ?: return needEyes(a)
        val t = e.readScreen()
        return if (t.isBlank()) "Screen pe kuch padhne layak nahi hai"
               else t.take(700)
    }

    /**
     * v3.1 — ab do jagah se padhta hai.
     *
     * Pehle sirf Eyes (Accessibility) se padhta tha. Dikkat:
     * Eyes sirf WAHI notification pakadti hai jo app khulne ke
     * BAAD aayi ho. Purani sab gayab.
     *
     * Ab Notif service (agar on hai) se poori list milti hai —
     * app band ho tab bhi.
     */
    private fun readNotif(a: Activity): String {
        if (Notif.allowed(a)) {
            val r = Notif.readOut(a)
            if (!r.startsWith("Koi nayi")) return r
        }
        val e = Eyes.live
        if (e != null) {
            val n = e.lastNotifs(5)
            if (n.isNotEmpty())
                return "Aapke notifications:\n" + n.joinToString("\n")
        }
        if (!Notif.allowed(a)) {
            Notif.openSettings(a)
            return "Sir, notification padhne ke liye ijazat chahiye. " +
                   "Settings khol raha hoon — \"IEYE RIS\" ko on kar " +
                   "dijiye. Uske baad main saari notification padh " +
                   "kar suna dunga."
        }
        return "Koi naya notification nahi hai sir"
    }

    /** 🔔 v3.1 — auto-reply on/off */
    private fun autoReply(a: Activity, arg: String): String {
        val on = arg != "off"
        if (on && !Notif.allowed(a)) {
            Notif.openSettings(a)
            return "Iske liye notification ki ijazat chahiye sir. " +
                   "Settings khol raha hoon — IEYE RIS ko on kar " +
                   "dijiye, phir dobara boliye."
        }
        Notif.setAuto(a, on)
        return if (on)
            "Auto-reply chalu sir. WhatsApp/SMS pe koi message " +
            "kare to main khud jawab de dunga:\n\n" +
            "\"" + Notif.replyText(a) + "\"\n\n" +
            "⚠️ Ek hi bande ko 30 minute me ek hi baar jawab " +
            "jayega — warna wo pareshan ho jayega. Group me " +
            "bilkul nahi bhejunga."
        else "Auto-reply band kar diya sir."
    }

    // ═══════════════ MEMORY ═══════════════

    private fun remember(a: Activity, raw: String): String {
        val m = Memory(a)
        val p = raw.split("=", limit = 2)
        return if (p.size == 2) {
            m.remember(p[0].trim(), p[1].trim())
            "Yaad rakh liya — ${p[0].trim()} hai ${p[1].trim()}"
        } else {
            m.remember("note_" + System.currentTimeMillis()
                .toString().takeLast(4), raw)
            "Yaad rakh liya sir"
        }
    }

    private fun recall(a: Activity, what: String): String {
        val v = Memory(a).recall(what)
        return v?.let { "Aapka $what $it hai sir" }
            ?: "Ye mujhe nahi pata sir — bataiye to yaad rakh lunga"
    }

    private fun allFacts(a: Activity): String {
        val f = Memory(a).allFacts()
        if (f.isEmpty()) return "Abhi kuch yaad nahi hai sir"
        return "Ye sab yaad hai:\n" +
            f.entries.joinToString("\n") { "• ${it.key} — ${it.value}" }
    }

    // ═══════════════ CALCULATOR ═══════════════

    /** Chhota sa calculator — "25 * 4" jaise sawaal */
    private fun calc(raw: String): String {
        var t = raw.lowercase()
            .replace("plus", "+").replace("jod", "+")
            .replace("minus", "-").replace("ghata", "-")
            .replace("multiply", "*").replace("guna", "*")
            .replace("into", "*").replace("x", "*").replace("×", "*")
            .replace("divide", "/").replace("bhag", "/")
            .replace("÷", "/").replace("by", "/")
        val m = Regex("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*" +
                      "(-?\\d+(?:\\.\\d+)?)").find(t)
            ?: return "Sawaal samajh nahi aaya sir"
        val x = m.groupValues[1].toDouble()
        val y = m.groupValues[3].toDouble()
        val r = when (m.groupValues[2]) {
            "+" -> x + y
            "-" -> x - y
            "*" -> x * y
            "/" -> if (y == 0.0) return "Zero se bhag nahi hota sir"
                   else x / y
            else -> return "Samajh nahi aaya"
        }
        val out = if (r == r.toLong().toDouble()) r.toLong().toString()
                  else String.format(Locale.US, "%.2f", r)
        return "Jawab hai $out"
    }

    // ═══════════════ CLIPBOARD / SHARE ═══════════════

    private fun copy(a: Activity, text: String): String {
        val cm = a.getSystemService(Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        cm.setPrimaryClip(
            android.content.ClipData.newPlainText("IEYE RIS", text))
        return "Copy kar liya"
    }

    private fun copyScreen(a: Activity): String {
        val e = Eyes.live ?: return needEyes(a)
        val t = e.readScreen()
        if (t.isBlank()) return "Screen pe kuch nahi mila"
        return copy(a, t)
    }

    private fun share(a: Activity, text: String): String {
        a.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Share menu khol diya"
    }

    // ═══════════════ RINGER / ROTATE ═══════════════

    private fun ringer(a: Activity, mode: String): String {
        val m = am(a)
        return try {
            m.ringerMode = when (mode) {
                "silent" -> AudioManager.RINGER_MODE_SILENT
                "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            "Phone $mode mode me"
        } catch (e: Exception) {
            panel(a, Settings.ACTION_SOUND_SETTINGS,
                "Sound settings khol di — DND permission chahiye")
        }
    }

    private fun rotate(a: Activity): String {
        if (!Settings.System.canWrite(a)) {
            a.startActivity(Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${a.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Permission de dijiye sir"
        }
        val cur = Settings.System.getInt(a.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION, 0)
        Settings.System.putInt(a.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION, 1 - cur)
        return if (cur == 0) "Auto-rotate chalu" else "Auto-rotate band"
    }

    // ⚠️ v4.0 — purana uninstall() hataya. Wo Brain.APPS ki
    //    hardcoded list dekhta tha, isliye sirf 30-40 mashhoor
    //    app pehchanta tha. Naya wala AppRegistry se dekhta hai
    //    — phone ke SAARE app milte hain. Neeche hai.

    private fun timer(a: Activity, min: String): String {
        val m = min.toIntOrNull() ?: 5
        a.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, m * 60)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return "$m minute ka timer laga diya"
    }

    /** Action ka aadmi-samajhne-layak naam (multi-command list me) */
    // ═══════════════════════════════════════════
    //   IEYE RIS ke apne hukum
    // ═══════════════════════════════════════════

    // ═══════════════════════════════════════════
    //   v4.0 — NAYE KAAM
    // ═══════════════════════════════════════════

    /**
     * 🎵 Gaana control — play / pause / next / previous
     *
     * ⚠️ Ye kisi bhi music app pe chalta hai (Spotify, YouTube
     *    Music, Gaana) kyunki media BUTTON bhejte hain — wahi
     *    jo headphone ka button bhejta hai. Kisi app ke andar
     *    ghusne ki zarurat nahi.
     */
    private fun music(a: Activity, what: String): String {
        val code = when {
            what.contains("pause") || what.contains("ruk") ->
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE
            what.contains("next") || what.contains("agla") ||
            what.contains("aage") ->
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT
            what.contains("prev") || what.contains("pichla") ||
            what.contains("peeche") || what.contains("wapas") ->
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS
            what.contains("stop") || what.contains("band") ->
                android.view.KeyEvent.KEYCODE_MEDIA_STOP
            else -> android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        return try {
            val m = a.getSystemService(Context.AUDIO_SERVICE)
                as AudioManager
            m.dispatchMediaKeyEvent(android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN, code))
            m.dispatchMediaKeyEvent(android.view.KeyEvent(
                android.view.KeyEvent.ACTION_UP, code))
            when (code) {
                android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> "Agla gaana"
                android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS ->
                    "Pichla gaana"
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> "Gaana rok diya"
                android.view.KeyEvent.KEYCODE_MEDIA_STOP -> "Gaana band"
                else -> "Ho gaya sir"
            }
        } catch (e: Exception) {
            "Koi music app chal nahi raha sir."
        }
    }

    /**
     * 🗑 App hatao.
     *
     * ⚠️ IEYE RIS KHUD app nahi hata sakta — Android kisi app
     *    ko dusre app uninstall karne nahi deta (aur sahi hi
     *    hai). Hum sirf uninstall ka screen khol sakte hain,
     *    "Uninstall" user ko khud dabana padega.
     */
    private fun uninstall(a: Activity, name: String): String {
        val app = AppRegistry.find(a, name)
            ?: return "\"$name\" naam ka app nahi mila sir."
        return try {
            a.startActivity(Intent(Intent.ACTION_DELETE,
                android.net.Uri.parse("package:" + app.pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "${app.name} ka uninstall screen khol diya — " +
            "\"Uninstall\" aap daba dijiye. Main khud nahi " +
            "hata sakta, Android ijazat nahi deta."
        } catch (e: Exception) {
            "Uninstall screen nahi khula sir."
        }
    }

    /** ℹ️ App ki jaankari (size, permission, force stop) */
    private fun appInfo(a: Activity, name: String): String {
        val app = AppRegistry.find(a, name)
            ?: return "\"$name\" naam ka app nahi mila sir."
        return try {
            a.startActivity(Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + app.pkg))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "${app.name} ki jaankari khol di sir."
        } catch (e: Exception) { "Nahi khul paya sir." }
    }

    /** 📤 Kuch bhi share karo (WhatsApp, Telegram, kahin bhi) */
    private fun shareText(a: Activity, text: String): String {
        if (text.isBlank()) return "Kya share karna hai sir?"
        return try {
            a.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Share ka menu khol diya sir."
        } catch (e: Exception) { "Share nahi ho paya sir." }
    }

    /**
     * 📝 Jaldi ka note.
     *
     * ⚠️ Reminder se ALAG hai — isme koi waqt nahi hota, bas
     *    likh kar rakh lete hain. "doodh lena hai" jaisi
     *    baaton ke liye.
     */
    private fun quickNote(a: Activity, text: String): String {
        if (text.isBlank()) return "Kya likhna hai sir?"
        return try {
            val f = java.io.File(a.filesDir, "notes.json")
            val arr = if (f.exists())
                org.json.JSONArray(f.readText()) else org.json.JSONArray()
            arr.put(org.json.JSONObject().apply {
                put("t", text.trim())
                put("at", System.currentTimeMillis())
            })
            // sirf aakhri 100
            val keep = org.json.JSONArray()
            for (i in maxOf(0, arr.length() - 100) until arr.length())
                keep.put(arr.get(i))
            f.writeText(keep.toString())
            "Likh liya sir — \"${text.trim().take(50)}\""
        } catch (e: Exception) { "Note save nahi hua sir." }
    }

    private fun readNotes(a: Activity): String = try {
        val f = java.io.File(a.filesDir, "notes.json")
        if (!f.exists()) "Koi note nahi hai sir."
        else {
            val arr = org.json.JSONArray(f.readText())
            if (arr.length() == 0) "Koi note nahi hai sir."
            else {
                val sb = StringBuilder("📝 NOTES (${arr.length()})\n\n")
                val from = maxOf(0, arr.length() - 12)
                for (i in from until arr.length()) {
                    sb.append("${i - from + 1}. ")
                    sb.append(arr.getJSONObject(i).optString("t"))
                    sb.append("\n")
                }
                sb.toString().trim()
            }
        }
    } catch (e: Exception) { "Notes padhe nahi gaye sir." }

    // ═══════════════════════════════════════════
    //   v2.2 — REMINDER / MODE / TRANSLATE
    // ═══════════════════════════════════════════

    /**
     * ═══ v2.2 — ASLI BAAT-CHEET ═══
     *
     * ⚠️ Pehle yahan sirf ye tha:
     *        "chat" -> c.say.ifBlank { "Ji sir?" }
     *
     *    Yaani jab bhi koi asli sawaal poochha jata ("photosynthesis
     *    kya hai", "ye code samjhao"), IEYE RIS sirf 4-7 shabd ka
     *    tuk-bandi wala jawab de deta tha jo aiMulti ne bana diya
     *    tha. Study Mode aur Coding Mode ka koi matlab hi nahi
     *    banta tha.
     *
     *    Ab asli sawaal AI ko poora bheja jata hai, mode ki
     *    hidayat ke saath.
     *
     * ⚠️ Chhoti baat pe AI mat bulao — "hello", "thanks" jaise
     *    par c.say hi theek hai. Warna har baar 1-2 second lagta
     *    aur Groq ki limit bekaar khatam hoti.
     */
    private fun chat(act: Activity, c: Brain.Cmd): String {
        val q = c.arg.ifBlank { c.raw }.trim()
        val mode = Modes.get(act)

        // sawaal chhota / khali -> AI ki zarurat nahi
        val realQ = q.length >= 8 || q.contains("?") ||
            Regex("(kya|kaise|kyun|kyu|batao|samjha|explain|" +
                  "matlab|meaning|difference|fark|likho|banao|" +
                  "code|error|quiz|test|revision)")
                .containsMatchIn(q.lowercase())

        if (!realQ || q.isBlank())
            return c.say.ifBlank { "Ji sir?" }

        val out = Brain.talk(act, q, Modes.prompt(mode))
        return out.ifBlank { c.say.ifBlank { "Ji sir?" } }
    }

    private fun addRemind(act: Activity, raw: String): String {
        val (at, rep, task) = Reminders.parse(raw)
        if (at == 0L) {
            return "Sir, waqt samajh nahi aaya. Aise boliye — " +
                   "\"10 minute baad chai yaad dilana\" ya " +
                   "\"kal subah 7 baje uthana\"."
        }
        val r = Reminders.add(act, task, at, rep)
        val when_ = Reminders.fmt(r.at)
        val rp = when (rep) {
            "daily" -> " (roz)"
            "weekly" -> " (har hafte)"
            else -> ""
        }
        var msg = "Theek hai sir — $when_$rp yaad dila dunga: ${r.text}"
        // ⚠️ Chup-chaap fail mat ho. Android 12+ pe exact alarm
        //    ki ijazat na ho to reminder late bajta hai — user ko
        //    pata hona chahiye.
        if (!Reminders.canExact(act)) {
            msg += "\n\n⚠️ Phone ne exact alarm ki ijazat nahi di " +
                   "hai, isliye thoda late baj sakta hai. " +
                   "Settings → Apps → IEYE RIS → \"Alarms & " +
                   "reminders\" on kar dijiye."
        }
        return msg
    }

    private fun delRemind(act: Activity, arg: String): String {
        if (arg.equals("all", true) || arg.isBlank()) {
            val n = Reminders.deleteAll(act)
            return if (n == 0) "Koi reminder tha hi nahi sir."
                   else "Saare $n reminder hata diye sir."
        }
        val list = Reminders.all(act)
        val n = arg.trim().toIntOrNull()
            ?: return "Kaunsa hatana hai sir? Number bataiye."
        // user "1" bole to pehla wala (list me dikhne wala kram)
        val r = list.getOrNull(n - 1)
            ?: return "Sir, $n number ka reminder nahi mila. " +
                      "\"reminder list\" bol kar dekh lijiye."
        Reminders.delete(act, r.id)
        return "Hata diya sir — ${r.text}"
    }

    private fun setMode(act: Activity, m: String): String {
        Modes.set(act, m)
        return when (m) {
            Modes.STUDY ->
                "🎓 Study Mode chalu sir. Ab main teacher hoon — " +
                "kuch bhi poochhiye, aasan bhasha me samjhaunga. " +
                "\"quiz lo\" bol kar test bhi de sakte hain."
            Modes.CODING ->
                "💻 Coding Mode chalu sir. Code bhejiye — " +
                "samjhaunga, error pakdunga, fix karke dunga."
            else ->
                "⚡ Normal mode. Wapas aam kaam pe aa gaya sir."
        }
    }

    /**
     * Translate — spec section 29.
     *
     * ⚠️ Ye AI call karta hai, isliye THREAD block hota hai.
     *    Actions.run() pehle se background thread se bulaya
     *    jata hai (MainActivity/VoiceActivity dono me), isliye
     *    yahan safe hai. UI thread pe hota to app jam jaati.
     */
    private fun translate(act: Activity, arg: String): String {
        val p = arg.split("|", limit = 2)
        val lang = p.getOrNull(0)?.trim().orEmpty()
            .ifBlank { "English" }
        var text = p.getOrNull(1)?.trim().orEmpty()

        // 🌐 v4.2 — pehle offline shabdkosh (turant, bina net)
        if (text.isNotBlank() && text.split(" ").size <= 2) {
            Offline.translate(act, text, lang.equals("Hindi", true))
                ?.let { return it }
        }

        // khali = pichhla message translate karna hai
        if (text.isBlank()) {
            text = try {
                Memory(act).load().lastOrNull { !it.me }?.text.orEmpty()
            } catch (e: Exception) { "" }
            if (text.isBlank())
                return "Sir, kya translate karna hai? Bataiye."
        }
        val out = Brain.talk(act, Modes.translatePrompt(lang, text))
        return if (out.isBlank()) "Translate nahi ho paya sir."
               else out
    }

    // ═══════════════════════════════════════════
    //   v2.0 — MIC aur AWAAZ ke hukum
    //
    //   User ki shikayat thi:
    //     "mic band karo bolta hoon to nahi karta"
    //     "ab kuch mat bolna bolta hoon to phir bhi bolti hai"
    //
    //   Wajah: ye hukum Brain me EXACT match maangte the, aur
    //   inka koi action tha hi nahi. Ab dono hain.
    // ═══════════════════════════════════════════

    /** Kaan band — sunna bilkul band */
    private fun micOff(act: Activity): String {
        try { WakeService.stop(act) } catch (e: Exception) {}
        try {
            (act as? MainActivity)?.forceStopListening()
        } catch (e: Exception) {}
        Keys(act).setFlag("mic_off", true)
        return "Mic band kar diya sir. Ab main nahi sun raha. " +
               "Chalu karne ke liye mic ka button dabaiye ya " +
               "likh kar \"mic chalu karo\" boliye."
    }

    private fun micOn(act: Activity): String {
        Keys(act).setFlag("mic_off", false)
        try {
            if (Keys(act).flag("wake_on", false)) WakeService.start(act)
        } catch (e: Exception) {}
        return "Mic chalu. Sun raha hoon sir."
    }

    /**
     * Muh band — bolna band, par sunna chalu rahega.
     *
     * ⚠️ Sirf flag lagana kaafi nahi tha — jo awaaz ABHI bol
     *    rahi hai use bhi rokna padta hai, warna user ko lagta
     *    hai hukum maana hi nahi.
     */
    private fun muteVoice(act: Activity): String {
        try { Voice(act).stop() } catch (e: Exception) {}
        Voice.setMuted(act, true)
        return "Theek hai sir, ab main chup hoon. Jawab " +
               "sirf likha hua aayega. Bolne ke liye " +
               "\"ab bolo\" kah dijiye."
    }

    private fun unmuteVoice(act: Activity): String {
        Voice.setMuted(act, false)
        return "Ji sir, ab main bol sakta hoon."
    }

    /**
     * ⚠️ v4.0 — "WAKE UP" AB ASLI ME UTHTA HAI
     *
     * User ki shikayat: "bolo iris wake up to real mein uth
     * Jana chahie"
     *
     * Pehle wakeUp() sirf do flag badalta tha aur ek jumla
     * bol deta tha. Agar pehle "shutdown" bola gaya tha to
     * WakeService aur Bubble band pade rehte the — yaani
     * "wake up" bolne ke baad bhi kuch zinda nahi hota tha.
     *
     * Ab: sab kuch WAAPAS chalu hota hai.
     */
    private fun wakeUp(act: Activity): String {
        val k = Keys(act)
        Voice.setMuted(act, false)
        k.setFlag("mic_off", false)

        val up = mutableListOf<String>()

        // 👂 wake word wapas — tabhi jab user ne khud on rakha ho
        try {
            if (k.wake() && !WakeService.on()) {
                WakeService.start(act); up.add("kaan")
            }
        } catch (e: Exception) {}

        // 🫧 master circle wapas
        try {
            if (Bubble.allowed(act) && !Bubble.on() &&
                !k.flag("bubble_off", false)) {
                Bubble.start(act); up.add("circle")
            }
        } catch (e: Exception) {}

        return if (up.isEmpty())
            "IEYE RIS online sir. Boliye."
        else
            "IEYE RIS wapas online — " + up.joinToString(" aur ") +
            " chalu kar diya. Boliye sir."
    }

    private fun sleepMode(act: Activity): String {
        try { Sfx.play(act, Sfx.SLEEP) } catch (e: Exception) {}
        // ⚠️ "sleep" ka matlab APP standby — phone band karna
        //    NAHI. Android kisi app ko phone sulane nahi deta.
        try { WakeService.stop(act) } catch (e: Exception) {}
        try { Bubble.stop(act) } catch (e: Exception) {}
        return "Standby me ja raha hoon sir. Bulana ho to " +
               "\"IEYE RIS wake up\" boliye."
    }

    /**
     * 🛑 "stop" / "ruk jao" — bol kar rokna.
     *
     * v4.1: pehle ye sirf Voice.stop() karta tha — yaani sirf
     * bolna rukta tha. Agent apna kaam poora karta rehta tha!
     * User "ruko" chillata rehta aur phone khud chalta rehta.
     *
     * Ab ye wahi Halt bulata hai jo STOP button bulata hai —
     * dono ka natija bilkul ek jaisa.
     */
    private fun stopAll(act: Activity): String {
        Halt.stopAll(act)
        return "Ruk gaya sir."
    }

    private fun systemStatus(act: Activity): String {
        val sb = StringBuilder("👁 IEYE RIS — STATUS\n\n")
        // battery
        try {
            val bm = act.getSystemService(Context.BATTERY_SERVICE)
                as android.os.BatteryManager
            val p = bm.getIntProperty(
                android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
            sb.append("Battery ......... ").append(p).append("%\n")
        } catch (e: Exception) {}
        // internet
        val net = try {
            val cm = act.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
            val cap = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                cap == null -> "OFFLINE"
                cap.hasTransport(android.net.NetworkCapabilities
                    .TRANSPORT_WIFI) -> "WiFi"
                cap.hasTransport(android.net.NetworkCapabilities
                    .TRANSPORT_CELLULAR) -> "Mobile data"
                else -> "OK"
            }
        } catch (e: Exception) { "?" }
        sb.append("Network ......... ").append(net).append("\n")
        sb.append("Eyes (screen) ... ")
            .append(if (Eyes.on()) "ON" else "OFF").append("\n")
        sb.append("Bubble .......... ")
            .append(if (Bubble.on()) "ON" else "OFF").append("\n")
        val k = Keys(act)
        sb.append("AI key .......... ")
            .append(if (k.groq().isNotBlank()) "OK" else "NAHI")
            .append("\n")
        sb.append("Wake word ....... ").append(k.wakeWord())
            .append("\n\n")
        sb.append("SYSTEM READY")
        return sb.toString()
    }

    private fun shutdownSelf(act: Activity): String {
        // ⚠️ Sirf APNI service band — phone nahi. Android kisi
        //    app ko phone band karne ki ijazat nahi deta, aur
        //    dena bhi nahi chahiye.
        try { WakeService.stop(act) } catch (e: Exception) {}
        try { Bubble.stop(act) } catch (e: Exception) {}
        try { Voice(act).stop() } catch (e: Exception) {}
        // ⚠️ v4.0 — bubble_off flag NAHI lagate. Wo user ke
        //    khud band karne ke liye hai. Warna "wake up" bolne
        //    par circle wapas nahi aata.
        return "Sab band kar diya sir. Wapas bulana ho to " +
               "\"IEYE RIS wake up\" boliye."
    }

    fun label(a: String): String = when (a) {
        "open_app" -> "App khol raha hoon"
        "yt_search" -> "YouTube pe dhoondh raha hoon"
        "google" -> "Google kar raha hoon"
        "call" -> "Call laga raha hoon"
        "sms" -> "SMS bhej raha hoon"
        "whatsapp" -> "WhatsApp bhej raha hoon"
        "torch_on" -> "Torch on"
        "torch_off" -> "Torch off"
        "wifi" -> "WiFi"
        "bluetooth" -> "Bluetooth"
        "dnd" -> "DND"
        "vol_up" -> "Volume badha raha hoon"
        "vol_down" -> "Volume kam kar raha hoon"
        "vol_max" -> "Volume full"
        "mute" -> "Mute"
        "bright_up" -> "Brightness badha raha hoon"
        "bright_down" -> "Brightness kam kar raha hoon"
        "lock" -> "Phone lock"
        "screenshot" -> "Screenshot le raha hoon"
        "wake" -> "Jaag raha hoon"
        "sleep" -> "Standby me ja raha hoon"
        "stop" -> "Ruk raha hoon"
        "status" -> "Status dekh raha hoon"
        "shutdown" -> "Sab band kar raha hoon"
        "mic_off" -> "Mic band kar raha hoon"
        "mic_on" -> "Mic chalu kar raha hoon"
        "mute_voice" -> "Chup ho raha hoon"
        "unmute_voice" -> "Wapas bol raha hoon"
        "remind" -> "Reminder laga raha hoon"
        "auto_reply" -> "Auto-reply set kar raha hoon"
        "music" -> "Gaana chala raha hoon"
        "battery_saver" -> "Battery saver"
        "data" -> "Mobile data"
        "airplane" -> "Airplane mode"
        "location" -> "Location"
        "uninstall" -> "App hata raha hoon"
        "app_info" -> "App ki jaankari"
        "share" -> "Share kar raha hoon"
        "note" -> "Note likh raha hoon"
        "notes" -> "Note padh raha hoon"
        "remind_list" -> "Reminder dekh raha hoon"
        "remind_del" -> "Reminder hata raha hoon"
        "mode" -> "Mode badal raha hoon"
        "translate" -> "Translate kar raha hoon"
        "battery" -> "Battery dekh raha hoon"
        "time" -> "Time bata raha hoon"
        "date" -> "Date bata raha hoon"
        "back" -> "Peeche ja raha hoon"
        "home" -> "Home"
        "recents" -> "Recent apps"
        "read_notif" -> "Notification padh raha hoon"
        "read_screen" -> "Screen padh raha hoon"
        "scroll" -> "Scroll kar raha hoon"
        "tap" -> "Button daba raha hoon"
        "type" -> "Likh raha hoon"
        "remember" -> "Yaad rakh raha hoon"
        "recall" -> "Yaad kar raha hoon"
        "alarm" -> "Alarm laga raha hoon"
        "timer" -> "Timer laga raha hoon"
        "calc" -> "Hisaab kar raha hoon"
        "chat" -> "Jawab de raha hoon"
        else -> a.replace("_", " ")
    }
}
