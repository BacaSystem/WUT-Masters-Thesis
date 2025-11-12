# Projekt Aplikacji Dyplomowej: CaptionLab

## Informacje podstawowe

**Temat pracy:**  
*Analiza efektywności lokalnych i chmurowych modeli AI do generowania opisów obrazów w aplikacjach mobilnych*

**Autor:** Dominik Baczyński (300475)  
**Promotor:** dr inż. Piotr Bobiński  
**Kierunek:** Informatyka  
**Specjalność:** Systemy Internetowe Wspomagania Zarządzania  
**Wydział:** Elektroniki i Technik Informacyjnych, Politechnika Warszawska

---

## 1. Cel i zakres pracy

### 1.1. Cel główny
Zbadanie efektywności rozwiązań wykorzystujących lokalne i chmurowe modele sztucznej inteligencji w generowaniu opisów obrazów (image captioning) w środowisku mobilnym Android.

### 1.2. Cele szczegółowe
1. Implementacja aplikacji testowej **CaptionLab** na platformę Android (Kotlin/Java)
2. Integracja i porównanie 3-4 rozwiązań chmurowych (Azure Vision, Google Vertex AI Gemini, AWS Bedrock Claude, OpenAI GPT-4o)
3. Integracja i porównanie 3-4 rozwiązań lokalnych (Florence-2 ONNX, LightCap TFLite, Moondream ONNX, opcjonalnie Google Gemini Nano)
4. Analiza i porównanie:
   - Jakości generowanych opisów (metryki: CIDEr, SPICE, BLEU, METEOR, BERTScore)
   - Czasu przetwarzania (latencja end-to-end, percentyle p50/p90)
   - Zużycia zasobów sprzętowych (RAM, energia)
   - Kosztów operacyjnych (rozwiązania chmurowe)
5. Określenie optymalnych scenariuszy zastosowań dla każdego typu rozwiązania

### 1.3. Zakres funkcjonalny aplikacji
- Wybór i wczytanie obrazu z galerii lub aparatu
- Generowanie opisów przez różne providery (lokalne i chmurowe)
- Automatyczne zbieranie metryk wydajnościowych
- Eksport wyników do formatów CSV/JSON
- Wsparcie dla testów batchowych (wiele obrazów)
- Konfiguracja parametrów testowych

---

## 2. Stan obecny projektu (PoC)

### 2.1. Zrealizowane komponenty

#### Architektura podstawowa
```kotlin
// Interfejs providera
interface CaptioningProvider {
    val id: String
    suspend fun caption(bitmap: Bitmap): CaptionResult
}

data class CaptionResult(
    val text: String,
    val extra: Map<String, Any?> = emptyMap()
)
```

#### Zaimplementowane providery
1. **CloudProviderOpenAI** - integracja z OpenAI GPT-4o (vision)
   - Obsługa kompresji JPEG + base64
   - Wywołania REST API
   - Logowanie błędów i odpowiedzi

2. **OnnxFlorenceProvider** - Florence-2 lokalnie przez ONNX Runtime
   - Preprocessing obrazu (384x384, NCHW)
   - Encoder-decoder architecture
   - Greedy decoding z obsługą BOS/EOS
   - Detokenizacja SPM
   - Szczegółowe metryki (pre_ms, enc_ms, dec_ms, post_ms, e2e_ms)

3. **LocalProviderTFLite** / **LocalProviderTFLiteNoMeta** - próby z TensorFlow Lite
   - Image classification jako baseline

#### MainActivity - podstawowy UI
- Wybór obrazu (ACTION_PICK)
- Podgląd obrazu (Coil)
- Konfiguracja klucza API (SharedPreferences)
- Uruchamianie inference
- Wyświetlanie wyników z metrykami czasowymi

#### Narzędzia pomocnicze (ONNX)
- `ImagePreprocessor` - normalizacja i konwersja do NCHW
- `VocabLoader` - ładowanie słownika tokenów
- `SpmDetokenizer` - dekodowanie tokenów do tekstu
- `OnnxIOInspector` - inspekcja tensorów wejściowych/wyjściowych

### 2.2. Zależności technologiczne
```kotlin
// Build.gradle.kts
- Kotlin + Coroutines (Dispatchers.IO)
- Android SDK 26-36
- OkHttp + Moshi (JSON, REST)
- Coil (ładowanie obrazów)
- ONNX Runtime Android 1.23.2
- TensorFlow Lite (Task Vision, Support, Metadata)
```

---

## 3. Architektura docelowa aplikacji CaptionLab

### 3.1. Diagram komponentów

```
┌─────────────────────────────────────────────────────────────┐
│                      CaptionLab App                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐    │
│  │   UI Layer   │  │ Benchmark    │  │  Data Export    │    │
│  │ (Activities, │  │ Runner       │  │  (CSV/JSON)     │    │
│  │  Fragments)  │  │              │  │                 │    │ 
│  └──────────────┘  └──────────────┘  └─────────────────┘    │
│         │                  │                    │           │
│  ┌──────────────────────────────────────────────────────┐   │
│  │          Provider Manager & Metrics Collector        │   │
│  └──────────────────────────────────────────────────────┘   │
│         │                                                   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           CaptioningProvider Interface               │   │
│  └──────────────────────────────────────────────────────┘   │
│         │                                                   │
│  ┌──────┴────────────────────────────────────────┐          │
│  │                                               │          │
│  ▼  Local Providers              Cloud Providers ▼          │
│  ┌────────────────────────┐    ┌───────────────────────┐    │
│  │ OnnxFlorenceProvider   │    │ AzureVisionProvider   │    │
│  │ OnnxMoondreamProvider  │    │ VertexGeminiProvider  │    │
│  │ TFLiteLightCapProvider │    │ BedrockClaudeProvider │    │
│  │ GeminiNanoProvider(*)  │    │ OpenAIGPTProvider     │    │
│  └────────────────────────┘    └───────────────────────┘    │
│         │                                │                  │
│  ┌──────┴────────┐              ┌────────┴──────┐           │
│  │ ONNX Runtime  │              │  HTTP Client  │           │
│  │ TFLite/LiteRT │              │  (OkHttp)     │           │
│  │ ML Kit GenAI  │              │  Backend API  │           │
│  └───────────────┘              └───────────────┘           │
│                                                             │
└─────────────────────────────────────────────────────────────┘

(*) Wymaga urządzenia z obsługą AICore - Samsung Galaxy S24
```

