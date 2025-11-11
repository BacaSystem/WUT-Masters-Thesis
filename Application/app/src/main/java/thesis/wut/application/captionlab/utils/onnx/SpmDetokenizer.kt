package thesis.wut.application.captionlab.utils.onnx

class SpmDetokenizer(private val vocab: List<String>) {
    fun decode(ids: IntArray, bos: Int? = null, eos: Int? = null): String {
        val sb = StringBuilder()
        for (id in ids) {
            if (bos != null && id == bos) continue
            if (eos != null && id == eos) break
            val tok = if (id in vocab.indices) vocab[id] else ""
            if (tok.isEmpty()) continue
// SentencePiece: "▁" = spacja przed tokenem
            val piece = tok.replace("▁", " ")
            sb.append(piece)
        }
        return sb.toString().trim().replace(Regex(" +"), " ")
    }
}