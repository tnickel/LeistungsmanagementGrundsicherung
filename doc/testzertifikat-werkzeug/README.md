Testzertifikat SDN.Leistung LMG — Werkzeugordner

Was liegt hier:
- generate.js            Generator (node generate.js [ausgabe.docx]) — erzeugt DOCX; der PDF-Export
                         läuft über LibreOffice (soffice), siehe erstelle-zertifikat.bat oder GUI-Knopf.
- erstelle-zertifikat.bat  Doppelklick-Erzeugung (DOCX + PDF + Kopie nach doc/).
- gesetzesgrundlagen-2026.md  Die Rechengrundlagen (Sätze + Normen + Fundstellen), Stand der
                         Verifikation 2026-10-10 — DAS gibt man einer prüfenden KI ZUSAMMEN mit
                         der testdokumentation.md.
- llm-pruefauftrag.txt   Fertiger Prüfauftrag (Prompt) für die KI: was sie womit wie prüfen und
                         in welcher Form sie antworten soll.
- llm-nachrechnung-<Datum>.md  Protokolle der durchgeführten Nachrechnungen; der Generator nimmt
                         automatisch das NEUESTE und nennt dessen Datum im Zertifikat.
- stand.json             Wird je Generatorlauf geschrieben: automatisch erhobene Laufzahlen,
                         LLM-Protokolldatum, Warnungen (z. B. „MD neuer als Nachrechnung“).

Zertifikats-Zyklus (Reihenfolge wichtig):
1. Läufe fahren: testsweet-GUI oder start.bat (POSITIV/NEGATIV, ggf. LAST/STRESS) — der
   Generator erfasst die neuesten Ergebnisse je Kategorie automatisch aus testsweet/logs/.
2. mvn test im Hauptprojekt — die Testzahl wird manuell im generate.js gepflegt (PRUEFSCHICHTEN[0]),
   da ein Testlauf beim Klick zu lange dauerte.
3. LLM-Nachrechnung: die neueste testdokumentation.md ZUSAMMEN mit gesetzesgrundlagen-2026.md
   und llm-pruefauftrag.txt einer KI geben; ihre Bestätigung/Rechnungen als
   llm-nachrechnung-<Datum>.md hier ablegen (Muster: llm-nachrechnung-2026-10-13.md).
4. Knopf „🏅 Testzertifikat erstellen“ (GUI) oder erstelle-zertifikat.bat.
   Warnungen im Zertifikat/Dialog bedeuten: Schritt 2 oder 3 ist älter als der Datenstand —
   erst erneuern, dann ausstellen.

**Automatische Nachrechnung beim Zertifikat-Klick (2026-10-13):** Der Knopf „🏅 Testzertifikat
erstellen“ prüft vor dem Erzeugen, ob der Lauf der neuesten Testdokumentation (mit
Kombinationsfällen) bereits nachgerechnet ist — verglichen wird der LAUFORDNER
(run-<Zeitstempel>), nicht das Datum, weil dieselbe Kalendertags-Basis täuschen kann
(gleicher Tag, anderer Seed = andere Fälle). Bei neuem Lauf läuft nachrechnen_100.py
automatisch über die ersten 100 Kombinationsfälle und schreibt ein Protokoll
llm-nachrechnung-<Datum>-auto-<HHMM>.md (Kopf: pruefumfang/mitgegeben/modell/methode/
ergebnis/datenstand/abdeckung); bei gleichem Lauf wird nichts erneut gerechnet. Der
Dialog meldet beides („ERNEUT AUSGEFÜHRT für Lauf …" / „aktuell — bereits nachgerechnet").
Hinweis zu den 500 Fällen: Mit festem Seed (Preset-Standard 42) ist der Topf exakt
reproduzierbar; mit Seed 0 zieht der Kombinationsgenerator jedes Mal neue Konstellationen —
ein neuer Lauf mit anderem Seed löst die Nachrechnung daher automatisch wieder aus.
