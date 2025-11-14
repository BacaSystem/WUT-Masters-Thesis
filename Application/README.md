# CaptionLab - Dokumentacja 

**Wersja:** 1.1  
**Data:** 13 listopada 2025  
**Autor:** Dominik Baczyński  
**Temat pracy:** Analiza efektywności lokalnych i chmurowych modeli AI do generowania opisów obrazów w aplikacjach mobilnych

---

## 1. Wprowadzenie

### 1.1. Cel aplikacji

CaptionLab to aplikacja badawcza na platformę Android służąca do kompleksowej analizy i porównania wydajności różnych rozwiązań AI w zadaniu generowania opisów obrazów (image captioning). Aplikacja umożliwia:

- Porównanie modeli lokalnych (działających na urządzeniu) z rozwiązaniami chmurowymi
- Zbieranie szczegółowych metryk wydajnościowych (latencja, zużycie pamięci, energia)
- Automatyzację testów batchowych na dużych zbiorach danych
- Eksport wyników do formatów CSV i JSON dla dalszej analizy statystycznej

### 1.2. Zakres funkcjonalny

1. **Generowanie opisów pojedynczych obrazów** - interaktywny interfejs do testowania modeli
2. **Testy batchowe** - automatyczne przetwarzanie zestawów obrazów z wieloma powtórzeniami
3. **Zbieranie metryk** - szczegółowe pomiary czasowe, pamięciowe i energetyczne
4. **Eksport danych** - możliwość zapisania wyników w formatach CSV/JSON
5. **Zarządzanie providerami** - elastyczny system rejestracji i konfiguracji modeli AI

### 1.3. Technologie

- **Język:** Kotlin 1.9+
- **Platforma:** Android SDK 26-36 (Android 8.0 - Android 15)
- **Architektura:** Coroutines, ViewBinding, Strategy Pattern
- **Inference lokalny:** ONNX Runtime 1.23.2
- **API chmurowe:** OkHttp 4.12, Moshi (JSON)
- **UI:** Material Design 3, AndroidX

---

## 2. Architektura systemu

### 2.1. Diagram komponentów wysokiego poziomu

```
                     ╔════════════════════════════════════════╗
                     ║     CaptionLab Android Application     ║
                     ╚════════════════════════════════════════╝
                                         │
                 ┌───────────────────────┼───────────────────────┐
                 │                       │                       │
                 ▼                       ▼                       ▼
        ┌────────────────┐     ┌─────────────────┐    ┌─────────────────┐
        │  Presentation  │     │    Benchmark    │    │   Data Export   │
        │     Layer      │     │     System      │    │     System      │
        ├────────────────┤     ├─────────────────┤    ├─────────────────┤
        │  MainActivity  │     │ BenchmarkRunner │    │  DataExporter   │
        │BatchTestActivity     │ MetricsCollector│    │  (CSV/JSON)     │
        │                │     │ ProgressListener│    │                 │
        └────────┬───────┘     └────────┬────────┘    └────────┬────────┘
                 │                      │                      │
                 └──────────────────────┼──────────────────────┘
                                        │
                                        ▼
                        ┌───────────────────────────────┐
                        │     ProviderManager           │
                        │  (Strategy Pattern Registry)  │
                        │                               │
                        │  • Provider Registration      │
                        │  • API Key Management         │
                        │  • Local/Cloud Categorization │
                        └───────────────┬───────────────┘
                                        │
                        ┌───────────────┴───────────────┐
                        │ CaptioningProvider (Interface)│
                        │                               │
                        │  + id: String                 │
                        │  + caption(Bitmap): Result    │
                        └───────────────┬───────────────┘
                                        │
                ┌───────────────────────┴───────────────────────┐
                │                                               │
                ▼                                               ▼
    ┌───────────────────────┐                   ┌──────────────────────────┐
    │   Local Providers     │                   │    Cloud Providers       │
    │    (ONNX Runtime)     │                   │     (REST APIs)          │
    ├───────────────────────┤                   ├──────────────────────────┤
    │ • Florence2Provider   │                   │ • OpenAIProvider         │
    │   (~215 MB)           │                   │   (GPT-4o-mini)          │
    │                       │                   │                          │
    │ • ViTGPT2Provider     │                   │ • AzureVisionProvider    │
    │   (~387 MB)           │                   │   (Florence-based API)   │
    │                       │                   │                          │
    │ • BlipProvider        │                   │ • GeminiProvider         │
    │   (~241 MB)           │                   │   (Gemini Pro Vision)    │
    └───────────┬───────────┘                   └───────────┬──────────────┘
                │                                           │
                ▼                                           ▼
    ┌───────────────────────┐                   ┌──────────────────────────┐
    │  ONNX Runtime Engine  │                   │   OkHttp HTTP Client     │
    ├───────────────────────┤                   ├──────────────────────────┤
    │ • Session Management  │                   │ • REST API Calls         │
    │ • Tensor Operations   │                   │ • JSON Serialization     │
    │ • Model Inference     │                   │ • Response Parsing       │
    │ • Memory Management   │                   │ • Error Handling         │
    └───────────────────────┘                   └──────────────────────────┘
                │                                           │
                └───────────────────┬───────────────────────┘
                                    │
                                    ▼
                        ┌───────────────────────┐
                        │   Storage & Assets    │
                        ├───────────────────────┤
                        │ • ONNX Models (843MB) │
                        │ • Tokenizer Configs   │
                        │ • Test Datasets       │
                        │ • Exported Results    │
                        └───────────────────────┘
```

