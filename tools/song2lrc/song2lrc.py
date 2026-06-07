#!/usr/bin/env python3
# -*- coding: utf-8 -*-

from __future__ import annotations

import gc
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import tkinter as tk
from tkinter import filedialog, messagebox, ttk
from pathlib import Path

def ms_to_tag(ms: float) -> str:
    if ms < 0:
        ms = 0
    total_cs = int(round(ms / 10.0))
    cc = total_cs % 100
    total_s = total_cs // 100
    ss = total_s % 60
    mm = total_s // 60
    return f"{mm:02d}:{ss:02d}.{cc:02d}"

def build_lrc(segments, title: str | None, artist: str | None, line_level: bool) -> str:
    out: list[str] = []
    if title:
        out.append(f"[ti:{title}]")
    if artist:
        out.append(f"[ar:{artist}]")
    out.append("[offset:0]")
    out.append("[by:song2lrc]")

    for seg in segments:
        text = seg.get("text", "").strip()
        if not text:
            continue
            
        words = [w for w in (seg.get("words") or []) if w.get("text", "").strip()]

        if line_level or not words:
            start = seg.get("start", 0.0)
            out.append(f"[{ms_to_tag(start * 1000)}]{text}")
        else:
            line_start = words[0]["start"]
            chunks = [f"[{ms_to_tag(line_start * 1000)}]"]
            for w in words:
                word_text = w["text"].strip()
                chunks.append(f"<{ms_to_tag(w['start'] * 1000)}>{word_text} ")
            out.append("".join(chunks).rstrip())

    return "\n".join(out) + "\n"

def separate_vocals(audio: Path, model: str, device: str, tmp: Path, logger) -> Path:
    out_dir = tmp / "demucs"
    out_dir.mkdir(parents=True, exist_ok=True)
    
    # Usiamo sys.executable per essere sicuri di usare lo stesso ambiente python
    cmd = [
        sys.executable, "-m", "demucs",
        "--two-stems=vocals",
        "-n", model,
        "-d", device,
        "-o", str(out_dir),
        str(audio),
    ]
    logger("[1/3] Demucs - Avvio separazione traccia vocale. Attendere...\n")
    
    # Catturiamo i log in tempo reale per capire se si pianta
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        logger(f"\n[ERRORE CRITICO DEMUCS]\nStdout: {proc.stdout}\nStderr: {proc.stderr}\n")
        raise RuntimeError(f"Demucs ha fallito con codice {proc.returncode}. Controlla la console di log.")

    # Cerchiamo il file vocals.wav generato
    found = list(out_dir.rglob("vocals.wav"))
    if not found or not found[0].exists():
        # Prova a cercare qualsiasi file .wav se il nome cambia a seconda della versione
        found = list(out_dir.rglob("*.wav"))
        if not found:
            raise RuntimeError("Demucs non ha prodotto nessun file audio .wav!")
            
    vocals = found[0]
    logger(f"      Voce isolata temporaneamente in: {vocals.name}\n")
    return vocals

def transcribe(audio: Path, model_name: str, device: str, compute_type: str,
               language: str | None, logger) -> tuple[list[dict], str]:
    from faster_whisper import WhisperModel

    logger(f"[2/3] Whisper - Caricamento modello ({model_name})...\n")
    model = WhisperModel(model_name, device=device, compute_type=compute_type)

    logger("[2/3] Whisper - Analisi del canto e generazione testo...\n")
    
    # Ottimizzato per il canto (no vad_filter per evitare tagli, temperature fisse)
    segments_gen, info = model.transcribe(
        str(audio),
        language=language if language != "Autodetect" else None,
        task="transcribe",
        word_timestamps=True,
        vad_filter=False,  
        beam_size=5,
        temperature=[0.0, 0.2, 0.4, 0.6],
        condition_on_previous_text=False,
    )

    segments: list[dict] = []
    has_output = False
    
    for seg in segments_gen:
        if not seg.text.strip():
            continue
            
        has_output = True
        words = []
        if seg.words:
            for w in seg.words:
                if w.start is None or w.end is None:
                    continue
                txt = w.word.strip()
                if txt:
                    words.append({"text": txt, "start": w.start, "end": w.end})
                    
        segments.append({"start": seg.start, "end": seg.end,
                         "text": seg.text.strip(), "words": words})
        logger(f"      [{ms_to_tag(seg.start*1000)}] {seg.text.strip()}\n")

    if not has_output:
        logger("[ATTENZIONE] Whisper non ha trovato testo comprensibile!\n")

    detected = language if language != "Autodetect" else info.language
    logger(f"      Lingua impostata/rilevata: {detected}\n")

    del model
    gc.collect()
    try:
        import torch
        torch.cuda.empty_cache()
    except Exception:
        pass

    return segments, detected

