package com.ryansteckler.lx470climate

import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import com.aoe.fytcanbusmonitor.IModuleCallback

/**
 * Binder callback with no static Activity reference.
 * Each callback instance is owned by MainActivity and is unregistered with it.
 */
class ModuleCallback(
    private val systemName: String,
    private val onUpdate: (
        systemName: String,
        updateCode: Int,
        intArray: IntArray?,
        floatArray: FloatArray?,
        strArray: Array<String?>?
    ) -> Unit
) : IModuleCallback.Stub() {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Throws(RemoteException::class)
    override fun update(
        updateCode: Int,
        intArray: IntArray?,
        floatArray: FloatArray?,
        strArray: Array<String?>?
    ) {
        mainHandler.post {
            onUpdate(systemName, updateCode, intArray, floatArray, strArray)
        }
    }
}
