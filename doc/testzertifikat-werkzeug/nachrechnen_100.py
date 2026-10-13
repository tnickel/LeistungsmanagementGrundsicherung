# -*- coding: utf-8 -*-
# Deterministische Zweitimplementierung der Rechenkette, KEIN LLM-Aufruf.
# Regeln und Sätze sind fest programmiert (Referenzjahr 2026).
# Gesetzesdatei und Prüfauftrag werden zur Laufzeit NICHT gelesen/übergeben.
# Aufruf: python nachrechnen_100.py <bericht.md> [--max 100] [--protokoll ziel.md]
import re, io, sys, hashlib, pathlib
from decimal import Decimal as D, ROUND_HALF_UP

R2 = lambda x: x.quantize(D("0.01"), ROUND_HALF_UP)
RBS = {1: D("563.00"), 2: D("506.00"), 3: D("451.00"), 4: D("471.00"), 5: D("390.00"), 6: D("357.00")}
WW_PROZENT = {1: D("0.023"), 2: D("0.023"), 3: D("0"), 4: D("0.014"), 5: D("0.012"), 6: D("0.008")}

def parse_eingabe(zeile):
    feld = dict(kv.split("=", 1) for kv in zeile.split(": ", 1)[1].split(", "))
    return dict(
        rbs=int(feld["rbs"]), g=feld["merkzeichen_g"] == "true",
        schwanger=feld["schwanger"] == "true",
        entb=None if feld["entbindungsmonat"] == "-" else feld["entbindungsmonat"],
        u7=int(feld["kinder_u7"]), u16=int(feld["kinder_u16"]), mind=int(feld["kinder_minderjaehrig"]),
        ww=feld["warmwasser"] == "true",
        kdu=sum(D(feld[k]) for k in ("kdu_miete", "kdu_betrieb", "kdu_heizung")),
        rente=D(feld["rente"]), erwerb=D(feld["erwerb"]),
        abz=sum(D(feld[k]) for k in ("abz_steuern", "abz_sv", "abz_vers", "abz_erwerb")),
    )

def rechne_person(p, monat):
    rb = RBS[p["rbs"]]
    # § 30 Abs. 2: endet mit dem Folgemonat der Entbindung -> im Zahlungsmonat
    # NACH der Entbindung kein Zuschlag mehr
    schwanger_wirksam = p["schwanger"] and not (p["entb"] and monat > p["entb"])
    z1 = R2(rb * D("0.17")) if p["g"] else D("0.00")            # § 30 Abs. 1
    z2 = R2(rb * D("0.17")) if schwanger_wirksam else D("0.00") # § 30 Abs. 2
    minderj = max(p["u16"], p["mind"], p["u7"])                 # minderjaehrige Kinder
    if minderj > 0:                                             # § 30 Abs. 3 (Basis RBS 1!)
        anteil = D("0.36") if (p["u7"] >= 1 or 2 <= p["u16"] <= 3) else D("0.12") * minderj
        z3 = min(R2(RBS[1] * anteil), R2(RBS[1] * D("0.60")))
    else:
        z3 = D("0.00")
    summe15 = z1 + z2 + z3
    if summe15 > rb:                                            # § 30 Abs. 6 (Kappung)
        summe15 = rb
    zww = R2(rb * WW_PROZENT[p["rbs"]]) if p["ww"] else D("0.00")  # § 30 Abs. 7
    gesamt = rb + summe15 + zww + p["kdu"]
    frei = min(R2(p["erwerb"] * D("0.30")), R2(RBS[1] * D("0.50")))  # § 82 Abs. 3
    anr = max(D("0.00"), p["rente"] + p["erwerb"] - frei - p["abz"])  # § 82 Abs. 2
    return dict(regelbedarf=rb, mehrbedarf_g=z1, mehrbedarf_schwanger=z2,
                mehrbedarf_alleinerziehend=z3, mehrbedarf_warmwasser=zww,
                kdu=p["kdu"], gesamtbedarf=gesamt, freibetrag_erwerb=frei,
                absetzbetaetrag=p["abz"], anrechenbar=R2(anr), zahlbetrag=gesamt-R2(anr))

