package com.ravanx.ieyeris

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 🎙️ VOICE ACTIVITY — bubble ka asli dimaag
 *
 * ⚠️ SABSE BADA SABAK:
 *    Android 11+ me SpeechRecognizer ek background Service se
 *    THEEK SE NAHI CHALTA. Mic khulta hai par kuch sunai nahi
 *    deta — bilkul wahi jo aapke saath ho raha tha.
 *
 *    Isliye ab bubble tap karne par YE Activity khulti hai —
 *    poori transparent, sirf ek chhota card dikhta hai. Activity
 *    ke andar mic 100% kaam karta hai.
 *
 *    User ko lagta hai bubble hi sun raha hai. Kaam khatam,
 *    ye khud band ho jaati hai.
 */
class VoiceActivity : AppCompatActivity() {

    private lateinit var voice: Voice
    private var rec: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())

    private var ring: TextView? = null
    private var icon: TextView? = null
    private var line: TextView? = null
    private var wave: TextView? = null
    private var card: LinearLayout? = null
    private var core: CoreView? = null

    private var spin: ValueAnimator? = null
    private var partial = ""
    private var busy = false
    private var keepOn = true

    /*
     * ⚠️⚠️ v4.0 — "NA BOLO TO KHUD BAND HO JAO" ⚠️⚠️
     *
     * User ki shikayat: "jab Ham bole tab hamari baat sune,
     * jab Ham Na bole to vah automatic shut down ho jaega"
     *
     * Pehle onError() me NO_MATCH aane par seedha again(180)
     * tha — yaani chup rehne par bhi HAMESHA dobara sunta
     * rehta tha. Bubble kabhi apne aap band hota hi nahi tha,
     * mic khula rehta tha aur battery pita rehta tha.
     *
     * Ab ginti rakhte hain: lagataar 3 baar kuch na suna to
     * chup-chaap band. Beech me ek baar bhi bol diya to ginti
     * phir se zero.
     */
    private var chupCount = 0
    private var lastHeard = System.currentTimeMillis()
    private var silence: Runnable? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen pe bhi khule
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(0x99000000.toInt()))

        setContentView(ui())

        voice = Voice(this)

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish(); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            line?.text = "Google voice service chahiye sir"
            h.postDelayed({ finish() }, 2200); return
        }

        // Bubble se command bhi aa sakti hai (bina bole)
        val a = intent?.getStringExtra("cmd_action")
        if (!a.isNullOrBlank()) {
            busy = true
            think(true)
            h.postDelayed({
                doIt(Brain.Cmd(a,
                    intent.getStringExtra("cmd_arg") ?: "",
                    intent.getStringExtra("cmd_say") ?: ""))
            }, 200)
        } else {
            h.postDelayed({ listen() }, 120)
        }
    }

    // ═══════════════ UI ═══════════════

    private fun ui(): View {
        val root = FrameLayout(this)
        root.setOnClickListener { bye() }   // bahar tap = band

        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(24), dp(26), dp(24))
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(Color.parseColor("#F00B1020"))
                setStroke(dp(1), Color.parseColor("#3394A3B8"))
            }
            isClickable = true              // andar tap = kuch nahi
            alpha = 0f
            scaleX = 0.82f; scaleY = 0.82f
        }

        // ── 👁 IRIS CORE ──
        //
        // v2.1: pehle yahan sirf ek gulabi circle aur 🎤 emoji
        // tha. User ne kaha "uske andar ek hoga" — yaani bubble
        // ke andar bhi wahi asli iris dikhna chahiye jo main
        // screen pe hai. Ab bilkul wahi CoreView lagta hai.
        val orb = FrameLayout(this)
        core = CoreView(this)
        orb.addView(core, FrameLayout.LayoutParams(dp(104), dp(104)))
        card!!.addView(orb, LinearLayout.LayoutParams(dp(104), dp(104)))

        // ye dono ab dikhte nahi — purana code inhe chhoo sakta
        // hai, isliye rakhe hain (crash se bachne ke liye)
        ring = TextView(this)
        icon = TextView(this)

        wave = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#00E6FF"))
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            text = "▁▁▁▁▁"
        }
        card!!.addView(wave, lp(dp(12)))

        line = TextView(this).apply {
            text = "Boliye…"
            textSize = 15f
            setTextColor(Color.parseColor("#F8FAFC"))
            gravity = Gravity.CENTER
            maxLines = 4
        }
        card!!.addView(line, lp(dp(10)))

        /*
         * 🛑 v4.1 — bubble me bhi EMERGENCY STOP
         *
         * Spec: "must always be accessible". Bubble kisi bhi app
         * ke upar khulta hai — agar agent wahin galat kaam karne
         * lage to user ko yahin rokna hoga. Main app tak jaane
         * ka waqt nahi hota.
         */
        val stopB = TextView(this).apply {
            text = "🛑  STOP"
            textSize = 12f
            setTextColor(Color.parseColor("#FFE4E4"))
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.parseColor("#26EF4444"))
                setStroke(dp(1), Color.parseColor("#66EF4444"))
            }
            setPadding(dp(18), dp(9), dp(18), dp(9))
            setOnClickListener {
                Halt.stopAll(this@VoiceActivity)
                keepOn = false
                cancelSil()
                try { rec?.cancel() } catch (e: Exception) {}
                core?.state = CoreView.S.IDLE
                line?.text = "🛑 Ruk gaya"
                h.postDelayed({ bye() }, 700)
            }
        }
        card!!.addView(stopB, lp(dp(14)))

        root.addView(card, FrameLayout.LayoutParams(
            dp(290),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER))

        card!!.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(260).start()
        return root
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = top
    }

    private fun rotate(color: String, ms: Long) {
        spin?.cancel()
        ring?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(3), Color.parseColor(color))
            setColor(Color.TRANSPARENT)
        }
        spin = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = ms
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { ring?.rotation = it.animatedValue as Float }
            start()
        }
    }

    private fun think(on: Boolean) {
        if (on) {
            core?.state = CoreView.S.THINKING
            line?.text = "Soch raha hoon…"
            rotate("#F59E0B", 620)
        }
    }

    // ═══════════════ SUNNA ═══════════════

    private fun listen() {
        if (!keepOn || busy) return
        voice.stop()
        partial = ""
        core?.state = CoreView.S.LISTENING
        line?.text = "Boliye…"
        rotate("#EF4444", 1100)

        try { rec?.destroy() } catch (e: Exception) {}
        rec = SpeechRecognizer.createSpeechRecognizer(this)
        rec?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() { cancelSil() }

            override fun onRmsChanged(v: Float) {
                if (v > 1f) {
                    // v2.1 — lahar ab iris ke andar dikhti hai
                    core?.level = (v / 10f).coerceIn(0f, 1f)
                }
            }

            override fun onBufferReceived(p: ByteArray?) {}
            override fun onEndOfSpeech() {
                wave?.visibility = View.INVISIBLE
            }

            override fun onError(e: Int) {
                wave?.visibility = View.INVISIBLE
                val chup = e == SpeechRecognizer.ERROR_NO_MATCH ||
                           e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                if (partial.trim().length > 1) {
                    chupCount = 0
                    fire(partial.trim()); return
                }

                if (chup) {
                    chupCount++
                    // 3 baar chup = baat khatam, band ho jao
                    if (chupCount >= 3) {
                        Brain.log("🫧 3 baar chup — khud band")
                        bye(); return
                    }
                    // 25 second se kuch nahi bole to bhi band
                    if (System.currentTimeMillis() - lastHeard > 25_000) {
                        Brain.log("🫧 25s khamoshi — khud band")
                        bye(); return
                    }
                    line?.text = "Sun raha hoon…"
                    again(200); return
                }

                if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    again(450); return
                }
                // ⚠️ Baaki error (mic busy, network) baar-baar aa
                //    sakte hain. 4 se zyada hue to band — warna
                //    hamesha ka loop ban jata hai.
                chupCount++
                if (chupCount >= 4) { bye(); return }
                again(600)
            }

            override fun onResults(r: Bundle?) {
                wave?.visibility = View.INVISIBLE
                val t = r?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: partial.trim()
                if (t.isNotBlank()) fire(t) else again(180)
            }

            override fun onPartialResults(r: Bundle?) {
                val t = r?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: return
                if (t.isBlank() || t == partial) return
                partial = t
                chupCount = 0
                lastHeard = System.currentTimeMillis()
                line?.text = t
                cancelSil()
                // v3.0 — bubble me bhi wahi sabr (Settings se)
                var wait = Keys(this@VoiceActivity).waitMs()
                if (t.length > 60) wait += 700
                if (t.length > 120) wait += 700
                if (Regex("(aur|phir|uske baad|fir|then|and|" +
                          "ke baad|iske alawa)$")
                        .containsMatchIn(t.lowercase().trimEnd()))
                    wait += 1800
                silence = Runnable {
                    try { rec?.stopListening() } catch (e: Exception) {}
                }
                h.postDelayed(silence!!, wait)
            }

            override fun onEvent(t: Int, p: Bundle?) {}
        })

        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            val w = Keys(this@VoiceActivity).waitMs()
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, w)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
        }
        try { rec?.startListening(i) } catch (e: Exception) { again(600) }
    }

    private fun cancelSil() {
        silence?.let { h.removeCallbacks(it) }
        silence = null
    }

    private fun again(ms: Long) {
        cancelSil()
        h.postDelayed({ if (keepOn && !busy) listen() }, ms)
    }

    // ═══════════════ KAAM ═══════════════

    private fun fire(text: String) {
        if (busy) return
        busy = true
        Halt.reset()          // naya kaam — purana stop saaf
        cancelSil()
        try { rec?.cancel() } catch (e: Exception) {}
        line?.text = text
        think(true)

        // 🧠 v2.1 — bubble bhi ab khud fact yaad rakhta hai.
        //    Pehle sirf MainActivity seekhta tha, bubble se
        //    boli hui baat kabhi yaad nahi rehti thi.
        try { Memory(this).autoLearn(text) } catch (e: Exception) {}

        // ═══ AGENT — poore phone ka kaam ═══
        //
        // ⚠️ v2.1 FIX: pehle shart thi `Agent.needed() && Eyes.on()`.
        //    Eyes (Accessibility) OFF ho to Agent CHUP-CHAAP skip
        //    ho jata tha aur bubble sirf ek-line ka kaam karta tha.
        //
        //    User ne kaha: "vah pura phone pahle jaega FIR sab
        //    kuchh Karega" — yaani bubble ko poora phone chalana
        //    chahiye. Ab agar Eyes OFF hai to CHUP nahi rehte,
        //    saaf batate hain ki kya on karna hai.
        if (Agent.needed(text)) {
            if (!Eyes.on()) {
                say("Is kaam ke liye mujhe screen dekhni padegi " +
                    "sir. Settings me \"IEYE RIS\" ki " +
                    "Accessibility on kar dijiye — phir main " +
                    "poore phone me kahin bhi kaam kar dunga.")
                try {
                    Eyes.openSettings(this)
                } catch (e: Exception) {}
                return
            }
            line?.text = "🤖 Karta hoon…"
            Agent.run(this, text, object : Agent.Watch {
                override fun onState(st: String) {
                    runOnUiThread { line?.text = st }
                }
                override fun onStep(n: Int, total: Int, what: String) {
                    runOnUiThread { line?.text = "🤖 $n · $what" }
                }
                override fun onDone(msg: String) {
                    runOnUiThread { say(msg) }
                }
            })
            return
        }

        val loc = Brain.local(text)
        if (loc != null) { runAll(listOf(loc)); return }

        Thread {
            val cs = try { Brain.aiMulti(this, text) }
                catch (e: Exception) { emptyList<Brain.Cmd>() }
            runOnUiThread {
                if (cs.isNotEmpty()) runAll(cs)
                else {
                    val e = Brain.lastError
                    say(if (e.isBlank()) "Samajh nahi aaya sir"
                        else "AI fail — $e")
                }
            }
        }.start()
    }

    /** Ek se zyada kaam — ek ke baad ek, 350ms ke gap se */
    private fun runAll(cmds: List<Brain.Cmd>) {
        if (cmds.isEmpty()) { say("Samajh nahi aaya sir"); return }

        /*
         * ⚠️⚠️ v4.0 — SHUTDOWN / SLEEP YAHAN SE HANDLE ⚠️⚠️
         *
         * User ki shikayat: "Maine use bola iris shut down ho
         * jao to ho jana chahie"
         *
         * Pehle bubble me shutdown bolne par Actions.run()
         * chalta tha jo services band kar deta tha — par ye
         * VoiceActivity khuli hi reh jaati thi. User ko lagta
         * tha hukum maana hi nahi.
         *
         * Ab: shutdown/sleep sunte hi jawab bol kar card BAND.
         */
        val quit = cmds.firstOrNull {
            it.action == "shutdown" || it.action == "sleep"
        }
        if (quit != null) {
            val msg = try { Actions.run(this, quit) }
                      catch (e: Exception) { "Band kar raha hoon sir." }
            core?.state = CoreView.S.IDLE
            line?.text = msg
            keepOn = false
            cancelSil()
            try { rec?.cancel() } catch (e: Exception) {}
            voice.say(msg) {
                runOnUiThread { h.postDelayed({ bye() }, 400) }
            }
            // agar awaaz na chali to bhi 4 sec me band
            h.postDelayed({ if (!isFinishing) bye() }, 4500)
            return
        }

        val out = mutableListOf<String>()

        fun step(i: Int) {
            if (i >= cmds.size) {
                val all = out.filter { it.isNotBlank() }
                say(if (all.isEmpty()) "Ho gaya sir"
                    else all.joinToString(". "))
                return
            }
            // 🛑 STOP daba diya? aage mat badho
            if (Halt.stopped()) { bye(); return }

            val c = cmds[i]
            if (cmds.size > 1) line?.text =
                "${i + 1}/${cmds.size}  ${Actions.label(c.action)}"

            // ⚠️ v3.1 — wahi bug jo MainActivity me tha.
            //    chat/translate NETWORK call karte hain. UI thread
            //    pe Android turant exception phenkta hai, aur
            //    jawab kabhi nahi aata. Background pe bhejo.
            if (c.action == "chat" || c.action == "translate") {
                core?.state = CoreView.S.THINKING
                Thread {
                    val r = try { Actions.run(this, c) }
                        catch (e: Exception) { "" }
                    runOnUiThread { out.add(r); step(i + 1) }
                }.start()
                return
            }

            val r = try { Actions.run(this, c) }
                catch (e: Exception) { "" }
            out.add(r)
            h.postDelayed({ step(i + 1) },
                if (cmds.size == 1) 0L else 350L)
        }
        step(0)
    }

    private fun doIt(c: Brain.Cmd) = runAll(listOf(c))

    private fun say(msg: String) {
        core?.state = CoreView.S.SPEAKING
        line?.text = msg
        rotate("#22C55E", 1600)
        Memory(this).let {
            val l = it.load()
            l.add(Memory.Msg(false, msg))
            it.save(l)
        }
        voice.say(msg) {
            runOnUiThread {
                busy = false
                // Chhota jawab = shayad aur bologe. Sun lete hain.
                if (keepOn) { again(150) }
                // 12 second koi baat nahi = band
                h.removeCallbacksAndMessages("bye")
                h.postDelayed({ if (!busy) bye() }, 12_000)
            }
        }
    }

    private fun bye() {
        keepOn = false
        cancelSil()
        spin?.cancel()
        try { rec?.cancel(); rec?.destroy() } catch (e: Exception) {}
        rec = null
        card?.animate()?.alpha(0f)?.scaleX(0.85f)?.scaleY(0.85f)
            ?.setDuration(180)?.withEndAction { finish() }?.start()
            ?: finish()
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) bye()
    }

    override fun onDestroy() {
        keepOn = false
        cancelSil()
        spin?.cancel()
        try { rec?.destroy() } catch (e: Exception) {}
        voice.shutdown()
        super.onDestroy()
    }
}
