# Contribuire a *−minus*

Grazie di voler aiutare. *−minus* è un progetto piccolo: ogni segnalazione viene letta.

## Segnalare un problema o proporre un'idea

- **Qualcosa non funziona:** apri una [segnalazione](https://github.com/minus-phone/minus/issues/new/choose) con il modulo «Qualcosa non funziona».
- **Un'idea:** usa il modulo «Un'idea». Racconta prima il problema, poi la soluzione.
- **Non hai un account GitHub?** Usa «Segnala un problema» dentro l'app (Impostazioni).

Le segnalazioni sono pubbliche: non scrivere nomi, numeri o messaggi di altre persone.

## Il principio che guida le scelte

*−minus* toglie più che aggiungere. Una proposta viene valutata con tre domande:

1. Aiuta a prendere il telefono per un motivo, invece che per abitudine?
2. Tiene a portata ciò che serve davvero (persone, giornata, app indispensabili)?
3. Si può annullare, e non fa sentire in colpa chi torna indietro?

Il linguaggio dell'app non parla di dipendenza, detox o fallimenti, e non usa punteggi, serie o badge.

## Contribuire al codice

1. Apri prima una segnalazione per parlarne: evita lavoro che poi non entra.
2. Il codice è Java 8 senza lambda, compilato contro Android 23, senza Gradle: vedi `build.sh`.
3. La logica che non dipende da Android sta in classi pure (`Rules`, `Tempo`, `Scopo`, `Notte`, `Cerca`…) ed è coperta da `test/logica.sh`: aggiungi i test lì.
4. Accessibilità: aree di tocco di almeno 44 dp, testi con contrasto almeno 4,5:1 in tema chiaro e scuro, descrizioni per i pulsanti con soli simboli.
5. Proponi le modifiche con una pull request che spiega cosa cambia per chi usa l'app.

Contribuendo accetti che il tuo codice sia distribuito con la licenza [GPL-3.0](LICENSE).

## Comportamento

Rispetto, gentilezza, nessun giudizio su come le persone usano il telefono. Vale qui come nell'app.
