package com.example.visualizeroscbridge

import android.util.Log
import netP5.NetAddress
import netP5.NetAddressList
import oscP5.OscMessage
import oscP5.OscP5
import java.io.IOException
import java.net.SocketException
import java.util.*
import kotlin.concurrent.timerTask




class Osc(
    val onClientConnect: (clientCount: Int) -> Unit = {},
    val onClientDisconnect: (clientCount: Int) -> Unit = {},
    val onClientCountChange: (clientCount: Int) -> Unit = {}
) {

    var port: Int = 32123

//    private val portSearchStart = 32123  // start searching for an open port here
//    private val maxNumPortSearch = 100  // number of ports to try before giving up
    private val logTag = this.javaClass.name
    private val addressClientConnect = "/server/connect"
    private val addressClientDisconnect = "/server/disconnect"
    private val addressWaveform = "/wav"
    private val addressFft = "/fft"

    private var osc: OscP5? = null
    private val clientList = NetAddressList()
    private val clientDisconnectTimeout: Long = 5000

    private inner class TimedNetAddress(
        netAddress: NetAddress
    ) : NetAddress(netAddress) {
        var lastAliveTime: Long = System.currentTimeMillis()
    }

    private val lock = Object()
    private var timer = Timer()

    fun start(_port: Int = port) {
        port = _port
        if (osc == null) {
            Log.d(logTag, "Starting OSC")
            synchronized(lock) {
//                This doesn't work because OscP5 library does not provide any way to verify the chosen
//                port actually connected.
//                if (port <= 0) {
//                    // search for open port
//                    port = portSearchStart
//                    while (port < portSearchStart + maxNumPortSearch) {
//                        try {
//                            osc = OscP5(this, port)
//                            Log.d(logTag, "osc sendStatus: " + osc?.properties()?.sendStatus())
//                            break
//                        } catch (e: IOException) {
//                            Log.d(logTag, "Port $port in use. Trying next port.")
//                            port++
//                        }
//                    }
//                    if (osc == null) {
//                        throw SocketException("Unable to find open port to bind to between the range of $portSearchStart and ${portSearchStart + maxNumPortSearch}")
//                    }
//                } else {
//                    // open given port
//                    osc = OscP5(this, port)
//                }
                osc = OscP5(this, port)
            }
            timer.scheduleAtFixedRate(
                timerTask {
                    synchronized(lock) {
                        val toRemove = ArrayList<Any>()
                        clientList.list().forEach{
                            if (System.currentTimeMillis() - (it as TimedNetAddress).lastAliveTime > clientDisconnectTimeout) {
                                Log.d(logTag, "Client ${it.address()} timed out. Disconnecting.")
                                toRemove.add(it)
                            }
                        }
                        clientList.list().removeAll(toRemove)
                        if (toRemove.size > 0) {
                            onClientCountChange(clientList.list().size)
                            onClientDisconnect(clientList.list().size)
                        }
                    }
                },
                clientDisconnectTimeout,
                clientDisconnectTimeout
            )
        } else {
            Log.d(logTag, "OSC already running")
        }
    }

    fun stop() {
        synchronized(lock) {
            Log.d(logTag,"Stopping OSC")
            osc?.dispose()
            osc = null
            timer.cancel()
            clientList.list().clear()
        }
    }

    fun sendWaveformData(data: ByteArray?, samplingRate: Int) {
        synchronized(lock) {
            osc?.send(
                OscMessage(addressWaveform).apply {
                    add(data)
                    add(samplingRate)
                },
                clientList
            )
        }
    }

    fun sendFftData(data: ByteArray?, samplingRate: Int) {
        synchronized(lock) {
            osc?.send(
                OscMessage(addressFft).apply {
                    add(data)
                    add(samplingRate)
                },
                clientList
            )
        }
    }

    private fun oscEvent(m: OscMessage) {
        when (m.addrPattern()) {
            addressClientConnect -> connectOscClient(m.netAddress())
            addressClientDisconnect -> disconnectOscClient(m.netAddress())
        }
    }

    private fun connectOscClient(netAddress: NetAddress) {
        synchronized(lock) {
            var clientInList = false
            clientList.list().forEach {
                val c = it as TimedNetAddress
                if (c.address() == netAddress.address()) {
                    clientInList = true
                    c.lastAliveTime = System.currentTimeMillis()
                    return
                }
            }
            if (!clientInList) {
                Log.d(logTag, "Connecting client: ${netAddress.address()}")
                clientList.add(TimedNetAddress(NetAddress(netAddress.address(), port)))
                onClientConnect(clientList.list().size)
                onClientCountChange(clientList.list().size)
            }
        }
    }

    private fun disconnectOscClient(netAddress: NetAddress) {
        synchronized(lock) {
            Log.d(logTag, "Disconnecting client: ${netAddress.address()}")
            clientList.remove(netAddress.address(), port)
            onClientDisconnect(clientList.list().size)
            onClientCountChange(clientList.list().size)
        }
    }

}