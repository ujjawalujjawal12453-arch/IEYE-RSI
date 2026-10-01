package com.ravanx.ieyeris

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject

/**
 * 🤖 AGENT — IEYE RIS ka asli dimaag (Friday wala tareeka)
 *
 * ════════════════════════════════════════════════════════
 *  PEHLE KYA GALAT THA
 * ════════════════════════════════════════════════════════
 *
 * Purana Brain aise chalta tha:
 *
 *     User bola  →  AI se ek baar poochho  →  kaam karo  →  KHATAM
 *
 * Isliye "WhatsApp kholo, School Friends group me hi bhejo"
 * jaisa kaam KABHI pura nahi hota tha. Kyunki:
 *
 *   • AI ko pata hi nahi tha ki group kis jagah hai
 *   • WhatsApp khulne me 2 second lagte hain — tab tak AI ka
 *     kaam khatam ho chuka hota tha
 *   • Group pe tap karne ke baad screen BADAL jaati hai —
 *     par AI ko naya screen dikhta hi nahi tha
 *
 * ════════════════════════════════════════════════════════
 *  AB KYA HOTA HAI
 * ════════════════════════════════════════════════════════
 *
 *     User bola
 *        ↓
 *     ┌─→ SCREEN DEKHO (snapshot — har button number ke saath)
 *     │      ↓
 *     │   AI SE POOCHHO "ab kya karun?"
 *     │      ↓
 *     │   EK KADAM UTHAO (tap / type / open / swipe)
 *     │      ↓
 *     └───  ruko 900ms, phir se dekho
 *            ↓
 *          jab AI bole "done" → khatam
 *
 * Yahi Friday/IEYE RIS ka tareeka hai. Har kadam ke baad aankh
 * kholo, phir socho.
 *
 * ⚠️ Zaroori: Eyes (Accessibility) ON hona chahiye. Bina uske
 *    IEYE RIS andha hai — sirf app khol sakta hai, andar kuch
 *    nahi kar sakta.
 */
object Agent {

    /** Ek kadam jo AI ne bola */
    data class Step(
        val tool: String,
        val idx: Int = -1,
        val text: String = "",
        val say: String = ""
    )

    /** Kaam kaisa chal raha hai — UI ko batane ke liye */
    interface Watch {
        fun onStep(n: Int, total: Int, what: String)
        fun onDone(msg: String)
        /**
         * v4.1 — live haalat (spec: Agent Activity panel).
         * Default khali hai taaki purana code na toote.
         */
        fun onState(s: String) {}
    }

    /** 🛑 Abhi koi kaam chal raha hai? */
    @Volatile
    var busy = false
        private set

    /** 🛑 Emergency STOP — Halt yahan se bulata hai */
    fun cancel() {
        if (busy) Brain.log("🛑 AGENT roka gaya")
        busy = false
    }

    private val h = Handler(Looper.getMainLooper())

    const val MAX_STEPS = 12

