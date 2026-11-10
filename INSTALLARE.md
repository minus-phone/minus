# Installare *−minus*

*−minus* non è ancora sul Play Store: si installa da un file APK. Servono circa 10 minuti, un telefono Android 6 o successivo, e il PIN del telefono a portata. È sviluppato e provato su Android 14.

Su Android 13 e successivi il passo 5 è quello in cui ci si ferma più spesso: Android protegge alcuni permessi delle app installate fuori dallo store, e vanno sbloccati a mano. È normale e si fa una volta sola.

> I nomi delle voci cambiano un po' da un produttore all'altro (Samsung, Xiaomi, Crosscall…). Se non trovi una voce, cerca la parola tra virgolette nella ricerca delle Impostazioni di Android.

## 1. Scarica l'APK

Dal telefono apri l'[ultima release](https://github.com/minus-phone/minus/releases/latest) e tocca il file che finisce con `.apk`. Se il browser avvisa che il file potrebbe essere dannoso, tocca «Scarica comunque»: è il messaggio standard per tutti gli APK.

## 2. Consenti l'installazione

Apri il file scaricato (dalla notifica del download o dall'app File). Android chiede di consentire l'installazione da quell'app: tocca «Impostazioni», accendi «Consenti da questa origine» e torna indietro.

Finita l'installazione puoi rispegnerlo da Impostazioni › App › Accesso speciale per le app › «Installa app sconosciute».

## 3. Installa

Tocca «Installa». Se compare Play Protect con «App non sicura» o «App sconosciuta», tocca «Altri dettagli» e poi «Installa comunque». Play Protect lo dice di ogni app che non conosce; il codice di *−minus* è [pubblico](https://github.com/minus-phone/minus) e l'app non ha il permesso di accedere a internet.

## 4. Apri *−minus* e segui la configurazione

La configurazione chiede un permesso alla volta, spiegando a cosa serve. Quello che salti finisce in Impostazioni › «Da completare» e si fa dopo.

## 5. Sblocca le impostazioni con restrizioni (Android 13 e successivi)

Quando *−minus* ti porta ad «Accesso alle notifiche» e l'interruttore è grigio, oppure compare «Impostazione con restrizioni»:

1. Prova comunque ad accendere l'interruttore: tocca *−minus* nell'elenco anche se è grigio e chiudi il messaggio con «OK». È indispensabile: finché non l'hai fatto almeno una volta dopo aver installato *−minus*, il menu del punto 3 non compare. Se reinstalli l'app, va rifatto.
2. Vai in Impostazioni di Android › App › *−minus* (a volte «Mostra tutte le app»).
3. Tocca il menu ⋮ in alto a destra e scegli «Consenti impostazioni con restrizioni». Su alcuni Samsung la voce è in fondo alla pagina, senza menu.
4. Conferma con PIN, impronta o sequenza.
5. Torna in *−minus*: la configurazione riprende da dove eri. Ora l'interruttore si accende.

**Il menu ⋮ non compare?** Ripeti il punto 1 e chiudi il messaggio con «OK» (non con Indietro), poi riapri la pagina dell'app al punto 2. Se ancora non c'è, riavvia il telefono e riprova.

**Ultima strada, con un computer.** Se il menu non compare proprio, si sblocca con il cavo USB e gli strumenti di Android (ADB), con il debug USB acceso nelle Opzioni sviluppatore:

```
adb shell appops set it.essenziale.launcher ACCESS_RESTRICTED_SETTINGS allow
```

Poi torna ad «Accesso alle notifiche»: l'interruttore si accende.

Su Android 15 la stessa protezione vale anche per «Dati di utilizzo» e «Mostra sopra altre app». Se uno di questi è grigio, il procedimento è lo stesso e basta farlo una volta.

## 6. Scegli *−minus* come home

Nella configurazione, «Scegli −minus come home» apre la scelta di Android: seleziona *−minus* e «Imposta come predefinita». Se l'hai saltato: Impostazioni di Android › App › App predefinite › «App Home».

## Aggiornare

Scarica la nuova versione dalla [release](https://github.com/minus-phone/minus/releases/latest) e installala sopra quella che hai: le tue scelte restano. Non serve disinstallare. Se Android dice «App non installata» o «Il pacchetto è in conflitto», la versione che hai è firmata in modo diverso: scrivici prima di disinstallare, perché disinstallando si perdono le impostazioni.

## Tornare alla home di prima

Impostazioni di Android › App › App predefinite › «App Home», e scegli quella di prima: icone e notifiche tornano come prima, e *−minus* resta installata. Per toglierla del tutto, disinstallala come qualsiasi app: si cancellano anche tutti i suoi dati. Prima, se avevi acceso il silenzio, controlla che «Non disturbare» sia spento.

## Problemi?

Leggi le [domande frequenti](FAQ.md). Se non trovi la risposta, usa «Segnala un problema» dentro l'app (in fondo alle Impostazioni), oppure apri una [segnalazione](https://github.com/minus-phone/minus/issues/new/choose).
