package thesis.wut.application.captionlab.utils.florence

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Simplified tokenizer for Florence-2 model
 * 
 * Handles encoding of task prompts and decoding of generated token IDs
 * Uses vocabulary from tokenizer.json
 * 
 * @param context Android context for asset loading
 */
class Florence2Tokenizer(private val context: Context) {
    
    companion object {
        private const val TAG = "Florence2Tokenizer"
        
        // Special tokens
        private const val PAD_TOKEN = "<pad>"
        private const val BOS_TOKEN = "<s>"
        private const val EOS_TOKEN = "</s>"
        private const val UNK_TOKEN = "<unk>"
        
        // Special token IDs (standard for most transformers)
        private const val PAD_TOKEN_ID = 0
        private const val BOS_TOKEN_ID = 1
        private const val EOS_TOKEN_ID = 2
        private const val UNK_TOKEN_ID = 3
        
        // Task tokens for Florence-2
        private val TASK_TOKENS = listOf(
            "<CAPTION>",
            "<DETAILED_CAPTION>",
            "<MORE_DETAILED_CAPTION>",
            "<OD>", // Object Detection
            "<DENSE_REGION_CAPTION>",
            "<REGION_PROPOSAL>",
            "<CAPTION_TO_PHRASE_GROUNDING>",
            "<REFERRING_EXPRESSION_SEGMENTATION>",
            "<REGION_TO_SEGMENTATION>",
            "<OPEN_VOCABULARY_DETECTION>",
            "<REGION_TO_CATEGORY>",
            "<REGION_TO_DESCRIPTION>"
        )
    }
    
    private val vocab: MutableMap<String, Int> = mutableMapOf()
    private val reverseVocab: MutableMap<Int, String> = mutableMapOf()
    
    init {
        loadVocabulary()
    }
    
    /**
     * Load vocabulary from tokenizer.json or vocab.txt
     */
    private fun loadVocabulary() {
        try {
            // Try loading from tokenizer.json first
            val vocabLoaded = tryLoadTokenizerJson() || tryLoadVocabTxt()
            
            if (!vocabLoaded) {
                Log.w(TAG, "No vocabulary file found, using fallback")
                createFallbackVocabulary()
            }
            
            Log.i(TAG, "Vocabulary loaded: ${vocab.size} tokens")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading vocabulary", e)
            createFallbackVocabulary()
        }
    }
    
    /**
     * Try to load tokenizer.json (HuggingFace format)
     */
    private fun tryLoadTokenizerJson(): Boolean {
        return try {
            val inputStream = context.assets.open("models/florence2/tokenizer.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = reader.readText()
            reader.close()
            
            val jsonObj = JSONObject(json)
            val model = jsonObj.getJSONObject("model")
            val vocabObj = model.getJSONObject("vocab")
            
            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                val id = vocabObj.getInt(token)
                vocab[token] = id
                reverseVocab[id] = token
            }
            
            true
        } catch (e: Exception) {
            Log.d(TAG, "tokenizer.json not found or invalid")
            false
        }
    }
    
    /**
     * Try to load vocab.txt (simple format: one token per line)
     */
    private fun tryLoadVocabTxt(): Boolean {
        return try {
            val inputStream = context.assets.open("models/florence2/vocab.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            var id = 0
            reader.forEachLine { token ->
                vocab[token.trim()] = id
                reverseVocab[id] = token.trim()
                id++
            }
            
            reader.close()
            true
        } catch (e: Exception) {
            Log.d(TAG, "vocab.txt not found")
            false
        }
    }
    
    /**
     * Create minimal fallback vocabulary with special tokens and task tokens
     */
    private fun createFallbackVocabulary() {
        Log.w(TAG, "Using fallback vocabulary")
        
        // Special tokens
        vocab[PAD_TOKEN] = PAD_TOKEN_ID
        vocab[BOS_TOKEN] = BOS_TOKEN_ID
        vocab[EOS_TOKEN] = EOS_TOKEN_ID
        vocab[UNK_TOKEN] = UNK_TOKEN_ID
        
        reverseVocab[PAD_TOKEN_ID] = PAD_TOKEN
        reverseVocab[BOS_TOKEN_ID] = BOS_TOKEN
        reverseVocab[EOS_TOKEN_ID] = EOS_TOKEN
        reverseVocab[UNK_TOKEN_ID] = UNK_TOKEN
        
        // Task tokens (assign IDs starting from 4)
        var nextId = 4
        TASK_TOKENS.forEach { token ->
            vocab[token] = nextId
            reverseVocab[nextId] = token
            nextId++
        }
        
        // Add common words (placeholder - in real scenario would need full vocab)
        val commonWords = listOf(
            "a", "the", "is", "are", "in", "on", "at", "of", "and", "with",
            "person", "people", "man", "woman", "child", "dog", "cat", "car", "tree", "house",
            "street", "building", "sky", "grass", "water", "standing", "sitting", "walking"
        )
        
        commonWords.forEach { word ->
            if (!vocab.containsKey(word)) {
                vocab[word] = nextId
                reverseVocab[nextId] = word
                nextId++
            }
        }
    }
    
    /**
     * Encode text to token IDs
     * 
     * Simplified: splits on whitespace and looks up in vocab
     * Real tokenizer would use BPE/WordPiece
     * 
     * @param text Input text (usually task prompt)
     * @return Array of token IDs, starting with BOS
     */
    fun encode(text: String): IntArray {
        val tokens = mutableListOf<Int>()
        
        // Add BOS token
        tokens.add(BOS_TOKEN_ID)
        
        // Check if it's a task token (exact match)
        if (vocab.containsKey(text)) {
            tokens.add(vocab[text]!!)
        } else {
            // Split by whitespace and encode each token
            text.trim().split(Regex("\\s+")).forEach { word ->
                val tokenId = vocab[word] ?: vocab[word.lowercase()] ?: UNK_TOKEN_ID
                tokens.add(tokenId)
            }
        }
        
        return tokens.toIntArray()
    }
    
    /**
     * Decode token IDs to text
     * 
     * @param tokenIds Array of token IDs
     * @param skipSpecialTokens Whether to skip special tokens in output
     * @return Decoded text
     */
    fun decode(tokenIds: IntArray, skipSpecialTokens: Boolean = true): String {
        val tokens = mutableListOf<String>()
        
        tokenIds.forEach { id ->
            val token = reverseVocab[id]
            
            if (token != null) {
                // Skip special tokens if requested
                if (skipSpecialTokens && isSpecialToken(token)) {
                    return@forEach
                }
                tokens.add(token)
            } else {
                Log.w(TAG, "Unknown token ID: $id")
                if (!skipSpecialTokens) {
                    tokens.add(UNK_TOKEN)
                }
            }
        }
        
        // Join tokens and clean up
        // Handle GPT-style Ġ prefix (U+0120) which represents space before token
        var text = tokens.joinToString("")
        text = text.replace("Ġ", " ")  // Ġ (U+0120) -> space
        text = text.replace("Ċ", "\n") // Ċ (U+010A) -> newline  
        text = text.replace("ĉ", "\t")  // ĉ (U+0109) -> tab
        text = text.trim()
        
        return text
    }
    
    /**
     * Check if token is a special token
     */
    private fun isSpecialToken(token: String): Boolean {
        return token.startsWith("<") && token.endsWith(">")
    }
    
    /**
     * Get token ID for a specific token
     */
    fun getTokenId(token: String): Int? {
        return vocab[token]
    }
    
    /**
     * Get token for a specific ID
     */
    fun getToken(id: Int): String? {
        return reverseVocab[id]
    }
}