class Song2LrcGUI:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("Song2Lrc - Generatore Karaoke AI")
        self.root.geometry("650x580")
        self.root.minsize(600, 520)

        self.file_path_var = tk.StringVar()
        self.model_var = tk.StringVar(value="large-v3")
        self.lang_var = tk.StringVar(value="Autodetect")
        self.compute_var = tk.StringVar(value="float16")
        self.vocals_var = tk.BooleanVar(value=True)
        self.save_wav_var = tk.BooleanVar(value=True)  # Attivo di default per scovare i problemi
        self.format_var = tk.StringVar(value="Parola per Parola")
        self.title_var = tk.StringVar()
        self.artist_var = tk.StringVar()

        self._create_widgets()

    def _create_widgets(self):
        file_frame = ttk.LabelFrame(self.root, text=" 1. Seleziona Brano Audio ", padding=10)
        file_frame.pack(fill="x", padx=15, pady=10)
        ttk.Entry(file_frame, textvariable=self.file_path_var, width=50).pack(side="left", expand=True, fill="x", padx=(0, 5))
        ttk.Button(file_frame, text="Sfoglia...", command=self._browse_file).pack(side="right")

        opt_frame = ttk.LabelFrame(self.root, text=" 2. Impostazioni Avanzate ", padding=10)
        opt_frame.pack(fill="x", padx=15, pady=5)
        ttk.Label(opt_frame, text="Modello Whisper:").grid(row=0, column=0, sticky="w", pady=5)
        ttk.Combobox(opt_frame, textvariable=self.model_var, values=["tiny", "base", "small", "medium", "large-v3"], state="readonly", width=15).grid(row=0, column=1, sticky="w", padx=5)
        ttk.Label(opt_frame, text="Lingua:").grid(row=0, column=2, sticky="w", pady=5, padx=(15, 0))
        ttk.Combobox(opt_frame, textvariable=self.lang_var, values=["Autodetect", "it", "en", "es", "fr", "de"], state="readonly", width=12).grid(row=0, column=3, sticky="w", padx=5)
        ttk.Label(opt_frame, text="Precisione (VRAM):").grid(row=1, column=0, sticky="w", pady=5)
        ttk.Combobox(opt_frame, textvariable=self.compute_var, values=["float16", "int8_float16", "int8", "float32"], state="readonly", width=15).grid(row=1, column=1, sticky="w", padx=5)
        ttk.Label(opt_frame, text="Tipo LRC:").grid(row=1, column=2, sticky="w", pady=5, padx=(15, 0))
        ttk.Combobox(opt_frame, textvariable=self.format_var, values=["Parola per Parola", "Riga per Riga"], state="readonly", width=15).grid(row=1, column=3, sticky="w", padx=5)
        
        ttk.Checkbutton(opt_frame, text="Isola la voce con Demucs (Consigliato)", variable=self.vocals_var).grid(row=2, column=0, columnspan=4, sticky="w", pady=(8, 2))
        ttk.Checkbutton(opt_frame, text="Forza salvataggio traccia vocale (.wav) nella cartella del brano", variable=self.save_wav_var).grid(row=3, column=0, columnspan=4, sticky="w", pady=(0, 4))

        meta_frame = ttk.LabelFrame(self.root, text=" 3. Metadati LRC (Opzionale) ", padding=10)
        meta_frame.pack(fill="x", padx=15, pady=5)
        ttk.Label(meta_frame, text="Titolo:").grid(row=0, column=0, sticky="w")
        ttk.Entry(meta_frame, textvariable=self.title_var, width=20).grid(row=0, column=1, padx=5, sticky="ew")
        ttk.Label(meta_frame, text="Artista:").grid(row=0, column=2, sticky="w", padx=(15, 0))
        ttk.Entry(meta_frame, textvariable=self.artist_var, width=20).grid(row=0, column=3, padx=5, sticky="ew")

        log_frame = ttk.LabelFrame(self.root, text=" Stato Avanzamento ", padding=5)
        log_frame.pack(expand=True, fill="both", padx=15, pady=10)
        self.log_area = tk.Text(log_frame, height=8, state="disabled", wrap="word", background="#f4f4f4")
        self.log_area.pack(expand=True, fill="both", side="top")
        self.progress = ttk.Progressbar(log_frame, mode="indeterminate")
        self.progress.pack(fill="x", pady=(5, 0))

        self.btn_run = ttk.Button(self.root, text="GENERA FILE LRC", command=self._start_processing_thread)
        self.btn_run.pack(pady=10, ipadx=20, ipady=5)

    def _browse_file(self):
        file_selected = filedialog.askopenfilename(
            title="Seleziona traccia audio",
            filetypes=[("File Audio", "*.mp3 *.wav *.m4a *.flac *.ogg"), ("Tutti i file", "*.*")]
        )
        if file_selected:
            self.file_path_var.set(file_selected)

    def _log(self, text: str):
        self.log_area.configure(state="normal")
        self.log_area.insert(tk.END, text)
        self.log_area.see(tk.END)
        self.log_area.configure(state="disabled")

    def _start_processing_thread(self):
        if not self.file_path_var.get():
            messagebox.showwarning("Attenzione", "Per favore, seleziona prima un file audio.")
            return
        self.btn_run.configure(state="disabled")
        self.progress.start(10)
        threading.Thread(target=self._process, daemon=True).start()

    def _process(self):
        audio_path = Path(self.file_path_var.get()).resolve()
        output_path = audio_path.with_suffix(".lrc")
        tmp_dir = Path(tempfile.mkdtemp(prefix="song2lrc_gui_"))
        
        self._log(f"--- Inizio elaborazione per: {audio_path.name} ---\n")

        try:
            source = audio_path
            
            if self.vocals_var.get():
                # separate_vocals ora BLOCCA il programma se fallisce
                source = separate_vocals(audio_path, "htdemucs", "cuda", tmp_dir, self._log)
                
                # Salvataggio immediato del WAV per evitare che sparisca
                if self.save_wav_var.get():
                    destination_wav = audio_path.parent / f"{audio_path.stem}_vocale.wav"
                    shutil.copy2(source, destination_wav)
                    self._log(f"      [FILE SALVATO] Traccia vocale estratta in:\n      {destination_wav}\n")
            else:
                self._log("[1/3] Demucs disattivato manualmente.\n")

            line_level_mode = (self.format_var.get() == "Riga per Riga")
            
            segments, lang = transcribe(
                source, self.model_var.get(), "cuda", self.compute_var.get(), self.lang_var.get(), self._log
            )

            self._log("[3/3] Generazione file .lrc definitivo...\n")
            lrc_content = build_lrc(
                segments, self.title_var.get() or None, self.artist_var.get() or None, line_level_mode
            )
            output_path.write_text(lrc_content, encoding="utf-8")

            n_lines = len([l for l in lrc_content.splitlines() if l.strip() and not l.startswith(('[ti:', '[ar:', '[off', '[by:'))])
            
            self._log(f"\n[COMPLETATO] File salvato con successo in:\n{output_path}\n")
            messagebox.showinfo("Successo", f"File LRC generato con {n_lines} righe!")

        except Exception as e:
            self._log(f"\n[ERRORE DI SISTEMA] {str(e)}\n")
            messagebox.showerror("Errore", f"Si è verificato un errore:\n{str(e)}")
        finally:
            shutil.rmtree(tmp_dir, ignore_errors=True)
            self.progress.stop()
            self.btn_run.configure(state="normal")

if __name__ == "__main__":
    root = tk.Tk()
    app = Song2LrcGUI(root)
    root.mainloop()