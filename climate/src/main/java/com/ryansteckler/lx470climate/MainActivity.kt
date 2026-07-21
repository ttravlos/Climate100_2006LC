package com.ryansteckler.lx470climate

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.aoe.fytcanbusmonitor.ModuleCodes.MODULE_CODE_CANBUS
import com.aoe.fytcanbusmonitor.ModuleCodes.MODULE_CODE_CAN_UP
import com.aoe.fytcanbusmonitor.ModuleCodes.MODULE_CODE_MAIN
import com.aoe.fytcanbusmonitor.ModuleCodes.MODULE_CODE_SOUND
import com.aoe.fytcanbusmonitor.MsToolkitConnection

class MainActivity : AppCompatActivity(), View.OnTouchListener {

    private lateinit var diagnosticLogger: DiagnosticLogger
    private val ipcConnections = mutableListOf<IPCConnection>()
    private var observersRegistered = false

    private var windowOn = false
    private var faceOn = false
    private var feetOn = false
    private var acOn = false
    private var recircOn = false
    private var defrostOn = false
    private var autoOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        diagnosticLogger = DiagnosticLogger(applicationContext)
        diagnosticLogger.log("=== Climate100 1.02 started ===")
        diagnosticLogger.logMemory("onCreate")

        listOf(
            R.id.btnTempPlus,
            R.id.btnTempMinus,
            R.id.btnAuto,
            R.id.btnVent,
            R.id.btnRecirc,
            R.id.btnAC,
            R.id.btnDefrost,
            R.id.btnFanPlus,
            R.id.btnFanMinus,
            R.id.btnFanOff
        ).forEach { id -> findViewById<ImageButton>(id).setOnTouchListener(this) }

