# E016 · Mappatura B: pressed e movimento in Impostazioni, Settimana, Aggiungi, liste e primo avvio (0.21)

Dopo la prova sul telefono (tema scuro): in Impostazioni e Settimana non si vedeva né lo stato premuto né il movimento. Questa mappatura elenca ogni elemento interattivo delle schermate secondarie, la famiglia di stato premuto applicata e lo stato del lavoro. Specifica: `E016-revisione.md`. Colonna **stato**: *fatto* (0.21), *già c'era* (verificato, invariato), *non applicabile* (con il perché).

## Cosa non funzionava (e perché)

1. **Le righe senza bordo non si vedevano.** Nelle pagine che scorrono (tutte le impostazioni, la Settimana, le liste) Android segna «premuto» solo dopo 100 ms e, con un tocco veloce, lo toglie dopo 64 ms: il fondo della riga (che per specifica non ha alone) compariva e spariva in un lampo. Ora `PressB.Face` tiene la faccia premuta **almeno 240 ms** dal momento in cui compare, come già faceva l'alone esterno di `Press.Halo`.
2. **Nel tema scuro la riga premuta era invisibile.** `#2A2927` al 60% su `#121211` è quasi lo stesso colore del fondo. Ora la riga prende il colore pieno dei riquadri (CHIP) nello scuro; nel chiaro resta `#E7E4DB` al 60%.
3. **Mancavano intere famiglie** (link, `‹ Home`, ‹ ›, +, ✕, ↑, pillole con bordo, schede, caselle delle liste, menu del tocco lungo) e c'erano **tre interruttori diversi** (Calendari ne disegnava uno suo, senza movimento; Persone a portata, Registro beta, «Allega» e le righe di Aggiungi e dell'onboarding usavano `SwitchView` senza chiave né scatto).
4. **Bug in Aggiungi:** passando da «Impegno» a «Task» la scheda «Impegno» restava piena (testo e fondo dello stesso colore). Ora le due schede si rifanno a ogni scelta.

## Nuovi pezzi

| File | Cosa |
|---|---|
| `PressB.java` | `Face` (riga con tenuta di 240 ms), `row`, `rowOn`, `rowWithBadge`, `rowWithBox`/`boxHalo` (alone di 56 dp dietro la casella), `link`, `icon`, `chip`, `tabOff` (scheda non scelta su un vassoio), `pill` (pillola con bordo tenue), `outlined`, `card`, `slideThen` (interruttore dentro una finestra), `box`/`checkThen` (casella delle liste con il gesto di quella dei task). Le misure sono quelle di `Press`: 8 dp, 6 dp, 56 dp. Press.java e Motion.java non sono toccati. |
| `PageActivity.java` | Base delle schermate secondarie: avanti e indietro tra schermate di −minus è una **dissolvenza RAPIDO** (`Motion.quickCut`, 80 ms con «Rimuovi animazioni»). Verso app o impostazioni di Android resta la transizione di sistema. |
| `U.java` | `screen()`: «‹ Home» è un link (anello 6 dp solo attorno al testo; vale per ogni pagina che usa `U.screen`). `switchRow()`: riga premuta, scatto, interruttore con chiave. `allDayChipsPressed()`: chip «tutto il giorno» con stato premuto (la versione a 2 argomenti usata dalla home è invariata). |

Famiglie: **pillole piene / con bordo / schede con bordo** = anello 8 dp · **chip, link, `‹ Home`** = anello 6 dp · **icone, caselle** = alone circolare 56 dp (scala 0,8 → 1 per le icone) · **righe senza bordo** = solo fondo più scuro, nessun alone, 240 ms · **interruttori** = `SwitchView` (pallino BASE, pista RAPIDO, scatto, poi «acceso/spento · Annulla» quando la scelta passa da `Changes.track`).

## Impostazioni (`SettingsActivity`, tutte le aree)