### 3.2. Warstwy aplikacji

#### UI Layer
- **MainActivity** - główny ekran, wybór obrazu, uruchamianie testów
- **BatchTestActivity** - ekran testów batchowych
- **ResultsActivity** - przeglądanie i analiza wyników
- **SettingsActivity** - konfiguracja API keys, parametrów

#### Business Logic Layer
- **ProviderManager** - zarządzanie dostępnymi providerami
- **BenchmarkRunner** - orkiestracja testów, warm-up, pomiary
- **MetricsCollector** - zbieranie metryk (czas, RAM, energia)
- **DataExporter** - eksport do CSV/JSON

#### Provider Layer
- Implementacje interfejsu `CaptioningProvider`
- Izolacja logiki komunikacji z modelami
- Standaryzowany zwrot wyników

---

## 4. Interfejs użytkownika (UI/UX)

### 4.1. Ekran główny (MainActivity)

```
┌─────────────────────────────────────┐
│  CaptionLab                    ⚙️   │
├─────────────────────────────────────┤
│                                     │
│   ┌───────────────────────────┐    │
│   │                           │    │
│   │   [Podgląd obrazu]        │    │
│   │                           │    │
│   └───────────────────────────┘    │
│                                     │
│   [📷 Wybierz obraz]                │
│                                     │
│   Wybierz provider:                 │
│   ○ Florence-2 (ONNX, local)       │
│   ○ Moondream (ONNX, local)        │
│   ○ Azure Vision (cloud)           │
│   ○ Vertex Gemini (cloud)          │
│   ○ OpenAI GPT-4o (cloud)          │
│                                     │
│   [▶️ Generuj opis]                 │
│                                     │
│   ┌─────────────────────────────┐  │
│   │ Wyniki:                     │  │
│   │ Provider: Florence-2        │  │
│   │ Opis: A dog sitting in...   │  │
│   │                             │  │
│   │ Metryki:                    │  │
│   │ • pre_ms: 45.2              │  │
│   │ • infer_ms: 523.7           │  │
│   │ • post_ms: 12.1             │  │
│   │ • e2e_ms: 581.0             │  │
│   │ • RAM peak: 234 MB          │  │
│   └─────────────────────────────┘  │
│                                     │
│   [📊 Testy batchowe]               │
│   [📁 Eksport wyników]              │
└─────────────────────────────────────┘
```

### 4.2. Ekran testów batchowych

```
┌─────────────────────────────────────┐
│  ← Testy batchowe                   │
├─────────────────────────────────────┤
│                                     │
│  Dataset:                           │
│  ⦿ COCO validation (100 obrazów)   │
│  ○ Flickr30k subset (50 obrazów)   │
│  ○ Niestandardowy folder           │
│                                     │
│  Providery do testowania:           │
│  ☑ Florence-2 (local)               │
│  ☑ Azure Vision (cloud)             │
│  ☑ Vertex Gemini (cloud)            │
│  ☐ Moondream (local)                │
│                                     │
│  Parametry:                         │
│  • Warm-up runs: [3]                │
│  • Powtórzenia: [5]                 │
│  • Timeout (s): [30]                │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ Postęp: 23/100 (23%)        │   │
│  │ ████████░░░░░░░░░░░░░░░░░   │   │
│  │                             │   │
│  │ Aktualnie:                  │   │
│  │ Florence-2 → img_00023.jpg  │   │
│  │ Czas: 542 ms                │   │
│  └─────────────────────────────┘   │
│                                     │
│  [⏸ Pauza]  [⏹ Stop]  [📄 Log]     │
│                                     │
└─────────────────────────────────────┘
```

### 4.3. Ekran ustawień

```
┌─────────────────────────────────────┐
│  ← Ustawienia                       │
├─────────────────────────────────────┤
│                                     │
│  Cloud API Keys                     │
│  ┌─────────────────────────────┐   │
│  │ OpenAI API Key:             │   │
│  │ [sk-proj-...] 👁 💾          │   │
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ Azure Vision Key:           │   │
│  │ [...] 👁 💾                   │   │
│  └─────────────────────────────┘   │
│  ┌─────────────────────────────┐   │
│  │ Google Cloud Key:           │   │
│  │ [...] 👁 💾                   │   │
│  └─────────────────────────────┘   │
│                                     │
│  Local Models                       │
│  ☑ Użyj GPU delegate (TFLite)      │
│  ☑ Użyj QNN EP (ONNX/Snapdragon)   │
│  ☐ Log szczegółowy ONNX            │
│                                     │
│  Benchmark Settings                 │
│  • Monitoruj energię: ☑            │
│  • Auto-export wyników: ☑          │
│  • Format eksportu:                │
│    ⦿ CSV  ○ JSON  ○ Oba            │
│                                     │
│  [🗑️ Wyczyść cache modeli]          │
│  [📂 Ścieżka eksportu]              │
│                                     │
└─────────────────────────────────────┘
```

---

## 5. Flow badań i zbieranie danych

### 5.1. Przepływ pojedynczego testu

```
┌─────────────────┐
│ Wybór obrazu    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Preprocessing   │ ← t0: start
│ (resize, JPEG)  │
└────────┬────────┘
         │         ← t1: pre_end
         ▼
┌─────────────────┐
│ Inference       │ ← Lokalnie: encoder + decoder
│ (Provider)      │ ← Chmura: HTTP request + response
└────────┬────────┘
         │         ← t2: infer_end
         ▼
┌─────────────────┐
│ Postprocessing  │ ← Detokenizacja, formatowanie
│                 │
└────────┬────────┘
         │         ← t3: post_end
         ▼
┌─────────────────┐
│ Zbieranie       │ ← Metryki:
│ metryk          │   • pre_ms = (t1-t0)/1e6
└────────┬────────┘   • infer_ms = (t2-t1)/1e6
         │            • post_ms = (t3-t2)/1e6
         │            • e2e_ms = (t3-t0)/1e6
         ▼            • RAM: Runtime.totalMemory()
┌─────────────────┐   • Energia: Power Profiler
│ Wyświetlenie    │
│ + zapis         │
└─────────────────┘
```