### 2.2. Wzorce architektoniczne

#### Strategy Pattern
Każdy provider AI implementuje interfejs `CaptioningProvider`, umożliwiając wymienne stosowanie różnych modeli bez zmian w kodzie klienta.

```kotlin
interface CaptioningProvider {
    val id: String
    suspend fun caption(bitmap: Bitmap): CaptionResult
}
```

#### Repository Pattern (częściowy)
`ProviderManager` działa jako repozytorium providerów, centralizując logikę rejestracji i dostępu.

#### Observer Pattern
`BenchmarkProgressListener` umożliwia obserwację postępu długotrwałych testów batchowych.

#### Dependency Injection (manual)
Komponenty otrzymują zależności przez konstruktor (np. `Context`, `ProviderManager`, lambda dla API keys).

---

## 3. Warstwa prezentacji

### 3.1. MainActivity

**Odpowiedzialność:** Interaktywne testowanie pojedynczych obrazów

**Funkcjonalność:**
- Wybór obrazu z galerii
- Wybór providera AI (spinner z lokalnymi i chmurowymi)
- Generowanie opisu przez wybrany model
- Wyświetlanie wyniku z metrykami (czas preprocessing, inference, postprocessing)
- Możliwość przejścia do testów batchowych

**Kluczowe komponenty:**
```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var providerManager: ProviderManager
    private lateinit var metricsCollector: MetricsCollector
    
    private var selectedImageUri: Uri?
    private var lastMetrics: BenchmarkMetrics?
}
```

**Flow użycia:**
1. Użytkownik wybiera obraz → `pickImage()`
2. Użytkownik wybiera provider ze spinnera
3. Użytkownik klika "Generate Caption" → `runCaption()`
4. `MetricsCollector.collectSingle()` zbiera metryki
5. Wynik wyświetlany w TextView 

### 3.2. BatchTestActivity

**Odpowiedzialność:** Automatyczne testy wydajnościowe

**Funkcjonalność:**
- Wczytywanie zestawów obrazów (dataset loader lub custom folder)
- Wybór wielu providerów jednocześnie (checkboxy)
- Konfiguracja parametrów testu:
  - Liczba powtórzeń (warmup + actual runs)
  - Timeout na obraz
  - Format eksportu (CSV/JSON/oba)
- Wizualizacja postępu (progress bar, log tekstowy)
- Eksport wyników po zakończeniu

**Kluczowe komponenty:**
```kotlin
class BatchTestActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBatchTestBinding
    private lateinit var benchmarkRunner: BenchmarkRunner
    private lateinit var dataExporter: DataExporter
    private lateinit var datasetLoader: DatasetLoader
    
    private var loadedImages: List<Pair<Bitmap, String>>
    private var currentResult: BenchmarkResult?
}
```

**Flow użycia:**
1. Użytkownik wczytuje dataset → `loadDataset()`
2. Konfiguruje parametry (checkboxy, edity)
3. Klika "Start Benchmark" → `startBenchmark()`
4. `BenchmarkRunner.runBenchmark()` wykonuje testy
5. Callback `BenchmarkProgressListener` aktualizuje UI
6. Po zakończeniu: `exportResults()` → CSV/JSON w /data/thesis.wut.application.captionlab/files/exports

### 3.3. ViewBinding

Obie aktywności wykorzystują ViewBinding dla type-safe dostępu do widoków:

```kotlin
// MainActivity
binding = ActivityMainBinding.inflate(layoutInflater)
setContentView(binding.root)
binding.btnPick.setOnClickListener { ... }

// BatchTestActivity
binding = ActivityBatchTestBinding.inflate(layoutInflater)
binding.checkboxFlorence2.isChecked
```

