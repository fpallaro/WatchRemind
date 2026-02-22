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
            if (key.isBlank()) {
                statusText.text = "Inserisci la chiave API"
                return@setOnClickListener
            }
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
        statusText.text = "Attivo — $count promemoria programmati"
    }
}
