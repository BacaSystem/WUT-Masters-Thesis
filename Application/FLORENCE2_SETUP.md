# Instrukcje: Pobieranie i instalacja modelu Florence-2 ONNX

## 1. Informacje o modelu

**Model:** Florence-2-base-ft (fine-tuned)  
**Źródło:** [onnx-community/Florence-2-base-ft](https://huggingface.co/onnx-community/Florence-2-base-ft)  
**Rozmiar:** ~230 MB (base model)  
**Format:** ONNX  
**Licencja:** MIT  

### Możliwości modelu:
- Image Captioning (`<CAPTION>`, `<DETAILED_CAPTION>`, `<MORE_DETAILED_CAPTION>`)
- Object Detection (`<OD>`)
- Dense Region Captioning (`<DENSE_REGION_CAPTION>`)
- Grounding, Segmentation i inne zadania vision-language

## 2. Opcja A: Pobieranie przez Git LFS (zalecane)

### Wymagania:
- Git (zainstalowany)
- Git LFS: https://git-lfs.github.com/

### Kroki:

```bash
# 1. Zainstaluj Git LFS (jeśli nie masz)
# Windows (przez Git Bash lub PowerShell):
git lfs install

# 2. Sklonuj repozytorium modelu
cd c:\Studia\mgr\Application\app\src\main\assets
mkdir models
cd models

git clone https://huggingface.co/onnx-community/Florence-2-base-ft florence2

# 3. Poczekaj na pobranie (może zająć kilka minut - ~230 MB)
```

### Struktura po pobraniu:
```
assets/
└── models/
    └── florence2/
        ├── onnx/
        │   ├── decoder_model_merged.onnx          (~115 MB)
        │   ├── encoder_model.onnx                 (~100 MB)
        │   └── vision_encoder.onnx                (~100 MB)
        ├── tokenizer.json
        ├── config.json
        └── ... (inne pliki)
```

## 3. Opcja B: Pobieranie manualne przez przeglądarkę

Jeśli Git LFS nie działa, możesz pobrać pliki ręcznie:

### Krok 1: Otwórz stronę modelu
https://huggingface.co/onnx-community/Florence-2-base-ft/tree/main

### Krok 2: Pobierz wymagane pliki

Przejdź do folderu `onnx/` i pobierz:

1. **vision_encoder.onnx** (lub `encoder_model.onnx`)
   - Link: https://huggingface.co/onnx-community/Florence-2-base-ft/resolve/main/onnx/vision_encoder.onnx
   - Rozmiar: ~100 MB

2. **decoder_model_merged.onnx**
   - Link: https://huggingface.co/onnx-community/Florence-2-base-ft/resolve/main/onnx/decoder_model_merged.onnx
   - Rozmiar: ~115 MB

3. **tokenizer.json** (z głównego folderu)
   - Link: https://huggingface.co/onnx-community/Florence-2-base-ft/resolve/main/tokenizer.json
   - Rozmiar: ~2 MB

### Krok 3: Umieść pliki w projekcie

Skopiuj pobrane pliki do struktury:

```
c:\Studia\mgr\Application\app\src\main\assets\
└── models\
    └── florence2\
        ├── vision_encoder.onnx
        ├── decoder_model_merged.onnx
        └── tokenizer.json
```

**UWAGA:** Upewnij się, że nazwy plików są dokładnie takie jak powyżej!

## 4. Opcja C: Użycie mniejszego modelu (dla ograniczonej pamięci)

Jeśli pełny model jest za duży, możesz użyć kwantyzowanej wersji:

**Model:** jeiku/Florence-2-base-ft-onnx-q4  
**Link:** https://huggingface.co/jeiku/Florence-2-base-ft-onnx-q4  
**Rozmiar:** ~60 MB (quantized to 4-bit)  

```bash
git clone https://huggingface.co/jeiku/Florence-2-base-ft-onnx-q4 florence2
```

**Trade-off:** Mniejszy model, szybsza inferencja, ale nieco niższa jakość.

## 5. Weryfikacja instalacji

### Sprawdź strukturę plików:

```powershell
# W PowerShell:
cd c:\Studia\mgr\Application\app\src\main\assets\models\florence2
ls

# Powinny być widoczne:
# - vision_encoder.onnx (lub encoder_model.onnx)
# - decoder_model_merged.onnx
# - tokenizer.json
```

### Sprawdź rozmiary plików:

```powershell
Get-ChildItem .\models\florence2\*.onnx | Select-Object Name, @{Name="Size(MB)";Expression={[math]::Round($_.Length/1MB, 2)}}
```

Oczekiwane rozmiary:
- `vision_encoder.onnx`: ~100 MB
- `decoder_model_merged.onnx`: ~115 MB
- `tokenizer.json`: ~2 MB

## 6. Alternatywne nazwy plików

Jeśli w repozytorium znajdujesz inne nazwy plików, możesz je przemianować:

### Encoder:
- `encoder_model.onnx` → `vision_encoder.onnx`

### Decoder:
- `decoder_model.onnx` → `decoder_model_merged.onnx`

Lub zmień nazwy w kodzie `Florence2OnnxProvider.kt`:

```kotlin
private val visionEncoderSession: OrtSession by lazy {
    loadModelFromAssets("models/florence2/encoder_model.onnx") // Zmień tutaj
}
```

## 7. Testowanie w aplikacji

### Uruchom aplikację:
1. Otwórz projekt w Android Studio
2. Sync Gradle
3. Uruchom aplikację na emulatorze lub urządzeniu
4. Wybierz obraz
5. Wybierz "Florence-2 ONNX (local)"
6. Kliknij "Generuj opis"

### Sprawdź logi:

```kotlin
// W Logcat (Android Studio) filtruj po tag: "Florence2ONNX"
// Powinny pojawić się komunikaty:
// I/Florence2ONNX: Loading model: models/florence2/vision_encoder.onnx
// I/Florence2ONNX: Model loaded successfully
// I/Florence2ONNX: Caption generated: [opis obrazu]
```

## 8. Rozwiązywanie problemów

### Problem: "FileNotFoundException: models/florence2/vision_encoder.onnx"
**Rozwiązanie:**
- Sprawdź dokładną ścieżkę i nazwę pliku
- Upewnij się, że pliki są w folderze `src/main/assets/models/florence2/`
- Sprawdź, czy nie ma literówek w nazwach

### Problem: "Out of Memory" podczas ładowania modelu
**Rozwiązanie:**
- Użyj kwantyzowanego modelu (q4 lub q8)
- Zwiększ heap size w `gradle.properties`:
  ```
  android.enableJetifier=true
  org.gradle.jvmargs=-Xmx4096m
  ```
- Testuj na prawdziwym urządzeniu zamiast emulatora

### Problem: Bardzo długi czas inferencji (> 30s)
**Rozwiązanie:**
- Użyj prawdziwego urządzenia (emulator jest wolniejszy)
- Włącz optymalizacje ONNX Runtime
- Rozważ użycie GPU delegate (jeśli dostępne)

### Problem: Model generuje puste opisy
**Rozwiązanie:**
- Sprawdź czy `tokenizer.json` jest poprawnie załadowany
- Sprawdź logi pod kątem błędów dekodowania
- Upewnij się, że używasz poprawnego promptu (np. `<DETAILED_CAPTION>`)

## 9. Dataset COCO (opcjonalnie - do testów jakości)

### Pobierz COCO Validation 2017:

```bash
# Obrazy (6.2 GB)
wget http://images.cocodataset.org/zips/val2017.zip
unzip val2017.zip

# Adnotacje (241 MB)
wget http://images.cocodataset.org/annotations/annotations_trainval2017.zip
unzip annotations_trainval2017.zip
```

### Lub mniejsza próbka (zalecane do testów):

Wybierz losowo 100 obrazów z `val2017/` i skopiuj do:
```
c:\Studia\mgr\Application\datasets\coco_val_subset\images\
```

Wyciągnij odpowiadające adnotacje z `annotations/captions_val2017.json`

## 10. Dodatkowe zasoby

### HuggingFace Spaces (demo online):
- https://huggingface.co/spaces/Xenova/florence2-webgpu

### Dokumentacja:
- Florence-2 Paper: https://arxiv.org/abs/2311.06242
- ONNX Runtime Android: https://onnxruntime.ai/docs/tutorials/mobile/

### Community Support:
- HuggingFace Discussions: https://huggingface.co/onnx-community/Florence-2-base-ft/discussions
- ONNX Runtime Issues: https://github.com/microsoft/onnxruntime/issues

---

## Szybkie podsumowanie (TL;DR)

```bash
# 1. Przejdź do assets
cd c:\Studia\mgr\Application\app\src\main\assets

# 2. Utwórz folder
mkdir models

# 3. Pobierz model
cd models
git lfs install
git clone https://huggingface.co/onnx-community/Florence-2-base-ft florence2

# 4. Sprawdź czy pliki istnieją
ls florence2/onnx/*.onnx
ls florence2/tokenizer.json

# 5. Uruchom aplikację w Android Studio
```

**Gotowe!** Model powinien działać w aplikacji.