    private val SYS = """Tum IEYE RIS ho — Android phone chalane wale agent.
User Hinglish me kaam bolta hai. Tum SCREEN DEKH KAR ek-ek kadam
uthate ho, bilkul jaise insaan phone chalata hai.

Har baar tumhe screen ka naksha milega. Har dabane-layak cheez
ka number hota hai:
  [3] School Friends        <- tap kar sakte ho
  [7] ✏️ Message likhne ka box   <- likh sakte ho
  (bina number wala) sirf padhne ke liye hai

SIRF ek JSON do, aur kuch nahi:
{"tool":"...","idx":0,"text":"...","say":"..."}

tools:
  open      text=app ka naam        (app kholo)
  tap       idx=number              (button/cheez dabao)
  type      idx=number, text=likhna hai
  send      (message bhejne ka button dhoondh kar dabao)
  enter     (keyboard ka enter)
  back      home      recents
  swipe     text=up|down

  ⚠️ BACK bahut kam use karo — sirf tab jab galat screen pe
     pahunch gaye ho. Bina wajah back mat dabao, warna user
     ka kaam beech me tut jata hai.
  wait      (screen load hone do)
  search    text=kya dhoondhna hai  (screen pe search box me)
  torch_on  torch_off  wifi  bluetooth  vol_max  screenshot
  answer    say=jawab               (sirf batana hai, kaam nahi)
  done      say=kaam pura hua       (SAB HO GAYA)

NIYAM:
1. Ek baar me SIRF EK kadam. Jaldbaazi mat karo.
2. Jo cheez screen pe DIKH RAHI hai sirf uska number do.
   Number galat diya to kaam bigad jayega.
3. App abhi khuli hi hai aur khali dikh rahi hai -> "wait" do.
4. Group/contact dhoondhna hai par dikh nahi raha -> pehle
   search wala button [number] tap karo, phir "search" tool.
5. Message likhne ke baad "send" karna MAT bhoolo.
5b. text me SIRF wo likho jo bhejna hai — hukum ke shabd nahi.
   "school group me hi bhej do"  -> text="hi"   (SAHI)
   "school group me hi bhej do"  -> text="hi bhej do"  (GALAT)
   "papa ko bolo late aaunga"    -> text="late aaunga"
6. Kaam pura ho gaya to "done" do — warna loop chalta rahega.
7. 3 baar koshish ke baad bhi na ho to "done" me sach batao.
8. say = chhota Hinglish (3-6 shabd)."""

    /**
     * Kaam shuru karo.
     *
     * @param goal user ne kya bola
     * @param w    har kadam ki khabar
     */
    fun run(ctx: Activity, goal: String, w: Watch) {
        if (!Eyes.on()) {
            w.onDone("Sir, iske liye meri aankhein chahiye.\n\n" +
                "👁 button dabaiye — Accessibility ON kar dijiye. " +
                "Uske bina main sirf app khol sakta hoon, andar " +
                "kuch nahi kar sakta.")
            return
        }
        val keys = Keys(ctx)
        if (keys.groq().isBlank()) {
            w.onDone("Sir, AI key nahi hai. Settings me daaliye.")
            return
        }

        backCount = 0
        busy = true
        Halt.reset()          // naya kaam — purana stop saaf
        val history = mutableListOf<String>()
        Brain.log("🤖 AGENT shuru: " + goal.take(60))
        w.onState("UNDERSTANDING")
        loop(ctx, goal, history, 1, w)
    }

    private fun loop(ctx: Activity, goal: String,
                     history: MutableList<String>,
                     n: Int, w: Watch) {

        // 🛑 v4.1 — HAR kadam se pehle STOP check.
        //    Yahi asli emergency stop hai: agent chahe kitna bhi
        //    aage nikal chuka ho, agla kadam nahi uthega.
        if (Halt.stopped() || !busy) {
            busy = false
            Brain.log("🛑 AGENT ruk gaya (kadam $n pe)")
            w.onState("STOPPED")
            w.onDone("Ruk gaya sir.")
            return
        }

        if (n > MAX_STEPS) {
            busy = false
            Brain.log("🤖 AGENT: $MAX_STEPS kadam ho gaye, ruk raha hoon")
            w.onDone("Sir, $MAX_STEPS kadam ho gaye par kaam pura " +
                "nahi hua. Thoda alag tarah se bataiye?")
            return
        }

        Thread {
            val eyes = Eyes.live
            val screen = try { eyes?.snapshot() ?: "" }
                catch (e: Exception) { "" }

            val prompt = StringBuilder()
            prompt.append("KAAM: ").append(goal).append("\n\n")
            if (history.isNotEmpty()) {
                prompt.append("AB TAK JO KIYA:\n")
                history.takeLast(6).forEach {
                    prompt.append("  ").append(it).append("\n") }
                prompt.append("\n")
            }
            prompt.append("SCREEN ABHI:\n").append(screen)
            prompt.append("\n\nAb kaunsa EK kadam uthaun?")

            val step = ask(ctx, prompt.toString())

            h.post {
                // 🛑 AI se jawab aate-aate user ne STOP daba diya?
                if (Halt.stopped() || !busy) {
                    busy = false
                    w.onState("STOPPED")
                    w.onDone("Ruk gaya sir.")
                    return@post
                }
                if (step == null) {
                    Brain.log("🤖 AGENT: AI ne jawab nahi diya")
                    val e = Brain.lastError
                    w.onDone(when {
                        e.contains("limit", true) ->
                            "Sir, AI ki ek-minute wali limit khatam " +
                            "ho gayi. Ek minute ruk kar dobara " +
                            "boliye — phir chal jayega."
                        e.isNotBlank() -> "Sir, AI se baat nahi hui — $e"
                        else -> "Sir, AI se jawab nahi aaya. Dobara boliye?"
                    })
                    busy = false
                    return@post
                }

                if (step.tool == "done" || step.tool == "answer") {
                    busy = false
                    Brain.log("🤖 AGENT: ✅ " + step.say)
                    w.onState("COMPLETED")
                    w.onDone(step.say.ifBlank { "Ho gaya sir." })
                    return@post
                }

                val what = describe(step)
                w.onState(if (n == 1) "PLANNING" else "ACTING")
                w.onStep(n, MAX_STEPS, what)
                Brain.log("🤖 [$n] $what")

                val ok = act(ctx, step)
                history.add("$n. $what → " +
                    (if (ok) "ho gaya" else "NAHI hua"))

                // ⚠️ Ruko — screen badalne me waqt lagta hai.
                //    App khulne me sabse zyada.
                val gap = when (step.tool) {
                    "open" -> 2300L
                    "tap", "send", "enter" -> 1100L
                    "wait" -> 1600L
                    "type" -> 600L
                    else -> 900L
                }
                h.postDelayed({
                    loop(ctx, goal, history, n + 1, w)
                }, gap)
            }
        }.start()
    }

