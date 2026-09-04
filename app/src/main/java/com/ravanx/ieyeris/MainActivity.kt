package com.ravanx.ieyeris

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ravanx.ieyeris.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        /**
         * IEYE RIS abhi screen pe saamne hai?
         *
         * ⚠️ WakeService isse dekh kar decide karta hai ki
         *    Activity dobara kholni hai ya nahi. Bina iske
         *    app baar-baar apne aap saamne aa jaata tha —
         *    user ko lagta tha "auto back ho gaya".
         */
        @Volatile
        var isForeground = false

        /**
         * v4.1 — Halt (emergency stop) ko chalti hui Activity
         * chahiye taaki mic band kar sake.
         *
         * ⚠️ WeakReference use ki hai, seedha reference NAHI.
         *    Warna Activity band hone ke baad bhi memory me
         *    padi rehti (memory leak) — Android me ye sabse
         *    aam galti hai.
         */
        @Volatile
        var liveRef: java.lang.ref.WeakReference<MainActivity>? = null
    }

    private lateinit var b: ActivityMainBinding
    private lateinit var voice: Voice
    private lateinit var keys: Keys
    private lateinit var mem: Memory
    private val log = mutableListOf<Memory.Msg>()
    private var rec: SpeechRecognizer? = null
    private var listening = false

    private val PERMS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🛡️ v1.6 — CRASH KA SABOOT
        //
        //    Pehle app crash hoti to chup-chaap band ho jaati thi.
        //    User ko sirf itna dikhta tha "app khula aur wapas chala
        //    gaya" — wajah kabhi pata nahi chalti thi.
        //    Ab crash ki poori tafseel file me likh dete hain, jo
        //    Settings me "🐞 Aakhri crash" me dikhti hai.
        installCrashCatcher()
        liveRef = java.lang.ref.WeakReference(this)

        /*
         * 🔑 v4.3 — LICENSE GUARD
         *
         * ⚠️ LicenseActivity launcher hai, par koi shortcut,
         *    bubble ya notification se seedha yahan aa sakta
         *    hai. Isliye yahan bhi check.
         *
         * ⚠️ Aur har baar app khulne pe BACKGROUND me server se
         *    dobara poochte hain — admin ne key band ki ho to
         *    agli baar app nahi khulegi. Yahi user ne maanga
         *    tha: "agar server bole nahi hai to nahi".
         */
        if (!License.activated(this)) {
            startActivity(Intent(this, LicenseActivity::class.java))
            finish()
            return
        }
        License.checkAsync(this) { r ->
            if (!r.ok && !r.offline) {
                runOnUiThread {
                    try {
                        startActivity(Intent(this,
                            LicenseActivity::class.java))
                        finish()
                    } catch (e: Exception) {}
                }
            }
        }
        // 🔊 v4.2 — awaazein pehle se load (turant bajni chahiye)
        Sfx.init(this)

        // 🚀 Pehli baar? -> Setup wizard
        //
        // ⚠️ Pehle app seedha chat kholta tha aur permission ka
        //    popup thok deta tha. User ko na pata chalta tha ye
        //    app kya hai, na kyun permission maang raha hai.
        // ⚠️ v1.6 — DOOSRA TAALA
        //
        //    Agar kisi wajah se setup_done padha na ja sake (store
        //    kharab, Keystore fail), to app har baar Setup pe bhej
        //    kar khud ko finish() kar deti thi. User ko lagta tha
        //    "app khulta hai phir apne aap band ho jata hai".
        //
        //    Ab ginti rakhte hain: agar Setup 2 baar se zyada bhej
        //    chuke hain, to maan lo setup ho chuka hai aur seedha
        //    chat kholo. App band hone se to behtar hai.
        if (!SetupActivity.done(this)) {
            val k = Keys(this)
            val tries = k.get("setup_bounce", "0").toIntOrNull() ?: 0
            if (tries < 2) {
                k.set("setup_bounce", (tries + 1).toString())
                startActivity(Intent(this, SetupActivity::class.java))
                finish()
                return
            }
            // 3rd baar — aage badho, warna app kabhi khulegi hi nahi
            Brain.log("⚠️ setup flag padha nahi ja raha — " +
                      "aage badh raha hoon")
            k.setFlag("setup_done", true)
        }
        Keys(this).set("setup_bounce", "0")

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        splash()

        keys = Keys(this)
        voice = Voice(this)
        mem = Memory(this)

        askPerms()

        // ═══ 👁 IRIS CORE — v2.0 ka mukhya button ═══
        //
        //   User ne kaha: "ek button rahega, usko dabate hi
        //   sunna chalu kar dega, beech me iris nikla hoga".
        //
        //   Dono core (bada aur chhota) ek hi kaam karte hain.
        val coreTap = View.OnClickListener {
            if (keepOn) {
                stopListen()
                bot("Theek hai sir, mic band.")
            } else {
                // chup mode me tha to iris dabane pe kaan
                // wapas khul jate hain — warna user phans jata
                keys.setFlag("mic_off", false)
                startListen()
            }
        }
        b.core.setOnClickListener(coreTap)

        /*
         * 🛑 v4.1 — EMERGENCY STOP
         *
         * Spec: "This button must always be accessible."
         *
         * Ek dabane pe: agent ruka, awaaz bandh, mic bandh,
         * katar saaf. Kuch bhi chal raha ho — sab ruk jayega.
         */
        b.stopBtn.setOnClickListener {
            Halt.stopAll(this)
            Sfx.play(this, Sfx.STOP)
            try { stopListen() } catch (e: Exception) {}
            try { voice.stop() } catch (e: Exception) {}
            busy = false
            core(CoreView.S.IDLE)
            coreLevel(0f)
            act("STOPPED")
            b.status.text = "🛑 Sab rok diya sir."
            b.stopBtn.text = "🛑  RUK GAYA"
            b.stopBtn.postDelayed({
                b.stopBtn.text = "🛑  IEYE RIS STOP"
                b.status.text = "DABAO AUR BOLO"
                act("")
            }, 2200)
        }

        // lamba dabao -> chup mode on/off (jaldi ka rasta)
        b.core.setOnLongClickListener {
            val now = !Voice.isMuted(this)
            Voice.setMuted(this, now)
            if (now) { try { voice.stop() } catch (e: Exception) {} }
            bot(if (now)
                    "Chup mode ON. Ab sirf likh kar jawab dunga."
                else "Chup mode OFF. Ab bol sakta hoon sir.")
            refreshMuteTag()
            true
        }
        b.settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.wakeSwitch.isChecked = keys.wake()
        b.wakeSwitch.setOnCheckedChangeListener { _, on ->
            keys.setFlag("wake", on)
            if (on) {
                WakeService.start(this)
                bot("Ab main hamesha sunta rahunga sir. " +
                    "\"IEYE RIS\" bol kar bulaiye.")
            } else {
                WakeService.stop(this)
                bot("Theek hai, ab sirf button dabane pe sunuga.")
            }
        }

        // ⚠️⚠️ v2.1 — "NAYA UI DIKHTA HI NAHI" KA ASLI BUG ⚠️⚠️
        //
        //   User ne kaha "UI wahi purana hai jo pehle dikhaya tha".
        //   Wo bilkul sahi the. Wajah ye thi:
        //
        //   Purani chat hoti thi -> yahan seedha bubble() chalta tha.
        //   bubble() sirf row banata hai — wo toChat() NAHI bulata.
        //   Par purane bot() me toChat() tha... to bhi core chhup
        //   jata tha, kyunki chat wali ScrollView dikh jaati thi
        //   aur coreWrap peeche reh jata tha.
        //
        //   Natija: jis user ke paas purani chat thi (yaani TUM),
        //   usko naya IRIS CORE kabhi dikha hi nahi. Naye phone pe
        //   dikhta, purane pe nahi.
        //
        //   ✅ FIX: purani chat CHUPCHAAP load karo (view banao par
        //      screen na badlo). Core saamne hi rahega. Jaise hi
        //      user pehli baat kare, tab chat khulegi.
        val old = mem.load()
        if (old.isEmpty()) {
            // pehli baar — swagat
            val hi = "Namaste sir! Main IEYE RIS hoon."
            log.add(Memory.Msg(false, hi))
            mem.save(log)
        } else {
            log.addAll(old.takeLast(40))
        }

        /*
         * 🔍 v3.0 — PEHLI BAAR: POORA PHONE ANALYSE
         *
         * User ne kaha: "pahli Bari open hogi na to yah Mera
         * pura phone analyse karegi — ismein Kya Hai, Kaun Sa
         * app Hai, Kaun Sa kya hai"
         *
         * ⚠️ BACKGROUND thread pe — 100+ app scan karne me
         *    1-3 second lagte hain. UI thread pe app jam jaati
         *    aur user ko lagta "app hang ho gaya".
         */
        if (!PhoneScan.done(this)) {
            b.status.text = "🔍 Aapka phone samajh raha hoon…"
            core(CoreView.S.WORKING)
            Thread {
                val r = try { PhoneScan.run(this) }
                        catch (e: Exception) { null }
                runOnUiThread {
                    core(CoreView.S.IDLE)
                    if (r == null) {
                        b.status.text = "DABAO AUR BOLO"
                        return@runOnUiThread
                    }
                    val rep = PhoneScan.report(r)
                    log.add(Memory.Msg(false, rep))
                    mem.save(log)
                    showReport(rep)
                }
            }.start()
        }

        /*
         * 🔮 v3.1 — MASTER CIRCLE KHUD CHALU
         *
         * User ne kaha: "Main vah button dabana to mere screen
         * ke samne ek button aana chahie... ek master circle"
         *
         * Pehle bubble tabhi chalta tha jab user 🫧 dabata tha.
         * Zyadatar log usse dhoondh hi nahi paate the.
         *
         * Ab: permission hai to KHUD chalu ho jata hai. User
         * ne khud band kiya ho to nahi chalate (uski marzi ka
         * ehtiram).
         */
        if (Bubble.allowed(this) && !Bubble.on() &&
            !keys.flag("bubble_off", false)) {
            try { Bubble.start(this) } catch (e: Exception) {}
        }

        if (keys.wake()) WakeService.start(this)
        handoff(intent)

        // ⚠️ Eyes OFF hai to IEYE RIS aadha hi kaam kar payega.
        //    User ki jaanch me "❌ OFF" aaya tha aur usse pata
        //    hi nahi tha ki asli dikkat yahi hai. Ab saaf bolo.
        if (!Eyes.on()) {
            b.root.postDelayed({
                bot("⚠️ Sir, meri aankhein band hain.\n\n" +
                    "Abhi main sirf app khol sakta hoon. " +
                    "Group dhoondhna, message likhna, send " +
                    "dabana — ye sab NAHI kar sakta.\n\n" +
                    "Upar 👁 button dabaiye → list me IEYE RIS " +
                    "→ ON kar dijiye.\n\n" +
                    "Uske baad boliye:\n" +
                    "\"WhatsApp me School Friends group me " +
                    "hi bhej do\"")
            }, 2200)
        }

        // Eyes / Bubble chalu hai ya nahi
        updateEyes()
        updateBub()

        b.bubble.setOnClickListener {
            if (Bubble.on()) {
                Bubble.stop(this)
                keys.setFlag("bubble_off", true)
                bot("Master circle band kar diya sir.")
            } else if (!Bubble.allowed(this)) {
                bot("Sir, home screen pe bubble dikhane ke liye ek " +
                    "permission chahiye.\n\n" +
                    "Settings khol raha hoon — IEYE RIS ko " +
                    "\"Display over other apps\" me ON kar dijiye.")
                Bubble.askPerm(this)
            } else {
                keys.setFlag("bubble_off", false)
                Bubble.start(this)
                bot("Master circle chalu! 👁\n\n" +
                    "• Ek TAP = mic khulega, boliye\n" +
                    "• Ungli se kheench kar kahin bhi rakho\n" +
                    "• LAMBA dabao = ye app khule\n\n" +
                    "Home screen pe jaake dekhiye — circle " +
                    "wahan milega. Kisi bhi app me kaam karega.")
            }
            updateBub()
        }

        b.eyes.setOnClickListener {
            if (Eyes.on()) {
                bot("Eyes chalu hai sir — main screen dekh sakta hoon, " +
                    "button daba sakta hoon, aur message bhej sakta " +
                    "hoon.\n\nBand karna ho to Settings → " +
                    "Accessibility → IEYE RIS")
                Eyes.openSettings(this)
            } else {
                bot("Sir, ye IEYE RIS ki sabse badi taakat hai.\n\n" +
                    "Isse main:\n" +
                    "• Screen padh sakta hoon\n" +
                    "• Button khud daba sakta hoon\n" +
                    "• WhatsApp message SACH ME bhej sakta hoon\n" +
                    "• Notification padh sakta hoon\n\n" +
                    "Settings khol raha hoon — list me IEYE RIS " +
                    "dhoondh kar ON kar dijiye.")
                Eyes.openSettings(this)
            }
        }
    }

    // ═══════════════ SPLASH ═══════════════

    /**
     * App khulte hi ek chhota sa intro — 3 ring phailti hain,
     * beech me orb dhadakta hai, phir naam aata hai aur sab
     * gayab. Kul 1.4 second.
     */
    private fun splash() {
        val root = android.widget.FrameLayout(this)
        root.setBackgroundColor(0xFF070B14.toInt())

        fun ring(size: Int, color: String, delay: Long) {
            val v = android.widget.TextView(this)
            v.background =
                android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setStroke(dp(2),
                        android.graphics.Color.parseColor(color))
                    setColor(android.graphics.Color.TRANSPARENT)
                }
            v.alpha = 0f
            v.scaleX = 0.2f; v.scaleY = 0.2f
            root.addView(v, android.widget.FrameLayout.LayoutParams(
                dp(size), dp(size), android.view.Gravity.CENTER))
            v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(delay).setDuration(560)
                .withEndAction {
                    v.animate().alpha(0f).scaleX(1.5f).scaleY(1.5f)
                        .setDuration(420).start()
                }.start()
        }
        ring(190, "#7C3AED", 0)
        ring(140, "#00E6FF", 110)
        ring(96, "#EC4899", 220)

        val orb = android.widget.TextView(this).apply {
            text = "⚡"; textSize = 34f
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable
                    .Orientation.TL_BR,
                intArrayOf(0xFF7C3AED.toInt(), 0xFFEC4899.toInt()))
                .apply {
                    shape = android.graphics.drawable
                        .GradientDrawable.OVAL
                }
            alpha = 0f; scaleX = 0.4f; scaleY = 0.4f
        }
        root.addView(orb, android.widget.FrameLayout.LayoutParams(
            dp(74), dp(74), android.view.Gravity.CENTER))
        orb.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(420).start()

        val name = android.widget.TextView(this).apply {
            text = "I E Y E   R I S"
            textSize = 19f
            setTextColor(0xFFF8FAFC.toInt())
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.4f
            alpha = 0f
            translationY = dp(18).toFloat()
        }
        root.addView(name, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER).apply { topMargin = dp(120) })
        name.animate().alpha(1f).translationY(0f)
            .setStartDelay(320).setDuration(420).start()

        addContentView(root,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT))

        root.postDelayed({
            root.animate().alpha(0f).setDuration(340)
                .withEndAction {
                    (root.parent as? android.view.ViewGroup)
                        ?.removeView(root)
                }.start()
        }, 1400)
    }

    // ═══════════════ PERMISSION ═══════════════

    private fun askPerms() {
        val need = PERMS.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                PackageManager.PERMISSION_GRANTED
        }.toMutableList()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty())
            ActivityCompat.requestPermissions(this,
                need.toTypedArray(), 1)
    }

    // ═══════════════ SUNNA — CONTINUOUS ═══════════════
    //
    // ⚠️ 3 badi dikkatein thi, teeno yahan theek ki hain:
    //
    // 1. MIC BAND-CHALU HOTA THA — har jawab ke baad mic band ho
    //    jaata tha. Ab `keepOn` chalu rehta hai: IEYE RIS bol kar
    //    khatam karte hi mic apne aap wapas on ho jaata hai.
    //
    // 2. "Sunai nahi diya, phir se boliye" — ye NO_MATCH pe aata
    //    tha, jo chup rehne pe bhi aata hai. Ab chup rehna normal
    //    maana jaata hai, koi message nahi — bas mic chalta rehta.
    //
    // 3. LATE — pehle poora bolne ka intezaar hota tha. Ab partial
    //    result se hi kaam shuru ho jaata hai (jab bolna ruk jaye).

    private var keepOn = false          // continuous mode chalu?
    private var lastPartial = ""
    private var busy = false            // ek waqt me ek hi kaam
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var silence: Runnable? = null

    private fun micOk(): Boolean {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            askPerms(); return false
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            bot("Sir, is phone me Google ka voice service nahi hai. " +
                "Play Store se \"Google\" app install kar lijiye.")
            return false
        }
        return true
    }

    private fun startListen() {
        if (!micOk()) return
        keepOn = true
        listen()
    }

    /** Asli mic — baar-baar isi ko call karte hain */
    private fun listen() {
        if (!keepOn || busy) return
        voice.stop()
        listening = true
        lastPartial = ""
        b.status.text = "Boliye sir…"
        b.status.visibility = View.VISIBLE
        core(CoreView.S.LISTENING)
        act("LISTENING")
        Sfx.play(this, Sfx.LISTEN)

        try { rec?.destroy() } catch (e: Exception) {}
        rec = SpeechRecognizer.createSpeechRecognizer(this)
        rec?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(p: Bundle?) {
                b.status.text = "🎤 Boliye…"
            }

            override fun onBeginningOfSpeech() {
                b.status.text = "🎤 Sun raha hoon…"
                cancelSilence()
            }

            /** Awaaz ka level — bar dikhane ke liye */
            override fun onRmsChanged(v: Float) {
                if (v > 1f) {
                    val n = (v / 2f).toInt().coerceIn(1, 8)
                    // v2.0 — ab lahar core ke andar dikhti hai,
                    // alag se text-wave ki zarurat nahi
                    coreLevel(n / 8f)
                }
            }

            override fun onBufferReceived(p: ByteArray?) {}

            override fun onEndOfSpeech() {
                coreLevel(0f)
                b.status.text = "⚡ Samajh raha hoon…"
            }

            override fun onError(e: Int) {
                coreLevel(0f)
                listening = false

                // ⚠️ Chup rehna = error NAHI hai. Bas phir se suno.
                val chup = e == SpeechRecognizer.ERROR_NO_MATCH ||
                           e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (chup) {
                    // partial me kuch tha? to wahi use karo
                    val p = lastPartial.trim()
                    if (p.length > 1) { fire(p); return }
                    if (keepOn) { again(250); return }
                    idle(); return
                }

                if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    again(500); return
                }
                if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    keepOn = false; idle(); askPerms(); return
                }
                if (e == SpeechRecognizer.ERROR_NETWORK ||
                    e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                    b.status.text = "📶 Network slow…"
                    again(900); return
                }
                // baaki sab — chup-chaap dobara
                if (keepOn) again(600) else idle()
            }

            override fun onResults(r: Bundle?) {
                coreLevel(0f)
                listening = false
                val t = r?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()
                    ?: lastPartial.trim()
                if (t.isNotBlank()) fire(t)
                else if (keepOn) again(200) else idle()
            }

            /**
             * REAL-TIME — bolte-bolte hi text dikhta hai.
             * Aur agar 900ms tak kuch naya na aaye to samajh lo
             * baat khatam — turant kaam shuru. Isse 2-3 second
             * bach jaate hain.
             */
            override fun onPartialResults(r: Bundle?) {
                val t = r?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: return
                if (t.isBlank() || t == lastPartial) return
                lastPartial = t
                b.status.text = t
                cancelSilence()

                /*
                 * ⚠️⚠️ v3.0 — "POORI BAAT KHATAM HONE DO" ⚠️⚠️
                 *
                 * User ki shikayat:
                 *   "hamari baat Puri khatm hogi hai na tab hi vah
                 *    jawab degi... Maine bola yah karo vah karo aur
                 *    Main chup hua thodi der ke liye to vah man
                 *    lagakar baat khatam Ho Gai"
                 *
                 * Bilkul sahi. Pehle yahan sirf 900ms tha. Yaani
                 * aap bolte-bolte ek pal ruke — soch me, saans
                 * lene ke liye, ya "aur... haan" kehne se pehle —
                 * aur IEYE RIS turant maan leta tha ki baat khatam.
                 * Aadhi baat pe kaam shuru kar deta tha.
                 *
                 * Aam aadmi bolte waqt 0.8-1.5 second aaram se
                 * rukta hai. 900ms us se bhi kam tha.
                 *
                 * ✅ AB:
                 *   • Settings se badla ja sakta hai (nakhre)
                 *   • Default 2.2 second (pehle 0.9)
                 *   • Baat LAMBI ho to aur zyada intezaar —
                 *     kyunki lambi baat me rukna aam hai
                 *   • "aur", "phir", "uske baad" jaise shabd pe
                 *     samajh lo ki aage aur aane wala hai
                 */
                var wait = Keys(this@MainActivity).waitMs()

                // lambi baat = zyada sabr
                if (t.length > 60) wait += 700
                if (t.length > 120) wait += 700

                // "...aur" pe ruke ho to pakka aage bologe
                val tail = t.lowercase().trimEnd()
                if (Regex("(aur|phir|uske baad|fir|then|and|" +
                          "ke baad|aur bhi|iske alawa|plus)$")
                        .containsMatchIn(tail)) {
                    wait += 1800
                }

                silence = Runnable {
                    if (listening && lastPartial.isNotBlank()) {
                        try { rec?.stopListening() } catch (e: Exception) {}
                    }
                }
                ui.postDelayed(silence!!, wait)
            }

            override fun onEvent(t: Int, p: Bundle?) {}
        })

        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Jaldi jawab ke liye — Android ko batao ki thoda sa
            // rukna kaafi hai
            // v3.0 — Android ko bhi batao ki sabr rakhe.
            // ⚠️ Ye sirf ISHAARA hai — har phone maanta nahi.
            //    Isliye upar apna khud ka timer bhi rakha hai.
            val w = Keys(this@MainActivity).waitMs()
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, w)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                w - 400)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1200L)
        }
        try { rec?.startListening(i) } catch (e: Exception) { again(700) }
    }

    private fun cancelSilence() {
        silence?.let { ui.removeCallbacks(it) }
        silence = null
    }

    /** Thodi der baad phir se suno */
    private fun again(ms: Long) {
        cancelSilence()
        listening = false
        ui.postDelayed({ if (keepOn && !busy) listen() }, ms)
    }

    private fun idle() {
        cancelSilence()
        listening = false
        keepOn = false
        b.status.text = "DABAO AUR BOLO"
        b.status.visibility = View.VISIBLE
        coreLevel(0f)
        core(CoreView.S.IDLE)
        act("")
    }

    private fun stopListen() {
        keepOn = false
        cancelSilence()
        try { rec?.cancel() } catch (e: Exception) {}
        try { rec?.destroy() } catch (e: Exception) {}
        rec = null
        idle()
    }

    /**
     * v2.0 — "mic band karo" hukum ke liye.
     *
     * Actions.kt background thread se bulata hai, isliye UI
     * ka kaam runOnUiThread me karna zaroori hai — warna
     * app crash ho jati.
     */
    fun forceStopListening() {
        runOnUiThread {
            try { stopListen() } catch (e: Exception) {}
        }
    }

    /** Kaam shuru — ek hi baar, chahe partial se aaye ya final se */
    private fun fire(text: String) {
        if (busy) return
        busy = true
        cancelSilence()
        listening = false
        try { rec?.cancel() } catch (e: Exception) {}
        handle(text)
    }

    // ═══════════════ SAMAJHNA + KARNA ═══════════════

    private fun handle(text: String) {
        me(text)
        // 🛑 naya kaam — purana STOP saaf karo, warna ek baar
        //    stop dabane ke baad app hamesha ruki rehti
        Halt.reset()
        b.status.text = "⚡ Kaam kar raha hoon…"
        b.status.visibility = View.VISIBLE
        core(CoreView.S.WORKING)
        act("UNDERSTANDING")

        // 📖 v4.2 — offline se jawab mil sakta hai? (net na lage)
        Offline.tryAnswer(this, text)?.let {
            showReport(it)
            busy = false
            idle()
            return
        }

        // 🧠 v2.1 — khud-b-khud yaad rakho
        //
        //    Pehle user ko har baar "yaad rakho" bolna padta tha.
        //    Ab normal baat se hi fact pakad lete hain —
        //    "mera naam Ujjawal hai", "main Bhopal me rehta hoon",
        //    "mujhe chai pasand hai" — sab apne aap.
        try {
            val learnt = mem.autoLearn(text)
            if (learnt.isNotBlank()) Brain.log("🧠 seekha: $learnt")
        } catch (e: Exception) {}

        // ═══ 0. AGENT — screen ke andar ka kaam ═══
        //
        // "WhatsApp kholo, School Friends group me hi bhejo"
        // jaisa kaam ek JSON se nahi hota. Uske liye screen
        // dekh-dekh kar kadam uthane padte hain. Agent wahi
        // karta hai.
        if (Agent.needed(text) && online()) {
            if (!Eyes.on()) {
                done("Sir, iske liye meri aankhein chahiye 👁\n\n" +
                    "Upar 👁 button dabaiye aur Accessibility ON " +
                    "kar dijiye. Uske bina main sirf app khol " +
                    "sakta hoon — andar group dhoondhna, message " +
                    "likhna, send dabana nahi kar sakta.")
                return
            }
            bot("🤖 Theek hai sir, main khud karta hoon…")
            thinking(true)
            Agent.run(this, text, object : Agent.Watch {
                override fun onState(st: String) { act(st) }
                override fun onStep(n: Int, total: Int, what: String) {
                    runOnUiThread {
                        b.status.text = "🤖 $n · $what"
                        b.status.visibility = View.VISIBLE
                    }
                }
                override fun onDone(msg: String) {
                    runOnUiThread {
                        thinking(false)
                        bot(msg)
                        core(CoreView.S.SPEAKING)
                        voice.say(msg) {
                            core(CoreView.S.IDLE)
                            runOnUiThread { after() } }
                    }
                }
            })
            return
        }

        // 1. LOCAL — 0 ms, bina internet.
        //    ⚠️ Multi-command (do kaam ek saath) ho to local
        //       khud null deta hai — AI hi sambhalta hai.
        val loc = Brain.local(text)
        if (loc != null) { runAll(listOf(loc)); return }

        if (!online()) {
            done("Sir, internet nahi hai. Bina internet main seedhe " +
                 "kaam kar sakta hoon — \"torch on\", \"YouTube " +
                 "kholo\", \"volume badhao\".")
            return
        }

        // 2. AI — background thread, UI atkegi nahi
        b.status.text = "🧠 Soch raha hoon…"
        core(CoreView.S.THINKING)
        act("THINKING")
        thinking(true)
        Thread {
            val cmds = try { Brain.aiMulti(this, text) } catch (e: Exception) {
                emptyList() }
            runOnUiThread {
                thinking(false)
                if (cmds.isNotEmpty()) runAll(cmds)
                else {
                    // ⚠️ Pehle yahan sirf "samajh nahi aaya" tha.
                    //    User ko kabhi pata nahi chalta tha ki
                    //    asli me API fail hui hai. Ab saaf batao.
                    val e = Brain.lastError
                    done(if (e.isBlank())
                        "Samajh nahi aaya sir. Thoda saaf boliye?"
                    else "Sir, AI se baat nahi ho payi — $e\n\n" +
                        "⚙ Settings → 🩺 AI Jaanch dabao, " +
                        "poori detail mil jayegi.")
                }
            }
        }.start()
    }

    /**
     * ⚡ EK SE ZYADA KAAM — ek ke baad ek.
     *
     * User ki shikayat thi: "jab ek saath kuch bola jata hai to
     * kuch nahi kar pata". Wajah — pehle sirf PEHLA command
     * chalta tha, baaki gir jaate the. Ab poori list chalti hai.
     *
     * Har kaam ke beech 350ms ka gap — warna Android ke pass
     * ek Activity khulne se pehle doosri aa jaati hai aur
     * dono me se koi nahi chalti.
     */
    /**
     * v2.2 — kaam ke baad tag refresh (mode badla ho sakta hai)
     */
    private fun afterAction(cmds: List<Brain.Cmd>) {
        if (cmds.any { it.action == "mode" ||
                       it.action == "mute_voice" ||
                       it.action == "unmute_voice" })
            refreshMuteTag()
    }

    private fun runAll(cmds: List<Brain.Cmd>) {
        if (cmds.isEmpty()) { after(); return }

        if (cmds.size > 1) {
            val list = cmds.mapIndexed { i, c ->
                "${i + 1}. ${Actions.label(c.action)}" }
                .joinToString("\n")
            bot("Theek hai sir — ${cmds.size} kaam kar raha hoon:\n" +
                list)
        }

        val replies = mutableListOf<String>()

        fun step(i: Int) {
            if (i >= cmds.size) {
                // sab ho gaya — ab ek saath bol do
                afterAction(cmds)
                val all = replies.filter { it.isNotBlank() }
                val say = when {
                    all.isEmpty() -> "Ho gaya sir."
                    cmds.size == 1 -> all.first()
                    else -> all.joinToString(". ")
                }
                core(CoreView.S.SPEAKING)
                act("SPEAKING")
                Sfx.play(this@MainActivity, Sfx.DONE)
                voice.say(say) {
                    core(CoreView.S.IDLE)
                    runOnUiThread { after() } }
                return
            }
            // 🛑 v4.1 — beech me STOP daba diya? aage mat badho
            if (Halt.stopped()) {
                Brain.log("🛑 kaam beech me roka gaya")
                busy = false
                idle()
                return
            }

            val c = cmds[i]

            /*
             * ⚠️⚠️ v3.1 — "EK HI ANSWER AA RAHA HAI: JAWAB NAHI MILA"
             *
             * User ki shikayat: har baat pe wahi ek jawab.
             * Report me saaf tha — model 662ms me "chat" laut
             * raha tha, yaani AI theek chal rahi thi. Phir jawab
             * kyun nahi?
             *
             * ASLI WAJAH: Actions.run() SEEDHA yahin bulaya jata
             * tha — UI THREAD pe. v2.2 me maine "chat" action ko
             * Brain.talk() se joda tha, jo NETWORK call karta hai.
             *
             * Android UI thread pe network call karte hi
             * NetworkOnMainThreadException phenk deta hai —
             * turant, bina koshish kiye. Wo exception yahan
             * catch ho kar "Ye kaam nahi ho paya sir" ban jaata
             * tha. Isliye HAR sawaal ka ek hi jawab aata tha.
             *
             * Ab: network wale action BACKGROUND thread pe.
             * Baaki (torch, app kholo) UI thread pe hi theek hain
             * — wo turant hote hain aur kuch Android API ko UI
             * thread hi chahiye.
             */
            val needsNet = c.action in setOf("chat", "translate")

            if (needsNet) {
                Thread {
                    val r = try { Actions.run(this, c) }
                        catch (e: Exception) {
                            Brain.log("✘ ${c.action}: ${e.message}")
                            "Ye kaam nahi ho paya sir."
                        }
                    runOnUiThread {
                        bot(r, c)
                        replies.add(r)
                        step(i + 1)
                    }
                }.start()
                return
            }

            val reply = try { Actions.run(this, c) }
                catch (e: Exception) {
                    Brain.log("✘ ${c.action}: ${e.message}")
                    "Ye kaam nahi ho paya sir."
                }
            bot(reply, c)
            replies.add(reply)
            // agla kaam thodi der baad
            b.root.postDelayed({ step(i + 1) },
                if (cmds.size == 1) 0L else 350L)
        }
        step(0)
    }

    private fun doIt(c: Brain.Cmd) = runAll(listOf(c))

    private fun done(msg: String) {
        bot(msg)
        core(CoreView.S.SPEAKING)
        voice.say(msg) {
            core(CoreView.S.IDLE)
            runOnUiThread { after() } }
    }

    /** Jawab khatam — ab phir se suno */
    private fun after() {
        busy = false
        if (keepOn) {
            b.status.text = "🎤 Boliye…"
            again(120)          // turant wapas mic
        } else {
            b.status.visibility = View.GONE
        }
    }

    // ═══════════════════════════════════════════
    //   👁 IRIS CORE ka intezaam — v2.0
    // ═══════════════════════════════════════════

    /**
     * Core ki haalat badlo. Dono core (bada + chhota) ek
     * saath badalte hain, warna dono alag-alag dikhte.
     *
     * ⚠️ Ye kisi bhi thread se bulaya ja sakta hai (Brain
     *    aur Agent background me chalte hain), isliye
     *    runOnUiThread zaroori hai.
     */
    private fun core(s: CoreView.S) {
        runOnUiThread {
            try {
                b.core.state = s
            } catch (e: Exception) {}
        }
    }

    /** Mic ki awaaz ka level core tak pahuchao */
    private fun coreLevel(v: Float) {
        try {
            b.core.level = v
        } catch (e: Exception) {}
    }

    /**
     * v3.0 — CHAT MODE HATA DIYA.
     *
     * User ne kaha: "ab Ham chat vagaira nahin karenge, Ham
     * sidha direct bolenge". Ab sirf circle rehta hai — chat
     * kabhi nahi khulti.
     *
     * ⚠️ Baat-cheet ab bhi Memory me SAVE hoti hai (AI ko
     *    context chahiye), bas screen pe nahi dikhti. Purani
     *    baat Settings me dikh jayegi.
     */
    private fun toChat() {
        // jaanbujh kar khali — ab chat screen hai hi nahi
    }

    /**
     * 📊 v4.1 — AGENT ACTIVITY panel
     *
     * Spec: Listening / Understanding / Planning / Executing /
     *       Verifying / Completed
     *
     * ⚠️ Spec me saaf likha: "Do not show secrets, API keys or
     *    passwords." Isliye yahan sirf HAALAT ka naam jaata hai,
     *    kabhi koi data ya key nahi.
     */
    private fun act(state: String) {
        runOnUiThread {
            try {
                if (state.isBlank()) {
                    b.activity.visibility = View.GONE
                    return@runOnUiThread
                }
                b.activity.text = state
                b.activity.visibility = View.VISIBLE
            } catch (e: Exception) {}
        }
    }

    /**
     * v3.0 — Lamba report dikhao (phone scan, memory, reminders).
     *
     * Chat screen ab hai nahi, isliye lambi cheezein scroll
     * wale dialog me dikhate hain. Status line pe 300 akshar
     * se zyada padhna mushkil hota.
     */
    private fun showReport(text: String) {
        try {
            val tv = TextView(this)
            tv.text = text
            tv.textSize = 12.5f
            tv.setTextIsSelectable(true)
            tv.setTextColor(0xFFE2E8F0.toInt())
            tv.typeface = android.graphics.Typeface.MONOSPACE
            val p = dp(18)
            tv.setPadding(p, p, p, p)
            val sc = ScrollView(this)
            sc.addView(tv)
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(sc)
                .setPositiveButton("Theek hai") { _, _ ->
                    b.status.text = "DABAO AUR BOLO"
                }
                .setNeutralButton("Copy") { _, _ ->
                    try {
                        val cm = getSystemService(CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText(
                                "IEYE RIS", text))
                    } catch (e: Exception) {}
                }
                .show()
        } catch (e: Exception) {
            b.status.text = text.take(300)
        }
    }

    /**
     * 🔇 chup mode + 🎓 study/coding mode ka nishaan header me.
     *
     * v2.2: pehle sirf chup mode dikhta tha. Ab mode bhi —
     * warna user ko pata hi nahi chalta ki Study Mode chalu hai
     * aur jawab alag kyun aa rahe hain.
     */
    private fun refreshMuteTag() {
        runOnUiThread {
            try {
                val muted = Voice.isMuted(this)
                val m = Modes.get(this)
                when {
                    muted && m != Modes.NORMAL -> {
                        b.mutedTag.text = "🔇 " + Modes.label(m)
                        b.mutedTag.visibility = View.VISIBLE
                    }
                    muted -> {
                        b.mutedTag.text = "🔇 CHUP"
                        b.mutedTag.visibility = View.VISIBLE
                    }
                    m != Modes.NORMAL -> {
                        b.mutedTag.text = Modes.label(m)
                        b.mutedTag.visibility = View.VISIBLE
                    }
                    else -> b.mutedTag.visibility = View.GONE
                }
            } catch (e: Exception) {}
        }
    }

    private var chatMode = false

    // ═══════════════ CHAT UI ═══════════════

    private fun me(t: String) {
        toChat()
        bubble(t, true, null)
        log.add(Memory.Msg(true, t)); mem.save(log)
    }

    private fun bot(t: String, c: Brain.Cmd? = null) {
        toChat()
        bubble(t, false, c)
        log.add(Memory.Msg(false, t, c?.action ?: "",
            c?.fromAI ?: false))
        mem.save(log)
    }

    private fun updateBub() {
        b.bubble.alpha = if (Bubble.on()) 1f else 0.35f
    }

    private fun updateEyes() {
        val on = Eyes.on()
        b.eyes.text = if (on) "👁" else "👁"
        b.eyes.alpha = if (on) 1f else 0.35f
    }

    // ═══════════════════════════════════════════
    //   🛡️ CRASH CATCHER — v1.6
    // ═══════════════════════════════════════════

    /**
     * App crash ho to chup-chaap band mat ho — wajah likh do.
     *
     * Pehle user ko sirf itna dikhta tha ki app khula aur turant
     * band ho gaya. Ab poora crash Settings me dikhta hai, aur
     * mujhe bhejne pe seedha pata chal jata hai kya toota.
     */
    private fun installCrashCatcher() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(sw))
                val time = SimpleDateFormat(
                    "dd-MM-yyyy HH:mm:ss", Locale.US).format(Date())
                val txt = "IEYE RIS v${BuildConfig.VERSION_NAME}\n" +
                          "$time\nThread: ${t.name}\n\n$sw"
                java.io.File(filesDir, "last_crash.txt")
                    .writeText(txt)
            } catch (x: Exception) {}
            prev?.uncaughtException(t, e)
        }
    }

    // ══════════════════════════════════════════════════
    //  ⏱ v4.5 — LICENSE TIMER (sabse upar, real-time)
    // ══════════════════════════════════════════════════
    private val licTick = object : Runnable {
        override fun run() {
            drawLic()
            ui.postDelayed(this, 1000)
        }
    }

    private fun drawLic() {
        if (!::b.isInitialized) return
        try {
            if (!License.activated(this)) {
                b.licTimer.visibility = View.GONE
                return
            }
            b.licTimer.visibility = View.VISIBLE
            b.licTimer.text = License.timerText(this)
            b.licTimer.setTextColor(License.timerColor(this))

            /*
             * ⌛ Samay khatam -> app ko andar rakhne ka koi
             *    matlab nahi. Seedha License screen pe bhejo.
             *
             * ⚠️ finish() ZAROORI hai — warna back dabate hi
             *    banda wapas andar aa jayega.
             */
            if (!License.unlimited(this) &&
                License.secondsLeft(this) == 0L) {
                ui.removeCallbacks(licTick)
                try { stopListen() } catch (e: Exception) {}
                try { voice.stop() } catch (e: Exception) {}
                startActivity(Intent(this,
                    LicenseActivity::class.java))
                finish()
            }
        } catch (e: Exception) {
            // timer ki wajah se app kabhi na girre
        }
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        updateEyes()
        updateBub()
        refreshMuteTag()

        // ⏱ timer chalu — sirf screen saamne hone par
        drawLic()
        ui.removeCallbacks(licTick)
        ui.postDelayed(licTick, 1000)
    }

    override fun onPause() {
        super.onPause()
        isForeground = false
        // ⚠️ band karna zaroori — warna background me har second
        //    jagta rehta hai, battery jaati hai
        ui.removeCallbacks(licTick)
    }

    /**
     * v3.0 — CHAT BUBBLE HATA DIYA.
     *
     * Pehle har baat screen pe bubble ban kar dikhti thi.
     * User ne kaha ab sirf circle chahiye, chat nahi.
     *
     * ⚠️ Ye function poori tarah HATAYA NAHI, khali chhoda hai.
     *    Wajah: ise 6 jagah se bulaya jata hai. Har call site
     *    hatane se galti hone ka khatra tha. Khali function
     *    surakshit hai aur baad me chat wapas laani ho to
     *    sirf yahi bharna padega.
     *
     *    Baat-cheet ab bhi Memory me jaati hai — AI ko context
     *    milta rehta hai, aur Settings me dikh jaati hai.
     */
    private fun bubble(text: String, mine: Boolean, c: Brain.Cmd?) {
        // screen pe kuch nahi — sirf status line update
        if (!mine && text.isNotBlank()) {
            runOnUiThread {
                b.status.text = text.take(300)
                b.status.visibility = View.VISIBLE
            }
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun online(): Boolean = try {
        val cm = getSystemService(CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= 23) {
            val n = cm.activeNetwork
            val c = cm.getNetworkCapabilities(n)
            c != null && c.hasCapability(
                android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    } catch (e: Exception) { true }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        setIntent(i)
        handoff(i)
        if (i?.getBooleanExtra("listen", false) == true) startListen()
    }

    /** Bubble ne jo kaam nahi kar paya, wo yahan poora hota hai */
    private fun handoff(i: Intent?) {
        val a = i?.getStringExtra("cmd_action") ?: return
        if (a.isBlank()) return
        i.removeExtra("cmd_action")
        val c = Brain.Cmd(a, i.getStringExtra("cmd_arg") ?: "",
            i.getStringExtra("cmd_say") ?: "")
        busy = true
        ui.postDelayed({ doIt(c) }, 350)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { ui.removeCallbacks(licTick) } catch (e: Exception) {}
        /*
         * ⚠️ v3.1 CRASH FIX — report me ye aaya tha:
         *
         *   UninitializedPropertyAccessException:
         *   lateinit property voice has not been initialized
         *
         * Wajah: onCreate() me setup check hai —
         *     if (!SetupActivity.done(this)) { ...; finish(); return }
         * Agar wahan se return ho jaye to `voice` kabhi bani hi
         * nahi. Phir Android onDestroy() bulata hai aur
         * voice.shutdown() pe app crash kar jaati hai.
         *
         * ✅ Ab pehle check karte hain ki bani bhi hai ya nahi.
         */
        try {
            if (::voice.isInitialized) voice.shutdown()
        } catch (e: Exception) {}
        try { rec?.destroy() } catch (e: Exception) {}
        rec = null
        try { Sfx.release() } catch (e: Exception) {}
        if (liveRef?.get() === this) liveRef = null
    }

    // ═══════════════ 🧠 SOCHNE KA ANIMATION ═══════════════
    //
    // User bola: "usse puchte hain to bahut time lagta hai".
    // Waqt to 0.2s hi hai, par bina kuch dikhe wo lamba lagta
    // hai. Ab 3 dot naachte hain — pata chalta hai kaam ho raha.

    private var dots: android.widget.LinearLayout? = null

    private fun thinking(on: Boolean) {
        if (!on) {
            dots?.let { d ->
                (d.parent as? android.view.ViewGroup)?.removeView(d)
            }
            dots = null
            return
        }
        if (dots != null) return

        val row = android.widget.LinearLayout(this)
        row.orientation = android.widget.LinearLayout.HORIZONTAL
        row.setPadding(dp(16), dp(10), dp(16), dp(10))
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(4), dp(6), dp(4), dp(6))
        row.layoutParams = lp
        row.background = android.graphics.drawable.GradientDrawable()
            .apply {
                shape = android.graphics.drawable.GradientDrawable
                    .RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(0xFF111827.toInt())
            }

        for (i in 0..2) {
            val d = android.widget.TextView(this)
            d.background = android.graphics.drawable.GradientDrawable()
                .apply {
                    shape = android.graphics.drawable
                        .GradientDrawable.OVAL
                    setColor(0xFF00E6FF.toInt())
                }
            val p = android.widget.LinearLayout.LayoutParams(
                dp(8), dp(8))
            p.setMargins(dp(3), 0, dp(3), 0)
            row.addView(d, p)
            d.alpha = 0.3f
            d.animate().alpha(1f).setDuration(400)
                .setStartDelay(i * 160L)
                .withEndAction(object : Runnable {
                    override fun run() {
                        if (dots == null) return
                        d.animate().alpha(0.3f).setDuration(400)
                            .withEndAction {
                                if (dots != null)
                                    d.animate().alpha(1f)
                                        .setDuration(400)
                                        .withEndAction(this).start()
                            }.start()
                    }
                }).start()
        }
        dots = row
        // v3.0 — screen pe dots nahi, core khud thinking dikhata hai
    }
}
