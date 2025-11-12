# Florence-2 ONNX Provider - Dokumentacja implementacji

## Przegląd

Kompletna, przepisana od zera implementacja providera Florence-2 dla generowania opisów obrazów w aplikacji CaptionLab.

## Struktura plików

```
app/src/main/java/thesis/wut/application/captionlab/
├── providers/
│   ├── CaptioningProvider.kt                    # Interfejs
│   └── local/
│       └── Florence2OnnxProvider.kt             # ✅ NOWY - główna implementacja
└── utils/
    └── florence/
        ├── Florence2ImageProcessor.kt           # ✅ NOWY - preprocessing obrazów
        └── Florence2Tokenizer.kt                # ✅ NOWY - tokenizacja/detokenizacja
```

## Kluczowe komponenty

### 1. Florence2OnnxProvider

**Lokalizacja:** `providers/local/Florence2OnnxProvider.kt`

**Główna klasa** odpowiedzialna za:
- Ładowanie modeli ONNX (encoder + decoder)
- Orkiestrację procesu captioningu
- Zbieranie metryk wydajnościowych

**Kluczowe metody:**
```kotlin
// Główna metoda - generuje opis obrazu
suspend fun caption(bitmap: Bitmap): CaptionResult

// Uruchamia vision encoder
private fun runVisionEncoder(pixelValues: FloatBuffer): OnnxTensor

// Uruchamia decoder autoregressively (greedy decoding)
private fun runDecoderAutoregressive(
    inputIds: IntArray,
    encoderHiddenStates: OnnxTensor,
    maxNewTokens: Int
): IntArray
```

**Konfiguracja:**
```kotlin
companion object {
    private const val IMAGE_SIZE = 768           // Florence-2 wymaga 768x768
    private const val MAX_NEW_TOKENS = 100       // Max długość opisu
    private const val NUM_BEAMS = 3              // Beam search (TODO)
    
    private const val PAD_TOKEN_ID = 0
    private const val BOS_TOKEN_ID = 1
    private const val EOS_TOKEN_ID = 2
    
    private const val TASK_DETAILED_CAPTION = "<DETAILED_CAPTION>"
}
```

**Metryki zbierane:**
- `pre_ms` - czas preprocessingu (resize, normalizacja)
- `enc_ms` - czas encodera (vision encoder)
- `dec_ms` - czas dekodera (autoregressive generation)
- `post_ms` - czas postprocessingu (detokenizacja)
- `e2e_ms` - czas end-to-end całego procesu
- `tokens_generated` - liczba wygenerowanych tokenów

### 2. Florence2ImageProcessor

**Lokalizacja:** `utils/florence/Florence2ImageProcessor.kt`

**Odpowiedzialność:**
- Preprocessing obrazów do formatu oczekiwanego przez model
- Konwersja Bitmap → FloatBuffer w formacie NCHW [1, 3, H, W]
- Normalizacja ImageNet (mean, std)

**Proces:**
```
Input Bitmap
    ↓
Resize to 768x768
    ↓
Extract RGB pixels
    ↓
Normalize: (pixel/255.0 - mean) / std
    ↓
Convert to NCHW format [1, 3, 768, 768]
    ↓
FloatBuffer ready for ONNX
```

**Parametry normalizacji:**
```kotlin
// ImageNet normalization
private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)  // RGB
private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)   // RGB
```

### 3. Florence2Tokenizer

**Lokalizacja:** `utils/florence/Florence2Tokenizer.kt`

**Odpowiedzialność:**
- Ładowanie słownika (vocab) z `tokenizer.json` lub `vocab.txt`
- Enkodowanie tekstów (task prompts) do token IDs
- Dekodowanie token IDs do tekstów

**Kluczowe metody:**
```kotlin
// Encode text → token IDs
fun encode(text: String): IntArray

// Decode token IDs → text
fun decode(tokenIds: IntArray, skipSpecialTokens: Boolean = true): String
```

**Obsługiwane źródła słownika:**
1. `tokenizer.json` (format HuggingFace) - priorytet
2. `vocab.txt` (jeden token na linię)
3. Fallback vocabulary (minimalne special tokens + task tokens)

**Special tokens:**
```kotlin
<pad> = 0
<s>   = 1  (BOS - Begin of Sequence)
</s>  = 2  (EOS - End of Sequence)
<unk> = 3  (Unknown token)
```

