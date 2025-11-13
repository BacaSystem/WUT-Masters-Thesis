package thesis.wut.application.captionlab.providers.manager

/**
 * Information about a captioning provider for UI display
 */
data class ProviderInfo(
    val id: String,
    val displayName: String,
    val category: String,
    val description: String = "",
    val requiresApiKey: Boolean = false
)
