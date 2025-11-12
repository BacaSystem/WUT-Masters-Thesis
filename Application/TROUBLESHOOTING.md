# Troubleshooting Guide - CaptionLab

## Problem: FileNotFoundException - "This file can not be opened as a file descriptor; it is probably compressed"

### Przyczyna
Android Build System domyślnie kompresuje niektóre pliki w folderze `assets/`, aby zmniejszyć rozmiar APK. Pliki ONNX (`.onnx`) są kompresowane, co uniemożliwia bezpośredni dostęp przez file descriptor wymagany przez ONNX Runtime.

### Rozwiązanie 1: Wyłączenie kompresji (✅ ZAIMPLEMENTOWANE)

**Plik:** `app/build.gradle.kts`

```kotlin
android {
    // ... inne ustawienia ...
    
    // Prevent compression of ONNX model files in assets
    androidResources {
        noCompress += listOf("onnx", "tflite", "txt", "json")
    }
}
```

**Efekt:** Pliki `.onnx` nie będą kompresowane w APK.

**⚠️ UWAGA:** Po tej zmianie musisz:
1. **Sync Gradle** (w Android Studio)
2. **Clean Project** (`Build > Clean Project`)
3. **Rebuild Project** (`Build > Rebuild Project`)

### Rozwiązanie 2: Ładowanie przez cache (✅ ZAIMPLEMENTOWANE)

**Klasa:** `Florence2OnnxProvider.kt`

Nowa metoda `loadModelFromAssets()` automatycznie:
1. **Próbuje skopiować** model z `assets/` do `cacheDir/`
2. **Ładuje z cache** (szybsze, mniej pamięci RAM)
3. **Fallback:** Ładuje do pamięci jeśli cache nie działa

```kotlin
private fun copyAssetToCache(assetPath: String): File? {
    val fileName = assetPath.substringAfterLast('/')
    val cacheFile = File(appContext.cacheDir, fileName)
    
    // Check if already cached
    if (cacheFile.exists()) return cacheFile
    
    // Copy from assets to cache
    appContext.assets.open(assetPath).use { input ->
        FileOutputStream(cacheFile).use { output ->
            input.copyTo(output)
        }
    }
    
    return cacheFile
}
```

**Zalety:**
- ✅ Nie wymaga trzymania całego modelu w RAM
- ✅ Szybsze ładowanie przy kolejnych uruchomieniach (cache)
- ✅ Działa niezależnie od kompresji

**Wady:**
- ❌ Pierwsze uruchomienie trochę wolniejsze (kopiowanie)
- ❌ Zajmuje miejsce w cache (ale można wyczyścić)

---

## Problem: Out of Memory przy ładowaniu modelu

### Objawy
```
java.lang.OutOfMemoryError: Failed to allocate a ... byte allocation with ... free bytes
```

### Przyczyny
1. Model zbyt duży (Florence-2-base: ~230 MB)
2. Za mała pamięć heap dla aplikacji
3. Emulator z ograniczoną pamięcią

### Rozwiązanie 1: Zwiększ heap size

**Plik:** `gradle.properties`

```properties
# Increase memory for Gradle daemon
org.gradle.jvmargs=-Xmx4096m -XX:MaxPermSize=512m

# Increase dex heap
org.gradle.dexOptions.javaMaxHeapSize=4g
```

### Rozwiązanie 2: Użyj kwantyzowanego modelu

Zamiast `Florence-2-base-ft` (230 MB), użyj:

**INT8 quantized:** `jeiku/Florence-2-base-ft-onnx-q8` (~115 MB)
```bash
git clone https://huggingface.co/jeiku/Florence-2-base-ft-onnx-q8 florence2
```

**INT4 quantized:** `jeiku/Florence-2-base-ft-onnx-q4` (~60 MB)
```bash
git clone https://huggingface.co/jeiku/Florence-2-base-ft-onnx-q4 florence2
```

### Rozwiązanie 3: Testuj na prawdziwym urządzeniu

Emulatory często mają ograniczoną pamięć. Samsung Galaxy S24 ma 8 GB RAM.

---

