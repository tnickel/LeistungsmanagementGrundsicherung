# Authentifizierungs- und HTTP-Korrekturen vom 15.10.2026

## Umgesetzt

- **GR-01:** Die vorgelagerte Storno-Adminprüfung und der Handler verwenden dieselbe Routenstruktur. Die tatsächliche Stornooperation prüft die Adminrolle zusätzlich unmittelbar vor dem Serviceaufruf. Führendes Plus, kodiertes Plus, Unicode-Ziffern, führende Nullen und abschließende Slashes verändern die erforderliche Rolle nicht.
- **GR-07:** Fehlversuche für echte konfigurierte Konten liegen in einer eigenen Map. Deren Maximalgröße ergibt sich aus der festen Anzahl konfigurierter Konten. Die unbekannten Phantomkennungen behalten ihren Deckel von 1.024 Einträgen; deren Sättigung verhindert keine Sperre eines echten Kontos mehr. Der Anmeldemonitor verhindert konkurrierendes Überschreiten des Phantomdeckels.
- **GR-08:** Drucktoken enthalten die Herkunftssitzung und die Ziel-BG. Logout widerruft alle abgeleiteten Token genau dieser Sitzung. Verbrauch prüft die noch aktive Sitzung, ihren Idle-Ablauf und die 60-Sekunden-Drucktokenfrist. Die Fristgrenze selbst ist bereits abgelaufen. Ausgabe, Verbrauch, Sitzungsprüfung und Logout werden über denselben Monitor koordiniert. Eine neue Anmeldung desselben Benutzers reaktiviert alte Token nicht.

## Geänderter Druckvertrag

`POST /api/drucktoken` benötigt den Authorization-Header einer aktiven Sitzung und einen JSON-Body `{"bgId":123}`. Fehlendes Feld, falscher Typ, Bruchzahl oder eine nichtpositive ID ergeben 400. Die Antwort enthält weiterhin `drucktoken`.

Die Klassen-API lautet `erzeugeDruckToken(String sitzungsToken, long bgId)` und `verbraucheDruckToken(String token, long bgId)`. Die frühere Ausgabe aus einem frei übergebenen Benutzerobjekt wurde entfernt. Der Headerpfad für direkte Druckanfragen bleibt eine normale Sitzungsprüfung; Queryparameter akzeptieren ausschließlich Einmal-Drucktoken. Ein vorhandener ungültiger Bearer-Header erhält Vorrang vor einem gültigen Querytoken und wird abgewiesen.

Der UI-Aufruf übernimmt die BG-ID vor dem asynchronen Request in eine lokale Variable und verwendet dieselbe ID im Tokenauftrag und Drucklink. Ein Zielwechsel während des Requests kann die Bindung dadurch nicht verschieben.

## Regressionen

Die Haupttests `BenutzerverwaltungZeitTest` und `LmgServerHttpTest` prüfen Sperrmap-Sättigung, Speichergrenze, Erhaltung vorhandener Sperren, präzises Fristende, Sitzungs-/BG-Bindung, getrennte Sitzungen, Wiederanmeldung, Header-/Queryvertrag und Storno-ID-Varianten mit einer Admin-Positivkontrolle.

Der neue testsweet-Eigentest `de.allgemein.lmg.web.ProduktHttpSicherheitTest` enthält sieben Regressionen gegen den echten Produkt-HttpServer aus dem gebauten Hauptprojekt-JAR. Der Fachservice zählt Aufrufe und zeichnet die Testzahlung auf, enthält aber keinen eigenen Berechtigungsfilter. Verbotene Storno- und Druckanfragen müssen daher bereits an der produktiven Webgrenze enden. SQL und Browser werden dabei nicht simuliert; dieser gezielte HTTP-Test ist ausdrücklich kein Oracle-Persistenznachweis.

Die Vorbereitung wurde vor Quellenfreigabe ausschließlich in diesem Belegordner gespeichert. Die Übernahme erfolgte nach ausdrücklicher Freigabe des koordinierenden Agenten und vorheriger Originalhash-Prüfung. `auth-product.patch` dokumentiert den vorbereiteten Patch; die spätere lokale BG-ID-Variable im UI und der neue testsweet-Test gehören zum endgültigen Arbeitsbaum. Keine Maven-, Datenbank- oder API-Prüfläufe wurden durch diesen Teilagenten gestartet; ihre Ausführung und Bewertung erfolgt zentral.
