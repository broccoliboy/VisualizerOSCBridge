package com.example.visualizeroscbridge


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.audiofx.Visualizer
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.lang.Integer.max
import java.util.*


class VisualizerOscBridgeService : Service() {

    // android service and notification
    private var serviceIsRunning = false
    private val logTag = this.javaClass.name
    private val notificationChannelId = "VisualizerOscBridgeNotificationChannel"
    private val notificationChannelName = "Visualizer OSC Bridge"

    private var serviceLooper: Looper? = null
    private var serviceHandler: ServiceHandler? = null

    private val MESSAGE_TYPE_STOP = 101
    private val MESSAGE_TYPE_START = 102
    private val MESSAGE_TYPE_WAVEFORM_DATA = 201

    private var _oscClientCount = 0
    private var _boundServiceCount = 0

    private fun getClientCount(): Int {
        return _oscClientCount + _boundServiceCount
    }

    // visualizer
    private var mVisualizer: Visualizer? = null

    // Service binding
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        // Return this instance of VisualizerOscBridgeService so clients can call public methods
        fun getService(): VisualizerOscBridgeService = this@VisualizerOscBridgeService
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(logTag, "Activity bound to service")
        _boundServiceCount++
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(logTag, "Activity unbound from service")
        _boundServiceCount = max(0, _boundServiceCount - 1)
        return super.onUnbind(intent)
    }

    // Handler that receives messages from the thread
    private inner class ServiceHandler(looper: Looper) : Handler(looper) {

        var vis: VisualizerWrapper? = null
        var osc: Osc? = null
        var nsd: NetworkServiceDiscovery? = null

        private fun start() {
            vis = VisualizerWrapper(
                onWaveformData = { data: ByteArray?, samplingRate: Int ->
                    osc?.sendWaveformData(data, samplingRate)
                },
                onFftData = { data: ByteArray?, samplingRate: Int ->
                    osc?.sendFftData(data, samplingRate)
                }
            ).apply {
                init()
            }
            nsd = NetworkServiceDiscovery(this@VisualizerOscBridgeService)
            osc = Osc(
                onClientCountChange = {
                    if (it > 0) {
                        vis?.start()
                    } else {
                        vis?.stop()
                    }
                    _oscClientCount = it
                }
            ).apply {
                start()
                nsd?.start(port)
            }
        }

        private fun stop() {
            osc?.stop()
            osc = null
            nsd?.stop()
            nsd = null
            vis?.destroy()
            vis = null
        }

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_TYPE_START -> start()
                MESSAGE_TYPE_STOP -> stop()
//                MESSAGE_TYPE_WAVEFORM_DATA -> {
//                    try { // in case ByteArray conversion fails for some reason
//                        osc?.sendWaveformData(msg.obj as ByteArray)
//                    } catch (e: Exception) {
//                        Log.e(logTag, "Failed to send visualizer to osc clients")
//                    }
//                }
            }
        }

    }

    override fun onCreate() {
        Log.d(logTag, "Creating service")
        // create thread to handle network communication
        HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_AUDIO).apply {
            start()
            // Get the HandlerThread's Looper and use it for our Handler
            serviceLooper = looper
            serviceHandler = ServiceHandler(looper)
        }

    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(logTag, "Service told to start by startService() or startForegroundService()")

        if (!serviceIsRunning) {
            initService(intent)
        }

        return START_STICKY
    }

    private fun initService(intent: Intent) {
        Log.d(logTag, "Initializing service")
        val input = intent.getStringExtra("inputExtra")

        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle(notificationChannelName)
            .setContentText(input)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)

        Toast.makeText(this, "Starting Visualizer OSC Bridge", Toast.LENGTH_SHORT).show()

        serviceHandler?.obtainMessage()?.also {
            it.what = MESSAGE_TYPE_START
            serviceHandler?.sendMessage(it)
        }

        serviceIsRunning = true

//        if (mVisualizer == null) setupVis()

//        serviceHandler?.obtainMessage()?.also {
//            it.what = MESSAGE_TYPE_WAVEFORM_DATA
//            it.obj = waveform
//            serviceHandler?.sendMessage(it)
//        }
//        if (this@VisualizerOscBridgeService.getClientCount() > 0) {
//            enabled = true
//        }

    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            notificationChannelId,
            notificationChannelName,
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(
            NotificationManager::class.java
        )
        manager?.createNotificationChannel(serviceChannel)
    }

    override fun onDestroy() {
        Log.d(logTag, "Destroying service")
        serviceHandler?.obtainMessage()?.also {
            it.what = MESSAGE_TYPE_STOP
            serviceHandler?.sendMessage(it)
        }
        serviceIsRunning = false
        Toast.makeText(this, "Stopping Visualizer OSC Bridge", Toast.LENGTH_SHORT).show()
    }

}