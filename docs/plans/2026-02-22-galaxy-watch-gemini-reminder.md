# Galaxy Watch Gemini Voice Reminder — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** App Wear OS per Galaxy Watch 5 Pro che annuncia vocalmente gli appuntamenti del calendario 5 minuti prima, usando Gemini AI per generare frasi naturali in italiano.

**Architecture:** AlarmManager schedula allarmi esatti 5 min prima di ogni evento calendario. Quando scatta, app chiama Gemini 1.5 Flash API → ottiene testo naturale → TextToSpeech lo legge ad alta voce dallo speaker dell'orologio. Internet via Bluetooth del telefono (normale). Build cloud via GitHub Actions → APK pronto da scaricare. Installazione una tantum via ADB con WiFi temporaneo sull'orologio.

**Tech Stack:** Kotlin, Wear OS 4 (minSdk 30), Gemini 1.5 Flash REST API, AlarmManager, CalendarContract, TextToSpeech, OkHttp 4, Gson, GitHub Actions.

---

### Task 1: File di build Gradle

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `app/build.gradle.kts`
- Create: `gradle.properties`

**Step 1: Scrivere `settings.gradle.kts`**
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "WatchRemind"
include(":app")
```

**Step 2: Scrivere `build.gradle.kts` (root)**
```kotlin
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
```

**Step 3: Scrivere `app/build.gradle.kts`**
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.npal.watchremind"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.npal.watchremind"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        debug { isDebuggable = true }
    }
    buildFeatures { viewBinding = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}
dependencies {
    implementation("androidx.wear:wear:1.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

**Step 4: Scrivere `gradle.properties`**
```properties
android.useAndroidX=true
android.enableJetifier=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

---

### Task 2: AndroidManifest.xml

**Files:**
- Create: `app/src/main/AndroidManifest.xml`

**Step 1: Scrivere il manifest con tutti i permessi necessari**
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_CALENDAR" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <uses-feature android:name="android.hardware.type.watch" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@android:style/Theme.DeviceDefault">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver android:name=".AlarmReceiver" android:exported="false" />

        <receiver android:name=".BootReceiver" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

---

### Task 3: MainActivity.kt — Schermata impostazioni chiave API

**Files:**
- Create: `app/src/main/java/com/npal/watchremind/MainActivity.kt`

**Step 1: Scrivere la activity**
```kotlin
package com.npal.watchremind

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private lateinit var statusText: TextView
    private lateinit var apiKeyInput: EditText

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) activateReminders()
        else statusText.text = "Permesso calendario negato"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        apiKeyInput = findViewById(R.id.apiKeyInput)

        val prefs = getSharedPreferences("watchremind", MODE_PRIVATE)
        apiKeyInput.setText(prefs.getString("gemini_api_key", ""))

        if (prefs.getString("gemini_api_key", "")!!.isNotBlank()) {
            statusText.text = "App attiva — premi Salva per aggiornare"
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val key = apiKeyInput.text.toString().trim()
            if (key.isBlank()) { statusText.text = "Inserisci la chiave API"; return@setOnClickListener }
            prefs.edit().putString("gemini_api_key", key).apply()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED) {
                activateReminders()
            } else {
                requestPermission.launch(Manifest.permission.READ_CALENDAR)
            }
        }
    }

    private fun activateReminders() {
        val count = CalendarAlarmScheduler.scheduleUpcoming(this)
        statusText.text = "Attivo — $count promemoria"
    }
}
```

---

### Task 4: CalendarAlarmScheduler.kt — Legge il calendario e schedula allarmi

**Files:**
- Create: `app/src/main/java/com/npal/watchremind/CalendarAlarmScheduler.kt`

**Step 1: Scrivere lo scheduler**
```kotlin
package com.npal.watchremind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.content.getSystemService
import java.util.concurrent.TimeUnit

object CalendarAlarmScheduler {

    fun scheduleUpcoming(context: Context): Int {
        val now = System.currentTimeMillis()
        val weekFromNow = now + TimeUnit.DAYS.toMillis(7)
        val alarmManager = context.getSystemService<AlarmManager>()!!

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.EVENT_LOCATION
            ),
            "${CalendarContract.Events.DTSTART} BETWEEN ? AND ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(now.toString(), weekFromNow.toString()),
            CalendarContract.Events.DTSTART + " ASC"
        ) ?: return 0

        var count = 0
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val title = it.getString(1) ?: "Appuntamento"
                val startTime = it.getLong(2)
                val location = it.getString(3) ?: ""

                val triggerAt = startTime - TimeUnit.MINUTES.toMillis(5)
                if (triggerAt <= now) continue

                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("event_id", id)
                    putExtra("event_title", title)
                    putExtra("event_start", startTime)
                    putExtra("event_location", location)
                }
                val pending = PendingIntent.getBroadcast(
                    context, id.toInt(), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
                count++
            }
        }
        return count
    }
}
```

