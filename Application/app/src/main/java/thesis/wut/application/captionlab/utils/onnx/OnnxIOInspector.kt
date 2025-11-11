package thesis.wut.application.captionlab.utils.onnx

import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo

object OnnxIoInspector {
    fun dump(session: OrtSession): String {
        val sb = StringBuilder()
        sb.appendLine("=== ONNX I/O ===")
        sb.appendLine("Inputs:")
        session.inputInfo.forEach { (name, info) ->
            val ti = info.info as TensorInfo
            sb.appendLine(" - $name : ${ti.type} ${ti.shape?.contentToString()}")
        }
        sb.appendLine("Outputs:")
        session.outputInfo.forEach { (name, info) ->
            val ti = info.info as TensorInfo
            sb.appendLine(" - $name : ${ti.type} ${ti.shape?.contentToString()}")
        }
        return sb.toString()
    }
}