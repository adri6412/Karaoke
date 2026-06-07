package karaoke.app.bluetooth

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Stato dell'uscita audio Bluetooth (A2DP). L'audio viene instradato da Android. */
object BluetoothAudio {

    /** Nome del dispositivo A2DP collegato, o `null` se non c'è uscita Bluetooth. */
    fun connectedOutputName(context: Context): String? {
        val am = context.getSystemService(AudioManager::class.java) ?: return null
        val device = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            } ?: return null
        val name = runCatching { device.productName?.toString() }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: "Dispositivo Bluetooth"
    }

    fun isConnected(context: Context): Boolean = connectedOutputName(context) != null
}
