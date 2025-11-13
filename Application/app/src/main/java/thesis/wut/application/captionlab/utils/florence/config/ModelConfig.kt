package thesis.wut.application.captionlab.utils.florence.config

import android.content.Context
import org.json.JSONObject

data class ModelConfig(
    // Model architecture
    val bosTokenId: Long,
    val eosTokenId: Long,
    val padTokenId: Long,
    val numImageTokens: Int,
    val projectionDim: Int,
    val vocabSize: Int,
    
    // Text generation config
    val maxLength: Int,
    val numBeams: Int,
    val noRepeatNgramSize: Int,
    
    // Text model config
    val hiddenDim: Int,
    val decoderLayers: Int,
    val encoderLayers: Int,
    
    // Image preprocessing config
    val imageSize: Int,
    val imageMean: FloatArray,
    val imageStd: FloatArray,
    val imageSeqLength: Int
) {
    companion object {
        fun load(context: Context, basePath: String): ModelConfig {
            // Load main config
            val configJson = context.assets.open("$basePath/config.json")
                .bufferedReader().use { it.readText() }
            val config = JSONObject(configJson)
            
            // Load preprocessor config
            val preprocessorJson = context.assets.open("$basePath/preprocessor_config.json")
                .bufferedReader().use { it.readText() }
            val preprocessor = JSONObject(preprocessorJson)
            
            // Extract text config
            val textConfig = config.getJSONObject("text_config")
            
            // Extract image preprocessing params
            val imageMeanArray = preprocessor.getJSONArray("image_mean")
            val imageStdArray = preprocessor.getJSONArray("image_std")
            val sizeConfig = preprocessor.getJSONObject("size")
            
            return ModelConfig(
                // Model architecture
                bosTokenId = config.getLong("bos_token_id"),
                eosTokenId = config.getLong("eos_token_id"),
                padTokenId = config.getLong("pad_token_id"),
                numImageTokens = config.getInt("num_image_tokens"),
                projectionDim = config.getInt("projection_dim"),
                vocabSize = config.getInt("vocab_size"),
                
                // Text generation
                maxLength = textConfig.optInt("max_length", 20),
                numBeams = textConfig.optInt("num_beams", 3),
                noRepeatNgramSize = textConfig.optInt("no_repeat_ngram_size", 3),
                
                // Text model architecture
                hiddenDim = textConfig.getInt("d_model"),
                decoderLayers = textConfig.getInt("decoder_layers"),
                encoderLayers = textConfig.getInt("encoder_layers"),
                
                // Image preprocessing
                imageSize = sizeConfig.getInt("height"), // assuming square
                imageMean = FloatArray(3) { i -> imageMeanArray.getDouble(i).toFloat() },
                imageStd = FloatArray(3) { i -> imageStdArray.getDouble(i).toFloat() },
                imageSeqLength = preprocessor.getInt("image_seq_length")
            )
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as ModelConfig
        
        if (bosTokenId != other.bosTokenId) return false
        if (eosTokenId != other.eosTokenId) return false
        if (!imageMean.contentEquals(other.imageMean)) return false
        if (!imageStd.contentEquals(other.imageStd)) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = bosTokenId.hashCode()
        result = 31 * result + eosTokenId.hashCode()
        result = 31 * result + imageMean.contentHashCode()
        result = 31 * result + imageStd.contentHashCode()
        return result
    }
}