    private fun describe(s: Step): String = when (s.tool) {
        "open" -> "${s.text} khol raha hoon"
        "tap" -> "[${s.idx}] daba raha hoon" +
            (if (s.say.isNotBlank()) " — ${s.say}" else "")
        "type" -> "Likh raha hoon: ${s.text.take(40)}"
        "send" -> "Bhej raha hoon"
        "enter" -> "Enter"
        "back" -> "Peeche"
        "home" -> "Home"
        "swipe" -> "Scroll ${s.text}"
        "wait" -> "Ruk raha hoon…"
        "search" -> "Dhoondh raha hoon: ${s.text}"
        else -> s.tool
    }

    /** Ek kadam sach me uthao */
    private fun act(ctx: Activity, s: Step): Boolean {
        val e = Eyes.live ?: return false
        return try {
            when (s.tool) {
                "open" -> {
                    val pkg = Brain.APPS[s.text.lowercase().trim()]
                        ?: s.text
                    Actions.run(ctx, Brain.Cmd("open_app", pkg))
                    true
                }
                "tap" -> {
                    if (s.idx > 0) e.tapIdx(s.idx)
                    else if (s.text.isNotBlank()) e.tapText(s.text)
                    else false
                }
                "type" -> {
                    if (s.idx > 0) e.typeIdx(s.idx, s.text)
                    else e.type(s.text)
                }
                "send" -> e.tapText("Send") || e.tapText("भेजें") ||
                          e.tapText("send") || e.pressEnter()
                "enter" -> e.pressEnter()
                "back" -> {
                    // ⚠️ Lagatar back = app se bahar nikal jana.
                    //    User ko lagta hai "auto back ho gaya".
                    //    Isliye ek baar ke baad rok.
                    if (backCount >= 2) {
                        Brain.log("🤖 back rok diya (2 baar ho chuka)")
                        false
                    } else {
                        backCount++
                        e.back()
                    }
                }
                "home" -> e.home()
                "recents" -> e.recents()
                "swipe" -> e.scroll(s.text.lowercase() != "up")
                "wait" -> true
                "search" -> e.type(s.text)
                "screenshot" -> e.screenshot()
                else -> {
                    // baaki sab purane Actions se
                    Actions.run(ctx, Brain.Cmd(s.tool, s.text))
                    true
                }
            }
        } catch (ex: Exception) {
            Brain.log("🤖 kadam fail: " + (ex.message ?: ""))
            false
        }
    }

