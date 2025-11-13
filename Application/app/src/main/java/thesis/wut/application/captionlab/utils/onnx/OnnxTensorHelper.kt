package thesis.wut.application.captionlab.utils.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer

internal object OnnxTensorHelper {

    fun createFloatTensor(
        env: OrtEnvironment,
        data: FloatArray,
        shape: LongArray
    ): OnnxTensor {
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)
    }

    fun createLongTensor(
        env: OrtEnvironment,
        data: LongArray,
        shape: LongArray
    ): OnnxTensor {
        return OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape)
    }

    fun extractFloatArray(result: OrtSession.Result): FloatArray {
        val outputArray = result[0].value as Array<Array<FloatArray>>
        val batchSize = outputArray.size
        val seqLen = outputArray[0].size
        val hiddenDim = outputArray[0][0].size
        
        val flattened = FloatArray(batchSize * seqLen * hiddenDim)
        var idx = 0
        
        for (b in 0 until batchSize) {
            for (s in 0 until seqLen) {
                for (h in 0 until hiddenDim) {
                    flattened[idx++] = outputArray[b][s][h]
                }
            }
        }
        
        return flattened
    }

    fun getLastPositionLogits(result: OrtSession.Result, position: Int): FloatArray {
        val logitsArray = result[0].value as Array<Array<FloatArray>>
        return logitsArray[0][position]
    }

    fun argmax(logits: FloatArray): Int {
        var maxIdx = 0
        var maxVal = Float.NEGATIVE_INFINITY
        
        for (i in logits.indices) {
            if (logits[i] > maxVal) {
                maxVal = logits[i]
                maxIdx = i
            }
        }
        
        return maxIdx
    }

    fun concatenate(first: FloatArray, second: FloatArray): FloatArray {
        val result = FloatArray(first.size + second.size)
        System.arraycopy(first, 0, result, 0, first.size)
        System.arraycopy(second, 0, result, first.size, second.size)
        return result
    }
}
