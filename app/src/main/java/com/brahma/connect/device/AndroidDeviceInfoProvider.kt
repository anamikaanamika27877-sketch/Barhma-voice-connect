package com.brahma.connect.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings

data class AndroidDeviceInfo(
    val deviceName: String,
    val androidVersion: String,
    val batteryPercentage: Int,
    val charging: Boolean,
    val wifiEnabled: Boolean,
    val model: String
)

class AndroidDeviceInfoProvider(private val context: Context) {

    val deviceName: String
        get() {
            val name = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
                ?: Settings.Secure.getString(context.contentResolver, "bluetooth_name")
                ?: Build.MODEL
            return name.ifBlank { "Android Device" }
        }

    val batterySnapshot: Pair<Int, Boolean>
        get() {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val percentage = if (level >= 0 && scale > 0) {
                ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else {
                50
            }
            return Pair(percentage, isCharging)
        }

    val wifiEnabled: Boolean
        get() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }

    val snapshot: AndroidDeviceInfo
        get() {
            val (battery, charging) = batterySnapshot
            return AndroidDeviceInfo(
                deviceName = deviceName,
                androidVersion = Build.VERSION.RELEASE ?: "Unknown",
                batteryPercentage = battery,
                charging = charging,
                wifiEnabled = wifiEnabled,
                model = "${Build.MANUFACTURER} ${Build.MODEL}"
            )
        }
}