**Korzyści:**
- Compile-time safety (brak błędów cast)
- Null safety (View binding generuje nullable pola dla widoków w <include>)
- Lepsze autouzupełnianie w IDE

---

## 4. Warstwa logiki biznesowej

### 4.1. ProviderManager

**Odpowiedzialność:** Zarządzanie dostępnymi providerami AI

**Kluczowe funkcje:**
```kotlin
class ProviderManager(
    private val context: Context,
    private val apiKeyProvider: (String) -> String?
) {
    fun getProvider(id: String): CaptioningProvider?
    fun getAllProviders(): List<CaptioningProvider>
    fun getLocalProviders(): List<CaptioningProvider>
    fun getCloudProviders(): List<CaptioningProvider>
    fun getProviderDisplayName(id: String): String
    fun isLocalProvider(id: String): Boolean
}
```

**Inicjalizacja:**
```kotlin
init {
    registerLocalProvider(Florence2Provider(context))
    registerLocalProvider(ViTGPT2Provider(context))
    registerLocalProvider(BlipProvider(context))
    
    registerCloudProvider(OpenAIProvider { apiKeyProvider("openai_key") })
    registerCloudProvider(AzureVisionProvider { apiKeyProvider("azure_key") })
    registerCloudProvider(GeminiProvider { apiKeyProvider("gemini_key") })
}
```

**Identyfikatory providerów:**
- `florence2_onnx_local` - Florence-2 (ONNX Runtime)
- `vitgpt2_onnx_local` - ViT-GPT2 (ONNX Runtime)
- `blip_onnx_local` - BLIP (ONNX Runtime)
- `openai_cloud` - OpenAI GPT-4o
- `azure_vision_cloud` - Azure Computer Vision
- `gemini_cloud` - Google Vertex AI Gemini

### 4.2. BenchmarkRunner

**Odpowiedzialność:** Orkiestracja testów wydajnościowych

**Architektura:**
```kotlin
class BenchmarkRunner(
    private val context: Context,
    private val providerManager: ProviderManager,
    private val metricsCollector: MetricsCollector
) {
    suspend fun runBenchmark(
        config: BenchmarkConfig,
        images: List<Pair<Bitmap, String?>>,
        progressListener: BenchmarkProgressListener?
    ): BenchmarkResult
    
    fun pause()
    fun resume()
    fun cancel()
}
```

**Algorytm wykonania:**
```
FOR EACH provider IN config.providerIds:
    onProviderStart(provider)
    
    // Warm-up phase
    FOR i = 1 TO config.warmupRuns:
        run_caption(first_image)  // discard results
    
    FOR EACH image IN images:
        onImageStart(image)
        
        // Collect multiple runs for statistical significance
        metrics_list = []
        FOR run = 1 TO config.runsPerImage:
            metrics = metricsCollector.collectSingle(provider, image)
            metrics_list.append(metrics)
        
        // Aggregate metrics (median, p90, p95, etc.)
        aggregated = aggregate(metrics_list)
        onImageComplete(image, aggregated)
        
        cooldown_delay(config.cooldownMs)
    
    onProviderComplete(provider)

RETURN BenchmarkResult with all aggregated data
```

**Obsługa błędów:**
- Timeout dla zbyt długich inference (config.timeoutMs)
- Max kolejnych błędów (config.maxConsecutiveErrors)
- Tryb `continueOnError` - czy kontynuować po błędzie
- Logowanie szczegółów błędów do `ProviderBenchmarkResult.errors`

### 4.3. MetricsCollector

**Odpowiedzialność:** Zbieranie metryk wydajnościowych podczas inferencji

**Architektura pomiarowa:**
```
MetricsCollector
├── PowerMonitor (pomiar energii)
├── MemoryMonitor (pomiar RAM)
└── TimingMeasurements (czas inferencji)
```

**Zbierane metryki:**

| Kategoria | Metryka | Opis |
|-----------|---------|------|
| **Czas** | `pre_ms` | Preprocessing (resize, normalizacja) |
| | `infer_ms` | Czysta inferencja (model forward pass) |
| | `post_ms` | Postprocessing (detokenizacja) |
| | `e2e_ms` | End-to-end całkowity czas |
| **Pamięć** | `ram_peak_mb` | Szczytowe zużycie RAM podczas inference |
| | `model_size_mb` | Rozmiar pliku modelu (tylko local) |
| **Energia** | `energy_mwh` | Rzeczywiste zużycie energii (mWh) |
| **Koszt** | `cost_usd` | Koszt wywołania API (tylko cloud) |
| | `tokens_input` | Liczba tokenów wejściowych (LLM) |
| | `tokens_output` | Liczba tokenów wyjściowych (LLM) |
| **Jakość** | `caption_length` | Długość wygenerowanego opisu |

