# Sprawdź swoje modele Florence-2

## Krok 1: Zobacz jakie pliki ONNX masz

```powershell
cd c:\Studia\mgr\Application\app\src\main\assets\models\florence2
Get-ChildItem *.onnx | Select-Object Name, @{Name="MB";Expression={[math]::Round($_.Length/1MB,1)}}
```

## Krok 2: Struktury modeli w repozytorium onnx-community/Florence-2-base-ft

W folderze `onnx/` powinny być następujące pliki:

### Opcja A: Pełny zestaw (3 modele)
1. **vision_encoder.onnx** (~100 MB)
   - Input: `pixel_values` [1, 3, 768, 768]
   - Output: `last_hidden_state` [1, seq_len, hidden_dim]

2. **decoder_model_merged.onnx** (~115 MB)  ← POTRZEBUJEMY TEGO
   - Input: `input_ids` [1, seq_len]
   - Input: `encoder_hidden_states` [1, enc_seq_len, hidden_dim]
   - Input: `attention_mask` [1, seq_len]
   - Output: `logits` [1, seq_len, vocab_size]
   - ✅ Prostsza struktura, bez KV cache

3. **decoder_model.onnx** (~115 MB)  ← TO MASZ TERAZ
   - Input: `inputs_embeds` [1, seq_len, hidden_dim]  ← Wymaga embeddingów!
   - Input: `encoder_hidden_states` [1, enc_seq_len, hidden_dim]
   - Input: `past_key_values.*` (wiele tensorów dla KV cache)
   - Output: `logits` + `present_key_values.*`
   - ❌ Bardziej złożona, wymaga dodatkowych modeli

### Opcja B: Dodatkowe modele embeddingów
4. **embed_tokens.onnx** (rzadko dostępny)
   - Input: `input_ids` [1, seq_len]
   - Output: `embeddings` [1, seq_len, hidden_dim]

## Krok 3: Pobierz brakujący plik

### Jeśli nie masz `decoder_model_merged.onnx`:

```bash
cd c:\Studia\mgr\Application\app\src\main\assets\models\florence2

# Pobierz bezpośrednio z HuggingFace
# Windows PowerShell:
Invoke-WebRequest -Uri "https://huggingface.co/onnx-community/Florence-2-base-ft/resolve/main/onnx/decoder_model_merged.onnx" -OutFile "decoder_model_merged.onnx"
```

Lub przez przeglądarkę:
https://huggingface.co/onnx-community/Florence-2-base-ft/resolve/main/onnx/decoder_model_merged.onnx

## Krok 4: Alternatywa - użyj całego repo

Jeśli masz Git LFS:

```bash
cd c:\Studia\mgr\Application\app\src\main\assets\models

# Usuń stary folder (jeśli niekompletny)
rm -r florence2

# Sklonuj pełne repo
git lfs install
git clone https://huggingface.co/onnx-community/Florence-2-base-ft florence2
```

## Krok 5: Sprawdź strukturę po pobraniu

Powinna być taka:

```
models/florence2/
├── onnx/
│   ├── vision_encoder.onnx           (~100 MB)
│   ├── decoder_model_merged.onnx     (~115 MB)  ← KLUCZOWY
│   └── decoder_model.onnx            (~115 MB)  ← Opcjonalny
├── tokenizer.json                     (~2 MB)
├── config.json
└── ... (inne pliki)
```

## Krok 6: Zaktualizuj kod

Gdy masz `decoder_model_merged.onnx`, kod powinien działać.

Jeśli masz tylko `decoder_model.onnx` (bez merged), potrzebujesz dodatkowych zmian w kodzie.

---

**Pytanie do Ciebie:**

Jakie pliki `.onnx` widzisz w swoim folderze `models/florence2`?

Uruchom:
```powershell
cd c:\Studia\mgr\Application\app\src\main\assets\models\florence2
ls *.onnx
```

I wklej wynik tutaj.
