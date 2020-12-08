package com.example.visualizeroscbridge

import android.util.Log
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo


class NetworkServiceDiscovery(
    val context: Context,
    var serviceName: String = "VisualizerOscBridge",
    var serviceType: String = "_visosc._udp"
) {

    val logTag = this.javaClass.name
    var nsdManager: NsdManager? = null

    private val registrationListener = object : NsdManager.RegistrationListener {

        override fun onServiceRegistered(info: NsdServiceInfo) {
            // Save the service name. Android may have changed it in order to
            // resolve a conflict, so update the name you initially requested
            // with the name Android actually used.
            serviceName = info.serviceName
            Log.d(logTag, "Registered service $serviceName ($serviceType)")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Registration failed! Put debugging code here to determine why.
            Log.d(logTag, "Registration failed for service $serviceName ($serviceType)")
        }

        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
            // Service has been unregistered. This only happens when you call
            // NsdManager.unregisterService() and pass in this listener.
            Log.d(logTag, "Unregistered service $serviceName ($serviceType)")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            // Unregistration failed. Put debugging code here to determine why.
            Log.d(logTag, "Unregistration failed for service $serviceName ($serviceType)")
        }
    }

    fun start(port: Int) {
        registerService(port)
    }

    fun stop() {
        unregisterService()
    }

    private fun registerService(port: Int) {

        val self = this

        // Create the NsdServiceInfo object, and populate it.
        val serviceInfo = NsdServiceInfo().apply {
            // The name is subject to change based on conflicts
            // with other services advertised on the same network.
            this.serviceName = self.serviceName
            this.serviceType = self.serviceType
            this.port = port
        }

        nsdManager = (context.getSystemService(Context.NSD_SERVICE) as NsdManager).apply {
            registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }

    }

    private fun unregisterService() {
        nsdManager?.apply {
            unregisterService(registrationListener)
        }
    }

}