#### 4.3.1. PowerMonitor - pomiar energii

Używa Android BatteryManager API do pomiaru rzeczywistego zużycia baterii:

```kotlin
class PowerMonitor(private val context: Context) {
    fun start()           // rozpoczyna próbkowanie co 50ms
    fun stop(): Double?   // zwraca energię w mWh
}
```

**Algorytm pomiaru:**
1. Próbkowanie parametrów baterii (prąd, napięcie, pojemność) co 50ms
2. Obliczenie energii metodą numerycznej integracji prądu chwilowego:
   ```
   E = Σ[(V₁ * I₁ + V₂ * I₂) / 2] * Δt / 3600
   ```
   gdzie:
   - V = napięcie w woltach (EXTRA_VOLTAGE)
   - I = prąd w amperach (BATTERY_PROPERTY_CURRENT_NOW)
   - Δt = czas między próbkami w sekundach
   - Wynik w mWh (milliwatt-hours)

3. Metody fallback:
   - Metoda prądu średniego (BATTERY_PROPERTY_CURRENT_AVERAGE)
   - Zmiana pojemności baterii (BATTERY_PROPERTY_CHARGE_COUNTER)

**Typowe wartości (Android Emulator API 36):**
- Florence-2 local: ~6.5 mWh/obraz
- Gemini cloud: ~1.4 mWh/obraz

**Uwaga:** Wartości na emulatorze służą porównaniom relatywnym. Rzeczywiste urządzenia pokażą inne wartości bezwzględne.

#### 4.3.2. MemoryMonitor - pomiar RAM

Monitoruje zużycie pamięci JVM podczas inferencji:

```kotlin
class MemoryMonitor {
    fun start()                      // rozpoczyna monitoring (10ms)
    fun getMemoryIncreaseMb(): Int   // zwraca delta RAM
}
```

**Mechanizm:**
- Próbkowanie co 10ms przez dedykowany wątek
- Tracking `Runtime.getRuntime().totalMemory() - freeMemory()`
- Zwraca różnicę między szczytową a początkową pamięcią

### 4.4. DataExporter

**Odpowiedzialność:** Eksport wyników do plików

**Obsługiwane formaty:**
- **CSV** - dla analizy w Excel/Python pandas
- **JSON** - dla dalszego przetwarzania programistycznego
- **BOTH** - oba formaty jednocześnie

**Struktura CSV:**
```csv
timestamp,provider_id,image_id,caption_text,pre_ms,infer_ms,post_ms,e2e_ms,ram_peak_mb,energy_mwh,cost_usd
2025-11-13T10:15:23,florence2_onnx_local,img_001,A dog sitting on grass,45.2,523.7,12.1,581.0,234,12.5,0.0
2025-11-13T10:15:45,openai_cloud,img_001,A brown dog sitting in the grass,8.3,342.1,2.1,352.5,85,3.2,0.002
```

**Struktura JSON:**
```json
{
  "metadata": {
    "benchmark_name": "COCO_val_test",
    "start_time": "2025-11-13T10:15:00Z",
    "device": {
      "manufacturer": "Samsung",
      "model": "Galaxy S24",
      "android_version": "14",
      "total_memory_mb": 8192
    }
  },
  "config": {
    "num_runs": 10,
    "warmup_runs": 3,
    "timeout_ms": 30000
  },
  "provider_results": {
    "florence2_onnx_local": {
      "aggregated": {
        "e2e_median": 581.0,
        "e2e_p90": 623.5,
        "throughput_median": 1.72
      },
      "per_image_results": [...]
    }
  }
}
```

**Lokalizacja plików:**
```
/Android/data/thesis.wut.application.captionlab/files/exports/
├── benchmark_20251113_101500.csv
├── benchmark_20251113_101500.json
└── summary_20251113.txt
```

---

## 5. Warstwa providerów

### 5.1. Interfejs CaptioningProvider

Każdy provider AI implementuje wspólny interfejs `CaptioningProvider`, który definiuje kontrakt do generowania opisów:

- **Identyfikator** (`id: String`) - unikalny identyfikator providera
- **Metoda captioning** (`suspend fun caption(Bitmap): CaptionResult`) - główna metoda generująca opis
- **Wynik** (`CaptionResult`) - zawiera wygenerowany tekst oraz dodatkowe metryki (czasy wykonania, koszty API, liczba tokenów)

