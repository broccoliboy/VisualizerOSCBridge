package com.example.visualizeroscbridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {

    private val logTag = this.javaClass.name
    private val audioPermissionRequestCode = 101
    private val enabledSwitch by lazy { findViewById<Switch>(R.id.enabled_switch) }
    private var mService: VisualizerOscBridgeService? = null

    // Defines callbacks for service binding, passed to bindService()
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            Log.d(logTag, "Bound service connected")
            val binder = service as VisualizerOscBridgeService.LocalBinder
            mService = binder.getService()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            // Only gets called when remote service crashes. Does not get called from unbindService().
            Log.d(logTag, "Bound service crashed")
            mService = null
        }
    }

    private fun bind() {
        Intent(this, VisualizerOscBridgeService::class.java).also {
            bindService(it, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun unbind() {
        if (mService != null) {
            unbindService(connection)
            mService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ensurePermissionAllowed()
    }

    override fun onStart() {
        super.onStart()
        Log.d(logTag, "Main activity started")
        // bind to service
        if (enabledSwitch.isChecked) {
            startVisualizerOscBridgeService()
            bind()
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(logTag, "Main activity stopped")
        unbind()
    }

    fun onEnabledSwitchClicked(view: View) {
        if (view is Switch) {
            if (view.isChecked) {
                startVisualizerOscBridgeService()
            } else {
                stopVisualizerOscBridgeService()
            }
        }
    }

    private fun startVisualizerOscBridgeService() {
        Log.d(logTag, "Sending intent to start service in foreground")
        Intent(this, VisualizerOscBridgeService::class.java).also {
            startForegroundService(it)
        }
    }

    private fun stopVisualizerOscBridgeService() {
        Log.d(logTag, "Sending intent to stop service")
        unbind()
        Intent(this, VisualizerOscBridgeService::class.java).also {
            stopService(it)
        }
    }

    private fun ensurePermissionAllowed() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVisualizerOscBridgeService()
        }
        else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.MODIFY_AUDIO_SETTINGS
                ),
                audioPermissionRequestCode
            )
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        when (requestCode) {
            audioPermissionRequestCode -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startVisualizerOscBridgeService()
                } else {
                    Toast.makeText(
                        this,
                        "Visualizer OSC Bridge requires RECORD_AUDIO permissions to use Android Visualizer API",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

}