# PhoneRemind Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** App Android per telefono che legge Google Calendar via ContentObserver (push), chiama Gemini 5 minuti prima di ogni evento, e invia una notifica che la watch app legge ad alta voce via TTS.

**Architecture:** Due moduli Gradle nello stesso repo — `:phone` (nuova app telefono con Foreground Service + ContentObserver + AlarmManager + Gemini + notifica) e `:app` (watch app semplificata con NotificationListenerService + TTS). Il ContentObserver notifica l'app in real-time ad ogni modifica del calendario, senza polling.

**Tech Stack:** Kotlin 1.9.22, Android SDK 34, CalendarContract, AlarmManager, Foreground Service, ContentObserver, NotificationListenerService, Gemini 1.5 Flash REST API, OkHttp 4.12, Android TextToSpeech, GitHub Actions

---

### Task 1: Registra il modulo `:phone` in Gradle

**Files:**
- Modify: `settings.gradle.kts` (riga 16)
- Create: `phone/build.gradle.kts`

**Step 1: Aggiungi `:phone` a settings.gradle.kts**

Sostituisci riga 16:
```
include(":app")
```
con:
```
include(":app")
include(":phone")
```

**Step 2: Crea `phone/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.npal.phoneremind"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.npal.phoneremind"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"${System.getenv("GEMINI_API_KEY") ?: ""}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

**Step 3: Verifica che Gradle riconosca il modulo**

```bash
gradle projects
```
Expected output: contiene `:phone` nella lista.

**Step 4: Commit**

```bash
git add settings.gradle.kts phone/build.gradle.kts
git commit -m "feat: add :phone gradle module"
```

---

### Task 2: Risorse della phone app (manifest, layout, strings)

**Files:**
- Create: `phone/src/main/AndroidManifest.xml`
- Create: `phone/src/main/res/layout/activity_main.xml`
- Create: `phone/src/main/res/values/strings.xml`

**Step 1: Crea `phone/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.READ_CALENDAR" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:theme="@style/Theme.AppCompat">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".ReminderService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />

        <receiver
            android:name=".AlarmReceiver"
            android:exported="false" />

        <receiver
            android:name=".BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

**Step 2: Crea `phone/src/main/res/layout/activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp"
    android:gravity="center">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/app_name"
        android:textSize="24sp"
        android:textStyle="bold"
        android:layout_marginBottom="32dp" />

    <Button
        android:id="@+id/startButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Attiva"
        android:layout_marginBottom="16dp" />

    <TextView
        android:id="@+id/statusText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="In attesa..."
        android:textColor="#888888"
        android:gravity="center" />

</LinearLayout>
```

**Step 3: Crea `phone/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">PhoneRemind</string>
</resources>
```

**Step 4: Commit**

```bash
git add phone/src/main/
git commit -m "feat: add phone app resources and manifest"
```

---

### Task 3: GeminiApiClient.kt (phone)

**Files:**
- Create: `phone/src/main/java/com/npal/phoneremind/GeminiApiClient.kt`

**Step 1: Crea il file** (copia identica dalla watch app, solo package diverso)

```kotlin
package com.npal.phoneremind

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

    fun generateAnnouncement(
        apiKey: String,
        title: String,
        time: String,
        location: String
    ): String {
        val locationLine = if (location.isNotBlank()) "\nLuogo: $location" else ""
        val prompt = """Sei un assistente vocale su smartphone.
Genera un promemoria vocale in italiano, naturale e conciso (massimo 2 frasi brevi),
per questo appuntamento che inizia tra 5 minuti:
Titolo: $title
Ora: $time$locationLine
Inizia con "Attenzione" o simile. Rispondi solo con il testo vocale, nient'altro."""

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

**Step 2: Commit**

```bash
git add phone/src/main/java/com/npal/phoneremind/GeminiApiClient.kt
git commit -m "feat: add GeminiApiClient for phone app"
```

---

### Task 4: NotificationSender.kt

**Files:**
- Create: `phone/src/main/java/com/npal/phoneremind/NotificationSender.kt`

**Step 1: Crea il file**

```kotlin
package com.npal.phoneremind

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat

object NotificationSender {

    private const val CHANNEL_ID = "phone_remind_events"