        createConnectionsOnce()
    }

    override fun onStart() {
        super.onStart()
        diagnosticLogger.log("LIFECYCLE onStart")
        registerObservers()
        MsToolkitConnection.instance.connect(applicationContext)
    }

    override fun onStop() {
        diagnosticLogger.log("LIFECYCLE onStop")
        diagnosticLogger.logMemory("onStop")
        unregisterObservers()
        super.onStop()
    }

    override fun onDestroy() {
        diagnosticLogger.log("LIFECYCLE onDestroy")
        unregisterObservers()
        super.onDestroy()
    }

    override fun onTouch(view: View?, motionEvent: MotionEvent?): Boolean {
        if (view !is ImageView || motionEvent == null) return false

        val canBusCommand = when (view.id) {
            R.id.btnTempPlus -> 3
            R.id.btnTempMinus -> 2
            R.id.btnAuto -> 21
            R.id.btnVent -> 36
            R.id.btnRecirc -> 25
            R.id.btnAC -> 23
            R.id.btnDefrost -> 18
            R.id.btnFanPlus -> 10
            R.id.btnFanMinus -> 9
            R.id.btnFanOff -> 1
            else -> return false
        }

        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val highlight = when (view.id) {
                    R.id.btnTempPlus -> Color.argb(50, 255, 0, 0)
                    R.id.btnTempMinus -> Color.argb(50, 0, 0, 255)
                    else -> Color.argb(50, 255, 255, 255)
                }
                view.setColorFilter(highlight, PorterDuff.Mode.SRC_ATOP)
                view.invalidate()
                sendCanButtonCommand(canBusCommand, pressed = true)
                view.performClick()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.clearColorFilter()
                view.invalidate()
                sendCanButtonCommand(canBusCommand, pressed = false)
            }
        }

        return true
    }

    private fun sendCanButtonCommand(command: Int, pressed: Boolean) {
        diagnosticLogger.log("COMMAND code=$command pressed=$pressed")
        val remoteModule = MsToolkitConnection.instance.remoteToolkit
            ?.getRemoteModule(MODULE_CODE_CANBUS)
        remoteModule?.cmd(0, intArrayOf(command, if (pressed) 1 else 0), null, null)
    }

    private fun createConnectionsOnce() {
        if (ipcConnections.isNotEmpty()) return

        ipcConnections += createConnection("Main", MODULE_CODE_MAIN, 0..119)
        ipcConnections += createConnection("Canbus", MODULE_CODE_CANBUS, (0..50) + (1000..1036))
        ipcConnections += createConnection("Sound", MODULE_CODE_SOUND, 0..49)
        ipcConnections += createConnection("CanUp", MODULE_CODE_CAN_UP, listOf(100))
    }

    private fun createConnection(
        systemName: String,
        moduleCode: Int,
        updateCodes: Iterable<Int>
    ): IPCConnection {
        val callback = ModuleCallback(systemName, ::canBusNotify)
        return IPCConnection(moduleCode).apply {
            updateCodes.forEach { updateCode -> addCallback(callback, updateCode) }
        }
    }

    private fun registerObservers() {
        if (observersRegistered) return
        ipcConnections.forEach(MsToolkitConnection.instance::addObserver)
        observersRegistered = true
        diagnosticLogger.log("IPC registered observers=${ipcConnections.size}")
    }

    private fun unregisterObservers() {
        if (!observersRegistered) return
        ipcConnections.forEach(MsToolkitConnection.instance::removeObserver)
        observersRegistered = false
        diagnosticLogger.log("IPC unregistered observers=${ipcConnections.size}")
    }

    private fun canBusNotify(
        systemName: String,
        updateCode: Int,
        intArray: IntArray?,
        floatArray: FloatArray?,
        strArray: Array<String?>?
    ) {
        if (isFinishing || isDestroyed) return

        val firstValue = intArray?.firstOrNull()
        if (systemName.equals("Canbus", ignoreCase = true)) {
            // Keep logs useful but bounded: record the Climate100-relevant updates only.
            if (updateCode in CLIMATE_UPDATE_CODES) {
                diagnosticLogger.log("CAN updateCode=$updateCode value=$firstValue")
            }

            when (updateCode) {
                // LC100-specific temperature setpoint mapping: raw value + 64 = Fahrenheit.
                27 -> updateTemperature(firstValue)

                4 -> {
                    autoOn = firstValue == 1
                    findViewById<TextView>(R.id.lblAuto).visibility =
                        if (autoOn) View.VISIBLE else View.INVISIBLE
                }

                11 -> {
                    acOn = firstValue == 1
                    findViewById<ImageView>(R.id.imgAC).setImageResource(
                        if (acOn) R.drawable.img_ac_on else R.drawable.img_ac_off
                    )
                }

                12 -> {
                    recircOn = firstValue == 1
                    findViewById<ImageView>(R.id.imgRecirc).setImageResource(
                        if (recircOn) R.drawable.img_recirc_on else R.drawable.img_recirc_off
                    )
                }

                15 -> {
                    defrostOn = firstValue == 1
                    findViewById<ImageView>(R.id.imgDefrost).setImageResource(
                        if (defrostOn) R.drawable.img_defrost_on else R.drawable.img_defrost_off
                    )
                }

                21 -> findViewById<TextView>(R.id.txtFanSpeed).text = firstValue?.toString() ?: "—"

                18 -> {
                    feetOn = firstValue == 1
                    handleVentStatus()
                }

                19 -> {
                    faceOn = firstValue == 1
                    handleVentStatus()
                }

                20 -> {
                    windowOn = firstValue == 1
                    handleVentStatus()
                }
            }
        }
    }

    private fun updateTemperature(rawTemperature: Int?) {
        val temperatureText = when (rawTemperature) {
            null -> return
            -2 -> "LO"
            -3 -> "HI"
            else -> (rawTemperature + 64).toString()
        }
        findViewById<TextView>(R.id.txtTemperature).text = temperatureText
    }

    private fun handleVentStatus() {
        // This combination appears transiently while the LC100 CAN box cycles modes.
        if (faceOn && windowOn) return

        val imageResource = when {
            faceOn && feetOn -> R.drawable.img_vents_foot_face
            feetOn && windowOn -> R.drawable.img_vents_foot_defrost
            feetOn -> R.drawable.img_vents_foot
            faceOn -> R.drawable.img_vents_face
            windowOn -> R.drawable.img_vents_defrost_only
            else -> R.drawable.img_vents_off
        }
        findViewById<ImageView>(R.id.imgVents).setImageResource(imageResource)
    }

    companion object {
        private val CLIMATE_UPDATE_CODES = setOf(4, 11, 12, 15, 18, 19, 20, 21, 27)
    }
}
