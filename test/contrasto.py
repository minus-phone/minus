#!/usr/bin/env python3
"""0.11: contrasto dei colori del tema, chiaro e scuro, letti da U.applyTheme (WCAG 2.1 AA)."""
import re, sys
src = open('src/it/essenziale/launcher/U.java', encoding='utf-8').read()
T = {'chiaro': {}, 'scuro': {}}
for k, d, l in re.findall(r'\n\s+(\w+) = Color\.parseColor\(d \? "#(\w{6})" : "#(\w{6})"\)', src):
    T['scuro'][k] = d; T['chiaro'][k] = l
def L(h):
    c = [int(h[i:i+2], 16) / 255 for i in (0, 2, 4)]
    c = [x / 12.92 if x <= 0.03928 else ((x + .055) / 1.055) ** 2.4 for x in c]
    return .2126 * c[0] + .7152 * c[1] + .0722 * c[2]
def cr(a, b):
    a, b = L(a), L(b); return (max(a, b) + .05) / (min(a, b) + .05)
# (primo piano, sfondo, minimo): testo 4.5, componenti e bordi dei campi 3
PAIRS = [(f, b, 4.5) for f in ('INK', 'MUTED', 'ALERT', 'URGENT') for b in ('BG', 'SURFACE', 'CHIP')] + \
        [('BG', 'INK', 4.5), ('BORDER', 'BG', 3), ('BORDER', 'SURFACE', 3), ('TRACK_OFF', 'BG', 3), ('INK', 'BADGE', 4.5)]
bad = 0
for th, t in T.items():
    for f, b, m in PAIRS:
        v = cr(t[f], t[b])
        if v < m: bad += 1; print(f'FAIL {th}: {f} su {b} {v:.2f} < {m}')
        else: print(f'ok   {th}: {f} su {b} {v:.2f}')
sys.exit(1 if bad else 0)