    fun createChannel(context: android.content.Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Promemoria Appuntamenti",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Annunci vocali promemoria appuntamenti"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun send(context: android.content.Context, eventId: Long, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setContentTitle("Promemoria")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify((2000 + eventId).toInt(), notification)
    }
}
```

**Step 2: Commit**

```bash
git add phone/src/main/java/com/npal/phoneremind/NotificationSender.kt
git commit -m "feat: add NotificationSender"
```

---

### Task 5: CalendarPoller.kt

**Files:**
- Create: `phone/src/main/java/com/npal/phoneremind/CalendarPoller.kt`

**Step 1: Crea il file**

```kotlin
package com.npal.phoneremind

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import java.util.concurrent.TimeUnit

object CalendarPoller {

    private const val TAG = "PhoneRemind"

    data class CalendarEvent(
        val id: Long,
        val title: String,
        val startTime: Long,
        val location: String
    )

    fun getUpcomingEvents(context: Context): List<CalendarEvent> {
        val now = System.currentTimeMillis()
        val weekFromNow = now + TimeUnit.DAYS.toMillis(7)

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.EVENT_LOCATION
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? " +
            "AND ${CalendarContract.Events.DTSTART} <= ? " +
            "AND ${CalendarContract.Events.DELETED} = 0"
        val selectionArgs = arrayOf(now.toString(), weekFromNow.toString())

        val cursor = try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection, selection, selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "No calendar permission: ${e.message}")
            return emptyList()
        } ?: return emptyList()

