# song2lrc

Tool **esterno** in Python che genera un file **`.lrc`** (testo karaoke sincronizzato)
da un file audio, usando modelli AI **locali su GPU NVIDIA**. Il `.lrc` si importa
poi nel **desktopAuthor** ("Importa LRC").

## Come funziona

```
audio ──▶ Demucs (isola la voce) ──▶ faster-whisper (CUDA, word timestamps) ──▶ .lrc
```

1. **Demucs** separa la voce dalla base musicale → la trascrizione del cantato è
   molto più accurata. Gira in un processo separato così la VRAM si libera prima
   di Whisper (importante su GPU da 6 GB, es. RTX 2060).
2. **faster-whisper** trascrive e assegna i tempi a ogni **parola**.
3. Viene scritto un **LRC "enhanced"** (timing per-parola), nel formato letto dal
   `LrcParser` del progetto:
   `[mm:ss.cc]<mm:ss.cc>par<mm:ss.cc>ola` → evidenziazione parola-per-parola.
   Con `--line` genera invece l'LRC standard per-riga.

## Requisiti

- Python 3.10+ con **torch + CUDA** già funzionante (qui: torch 2.6.0+cu124).
- **ffmpeg** nel PATH (per decodificare mp3/m4a/...). Già presente.
- GPU NVIDIA. Con 6 GB di VRAM usa `large-v3` (Demucs e Whisper girano in sequenza).

## Installazione

```powershell
cd tools\song2lrc
pip install -r requirements.txt
# se Demucs si lamenta di torchaudio:
#   pip install torchaudio==2.6.0 --index-url https://download.pytorch.org/whl/cu124
```

Al primo avvio vengono scaricati i modelli (Whisper `large-v3` ~3 GB, Demucs
`htdemucs` ~150 MB) nella cache di Hugging Face / torch.

## Uso

```powershell
# base: genera canzone.lrc accanto all'audio
python song2lrc.py "C:\musica\canzone.mp3"

# specifica lingua e output
python song2lrc.py "canzone.mp3" -o "canzone.lrc" --language it

# salta la separazione voce (più veloce, meno preciso su musica)
python song2lrc.py "canzone.mp3" --no-vocals

# LRC standard per-riga invece che per-parola
python song2lrc.py "canzone.mp3" --line

# poca VRAM: modello più piccolo o quantizzazione int8
python song2lrc.py "canzone.mp3" --model medium
python song2lrc.py "canzone.mp3" --compute-type int8_float16

# metadati nel file
python song2lrc.py "canzone.mp3" --title "Titolo" --artist "Artista"
```

## Opzioni principali

| Opzione | Default | Note |
|---|---|---|
| `--model` | `large-v3` | tiny/base/small/medium/large-v3 |
| `--language` | autodetect | es. `it`, `en` — indicarla migliora l'accuratezza |
| `--compute-type` | `float16` | `int8_float16`/`int8` se la VRAM è poca |
| `--no-vocals` | off | salta Demucs |
| `--line` | off | LRC per-riga invece che per-parola |
| `--device` | `cuda` | `cpu` per forzare la CPU |

## Flusso completo

1. `python song2lrc.py "brano.mp3" --language it`
2. Apri il **desktopAuthor** → **Importa LRC** → scegli `brano.lrc`.
3. Rifinisci i tempi col tap-sync se serve, poi esporta il `.krz`.

## Note

- I tempi di Whisper sul cantato sono buoni ma non perfetti: per il karaoke
  conviene una rapida rifinitura nel desktopAuthor (che è fatto apposta).
- Se vedi `Could not locate cudnn...`: `pip install nvidia-cudnn-cu12 nvidia-cublas-cu12`.
- L'accuratezza migliore si ha con Demucs attivo + `--language` corretta.
