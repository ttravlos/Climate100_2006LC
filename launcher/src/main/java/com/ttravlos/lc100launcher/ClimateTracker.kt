package com.ttravlos.lc100launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.aoe.fytcanbusmonitor.ConnectionObserver
import com.aoe.fytcanbusmonitor.IModuleCallback
import com.aoe.fytcanbusmonitor.IRemoteToolkit
import com.aoe.fytcanbusmonitor.ModuleCodes.MODULE_CODE_CANBUS
import com.aoe.fytcanbusmonitor.MsToolkitConnection
import com.aoe.fytcanbusmonitor.RemoteModuleProxy

/** Reads the same FYT CAN-bus values used by the Climate100 app. */
class ClimateTracker(
    private val context: Context,
    private val onState: (ClimateUiState) -> Unit
) : ConnectionObserver {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val proxy = RemoteModuleProxy()
    private var registered = false
    private var state = ClimateUiState()

    private val callback = object : IModuleCallback.Stub() {
        override fun update(
            updateCode: Int,
            intArray: IntArray?,
            floatArray: FloatArray?,
            strArray: Array<String?>?
        ) {
            val value = intArray?.firstOrNull() ?: return
            mainHandler.post { applyUpdate(updateCode, value) }
        }
    }

    fun start() {
        if (registered) return
        MsToolkitConnection.instance.addObserver(this)
        registered = true
        MsToolkitConnection.instance.connect(context.applicationContext)
        onState(state.copy(status = "Connecting"))
    }

    fun stop() {
        if (!registered) return
        MsToolkitConnection.instance.removeObserver(this)
        registered = false
    }

    override fun onConnected(toolkit: IRemoteToolkit?) {
        val remote = toolkit?.getRemoteModule(MODULE_CODE_CANBUS)
        proxy.remoteModule = remote
        CLIMATE_CODES.forEach { proxy.register(callback, it, 1) }
        mainHandler.post { onState(state.copy(status = "Live")) }
    }

    override fun onDisconnected() {
        CLIMATE_CODES.forEach { proxy.unregister(callback, it) }
        proxy.remoteModule = null
        mainHandler.post { onState(state.copy(status = "Disconnected")) }
    }

    private fun applyUpdate(code: Int, value: Int) {
        state = when (code) {
            27 -> state.copy(temperature = when (value) { -2 -> "LO"; -3 -> "HI"; else -> (value + 64).toString() })
            4 -> state.copy(auto = value == 1)
            11 -> state.copy(ac = value == 1)
            12 -> state.copy(recirc = value == 1)
            15 -> state.copy(defrost = value == 1)
            21 -> state.copy(fanSpeed = value)
            18 -> state.copy(feet = value == 1)
            19 -> state.copy(face = value == 1)
            20 -> state.copy(window = value == 1)
            else -> state
        }.copy(status = "Live")
        onState(state)
    }

    companion object {
        private val CLIMATE_CODES = listOf(4, 11, 12, 15, 18, 19, 20, 21, 27)
    }
}

data class ClimateUiState(
    val temperature: String = "—",
    val fanSpeed: Int? = null,
    val auto: Boolean = false,
    val ac: Boolean = false,
    val recirc: Boolean = false,
    val defrost: Boolean = false,
    val feet: Boolean = false,
    val face: Boolean = false,
    val window: Boolean = false,
    val status: String = "Waiting"
) {
    val ventLabel: String
        get() = when {
            face && feet -> "Face + feet"
            feet && window -> "Feet + defrost"
            feet -> "Feet"
            face -> "Face"
            window -> "Defrost"
            else -> "Vents off"
        }
}
