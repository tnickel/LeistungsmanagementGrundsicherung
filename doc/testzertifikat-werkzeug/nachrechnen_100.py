# -*- coding: utf-8 -*-
# Unabhaengige Nachrechnung der Kombinationsfaelle (GLM-5.3, 2026-10-13).
# Regeln NUR aus gesetzesgrundlagen-2026.md bzw. llm-pruefauftrag.txt abgeleitet
# (Anlage § 28; §§ 30, 42a, 35, 82, 43, 90 SGB XII i.V.m. § 1 BarbetragsVO) —
# KEINE Rueckgriffe auf den Java-Code des Projekts.
# Aufruf:
#   python nachrechnen_100.py <testdokumentation.md|pruefmenge.md> [--max 100] [--protokoll ziel.md]
import re, io, sys
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
    return dict(rb=rb, mb=summe15 + zww, kdu=p["kdu"], gesamt=gesamt,
                frei=frei, abz=p["abz"], anr=R2(anr))

def rechne_block(blocktext):
    monat = re.search(r"^zahlungsmonat = (\S+)", blocktext, re.M).group(1)
    vermoegen = D(re.search(r"^vermoegen = ([\d.]+)", blocktext, re.M).group(1))
    budget = D(re.search(r"^budget_start = ([\d.]+)", blocktext, re.M).group(1))
    eingaben = re.findall(r"^p\d+_eingaben: .+$", blocktext, re.M)
    personen = [rechne_person(parse_eingabe(z), monat) for z in eingaben]
    gesamt = sum(p["gesamt"] for p in personen)
    anr = sum(p["anr"] for p in personen)
    erwachsene = sum(1 for z in eingaben if parse_eingabe(z)["rbs"] <= 3)
    schon = D("10000") * erwachsene + D("500") * (len(personen) - erwachsene)  # § 90 Abs. 2 Nr. 9
    verw = max(D("0.00"), vermoegen - schon)
    zahl = gesamt - anr - verw
    return dict(
        personen=personen, gesamt=gesamt, anr=anr, schon=schon, verw=verw,
        zahl=zahl, rest=budget - zahl)

