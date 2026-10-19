#!/bin/bash
# GR-01-Gegenprobe (Review zc1, 15.10.2026): Admin-Pflicht beim Storno über die
# echte Kette HTTP -> LmgServer -> SozialLeistungsService -> Oracle.
# Eigene Probendaten mit Kennung ZC1-GR01-*; Bereinigung siehe gr01-cleanup.sql.
B=http://127.0.0.1:8098
PY=python
t() { $PY -c "import sys,json;print(json.load(sys.stdin)$1)"; }
echo "=== GR-01 Gegenprobe $(date -Iseconds) — echter Server 8098, echte Oracle-DB ==="
T1=$(curl -s -X POST -H "Content-Type: application/json" -d '{"kennung":"sb-01","passwort":"sb-01"}' $B/api/anmeldung | t "['token']")
T2=$(curl -s -X POST -H "Content-Type: application/json" -d '{"kennung":"sb-02","passwort":"sb-02"}' $B/api/anmeldung | t "['token']")
TA=$(curl -s -X POST -H "Content-Type: application/json" -d '{"kennung":"admin-01","passwort":"admin"}' $B/api/anmeldung | t "['token']")
echo "Anmeldungen: sb-01=$([ -n "$T1" ] && echo OK) sb-02=$([ -n "$T2" ] && echo OK) admin-01=$([ -n "$TA" ] && echo OK)"
AZ="ZC1-GR01-$(date +%H%M%S)"
BG=$(curl -s -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $T1" \
  -d "{\"aktenzeichen\":\"$AZ\",\"hauptpersonName\":\"Probe-ZC1\",\"startBudget\":2000,\"iban\":\"DE02120300000000202051\",\"bic\":\"BYLADEM1001\",\"kontoinhaber\":\"Probe ZC1\"}" \
  $B/api/bedarfsgemeinschaften)
BGID=$(echo "$BG" | t "['bedarfsgemeinschaft']['id']")
VNR=$(echo "$BG" | t "['bedarfsgemeinschaft'].get('vorgangsNr','')")
echo "BG angelegt: id=$BGID az=$AZ vorgangsNr=$VNR"
FALL=$(curl -s -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $T1" \
  -d '{"regelbedarfsstufe":1,"merkzeichenG":false,"schwanger":false,"anzKinderUnter7":0,"anzKinderUnter16":0,"anzKinderMinderjaehrig":0,"warmwasser":true,"kduMiete":"500.00","kduBetriebskosten":"0","kduHeizung":"0","einkommenRente":"0","einkommenErwerb":"0","abzSteuern":"0","abzSozialversicherung":"0","abzVersicherungen":"0","abzErwerbsaufwendungen":"0"}' \
  $B/api/bedarfsgemeinschaften/$BGID/sozialfaelle)
echo "Fall angelegt: $(echo "$FALL" | head -c 160)"
ZAHL=$(curl -s -X POST -H "Content-Type: application/json" -H "Authorization: Bearer $T1" \
  -d "{\"bgId\":$BGID,\"vorgangsNr\":$VNR,\"zahlungsmonat\":\"2026-10\"}" $B/api/auszahlung)
echo "Auszahlung: $(echo "$ZAHL" | head -c 260)"
ZID=$(echo "$ZAHL" | t "['zahlung']['id']")
echo "Zahlungs-ID=$ZID  Freigabe(sb-02)=HTTP $(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $T2" $B/api/zahlungen/$ZID/freigabe)"
echo "--- Storno als SACHBEARBEITER sb-01 (Soll: 403) ---"
for V in "$ZID" "+$ZID" "%2B$ZID" "000$ZID"; do
  echo "  ID [$V] -> HTTP $(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $T1" "$B/api/zahlungen/$V/storno")"
done
echo "--- Storno als ADMIN admin-01 (Soll: 200) ---"
echo "  kanonisch -> HTTP $(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $TA" "$B/api/zahlungen/$ZID/storno")"
echo "PROBE_BGID=$BGID"