---

### Task 5: AlarmReceiver.kt — Gestisce lo scatto dell'allarme

**Files:**
- Create: `app/src/main/java/com/npal/watchremind/AlarmReceiver.kt`

**Step 1: Scrivere il receiver**
```kotlin
package com.npal.watchremind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("event_title") ?: return
        val startTime = intent.getLongExtra("event_start", 0)
        val location = intent.getStringExtra("event_location") ?: ""
        val timeStr = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(startTime))

        val prefs = context.getSharedPreferences("watchremind", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = if (apiKey.isNotBlank()) {
                    GeminiApiClient.generateAnnouncement(apiKey, title, timeStr, location)
                } else {
                    "Attenzione, tra 5 minuti hai: $title alle $timeStr"
                }
                TtsAnnouncer.speak(context, text)
            } catch (e: Exception) {
                TtsAnnouncer.speak(context, "Tra 5 minuti hai: $title alle $timeStr")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

---

### Task 6: GeminiApiClient.kt — Chiama Gemini API

**Files:**
- Create: `app/src/main/java/com/npal/watchremind/GeminiApiClient.kt`

**Step 1: Scrivere il client HTTP per Gemini**
```kotlin
package com.npal.watchremind

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun generateAnnouncement(apiKey: String, title: String, time: String, location: String): String {
        val locationLine = if (location.isNotBlank()) "\nLuogo: $location" else ""
        val prompt = """Sei un assistente vocale su uno smartwatch Samsung.
Genera un promemoria vocale in italiano, naturale e conciso (massimo 2 frasi brevi),
per questo appuntamento che inizia tra 5 minuti:
Titolo: $title
Ora: $time$locationLine
Inizia con "Attenzione" o simile. Solo il testo vocale, niente altro."""

        val body = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", prompt)
                ))
            )).toString()

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")

        return JSONObject(responseBody)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
            .trim()
    }
}
```

---

### Task 7: TtsAnnouncer.kt — Legge ad alta voce dallo speaker del watch

**Files:**
- Create: `app/src/main/java/com/npal/watchremind/TtsAnnouncer.kt`

**Step 1: Scrivere il manager TTS**
```kotlin
package com.npal.watchremind

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

object TtsAnnouncer {

    fun speak(context: Context, text: String) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.ITALIAN
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "watchremind_utterance")
                Handler(Looper.getMainLooper()).postDelayed({ tts?.shutdown() }, 15_000)
            }
        }
    }
}
```

---

### Task 8: BootReceiver.kt — Riattiva gli allarmi dopo riavvio

**Files:**
- Create: `app/src/main/java/com/npal/watchremind/BootReceiver.kt`

**Step 1: Scrivere il boot receiver**
```kotlin
package com.npal.watchremind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CalendarAlarmScheduler.scheduleUpcoming(context)
        }
    }
}
```

---

### Task 9: Layout e risorse

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Create: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`

**Step 1: Layout principale (schermata watch)**
Vedi file `activity_main.xml` — LinearLayout verticale centrato, campo testo per API key, bottone Salva, testo stato.

**Step 2: strings.xml e colors.xml**
Vedi file corrispondenti.

---

### Task 10: GitHub Actions — Build automatica APK nel cloud

**Files:**
- Create: `.github/workflows/build.yml`

**Step 1: Configurare il workflow**
```yaml
name: Build APK
on:
  push:
    branches: [ main ]
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '17', distribution: 'temurin' }
      - uses: gradle/actions/setup-gradle@v4
        with: { gradle-version: '8.6' }
      - run: gradle assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: WatchRemind-APK
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
```

**Step 2: Dopo push su GitHub → Actions → Download APK**

---

### Task 11: Installazione via ADB (istruzioni utente)

1. Abilita modalità sviluppatore sul watch: `Impostazioni → Sistema → Info → Versione build` (7 tap)
2. Attiva WiFi temporaneo sul watch (solo per installazione)
3. Attiva ADB Wireless: `Impostazioni → Sviluppatore → Debug ADB via WiFi`
4. Installa ADB sul PC: scarica [Platform Tools](https://developer.android.com/tools/releases/platform-tools)
5. Da terminale PC:
   ```bash
   adb connect <IP-watch>:5555
   adb install app-debug.apk
   ```
6. Apri WatchRemind sul watch → inserisci chiave API Gemini → premi Salva
7. Disattiva WiFi sul watch → da ora usa Bluetooth del telefono

---

### Task 12: Chiave API Gemini (gratuita)

1. Vai su [aistudio.google.com](https://aistudio.google.com)
2. Accedi con il tuo account Google
3. `Get API key` → `Create API key`
4. Copia la chiave e inseriscila nell'app sul watch