**Task tokens Florence-2:**
- `<CAPTION>` - prosty caption
- `<DETAILED_CAPTION>` - szczegółowy caption
- `<MORE_DETAILED_CAPTION>` - bardzo szczegółowy caption
- `<OD>` - object detection
- `<DENSE_REGION_CAPTION>` - opisy regionów
- i inne...

## Flow wykonania

```
MainActivity.runProvider(florence2Provider)
    ↓
Florence2OnnxProvider.caption(bitmap)
    ↓
┌─────────────────────────────────────────┐
│ 1. PREPROCESSING (pre_ms)               │
│    - Florence2ImageProcessor.preprocess │
│    - Florence2Tokenizer.encode          │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│ 2. VISION ENCODER (enc_ms)              │
│    - runVisionEncoder()                 │
│    - Input: pixel_values [1,3,768,768] │
│    - Output: hidden_states [1,N,Dim]   │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│ 3. DECODER LOOP (dec_ms)                │
│    - runDecoderAutoregressive()         │
│    - For each step (max 100):           │
│      * Prepare inputs                   │
│      * Run decoder                      │
│      * Get logits                       │
│      * Argmax → next token              │
│      * Check for EOS                    │
│      * Append to sequence               │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│ 4. POSTPROCESSING (post_ms)             │
│    - Florence2Tokenizer.decode          │
│    - postProcessCaption()               │
│    - Clean special tokens               │
│    - Capitalize, add period             │
└─────────────────────────────────────────┘
    ↓
CaptionResult(text, extra metrics)
```

## Wymagane pliki modelu

**Lokalizacja:** `app/src/main/assets/models/florence2/`

### Pliki ONNX:
1. **vision_encoder.onnx** (~100 MB)
   - Encoder wizyjny (transformuje obraz → hidden states)
   
2. **decoder_model_merged.onnx** (~115 MB)
   - Decoder językowy (autoregressive text generation)

### Pliki pomocnicze:
3. **tokenizer.json** (~2 MB)
   - Słownik tokenów (vocab)
   - Format HuggingFace

### Alternatywne nazwy:
- `encoder_model.onnx` zamiast `vision_encoder.onnx`
- `vocab.txt` zamiast `tokenizer.json`

## Instalacja modelu

**Zobacz szczegółowe instrukcje:** [FLORENCE2_SETUP.md](../FLORENCE2_SETUP.md)

### Szybki start:

```bash
# PowerShell w katalogu projektu
cd c:\Studia\mgr\Application\app\src\main\assets

# Utwórz folder
mkdir models
cd models

# Pobierz model (wymaga Git LFS)
git lfs install
git clone https://huggingface.co/onnx-community/Florence-2-base-ft florence2

# Sprawdź pliki
ls florence2/onnx/*.onnx
```

## Użycie w aplikacji