Taka abstrakcja umożliwia wymienne stosowanie różnych modeli bez zmian w kodzie klienta (Strategy Pattern).

### 5.2. Providery lokalne (ONNX Runtime)

Providery lokalne wykonują inferencję bezpośrednio na urządzeniu za pomocą ONNX Runtime. Każdy model składa się z oddzielnych plików ONNX dla różnych etapów pipeline'u.

#### **Florence2Provider**

**Model:** microsoft/Florence-2-base  
**Architektura:** Vision Encoder-Decoder (DaViT + BART)  
**Rozmiar:** 215 MB (4 pliki ONNX)  
**Kwantyzacja:** INT8

**Pipeline inferencji:**
1. **Vision Encoding** - obraz 384×384 przekształcany przez DaViT encoder na sekwencję feature vectors (577×768)
2. **Prompt Encoding** - tekst promptu tokenizowany i osadzany w przestrzeni embeddings
3. **Text Encoding** - konkatenacja image features i prompt embeddings przetwarzana przez BART encoder
4. **Autoregressive Decoding** - BART decoder generuje tokeny tekstu w pętli, aż do napotkania tokenu EOS (End-of-Sequence)
5. **Detokenization** - sekwencja tokenów konwertowana na tekst końcowy

**Preprocessing:** Obrazy skalowane do 384×384, normalizowane według statystyk ImageNet (mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]), konwertowane do formatu NCHW.

#### **ViTGPT2Provider**

**Model:** nlpconnect/vit-gpt2-image-captioning  
**Architektura:** Vision Transformer + GPT-2  
**Rozmiar:** 387 MB (2 pliki ONNX)  
**Kwantyzacja:** FP32 (brak kwantyzacji)

**Pipeline inferencji:**
1. **Vision Encoding** - ViT-base/16 przetwarza obraz 224×224 na sekwencję 197 feature vectors (768-dim)
2. **Autoregressive Decoding** - GPT-2 decoder generuje caption token po tokenie, używając image features jako kontekstu

**Preprocessing:** Obrazy skalowane do 224×224, normalizacja ImageNet.

#### **BlipProvider**

**Model:** Salesforce/blip-image-captioning-base  
**Architektura:** ViT + BERT decoder  
**Rozmiar:** 241 MB (2 pliki ONNX)  
**Kwantyzacja:** INT8

**Pipeline inferencji:**
1. **Visual Encoding** - ViT-base przetwarza obraz na visual features
2. **Text Decoding** - BERT-style decoder generuje caption w sposób autoregresywny

**Preprocessing:** Obrazy skalowane do 384×384, normalizacja standardowa.

**Uwaga:** Wszystkie lokalne providery wykorzystują tokenizery specyficzne dla danego modelu (SentencePiece dla Florence-2, BPE dla GPT-2, WordPiece dla BLIP) do konwersji między tekstem a tokenami.

### 5.3. Providery chmurowe (REST API)

Providery chmurowe wysyłają obrazy do zewnętrznych API i otrzymują wygenerowane opisy przez sieć. Proces obejmuje: kompresję obrazu (JPEG), kodowanie base64, wysłanie requestu HTTP POST, parsing odpowiedzi JSON.

#### **OpenAIProvider**

**Model:** GPT-4o-mini (multimodal)  
**Endpoint:** OpenAI Chat Completions API  
**Cechy charakterystyczne:**
- Obsługa obrazów jako part of chat completions
- Precyzyjna  parametrów generacji
- Detaliowe statystyki usage (input/output tokens)

**Pricing:** ~$0.002 per image (depending on resolution and token count)
- Input: $0.15 / 1M tokens
- Output: $0.60 / 1M tokens
- Vision images: ~85-170 tokens depending on resolution

#### **AzureVisionProvider**

**Model:** Azure Computer Vision 4.0 (Florence-based)  
**Endpoint:** Azure Cognitive Services Image Analysis API  
**Cechy charakterystyczne:**
- Specjalizowane API do analizy obrazów
- Wsparcie dla dense captions (wiele opisów z bounding boxes)
- Confidence scores dla generated captions

**Pricing:** $1.50 / 1000 images

#### **GeminiProvider**

**Model:** gemini-2.5-flash-lite  
**Endpoint:** Google Vertex AI Prediction API  
**Cechy charakterystyczne:**
- Integracja z Google Cloud Platform
- Service Account authentication
- Multimodal understanding (text + image)

**Pricing:** ~$0.001 per image (depending on token count)
- Input: $0.10 / 1M tokens
- Output: $0.40 / 1M tokens

