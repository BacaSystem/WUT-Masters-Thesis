package thesis.wut.application.captionlab.utils.gpt2

import android.content.Context
import android.util.Log
import org.json.JSONObject

internal class GPT2Tokenizer(
    context: Context,
    modelPath: String,
    private val bosTokenId: Long = 50256L,
    private val eosTokenId: Long = 50256L
) {

    companion object {
        private const val TAG = "GPT2Tokenizer"
        private const val PAD_TOKEN_ID = 50256L
    }

    private val vocab: Map<String, Long>
    private val reverseVocab: Map<Long, String>
    private val bpeMerges: List<Pair<String, String>>

    init {
        // Load vocabulary
        val vocabJson = context.assets.open("$modelPath/vocab.json")
            .bufferedReader().use { it.readText() }
        val vocabObject = JSONObject(vocabJson)
        
        val vocabMap = mutableMapOf<String, Long>()
        val reverseMap = mutableMapOf<Long, String>()
        
        vocabObject.keys().forEach { key ->
            val value = vocabObject.getLong(key)
            vocabMap[key] = value
            reverseMap[value] = key
        }
        
        vocab = vocabMap
        reverseVocab = reverseMap
        
        // Load BPE merges
        val mergesText = context.assets.open("$modelPath/merges.txt")
            .bufferedReader().use { it.readLines() }
        
        bpeMerges = mergesText
            .drop(1) // Skip header line
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(" ")
                Pair(parts[0], parts[1])
            }
        
        Log.d(TAG, "Loaded vocabulary: ${vocab.size} tokens")
    }

    fun encode(text: String, addBos: Boolean = true): List<Long> {
        val ids = mutableListOf<Long>()
        
        if (addBos) {
            ids.add(bosTokenId)
        }

        val words = text.split("\\s+".toRegex())
        
        for ((index, word) in words.withIndex()) {
            val prefix = if (index > 0) "Ġ" else ""
            val wordWithPrefix = prefix + word
            
            val wordId = vocab[wordWithPrefix] 
                ?: vocab[word]
                ?: vocab[word.lowercase()]
                ?: vocab["${prefix}${word.lowercase()}"]
            
            if (wordId != null) {
                ids.add(wordId)
            } else {
                Log.w(TAG, "Word '$word' not in vocab, using character fallback")
                for (char in word) {
                    val charStr = char.toString()
                    val charId = vocab[charStr] ?: vocab["Ġ$charStr"] ?: PAD_TOKEN_ID
                    ids.add(charId)
                }
            }
        }

        return ids
    }

    fun decode(ids: List<Long>, skipSpecialTokens: Boolean = true): String {
        val tokens = ids
            .filter { !skipSpecialTokens || !isSpecialToken(it) }
            .mapNotNull { id ->
                reverseVocab[id] ?: run {
                    Log.w(TAG, "Unknown token ID: $id")
                    null
                }
            }

        val result = tokens
            .joinToString("")
            .replace("Ġ", " ")
            .replace("Ċ", "\n")
            .trim()

        return result
    }

    fun getBosTokenId() = bosTokenId
    fun getEosTokenId() = eosTokenId

    private fun isSpecialToken(id: Long): Boolean {
        return id == bosTokenId || id == eosTokenId || id == PAD_TOKEN_ID
    }
}
