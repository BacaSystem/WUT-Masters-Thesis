package thesis.wut.application.captionlab.providers

import android.graphics.Bitmap
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlin.math.min

/**
 * Wrapper dla cloud providerów, który implementuje client-side rate limiting
 * przez kontrolowanie czasu między requestami.
 *
 * Parametry:
 * - requestsPerSecond: Maksymalna liczba żądań na sekundę (domyślnie 2.0)
 * - burstSize: Liczba żądań które mogą być wykonane natychmiast (domyślnie 1.0)
 */
class RateLimitedProviderWrapper(
    private val provider: CaptioningProvider,
    private val requestsPerSecond: Double = 2.0,
    private val burstSize: Double = 1.0
) : CaptioningProvider {

    override val id: String = provider.id
    
    private val mutex = Mutex()
    private val minIntervalMs = (1000.0 / requestsPerSecond).toLong()
    private var lastRequestTimeMs = 0L
    private var tokensAvailable = burstSize.toDouble()

    override suspend fun caption(bitmap: Bitmap): CaptionResult {
        // Token bucket algorithm dla rate limiting
        waitForRateLimit()
        return provider.caption(bitmap)
    }

    private suspend fun waitForRateLimit() {
        mutex.lock()
        try {
            val now = SystemClock.elapsedRealtimeNanos() / 1_000_000L
            val timeSinceLastRequest = now - lastRequestTimeMs

            // Odregeneruj tokeny na podstawie czasu
            if (timeSinceLastRequest > 0) {
                val tokensToAdd = (timeSinceLastRequest * requestsPerSecond) / 1000.0
                tokensAvailable = min(
                    tokensAvailable + tokensToAdd,
                    burstSize
                )
            }

            // Jeśli nie ma tokenów, czekaj
            if (tokensAvailable < 1.0) {
                val waitTimeMs = ((1.0 - tokensAvailable) * 1000) / requestsPerSecond
                mutex.unlock()
                delay(waitTimeMs.toLong())
                mutex.lock()
                tokensAvailable = 1.0
            }

            // Zużyj token
            tokensAvailable -= 1.0
            lastRequestTimeMs = now
        } finally {
            mutex.unlock()
        }
    }
    
    override fun cleanup() {
        provider.cleanup()
    }
}