**Wspólne cechy cloud providers:**
- Szybkie czasy inferencji (200-500ms) dzięki mocnym serwerom
- Niskie zużycie zasobów lokalnych (tylko network I/O)
- Koszty per-request
- Zależność od połączenia internetowego
- Privacy concerns (dane wysyłane na zewnątrz)

---

## 6. Kluczowe struktury danych

### 6.1. BenchmarkMetrics

Reprezentuje metryki pojedynczego uruchomienia inference - podstawowa jednostka pomiaru w systemie.

**Kategorie metryk:**

| Kategoria | Metryka | Opis |
|-----------|---------|------|
| **Identyfikacja** | `providerId` | ID providera (np. "florence2_onnx_local") |
| | `imageId` | ID obrazu testowego |
| | `runNumber` | Numer uruchomienia (dla powtórzeń) |
| **Czas** | `preMs` | Preprocessing (resize, normalizacja) |
| | `inferMs` | Czysta inferencja (model forward pass) |
| | `postMs` | Postprocessing (detokenizacja) |
| | `e2eMs` | End-to-end całkowity czas |
| **Pamięć** | `ramPeakMb` | Szczytowe zużycie RAM |
| | `modelSizeMb` | Rozmiar modelu (tylko local) |
| **Energia** | `energyMwh` | Szacowane zużycie energii |
| **Koszt** | `costUsd` | Koszt API call (tylko cloud) |
| | `tokensInput` | Tokeny wejściowe |
| | `tokensOutput` | Tokeny wyjściowe |
| **Wynik** | `captionText` | Wygenerowany opis |
| | `captionLength` | Długość tekstu |

**Szacowanie energii:** Dla lokalnych providerów zakładane jest zużycie ~2500mW (CPU+GPU), dla cloud ~200mW (network I/O). Energia = Moc × (czas_ms / 1000) / 3600 [mWh].

### 6.2. AggregatedMetrics

Statystyki agregowane z wielu uruchomień - umożliwiają ocenę wydajności z uwzględnieniem zmienności i outlierów.

**Główne pola:**

| Kategoria | Pole | Opis |
|-----------|------|------|
| **Statystyki E2E** | `e2eMean`, `e2eMedian` | Średnia i mediana czasu end-to-end |
| | `e2eStd` | Odchylenie standardowe |
| | `e2eMin`, `e2eMax` | Wartości ekstremalne |
| | `e2eP90`, `e2eP95`, `e2eP99` | Percentyle (90., 95., 99.) |
| **Throughput** | `throughputMean`, `throughputMedian` | Przepustowość (images/sec) |
| **Zasoby** | `ramPeakMeanMb`, `ramPeakMaxMb` | Średnie i max zużycie RAM |
| | `energyMeanMwh`, `energyTotalMwh` | Energia per-image i całkowita |
| **Koszt** | `costMeanUsd`, `costTotalUsd` | Koszt per-image i całkowity (cloud) |
| **Niezawodność** | `successCount`, `failureCount` | Liczba sukcesów i błędów |

### 6.3. Algorytm agregacji

Proces przekształcania listy `BenchmarkMetrics` w `AggregatedMetrics`:

**Krok 1: Sortowanie czasów**
```
e2eTimes = metrics.map(m -> m.e2eMs).sorted()
```

**Krok 2: Obliczenia statystyczne**
```
e2eMean = average(e2eTimes)
e2eMedian = percentile(e2eTimes, 50)
e2eStd = sqrt(variance(e2eTimes))
e2eP90 = percentile(e2eTimes, 90)
e2eP95 = percentile(e2eTimes, 95)
e2eP99 = percentile(e2eTimes, 99)
```

**Krok 3: Throughput**
```
throughputMedian = 1000.0 / e2eMedian  // images per second
throughputMean = 1000.0 / e2eMean
```

**Krok 4: Agregacja zasobów**
```
ramPeakMeanMb = average(metrics.map(m -> m.ramPeakMb))
ramPeakMaxMb = max(metrics.map(m -> m.ramPeakMb))
energyMeanMwh = average(metrics.map(m -> m.energyMwh))
energyTotalMwh = sum(metrics.map(m -> m.energyMwh))
```

**Krok 5: Koszty (cloud only)**
```
IF isCloudProvider:
    costMeanUsd = average(metrics.map(m -> m.costUsd))
    costTotalUsd = sum(metrics.map(m -> m.costUsd))
```

**Funkcja percentile:** Dla percentyla P% sortujemy wartości i wybieramy element na pozycji `(P/100) × (n-1)`, z interpolacją liniową dla pozycji niecałkowitych.

