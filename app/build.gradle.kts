import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

val BUILD_STAMP: String = SimpleDateFormat("dd MMM yyyy, hh:mm a").also {
    it.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
}.format(Date())

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ravanx.ieyeris"
    compileSdk = 34

    // 📦 v4.2 — assets ko dobara compress mat karo
    //
    // ⚠️ WAV aur .bin pehle se apne roop me hain. AAPT inhe
    //    dobara zip karta hai — usse:
    //      • APK banne me 3-4 minute extra
    //      • WAV chalane se pehle unzip hoti hai (awaaz late)
    //    noCompress se dono theek ho jate hain.
    androidResources {
        noCompress += listOf("wav", "bin", "json", "txt")
    }

    defaultConfig {
        applicationId = "com.ravanx.ieyeris"
        minSdk = 24
        targetSdk = 34
        versionCode = 460
        versionName = "4.6"
        buildConfigField("String", "BUILD_STAMP", "\"" + BUILD_STAMP + "\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // debug key se sign — taaki GitHub Actions se seedha
            // install ho jaye, koi keystore banane ki zarurat nahi
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Lint release build ko 2-3 minute slow kar deta tha aur kuch
    // rokta bhi nahi tha (sirf warnings). Band — build tez ho gayi.
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