### 5.2. Zbierane metryki

#### A. Metryki wydajnościowe (Performance)

| Metryka | Jednostka | Opis | Źródło |
|---------|-----------|------|--------|
| `pre_ms` | ms | Czas preprocessingu (decode, resize, normalizacja) | SystemClock.elapsedRealtimeNanos() |
| `infer_ms` | ms | Czas czystej inferencji (encoder+decoder lub HTTP) | SystemClock.elapsedRealtimeNanos() |
| `post_ms` | ms | Czas postprocessingu (detokenizacja) | SystemClock.elapsedRealtimeNanos() |
| `e2e_ms` | ms | Czas end-to-end całego procesu | SystemClock.elapsedRealtimeNanos() |
| `latency_p50` | ms | Mediana czasu e2e (5+ powtórzeń) | Percentyl 50 |
| `latency_p90` | ms | 90. percentyl czasu e2e | Percentyl 90 |
| `throughput` | img/s | Przepustowość (1000/latency_p50) | Obliczane |

#### B. Metryki zasobów (Resources)

| Metryka | Jednostka | Opis | Źródło |
|---------|-----------|------|--------|
| `ram_peak_mb` | MB | Szczytowe zużycie RAM | Runtime.totalMemory() - freeMemory() |
| `model_size_mb` | MB | Rozmiar pliku modelu (.onnx/.tflite) | File.length() |
| `apk_size_mb` | MB | Rozmiar APK z modelami | Build artifacts |
| `energy_mwh` | mWh | Energia zużyta na 1 opis | Android Studio Power Profiler |

#### C. Metryki jakości (Quality) - offline evaluation

| Metryka | Zakres | Opis | Narzędzie |
|---------|--------|------|-----------|
| `CIDEr` | 0-10+ | Consensus-based Image Description Evaluation | pycocoevalcap |
| `SPICE` | 0-1 | Semantic Propositional Image Caption Evaluation | pycocoevalcap |
| `BLEU-4` | 0-1 | Bilingual Evaluation Understudy | nltk.translate |
| `METEOR` | 0-1 | Metric for Evaluation of Translation with Explicit ORdering | nltk.translate |
| `BERTScore` | 0-1 | Similarity based on BERT embeddings | bert-score library |

#### D. Metryki kosztów (Cost) - tylko chmura

| Metryka | Jednostka | Opis | Źródło |
|---------|-----------|------|--------|
| `request_size_kb` | KB | Rozmiar zapytania (image base64) | HTTP body length |
| `response_size_kb` | KB | Rozmiar odpowiedzi | HTTP body length |
| `tokens_input` | tokens | Liczba tokenów wejściowych (dla LLM) | API response |
| `tokens_output` | tokens | Liczba tokenów wyjściowych | API response |
| `cost_per_call_usd` | USD | Koszt pojedynczego wywołania | Cennik providera |

### 5.3. Format eksportu danych

#### CSV (wyniki_batch_YYYYMMDD_HHMMSS.csv)
```csv
timestamp,provider_id,image_id,caption_text,pre_ms,infer_ms,post_ms,e2e_ms,ram_peak_mb,energy_mwh,cost_usd
2025-11-11T10:15:23,onnx_florence2_local,coco_val_000001,A dog sitting on grass,45.2,523.7,12.1,581.0,234,12.5,0.0
2025-11-11T10:15:24,azure_vision_cloud,coco_val_000001,A brown dog is sitting in the grass,8.3,342.1,2.1,352.5,85,3.2,0.002
```

#### JSON (wyniki_batch_YYYYMMDD_HHMMSS.json)
```json
{
  "metadata": {
    "app_version": "1.0",
    "android_version": "14",
    "device_model": "Samsung Galaxy S24",
    "timestamp": "2025-11-11T10:15:00Z",
    "dataset": "COCO_val_subset_100"
  },
  "results": [
    {
      "image_id": "coco_val_000001",
      "image_path": "/sdcard/DCIM/test/000001.jpg",
      "providers": {
        "onnx_florence2_local": {
          "caption": "A dog sitting on grass",
          "metrics": {
            "pre_ms": 45.2,
            "infer_ms": 523.7,
            "post_ms": 12.1,
            "e2e_ms": 581.0,
            "ram_peak_mb": 234,
            "energy_mwh": 12.5
          }
        },
        "azure_vision_cloud": {
          "caption": "A brown dog is sitting in the grass",
          "metrics": {
            "pre_ms": 8.3,
            "infer_ms": 342.1,
            "post_ms": 2.1,
            "e2e_ms": 352.5,
            "ram_peak_mb": 85,
            "energy_mwh": 3.2,
            "cost_usd": 0.002
          }
        }
      }
    }
  ]
}
```

---

## 6. Planowane providery do implementacji

### 6.1. Rozwiązania chmurowe (3-4)

