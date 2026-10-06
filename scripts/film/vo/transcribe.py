import sys
from faster_whisper import WhisperModel
m = WhisperModel("base.en", device="cpu", compute_type="int8", cpu_threads=2)
segs, _ = m.transcribe(sys.argv[1], language='en', word_timestamps=True)
for s in segs:
    print(f"{s.start:5.1f}-{s.end:5.1f} {s.text.strip()}")
    print('     ', ' '.join(f"{w.word.strip()}@{w.start:.1f}" for w in s.words))