**Odchylenie standardowe:** 
```
variance = sum((x - mean)²) / (n - 1)  // Bessel's correction
std = sqrt(variance)
```

Ta agregacja pozwala na:
- **Median vs Mean** - wykrywanie outlierów (jeśli median << mean, są wolne outliers)
- **Percentile 90/95/99** - analiza worst-case scenarios
- **Std** - ocena stabilności wydajności
- **Success rate** - niezawodność providera

### 6.4. Struktura pakietu metrics

Komponenty pomiarowe są zorganizowane w dedykowanym pakiecie:

```
thesis.wut.application.captionlab.metrics/
├── MetricsCollector.kt        // główny koordynator pomiarów
├── PowerMonitor.kt            // pomiar energii (BatteryManager API)
├── MemoryMonitor.kt           // pomiar RAM (Runtime monitoring)
├── BenchmarkMetrics.kt        // klasa danych pojedynczego pomiaru
├── AggregatedMetrics.kt       // agregacja statystyczna
```

**Separacja odpowiedzialności:**
- `MetricsCollector` - orkiestruje wszystkie pomiary, wywołuje providers
- `PowerMonitor` - autonomiczny, zarządza własnym lifecycle (start/stop)
- `MemoryMonitor` - niezależny wątek monitorujący, nie blokuje inferencji
- Data classes - czysto dane, bez logiki biznesowej

---

## 7. Konfiguracja i deployment

### 7.1. Zależności projektu

**Android Core:**
- AndroidX Core KTX 1.12.0 - Kotlin extensions dla Android SDK
- AppCompat 1.6.1 - Backward compatibility dla nowszych API
- Material Design 3 (1.11.0) - Komponenty UI
- ConstraintLayout 2.1.4 - Flexible layouts

**Coroutines:**
- Kotlinx Coroutines Android 1.7.3 - Asynchroniczne operacje, suspend functions

**ONNX Runtime:**
- ONNX Runtime Android 1.23.2 - Silnik inferencji lokalnych modeli

**Image Loading:**
- Coil 2.5.0 - Efektywne wczytywanie i cache'owanie obrazów

**HTTP & JSON:**
- OkHttp 4.12.0 - HTTP client dla cloud API
- Moshi 1.15.0 - JSON serialization/deserialization
- Moshi Kotlin Codegen (KSP) - Code generation dla data classes

**Testing:**
- JUnit 4.13.2 - Unit testing framework
- AndroidX Test Extensions 1.1.5 - Instrumentation tests
- Espresso 3.5.1 - UI testing

**Build Configuration:**
- Gradle 8.2
- Kotlin 1.9.20
- Android Gradle Plugin 8.1.4
- KSP (Kotlin Symbol Processing) 1.9.20-1.0.14
- ViewBinding enabled

### 7.2. Wymagania systemowe

**Minimalne wymagania:**
- Android 8.0 Oreo (API 26)
- RAM: 3 GB
- Pamięć wewnętrzna: 2 GB wolnej przestrzeni
- Procesor: ARM64-v8a lub x86_64
- Połączenie internetowe (dla cloud providers)

**Zalecane wymagania:**
- Android 14+ (API 34+)
- RAM: 8 GB lub więcej
- Pamięć: 5 GB wolnej przestrzeni
- Procesor: Snapdragon 8 Gen 2 lub nowszy (dla akceleracji NNAPI/QNN)
- Szybkie połączenie Wi-Fi/5G

### 7.3. Instalacja modeli

Aplikacja wykorzystuje modele ONNX, które muszą być umieszczone w katalogu `app/src/main/assets/models/` przed kompilacją.

**Florence-2 (microsoft/Florence-2-base):**
- Model dostarczany bezpośrednio jako ONNX (215 MB, INT8 quantized)
- Pliki: `vision_encoder.onnx`, `embed_tokens.onnx`, `encoder_model.onnx`, `decoder_model.onnx`
- Dodatkowe: `config.json`, `tokenizer.json`
- Źródło: HuggingFace, bezpośrednie ONNX export

**ViT-GPT2 (nlpconnect/vit-gpt2-image-captioning):**
- Model wymaga konwersji z PyTorch do ONNX (387 MB, FP32)
- Pliki: `vision_encoder.onnx`, `text_decoder.onnx`
- Konwersja: Użyj skryptów `export_to_onnx.py` z repozytorium
- Źródło: HuggingFace, manual export

**BLIP (Salesforce/blip-image-captioning-base):**
- Model wymaga konwersji z PyTorch do ONNX (241 MB, INT8 quantized)
- Pliki: `visual_encoder.onnx`, `text_decoder.onnx`
- Konwersja: Użyj Optimum library lub custom export script
- Źródło: HuggingFace, manual export + quantization