def rechne_block(blocktext):
    monat = re.search(r"^zahlungsmonat = (\S+)", blocktext, re.M).group(1)
    vermoegen = D(re.search(r"^vermoegen = ([\d.]+)", blocktext, re.M).group(1))
    budgetwert = re.search(r"^budget_start = ([^\r\n]+)", blocktext, re.M).group(1)
    budget = None if budgetwert.strip() == "-" else D(budgetwert)
    eingaben = re.findall(r"^p\d+_eingaben: .+$", blocktext, re.M)
    nummern = re.findall(r"^p(\d+)_eingaben: ", blocktext, re.M)
    if not eingaben or nummern != [str(i) for i in range(1, len(eingaben)+1)]:
        raise ValueError("Personeneingaben fehlen, sind doppelt oder nicht lückenlos")
    personen = [rechne_person(parse_eingabe(z), monat) for z in eingaben]
    gesamt = sum(p["gesamtbedarf"] for p in personen)
    anr = sum(p["anrechenbar"] for p in personen)
    erwachsene = sum(1 for z in eingaben if parse_eingabe(z)["rbs"] <= 3)
    schon = D("10000") * erwachsene + D("500") * (len(personen) - erwachsene)  # § 90 Abs. 2 Nr. 9
    verw = max(D("0.00"), vermoegen - schon)
    zahl = gesamt - anr - verw
    return dict(
        personen=personen, gesamt=gesamt, anr=anr, schon=schon, verw=verw,
        zahl=zahl, rest=None if budget is None else budget - zahl)

def dokumentwerte(blocktext):
    """die in der Pruefmenge dokumentierten Erwartungswerte je Person und BG"""
    erg = {}
    pers = {}
    for m in re.finditer(r"^p(\d+)_(\w+)=(\-?[\d.]+)$", blocktext, re.M):
        person = pers.setdefault(int(m.group(1)), {})
        if m.group(2) in person:
            raise ValueError("doppelter Personenwert: p%s_%s" % (m.group(1), m.group(2)))
        person[m.group(2)] = D(m.group(3))
    erg["personen"] = pers
    for schluessel in ("bg_gesamtbedarf", "bg_anrechenbar", "bg_verwertbares_vermoegen",
                       "bg_zahlbetrag", "budget_rest_erwartet", "budget_rest_hypothetisch"):
        values = re.findall(r"^" + schluessel + r"=(\-?[\d.]+)$", blocktext, re.M)
        if len(values) > 1:
            raise ValueError("doppelter BG-Wert: " + schluessel)
        if values:
            erg[schluessel] = D(values[0])
    return erg