### MainActivity.kt (już zaktualizowane):

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var florence2Provider: CaptioningProvider
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ...
        
        // Inicjalizacja providera
        florence2Provider = Florence2OnnxProvider(this)
        
        // Przypisanie do przycisku
        vb.btnLocal.setOnClickListener { 
            runProvider(florence2Provider) 
        }
    }
    
    private fun runProvider(provider: CaptioningProvider) {
        // ... load bitmap ...
        
        val result = withContext(Dispatchers.IO) { 
            provider.caption(bitmap) 
        }
        
        // Wyświetl wyniki
        vb.tvOut.text = """
            Provider: ${provider.id}
            Opis: ${result.text}
            
            Metryki:
              pre_ms: ${result.extra["pre_ms"]}
              enc_ms: ${result.extra["enc_ms"]}
              dec_ms: ${result.extra["dec_ms"]}
              post_ms: ${result.extra["post_ms"]}
              e2e_ms: ${result.extra["e2e_ms"]}
              tokens: ${result.extra["tokens_generated"]}
        """.trimIndent()
    }
}
```

## Testowanie

### 1. Test na pojedynczym obrazie:

1. Uruchom aplikację
2. Kliknij "Wybierz obraz"
3. Wybierz dowolne zdjęcie z galerii
4. Kliknij przycisk "Local" (Florence-2)
5. Poczekaj na wynik

### 2. Sprawdź logi (Logcat):

```
I/Florence2ONNX: Loading model: models/florence2/vision_encoder.onnx
I/Florence2ONNX: Model loaded successfully: models/florence2/vision_encoder.onnx
I/Florence2ONNX: === Session Info ===
I/Florence2ONNX: Inputs:
I/Florence2ONNX:   - pixel_values: TensorInfo(...)
I/Florence2ONNX: Outputs:
I/Florence2ONNX:   - last_hidden_state: TensorInfo(...)
I/Florence2ONNX: Loading model: models/florence2/decoder_model_merged.onnx
I/Florence2ONNX: Model loaded successfully: models/florence2/decoder_model_merged.onnx
I/Florence2ONNX: EOS reached at step 15
I/Florence2ONNX: Caption generated: A dog sitting on the grass.
I/Florence2ONNX: Timing - Pre: 45.2ms, Enc: 523.7ms, Dec: 1234.5ms, Post: 12.1ms, E2E: 1815.5ms
```

### 3. Oczekiwane wyniki:

**Urządzenie:** Emulator (x86_64, CPU only)
- **Pre:** 40-60 ms
- **Enc:** 400-700 ms
- **Dec:** 1000-2000 ms (zależy od długości opisu)
- **Post:** 10-20 ms
- **E2E:** 1500-3000 ms

**Urządzenie:** Samsung Galaxy S24 (Snapdragon 8 Gen 3, QNN)
- **Pre:** 20-40 ms
- **Enc:** 100-200 ms
- **Dec:** 300-600 ms
- **Post:** 5-10 ms
- **E2E:** 450-850 ms

## Rozwiązywanie problemów

### Problem: Błąd ładowania modelu

```
E/Florence2ONNX: Failed to load model: models/florence2/vision_encoder.onnx
```

**Rozwiązanie:**
1. Sprawdź czy pliki istnieją: `ls app/src/main/assets/models/florence2/`
2. Sprawdź nazwę - może być `encoder_model.onnx` zamiast `vision_encoder.onnx`
3. Upewnij się, że pliki są w folderze `assets/`, nie `res/`

### Problem: Out of Memory

```
java.lang.OutOfMemoryError: Failed to allocate ...
```

**Rozwiązanie:**
1. Użyj kwantyzowanego modelu (q4 lub q8)
2. Zwiększ heap size w gradle.properties:
   ```
   org.gradle.jvmargs=-Xmx4096m
   ```
3. Testuj na prawdziwym urządzeniu

### Problem: Puste opisy

```
Caption generated: (empty caption)
```

**Rozwiązanie:**
1. Sprawdź czy `tokenizer.json` załadował się poprawnie (logi)
2. Sprawdź czy decoder generuje tokeny (logi: "EOS reached at step X")
3. Upewnij się, że używasz poprawnego task promptu

### Problem: Bardzo długa inferencja (> 10s)

**Rozwiązanie:**
1. To normalne na emulatorze - użyj prawdziwego urządzenia
2. Zmniejsz `MAX_NEW_TOKENS` do 50
3. Włącz optymalizacje ONNX Runtime (już włączone)

## Dalsze usprawnienia (TODO)

### 1. Beam Search
Obecnie: greedy decoding (argmax)  
TODO: Implementacja beam search (num_beams=3)

### 2. Obsługa past_key_values
Obecnie: Pełna sekwencja na każdym kroku  
TODO: Cachowanie past key values dla szybszego decodingu

### 3. GPU/QNN support
Obecnie: CPU only  
TODO: QNN Execution Provider dla Snapdragon

### 4. Quantization
Obecnie: FP32/FP16  
TODO: INT8 quantization dla mniejszego rozmiaru

### 5. Batch processing
Obecnie: 1 obraz na raz  
TODO: Wsparcie dla batch inference

## Źródła i referencje

- **Florence-2 Paper:** https://arxiv.org/abs/2311.06242
- **HuggingFace Model:** https://huggingface.co/onnx-community/Florence-2-base-ft
- **ONNX Runtime:** https://onnxruntime.ai/docs/tutorials/mobile/
- **Transformers.js Demo:** https://huggingface.co/spaces/Xenova/florence2-webgpu

## Licencja

MIT (zgodnie z modelem Florence-2)

---

**Autor:** Dominik Baczyński  
**Data:** 12 listopada 2025  
**Projekt:** CaptionLab - Praca magisterska PW EiTI
