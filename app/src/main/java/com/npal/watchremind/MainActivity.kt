package com.npal.watchremind

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {

    private lateinit var statusText: TextView

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

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED) {
                activateReminders()
            } else {
                requestPermission.launch(Manifest.permission.READ_CALENDAR)
            }
        }

        // Auto-activate on first launch if permission already granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
            == PackageManager.PERMISSION_GRANTED) {
            activateReminders()
        } else {
            statusText.text = "Tocca Attiva per iniziare"
        }
    }

    private fun activateReminders() {
        val count = CalendarAlarmScheduler.scheduleUpcoming(this)
        statusText.text = "Attivo — $count promemoria"
    }
}