        val events = mutableListOf<CalendarEvent>()
        cursor.use {
            while (it.moveToNext()) {
                events.add(CalendarEvent(
                    id = it.getLong(0),
                    title = it.getString(1) ?: "Appuntamento",
                    startTime = it.getLong(2),
                    location = it.getString(3) ?: ""
                ))
            }
        }
        Log.d(TAG, "Found ${events.size} upcoming events")
        return events
    }

    fun scheduleAlarms(context: Context, events: List<CalendarEvent>) {
        val now = System.currentTimeMillis()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        for (event in events) {
            val triggerAt = event.startTime - TimeUnit.MINUTES.toMillis(5)
            if (triggerAt <= now) continue

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("event_id", event.id)
                putExtra("event_title", event.title)
                putExtra("event_start", event.startTime)
                putExtra("event_location", event.location)
            }
            val pending = PendingIntent.getBroadcast(
                context, event.id.toInt(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            Log.d(TAG, "Alarm set for: ${event.title} at triggerAt=$triggerAt")
        }
    }
}
```

**Step 2: Commit**

```bash
git add phone/src/main/java/com/npal/phoneremind/CalendarPoller.kt
git commit -m "feat: add CalendarPoller with exact alarm scheduling"
```

---

### Task 6: AlarmReceiver.kt (phone)

**Files:**
- Create: `phone/src/main/java/com/npal/phoneremind/AlarmReceiver.kt`

**Step 1: Crea il file**

```kotlin
package com.npal.phoneremind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra("event_id", -1)
        val title = intent.getStringExtra("event_title") ?: return
        val startTime = intent.getLongExtra("event_start", 0)
        val location = intent.getStringExtra("event_location") ?: ""
        val timeStr = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date(startTime))

        // Verify event still exists (not deleted/modified)
        if (!eventExists(context, eventId)) {
            Log.d("PhoneRemind", "Event $eventId no longer exists, skipping")
            return
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val text = if (apiKey.isNotBlank()) {
                    GeminiApiClient.generateAnnouncement(apiKey, title, timeStr, location)
                } else {
                    "Attenzione, tra 5 minuti hai: $title alle $timeStr"
                }
                NotificationSender.send(context, eventId, text)
                Log.d("PhoneRemind", "Notification sent for: $title")
            } catch (e: Exception) {
                Log.e("PhoneRemind", "Error generating announcement: ${e.message}")
                NotificationSender.send(context, eventId, "Tra 5 minuti hai: $title alle $timeStr")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun eventExists(context: Context, eventId: Long): Boolean {
        if (eventId < 0) return false
        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            arrayOf(CalendarContract.Events._ID),
            "${CalendarContract.Events._ID} = ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf(eventId.toString()), null
        ) ?: return false
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }
}
```

**Step 2: Commit**

```bash
git add phone/src/main/java/com/npal/phoneremind/AlarmReceiver.kt
git commit -m "feat: add AlarmReceiver with Gemini call and event verification"
```

---

### Task 7: ReminderService.kt (Foreground Service + ContentObserver)

**Files:**
- Create: `phone/src/main/java/com/npal/phoneremind/ReminderService.kt`

**Step 1: Crea il file**

```kotlin
package com.npal.phoneremind

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderService : Service() {

    companion object {
        private const val TAG = "PhoneRemind"
        private const val FOREGROUND_ID = 1
        private const val CHANNEL_SERVICE = "phone_remind_service"
    }

    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            Log.d(TAG, "Calendar changed, rescanning...")
            rescan()
        }
    }

    override fun onCreate() {
        super.onCreate()
        contentResolver.registerContentObserver(
            CalendarContract.Events.CONTENT_URI,
            true,
            calendarObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(FOREGROUND_ID, buildForegroundNotification())
        rescan()
        return START_STICKY
    }

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(calendarObserver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun rescan() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val events = CalendarPoller.getUpcomingEvents(this@ReminderService)
                CalendarPoller.scheduleAlarms(this@ReminderService, events)
            } catch (e: Exception) {
                Log.e(TAG, "Rescan error: ${e.message}")
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_SERVICE, "Servizio Promemoria", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setContentTitle("PhoneRemind")
            .setContentText("Monitoraggio calendario attivo")
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setOngoing(true)
            .build()
    }
}
```

**Step 2: Commit**

```bash
git add phone/src/main/java/com/npal/phoneremind/ReminderService.kt
git commit -m "feat: add ReminderService with ContentObserver push calendar monitoring"
```

---

### Task 8: BootReceiver.kt (phone)

**Files:**
- Create: `phone/src/main/java/com/npal/phoneremind/BootReceiver.kt`

**Step 1: Crea il file**

```kotlin
package com.npal.phoneremind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ContextCompat.startForegroundService(context, Intent(context, ReminderService::class.java))
        }
    }
}
```

**Step 2: Commit**

```bash
git add phone/src/main/java/com/npal/phoneremind/BootReceiver.kt
git commit -m "feat: add BootReceiver to restart service after reboot"
```

---

### Task 9: MainActivity.kt (phone) + prima build

**Files:**
- Create: `phone/src/main/java/com/npal/phoneremind/MainActivity.kt`

**Step 1: Crea il file**

```kotlin
package com.npal.phoneremind

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val requestCalendar = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startService()
        else statusText.text = "Permesso calendario negato"
    }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        checkCalendarAndStart()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        NotificationSender.createChannel(this)

        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkCalendarAndStart()
            }
        }
    }

    private fun checkCalendarAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            == PackageManager.PERMISSION_GRANTED) {
            startService()
        } else {
            requestCalendar.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    private fun startService() {
        ContextCompat.startForegroundService(this, Intent(this, ReminderService::class.java))
        statusText.text = "Attivo — monitoraggio calendario in corso"
    }
}
```

**Step 2: Build di verifica**

```bash
gradle :phone:assembleDebug --info 2>&1 | tail -50
```
Expected: `BUILD SUCCESSFUL`

**Step 3: Commit**

```bash
git add phone/src/main/java/com/npal/phoneremind/MainActivity.kt
git commit -m "feat: add MainActivity, phone app complete"
```

---

### Task 10: Semplifica la watch app

Rimuovi tutto il codice calendario dalla watch, aggiungi NotificationListenerService.

**Files:**
- Delete: `app/src/main/java/com/npal/watchremind/CalendarAlarmScheduler.kt`
- Delete: `app/src/main/java/com/npal/watchremind/AlarmReceiver.kt`
- Delete: `app/src/main/java/com/npal/watchremind/BootReceiver.kt`
- Delete: `app/src/main/java/com/npal/watchremind/GeminiApiClient.kt`
- Replace: `app/src/main/java/com/npal/watchremind/MainActivity.kt`
- Replace: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/npal/watchremind/NotificationListener.kt`

**Step 1: Elimina i file non più necessari**

```bash
git rm app/src/main/java/com/npal/watchremind/CalendarAlarmScheduler.kt
git rm app/src/main/java/com/npal/watchremind/AlarmReceiver.kt
git rm app/src/main/java/com/npal/watchremind/BootReceiver.kt
git rm app/src/main/java/com/npal/watchremind/GeminiApiClient.kt
```