    /** AI se agla kadam poochho */
    private fun ask(ctx: Activity, prompt: String): Step? {
        val gk = Keys(ctx).groq()
        val url = "https://api.groq.com/openai/v1/chat/completions"

        // ⚠️ Groq free tier = 8000 token/minute PER MODEL.
        //    Agent har kadam pe screen bhejta hai, isliye ek hi
        //    model pe rahe to 4-5 kadam me limit khatam ho jaati
        //    hai aur kaam beech me ruk jaata hai.
        //
        //    Maine live test me ye pakda:
        //      "Rate limit reached ... TPM: Limit 8000, Used 7675"
        //
        //    Ilaaj: har kadam ALAG model pe. 4 model = 32000
        //    token/minute. Aur har model ka apna hisaab.
        val order = listOf(
            "qwen/qwen3.8-27b" to "none",
            "openai/gpt-oss-20b" to "low",
            "qwen/qwen3.6-27b" to "none",
            "openai/gpt-oss-120b" to "low"
        )
        // har call pe agla model — bojh baant do
        val start = (turn++ % order.size)
        for (k in order.indices) {
            val (m, ef) = order[(start + k) % order.size]
            val body = JSONObject().apply {
                put("model", m)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system"); put("content", SYS) })
                    put(JSONObject().apply {
                        put("role", "user"); put("content", prompt) })
                })
                put("max_tokens", 220)
                put("temperature", 0.0)
                put("reasoning_effort", ef)
            }
            val out = Brain.callRaw(url, gk, body.toString())
            if (out.isNotBlank()) {
                parse(out)?.let { return it }
            }
        }
        return null
    }

    private var turn = 0

    /** Ek kaam me kitni baar back daba — 2 se zyada nahi */
    private var backCount = 0

    private fun parse(raw: String): Step? {
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try {
            val j = JSONObject(raw.substring(a, b + 1))
            var tool = j.optString("tool", "").trim().lowercase()
            if (tool.isBlank())
                tool = j.optString("action", "").trim().lowercase()
            if (tool.isBlank()) return null

            // AI kabhi apne naam bana deta hai
            val fix = mapOf(
                "click" to "tap", "press" to "tap", "dabao" to "tap",
                "write" to "type", "input" to "type",
                "open_app" to "open", "launch" to "open",
                "scroll" to "swipe", "finish" to "done",
                "complete" to "done", "reply" to "answer",
                "say" to "answer", "stop" to "done"
            )
            tool = fix[tool] ?: tool

            Step(
                tool,
                j.optInt("idx", -1),
                j.optString("text", "").trim(),
                j.optString("say", "").trim()
            )
        } catch (e: Exception) { null }
    }

    /**
     * Kya ye kaam agent se karwana chahiye?
     *
     * Sirf tab jab screen ke andar kuch karna ho — warna
     * seedha purana rasta tez hai.
     */
    fun needed(raw: String): Boolean {
        val t = raw.lowercase()

        // Ye shabd matlab screen ke andar ghusna padega
        val deep = listOf(
            "group", "grup", "chat", "message bhej", "msg bhej",
            "reply", "jawab de", "bhej do", "bhej dena", "likh do",
            "likh kar", "search kar", "dhoondh kar", "khol kar",
            "wala", "wale", "waali", "us pe", "uspe", "usme",
            "andar", "select", "chuno", "choose", "pe jao",
            "par jao", "click", "tap kar", "dabao", "form",
            "fill", "bharo", "login", "sign", "settings me",
            "option", "menu", "download kar", "install kar",
            "play kar", "chala do", "share kar", "forward",
            "delete kar", "hatao", "add kar", "banao"
        )
        if (deep.any { t.contains(it) }) return true

        // "WhatsApp pe X ko Y bhejo" — 3+ hisse
        val multi = Regex("\\b(pe|par|me|mein|ko|se)\\b")
            .findAll(t).count()
        val verbs = Regex(
            "\\b(kholo|bhejo|likho|karo|dabao|dhundo|search|" +
            "chalao|dekho|padho|bharo|chuno)\\b").findAll(t).count()
        return multi >= 2 && verbs >= 1
    }
}
