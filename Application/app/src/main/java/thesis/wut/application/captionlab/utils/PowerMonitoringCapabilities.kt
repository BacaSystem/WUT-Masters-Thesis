package thesis.wut.application.captionlab.utils

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log

/**
 * Utility class to check device capabilities for power monitoring
 */
object PowerMonitoringCapabilities {
    
    private const val TAG = "PowerCapabilities"
    
    /**
     * Check if the device supports real power monitoring
     * 
     * @return true if device can measure actual power consumption
     */
    fun isRealPowerMonitoringSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Log.w(TAG, "Android version too old (${Build.VERSION.SDK_INT}), need API 21+")
            return false
        }
        
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        if (batteryManager == null) {
            Log.w(TAG, "BatteryManager not available")
            return false
        }
        
        return try {
            // Try to get instantaneous current
            val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            
            // Check if value is valid (positive means discharging)
            val isSupported = currentNow != Int.MIN_VALUE && currentNow != 0
            
            if (isSupported) {
                Log.i(TAG, "✓ Real power monitoring SUPPORTED (current: ${currentNow}μA)")
            } else {
                Log.w(TAG, "✗ Real power monitoring NOT SUPPORTED (invalid current reading)")
            }
            
            isSupported
        } catch (e: Exception) {
            Log.w(TAG, "✗ Real power monitoring NOT SUPPORTED (exception: ${e.message})")
            false
        }
    }
    
    /**
     * Get detailed battery information for debugging
     */
    fun getBatteryInfo(context: Context): BatteryInfo {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)
        
        return BatteryInfo(
            // Current measurements
            currentNowMicroAmps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            } else null,
            
            currentAverageMicroAmps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            } else null,
            
            // Capacity
            capacityMicroAmpHours = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            } else null,
            
            capacityPercent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } else {
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) (level * 100 / scale) else null
            },
            
            // Voltage
            voltageMilliVolts = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1),
            
            // Temperature
            temperatureTenthsCelsius = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1),
            
            // Status
            isCharging = batteryStatus?.let {
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            } ?: false,
            
            chargePlug = batteryStatus?.let {
                when (it.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "Not charging"
                }
            },
            
            // Device info
            androidVersion = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            deviceProduct = Build.PRODUCT
        )
    }
    
    /**
     * Log comprehensive battery information
     */
    fun logBatteryInfo(context: Context) {
        val info = getBatteryInfo(context)
        
        Log.i(TAG, "=== Battery & Power Monitoring Info ===")
        Log.i(TAG, "Device: ${info.deviceModel} (${info.deviceProduct})")
        Log.i(TAG, "Android: API ${info.androidVersion}")
        Log.i(TAG, "")
        Log.i(TAG, "Battery Status:")
        Log.i(TAG, "  Level: ${info.capacityPercent}%")
        Log.i(TAG, "  Voltage: ${info.voltageMilliVolts} mV")
        Log.i(TAG, "  Temperature: ${info.temperatureTenthsCelsius?.let { it / 10.0 }}°C")
        Log.i(TAG, "  Charging: ${info.isCharging} (${info.chargePlug})")
        Log.i(TAG, "")
        Log.i(TAG, "Current Measurements:")
        Log.i(TAG, "  Instantaneous: ${info.currentNowMicroAmps?.let { "$it μA" } ?: "NOT AVAILABLE"}")
        Log.i(TAG, "  Average: ${info.currentAverageMicroAmps?.let { "$it μA" } ?: "NOT AVAILABLE"}")
        Log.i(TAG, "  Capacity: ${info.capacityMicroAmpHours?.let { "$it μAh" } ?: "NOT AVAILABLE"}")
        Log.i(TAG, "")
        Log.i(TAG, "Power Monitoring Support:")
        Log.i(TAG, "  Real measurement: ${if (isRealPowerMonitoringSupported(context)) "✓ YES" else "✗ NO (fallback to estimation)"}")
        Log.i(TAG, "=====================================")
    }
    
    /**
     * Get a summary string for display in UI
     */
    fun getPowerMonitoringSummary(context: Context): String {
        val info = getBatteryInfo(context)
        val supported = isRealPowerMonitoringSupported(context)
        
        return buildString {
            appendLine("Device: ${info.deviceModel}")
            appendLine("Android API: ${info.androidVersion}")
            appendLine("Battery: ${info.capacityPercent}%")
            appendLine("")
            if (supported) {
                appendLine("✓ Real power monitoring: SUPPORTED")
                appendLine("  Current: ${info.currentNowMicroAmps} μA")
                appendLine("  Voltage: ${info.voltageMilliVolts} mV")
            } else {
                appendLine("✗ Real power monitoring: NOT SUPPORTED")
                appendLine("  Will use estimation fallback")
            }
        }
    }
    
    /**
     * Data class for battery information
     */
    data class BatteryInfo(
        // Current measurements (may be null if not supported)
        val currentNowMicroAmps: Int?,
        val currentAverageMicroAmps: Int?,
        val capacityMicroAmpHours: Int?,
        
        // Battery state
        val capacityPercent: Int?,
        val voltageMilliVolts: Int?,
        val temperatureTenthsCelsius: Int?,
        
        // Charging status
        val isCharging: Boolean,
        val chargePlug: String?,
        
        // Device info
        val androidVersion: Int,
        val deviceModel: String,
        val deviceProduct: String
    )
}
