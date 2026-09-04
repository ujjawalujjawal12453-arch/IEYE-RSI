# ⚡ IEYE RIS — Android Voice Assistant

Bolo aur phone kaam kare. Hindi/Hinglish samajhta hai.

**Developer:** Ujjawal X Official · **Team:** RAVAN X HOSTING TEAM

---

# 📱 APK KAISE BANAYE — 5 minute

Aapko computer ki zarurat **nahi** hai. Phone se bhi ho jayega.

## Step 1 — GitHub pe account
```
github.com  →  Sign up  (free)
```

## Step 2 — Naya repo banao
```
github.com/new
  Repository name : ieyeris
  Public ya Private — dono chalega
  "Create repository" dabao
```

## Step 3 — Files upload karo
Repo page pe **"uploading an existing file"** link pe click karo.

`ieyeris` folder ke **saare files** drag karke daal do — ya ZIP
extract karke sab select karke upload karo.

⚠️ **`.github` folder zaroor upload ho** — isi me APK banane
wali file hai. GitHub website pe hidden folder upload karne me
dikkat aaye to:
- `Add file` → `Create new file`
- Naam me likho: `.github/workflows/build.yml`
- Neeche `build.yml` ka poora content paste kar do

## Step 4 — Commit
Neeche **"Commit changes"** dabao.

## Step 5 — APK apne aap ban jayegi
```
Repo →  Actions  tab  →  "Build IEYE RIS APK" chal raha hoga
```
**5-8 minute** lagenge. Green ✅ hone ka wait karo.

## Step 6 — Download
Do jagah se mil jayegi:

**A) Releases se (aasan)**
```
Repo → Releases → IEYERIS.apk
```

**B) Actions se**
```
Actions → wo run kholo → neeche "Artifacts" → IEYERIS-APK
```

## Step 7 — Install
Phone me APK kholo → **"Unknown sources allow"** → Install

---

# 🎙️ KYA-KYA BOL SAKTE HO

## App kholna
```
"IEYE RIS YouTube kholo"
"Instagram khol do"
"WhatsApp chalu karo"
"Chrome open karo"
```
40+ app pehle se hain — Paytm, PhonePe, Zomato, Swiggy, Netflix,
Spotify, Flipkart, Uber, Ola, Truecaller, Hotstar...

## Search
```
"YouTube pe Arijit Singh search karo"
"YouTube pe lofi music chalao"
"Google pe Python tutorial dhundo"
"Tum Hi Ho gaana bajao"
```

## 👁️ EYES ON karne ke baad — asli taakat

Settings → Accessibility → IEYE RIS → **ON**

Phir ye sab hone lagega:
```
"WhatsApp pe Papa ko hello bhejo"   ← send KHUD dabayega
"Screen pe kya likha hai"           ← padh kar sunayega
"Login pe click karo"               ← button khud dabayega
"Notification padho"                ← sab sunayega
"Neeche scroll karo"                ← scroll karega
"Back jao" / "Home jao"             ← navigate karega
"Screenshot lo"                     ← ab SACH ME lega
```

⚠️ Ye permission bahut taakatwar hai — isliye Android **aapse
khud** puchhta hai. IEYE RIS zabardasti nahi le sakta (aur sahi hi
hai). Aapka data kahin nahi jaata — sab phone me hi rehta hai.

## 🧠 Memory — IEYE RIS yaad rakhta hai

```
"Yaad rakho mera naam Ujjawal hai"
"Mera ghar Indore hai"
   ...baad me...
"Mera naam kya hai"        → "Aapka naam Ujjawal hai sir"
"Kya kya yaad hai"         → poori list
```

Chat history bhi save rehti hai — app band karke khologe to
purani baat wahin milegi.

## 🔢 Calculator

```
"25 into 4"        → "Jawab hai 100"
"150 plus 275"     → "Jawab hai 425"
"1000 divide 7"    → "Jawab hai 142.86"
```

## Phone control
```
"Phone lock karo"          ✅
"Torch on karo"            ✅
"Flashlight band karo"     ✅
"Volume badhao"            ✅
"Volume kam karo"          ✅
"Silent karo"              ✅
"Brightness badhao"        ✅
"WiFi on karo"             ✅
"Bluetooth kholo"          ✅
```

