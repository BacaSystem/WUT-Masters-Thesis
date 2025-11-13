package thesis.wut.application.captionlab.metrics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.*
import kotlin.math.abs

class PowerMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "PowerMonitor"
        private const val SAMPLING_INTERVAL_MS = 50L
        private const val MIN_SAMPLES = 3
    }
    
    private var isMonitoring = false
    private var monitorJob: Job? = null
    private val powerSamples = mutableListOf<PowerSample>()
    private var initialTimestamp: Long = 0

    fun start() {
        if (isMonitoring) return
        
        isMonitoring = true
        powerSamples.clear()
        initialTimestamp = System.currentTimeMillis()
        
        monitorJob = CoroutineScope(Dispatchers.Default).launch {
            while (isMonitoring) {
                try {
                    capturePowerSample()?.let { powerSamples.add(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Sample error: ${e.message}")
                }
                delay(SAMPLING_INTERVAL_MS)
            }
        }
    }

    fun stop(): Double? {
        if (!isMonitoring) return null
        
        isMonitoring = false
        monitorJob?.cancel()
        
        val durationMs = System.currentTimeMillis() - initialTimestamp
        return calculateEnergyConsumption(durationMs)
    }

    private fun capturePowerSample(): PowerSample? {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return null
        
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            
            PowerSample(
                timestamp = System.currentTimeMillis(),
                currentNowMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
                currentAvgMicroAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE),
                capacityMicroAmpHours = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
                voltageMilliVolts = voltage
            )
        } catch (e: Exception) {
            Log.e(TAG, "Battery read error: ${e.message}")
            null
        }
    }

    private fun calculateEnergyConsumption(durationMs: Long): Double? {
        if (powerSamples.size < MIN_SAMPLES) return null
        
        return calculateEnergyFromInstantaneousCurrent()
            ?: calculateEnergyFromAverageCurrent(durationMs)
            ?: calculateEnergyFromCapacityChange()
    }

    private fun calculateEnergyFromInstantaneousCurrent(): Double? {
        val validSamples = powerSamples.filter { 
            it.currentNowMicroAmps != null && 
            it.currentNowMicroAmps > 0 &&
            it.voltageMilliVolts > 0 
        }
        
        if (validSamples.size < MIN_SAMPLES) return null
        
        var totalEnergyMicroWattSeconds = 0.0
        
        for (i in 0 until validSamples.size - 1) {
            val s1 = validSamples[i]
            val s2 = validSamples[i + 1]
            
            val dt = (s2.timestamp - s1.timestamp) / 1000.0
            val power1 = (s1.voltageMilliVolts / 1000.0) * (s1.currentNowMicroAmps!! / 1_000_000.0)
            val power2 = (s2.voltageMilliVolts / 1000.0) * (s2.currentNowMicroAmps!! / 1_000_000.0)
            
            val energyWattSeconds = (power1 + power2) / 2.0 * dt
            totalEnergyMicroWattSeconds += energyWattSeconds * 1_000_000.0
        }
        
        val energyMwh = totalEnergyMicroWattSeconds / (1000.0 * 3600.0)
        return if (energyMwh > 0) energyMwh else null
    }

    private fun calculateEnergyFromAverageCurrent(durationMs: Long): Double? {
        val validSamples = powerSamples.filter { 
            it.currentAvgMicroAmps != null && 
            it.currentAvgMicroAmps > 0 &&
            it.voltageMilliVolts > 0 
        }
        
        if (validSamples.isEmpty()) return null
        
        val avgCurrent = validSamples.mapNotNull { it.currentAvgMicroAmps }.average() / 1_000_000.0
        val avgVoltage = validSamples.map { it.voltageMilliVolts }.average() / 1000.0
        val durationHours = durationMs / (1000.0 * 3600.0)
        
        val energyMwh = avgVoltage * avgCurrent * durationHours * 1000.0
        return if (energyMwh > 0) energyMwh else null
    }

    private fun calculateEnergyFromCapacityChange(): Double? {
        val validSamples = powerSamples.filter { 
            it.capacityMicroAmpHours != null &&
            it.voltageMilliVolts > 0
        }
        
        if (validSamples.size < 2) return null
        
        val firstSample = validSamples.first()
        val lastSample = validSamples.last()
        
        val capacityChange = abs(lastSample.capacityMicroAmpHours!! - firstSample.capacityMicroAmpHours!!)
        val avgVoltage = validSamples.map { it.voltageMilliVolts }.average() / 1000.0
        
        val energyMwh = capacityChange * avgVoltage / 1000.0
        return if (energyMwh > 0) energyMwh else null
    }

    private data class PowerSample(
        val timestamp: Long,
        val currentNowMicroAmps: Int?,
        val currentAvgMicroAmps: Int?,
        val capacityMicroAmpHours: Int?,
        val voltageMilliVolts: Int
    )
}
