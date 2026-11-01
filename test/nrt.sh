#!/usr/bin/env bash
# NRT di Essenziale: test automatici della logica + controlli statici su sorgenti, manifest e APK.
# Uso: test/nrt.sh [cartella_sorgenti_v0.2] [apk_v0.2]
set -uo pipefail
cd "$(dirname "$0")/.."
OLD_SRC=${1:-../essenziale-0.2-src}
OLD_APK=${2:-Essenziale-0.2.apk}
OUT=nrt-out; rm -rf "$OUT"; mkdir -p "$OUT/classes"
REPORT="$OUT/nrt-statico.md"; : > "$REPORT"
pass=0; fail=0
ok()   { pass=$((pass+1)); echo "PASS $1"; echo "- ✅ $1" >> "$REPORT"; }
ko()   { fail=$((fail+1)); echo "FAIL $1 → $2"; echo "- ❌ $1 — $2" >> "$REPORT"; }
check(){ if eval "$2" >/dev/null 2>&1; then ok "$1"; else ko "$1" "${3:-controllo fallito}"; fi; }

echo "## E. Build" >> "$REPORT"
check "L'APK si compila (javac → dex → aapt → firma)" "./build.sh"
check "Firma valida" "apksigner verify build/Essenziale.apk"
check "Stessa firma della v0.2 (si installa sopra senza disinstallare)" \
  "[ \"\$(apksigner verify --print-certs build/Essenziale.apk | grep SHA-256)\" = \"\$(apksigner verify --print-certs $OLD_APK | grep SHA-256)\" ]"
B=$(aapt dump badging build/Essenziale.apk 2>/dev/null)
check "Stesso pacchetto it.essenziale.launcher" "echo \"\$B\" | grep -q \"package: name='it.essenziale.launcher'\""
check "versionCode 26 > 25 (aggiornamento accettato da Android)" "echo \"\$B\" | grep -q \"versionCode='26'\""
check "minSdk 23 invariato" "echo \"\$B\" | grep -q \"sdkVersion:'23'\""

echo -e "\n## F. Manifest: nulla di v0.2 è sparito" >> "$REPORT"
M=AndroidManifest.xml; OM=$OLD_SRC/AndroidManifest.xml
for a in $(grep -o 'android:name="\.[A-Za-z]*"' "$OM" | cut -d'"' -f2); do
  check "Componente v0.2 ancora dichiarato: $a" "grep -q 'android:name=\"$a\"' $M"
done
for p in $(grep -o 'uses-permission android:name="[^"]*"' "$OM" | cut -d'"' -f2); do
  check "Permesso v0.2 ancora presente: ${p##*.}" "grep -q '$p' $M"
