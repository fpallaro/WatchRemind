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
        if (granted) startReminderService()
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
            startReminderService()
        } else {
            requestCalendar.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    private fun startReminderService() {
        ContextCompat.startForegroundService(this, Intent(this, ReminderService::class.java))
        statusText.text = "Attivo — monitoraggio calendario in corso"
    }
}
