package thesis.wut.application.captionlab.metrics

import kotlin.math.max

class MemoryMonitor {
    private var isRunning = false
    private var initialMemoryMb = 0.0
    private var peakMemoryMb = 0.0
    private var monitorThread: Thread? = null
    
    fun start() {
        isRunning = true
        initialMemoryMb = getCurrentMemoryMb()
        peakMemoryMb = initialMemoryMb
        
        monitorThread = Thread {
            while (isRunning) {
                val current = getCurrentMemoryMb()
                if (current > peakMemoryMb) {
                    peakMemoryMb = current
                }
                Thread.sleep(10)
            }
        }.apply {
            isDaemon = true
            start()
        }
    }
    
    fun stop() {
        isRunning = false
        monitorThread?.join(500)
    }
    
    fun getPeakMb(): Double = peakMemoryMb
    
    fun getMemoryIncreaseMb(): Double = max(0.0, peakMemoryMb - initialMemoryMb)
    
    private fun getCurrentMemoryMb(): Double {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
    }
}
