# PhoneRemind — Design Document

**Goal:** App Android per telefono che legge Google Calendar, chiama Gemini per generare un annuncio vocale naturale in italiano, e lo fa leggere automaticamente dall'orologio Galaxy Watch 5 Pro 5 minuti prima di ogni appuntamento.

**Architecture:** Approccio B — repo unico con due moduli Gradle (`:phone` nuovo, `:app` watch semplificato). Nessun refactoring al build system esistente.

**Tech Stack:** Kotlin, Android SDK 34, CalendarContract, AlarmManager, Foreground Service, NotificationListenerService, Gemini 1.5 Flash REST API, Android TextToSpeech, GitHub Actions

---

## Struttura repo

```
GalaxyWatchApp/
├── phone/                          ← NUOVO modulo
│   └── src/main/java/com/npal/phoneremind/
│       ├── MainActivity.kt
│       ├── ReminderService.kt      (Foreground Service)
│       ├── CalendarPoller.kt       (legge CalendarContract)
│       ├── AlarmReceiver.kt        (BroadcastReceiver, -5min)
│       ├── GeminiApiClient.kt
│       └── NotificationSender.kt
│   └── src/main/AndroidManifest.xml
│   └── build.gradle.kts
├── app/                            ← watch app SEMPLIFICATA
│   └── src/main/java/com/npal/watchremind/
│       ├── NotificationListener.kt  (NUOVO)
│       └── TtsAnnouncer.kt          (invariato)
│   └── (rimosso: CalendarAlarmScheduler, BootReceiver, AlarmReceiver, GeminiApiClient, MainActivity semplificata)
└── .github/workflows/build.yml     (builda entrambi)
```

---

## Flusso dati

```
[Telefono — avvio ReminderService]
ReminderService (Foreground Service, avvio automatico al boot)
  → CalendarPoller.schedule() legge CalendarContract prossimi 7 giorni
  → Per ogni evento: AlarmManager.setExactAndAllowWhileIdle(dtstart - 5min)
  → WorkManager job ogni 6h per ricaricare nuovi eventi

[Telefono — al trigger AlarmReceiver]
AlarmReceiver.onReceive()
  → Legge event_title, event_start, event_location dagli extras
  → GeminiApiClient.generateAnnouncement(title, timeStr, location)
  → fallback: testo fisso se Gemini non risponde
  → NotificationSender.send(testo) — notifica ad alta priorità

[Samsung bridge automatico]
  → La notifica del telefono appare sull'orologio

[Orologio — NotificationListenerService]
NotificationListener.onNotificationPosted(sbn)
  → Filtra: sbn.packageName == "com.npal.phoneremind"
  → TtsAnnouncer.speak(context, testo)
```

---

## Permessi

### Phone app (`com.npal.phoneremind`)
- `READ_CALENDAR`
- `INTERNET`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_DATA_SYNC`
- `RECEIVE_BOOT_COMPLETED`
- `POST_NOTIFICATIONS` (Android 13+)
- `USE_EXACT_ALARM`
- `WAKE_LOCK`

### Watch app (`com.npal.watchremind`)
- `BIND_NOTIFICATION_LISTENER_SERVICE`

**Setup una tantum orologio:** Settings → Accessibility → Notification Access → WatchRemind → ON

---

## Error handling

- **Gemini fallisce / no internet:** fallback a testo fisso `"Tra 5 minuti hai: $title alle $timeStr"`
- **Nessun evento nel calendario:** il servizio rimane attivo, riprogramma ogni 6h
- **Orologio non connesso al momento della notifica:** Samsung ritrasmette la notifica appena l'orologio torna in range

---

## Aggiornamento calendario

- All'avvio di `ReminderService`: pianifica allarmi per i prossimi 7 giorni
- Ogni 6 ore (WorkManager): re-scan calendario per nuovi/modificati eventi
- Al boot (BootReceiver sulla phone app): riavvia il servizio

---

## GitHub Action

```yaml
- name: Build Phone APK
  run: gradle :phone:assembleDebug

- name: Build Watch APK
  run: gradle :app:assembleDebug
```

Entrambi gli APK caricati come artefatti separati.

---

## Feature future (fuori scope ora)
- Calcolo tempo di viaggio con Google Maps Directions API
- Buffer dinamico basato su distanza/traffico
