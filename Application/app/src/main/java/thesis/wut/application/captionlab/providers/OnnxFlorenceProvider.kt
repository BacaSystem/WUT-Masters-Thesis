package thesis.wut.application.captionlab.providers

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import thesis.wut.application.captionlab.utils.onnx.ImagePreprocessor
import thesis.wut.application.captionlab.utils.onnx.OnnxIoInspector
import thesis.wut.application.captionlab.utils.onnx.SpmDetokenizer
import thesis.wut.application.captionlab.utils.onnx.VocabLoader
import java.nio.FloatBuffer

class OnnxFlorenceProvider(private val appContext: Context) : CaptioningProvider  {
    override val id: String = "onnx_florence2_local"


    // === KONFIGURACJA I/O (dopasuj po inspekcji) ===
    data class IoConfig(
        val encInput: String = "pixel_values", // [1,3,H,W] float
        val encOutput: String = "last_hidden_state", // [1,Hw,Dim] lub [1,Len,Dim]
        val decInputIds: String = "input_ids", // [1,curLen]
        val decEncOut: String = "encoder_hidden_states", // [1,Len,Dim]
        val decPast: String? = null, // jeśli model zwraca/oczekuje past_key_values (opcjonalnie)
        val decLogits: String = "logits" // [1,curLen,vocab]
    )
    private val io = IoConfig()

    private val env = OrtEnvironment.getEnvironment()
    private val encSession: OrtSession by lazy { sessionFromAsset("models/florence2/encoder.onnx") }
    private val decSession: OrtSession by lazy { sessionFromAsset("models/florence2/decoder.onnx") }


    private val pre = ImagePreprocessor(384, 384)
    private val vocab: List<String> by lazy { VocabLoader.load(appContext, "models/florence2/vocab.txt") }
    private val detok: SpmDetokenizer by lazy { SpmDetokenizer(vocab) }


    // Specjalne ID (dopasuj do vocab/special_tokens)
    private val BOS_ID = 1
    private val EOS_ID = 2
    private val MAX_NEW_TOKENS = 64

    override suspend fun caption(bitmap: Bitmap): CaptionResult {
        val t0 = SystemClock.elapsedRealtimeNanos()


// 1) ENCODER
        val img: FloatBuffer = pre.toNCHW(bitmap)
        val encInputs = mapOf(
            io.encInput to OnnxTensor.createTensor(env, img, longArrayOf(1, 3, 384, 384))
        )
//        env.createSessionOptions().use { }

        val t1 = SystemClock.elapsedRealtimeNanos()
        val encOut = encSession.run(encInputs)
        val t2 = SystemClock.elapsedRealtimeNanos()


// Wyciągnij encoder_hidden_states (jako tensor) do przekazania do dekodera
        val encTensor = encOut.get(0).value as OnnxTensor // zakładamy, że pierwszy to encOutput
        val encShape = (encTensor.info as TensorInfo).shape
        Log.d("Florence", "Encoder out shape: ${encShape?.contentToString()}")

        // 2) DECODER — greedy loop
        val generated = mutableListOf<Int>()
        var curIds = intArrayOf(BOS_ID)


        var tInferDecNs = 0L
        for (step in 0 until MAX_NEW_TOKENS) {

            // === FIX START ===
            // 1. Create a java.nio.IntBuffer from your curIds IntArray
            val inputIdsBuffer = java.nio.IntBuffer.wrap(curIds)
            val decInputs = HashMap<String, OnnxTensor>()

            // 2. Create the tensor from the buffer and specify the shape
            decInputs[io.decInputIds] = OnnxTensor.createTensor(env, inputIdsBuffer, longArrayOf(1, curIds.size.toLong()))
            // === FIX END ===

// input_ids
//            decInputs[io.decInputIds] = OnnxTensor.createTensor(env, arrayOf(curIds), longArrayOf(1, curIds.size.toLong()))
// encoder_hidden_states (przekazujemy cały tensor z encodera)
            decInputs[io.decEncOut] = encTensor


            val tDec0 = SystemClock.elapsedRealtimeNanos()
            val decOut = decSession.run(decInputs)
            val tDec1 = SystemClock.elapsedRealtimeNanos()
            tInferDecNs += (tDec1 - tDec0)


// logits: [1, curLen, vocab] — bierzemy ostatni krok
            val logits = (decOut[0].value as Array<Array<FloatArray>>)[0].last()
            val nextId = argmax(logits)
            if (nextId == EOS_ID) break
            generated.add(nextId)


// przygotuj kolejną iterację: doklej nextId
            curIds = curIds + nextId
            decOut.close()
        }

        val text = detok.decode(generated.toIntArray(), bos = BOS_ID, eos = EOS_ID)


        val t3 = SystemClock.elapsedRealtimeNanos()
        encOut.close()


        val preMs = (t1 - t0) / 1e6
        val encMs = (t2 - t1) / 1e6
        val decMs = tInferDecNs / 1e6
        val postMs = (t3 - t2 - tInferDecNs) / 1e6
        val e2eMs = (t3 - t0) / 1e6


        return CaptionResult(
            text = text.ifBlank { "(pusty opis)" },
            extra = mapOf(
                "pre_ms" to preMs,
                "enc_ms" to encMs,
                "dec_ms" to decMs,
                "post_ms" to postMs,
                "e2e_ms" to e2eMs
            )
        )
    }

    private fun argmax(arr: FloatArray): Int {
        var mi = 0
        var mv = Float.NEGATIVE_INFINITY
        for (i in arr.indices) if (arr[i] > mv) { mv = arr[i]; mi = i }
        return mi
    }


    private fun sessionFromAsset(assetPath: String): OrtSession {
        val afd = appContext.assets.openFd(assetPath)
        val fc = java.io.FileInputStream(afd.fileDescriptor).channel
        val model = fc.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.length)
        val opts = OrtSession.SessionOptions()
        val session = env.createSession(model, opts)
        Log.i("Florence", OnnxIoInspector.dump(session))
        return session
    }
}