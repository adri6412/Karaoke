# Karaoke

Progetto **Kotlin Multiplatform** per creare e riprodurre karaoke con testo
sincronizzato. È composto da un'**app Android** (player), un **tool desktop di
authoring** (per temporizzare il testo ed esportare i brani) e un modulo
**condiviso** con il formato proprietario `.krz` e i parser. A corredo, un tool
Python (`song2lrc`) che genera testi sincronizzati con l'AI.

```
audio + testo ──▶ desktopAuthor ──▶ brano.krz ──▶ app Android ──▶ karaoke
```

## Moduli

| Modulo | Tipo | Descrizione |
|---|---|---|
| [`shared`](shared/) | Kotlin/JVM | Modello dati, formato `.krz`, parser LRC e MIDI/KAR, logica di timing. Condiviso da app e tool. |
| [`androidApp`](androidApp/) | App Android (Compose) | Libreria brani + player karaoke con evidenziazione del testo. |
| [`desktopAuthor`](desktopAuthor/) | App desktop (Compose) | Carica audio, importa/sincronizza il testo ed esporta il `.krz`. |
| [`tools/song2lrc`](tools/song2lrc/) | Python | Genera `.lrc` sincronizzati da un audio con Demucs + Whisper. Vedi il suo [README](tools/song2lrc/README.md). |

## Il formato `.krz`

Un `.krz` è un archivio **ZIP** con un piccolo contenitore proprietario:

```
brano.krz
├── manifest.json   # metadati: titolo, artista, tipo audio, modalità, durata
├── lyrics.json     # testo temporizzato (LyricsDoc): righe → parole con startMs/endMs
├── audio.mp3       # oppure song.mid  (o un video .mp4)
└── cover.*         # opzionale
```

- **Audio**: MP3, MIDI o video MP4 (con testo già impresso, senza `lyrics.json`).
- **Testo** (`lyrics.json`): lista di righe; ogni riga ha parole con tempo di
  inizio/fine in millisecondi → consente l'evidenziazione parola-per-parola.
- **Modalità di evidenziazione** (`highlightMode`):
  - `word` — ogni parola si riempie al suo tempo (LRC enhanced / tap-sync);
  - `line` — l'intera riga si riempie tra un timecode e il successivo (LRC standard).

Lettura/scrittura: [`Krz.kt`](shared/src/main/kotlin/karaoke/shared/krz/Krz.kt) ·
modello: [`Models.kt`](shared/src/main/kotlin/karaoke/shared/model/Models.kt).

## Formati di input supportati

- **`.lrc`** standard (`[mm:ss.xx]testo`) ed **enhanced** per-parola
  (`[mm:ss.xx]<mm:ss.xx>par<mm:ss.xx>ola`) →
  [`LrcParser`](shared/src/main/kotlin/karaoke/shared/lyrics/LrcParser.kt).
- **`.mid` / `.kar`** (Soft-Karaoke): testo estratto dai meta-eventi Lyric/Text →
  [`KarLyrics`](shared/src/main/kotlin/karaoke/shared/lyrics/KarLyrics.kt).
- **`.mp4`**: video con testo già impresso (nessun testo separato).

## Flusso d'uso tipico

1. *(Opzionale)* genera il testo sincronizzato con
   [`song2lrc`](tools/song2lrc/) partendo da un MP3.
2. Apri il **desktopAuthor**: carica l'audio, **importa l'LRC** (o scrivi il testo
   e sincronizzalo col **tap-sync**), rifinisci i tempi.
3. **Esporta** il `.krz`.
4. Apri l'**app Android**, importa il `.krz` e premi play: il testo scorre
   sincronizzato con la musica.

## Build & run

Richiede **JDK 17+**. Per l'app Android serve l'**Android SDK**: crea
`local.properties` con `sdk.dir=...` (è ignorato da git).

```bash
# App desktop di authoring
./gradlew :desktopAuthor:run

# App Android (debug) su un dispositivo/emulatore collegato
./gradlew :androidApp:installDebug

# Test del modulo condiviso (parser LRC/MIDI, round-trip .krz)
./gradlew :shared:test
```

Su Windows usa `gradlew.bat` al posto di `./gradlew`.

## Player audio su Android

- **MP3** → **ExoPlayer** (media3): posizione di riproduzione accurata, testo
  perfettamente sincronizzato
  ([`AudioKaraokePlayer`](androidApp/src/main/kotlin/karaoke/app/player/AudioKaraokePlayer.kt)).
- **MIDI / KAR** → **MediaPlayer** (sintetizzatore Sonivox di Android: ExoPlayer
  non sintetizza i MIDI)
  ([`MidiKaraokePlayer`](androidApp/src/main/kotlin/karaoke/app/player/MidiKaraokePlayer.kt)).
- **Video MP4** → **ExoPlayer**
  ([`VideoKaraokePlayer`](androidApp/src/main/kotlin/karaoke/app/player/VideoKaraokePlayer.kt)).

## Licenza

Vedi [`LICENSE`](LICENSE). I file audio/`.krz` di esempio **non** sono inclusi nel
repository (materiale protetto da copyright) e sono esclusi via `.gitignore`.
