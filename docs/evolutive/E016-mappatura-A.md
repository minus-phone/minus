# Evolutiva 016 · Mappatura stati premuti e movimento, area A e schermate collegate

Versione 0.21, dopo la prova sul telefono (tema scuro). Fonte di verità: il prototipo (momenti 7 Ricerca, 8 Pulsanti, 9 Interruttori) e `E016-revisione.md`. Famiglie in `Press.java`:

- **pillola** (piena o con bordo): fondo + anello esterno 8 dp (`Press.primary`, `Press.outlined`, `Press.box(...).ring`)
- **chip / link / linguetta**: fondo + anello 6 dp (`Press.chip`, `Press.chipOn`, `Press.link`); sulla pausa scura e sul blocco l'anello è chiaro
- **icona / casella / checkbox**: cerchio 56 dp, da scala 0,8 a 1 (`Press.icon`, `Press.iconOnDark`, `Press.checkHalo`); icona tonda con bordo: anello 8 dp (`Press.circle`)
- **riga senza bordo**: solo fondo più scuro, nessun alone (`Press.row`, `Press.rowBest`, `Press.rowOnDark`)
- **badge con iniziale**: la riga scurisce, il badge si riempie d'inchiostro, la lettera diventa chiara (`Press.rowWithBadge`, `Press.tile`), almeno 240 ms

Tutti gli stati premuti entrano ed escono in RAPIDO (180 ms) con la curva unica; gli aloni restano almeno 240 ms. Gli aloni si disegnano nell'overlay della radice: nessun genitore li ritaglia. Dalla 0.21 c'è anche una rete di sicurezza: se la vista non è più premuta, o la schermata esce di scena, l'alone si toglie comunque (causa dell'alone «appeso» visto sul telefono).

Stato: **fatto** = aggiunto in questa revisione; **già c'era** = presente dalla 0.20 o dalla 0.21 iniziale; **non applicabile** = con il motivo.