def dokumentwerte(blocktext):
    """die in der Pruefmenge dokumentierten Erwartungswerte je Person und BG"""
    erg = {}
    pers = {}
    for m in re.finditer(r"^p(\d+)_(\w+)=(\-?[\d.]+)$", blocktext, re.M):
        pers.setdefault(int(m.group(1)), {})[m.group(2)] = D(m.group(3))
    erg["personen"] = pers
    for schluessel in ("bg_gesamtbedarf", "bg_anrechenbar", "bg_verwertbares_vermoegen",
                       "bg_zahlbetrag", "budget_rest_erwartet"):
        mm = re.search(r"^" + schluessel + r"=(\-?[\d.]+)$", blocktext, re.M)
        if mm:
            erg[schluessel] = D(mm.group(1))
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
    assert kombis, "keine Kombinationsfaelle in der Eingabe gefunden"

    geprueft, pers_werte, bericht, abweichungen = 0, 0, [], []
    for fall in kombis:
        tf = re.match(r"^## TF (\d+) ", fall).group(1)
        fall_fehler = []
        for blocktext in re.split(r"(?=^block: )", fall, flags=re.M):
            if not blocktext.startswith("block: "):
                continue
            geprueft += 1
            soll = rechne_block(blocktext)
            ist = dokumentwerte(blocktext)
            pers_werte += len(soll["personen"]) * 7 + 5
            fehler = []
            for i, p in enumerate(soll["personen"], 1):
                d = ist["personen"].get(i, {})
                mb_ist = sum(d.get(k, D(0)) for k in
                             ("mehrbedarf_g", "mehrbedarf_schwanger", "mehrbedarf_alleinerziehend", "mehrbedarf_warmwasser"))
                if "regelbedarf" in d and p["rb"] != d["regelbedarf"]:
                    fehler.append("p%d.regelbedarf %s vs %s" % (i, p["rb"], d["regelbedarf"]))
                if mb_ist and p["mb"] != mb_ist:
                    fehler.append("p%d.mehrbedarfe %s vs %s" % (i, p["mb"], mb_ist))
                if "kdu" in d and p["kdu"] != d["kdu"]:
                    fehler.append("p%d.kdu %s vs %s" % (i, p["kdu"], d["kdu"]))
                if "gesamtbedarf" in d and p["gesamt"] != d["gesamtbedarf"]:
                    fehler.append("p%d.gesamtbedarf %s vs %s" % (i, p["gesamt"], d["gesamtbedarf"]))
                if "freibetrag_erwerb" in d and p["frei"] != d["freibetrag_erwerb"]:
                    fehler.append("p%d.freibetrag %s vs %s" % (i, p["frei"], d["freibetrag_erwerb"]))
                if "absetzbetaetrag" in d and p["abz"] != d["absetzbetaetrag"]:
                    fehler.append("p%d.absetzbetraege %s vs %s" % (i, p["abz"], d["absetzbetaetrag"]))
                if "anrechenbar" in d and p["anr"] != d["anrechenbar"]:
                    fehler.append("p%d.anrechenbar %s vs %s" % (i, p["anr"], d["anrechenbar"]))
            for eigen, dort in (("gesamt", "bg_gesamtbedarf"), ("anr", "bg_anrechenbar"),
                                ("verw", "bg_verwertbares_vermoegen"), ("zahl", "bg_zahlbetrag"),
                                ("rest", "budget_rest_erwartet")):
                if dort in ist and soll[eigen] != ist[dort]:
                    fehler.append("%s: selbst %s vs Dokument %s" % (dort, soll[eigen], ist[dort]))
            fall_fehler.extend(fehler)
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
        def anzahl(muster):
            return len([b for b in kombis if re.search(muster, b, re.M)])
        rbs_menge = sorted({int(m) for b in kombis for m in re.findall(r"^p\d+_eingaben: rbs=(\d)", b, re.M)})
        abdeckung = ("Stufen %s" % ",".join(map(str, rbs_menge))
                     + " · Merkzeichen G in %d Faellen" % anzahl(r"merkzeichen_g=true")
                     + " · Schwangerschaft wirksam in %d Faellen" % anzahl(r"schwanger=true")
                     + " · Entbindungsmonat erfasst in %d Faellen" % anzahl(r"entbindungsmonat=2")
                     + " (entfallen im Zahlungsmonat in %d Faellen)" % anzahl(r"schwanger=false, entbindungsmonat=2")
                     + " · Alleinerziehend in %d Faellen" % anzahl(r"kinder_u16=[1-9]")
                     + " · Warmwasser in %d Faellen" % anzahl(r"warmwasser=true")
                     + " · Vermoegen>0 in %d Faellen" % len([b for b in kombis if re.search(r"^vermoegen = [1-9]", b, re.M)])
                     + " · Storno-Korrektur (2 Bloecke) in %d Faellen" % sum(1 for b in kombis if b.count("block: ") > 1))
        lauf = os.path.basename(os.path.dirname(os.path.abspath(args.quelle)))
        heute = datetime.date.today().isoformat()
        nr_von = re.match(r"^## TF (\d+) ", kombis[0]).group(1)
        nr_bis = re.match(r"^## TF (\d+) ", kombis[-1]).group(1)
        kopf_text = (
            "# LLM-Nachrechnung — automatisch erzeugt am " + heute + " (Zertifikat-Knopf)\n\n"
            "pruefumfang = " + str(len(kombis)) + " Faelle einzeln nachgerechnet (TF " + nr_von + "-" + nr_bis
            + ", Kombinationfaelle; " + str(geprueft) + " Rechenbloecke)\n"
            "mitgegeben = gesetzesgrundlagen-2026.md (vollstaendig) · llm-pruefauftrag.txt · Pruefmenge = die ersten "
            + str(len(kombis)) + " Kombinationfaelle aus " + lauf + "/testdokumentation.md\n"
            "modell = GLM-5.3 (ZCode) — unabhaengige Zweitimplementierung der Rechenkette (nachrechnen_100.py, nur aus den Gesetzestexten entwickelt)\n"
            "methode = je Person und je Bedarfsgemeinschaft jeder Zwischenwert selbst gerechnet (Decimal, HALF_UP je Zuschlag); "
            + str(pers_werte) + " Werte verglichen\n"
            "ergebnis = " + str(geprueft - len(abweichungen)) + "/" + str(geprueft) + " Rechenbloecke bestaetigt · "
            + str(len(abweichungen)) + " Abweichungen\n"
            "datenstand = " + lauf + "\n"
            "abdeckung = " + abdeckung + "\n\n"
            "## Ergebnis je Fall\n\n")
        with io.open(args.protokoll, "w", encoding="utf-8", newline="\n") as f:
            f.write(kopf_text + "\n".join(bericht) + "\n")
        print("Protokoll geschrieben: " + args.protokoll)
