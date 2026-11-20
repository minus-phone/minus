# Evolutiva 016 · Revisione dopo la prova del prototipo

Stato: **approvata il 19 novembre 2026**, inserita nella PR della 0.20.1 come specifica, da implementare.
Parte dalla 0.20, che ha implementato la prima versione del prototipo «−minus · Prototipo micro-interazioni (E016)». Dopo la revisione del prototipo:

- sono **approvati** i momenti 1–9, 12 e 13, con le modifiche qui sotto;
- sono **scartati** i momenti 10 («Il meno che si disegna») e 11 («Togliere diventa un meno»), che vanno tolti dall'app.

## 1 · Token: più lento, ease-in-out (`Motion`, `MotionLogic`)

| Token | 0.20 | Ora |
|---|---|---|
| RAPIDO | 120 ms | **180 ms** |
| BASE | 240 ms | **400 ms** |
| LENTO | 480 ms | **720 ms** |

- **Una sola curva, ease-in-out:** `new PathInterpolator(0.45f, 0f, 0.55f, 1f)`. Sostituisce DECELERA.
- **Via la MOLLA:** il battito della casella spuntata va da scala 1 a 1,12 e ritorno con la stessa curva ease-in-out, senza effetto elastico.
- I conteggi animati (per esempio il numero del rapporto che scende) usano la stessa curva e durano **900 ms** invece di 600.
- Restano invariate le vibrazioni «conferma» e «scatto» e la dissolvenza di 80 ms con «Rimuovi animazioni».

## 2 · Togliere i momenti scartati

- **Il meno che si disegna (10):** niente animazione del logo all'avvio né al primo sblocco della mattina (`BrandView`, `MotionLogic.marca*`, `MainActivity`, `OnboardingActivity`).
- **Togliere diventa un meno (11):** task, scheda dello sblocco e pillola non si schiacciano più in una linea.
  - Task e scheda tornano a chiudersi in altezza e opacità (`Motion.collapse`, BASE) e il resto sale.
  - La pillola torna a ritirarsi: il testo si richiude, poi la pillola si rimpicciolisce e sfuma (BASE).
  - Da togliere `Motion.leave` e i suoi usi (`TaskRows`, `MainActivity`, `Pill`).

## 3 · Alone esterno alla pressione

Con il dito sopra un pulsante non si vede nessuna animazione interna. Ogni elemento premibile mostra quindi un riscontro **fuori** dal dito.

- **Pulsanti con forma** (pillola piena, pillola con bordo, «+ Nuovo task», barra «Cerca o chiedi», pulsanti della pausa, pulsanti tondi delle icone): anello esterno di **8 dp**, inchiostro al 10 %. Sul fondo scuro della pausa l'anello è `#E9E7E1` al 18 %.
- **Chip e link di testo:** anello di **6 dp**.
- **Caselle dei task e icone piccole:** alone circolare di **56 dp** (oggi 36–40 dp), più grande del polpastrello. Per le icone va da scala 0,8 a 1.
- **Righe senza bordo** (risultati della ricerca, righe delle impostazioni e degli interruttori): **nessun alone**, come nella 0.20. Si scurisce solo il fondo: `#E7E4DB` al 60 %, e `#DCD8CD` per la riga del primo risultato.
- Il riscontro resta visibile **almeno 240 ms**, anche con un tocco veloce.
- Aloni e anelli sono solo disegnati: le aree di tocco non cambiano.
- **Telefono (momento 12):** via l'inclinazione di −8° della cornetta (`Telefono`, `tilt`). Alla pressione resta solo l'alone esterno.

## 4 · Contenitori a tutta larghezza

Oggi l'alone delle caselle e il fondo premuto dei risultati della ricerca vengono tagliati a sinistra dal contenitore della pagina.

- Home, ricerca e liste occupano il **100 % della larghezza**.
- Il margine di 20 dp diventa padding interno delle righe e dei blocchi.
- Le righe dei task non ritagliano i figli: `clipChildren`/`clipToPadding` a false, oppure l'alone si disegna nell'overlay del genitore.

## 5 · Check di fine (momento 2)

- **Via l'effetto del «!» che sfuma nella spunta** sull'ultimo urgente (`MotionLogic.lastUrgent` e i suoi usi): era troppo poco visibile. A riga fatta il «!» diventa solo grigio come il testo.
- **Al suo posto, un check al centro dello schermo**, quando si spunta l'ultimo urgente oppure l'ultimo task della pagina «Oggi»:
  - sta **dietro** i contenuti, al centro della pagina;
  - un cerchio grigio leggero, inchiostro al 7 %, appare e sparisce in trasparenza (entra con scala da 0,94 a 1);
  - dentro il cerchio il segno di spunta, grigio `#8A877F`, **si disegna da sinistra a destra**;
  - tempi: entrata LENTO; la spunta si disegna in LENTO, partendo dopo 0,6 × LENTO; resta 900 ms; uscita LENTO;
  - se l'ultimo urgente è anche l'ultimo task, compare una volta sola;
  - TalkBack legge «Urgenti fatti» oppure «Tutto fatto per oggi»;
  - con «Rimuovi animazioni» il check compare già intero e con dissolvenze.

## 6 · Il check nei pulsanti di scelta («Non ora» e simili)

Per rinforzare l'idea che è stata fatta la scelta giusta:

- alla pressione di «Non ora, torna alla home» nella pausa, e in generale delle pillole piene e con bordo che confermano una scelta, **la label sfuma e al suo posto si disegna un piccolo check** al centro del pulsante, da sinistra a destra (BASE, con un ritardo di metà RAPIDO);
- nella pausa, solo quando il check è disegnato la schermata schiarisce verso la home (LENTO);
- «Apri ‹app›» resta senza check, passaggio rapido e neutro;
- con «Rimuovi animazioni» il check compare già intero.

## 7 · La sera (momento 13)

- **Tutti i testi cambiano colore insieme**, task compresi. Nessun elemento deve avere una transizione di colore sua: nel prototipo il testo dei task restava indietro proprio per questo.
- **La luna va all'inizio della riga della data**, non dopo. Entra in dissolvenza salendo di 6 dp (LENTO) e la data scivola a destra per farle posto (LENTO). Tornando al giorno avviene il contrario.

## Invariato rispetto alla 0.20

Spunta di un task (riempimento, conferma, un secondo per annullare, chiusura della riga), pillola che scorre, domanda dello sblocco, rapporto con le righe una alla volta, ricerca che sale dal basso con il primo risultato evidenziato, interruttori con il pallino che scorre e «Annulla», microfono che pulsa mentre ascolta, numero delle notifiche che scorre. Cambiano solo durate e curva (punto 1) e, per le righe, l'alone (punto 3).

## Da provare sul telefono

Alone che non viene tagliato nelle righe dei task e nei risultati della ricerca, check di fine dietro i contenuti, check in «Non ora» prima del passaggio alla home, sera con task e testi che cambiano colore insieme, TalkBack.
