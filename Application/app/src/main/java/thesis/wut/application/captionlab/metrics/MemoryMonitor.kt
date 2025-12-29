package thesis.wut.application.captionlab.metrics

import android.util.Log
import kotlin.math.max

class MemoryMonitor {
    companion object {
        private const val TAG = "MemoryMonitor"
        private const val SAMPLING_INTERVAL_MS = 10L
    }
    
    @Volatile
    private var isRunning = false
    private var initialMemoryMb = 0.0
    private var peakMemoryMb = 0.0
    private var monitorThread: Thread? = null
    
    fun start() {
        if (isRunning) {
            Log.w(TAG, "Memory monitor already running, restarting...")
            stop()
        }
        
        isRunning = true
        initialMemoryMb = getCurrentMemoryMb()
        peakMemoryMb = initialMemoryMb
        
        monitorThread = Thread({
            try {
                while (isRunning) {
                    try {
                        val current = getCurrentMemoryMb()
                        if (current > peakMemoryMb) {
                            peakMemoryMb = current
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading memory: ${e.message}")
                    }
                    
                    Thread.sleep(SAMPLING_INTERVAL_MS)
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Memory monitor thread interrupted")
                Thread.currentThread().interrupt()
            } finally {
                isRunning = false
                Log.d(TAG, "Memory monitor stopped, peak: ${"%.2f".format(peakMemoryMb)} MB")
            }
        }, "MemoryMonitor").apply {
            isDaemon = true
            start()
        }
    }
    
    fun stop(): Double {
        if (!isRunning) {
            Log.w(TAG, "Memory monitor not running")
            return getMemoryIncreaseMb()
        }
        
        isRunning = false
        
        // Wait for thread to finish with a reasonable timeout
        try {
            monitorThread?.let { thread ->
                thread.join(1000) // Increased timeout to 1 second
                if (thread.isAlive) {
                    Log.w(TAG, "Memory monitor thread did not terminate within timeout")
                    thread.interrupt() // Force interrupt if still alive
                    thread.join(100)
                }
            }
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for memory monitor to stop")
            Thread.currentThread().interrupt()
        }
        
        monitorThread = null
        return getMemoryIncreaseMb()
    }
    
    fun getPeakMb(): Double = peakMemoryMb
    
    fun getMemoryIncreaseMb(): Double = max(0.0, peakMemoryMb - initialMemoryMb)
    
    private fun getCurrentMemoryMb(): Double {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
    }
}