**Struktura katalogów:**
```
app/src/main/assets/models/
├── florence2-base/
│   ├── vision_encoder.onnx       (58 MB)
│   ├── embed_tokens.onnx          (2 MB)
│   ├── encoder_model.onnx        (85 MB)
│   ├── decoder_model.onnx        (70 MB)
│   ├── config.json
│   └── tokenizer.json
├── vit-gpt2/
│   ├── vision_encoder.onnx      (330 MB)
│   ├── text_decoder.onnx         (57 MB)
│   └── config.json
└── blip/
    ├── visual_encoder.onnx      (180 MB)
    ├── text_decoder.onnx         (61 MB)
    └── config.json
```

### 7.4. Rozmiar aplikacji

**Pomierzone rozmiary (rzeczywiste):**

| Komponent | Rozmiar |
|-----------|---------|
| APK base (bez modeli) | ~15 MB |
| Florence-2 modele | **215 MB** (INT8) |
| ViT-GPT2 modele | **387 MB** (FP32) |
| BLIP modele | **241 MB** (INT8) |
| **Modele razem** | **843 MB** |
| **APK z modelami (total)** | **~1716 MB** |

**Optymalizacje rozmiaru:**
- **Kwantyzacja INT8** - Florence-2 i BLIP używają INT8, co daje ~50% redukcję vs FP32
- **On-demand download** - możliwość dynamicznego pobierania modeli (nie zaimplementowane w v1.0)
- **Model compression** - dodatkowa kompresja ONNX graph (możliwa optymalizacja)
- **Selective inclusion** - użytkownicy mogą wybrać tylko wybrane modele podczas instalacji

### 7.5. Konfiguracja kluczy API

Dla cloud providers wymagane są klucze API. W wersji deweloperskiej konfigurowane przez SharedPreferences:

**OpenAI:**
- Utwórz konto na https://platform.openai.com
- Wygeneruj API key w sekcji API Keys
- Wprowadź w ustawieniach aplikacji lub w kodzie

**Azure Computer Vision:**
- Utwórz zasób Computer Vision w Azure Portal
- Skopiuj klucz i endpoint z sekcji Keys and Endpoint
- Konfiguracja w aplikacji

**Google Gemini:**
- Utwórz projekt w Google Cloud Console
- Włącz Vertex AI API
- Wygeneruj Service Account key (JSON)
- Import credentials do aplikacji

**Uwaga bezpieczeństwa:** W produkcji klucze API powinny być przechowywane w bezpieczny sposób (np. Android Keystore, backend proxy).

### 7.6. Build i uruchomienie

**Klonowanie repozytorium:**
```bash
git clone https://github.com/BacaSystem/WUT-Masters-Thesis
cd WUT-Masters-Thesis/Application
```

**Kompilacja:**
```bash
# Debug build
./gradlew assembleDebug

# Release build (wymaga keystore)
./gradlew assembleRelease
```

**Instalacja na urządzeniu:**
```bash
# Via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Lub przez Android Studio: Run > Run 'app'
```

**Weryfikacja instalacji modeli:**
- Sprawdź logcat dla komunikatów: "Loading ONNX model from assets..."
- Pierwsza inferencja może trwać dłużej (JIT compilation, warm-up)

---

## 8. Bibliografia

### Dokumentacja techniczna
1. **Android Developers** - https://developer.android.com/
2. **ONNX Runtime Documentation** - https://onnxruntime.ai/docs/
3. **Kotlin Coroutines Guide** - https://kotlinlang.org/docs/coroutines-overview.html
4. **OkHttp** - https://square.github.io/okhttp/
5. **Moshi JSON Library** - https://github.com/square/moshi

### Modele AI
1. **Florence-2: Advancing a Unified Representation for a Variety of Vision Tasks** - https://huggingface.co/microsoft/Florence-2-base
2. **ViT-GPT2 Image Captioning** - https://huggingface.co/nlpconnect/vit-gpt2-image-captioning
3. **BLIP: Bootstrapping Language-Image Pre-training** - https://huggingface.co/Salesforce/blip-image-captioning-base

### Cloud API
1. **OpenAI Vision API** - https://platform.openai.com/docs/guides/vision
2. **Azure Computer Vision 4.0** - https://learn.microsoft.com/azure/ai-services/computer-vision/
3. **Google Vertex AI Gemini** - https://cloud.google.com/vertex-ai/docs/generative-ai/multimodal

---