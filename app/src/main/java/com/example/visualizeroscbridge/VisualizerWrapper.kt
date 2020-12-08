package com.example.visualizeroscbridge

import android.media.audiofx.Visualizer
import android.util.Log

class VisualizerWrapper(
    val captureSize: Int = 1024,
    val onWaveformData: (waveform: ByteArray?, samplingRate: Int) -> Unit = { _: ByteArray?, _: Int -> },
    val onFftData: (fft: ByteArray?, samplingRate: Int) -> Unit = { _: ByteArray?, _: Int -> }
) {

    private val logTag = this.javaClass.name
    private var vis: Visualizer? = null

    fun init() {
        try {
            Log.d(logTag, "Starting visualizer init")
            vis = Visualizer(0).apply {
                enabled = false
                captureSize = this@VisualizerWrapper.captureSize
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                measurementMode = Visualizer.MEASUREMENT_MODE_NONE
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        this@VisualizerWrapper.onFftData(fft, samplingRate)
                    }

                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        this@VisualizerWrapper.onWaveformData(waveform, samplingRate)
                    }

                }, Visualizer.getMaxCaptureRate(), true, true)
            }
            Log.d(logTag, "Visualizer initialized")
        } catch (se: SecurityException) {
            Log.e(logTag, "Error setting up visualizer due to security exception")
            throw(se)
        }
    }

    fun destroy() {
        stop()
        Log.d(logTag, "Destroying visualizer")
        vis?.release()
        vis = null
    }

    fun start() {
        Log.d(logTag, "Starting visualizer")
        vis?.enabled = true
    }

    fun stop() {
        Log.d(logTag, "Stopping visualizer")
        vis?.enabled = false
    }

}