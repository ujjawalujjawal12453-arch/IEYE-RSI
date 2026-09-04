package com.ravanx.ieyeris

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 🔑 LICENSE SCREEN — app ka sabse pehla darwaza
 *
 * User ne kaha: "jab dalega na SABSE PEHLE KEY HI maangega"
 *
 * MainActivity se pehle yahi khulti hai. Bina sahi key ke
 * aage kuch nahi.
 *
 * ⚠️ Yahan koi "skip" ya "baad me" wala button JAANBUJH KAR
 *    nahi rakha. Warna license ka matlab hi khatam.
 */
class LicenseActivity : AppCompatActivity() {

    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var btn: TextView
    private lateinit var core: CoreView
    private lateinit var srvLine: TextView

    /* ⏱ v4.5 — sabse upar wala real-time timer */
    private lateinit var timer: TextView
    private lateinit var vbtn: TextView

    private val tick = android.os.Handler(
        android.os.Looper.getMainLooper())

    /*
     * ⚠️ Runnable ko field me rakha hai (lambda inline nahi),
     *    warna removeCallbacks() usi object ko dhoondh nahi
     *    paata aur timer background me chalta reh jata hai —
     *    battery khata hai aur activity leak karta hai.
     */
    private val ticker = object : Runnable {
        override fun run() {
            drawTimer()
            tick.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(ui())

        /*
         * Pehle se chalu hai? -> seedha andar.
         *
         * ⚠️ v4.5 — timer yahan bhi chalu kar dete hain. Wajah:
         *    agar user ne back se wapas aakar ye screen dekhi,
         *    to use turant timer dikhna chahiye — na ki khali
         *    screen. User ne kaha "timer to chalu hoga".
         */
        if (License.activated(this)) {
            input.setText(License.savedCode(this))
            drawTimer()
            open()
            // ⚠️ Andar bhejne ke BAAD bhi background me check
            //    karte hain. Agar admin ne key band kar di ho to
            //    agli baar app khulegi hi nahi.
            License.checkAsync(this)
            return
        }

        // purani key hai par expire/band? wajah dikhao
        val old = License.savedMsg(this)
        if (old.isNotBlank()) {
            status.text = old
            status.setTextColor(0xFFFF8080.toInt())
            core.state = CoreView.S.ERROR
        }
        val saved = License.savedCode(this)
        if (saved.isNotBlank()) input.setText(saved)
        showServer()
        drawTimer()
    }

    /*
     * ⏱ Timer sirf tab chalta hai jab screen saamne ho.
     *   onResume  -> chalu
     *   onPause   -> band
     *
     * ⚠️ onPause pe band karna ZAROORI hai. Warna app background
     *    me chala jata hai aur ye har second Handler jagata rehta
     *    hai — battery khaata hai aur Activity leak hoti hai.
     */
    override fun onResume() {
        super.onResume()
        drawTimer()
        tick.removeCallbacks(ticker)
        tick.postDelayed(ticker, 1000)
    }

    override fun onPause() {
        super.onPause()
        tick.removeCallbacks(ticker)
    }

    override fun onDestroy() {
        super.onDestroy()
        tick.removeCallbacks(ticker)
    }

    /** ⏱ Har second — timer ka text taaza karo */
    private fun drawTimer() {
        if (!::timer.isInitialized) return
        if (!License.activated(this)) {
            // key hi nahi -> timer chhupa do (khali 00:00:00
            // dikhana user ko dara deta hai)
            timer.visibility = android.view.View.GONE
            return
        }
        timer.visibility = android.view.View.VISIBLE
        timer.text = License.timerText(this)
        timer.setTextColor(License.timerColor(this))

        // samay khatam ho gaya? turant band karo — ruko mat
        if (License.secondsLeft(this) == 0L &&
            !License.unlimited(this)) {
            timer.text = "⌛ SAMAY KHATAM"
            status.setTextColor(0xFFFF5A5A.toInt())
            status.text = "⌛ Aapka samay poora ho gaya.\n" +
                          "Nayi key ke liye admin se baat kijiye."
        }
    }

    /**
     * ✅ VERIFY — server se abhi ki abhi milao.
     *
     * ⚠️ Ye JAANBUJH KAR grace-period ka fayda nahi uthata jaisa
     *    background check karta hai. User ne khud daba kar poochha
     *    hai "sach batao" — to sach hi dikhana chahiye, chahe
     *    server band ho.
     */
    private fun verifyNow() {
        val code = License.savedCode(this)
            .ifBlank { input.text.toString().trim().uppercase() }
        if (code.length < 8) {
            status.setTextColor(0xFFFFC94D.toInt())
            status.text = "Pehle key daaliye, phir verify."
            return
        }
        if (License.server(this).isBlank()) {
            status.setTextColor(0xFFFFC94D.toInt())
            status.text = "⚙️ Server ka pata set nahi hai."
            return
        }

        vbtn.isEnabled = false
        vbtn.text = "⏳  MILA RAHA HOON…"
        core.state = CoreView.S.THINKING

        Thread {
            val t0 = System.currentTimeMillis()
            val r = License.check(this, code)
            val ms = System.currentTimeMillis() - t0
            runOnUiThread {
                vbtn.isEnabled = true
                vbtn.text = "✅  VERIFY"
                drawTimer()
                if (r.ok && !r.offline) {
                    core.state = CoreView.S.SPEAKING
                    status.setTextColor(0xFF39FF88.toInt())
                    status.text = "✅ Key sahi hai — server ne " +
                        "haan kaha\n(" + ms + " ms me jawab aaya)"
                    try { Sfx.play(this, Sfx.DONE) } catch (e: Exception) {}
                } else if (r.offline) {
                    core.state = CoreView.S.IDLE
                    status.setTextColor(0xFFFFC94D.toInt())
                    status.text = r.msg
                } else {
                    core.state = CoreView.S.ERROR
                    status.setTextColor(0xFFFF8080.toInt())
                    status.text = r.msg
                    try { Sfx.play(this, Sfx.ERROR) } catch (e: Exception) {}
                }
            }
        }.start()
    }

    private fun ui(): ScrollView {
        val sc = ScrollView(this)
        sc.setBackgroundColor(0xFF07090F.toInt())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(50), dp(28), dp(40))
        }
        sc.addView(root)