| Elemento | Famiglia | Animazione | Stato |
|---|---|---|---|
| ‹ Home / ‹ nome area (U.screen) | link 6 dp | anello, testo a INK | fatto |
| Righe di navigazione (La tua home, obiettivi, Aspetto, Calendario, A portata, Comandi vocali, Servizi, Contatti e gruppi, Icone fisse, Altro, Da completare, Modifiche, Segnala, Registro, Rapporto…) | riga | fondo più scuro, ≥ 240 ms; nello scuro colore pieno CHIP | fatto (era `Press.row`: senza tenuta e quasi invisibile nello scuro) |
| Righe con azione («Concedi ›», «Fai ora ›», «Cosa vede −minus», Chiamate, Notifiche delle app, Scala di grigi ↗) | riga | idem | fatto |
| Interruttori (Impegni e task in blocco, Tastiera, Conversazioni aperte, Pillola, Sblocchi, Modalità sera, Mattino tranquillo, interruttore di ogni obiettivo) | riga + SwitchView | scatto, pallino che scorre (chiave `settings:…`), «acceso · Annulla» | fatto (stato premuto con tenuta; il resto già c'era) |
| «Accendi» (obiettivo spento) | pillola piena 8 dp | anello 8 dp | già c'era |
| «Rifai la configurazione guidata» | pillola con bordo tenue 8 dp | fondo +6% (scuro +8%) di inchiostro, anello | fatto (era un riquadro senza stato) |
| Durata della pausa 4/6/8 s | scheda scelta: chip pieno; non scelte: tabOff | scelta piena che si schiarisce; le altre si scuriscono, testo INK leggibile in chiaro e scuro | fatto |
| «Controlla aggiornamenti», «Informativa sulla privacy ↗» | link 6 dp | anello | fatto |
| Dialoghi di sistema (La tua alternativa, Cosa vede −minus, stelle) | — | — | non applicabile: finestre di Android, i loro pulsanti hanno il ripple di sistema |
| Passaggio tra pagine | — | dissolvenza RAPIDO | fatto (`PageActivity`) |

## Aspetto (`Aspetto`, `AspettoActivity`)

| Elemento | Famiglia | Animazione | Stato |
|---|---|---|---|
| Tema Chiaro/Scuro/Automatico, Testo Normale/Grande | scelto: chip pieno; non scelto: tabOff 6 dp | scheda piena che si schiarisce / non scelta che si scurisce, testo leggibile | fatto (prima la non scelta era una riga CHIP 60%) |
| Anteprima della home | — | — | non applicabile: disegno, non si preme |
| Cambio tema | — | l'«Annulla» e il ridisegno già c'erano | già c'era |

## Settimana (`WeekActivity`)

| Elemento | Famiglia | Animazione | Stato |
|---|---|---|---|
| ‹ Home | link 6 dp | anello | fatto |
| «Torna a questa settimana» | link 6 dp | anello | fatto |
| ‹ e › (settimana precedente/successiva) | icona 56 dp | alone da scala 0,8 a 1 | fatto |
| + (Senza scadenza) e + di ogni giorno | icona 56 dp | alone | fatto |
| Chip «tutto il giorno» | chip 6 dp | un po' più scuro, anello | fatto (`allDayChipsPressed`) |
| Riga impegno (tap, tocco lungo) | riga | fondo più scuro, ≥ 240 ms | fatto |
| Riga task (da fare e fatti) | riga | fondo più scuro, ≥ 240 ms | fatto |
| Casella del task | casella 56 dp + TaskBox | alone di 56 dp, riempimento, spunta con rimbalzo, vibrazione «conferma», 1 s di riposo, riga che si chiude (`TaskRows`) | già c'era (verificato: stessa `TaskBox`/`TaskRows` della home) |
| Task fatto: tocco sulla riga che lo riapre | riga | come in home (riapertura diretta) | non applicabile: la home fa lo stesso, nessun gesto in più |
| Menu del tocco lungo | vedi Menu | — | fatto |
| Gesto «scorri a destra» | — | dissolvenza di sistema | già c'era |

## Aggiungi / Modifica (`AddActivity`, `RepeatDialog`)

| Elemento | Famiglia | Animazione | Stato |
|---|---|---|---|
| Annulla (in alto) | link 6 dp | anello solo attorno al testo | fatto |
| Tab Impegno / Task | scelto chip pieno; l'altro tabOff 6 dp | come sopra | fatto (corretto il bug della scheda rimasta piena) |
| Microfono del titolo | icona tonda 8 dp | anello (`MicView`) | già c'era |
| Chip Oggi / Domani / Nessuna / data | chip 6 dp | scelto pieno, gli altri più scuri | già c'era |
| Orario da/a, Ripeti, Calendario (valori sottolineati) | link 6 dp | anello | fatto |
| Tutto il giorno, ! Urgente | riga + SwitchView | riga premuta, scatto, pallino che scorre (chiave) | fatto |
| ✕ della casella «Si apre con» | icona 56 dp | alone | fatto |
| Voci della tendina e «Tutte le app ›» | riga | fondo più scuro | fatto |
| Apri nel calendario › | link 6 dp | anello | fatto |
| Elimina impegno / Elimina task | pillola con bordo (rosso) 8 dp | riempimento al 10% del bordo, anello | fatto |
| Salva / Salva modifiche | pillola piena 8 dp | schiarisce, anello | già c'era |
| Campi di testo, note | — | — | non applicabile: campi di testo (focus di Android) |
| Interruttori: «acceso · Annulla» | — | — | non applicabile: è un modulo, il valore si salva con «Salva» (niente da annullare) |
| RepeatDialog: − e + | pulsante tondo con bordo 8 dp | fondo +6%, anello | fatto |
| RepeatDialog: unità, giorni | chip 6 dp | già c'era | già c'era |
| RepeatDialog: Mai / Il… / Dopo n volte | riga | fondo più scuro | fatto |
| Finestre Ripeti, data, ora, calendario, conferme | — | — | non applicabile: dialoghi di sistema |

## Liste e contatti (`ListActivity`, `PeopleActivity`, `GroupsActivity`)

| Elemento | Famiglia | Animazione | Stato |
|---|---|---|---|
| Schede Contatti / Gruppi | riga (con la linea della scelta) | fondo scuro, linea invariata | fatto |
| Righe contatto/gruppo (con iniziale) | riga + badge | fondo scuro e cerchio che si riempie d'inchiostro | fatto |
| Stato «passa sempre / fino alle… / in Altro» | link 6 dp | anello | fatto |
| «+ Aggiungi dalla rubrica», «Tutti i contatti…›», righe Shop e Movimenti | riga | fondo scuro | fatto |
| Persone: Annulla / ‹ Indietro | link 6 dp | anello solo sul testo | fatto |
| Persone: righe della rubrica | riga + badge + casella 56 dp | fondo, alone di 56 dp, la casella si riempie, spunta con rimbalzo, scatto; poi si ridisegna | fatto (la casella era un testo fisso) |
| Persone: chip delle app, Avanti/Fatto | chip 6 dp / pillola 8 dp | già c'era | già c'era |
| «Tocca qui per permettere…» | riga | fondo scuro | fatto |
| Gruppi: chip WhatsApp/Telegram, chip «Per te/Altro» | chip 6 dp | già c'era | già c'era |
| Gruppi: riga e «Fai aspettare/Fai passare» | riga; pillola con bordo 8 dp | fondo; fondo +6% e anello | fatto |
| Campi di ricerca | — | — | non applicabile |

## A portata, Calendari, Servizi (`PortataActivity`, `PortataPicker`, `CalendarsActivity`, `ServiziActivity`)

| Elemento | Famiglia | Animazione | Stato |
|---|---|---|---|
| ↑ (sposta su) | icona 56 dp | alone | fatto |
| Righe «App», proposte, risultati | riga + casella 56 dp | come Persone; riga non disponibile (già 8): non si preme | fatto |
| Nota del social con «pausa e scopo ›» | scheda 8 dp | più scura, anello | fatto |
| Conversazioni attive, Ricerche recenti | riga + SwitchView | scatto, pallino che scorre (chiave), «Annulla» | fatto (prima senza chiave né scatto) |
| «Si cambiano in Notifiche › Contatti e gruppi ›» | riga | fondo scuro | fatto |
| Calendari: interruttore di ogni calendario | riga + SwitchView | stesso di Impostazioni (prima un interruttore disegnato a parte, senza movimento) | fatto |
| Calendari: «I nuovi impegni vanno in» (○/●), permesso | riga | fondo scuro | fatto (il pallino ○/● cambia al ridisegno: non applicabile un movimento in più, è una scelta singola) |
| Servizi: righe app | riga + casella 56 dp | gesto della casella dei task | fatto |

## Notte, Sera, Container (`NightSettingsActivity`, `SeraActivity`, `ContainerActivity`)

| Elemento | Famiglia | Animazione | Stato |
|---|---|---|---|
| App frenate: ✕ | icona 56 dp | alone | fatto |
| «+ Aggiungi un'app» | riga | fondo scuro | fatto |
| Sera: Preavviso, Consiglio | riga + SwitchView | già con scatto e chiave; riga con tenuta 240 ms | fatto (stato premuto) |
| Sera: «Tutti i consigli ›» | riga | fondo scuro | fatto |
| Contenitori: schede Shop/Movimenti/Conversazioni | scelta chip pieno; altre tabOff | come sopra | fatto |
| Contenitori: righe app (casella), «Pausa» | riga + casella 56 dp; chip 6 dp | casella dei task; chip già c'era | fatto / già c'era |
| Riga «non installata» | riga | fondo scuro; casella grigia fissa | fatto (la casella è un'indicazione, resta statica) |
| «Anteprima della pausa ›» | riga | fondo scuro | fatto |

## Beta, Bug, Modifiche recenti (`BetaActivity`, `BugActivity`, `ChangesActivity`)

| Elemento | Famiglia | Animazione | Stato |
|---|---|---|---|
| Registro per la beta (interruttore) | riga + SwitchView | scatto, pallino che scorre; acceso passa da `Changes.track` con «Annulla» | fatto |
| Spegnere il registro | — | — | non applicabile: cancella i dati, quindi chiede conferma in un dialogo invece dell'«Annulla» |
| «Invia il registro» | pillola piena 8 dp | schiarisce, anello | fatto (era un chip) |
| «Cancella il registro» | pillola con bordo tenue 8 dp | fondo +6%, anello | fatto |
| Segnala: chip «Cosa succede?», «Invia» | chip 6 dp / pillola 8 dp | già c'era | già c'era |
| Segnala: «Allega Cosa vede −minus» | riga + SwitchView | scatto, pallino che scorre | fatto (nessun «Annulla»: è un modulo, non una preferenza salvata) |
| Modifiche: «Ripristina» | link 6 dp | anello | fatto |

## Primo avvio e «Rifai» (`OnboardingActivity`, `Onboarding`, `PortataPicker`)

| Elemento | Famiglia | Animazione | Stato |
|---|---|---|---|
| Pillola «Inizia / Avanti / Fatto» | pillola piena 8 dp | già c'era | già c'era |
| «‹ Indietro», «Salta» (in alto e in basso) | link 6 dp | anello solo sul testo | fatto |
| Link di testo («Per ora voglio solo…», «Non funziona?», «Aggiungi un'altra app»…) | link 6 dp | anello solo sul testo | fatto (prima erano a tutta larghezza) |
| Schede di scelta (Cosa vuoi cambiare, calendari, «I nuovi impegni vanno in») | scheda con bordo 8 dp | scheda più scura, anello; il segno ✓/● cambia al ridisegno | fatto (movimento del segno: non applicabile, indicatore dentro la scheda) |
| Righe con interruttore (calendari, blocco, pillola, preavviso, mattino) | riga + SwitchView | scatto, pallino che scorre (chiave `onb:…`) | fatto (nessun «Annulla»: percorso guidato, le scelte restano in Modifiche recenti dove già c'erano) |
| Righe con azione («Chi conta», «Servizi», «Cambia», «Scegli», «Fai ora») | riga | fondo scuro | fatto (le righe di sola informazione non si premono) |
| Chi conta: righe persone | riga + badge + casella 56 dp | come Persone | fatto |
| Chi conta: «+ nome» dei risultati | riga | fondo scuro | fatto |
| «Collega il calendario», «Scegli dalla rubrica» | pillola con bordo 8 dp | già c'era | già c'era |
| Chip (ore di sonno, 4/6/8 s, minuti, Pausa/Scopo) | chip 6 dp | già c'era | già c'era |
| Idee per l'alternativa | chip 6 dp | un po' più scuro, anello | fatto |
| «· cambiala» (ora di staccare) | link 6 dp | anello | fatto |
| Etichette «Ti avvisano / Per te / Altro» | — | — | non applicabile: sono titoli, non si premono |
| Logo, splash, barra dei passi | — | — | non applicabile: nessuna interazione (il logo animato è della 0.20) |
| `Onboarding.java` | — | — | non applicabile: logica, nessuna vista |

## Menu del tocco lungo (`Menu`: home, Settimana)

| Elemento | Famiglia | Animazione | Stato |
|---|---|---|---|
| Voci (Modifica, Sposta, Elimina in rosso) | riga | fondo scuro ≥ 240 ms | fatto |
| Voce «! Urgente» | riga + SwitchView | scatto, il pallino scorre, la finestra si chiude dopo ~320 ms (80 con «Rimuovi animazioni») e parte l'azione | fatto (prima la finestra si chiudeva subito e l'interruttore non si vedeva muovere) |
| «Annulla» del dialogo | — | — | non applicabile: pulsante di sistema |

## Da provare sul telefono

Righe premute nello scuro (visibili e con testo leggibile), tenuta di 240 ms con un tocco veloce, anello dei link «‹ Home» e dei valori sottolineati di Aggiungi, alone di 56 dp dietro le caselle a destra delle liste, dissolvenza tra pagine con e senza «Rimuovi animazioni», il menu del tocco lungo con l'interruttore.

## Fuori da questo giro

`SessionActivity`, `PauseActivity`, `StickyActivity`, `NightReportActivity`, `CheckActivity`, `InboxActivity`, `VoiceHelpActivity` non erano nell'elenco (sono dell'altro lavoro in parallelo): ricevono già il link «‹ Home» perché passa da `U.screen`, ma la loro transizione, i loro interruttori (`SessionActivity` ne disegna uno suo, come faceva Calendari) e le loro righe vanno mappati lì, usando `PressB`.
