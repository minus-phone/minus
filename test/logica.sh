#!/usr/bin/env bash
# Test della logica di minus: classi pure, senza Android. Uso: test/logica.sh
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=nrt-out; rm -rf "$OUT"; mkdir -p "$OUT/classes"
javac -nowarn --release 8 -encoding UTF-8 -d "$OUT/classes" \
  src/it/essenziale/launcher/{Rules,Rec,VoiceParser,Quando,Cerca,Tempo,Scopo,Notte,Cosa}.java test/it/essenziale/launcher/*.java
java -Dfile.encoding=UTF-8 -cp "$OUT/classes" it.essenziale.launcher.NrtTest "$OUT/nrt-logica.md"
python3 test/contrasto.py
