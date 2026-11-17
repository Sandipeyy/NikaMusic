// Adapted from Echo Music (GPL-3.0, https://github.com/iad1tya/Echo-Music) for NikaMusic.
package com.sandip.nikamusic.recognition

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat

/** Transparent trampoline used by the Quick Settings tile: asks for the mic once, then starts the listener service. */
class RecognitionLaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (hasRecordPermission()) {
            startRecognitionService()
            finish()
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO && hasRecordPermission()) startRecognitionService()
        finish()
    }

    private fun hasRecordPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun startRecognitionService() {
        val intent = Intent(this, RecognitionForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (_: Exception) {
            // Foreground start can be refused by the system (e.g. app in background restrictions); nothing to recover here.
        }
    }

    private companion object {
        const val REQUEST_RECORD_AUDIO = 4107
    }
}