## Call / Message
```
"Papa ko call lagao"
"Ramesh ko call karo"
"Mummy ko message bhejo aa raha hoon"
"WhatsApp pe Rahul ko hello"
```

## Aur
```
"Battery kitni hai"
"Time kya hua"
"Aaj kaunsi date hai"
"Subah 7 baje ka alarm laga do"
"10 minute ka timer"
```

## Baat-cheet
```
"Hello IEYE RIS"
"Kaise ho"
"Tum kaun ho"
```

---

# ❌ JO NAHI HO SAKTA — saaf baat

## Phone UNLOCK — namumkin

Ye Android ki **core security** hai. Koi bhi app lock screen
unlock **nahi kar sakta** — Google Assistant bhi nahi. Agar kar
pata to koi bhi virus aapka phone khol leta.

**Lock ✅ hota hai. Unlock ❌ nahi.**

Bologe to IEYE RIS saaf mana kar dega, jhooth nahi bolega.

## Wake word — battery khayega

Asli "always-on" (jaise "Hey Google") ke liye phone ke chip ka
special hardware chahiye, jo sirf Google/Samsung ko milta hai.

IEYE RIS ka wake word **kaam karta hai** par baar-baar mic on-off
karke — isliye:
- Battery zyada khatam hoti hai
- Notification hamesha dikhega (Android ka niyam, hata nahi sakte)

Battery bachani ho to switch band karke sirf **mic button**
use karo.

---

# ⚙️ SETTINGS

Upar right corner me **⚙** dabao.

| Kya | Kaam |
|---|---|
| **Groq key** | Dimaag — pehle se bhari hai |
| **Cloudflare** | Backup dimaag |
| **Sarvam key** | Indian awaaz |
| **Sarvam voice** | Band karo to Android ki awaaz (offline chalti hai) |
| **Wake word** | "IEYE RIS" ki jagah kuch aur bhi rakh sakte ho |

**Saari keys ENCRYPTED store hoti hain** — Android ke hardware
keystore me. Koi doosri app padh nahi sakti.

---

# 🧠 KAISE KAAM KARTA HAI

```
Aap bolo
   ↓
1. LOCAL match     ← 0 ms, bina internet
   "torch on karo" jaise 60+ pattern
   ↓ (na mile to)
2. AI se poochho   ← Groq (0.2s) → Cloudflare (backup)
   ↓
3. Kaam karo + Hinglish me jawab bolo
```

**Local pehle** isliye ki "flashlight on" ke liye AI ko poochhna
bewakoofi hai — 2 second waste aur data bhi.

---

# 🔧 PEHLI BAAR — permissions

App khulte hi maangega:
- 🎤 **Microphone** — bolne ke liye (zaroori)
- 📞 **Phone** — call lagane ke liye
- 👤 **Contacts** — "Papa ko call" samajhne ke liye
- 📷 **Camera** — torch ke liye

Phone lock ke liye **alag** permission maangega jab pehli baar
"lock karo" bologe — ek baar deni hai, phir hamesha chalega.

---

# 🐛 Dikkat aaye to

**"Voice service nahi hai"**
Play Store se **Google** app install karo (Speech Services).

**Wake word kaam nahi kar raha**
Settings → Apps → IEYE RIS → Battery → **Unrestricted** karo.
Warna Android background me band kar deta hai.

**Build fail ho gaya**
Actions tab me red ❌ pe click karke error dekho. Aksar
`.github/workflows/build.yml` upload nahi hua hota.

**APK install nahi ho rahi**
Settings → Security → **Unknown sources** allow karo.

---

# 📂 FILE STRUCTURE

```
ieyeris/
├── .github/workflows/build.yml   ← APK yahin se banti hai
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ravanx/ieyeris/
│       │   ├── MainActivity.kt    ← chat UI + mic
│       │   ├── Brain.kt           ← command samajhna
│       │   ├── Actions.kt         ← asli kaam
│       │   ├── Voice.kt           ← Sarvam + Android awaaz
│       │   ├── Keys.kt            ← encrypted keys
│       │   ├── WakeService.kt     ← "IEYE RIS" sunna
│       │   ├── SettingsActivity.kt
│       │   ├── LockAdmin.kt
│       │   └── BootReceiver.kt
│       └── res/                   ← UI + colors
├── build.gradle.kts
└── settings.gradle.kts
```

---

**Contact:** @UjjawalXsarkar · **Channel:** @officialxujjawal
