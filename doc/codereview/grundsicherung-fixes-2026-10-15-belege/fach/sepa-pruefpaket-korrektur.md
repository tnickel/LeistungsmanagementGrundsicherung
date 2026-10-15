# SEPA-Rohwerte im Prüfmaterial

Fundquelle: unverändert erhaltener echter GLM-Lauf `f08a1759-76ee-4191-bcbc-5da986195f25`, 18/18 bearbeitet, 17 PASS und ein UNVERIFIED. Beim kuratierten Negativfall TF07 fehlten die tatsächlich geprüften IBAN-/BIC-Rohwerte; Prüfziffer und behauptete sieben Zeichen waren deshalb nicht unabhängig nachprüfbar. Der frühere Lauf wird nicht nachträglich als PASS ausgegeben.

Nach ausdrücklicher Quellenfreigabe umgesetzt:

- Kuratierter SEPA-Abwehrfall und generierte SEPA-Anlagefälle dokumentieren je Versuch beide synthetischen Eingabewerte im Namen der tatsächlich ausgeführten Aktion. Der Name wird im Schrittprotokoll und daraus in der übermittelten Markdown-Fallgruppe verwendet.
- Dieselben lokalen Variablen werden für Dokumenttext und unveränderliche `SzenarioAktion.SepaAnlage` verwendet. Keine zweite abweichende Werteliste.
- Die normale Aktionsschnittstelle und die Produkt-API sind unverändert. Der kleine SEPA-Aktionsträger verwendet für seinen konkreten Serviceaufruf einen gemeinsamen Delegaten auf das vorhandene `LeistungsService`-Interface.
- `SepaPruefpaketEingabenTest` enthält zwei Regressionen (kuratiert/generiert): Eine Serviceprobe erfasst die tatsächlichen Argumente derselben Anlageaktion, delegiert an die echte Fachservice-Validierung und verbietet jede DB-Ausleihe. Anschließend rendert der Test den Bericht und vergleicht alle dokumentierten IBAN-/BIC-Paare genau mit den erfassten Argumenten. Modulo 97 und BIC-Längen werden zusätzlich unabhängig kontrolliert.

Nur vorhandene synthetische Simulatorwerte werden ausgegeben; keine aus realen Beständen gelesenen Bankverbindungen und keine Passwörter. Die Angabe zur Position der IBAN-Prüfziffer im Szenarioerläuterungstext ist ebenfalls korrigiert (nach dem Ländercode).

Keine eigenen Maven-/DB-/API-Läufe gestartet. `git diff --check` für die betroffenen Quellen ohne Whitespacefehler. Ausführung der neuen Java-Regressionsprüfungen und neue quellengebundene Simulator-/GLM-Belege stehen beim zentral koordinierenden Agenten aus.