## Problem: Bardzo wolna inferencja (>30s na obraz)

### Objawy
```
I/Florence2ONNX: Timing - Pre: 45ms, Enc: 523ms, Dec: 25000ms, Post: 12ms, E2E: 25580ms
```

### Przyczyny
1. Uruchamianie na emulatorze (CPU emulacji)
2. Brak optymalizacji ONNX Runtime
3. Model FP32 zamiast INT8

### Rozwiązanie 1: Testuj na prawdziwym urządzeniu

**Emulator (x86_64):**
- Pre: 40-60 ms
- Enc: 400-700 ms
- Dec: **2000-5000 ms** ⚠️ WOLNO
- E2E: ~3000-6000 ms

**Samsung S24 (Snapdragon 8 Gen 3):**
- Pre: 20-40 ms
- Enc: 100-200 ms
- Dec: **300-600 ms** ✅ SZYBKO
- E2E: ~450-850 ms

### Rozwiązanie 2: Zmniejsz MAX_NEW_TOKENS

**Plik:** `Florence2OnnxProvider.kt`

```kotlin
companion object {
    private const val MAX_NEW_TOKENS = 50  // Było: 100
}
```

**Efekt:** Krótsze opisy, ale szybsza generacja.

### Rozwiązanie 3: Użyj QNN Execution Provider (S24)

**TODO:** Implementacja QNN EP dla Snapdragon NPU

```kotlin
val sessionOptions = OrtSession.SessionOptions()
sessionOptions.addQNN()  // Użyj Qualcomm Neural Network SDK
```

---

## Problem: Model generuje puste opisy

### Objawy
```
I/Florence2ONNX: Caption generated: (empty caption)
```

### Możliwe przyczyny

#### 1. Tokenizer nie załadował się poprawnie

**Sprawdź logi:**
```
I/Florence2Tokenizer: Vocabulary loaded: X tokens
```

Jeśli `X < 100`, tokenizer używa fallback vocabulary (za mały).

**Rozwiązanie:** Upewnij się, że `tokenizer.json` jest w `assets/models/florence2/`

#### 2. Decoder nie generuje tokenów

**Sprawdź logi:**
```
D/Florence2ONNX: EOS reached at step 0
```

Jeśli `step = 0`, decoder od razu generuje EOS.

**Możliwe przyczyny:**
- Błędny prompt task (`<DETAILED_CAPTION>`)
- Niepoprawne input_ids do dekodera
- Błąd w encoder hidden states

**Debug:** Dodaj logi w `runDecoderAutoregressive()`:
```kotlin
Log.d(TAG, "Decoder step $step: nextTokenId=$nextTokenId")
```

#### 3. Post-processing usuwa cały tekst

**Sprawdź:** Czy `decodedText` przed `postProcessCaption()` zawiera treść?

```kotlin
val decodedText = tokenizer.decode(generatedIds)
Log.d(TAG, "Raw decoded text: $decodedText")
val cleanedText = postProcessCaption(decodedText, prompt)
Log.d(TAG, "Cleaned text: $cleanedText")
```

---

## Problem: Crash przy uruchomieniu providera

### Objawy
```
java.lang.RuntimeException: Failed to load ONNX model: models/florence2/vision_encoder.onnx
```

### Checklist

#### ✅ Sprawdź czy pliki istnieją:

**PowerShell:**
```powershell
cd c:\Studia\mgr\Application\app\src\main\assets\models\florence2
ls *.onnx
ls tokenizer.json
```

Powinny być:
- `vision_encoder.onnx` (lub `encoder_model.onnx`)
- `decoder_model_merged.onnx`
- `tokenizer.json`

#### ✅ Sprawdź nazwy plików w kodzie:

**Plik:** `Florence2OnnxProvider.kt`

```kotlin
private val visionEncoderSession: OrtSession by lazy {
    loadModelFromAssets("models/florence2/vision_encoder.onnx")  // ← Sprawdź nazwę
}

private val decoderSession: OrtSession by lazy {
    loadModelFromAssets("models/florence2/decoder_model_merged.onnx")  // ← Sprawdź nazwę
}
```

