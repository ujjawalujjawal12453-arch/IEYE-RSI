package com.ravanx.ieyeris

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 📱 APP DISCOVERY ENGINE + APP REGISTRY
 * ══════════════════════════════════════
 *
 * Spec section 3 aur 4.
 *
 * Phone me kaunse app hain — IEYE RIS khud dhoondhta hai.
 * Isse do bade fayde:
 *
 *   1. "Zomato kholo" bolne pe kaam karega, chahe wo Brain.APPS
 *      ki hardcoded list me ho ya na ho. Pehle sirf 40 app
 *      wali list thi — baaki sab pe "samajh nahi aaya" aata tha.
 *
 *   2. AI ko pata hota hai ki is phone me KYA hai. Wo bina
 *      soche "Spotify kholo" nahi bolega jab Spotify hai hi
 *      nahi.
 *
 * ⚠️ SIRF WAHI JAANKARI jo Android khud deta hai:
 *      app ka naam, package, launch ho sakta hai ya nahi.
 *    Kisi doosre app ka data, uski private file, uska
 *    database — kuch nahi chhua jaata. Spec ka bhi yahi hukum
 *    hai aur ye sahi bhi hai.
 *
 * ⚠️ Android 11+ (API 30) pe `queryIntentActivities` sirf wahi
 *    app dikhata hai jo manifest ke <queries> me likhe ho, YA
 *    tab poori list milti hai jab QUERY_ALL_PACKAGES permission
 *    ho. Hum LAUNCHER wala intent use karte hain — wo bina
 *    special permission ke bhi chalta hai, kyunki launcher
 *    apps "public" maane jaate hain.
 */
object AppRegistry {

    data class App(
        val name: String,          // "WhatsApp"
        val pkg: String,           // "com.whatsapp"
        var alias: String = "",    // user ka apna naam
        var hidden: Boolean = false
    ) {
        /** Bolne pe ye naam match hone chahiye */
        fun names(): List<String> {
            val out = mutableListOf(name.lowercase())
            if (alias.isNotBlank()) out.add(alias.lowercase())
            // "com.google.android.youtube" -> "youtube"
            out.add(pkg.substringAfterLast('.').lowercase())
            return out.filter { it.length > 1 }.distinct()
        }
    }

    private var apps: List<App> = emptyList()
    private var lastScan = 0L

    private fun file(c: Context) = File(c.filesDir, "apps.json")

    // ─────────────────────────────────────────────
    //  DHOONDHO
    // ─────────────────────────────────────────────

    /**
     * Phone ke saare launchable app dhoondho.
     *
     * @param force true = cache bhool kar dobara scan
     */
    fun scan(c: Context, force: Boolean = false): List<App> {
        if (!force && apps.isNotEmpty() &&
            System.currentTimeMillis() - lastScan < 3600_000) {
            return apps
        }
        val pm = c.packageManager
        val found = LinkedHashMap<String, App>()

        // saved alias/hidden wapas laane ke liye
        val old = load(c).associateBy { it.pkg }

        try {
            val main = Intent(Intent.ACTION_MAIN, null)
                .addCategory(Intent.CATEGORY_LAUNCHER)
            val list: List<ResolveInfo> =
                if (android.os.Build.VERSION.SDK_INT >= 33)
                    pm.queryIntentActivities(
                        main, PackageManager.ResolveInfoFlags
                            .of(0L))
                else
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(main, 0)

            for (ri in list) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == c.packageName) continue      // khud ko chhod
                if (found.containsKey(pkg)) continue
                val label = try {
                    ri.loadLabel(pm).toString().trim()
                } catch (e: Exception) { pkg.substringAfterLast('.') }
                if (label.isBlank()) continue
                val o = old[pkg]
                found[pkg] = App(label, pkg,
                    o?.alias ?: "", o?.hidden ?: false)
            }
        } catch (e: Exception) {
            Brain.log("app scan fail: " + (e.message ?: ""))
        }

        apps = found.values.sortedBy { it.name.lowercase() }
        lastScan = System.currentTimeMillis()
        save(c)
        Brain.log("📱 ${apps.size} app mile")
        return apps
    }

    fun all(c: Context): List<App> {
        if (apps.isEmpty()) scan(c)
        return apps
    }

    /** Jo chhupaye nahi gaye */
    fun visible(c: Context) = all(c).filter { !it.hidden }

    fun count(c: Context) = all(c).size

    // ─────────────────────────────────────────────
    //  DHOONDHNA — bolne wale naam se
    // ─────────────────────────────────────────────

    /**
     * "youtube kholo" -> com.google.android.youtube
     *
     * Teen kadam:
     *   1. poora naam match
     *   2. shuruaat match ("whats" -> WhatsApp)
     *   3. kahin bhi match
     */
    fun find(c: Context, spoken: String): App? {
        val q = spoken.lowercase().trim()
        if (q.isBlank()) return null
        val list = visible(c)

        // 1. bilkul wahi naam
        list.forEach { a ->
            if (a.names().any { it == q }) return a
        }
        // 2. shuruaat
        list.forEach { a ->
            if (a.names().any { it.startsWith(q) && q.length >= 3 })
                return a
        }
        // 3. kahin bhi
        list.forEach { a ->
            if (a.names().any { q.length >= 4 && it.contains(q) })
                return a
        }
        // 4. ulta — user ne lamba bola, app ka naam chhota
        list.forEach { a ->
            if (a.names().any { it.length >= 4 && q.contains(it) })
                return a
        }
        return null
    }

    /**
     * AI ko batane ke liye chhoti list.
     * ⚠️ Sab 200 app bhejne se token khatam ho jaate hain,
     *    isliye sirf naam, aur 60 tak.
     */
    fun forAI(c: Context, limit: Int = 60): String {
        val v = visible(c)
        if (v.isEmpty()) return ""
        val names = v.take(limit).joinToString(", ") { it.name }
        val more = if (v.size > limit)
            " (aur ${v.size - limit})" else ""
        return "Is phone me ye app hain: $names$more"
    }

    // ─────────────────────────────────────────────
    //  USER ke apne naam
    // ─────────────────────────────────────────────

    fun setAlias(c: Context, pkg: String, alias: String) {
        apps.find { it.pkg == pkg }?.alias = alias.trim()
        save(c)
    }

    fun setHidden(c: Context, pkg: String, hide: Boolean) {
        apps.find { it.pkg == pkg }?.hidden = hide
        save(c)
    }

    fun reset(c: Context) {
        try { file(c).delete() } catch (e: Exception) {}
        apps = emptyList()
        lastScan = 0
        scan(c, true)
    }

    // ─────────────────────────────────────────────
    //  SAVE / LOAD
    // ─────────────────────────────────────────────

    private fun save(c: Context) {
        try {
            val arr = JSONArray()
            apps.forEach { a ->
                arr.put(JSONObject().apply {
                    put("n", a.name)
                    put("p", a.pkg)
                    if (a.alias.isNotBlank()) put("a", a.alias)
                    if (a.hidden) put("h", true)
                })
            }
            file(c).writeText(arr.toString())
        } catch (e: Exception) {}
    }

    private fun load(c: Context): List<App> {
        return try {
            val arr = JSONArray(file(c).readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                App(o.optString("n"), o.optString("p"),
                    o.optString("a", ""), o.optBoolean("h", false))
            }
        } catch (e: Exception) { emptyList() }
    }
}
