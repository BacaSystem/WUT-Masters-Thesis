package thesis.wut.application.captionlab.providers.manager

import android.content.Context
import thesis.wut.application.captionlab.providers.CaptioningProvider
import thesis.wut.application.captionlab.providers.RateLimitedProviderWrapper
import thesis.wut.application.captionlab.providers.cloud.AzureVisionProvider
import thesis.wut.application.captionlab.providers.cloud.GeminiProvider
import thesis.wut.application.captionlab.providers.cloud.OpenAIProvider
import thesis.wut.application.captionlab.providers.local.BlipProvider
import thesis.wut.application.captionlab.providers.local.Florence2Provider
import thesis.wut.application.captionlab.providers.local.ViTGPT2Provider

class ProviderManager(
    private val context: Context,
    private val apiKeyProvider: (String) -> String?
) {
    private val providers = mutableMapOf<String, CaptioningProvider>()
    private val localProviderIds = mutableSetOf<String>()
    private val cloudProviderIds = mutableSetOf<String>()

    init {
        registerLocalProvider(Florence2Provider(context))
        registerLocalProvider(ViTGPT2Provider(context))
        registerLocalProvider(BlipProvider(context))

        registerCloudProvider(OpenAIProvider { apiKeyProvider("openai_key") })
        registerCloudProvider(AzureVisionProvider { apiKeyProvider("azure_key") })
        registerCloudProvider(GeminiProvider { apiKeyProvider("gemini_key") })
    }

    private fun registerLocalProvider(provider: CaptioningProvider) {
        providers[provider.id] = provider
        localProviderIds.add(provider.id)
    }

    private fun registerCloudProvider(provider: CaptioningProvider) {
        // Opakowaj cloud provider w rate limiter
        // OpenAI: 200,000 TPM limit, ~14,200 tokens per request
        //   -> Max ~14 req/min = 0.23 req/sec (safe: 0.15 req/sec with buffer)
        // Azure: 10 TPS (transactions per second) dla Standard tier
        // Gemini: 60 req/min dla free tier, czyli 1 req/sec
        val wrappedProvider = when (provider.id) {
            "openai_cloud" -> RateLimitedProviderWrapper(provider, requestsPerSecond = 0.15, burstSize = 1.0)
            "azure_vision_cloud" -> RateLimitedProviderWrapper(provider, requestsPerSecond = 0.15, burstSize = 1.0)
            "gemini_cloud" -> RateLimitedProviderWrapper(provider, requestsPerSecond = 1.0, burstSize = 1.0)
            else -> provider
        }
        
        providers[provider.id] = wrappedProvider
        cloudProviderIds.add(provider.id)
    }

    fun getProvider(id: String): CaptioningProvider? = providers[id]

    fun getAllProviders(): List<CaptioningProvider> = providers.values.toList()

    fun getLocalProviders(): List<CaptioningProvider> = localProviderIds.mapNotNull { providers[it] }

    fun getCloudProviders(): List<CaptioningProvider> = cloudProviderIds.mapNotNull { providers[it] }

    fun getAllProviderIds(): List<String> = providers.keys.toList()

    fun getLocalProviderIds(): List<String> = localProviderIds.toList()

    fun getCloudProviderIds(): List<String> = cloudProviderIds.toList()

    fun isProviderAvailable(id: String): Boolean = providers.containsKey(id)

    fun isLocalProvider(id: String): Boolean = localProviderIds.contains(id)

    fun isCloudProvider(id: String): Boolean = cloudProviderIds.contains(id)

    fun getProviderDisplayName(id: String): String {
        return when (id) {
            FLORENCE2_LOCAL -> "Florence-2 (Local)"
            VITGPT2_LOCAL -> "ViT-GPT2 (Local)"
            BLIP_LOCAL -> "BLIP (Local)"
            OPENAI_CLOUD -> "OpenAI GPT-4o"
            AZURE_VISION -> "Azure Vision"
            GEMINI_CLOUD -> "Google Gemini"
            else -> id
        }
    }

    fun getProviderCategory(id: String): String {
        return when {
            isLocalProvider(id) -> "Local"
            isCloudProvider(id) -> "Cloud"
            else -> "Unknown"
        }
    }

    fun getProviderInfo(id: String): ProviderInfo? {
        if (!isProviderAvailable(id)) return null

        return ProviderInfo(
            id = id,
            displayName = getProviderDisplayName(id),
            category = getProviderCategory(id),
            description = getProviderDescription(id),
            requiresApiKey = isCloudProvider(id)
        )
    }

    fun getAllProviderInfo(): List<ProviderInfo> = getAllProviderIds().mapNotNull { getProviderInfo(it) }

    fun getStats(): Map<String, Int> {
        return mapOf(
            "total" to providers.size,
            "local" to localProviderIds.size,
            "cloud" to cloudProviderIds.size
        )
    }

    private fun getProviderDescription(id: String): String {
        return when (id) {
            FLORENCE2_LOCAL -> "Microsoft Florence-2 vision-language model running locally with ONNX Runtime"
            VITGPT2_LOCAL -> "Vision Transformer + GPT-2 image captioning model running locally"
            BLIP_LOCAL -> "BLIP (Bootstrapping Language-Image Pre-training) model running locally"
            OPENAI_CLOUD -> "OpenAI GPT-4o multimodal model via cloud API"
            AZURE_VISION -> "Microsoft Azure Computer Vision Image Analysis 4.0 API"
            GEMINI_CLOUD -> "Google Gemini Pro Vision multimodal model via Vertex AI"
            else -> ""
        }
    }

    companion object {
        const val FLORENCE2_LOCAL = "florence2_onnx_local"
        const val VITGPT2_LOCAL = "vit_gpt2_onnx_local"
        const val BLIP_LOCAL = "blip_onnx_local"
        const val OPENAI_CLOUD = "openai_cloud"
        const val AZURE_VISION = "azure_vision_cloud"
        const val GEMINI_CLOUD = "gemini_cloud"
    }
}