# *−minus*

**Il tuo smartphone. Ma *−minus*.**
Quasi un dumbphone, ma con WhatsApp, banca e SPID.

*−minus* è una schermata principale (launcher) per Android che toglie dal telefono tutto quello che non serve. Le app spariscono e si aprono scrivendone il nome, l'interfaccia è in bianco e nero, le notifiche aspettano tranne quelle delle persone che scegli, e prima di aprire un'app che distrae ti chiede per cosa la apri e per quanto. È radicale per scelta, ma reversibile: ogni scelta si annulla e la home di prima torna con un tocco.

> Il progetto è nato con il nome di lavoro *Essenziale*: nel codice lo trovi ancora così.

## Cosa fa

- **Solo l'essenziale a portata.** La giornata (impegni, cose da fare, sveglia) e le app di tutti i giorni. Il resto si apre dalla ricerca.
- **Raggiungibile da chi conta.** Le persone e i gruppi scelti passano sempre; le altre notifiche aspettano il riepilogo.
- **Un motivo prima di aprire.** Pausa, oppure pausa e scopo, sulle app che distraggono, anche aperte fuori da *−minus*.
- **Di notte.** Se ti svegli e prendi il telefono, solo l'essenziale e tempi più brevi.
- **Niente regole da aggirare.** Livelli per app, nessun «spegni per qualche giorno», ogni modifica si annulla.

*−minus* non raccoglie dati e non ha pubblicità. Tutto resta sul telefono.

## Installare

Scarica l'APK dall'ultima [release](https://github.com/minus-phone/minus/releases) e installalo. Serve Android 6 o successivo; è sviluppato e provato su Android 14.

## Da dove nasce

*−minus* nasce da una ricerca con 72 persone e dagli studi sull'uso del telefono: il problema non è il tempo in sé, ma il gesto automatico di prenderlo in mano senza un motivo. Il racconto completo, con le fonti, è sul [sito](https://minus-phone.github.io).

## Compilare

Il progetto si compila senza Gradle, con gli strumenti dell'SDK Android (piattaforma 23) e un JDK 11 o successivo:

```sh
./build.sh            # crea build/Essenziale.apk, firmato con una chiave di prova locale
test/logica.sh        # test della logica e controllo del contrasto dei colori
```

Le release ufficiali sono firmate con una chiave che non è nel repository.

## Contribuire

Le segnalazioni e le idee sono benvenute: leggi [CONTRIBUTING.md](CONTRIBUTING.md).

## Licenza

[GPL-3.0](LICENSE): puoi usare, studiare, modificare e ridistribuire *−minus*, purché le versioni modificate restino aperte con la stessa licenza.

---

# *−minus* (English)

**Your smartphone. But *−minus*.** Almost a dumbphone, but with WhatsApp.

*−minus* is an Android home screen that removes everything you don't need: apps disappear and open by typing their name, the interface is black and white, notifications wait except from the people you choose, and before opening a distracting app it asks what for and for how long. Radical by choice, reversible at any time. No data collection, no ads. Licensed under GPL-3.0. The interface is in Italian for now.