done
check "Essenziale resta una Home (intent HOME)" "grep -q 'android.intent.category.HOME' $M"
check "Il filtro notifiche resta un NotificationListenerService protetto" "grep -q 'BIND_NOTIFICATION_LISTENER_SERVICE' $M"
check "La schermata di blocco resta singleInstance" "grep -A3 '\.LockActivity' $M | grep -q singleInstance"
for f in src/it/essenziale/launcher/*Activity.java; do
  c=$(basename "$f" .java)
  [ "$c" = BaseActivity ] && continue
  check "Ogni schermata è dichiarata: $c" "grep -q 'android:name=\".$c\"' $M"
done
check "Permessi nuovi solo READ_CONTACTS (0.6), pillola e dati di utilizzo (0.9), Live Update di Android 16 (0.10)" "[ \$(grep -c uses-permission $M) -eq \$((\$(grep -c uses-permission $OM)+4)) ] && grep -q READ_CONTACTS $M && grep -q SYSTEM_ALERT_WINDOW $M && grep -q PACKAGE_USAGE_STATS $M"

echo -e "\n## G. Dati salvati della v0.2: stesse chiavi (nessuna perdita all'aggiornamento)" >> "$REPORT"
for k in '"contacts"' '"shop"' '"money"' '"waiting_apps"' '"myname"' '"silence"' '"lockscreen"' '"essenziale"'; do
  check "Chiave $k invariata in U.java" "grep -q 'String K_[A-Z_]* = $k\|getSharedPreferences($k' src/it/essenziale/launcher/U.java"
done
check "Task salvati sotto \"tasks\" come prima" "grep -q 'getString(\"tasks\"' src/it/essenziale/launcher/Tasks.java"
check "Conversazioni salvate sotto una chiave nuova, \"conversations\" (0.4)" "grep -q 'KEY = \"conversations\"' src/it/essenziale/launcher/Convs.java"
check "Notifiche trattenute sotto \"items\" come prima" "grep -q 'getString(\"items\"' src/it/essenziale/launcher/Store.java"
check "Ultimo calendario usato sotto \"last_cal\"" "grep -q '\"last_cal\"' src/it/essenziale/launcher/AddActivity.java"
check "Tasks.java legge ancora i task della v0.2 (0.13: in più l'app collegata, facoltativa)" "grep -q 'optString(\"title\")' src/it/essenziale/launcher/Tasks.java && grep -q 'optLong(\"due\")' src/it/essenziale/launcher/Tasks.java && grep -q 'optString(\"app\", \"\")' src/it/essenziale/launcher/Tasks.java"
check "Store.java identico alla v0.2" "diff -q $OLD_SRC/it/essenziale/launcher/Store.java src/it/essenziale/launcher/Store.java"
check "Notifiche: apertura e «visto» come nella v0.2" "grep -q 'Store.intent(it.id)' src/it/essenziale/launcher/InboxActivity.java && grep -q 'Store.remove(InboxActivity.this, it.id)' src/it/essenziale/launcher/InboxActivity.java && grep -q 'Segna tutto come visto' src/it/essenziale/launcher/InboxActivity.java"
check "Notifiche 0.6.1: tieni premuto per far passare sempre" "grep -q 'Pass.sheet' src/it/essenziale/launcher/InboxActivity.java"
check "Conversazioni 0.6.1: ⋯ visibile e «Togli dalla lista»" "grep -q 'Togli dalla lista' src/it/essenziale/launcher/ListActivity.java"
check "Filtro 0.6.1: notifica toccata apre la conversazione per 12 ore" "grep -q 'onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap, int reason)' src/it/essenziale/launcher/FilterService.java"
check "Conversazioni 0.6.2: chat Telegram e WhatsApp aperte dalla rubrica" "grep -q 'openChatFromContacts' src/it/essenziale/launcher/ListActivity.java"
check "Filtro 0.6.2: registro senza il testo dei messaggi" "grep -q 'mai il testo' src/it/essenziale/launcher/FilterService.java && ! grep -q 'note.text' <(sed -n '/static synchronized void log/,/^    }/p' src/it/essenziale/launcher/FilterService.java)"
check "Conversazioni 0.6.3: per ogni persona si scelgono le app (anche Telegram)" "grep -q 'chooseApps' src/it/essenziale/launcher/ListActivity.java && grep -q '\"TG\"' <(sed -n '/private void chooseApps/,/^    }/p' src/it/essenziale/launcher/ListActivity.java)"
check "Onboarding 0.6.3: le app già scelte per una persona restano" "grep -q 'le app scelte restano' src/it/essenziale/launcher/OnboardingActivity.java"
check "0.7: niente contenitori in home, la ricerca apre tutto" "! grep -q 'addContainer(grid' src/it/essenziale/launcher/MainActivity.java && grep -q 'SearchActivity.class' src/it/essenziale/launcher/MainActivity.java"
check "0.7: Shop, Movimenti e Persone e gruppi raggiungibili da Impostazioni" "grep -q 'MODE_SHOP' src/it/essenziale/launcher/SettingsActivity.java && grep -q 'MODE_MONEY' src/it/essenziale/launcher/SettingsActivity.java && grep -q 'peopleIntent' src/it/essenziale/launcher/SettingsActivity.java"
check "0.7: ricerca con risultati appoggiati in basso" "grep -q 'setGravity(Gravity.BOTTOM)' src/it/essenziale/launcher/SearchActivity.java"
check "0.7: le app con la pausa si aprono con la pausa anche dalla ricerca" "grep -q 'U.open(this, a)' src/it/essenziale/launcher/SearchActivity.java"
check "0.8: a portata in due blocchi, persone e gruppi insieme" "grep -q 'fixedPeople' src/it/essenziale/launcher/SearchActivity.java && grep -q 'groupAlways' <(sed -n '/static List<String\\[\\]> fixedPeople/,/^    }/p' src/it/essenziale/launcher/SearchActivity.java)"
check "0.8: righe che scorrono di lato" "grep -q 'HorizontalScrollView' src/it/essenziale/launcher/SearchActivity.java"
check "0.8: al massimo 3 conversazioni attive e «Altri ›» senza numero" "grep -q 'MAX_ACTIVE = 3' src/it/essenziale/launcher/SearchActivity.java && grep -q '\"Altri ›\"' src/it/essenziale/launcher/SearchActivity.java"
check "0.8: ordine di persone e gruppi annullabile" "grep -q 'K_ORDER}' src/it/essenziale/launcher/PortataActivity.java"
check "0.9: pillola spenta finché non la accendi e mai sopra Essenziale" "grep -q 'getBoolean(K_ON, false)' src/it/essenziale/launcher/Pill.java && grep -q 'Pill.resumed' src/it/essenziale/launcher/BaseActivity.java && grep -q 'Pill.resumed' src/it/essenziale/launcher/LockActivity.java"
check "0.9: un segnale alla volta (con la pillola niente avviso in tendina)" "grep -q 'if (Pill.active(this)) return' src/it/essenziale/launcher/FilterService.java"
check "0.9: icone fisse con «Rendi silenziosa» sulla categoria giusta" "grep -q 'CHANNEL_NOTIFICATION_SETTINGS' src/it/essenziale/launcher/Sticky.java && grep -q 'StickyActivity' AndroidManifest.xml"
check "0.9: onboarding con «Cosa ti interessa?» e percorso costruito sulle scelte" "grep -q 'Cosa ti interessa?' src/it/essenziale/launcher/OnboardingActivity.java && grep -q 'buildPath' src/it/essenziale/launcher/OnboardingActivity.java && ! grep -q 'Essere raggiungibile solo da chi conta' src/it/essenziale/launcher/OnboardingActivity.java"
check "0.9: niente contenitori nell'onboarding, al loro posto A portata" "! grep -q 'title(\"Contenitori\")' src/it/essenziale/launcher/OnboardingActivity.java && grep -q 'title(\"A portata\")' src/it/essenziale/launcher/OnboardingActivity.java"
check "0.9: chi aggiorna da prima della 0.6 non vede «Continua la configurazione»" "grep -q 'path.length() == 0) return false' src/it/essenziale/launcher/Onboarding.java"
check "0.10: pausa e scopo, «Non ora» con lo stesso peso di «Apri»" "grep -q 'Per cosa ti serve?' src/it/essenziale/launcher/FrictionActivity.java && grep -q 'new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));' src/it/essenziale/launcher/FrictionActivity.java"
check "0.10: livelli per app e niente «spegni per qualche giorno»" "grep -q 'Pausa e scopo' src/it/essenziale/launcher/PauseActivity.java && ! grep -qi 'per qualche giorno\\|sospendi' <(grep -v 'Non c' src/it/essenziale/launcher/PauseActivity.java)"
check "0.10: app aperte fuori da Essenziale fermate con i dati di utilizzo" "grep -q 'startGuard' src/it/essenziale/launcher/FilterService.java && grep -q 'outside' src/it/essenziale/launcher/Scope.java"
check "0.10: lo scopo finisce spegnendo lo schermo; «Chiudo» passa dalla home" "grep -q 'schermo spento' src/it/essenziale/launcher/FilterService.java && grep -q 'scope_done' src/it/essenziale/launcher/MainActivity.java"
check "0.10: Di notte non misura il sonno e la home resta a un tocco" "grep -q 'Non misura il sonno' src/it/essenziale/launcher/NightSettingsActivity.java && grep -q 'Apri la home' src/it/essenziale/launcher/NightActivity.java"
check "0.10: fine prova con Tienilo, Cambia qualcosa, Spegnilo" "grep -q 'Tienilo' src/it/essenziale/launcher/NightReportActivity.java && grep -q 'Spegnilo' src/it/essenziale/launcher/NightReportActivity.java"
check "0.10: Segnala un problema sempre in Impostazioni e nella ricerca" "grep -q 'BugActivity' src/it/essenziale/launcher/SettingsActivity.java && grep -q 'bug problema' src/it/essenziale/launcher/Cerca.java"
check "0.10: tema e testo dall'onboarding e da Aspetto" "grep -q 'ASPETTO' src/it/essenziale/launcher/OnboardingActivity.java && grep -q 'AspettoActivity' AndroidManifest.xml && grep -q 'applyTheme' src/it/essenziale/launcher/BaseActivity.java"
check "0.7: recenti senza app con la pausa" "grep -q 'paused.contains(r\\[1\\])' src/it/essenziale/launcher/SearchActivity.java"
check "Persone di esempio mai usate come vere" "grep -q 'Rules.stripSamples' src/it/essenziale/launcher/U.java"
check "Silenzio (Non disturbare) identico alla v0.2" \
  "diff <(sed -n '/static boolean applySilence/,/^    }/p' $OLD_SRC/it/essenziale/launcher/FilterService.java) <(sed -n '/static boolean applySilence/,/^    }/p' src/it/essenziale/launcher/FilterService.java)"

echo -e "\n## H. Percorsi della home v0.2 ancora presenti" >> "$REPORT"
H=src/it/essenziale/launcher/MainActivity.java
for t in 'AddActivity.class' 'SettingsActivity.class' 'WeekActivity.class' 'InboxActivity.class' 'SearchActivity.class' 'confirmDelete' 'Tasks.setDone'; do
  check "Home: $t" "grep -q '$t' $H"
done
L=src/it/essenziale/launcher/LockActivity.java
for t in 'toggleTorch' 'camera()' 'emergency()' 'unlockThen' 'FLAG_SHOW_WHEN_LOCKED' 'InboxActivity.class'; do
  check "Blocco: $t" "grep -q '$t' $L"
done
echo -e "\n## I. Novità della 0.6" >> "$REPORT"
S=src/it/essenziale/launcher
check "Home: «+ Aggiungi al calendario» in basso, niente + nell'intestazione" "grep -q '\"+ Aggiungi al calendario\"' $H && ! grep -q 'TextView plus' $H"
check "Home: Domani secondo le regole «quando serve»" "grep -q 'Quando.showTomorrow' $H && grep -q 'Quando.homeAlarm' $H"
check "Home: «+ Aggiungi» propone domani dopo le 19" "grep -q 'Quando.addDayOffset' $H"
check "Blocco: sveglia secondo le regole «quando serve»" "grep -q 'Quando.lockAlarm' $L"
check "Settimana: un + per ogni giorno con il giorno già scelto" "grep -q 'putExtra(\"day\", dayF)' $S/WeekActivity.java"
check "Impostazioni: cinque aree" "grep -q '\"Notifiche\", \"App\", \"Tempo e uso\", \"Home e giornata\", \"Attivazione\"' $S/SettingsActivity.java"
check "Impostazioni: Modifiche recenti" "grep -q 'ChangesActivity.class' $S/SettingsActivity.java"
for f in ListActivity GroupsActivity PauseActivity SessionActivity CalendarsActivity ContainerActivity SettingsActivity; do
  check "Annulla dopo le scelte: $f" "grep -q 'Changes.track' $S/$f.java"
done
check "Annulla: avviso di 6 secondi, ultime 20 modifiche per 30 giorni" "grep -q 'SHOW_MS = 6000' $S/Changes.java && grep -q 'MAX = 20' $S/Changes.java && grep -q '30L \* 24' $S/Changes.java"
check "Gruppi: niente campo nome né menzioni" "! grep -q 'K_MYNAME' $S/GroupsActivity.java && ! grep -q 'mention' $S/Rules.java"
check "Onboarding solo al primo avvio vero (chi aggiorna non lo rivede)" "grep -q 'Onboarding.firstRun' $H && grep -q 'K_CONTACTS, U.K_SHOP' $S/Onboarding.java"
check "Onboarding: guida alle impostazioni con restrizioni solo da Android 13" "grep -q 'SDK_INT >= 33' $S/OnboardingActivity.java"
check "Nessuna lambda nel bytecode (il dex senza Gradle non le supporta)" "! javap -c -p build/classes/it/essenziale/launcher/*.class | grep -q invokedynamic"

echo -e "\n## L. Novità della 0.11 e accessibilità" >> "$REPORT"
check "0.11: contrasto AA dei colori, tema chiaro e scuro" "python3 test/contrasto.py"
check "0.11: nessun colore fisso pensato solo per il tema chiaro" "! grep -n 'Color.WHITE\\|0xFF3C3C38' $S/*.java | grep -v 'U.java'"
check "0.11: A portata in una schermata con ricerca, anche nelle impostazioni" "grep -q 'Cerca tra tutte le app' $S/PortataPicker.java && grep -q 'PortataPicker' $S/OnboardingActivity.java && grep -q 'PortataPicker' $S/PortataActivity.java"
check "0.11: i social si possono scegliere, con nota e senza popup" "grep -q 'è un social' $S/PortataPicker.java && ! grep -q 'AlertDialog' $S/PortataPicker.java"
check "0.11: tempo dall'ultimo sblocco in home come pillola, area di tocco 48 dp" "grep -q 'Pill.homeText' $H && grep -q 'setMinimumHeight(U.dp(this, 48))' $H"

echo -e "\n## M. Novità della 0.12" >> "$REPORT"
check "0.12: nome visibile «minus», pacchetto invariato" "grep -q 'android:label=\"−minus\"' $M && grep -q 'package=\"it.essenziale.launcher\"' $M"
check "0.12: niente ⋯ in home, impostazioni nella ricerca e con pressione lunga" "! grep -q '\"⋯\", 22' $H && grep -q 'SettingsActivity.class' $S/SearchActivity.java && grep -q 'setOnLongClickListener(settings)' $H"
check "0.12: la ricerca si apre con la tastiera chiusa, salvo «Apri subito la tastiera»" "grep -q 'SearchActivity.*' $M && ! grep -A2 '\.SearchActivity' $M | grep -q stateVisible && grep -q 'U.K_KEYBOARD' $S/SearchActivity.java"
check "0.12: pausa di 8 secondi regolabile 4/6/8" "grep -q 'U.pauseSeconds' $S/FrictionActivity.java && grep -q 'pauseLength' $S/SettingsActivity.java"
check "0.12: una cosa adesso nella pausa e scopo, mai di notte" "grep -q 'Adesso.card' $S/FrictionActivity.java && grep -q 'if (!night)' $S/FrictionActivity.java"
check "0.12: sblocchi senza scopo spenti di default" "grep -q 'getBoolean(K_ON, false)' $S/Sblocco.java && grep -q 'Sblocco.unlocked' $S/FilterService.java"
check "0.13: niente schermo intero, barra di stato sempre" "! grep -q 'K_FULL\\|autoHideBars' $S/*.java && ! grep -q 'Schermo intero' $S/Cerca.java $S/AspettoActivity.java"
check "0.12: nessun «6 secondi» rimasto nei testi" "! grep -n '\"[^\"]*6 secondi' $S/*.java | grep -v Changes.java"

check "0.12.3: un recente (persona o gruppo) apre la conversazione, non riempie il campo" "grep -q 'for (Hit h : peopleHits(r\[1\]))' $S/SearchActivity.java"

check "0.12.4: Domani sulla schermata di blocco con la stessa regola della home" "grep -q 'Quando.showTomorrow(U.hourNow(), todayEmpty)' $L"
check "0.13: stile carta e inchiostro (Literata e Atkinson nell'APK, palette del sito)" "unzip -l build/Essenziale.apk | grep 'assets/fonts/literata-medium.ttf' >/dev/null && grep -q '#F3F1EB' $S/U.java && grep -q 'BrandSpan' $S/U.java"
check "0.13: logo animato al primo avvio" "grep -q 'new LogoView' $S/OnboardingActivity.java"
check "0.13: home con pillola in alto, Oggi / Settimana, Aggiungi al calendario in basso" "grep -q 'pillRow' $H && grep -q 'Aggiungi al calendario' $H && grep -q '\"Settimana\"' $H"
check "0.13: sveglia sulla riga del giorno, in home e nel blocco" "grep -q 'dayRow(this, \"Domani' $H && grep -q 'MainActivity.dayRow' $L"
check "0.13: Impostazioni come voce della ricerca" "grep -q 'row(\"Impostazioni\", null' $S/SearchActivity.java"
check "0.13: Servizi in Impostazioni e onboarding" "grep -q 'ServiziActivity' $S/SettingsActivity.java && grep -q 'ServiziActivity' $S/OnboardingActivity.java && grep -q '.ServiziActivity' AndroidManifest.xml"
check "0.13: icone delle app dalla barra di stato" "grep -q 'AppIcons.save' $S/FilterService.java && grep -q 'AppIcons.badge' $S/SearchActivity.java"
check "0.13: un task apre la sua app" "grep -q 't.app' $H && grep -q 'pickApp' $S/AddActivity.java"
check "0.13.1: sveglia di oggi sulla riga della data e nel blocco finché non suona" "grep -q 'Quando.todayAlarm' $H && grep -q 'Quando.todayAlarm' $L"
check "0.13: barometro in Cosa vede" "grep -q 'TYPE_PRESSURE' $S/SettingsActivity.java"

echo -e "\n## A–D. Test automatici della logica" >> "$REPORT"
javac -nowarn --release 8 -encoding UTF-8 -d "$OUT/classes" src/it/essenziale/launcher/{Rules,Rec,VoiceParser,Quando,Cerca,Tempo,Scopo,Notte,Cosa}.java test/it/essenziale/launcher/*.java 2>&1 | grep -v JAVA_TOOL
if java -Dfile.encoding=UTF-8 -cp "$OUT/classes" it.essenziale.launcher.NrtTest "$OUT/nrt-logica.md" 2>&1 | grep -v JAVA_TOOL > "$OUT/logica.log"; then
  ok "Test della logica: $(tail -1 $OUT/logica.log)"
else
  ko "Test della logica" "$(grep FAIL $OUT/logica.log | head -5)"
fi
echo
echo "Controlli statici e build: $pass superati, $fail falliti"
[ $fail -eq 0 ]