        /*
         * ⏱ v4.5 — REAL-TIME TIMER, SABSE UPAR
         *
         * User ne kaha: "jab bhi koi login vagaira karta hai na
         * SABSE UPAR timer chalu ho jaye, real time ka timer...
         * upar real time me chalu hona hi chahiye. Timer chahe
         * kuch bhi ho, timer to chalu hoga ek hi daalne ke baad."
         *
         * Isliye ye sabse pehli cheez hai — logo se bhi upar.
         * Har 1 second pe khud badalta hai.
         */
        timer = TextView(this).apply {
            text = ""
            textSize = 19f
            gravity = Gravity.CENTER
            letterSpacing = 0.14f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(0xFF39FF88.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = android.graphics.drawable.GradientDrawable()
                .apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#0B1220"))
                    setStroke(dp(1), Color.parseColor("#1E3050"))
                }
            visibility = android.view.View.GONE
        }
        root.addView(timer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(18)
        })

        core = CoreView(this)
        root.addView(core, LinearLayout.LayoutParams(dp(150), dp(150)))

        root.addView(TextView(this).apply {
            text = "IEYE RIS"
            textSize = 22f
            setTextColor(0xFFF8FAFC.toInt())
            letterSpacing = 0.24f
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, 0)
        })
        root.addView(TextView(this).apply {
            text = "LICENSE ZAROORI HAI"
            textSize = 10f
            setTextColor(0xFF39FF88.toInt())
            letterSpacing = 0.20f
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(28))
        })

        root.addView(TextView(this).apply {
            text = "Apni license key daaliye 👇"
            textSize = 14f
            setTextColor(0xFF8FA3BD.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })

        input = EditText(this).apply {
            hint = "IEYE-XXXX-XXXX-XXXX"
            textSize = 17f
            setTextColor(0xFFF8FAFC.toInt())
            setHintTextColor(0xFF4A5A70.toInt())
            gravity = Gravity.CENTER
            // ⚠️ Sirf bade akshar — key hamesha CAPS me hoti hai.
            //    Warna user chhote me type karke "galat key"
            //    dekhta aur pareshan hota.
            inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#070B14"))
                setStroke(dp(1), Color.parseColor("#253349"))
            }
            letterSpacing = 0.10f
        }
        root.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT))

        btn = TextView(this).apply {
            text = "🔓  CHALU KARO"
            textSize = 15f
            setTextColor(0xFF07090F.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(15), 0, dp(15))
            letterSpacing = 0.10f
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#39FF88"))
            }
            setOnClickListener { verify() }
        }
        root.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16)
        })

        /*
         * ⚙️ v4.4 — SERVER KA PATA (yahin se badlo)
         *
         * User ne poochha tha "render ka URL kahan daalun".
         * Ab file badalne ki zarurat nahi — yahi se ho jata hai.
         */
        /*
         * ✅ v4.5 — VERIFY BUTTON
         *
         * User ne kaha: "aur neeche verify button rakhna. Aisa
         * nahi ki server se poochh raha hoon ya wo — VERIFY ka
         * button rakhna."
         *
         * Ye "CHALU KARO" se alag hai:
         *   CHALU KARO -> nayi key daal kar activate
         *   VERIFY     -> jo key pehle se hai, wo abhi bhi sahi
         *                 hai? Server se turant milao. Timer bhi
         *                 taaza ho jata hai.
         */
        vbtn = TextView(this).apply {
            text = "✅  VERIFY"
            textSize = 14f
            setTextColor(0xFF39FF88.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(14))
            letterSpacing = 0.16f
            background = android.graphics.drawable.GradientDrawable()
                .apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.parseColor("#0B1220"))
                    setStroke(dp(1), Color.parseColor("#1E4A32"))
                }
            setOnClickListener { verifyNow() }
        }
        root.addView(vbtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        srvLine = TextView(this).apply {
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(16), dp(6), 0)
            setOnClickListener { askServer() }
            // 🔑 LAMBA DABAO -> owner password badlo
            setOnLongClickListener { changePass(); true }
        }
        root.addView(srvLine)

        status = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF8FA3BD.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(20), dp(6), 0)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        root.addView(status)

        root.addView(TextView(this).apply {
            text = "Key nahi hai? Admin se maangiye 🙏\n" +
                   "@UjjawalXsarkar"
            textSize = 12f
            setTextColor(0xFF4A5A70.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(34), 0, 0)
        })

        // 🆔 device ID — admin ko batane ke kaam aati hai
        root.addView(TextView(this).apply {
            text = "Device: " + License.deviceId(this@LicenseActivity)
                .take(12) + "…"
            textSize = 10f
            setTextColor(0xFF2E3A4D.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, 0)
            setOnClickListener {
                try {
                    val cm = getSystemService(CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData
                        .newPlainText("device",
                            License.deviceId(this@LicenseActivity)))
                    android.widget.Toast.makeText(
                        this@LicenseActivity, "Device ID copy ho gayi",
                        android.widget.Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {}
            }
        })
        return sc
    }

    /** Server ka pata upar dikhao — set hai ya nahi */
    private fun showServer() {
        val u = License.server(this)
        if (u.isBlank()) {
            srvLine.text = "⚙️  Server set nahi — TAP KARO 🔒"
            srvLine.setTextColor(0xFFFFC94D.toInt())
        } else {
            srvLine.text = "⚙️  " + u.replace("https://", "") +
                           "   🔒"
            srvLine.setTextColor(0xFF4A5A70.toInt())
        }
    }

    /**
     * Server ka pata poochho.
     *
     * ⚠️ https:// khud lagate hain agar user bhool jaye — ye
     *    sabse aam galti hai aur uska error samajh nahi aata
     *    ("SSL dikkat" jaisa kuch aata hai).
     */
    private fun askServer() {
        /*
         * 🔒 v4.5 — OWNER PASSWORD
         *
         * User ne kaha: "URL sirf jo hai na wo nahi badal sakta,
         * uske liye ek password lagega — yeh raha 2244"
         *
         * Ab aam user ye dialog khol hi nahi sakta. Password
         * daalne ke baad 10 minute tak khula rehta hai.
         */
        Owner.ask(this,
            "Server ka pata badalne ke liye password chahiye.") {
            askServerReal()
        }
    }

    private fun askServerReal() {
        val e = EditText(this).apply {
            hint = "https://aapka-app.onrender.com"
            setText(License.server(this@LicenseActivity))
            textSize = 15f
            setPadding(dp(16), dp(14), dp(16), dp(14))
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("⚙️ Server ka pata")
            .setMessage(
                "Render pe jo link mila hai wo yahan daaliye.\n\n" +
                "Misaal:\nhttps://ieyeris-license.onrender.com")
            .setView(e)
            .setPositiveButton("Save") { _, _ ->
                var u = e.text.toString().trim().trimEnd('/')
                if (u.isNotBlank() && !u.startsWith("http"))
                    u = "https://$u"          // bhool gaye to khud laga do
                License.setServer(this, u)
                showServer()
                android.widget.Toast.makeText(this,
                    if (u.isBlank()) "Server hata diya"
                    else "Server set ho gaya ✅",
                    android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Rehne do", null)
            .show()
    }

    private fun verify() {
        // ⚙️ server set hai? warna key daalne ka fayda nahi
        if (License.server(this).isBlank()) {
            status.setTextColor(0xFFFFC94D.toInt())
            status.text = "⚙️ Pehle server ka pata daaliye —\n" +
                          "upar wali line pe tap kijiye."
            askServer()
            return
        }

        val code = input.text.toString().trim().uppercase()
        if (code.length < 8) {
            status.text = "Poori key daaliye sir."
            status.setTextColor(0xFFFFC94D.toInt())
            return
        }

        btn.text = "⏳  SERVER SE POOCH RAHA HOON…"
        btn.isEnabled = false
        core.state = CoreView.S.THINKING
        status.text = ""

        // ⚠️ Background thread — network UI thread pe nahi chalta
        Thread {
            val r = License.check(this, code)
            runOnUiThread {
                btn.isEnabled = true
                btn.text = "🔓  CHALU KARO"
                if (r.ok) {
                    core.state = CoreView.S.SPEAKING
                    status.setTextColor(0xFF39FF88.toInt())
                    status.text = if (r.unlimited)
                        "✅ Key chalu ho gayi!\n♾️ Hamesha ke liye"
                    else
                        "✅ Key chalu ho gayi!\n⏳ ${r.daysLeft} din bache hain"
                    try { Sfx.play(this, Sfx.START) } catch (e: Exception) {}
                    btn.postDelayed({ open() }, 1100)
                } else {
                    core.state = CoreView.S.ERROR
                    status.setTextColor(0xFFFF8080.toInt())
                    status.text = r.msg
                    try { Sfx.play(this, Sfx.ERROR) } catch (e: Exception) {}
                }
            }
        }.start()
    }

    /**
     * 🔑 v4.5 — OWNER PASSWORD BADLO
     *
     * User ne kaha: "isi ko daalne ke baad wo password vagaira
     * change matlab URL vagaira change kar sakta hai"
     *
     * Server line ko LAMBA DABANE se khulta hai. Jaanbujh kar
     * chhupaya hai — aam user ko ye button dikhna hi nahi chahiye.
     */
    private fun changePass() {
        val d = dp(16)
        val oldE = EditText(this).apply {
            hint = "Purana password"
            inputType = InputType.TYPE_CLASS_NUMBER or
                        InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(d, d, d, d)
        }
        val newE = EditText(this).apply {
            hint = "Naya password (4-24 akshar)"
            inputType = InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(d, d, d, d)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(d + d / 2, d / 2, d + d / 2, 0)
            addView(oldE); addView(newE)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🔑 Owner password badlo")
            .setMessage("Bhool gaye to app dobara install karni " +
                        "padegi — yaad rakhiye.")
            .setView(box)
            .setPositiveButton("Badlo") { _, _ ->
                val (ok, msg) = Owner.setPass(this,
                    oldE.text.toString(), newE.text.toString())
                android.widget.Toast.makeText(this, msg,
                    android.widget.Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Rehne do", null)
            .show()
    }

    private fun open() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    /**
     * ⚠️ Back button se bahar nikal kar MainActivity me nahi
     *    ghus sakte — app hi band hogi.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        finishAffinity()
    }
}