**Step 2: Sostituisci `app/src/main/java/com/npal/watchremind/MainActivity.kt`**

```kotlin
package com.npal.watchremind

import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.statusText).text = "WatchRemind attivo"
    }
}
```

**Step 3: Crea `app/src/main/java/com/npal/watchremind/NotificationListener.kt`**

```kotlin
package com.npal.watchremind

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != "com.npal.phoneremind") return
        val text = sbn.notification.extras.getString(Notification.EXTRA_TEXT) ?: return
        TtsAnnouncer.speak(applicationContext, text)
    }
}
```

**Step 4: Sostituisci `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.WAKE_LOCK" />

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

        <service
            android:name=".NotificationListener"
            android:label="WatchRemind"
            android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.service.notification.NotificationListenerService" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

**Step 5: Build di verifica watch**

```bash
gradle :app:assembleDebug --info 2>&1 | tail -50
```
Expected: `BUILD SUCCESSFUL`

**Step 6: Commit**

```bash
git add app/src/main/
git commit -m "feat: simplify watch app — remove calendar, add NotificationListenerService"
```

---

### Task 11: Aggiorna GitHub Actions

**Files:**
- Replace: `.github/workflows/build.yml`

**Step 1: Sostituisci `.github/workflows/build.yml`**

```yaml
name: Build APKs

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.6'

      - name: Build Phone APK
        run: gradle :phone:assembleDebug --info 2>&1 | tail -100
        env:
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}

      - name: Build Watch APK
        run: gradle :app:assembleDebug --info 2>&1 | tail -100
        env:
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}

      - name: Upload Phone APK
        uses: actions/upload-artifact@v4
        with:
          name: PhoneRemind-APK
          path: phone/build/outputs/apk/debug/phone-debug.apk
          retention-days: 30

      - name: Upload Watch APK
        uses: actions/upload-artifact@v4
        with:
          name: WatchRemind-APK
          path: app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30
```

**Step 2: Commit e push**

```bash
git add .github/workflows/build.yml
git commit -m "ci: build both phone and watch APKs"
git push
```

---

### Task 12: Installa e testa

**Step 1: Scarica gli APK da GitHub Actions**

Vai su: `https://github.com/fpallaro/WatchRemind/actions` → ultimo run → Artifacts:
- `PhoneRemind-APK` → installa sul telefono
- `WatchRemind-APK` → installa sull'orologio

**Step 2: Installa sul telefono via ADB**

```bash
C:\Users\synclab\Downloads\adb\adb devices
# Se non connesso, connetti il telefono via USB o wireless
C:\Users\synclab\Downloads\adb\adb install -r PhoneRemind-APK/phone-debug.apk
```

**Step 3: Installa sulla watch via ADB**

```bash
C:\Users\synclab\Downloads\adb\adb uninstall com.npal.watchremind
C:\Users\synclab\Downloads\adb\adb install WatchRemind-APK/app-debug.apk
```

**Step 4: Abilita Notification Access sull'orologio (una tantum)**

Sul Galaxy Watch: **Settings → Accessibility → Notification Access → WatchRemind → ON**

**Step 5: Apri PhoneRemind sul telefono**

- Tocca **Attiva**
- Concedi i permessi: Notifiche → Calendario
- Stato: "Attivo — monitoraggio calendario in corso"

**Step 6: Test end-to-end**

1. Aggiungi un evento su Google Calendar che inizia tra **7 minuti**
2. Aspetta: il ContentObserver rileva la modifica → riscansiona → imposta allarme
3. Dopo 2 minuti (a -5min dall'evento): l'AlarmReceiver si attiva → chiama Gemini → invia notifica
4. L'orologio riceve la notifica → NotificationListenerService → TTS legge il testo ad alta voce

**Step 7: Verifica logcat (opzionale)**

```bash
C:\Users\synclab\Downloads\adb\adb logcat -s PhoneRemind
```
Expected lines:
```
D/PhoneRemind: Calendar changed, rescanning...
D/PhoneRemind: Found 1 upcoming events
D/PhoneRemind: Alarm set for: [titolo evento]
D/PhoneRemind: Notification sent for: [titolo evento]
```