Contenitori che scorrono (0.21): le ScrollView ritagliano ai propri bordi (nulla disegna sopra l'intestazione o sotto la barra); il contenuto interno non ritaglia nel proprio padding di 20 dp, così i fondi premuti possono sporgere di lato; gli aloni stanno nell'overlay.

## Home (MainActivity)

| Elemento | Famiglia pressed | Animazione | Stato |
|---|---|---|---|
| «Settimana» | link (6 dp) | scorrimento a sinistra = dissolvenza di sistema | già c'era |
| «Notifiche» + pallino | riga senza bordo | il numero scorre (RollText) | già c'era |
| Pillola del tempo (sopra le app frenate, in home) | pillola piena, anello 8 dp | la pillola scorre (momento 4) | fatto (pressed) / già c'era (movimento) |
| Sveglia di oggi (accanto alla data) | link | nessuna | già c'era |
| Chip «tutto il giorno» | chip (6 dp) | nessuna | fatto |
| Riga impegno | riga senza bordo (fondo sporge 10 dp) | nessuna | fatto |
| Casella del task | cerchio 56 dp (`checkHalo`) | riempimento orario, spunta, vibrazione conferma, chiusura riga (momento 1) | già c'era |
| Riga del task | riga senza bordo (fondo sporge 10 dp) | chiusura in altezza e opacità (`Motion.collapse`) | fatto (pressed) / già c'era (movimento) |
| «n task completati ▾/▴» | link | l'elenco si apre e si chiude di colpo: il prototipo non definisce un movimento | fatto (pressed); movimento non applicabile |
| «Nuovo» (riga tratteggiata) | riga «Nuovo»: bordo scuro, fondo CHIP 50%, anello 8 dp | nessuna | già c'era |
| Barra «Cerca o chiedi» | pillola con bordo, fondo CHIP, anello 8 dp | la ricerca sale dal basso (`Motion.openSearch`) | già c'era |
| Telefono (a sinistra della barra) | icona tonda con bordo: anello 8 dp, nessun movimento della cornetta | nessuna | già c'era |
| Microfono (MicView) | icona: cerchio 56 dp | pulsa solo mentre ascolta (momento 12) | già c'era |
| «Il filtro notifiche è spento · attivalo ›» | link | nessuna | già c'era |
| Riga setup («Da completare», controlli, fine prova) e suo ✕ | riga senza bordo / icona 56 dp | nessuna | già c'era |
| Domanda dello sblocco: chip di risposta | chip (6 dp); scelto = inchiostro pieno | chip scelto, poi la scheda si chiude (`Motion.collapse`, momento 5) | già c'era |
| «Non chiedermelo adesso», «Mi serve il telefono adesso ›» | link | la scheda si chiude come dopo una risposta | già c'era |
| Card Adesso («Fatto», «Più tardi», «Oppure…», «+ Aggiungi un task») | pillola piena / con bordo, link, riga | nessuna | già c'era |
| «Va bene» e «Ancora 15 min» (ora di staccare) | pillola piena / con bordo, anello 8 dp | la sera: colori insieme, luna e data (momento 13) | già c'era |
| Sveglia in «Domani» («Nessuna sveglia ›») | link (chiaro sul blocco) | nessuna | fatto |
| «Tocca qui per permettere la lettura del calendario» | link | nessuna | fatto |
| Menu del tocco lungo (Modifica, Sposta, ! Urgente, Elimina) | riga senza bordo | il menu è una finestra di sistema che si chiude subito: l'interruttore non si vede muovere | fatto (pressed); movimento non applicabile |
| Snackbar «Annulla» | link chiaro sul fondo scuro | compare e sparisce di colpo | già c'era (pressed); movimento: il prototipo non lo definisce |
| Tocco lungo su spazio vuoto (apre le Impostazioni) | non applicabile: nessun elemento visibile da premere | nessuna | non applicabile |
| Festa «Tutto fatto per oggi» / «Urgenti fatti» | non interattiva | check al centro, dietro i contenuti (momento 2) | già c'era |

## Ricerca (SearchActivity)

| Elemento | Famiglia pressed | Animazione | Stato |
|---|---|---|---|
| «Cambia impostazioni» | solo anello 8 dp (angoli vivi); non cambia fondo | nessuna | già c'era; corretto l'alone che restava appeso (rete di sicurezza in `Press.Halo`) |
| ✕ in alto, ✕ nel campo | icona: cerchio 56 dp | ✕ nel campo entra in dissolvenza (RAPIDO) | già c'era |
| Microfono nel campo | icona: cerchio 56 dp | pulsa mentre ascolta | già c'era |
| Telefono (a sinistra del campo) | icona tonda con bordo: anello 8 dp | nessuna | già c'era |
| Chip «Cercate di recente» | chip, anello 6 dp | nessuna | già c'era |
| Celle «App a portata» | riga senza bordo | i risultati si riordinano senza sfarfallio (momento 7) | già c'era |
| Tessere «Contatti e gruppi» (tonda e quadrata) | tessera piena d'inchiostro, lettera chiara, riga più scura; almeno 240 ms; chiaro e scuro | nessuna | fatto |
| Tessera «+ Scegli chi passa sempre» (tratteggiata) | riga senza bordo | nessuna | già c'era |
| Righe conversazione e persona, con badge dell'iniziale | riga più scura + badge pieno d'inchiostro, lettera chiara; almeno 240 ms | nessuna | già c'era; aggiunto il minimo di 240 ms |
| Stato come link («● passa sempre», «○ fino alle…») | link | nessuna | già c'era |
| «Vedi tutti ›» | link | nessuna | già c'era |
| Righe App, Impegni, Azioni, «Chiedi…», «Cerca sul web» | riga senza bordo | nessuna | già c'era |
| Primo risultato (riquadro scuro) | `rowBest` (#DCD8CD sul chiaro) | il riquadro entra in dissolvenza (RAPIDO) | già c'era; ora conserva anche lo stato del badge |
| Apertura e chiusura della ricerca | non applicabile | sale dal basso, esce verso il basso, `fadeOutSearch` verso un'app | già c'era |
| Campo di testo, elenco che scorre | non applicabile: non è un pulsante | l'elenco ritaglia in alto e in basso (non copre più «Cambia impostazioni» né la barra) | corretto |
| Finestre di scelta (app disponibili, `Pass.sheet`, `AppChoice`) | non applicabile: finestre di sistema con il ripple di sistema | nessuna | non applicabile |

## Blocco (LockActivity)

| Elemento | Famiglia pressed | Animazione | Stato |
|---|---|---|---|
| Torcia (icona `ic_torcia`, 48 dp, descrizione «Torcia, accesa/spenta») | icona su fondo scuro: cerchio 56 dp chiaro (ON_DARK 14%) | acceso: il fondo si inverte (nessuna transizione definita) | fatto |
| Fotocamera (icona `ic_fotocamera`, 48 dp) | icona su fondo scuro: cerchio 56 dp chiaro | transizione di sistema | fatto |
| «Sblocca» | riga senza bordo su fondo scuro (ON_DARK 8%) | richiesta di sblocco di sistema | fatto |
| «Emergenza» | pillola con bordo su fondo scuro, anello chiaro 8 dp | nessuna | fatto |
| «Notifiche» + numero | link su fondo scuro | nessuna | fatto |
| Riga «TASK» | riga senza bordo su fondo scuro | nessuna | fatto |
| Riga impegno modificabile | riga senza bordo su fondo scuro | nessuna | fatto |
| Impegno non modificabile, chip «tutto il giorno» (senza tocco), «+N altri» | non applicabile: non sono interattivi | nessuna | non applicabile |
| «Domani» con sveglia | link su fondo scuro | nessuna | fatto |

## Notifiche (InboxActivity)

| Elemento | Famiglia pressed | Animazione | Stato |
|---|---|---|---|
| «‹ Home» | link, fondo e anello 6 dp | transizione di sistema | fatto (`U.screen`, vale per tutte le schermate che lo usano) |
| ⚙ in alto a destra | icona: cerchio 56 dp | nessuna | fatto |
| Linguette «Per te» / «Altro» | come chip (6 dp). Scelta = inchiostro pieno con testo chiaro; non scelta = trasparente con testo INK sul fondo della barra, in chiaro e scuro | cambio di lista senza movimento (il prototipo non lo definisce). Lo sfondo di ogni linguetta è uno solo, ricolorato: l'alone non resta appeso | fatto (corretto il testo invisibile e l'anello fisso) |
| Riga della notifica | riga senza bordo | nessuna (la rimozione non ha un movimento nel prototipo) | fatto (pressed) |
| ✓ «Segna come visto» (cerchio 44 dp con bordo) | icona tonda con bordo: riempimento 10% e anello 8 dp | nessuna | fatto |
| «Nuova app»: Passa sempre / In Per te / In Altro | pillola con bordo, anello 8 dp | nessuna | già c'era |
| «Segna tutto come visto» | link, 48 dp di altezza | nessuna | fatto |
| Tocco lungo (scelta della persona o dell'app) | non applicabile: finestre di sistema | nessuna | non applicabile |

## Voce (VoiceActivity, VoiceHelpActivity)

| Elemento | Famiglia pressed | Animazione | Stato |
|---|---|---|---|
| «‹ Home» (VoiceActivity) | link | transizione di sistema | fatto |
| «Cosa posso dire» (link sottolineato) | link, 44 dp | nessuna | fatto |
| Microfono «Ripeti il comando» | icona (MicView), anello 8 dp sul microfono pieno | pulsa solo mentre ascolta (momento 12) | già c'era |
| Pulsante di conferma («Invia», «Chiama»…) | pillola piena, anello 8 dp | nessuna | già c'era |
| «Annulla» | link | nessuna | fatto |
| VoiceHelpActivity: «‹ Home» | link (da `U.screen`) | nessuna | fatto |
| VoiceHelpActivity: elenco di esempi | non applicabile: solo testo | nessuna | non applicabile |

## Pausa (PauseActivity, FrictionActivity)

| Elemento | Famiglia pressed | Animazione | Stato |
|---|---|---|---|
| PauseActivity · «‹ Home» | link | nessuna | fatto |
| Livelli di ogni app (segmento) | linguette come chip: scelta = inchiostro pieno, non scelta = fondo INK 8% e anello 6 dp | nessuna | fatto (prima la non scelta era una riga senza anello) |
| «Anteprima pausa ›» / «Anteprima scopo ›» | link, attorno al solo testo | nessuna | fatto |
| Scheda «Dati di utilizzo ›» | pillola con bordo, riempimento e anello 8 dp | nessuna | fatto |
| FrictionActivity · «Non ora, torna alla home» | pillola piena chiara sulla pausa scura, anello ON_DARK 18% 8 dp; «Non ora» dello scopo: con bordo | il check si disegna al posto del testo (BASE), poi la pausa si schiarisce verso la home (LENTO, `Motion.softHome`) | già c'era |
| «Apri ‹app›» | piena o con bordo secondo lo stato, mai con il check | taglio rapido (`Motion.quickCut`) | già c'era |
| Chip dei minuti e dei motivi | chip scuro (anello 8 dp sulla pausa scura) | scatto di conferma | già c'era |
| «Oppure, come avevi scelto» (alternativa di notte) | pillola con bordo chiara, anello 8 dp | nessuna | fatto |
| «Una cosa da fare adesso» (card Adesso) | pillola / link / riga | nessuna | già c'era |
| Conto alla rovescia, anello | non applicabile: non interattivo | l'anello del conto | già c'era |
| Campo «Scrivilo…» | non applicabile: campo di testo | nessuna | non applicabile |

## Controllo (CheckActivity)

| Elemento | Famiglia pressed | Animazione | Stato |
|---|---|---|---|
| «‹ Home» | link | nessuna | fatto |
| Chip delle risposte | chip scelto = pieno, non scelto = con bordo, anello | nessuna | già c'era |
| «Vedi gli obiettivi ›» | scheda con bordo: riempimento e anello 8 dp | nessuna | fatto |
| «Fatto» | pillola piena, anello 8 dp | la label lascia il posto a un check che si disegna, poi l'azione (momento 8, `Tick`) | già c'era |
| «Non mostrarmeli più» | link, attorno al testo | nessuna | fatto |
| Finestra «Non mostrarmeli più?» | non applicabile: finestra di sistema | nessuna | non applicabile |
| Campo «Cosa cambieresti?» | non applicabile: campo di testo | nessuna | non applicabile |

## Rapporto della notte (NightReportActivity)

| Elemento | Famiglia pressed | Animazione | Stato |
|---|---|---|---|
| «‹ Home» | link | nessuna | fatto |
| «Tienilo» | pillola piena, anello 8 dp | check disegnato al posto della label (momento 8) | già c'era |
| «Cambia qualcosa», «Spegnilo» | pillola con bordo, anello 8 dp | nessuna | già c'era |
| Righe del rapporto | non applicabile: non interattive | entrano una alla volta (80 ms, BASE, 8 dp); il numero scende in 900 ms | già c'era |

## Tempo nelle app che rapiscono (SessionActivity)

| Elemento | Famiglia pressed | Animazione | Stato |
|---|---|---|---|
| «‹ Home» | link | nessuna | fatto |
| Riga «Attivo» + interruttore | riga senza bordo | interruttore `SwitchView`: il pallino scorre (BASE), la pista cambia (RAPIDO), anche dopo «Annulla» (momento 9) | fatto (prima era un interruttore disegnato a mano, senza movimento) |
| Chip dei minuti | chip scelto = pieno, non scelto = chip | nessuna | già c'era |
| «Pillola «App usata per…» di Benessere digitale ›» | riga senza bordo | transizione di sistema | fatto |

## Icone fisse nella barra (StickyActivity)

| Elemento | Famiglia pressed | Animazione | Stato |
|---|---|---|---|
| «‹ Home» | link | nessuna | fatto |
| «Lascia» e «Rendi silenziosa ›» (per ogni riga) | link, fondo e anello 6 dp | la riga si ridisegna senza movimento (il prototipo non lo definisce) | fatto |

## Elementi condivisi toccati

| Elemento | Dove | Cosa | Stato |
|---|---|---|---|
| «‹ Home» di `U.screen` | tutte le schermate secondarie | link, attorno al testo; ora largo quanto il testo + 16 dp (prima tutta la riga) | fatto |
| Chip «tutto il giorno» (`U.allDayChips`) | Home, Settimana | chip da premuto | fatto |
| Menu del tocco lungo (`Menu`) | Home, Settimana, Ricerca | riga senza bordo | fatto |
| `Press.Halo` | tutti gli aloni | rete di sicurezza contro l'alone appeso | fatto |
| `Press.Fade.minHold` | badge e tessere | fondo premuto e badge restano almeno 240 ms | fatto |
| `Press.iconOnDark`, `linkOnDark`, `rowOnDark`, `outlinedOnDark`, `Press.tile` | blocco, Ricerca | nuove varianti, la API esistente è invariata | fatto |