# ------------------------------------------------------------------
# Hauptlauf: nur Kombinationsfaelle (max. --max), je Block jeder
# Zwischenwert nachrechnen und mit der Dokumentation vergleichen;
# optional --protokoll <pfad> fuer Zertifikat/GUI (maschinenlesbarer Kopf).
# ------------------------------------------------------------------
if __name__ == "__main__":
    import argparse, os, datetime
    ap = argparse.ArgumentParser(description="Unabhaengige Nachrechnung nach Gesetzestexten")
    ap.add_argument("quelle", nargs="?", default="pruefmenge-positiv-100faelle.md",
                    help="testdokumentation.md oder gekuerzte Pruefmenge")
    ap.add_argument("--max", type=int, default=100, help="maximale Anzahl Kombinationsfaelle")
    ap.add_argument("--protokoll", default=None, help="Zielpfad fuer das Nachrechnprotokoll")
    args = ap.parse_args()

    text = io.open(args.quelle, encoding="utf-8").read()
    alle = [b for b in re.split(r"(?=^## TF )", text, flags=re.M) if b.startswith("## TF ")]
    kombis = [b for b in alle if re.match(r"^## TF \d+ KOMBINATION ", b)][:args.max]
    if not 1 <= args.max <= 1000:
        ap.error("--max muss zwischen 1 und 1000 liegen")
    if not kombis:
        print("UNGEPRÜFT: keine Kombinationsfälle gefunden")
        sys.exit(1)

    geprueft, fehler_bloecke, pers_werte, bericht, abweichungen = 0, 0, 0, [], []
    for fall in kombis:
        tf = re.match(r"^## TF (\d+) ", fall).group(1)
        fall_fehler = []
        for blocktext in re.split(r"(?=^block: )", fall, flags=re.M):
            if not blocktext.startswith("block: "):
                continue
            geprueft += 1
            fehler = []
            try:
                soll = rechne_block(blocktext)
                ist = dokumentwerte(blocktext)
                for i, person in enumerate(soll["personen"], 1):
                    d = ist["personen"].get(i, {})
                    for key, expected in person.items():
                        if key not in d:
                            fehler.append("Pflichtfeld fehlt: p%d_%s" % (i, key))
                        elif not d[key].is_finite() or d[key] != expected:
                            fehler.append("p%d.%s: selbst %s vs Dokument %s" % (i, key, expected, d[key]))
                        pers_werte += 1
                for eigen, dort in (("gesamt", "bg_gesamtbedarf"), ("anr", "bg_anrechenbar"),
                                    ("verw", "bg_verwertbares_vermoegen"), ("zahl", "bg_zahlbetrag")):
                    if dort not in ist:
                        fehler.append("Pflichtfeld fehlt: " + dort)
                    elif not ist[dort].is_finite() or soll[eigen] != ist[dort]:
                        fehler.append("%s: selbst %s vs Dokument %s" % (dort, soll[eigen], ist[dort]))
                    pers_werte += 1
                schutz = re.search(r"^schonvermoegen = ([^\r\n]+)", blocktext, re.M)
                if not schutz or D(schutz.group(1)) != soll["schon"]:
                    fehler.append("Schonvermögen fehlt oder weicht ab")
                if soll["rest"] is not None:
                    key = "budget_rest_hypothetisch" if "budget_rest_hypothetisch" in ist else "budget_rest_erwartet"
                    if key not in ist or ist[key] != soll["rest"]:
                        fehler.append("hypothetisches Restbudget fehlt oder weicht ab")
                    # Neues budget_rest_erwartet beschreibt den tatsächlichen Workflow,
                    # keine bloße Anspruchssubtraktion; LLM prüft es mit SQL/Ablauf.
                    pers_werte += 1
            except (ValueError, KeyError, AttributeError, ArithmeticError) as error:
                fehler.append("Unvollständiger/ungültiger Rechenblock: " + str(error))
            if fehler:
                fehler_bloecke += 1
            fall_fehler.extend(fehler)
        if not re.search(r"^block: ", fall, re.M):
            fall_fehler.append("Pflicht-Rechenblock fehlt")
        if fall_fehler:
            abweichungen.append(tf)
            bericht.append("TF %s: ABWEICHUNG — %s" % (tf, "; ".join(fall_fehler)))
        else:
            bericht.append("TF %s: OK" % tf)

    print("Gepruefte Rechenbloecke: %d in %d Faellen" % (geprueft, len(kombis)))
    print("Abweichungen: %d" % len(abweichungen))
    for zeile in bericht:
        if "ABWEICHUNG" in zeile:
            print("  " + zeile)

    if args.protokoll:
        quelle = pathlib.Path(args.quelle)
        text = ("# Deterministische Nachrechnung (kein LLM)\n\n"
                "kind = DETERMINISTIC_RECALCULATION\n"
                "modell = keines; lokal ausgeführtes Python-Skript\n"
                "regeln = fest programmierte Referenz 2026; keine Laufzeit-Auswertung von Gesetzesdatei oder Prompt\n"
                "quelle = " + str(quelle.resolve()) + "\n"
                "quelle_sha256 = " + hashlib.sha256(quelle.read_bytes()).hexdigest() + "\n"
                "skript_sha256 = " + hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest() + "\n"
                "pruefumfang = %d Fälle; %d Rechenblöcke; %d Werte verglichen\n" % (len(kombis), geprueft, pers_werte)
                + "ergebnis = %d/%d Rechenblöcke bestätigt; %d abweichende/ungeprüfte Fälle\n" % (geprueft-fehler_bloecke, geprueft, len(abweichungen))
                + "grenzen = nur Arithmetik 2026; keine Prüfung von SQL-Endzustand, Workflow, Ablehnungsgründen oder aktuellem Recht\n\n"
                + "\n".join(bericht) + "\n")
        pathlib.Path(args.protokoll).write_text(text, encoding="utf-8")
        print("Protokoll geschrieben: " + args.protokoll)
    sys.exit(1 if abweichungen else 0)