Jeśli Twoje pliki mają inne nazwy (np. `encoder_model.onnx`), zmień w kodzie.

#### ✅ Sync Gradle po dodaniu plików:

1. `File > Sync Project with Gradle Files`
2. `Build > Clean Project`
3. `Build > Rebuild Project`

#### ✅ Sprawdź rozmiar plików:

```powershell
Get-ChildItem .\assets\models\florence2\*.onnx | Select Name, @{Name="MB";Expression={[math]::Round($_.Length/1MB,1)}}
```

Oczekiwane rozmiary:
- `vision_encoder.onnx`: ~100 MB
- `decoder_model_merged.onnx`: ~115 MB

Jeśli pliki są bardzo małe (<1 MB), mogą być uszkodzone lub nie w pełni pobrane.

---

## Problem: Błędy ONNX Runtime

### Typ 1: "Graph input not found"

**Objawy:**
```
ONNXRuntimeException: Graph input 'pixel_values' not found
```

**Przyczyna:** Model ma inne nazwy wejść niż oczekiwane.

**Rozwiązanie:** Sprawdź logi session info:
```
I/Florence2ONNX: === Session Info ===
I/Florence2ONNX: Inputs:
I/Florence2ONNX:   - image: TensorInfo(...)  ← Może być 'image' zamiast 'pixel_values'
```

Zmień nazwy w kodzie:
```kotlin
val inputs = mapOf(
    "image" to pixelValuesTensor  // Zamiast "pixel_values"
)
```

### Typ 2: "Shape mismatch"

**Objawy:**
```
ONNXRuntimeException: Expected shape [1, 3, 768, 768] but got [1, 3, 384, 384]
```

**Rozwiązanie:** Zmień `IMAGE_SIZE` w `Florence2OnnxProvider.kt`:
```kotlin
companion object {
    private const val IMAGE_SIZE = 384  // Było: 768
}
```

---

## Diagnostyka krok po kroku

### 1. Sprawdź instalację modelu

```bash
# Struktura katalogów
cd c:\Studia\mgr\Application\app\src\main\assets
tree models

# Powinno być:
# models/
# └── florence2/
#     ├── onnx/
#     │   ├── vision_encoder.onnx
#     │   └── decoder_model_merged.onnx
#     └── tokenizer.json
```

### 2. Sprawdź logi podczas uruchamiania

**Filtruj Logcat** w Android Studio:
- Tag: `Florence2ONNX`
- Tag: `Florence2Tokenizer`

**Oczekiwane logi (sukces):**
```
I/Florence2ONNX: Loading model: models/florence2/vision_encoder.onnx
I/Florence2ONNX: Copying model to cache: vision_encoder.onnx
I/Florence2ONNX: Model copied to cache: /data/user/0/.../cache/vision_encoder.onnx (100 MB)
I/Florence2ONNX: Loading from cache: /data/user/0/.../cache/vision_encoder.onnx
I/Florence2ONNX: Model session created successfully from cache
I/Florence2ONNX: === Session Info ===
I/Florence2ONNX: Inputs:
I/Florence2ONNX:   - pixel_values: TensorInfo(shape=[1, 3, 768, 768], type=FLOAT)
I/Florence2ONNX: Outputs:
I/Florence2ONNX:   - last_hidden_state: TensorInfo(...)
```

### 3. Test prostego obrazu

Użyj małego obrazu (np. 640x480) z galerii.
Sprawdź metryki w outputcie aplikacji.

### 4. Wyczyść cache jeśli problemy

```kotlin
// W MainActivity onCreate (temporary debug)
appContext.cacheDir.listFiles()?.forEach { it.delete() }
```

Lub ręcznie:
```bash
adb shell
cd /data/data/thesis.wut.application.captionlab/cache
rm *.onnx
```

---

## Kontakt

Jeśli problem nadal występuje:
1. Sprawdź pełne logi Logcat (tag: `Florence2*`)
2. Sprawdź `Build > Build Output` w Android Studio
3. Upewnij się, że masz najnowszą wersję kodu (git pull)

**Ostatnia aktualizacja:** 12 listopada 2025