#### 1. Azure AI Vision - Image Analysis 4.0 ✅ PRIORYTET
- **Endpoint:** `https://[endpoint].cognitiveservices.azure.com/computervision/imageanalysis:analyze`
- **Funkcje:** Caption, Dense Captions (z bbox)
- **Model:** Florence-2 based
- **Uwagi:** Najbardziej kompletne rozwiązanie, dobre jako punkt odniesienia
- **Dokumentacja:** [Azure Computer Vision Docs](https://learn.microsoft.com/en-us/azure/ai-services/computer-vision/concept-describe-images-40)

#### 2. Google Vertex AI - Gemini (multimodal) ✅ PRIORYTET
- **Endpoint:** `https://[region]-aiplatform.googleapis.com/v1/projects/[project]/locations/[location]/publishers/google/models/gemini-pro-vision:predict`
- **Funkcje:** Image understanding przez prompt
- **Uwagi:** Elastyczne, możliwość kontroli promptu
- **Dokumentacja:** [Vertex AI Gemini](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/image-understanding)

#### 3. AWS Bedrock - Claude 3.5 Sonnet (vision) ⭐ ZALECANE
- **Endpoint:** `https://bedrock-runtime.[region].amazonaws.com/model/anthropic.claude-3-5-sonnet/invoke`
- **Funkcje:** Multimodal LLM z opisem obrazów
- **Uwagi:** Bardzo dobre wyniki, łatwe logowanie kosztów przez Bedrock
- **Dokumentacja:** [AWS Bedrock Claude](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-claude.html)

#### 4. OpenAI GPT-4o (vision) ✅ CZĘŚCIOWO ZAIMPLEMENTOWANE
- **Endpoint:** `https://api.openai.com/v1/chat/completions`
- **Funkcje:** Vision przez Chat Completions
- **Uwagi:** Bardzo popularne, ale wymaga poprawki struktury image_url
- **Status:** Już zaimplementowane w `CloudProviderOpenAI.kt`
- **Dokumentacja:** [OpenAI Vision Guide](https://platform.openai.com/docs/guides/vision)

**Rekomendacja:** Azure + Gemini + Claude (3 providery, różne podejścia)

### 6.2. Rozwiązania lokalne (3-4)

#### 1. Florence-2 (ONNX Runtime) ✅ CZĘŚCIOWO ZAIMPLEMENTOWANE
- **Model:** microsoft/florence-2-base lub florence-2-base-ft
- **Format:** ONNX (encoder.onnx + decoder.onnx)
- **Rozmiar:** ~250 MB (base), możliwa kwantyzacja INT8 → ~130 MB
- **Obsługa:** Caption, Dense Captions, Object Detection
- **Status:** Implementacja w `OnnxFlorenceProvider.kt` (wymaga debugowania)
- **Uwagi:** Uniwersalny VLM, dobry punkt odniesienia lokalny
- **Źródło:** [Florence-2 HuggingFace](https://huggingface.co/microsoft/Florence-2-base)

#### 2. Moondream (0.5B / 1.9B) - ONNX Runtime ⭐ ZALECANE
- **Model:** vikhyatk/moondream2 (wersja 0.5B lub 1.9B)
- **Format:** ONNX po konwersji
- **Rozmiar:** 0.5B → ~500 MB (FP16), ~250 MB (INT8)
- **Obsługa:** Image captioning, VQA
- **Uwagi:** Mały VLM projektowany dla edge devices, łatwa kwantyzacja
- **Źródło:** [Moondream HuggingFace](https://huggingface.co/vikhyatk/moondream2)

#### 3. LightCap - TFLite 🎯 NOWY
- **Model:** Efficient Image Captioning for Edge Devices
- **Format:** TensorFlow Lite (.tflite)
- **Rozmiar:** ~40-60 MB (zoptymalizowany dla mobile)
- **Obsługa:** Image captioning
- **Uwagi:** Raportowane 188 ms/obraz na CPU smartfona, bardzo dobry kandydat
- **Paper:** [arXiv:2212.08985](https://arxiv.org/abs/2212.08985)
- **Implementacja:** Wymaga konwersji z PyTorch do TFLite

#### 4. Google Gemini Nano - ML Kit GenAI 🔒 WYMAGA S24
- **Model:** Gemini Nano (on-device, AICore)
- **Format:** Systemowy (dostęp przez ML Kit GenAI API)
- **Rozmiar:** Zarządzany przez system
- **Obsługa:** Image description, multimodal prompting
- **Uwagi:** Wymaga Samsung Galaxy S24 lub Pixel 9+, Android 15+
- **Dostępność:** Ograniczona do wspieranych urządzeń
- **Dokumentacja:** [Android AI - Gemini Nano](https://developer.android.com/ai/gemini-nano)

**Rekomendacja dla MVP:** Florence-2 (ONNX) + Moondream (ONNX) + LightCap (TFLite)  
**Docelowo z S24:** + Gemini Nano

---

## 7. Metodologia badań

### 7.1. Zestawy danych (Datasets)

#### A. COCO Captions (validation subset) ✅ PRIORYTET
- **Zbiór:** MS COCO 2014/2017 validation
- **Liczba obrazów:** 100-500 (losowa próbka)
- **Referencje:** 5 opisów na obraz
- **Format:** JPEG, ~640x480
- **Ground truth:** `captions_val2014.json`
- **Źródło:** [COCO Dataset](https://cocodataset.org/)
- **Paper:** Lin et al., "Microsoft COCO: Common Objects in Context", ECCV 2014

#### B. Flickr30k (opcjonalnie)
- **Zbiór:** Flickr30k Entities
- **Liczba obrazów:** 50-100 (próbka)
- **Referencje:** 5 opisów na obraz
- **Uwagi:** Bardziej naturalne zdjęcia niż COCO
- **Źródło:** [Flickr30k](http://shannon.cs.illinois.edu/DenotationGraph/)

#### C. Nocaps (opcjonalnie - test "novel objects")
- **Zbiór:** Novel Object Captioning at Scale
- **Uwagi:** Test generalizacji na nowe obiekty
- **Źródło:** [nocaps](https://nocaps.org/)

**Rekomendacja:** Głównie COCO validation (100 obrazów), opcjonalnie Flickr30k (50 obrazów)

### 7.2. Procedura eksperymentalna

#### Faza 1: Weryfikacja funkcjonalna
1. Test pojedynczego obrazu na każdym providerze
2. Weryfikacja poprawności output (czy generuje tekst)
3. Podstawowe metryki czasu (warm-up + 3 pomiary)

#### Faza 2: Benchmark wydajnościowy
1. **Warm-up:** 3-5 uruchomień przed pomiarem
2. **Pomiary:** 5-10 powtórzeń na obraz
3. **Aggregacja:** percentyle p50, p90, p95
4. **Warunki:**
   - Tryb CPU only
   - Tryb GPU/QNN (jeśli dostępne)
   - Różne rozdzielczości (224x224, 384x384, 512x512)

#### Faza 3: Benchmark jakościowy (offline)
1. Generowanie opisów dla datasetu walidacyjnego
2. Zapis do formatu zgodnego z pycocoevalcap
3. Obliczenie metryk: CIDEr, SPICE, BLEU, METEOR, BERTScore
4. Analiza korelacji metryki automatyczne vs. subjective (opcjonalnie)

#### Faza 4: Pomiar energii
1. Wykorzystanie Android Studio Power Profiler
2. Pomiar energii dla 10+ obrazów w batch
3. Normalizacja: mWh/obraz
4. Porównanie local vs cloud

#### Faza 5: Analiza kosztów (chmura)
1. Logowanie rozmiaru request/response
2. Zliczanie tokenów (dla LLM)
3. Przeliczenie wg oficjalnych cenników
4. Projekcja kosztów: 1K, 10K, 100K obrazów

### 7.3. Konfiguracje sprzętowe

| Urządzenie | Procesor | RAM | Android | Uwagi |
|------------|----------|-----|---------|-------|
| **Emulator** | x86_64 (host CPU) | 4 GB | 14 | Testy wstępne, TFLite/ONNX CPU |
| **Samsung Galaxy S24** | Snapdragon 8 Gen 3 | 8 GB | 15 | Target device, QNN EP, Gemini Nano |
| (opcjonalnie) Pixel 9 | Tensor G4 | 12 GB | 15 | Gemini Nano native |

### 7.4. Narzędzia ewaluacji

#### A. Automatyczne metryki (Python)
```bash
# Środowisko Python
pip install pycocoevalcap
pip install bert-score
pip install nltk

# Skrypt ewaluacji
python evaluate_results.py \
  --results results/batch_20251111.json \
  --ground-truth coco_val_annotations.json \
  --output metrics_summary.csv
```

#### B. Pomiar energii (Android Studio)
- **Narzędzie:** Power Profiler (Android Studio)
- **Metryka:** Energy consumed (mWh)
- **Metodyka:** 
  1. Uruchomienie profiler przed testem
  2. Batch 10+ obrazów
  3. Eksport raportu energii
  4. Normalizacja: energia_total / liczba_obrazów

#### C. Pomiar wydajności (Macrobenchmark)
```kotlin
// app/benchmark module
@RunWith(AndroidJUnit4::class)
class CaptionBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun captionFlorence2() = benchmarkRule.measureRepeated(
        packageName = "thesis.wut.application.captionlab",
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        setupBlock = { /* load image */ }
    ) {
        // Run inference
    }
}
```

---

## 8. Plan implementacji (Roadmap)

### Milestone 1: Fundament architektury (1-2 tygodnie) ✅ CZĘŚCIOWO UKOŃCZONE
- [x] Interfejs `CaptioningProvider`
- [x] MainActivity z podstawowym UI
- [x] Integracja ONNX Runtime
- [x] Proof-of-concept: Florence-2 ONNX
- [x] Proof-of-concept: OpenAI cloud
- [ ] ProviderManager (rejestr dostępnych providerów)
- [ ] MetricsCollector (zbieranie i agregacja metryk)

### Milestone 2: Implementacja providerów lokalnych (2-3 tygodnie)
- [ ] Debugowanie i stabilizacja `OnnxFlorenceProvider`
  - [ ] Weryfikacja tokenizera/detokenizera
  - [ ] Obsługa różnych rozdzielczości
  - [ ] Kwantyzacja INT8
- [ ] `OnnxMoondreamProvider` (nowy)
  - [ ] Konwersja modelu do ONNX
  - [ ] Implementacja preprocessingu
  - [ ] Testy wydajnościowe
- [ ] `TFLiteLightCapProvider` (nowy)
  - [ ] Pozyskanie/konwersja modelu
  - [ ] Integracja GPU delegate
  - [ ] Benchmarking
- [ ] (opcjonalnie) `GeminiNanoProvider` - jeśli dostępne S24

### Milestone 3: Implementacja providerów chmurowych (1-2 tygodnie)
- [ ] `AzureVisionProvider`
  - [ ] REST API integration
  - [ ] Obsługa Caption + Dense Captions
  - [ ] Error handling, retry logic
- [ ] `VertexGeminiProvider`
  - [ ] Authentication (Service Account)
  - [ ] Multimodal request formatting
  - [ ] Response parsing
- [ ] `BedrockClaudeProvider`
  - [ ] AWS SDK integration lub REST
  - [ ] Vision message formatting
  - [ ] Cost tracking
- [x] Refactoring `CloudProviderOpenAI` (poprawka image_url structure)

### Milestone 4: System pomiarowy (1-2 tygodnie)
- [ ] `BenchmarkRunner`
  - [ ] Warm-up logic
  - [ ] Multiple runs z percentylami
  - [ ] Timeout handling
- [ ] `MetricsCollector` - rozszerzenie
  - [ ] RAM monitoring (Runtime API)
  - [ ] Integracja z Power Profiler (manual workflow)
  - [ ] Storage metrics (model size, APK size)
- [ ] `DataExporter`
  - [ ] CSV export
  - [ ] JSON export
  - [ ] Metadata embedding
- [ ] UI dla testów batchowych (BatchTestActivity)

### Milestone 5: Testy i zbieranie danych (2-3 tygodnie)
- [ ] Przygotowanie datasetów (COCO val subset)
- [ ] Uruchomienie benchmark na emulatorze
- [ ] Uruchomienie benchmark na Samsung S24
- [ ] Zbieranie wyników (3+ konfiguracje × 100+ obrazów)
- [ ] Eksport do formatu pycocoevalcap
- [ ] Pomiar energii (Power Profiler sessions)

### Milestone 6: Ewaluacja jakości (offline) (1 tydzień)
- [ ] Setup środowiska Python (pycocoevalcap)
- [ ] Skrypty ewaluacji
- [ ] Obliczenie CIDEr, SPICE, BLEU, METEOR
- [ ] (opcjonalnie) BERTScore
- [ ] Analiza korelacji metryk
- [ ] Wizualizacje (wykresy, tabele)

### Milestone 7: Analiza kosztów i energii (1 tydzień)
- [ ] Agregacja danych energii
- [ ] Przeliczenie kosztów chmury (cenniki)
- [ ] Analiza TCO (Total Cost of Ownership)
- [ ] Projekcje scenariuszy (1K, 10K, 100K obrazów/dzień)

### Milestone 8: Dokumentacja i praca dyplomowa (2-3 tygodnie)
- [ ] Finalizacja wyników
- [ ] Wykresy i tabele do pracy
- [ ] Pisanie rozdziałów (metodologia, wyniki, dyskusja)
- [ ] Przygotowanie prezentacji
- [ ] Przegląd literaturowy (update)

**Łączny szacowany czas:** 11-17 tygodni (2.5 - 4 miesiące)

---

## 9. Ryzyka i mitygacja

| Ryzyko | Prawdopodobieństwo | Wpływ | Mitygacja |
|--------|-------------------|-------|-----------|
| **Problemy z konwersją modeli do ONNX/TFLite** | Wysokie | Wysokie | Użycie gotowych exportów z HuggingFace, community scripts, fallback do innych modeli |
| **Brak dostępu do Gemini Nano (emulator)** | Pewne | Średnie | Testy tylko na S24, opcjonalne w ramach badań |
| **Wysokie koszty API chmurowych** | Średnie | Średnie | Limitowanie liczby wywołań, kredyty edukacyjne (Azure for Students), małe datasety |
| **Długie czasy inferencji lokalnie (> 10s)** | Średnie | Wysokie | Kwantyzacja INT8/INT4, mniejsze modele (Moondream 0.5B), optymalizacja preprocessingu |
| **Problemy z pomiarem energii** | Średnie | Średnie | Alternatywne metody (Batterystats), dokumentacja limitacji, fokus na czas/RAM |
| **Niewystarczająca pamięć na urządzeniu** | Niskie | Wysokie | Lazy loading modeli, czyszczenie cache, jeden model na raz |
| **Jakość opisów zbyt niska (lokalne modele)** | Średnie | Średnie | Akceptacja jako trade-off, dokumentacja, fokus na specific use-cases |
| **Problemy z dostępem do datasetów COCO** | Niskie | Średnie | Alternatywne datasety (Flickr30k), własne zdjęcia z annotacjami |

---

## 10. Wymagania techniczne

### 10.1. Wymagania deweloperskie

#### Software
- **Android Studio:** Ladybug | 2024.2.1 lub nowszy
- **JDK:** 11+
- **Kotlin:** 1.9+
- **Gradle:** 8.13
- **Android SDK:** API 26 (min) - API 36 (target)

#### Hardware (deweloperska stacja)
- **RAM:** min. 16 GB (zalecane 32 GB dla emulatora + Android Studio)
- **Dysk:** min. 50 GB wolnego miejsca (modele + datasety)
- **Procesor:** x86_64 z wirtualizacją (dla emulatora)

### 10.2. Wymagania uruchomieniowe (aplikacja)

#### Minimalne
- **Android:** 8.0 Oreo (API 26)
- **RAM:** 3 GB
- **Pamięć:** 2 GB wolnego miejsca (modele)
- **Procesor:** ARM64 lub x86_64

#### Zalecane
- **Android:** 14+ (API 34+)
- **RAM:** 8 GB
- **Pamięć:** 5 GB wolnego miejsca
- **Procesor:** Snapdragon 8 Gen 2+ (dla QNN EP)
- **Akceleracja:** GPU (Mali, Adreno), NPU (opcjonalnie)

### 10.3. Wymagane klucze API (do uzyskania)

1. **OpenAI:** [platform.openai.com](https://platform.openai.com/) ✅ POSIADANE
2. **Azure Computer Vision:** [Azure Portal](https://portal.azure.com/)
3. **Google Vertex AI:** [Google Cloud Console](https://console.cloud.google.com/)
4. **AWS Bedrock:** [AWS Console](https://console.aws.amazon.com/)

**Uwaga:** Wykorzystanie kredytów edukacyjnych (Azure for Students, Google Cloud Education Grants)

---

## 11. Struktura projektu (docelowa)

```
Application/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/thesis/wut/application/captionlab/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── BatchTestActivity.kt
│   │   │   │   ├── ResultsActivity.kt
│   │   │   │   ├── SettingsActivity.kt
│   │   │   │   ├── providers/
│   │   │   │   │   ├── CaptioningProvider.kt
│   │   │   │   │   ├── local/
│   │   │   │   │   │   ├── OnnxFlorenceProvider.kt
│   │   │   │   │   │   ├── OnnxMoondreamProvider.kt
│   │   │   │   │   │   ├── TFLiteLightCapProvider.kt
│   │   │   │   │   │   └── GeminiNanoProvider.kt (*)
│   │   │   │   │   └── cloud/
│   │   │   │   │       ├── AzureVisionProvider.kt
│   │   │   │   │       ├── VertexGeminiProvider.kt
│   │   │   │   │       ├── BedrockClaudeProvider.kt
│   │   │   │   │       └── CloudProviderOpenAI.kt
│   │   │   │   ├── benchmark/
│   │   │   │   │   ├── BenchmarkRunner.kt
│   │   │   │   │   ├── MetricsCollector.kt
│   │   │   │   │   └── DataExporter.kt
│   │   │   │   ├── data/
│   │   │   │   │   ├── BenchmarkResult.kt
│   │   │   │   │   ├── ImageDataset.kt
│   │   │   │   │   └── ResultsDatabase.kt (Room - opcjonalnie)
│   │   │   │   └── utils/
│   │   │   │       ├── onnx/ (istniejące)
│   │   │   │       ├── ImageUtils.kt
│   │   │   │       └── PermissionUtils.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   │   ├── activity_main.xml
│   │   │   │   │   ├── activity_batch_test.xml
│   │   │   │   │   ├── activity_results.xml
│   │   │   │   │   └── activity_settings.xml
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   └── themes.xml
│   │   │   │   └── models/
│   │   │   │       ├── florence2/
│   │   │   │       │   ├── encoder.onnx
│   │   │   │       │   ├── decoder.onnx
│   │   │   │       │   └── vocab.txt
│   │   │   │       ├── moondream/
│   │   │   │       │   └── moondream_0.5b_int8.onnx
│   │   │   │       └── lightcap/
│   │   │   │           └── lightcap.tflite
│   │   │   └── AndroidManifest.xml
│   │   ├── androidTest/ (Macrobenchmark)
│   │   └── test/
│   └── build.gradle.kts
├── benchmark/ (moduł Macrobenchmark)
│   ├── src/
│   │   └── main/
│   │       └── java/thesis/wut/application/benchmark/
│   │           └── CaptionBenchmark.kt
│   └── build.gradle.kts
├── backend/ (opcjonalny - proxy dla cloud APIs)
│   ├── main.py (FastAPI)
│   ├── requirements.txt
│   └── config.yaml
├── evaluation/ (Python scripts - offline)
│   ├── evaluate_results.py
│   ├── calculate_metrics.py
│   ├── visualize.py
│   └── requirements.txt
├── datasets/
│   ├── coco_val_subset/
│   │   ├── images/
│   │   └── annotations.json
│   └── flickr30k_subset/
├── results/
│   ├── batch_YYYYMMDD_HHMMSS.csv
│   ├── batch_YYYYMMDD_HHMMSS.json
│   └── metrics_summary.csv
├── docs/
│   ├── PROJEKT_APLIKACJI.md (ten dokument)
│   ├── USER_MANUAL.md
│   └── API_KEYS_SETUP.md
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 12. Przykładowe wyniki (placeholder)

### 12.1. Wydajność (latencja)

| Provider | Device | Mode | Latency p50 (ms) | Latency p90 (ms) | Throughput (img/s) |
|----------|--------|------|------------------|------------------|--------------------|
| Florence-2 (ONNX INT8) | S24 | CPU | 581 | 623 | 1.72 |
| Florence-2 (ONNX INT8) | S24 | QNN | 245 | 278 | 4.08 |
| Moondream 0.5B (INT8) | S24 | CPU | 423 | 456 | 2.36 |
| LightCap (TFLite) | S24 | CPU | 188 | 205 | 5.32 |
| LightCap (TFLite) | S24 | GPU | 95 | 112 | 10.53 |
| Azure Vision | S24 | Cloud | 352 | 421 | 2.84 |
| Vertex Gemini | S24 | Cloud | 387 | 456 | 2.58 |
| Bedrock Claude | S24 | Cloud | 412 | 489 | 2.43 |

### 12.2. Zasoby

| Provider | Model Size (MB) | RAM Peak (MB) | Energy (mWh/img) |
|----------|----------------|---------------|------------------|
| Florence-2 (INT8) | 130 | 234 | 12.5 |
| Moondream 0.5B | 250 | 312 | 8.7 |
| LightCap (TFLite) | 58 | 98 | 3.2 |
| Azure Vision | - | 85 | 2.8 |
| Vertex Gemini | - | 72 | 3.1 |
| Bedrock Claude | - | 78 | 3.4 |

### 12.3. Jakość (COCO val subset)

| Provider | CIDEr | SPICE | BLEU-4 | METEOR |
|----------|-------|-------|--------|--------|
| Florence-2 | 0.95 | 0.21 | 0.28 | 0.26 |
| Moondream 0.5B | 0.78 | 0.18 | 0.22 | 0.23 |
| LightCap | 0.71 | 0.16 | 0.19 | 0.21 |
| Azure Vision | 1.12 | 0.24 | 0.32 | 0.29 |
| Vertex Gemini | 1.08 | 0.23 | 0.31 | 0.28 |
| Bedrock Claude | 1.15 | 0.25 | 0.33 | 0.30 |

### 12.4. Koszty (1000 obrazów)

| Provider | Cost (USD) | Notes |
|----------|------------|-------|
| Florence-2 | $0.00 | On-device, one-time download |
| Moondream | $0.00 | On-device, one-time download |
| LightCap | $0.00 | On-device, one-time download |
| Azure Vision | $2.00 | $0.002/image (standard tier) |
| Vertex Gemini | $3.75 | ~$0.00375/image (vision model) |
| Bedrock Claude | $5.00 | ~$0.005/image (Claude 3.5 Sonnet) |

---

## 13. Bibliografia i źródła

### 13.1. Prace naukowe

1. **Florence-2**: Xiao et al., "Florence-2: Advancing a Unified Representation for a Variety of Vision Tasks", CVPR 2024. [arXiv:2311.06242](https://arxiv.org/abs/2311.06242)

2. **LightCap**: Hosseinzadeh & Wang, "Efficient Image Captioning for Edge Devices", arXiv 2022. [arXiv:2212.08985](https://arxiv.org/abs/2212.08985)

3. **BLIP/BLIP-2**: Li et al., "BLIP: Bootstrapping Language-Image Pre-training", ICML 2022. [arXiv:2201.12086](https://arxiv.org/abs/2201.12086)

4. **MS COCO**: Lin et al., "Microsoft COCO: Common Objects in Context", ECCV 2014. [arXiv:1405.0312](https://arxiv.org/abs/1405.0312)

5. **CIDEr**: Vedantam et al., "CIDEr: Consensus-based Image Description Evaluation", CVPR 2015.

6. **SPICE**: Anderson et al., "SPICE: Semantic Propositional Image Caption Evaluation", ECCV 2016. [arXiv:1607.08822](https://arxiv.org/abs/1607.08822)

### 13.2. Dokumentacja techniczna

1. **Azure Computer Vision**: [Microsoft Learn - Image Analysis 4.0](https://learn.microsoft.com/en-us/azure/ai-services/computer-vision/concept-describe-images-40)

2. **Google Vertex AI Gemini**: [Vertex AI Multimodal](https://cloud.google.com/vertex-ai/generative-ai/docs/multimodal/image-understanding)

3. **AWS Bedrock Claude**: [Bedrock User Guide - Claude](https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-claude.html)

4. **OpenAI Vision API**: [OpenAI Vision Guide](https://platform.openai.com/docs/guides/vision)

5. **ONNX Runtime Mobile**: [ONNX Runtime Docs](https://onnxruntime.ai/docs/tutorials/mobile/)

6. **TensorFlow Lite**: [TFLite Android Guide](https://www.tensorflow.org/lite/android)

7. **Android Gemini Nano**: [Android AI - Gemini Nano](https://developer.android.com/ai/gemini-nano)

8. **Android Power Profiler**: [Profile Energy Use](https://developer.android.com/studio/profile/power-profiler)

9. **Android Macrobenchmark**: [Macrobenchmark Overview](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)

### 13.3. Repozytoria i modele

1. **Florence-2 HuggingFace**: [microsoft/Florence-2-base](https://huggingface.co/microsoft/Florence-2-base)

2. **Moondream HuggingFace**: [vikhyatk/moondream2](https://huggingface.co/vikhyatk/moondream2)

3. **COCO Dataset**: [cocodataset.org](https://cocodataset.org/)

4. **pycocoevalcap**: [GitHub - tylin/coco-caption](https://github.com/tylin/coco-caption)

---

## 14. Harmonogram pracy (przykładowy)

| Tydzień | Zadania | Deliverables |
|---------|---------|--------------|
| **1-2** | Setup projektu, debugging Florence-2, pierwszy provider chmurowy | Working Florence-2, Azure Vision provider |
| **3-4** | Implementacja Moondream + LightCap, stabilizacja | 3 local providers działające |
| **5-6** | Implementacja pozostałych cloud providers (Gemini, Claude) | 3-4 cloud providers |
| **7-8** | System pomiarowy (BenchmarkRunner, MetricsCollector, DataExporter) | Automated benchmarking |
| **9-10** | Testy na emulatorze, zbieranie danych, debugging | Dataset 1: emulator results |
| **11-12** | Testy na Samsung S24, pomiar energii | Dataset 2: S24 results, energy data |
| **13** | Ewaluacja jakości (Python scripts, metryki) | Quality metrics (CIDEr, SPICE, etc.) |
| **14** | Analiza kosztów, TCO, wizualizacje | Cost analysis, charts, tables |
| **15-17** | Pisanie pracy dyplomowej, finalizacja wyników | Thesis draft, presentation |

---

## 15. Następne kroki (Action Items)

### Natychmiast (priorytet 1)
1. ✅ Poprawka `CloudProviderOpenAI` - struktura `image_url` (DONE)
2. 🔄 Debugowanie `OnnxFlorenceProvider` - weryfikacja output
3. 🆕 Utworzenie `ProviderManager` - registry providerów
4. 🆕 Utworzenie `MetricsCollector` - framework zbierania metryk

### Krótkoterminowe (1-2 tygodnie)
1. Implementacja `AzureVisionProvider`
2. Implementacja `VertexGeminiProvider`
3. Uzyskanie kluczy API (Azure, GCP, AWS)
4. Konwersja Moondream do ONNX
5. Podstawowy `BenchmarkRunner`

### Średnioterminowe (3-4 tygodnie)
1. Batch testing UI
2. Integracja z Power Profiler workflow
3. Dataset preparation (COCO subset)
4. CSV/JSON export
5. Testy na emulatorze (pierwsze wyniki)

### Długoterminowe (5+ tygodni)
1. Testy na Samsung S24
2. Ewaluacja jakości (Python)
3. Analiza energii i kosztów
4. Pisanie pracy dyplomowej
5. Przygotowanie prezentacji obrony

---

## 16. Kontakt i wsparcie

**Autor projektu:** Dominik Baczyński  
**Email:** [dominik.baczynski.stud@pw.edu.pl](mailto:dominik.baczynski.stud@pw.edu.pl) (przypuszczalny)  
**Promotor:** dr inż. Piotr Bobiński  
**Uczelnia:** Politechnika Warszawska, Wydział EiTI  

**Repozytorium:** ArturB/WUT-Thesis (GitHub)

---

## Podsumowanie

Aplikacja **CaptionLab** ma być kompleksowym narzędziem badawczym do rzetelnej oceny efektywności rozwiązań AI w generowaniu opisów obrazów na platformie Android. Poprzez systematyczne porównanie 3-4 rozwiązań lokalnych (Florence-2, Moondream, LightCap, opcjonalnie Gemini Nano) z 3-4 rozwiązaniami chmurowymi (Azure Vision, Vertex Gemini, Bedrock Claude, OpenAI GPT-4o) w kontekście:

- **Jakości** (metryki automatyczne: CIDEr, SPICE, BLEU, METEOR)
- **Wydajności** (latencja, throughput)
- **Zasobów** (RAM, energia, rozmiar modelu)
- **Kosztów** (operacyjne koszty cloud)

praca dostarczy praktycznych wskazówek dla inżynierów i architektów aplikacji mobilnych co do optymalnego wyboru rozwiązania AI w zależności od ograniczeń projektowych (budget, prywatność, latencja, offline operation).

Obecny PoC stanowi solidny fundament - wymaga rozbudowy o dodatkowych providerów, system pomiarowy oraz narzędzia analityczne. Niniejszy dokument stanowi **roadmap** dla dalszego rozwoju i podstawę do strukturyzacji rozdziałów pracy dyplomowej.

---

**Wersja dokumentu:** 1.0  
**Data utworzenia:** 11 listopada 2025  
**Status:** Draft - do aktualizacji w trakcie realizacji projektu
