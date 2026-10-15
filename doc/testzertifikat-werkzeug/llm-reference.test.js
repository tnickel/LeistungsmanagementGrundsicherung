// Vorab handgerechnete Fach-Gegenbeispiele des Reviews GR-02/03.
const test = require("node:test");
const assert = require("node:assert/strict");
const reference = require("./llm-reference");

function input({ u7 = 0, u16 = 0, minor = 0, decentralized = false, rent = "0", heating = "0" } = {}) {
  return `p1_eingaben: rbs=1, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=${u7}, kinder_u16=${u16}, kinder_minderjaehrig=${minor}, warmwasser=${decentralized}, kdu_miete=${rent}, kdu_betrieb=0, kdu_heizung=${heating}, rente=0, erwerb=0, abz_steuern=0, abz_sv=0, abz_vers=0, abz_erwerb=0\n`;
}
function requirement(values, extra = "", documented = "") {
  return reference.requirement("GOLD", "block: Gold\nzahlungsmonat = 2026-10\nvermoegen = 0.00\nschonvermoegen = 10000.00\nbudget_start = -\n" + extra + input(values) + documented);
}
function person(result) { return result.calculations[0].persons[0].values; }

for (const [u7, u16, minor, expected, payout] of [
  [1, 2, 4, "270.24", "833.24"],
  [0, 3, 5, "337.80", "900.80"],
  [1, 3, 6, "337.80", "900.80"]
]) {
  test(`Alleinerziehend ${minor} Kinder: günstigere Alternative trotz U7/U16`, () => {
    const result = person(requirement({ u7, u16, minor }));
    assert.equal(result.mehrbedarf_alleinerziehend, expected);
    assert.equal(result.zahlbetrag, payout);
  });
  test(`Alter falscher 36-Prozent-Sollwert bei ${minor} Kindern wird beanstandet`, () => {
    const result = requirement({ u7, u16, minor }, "", "p1_mehrbedarf_alleinerziehend=202.68\n");
    assert.ok(result.sourceFindings.some(finding => finding.includes(`p1.mehrbedarf_alleinerziehend: Referenz ${expected}`)));
  });
}

test("Zentrales Warmwasser: 563 + 400 + 100 = 1.063 EUR trotz Altparameter 20 Prozent", () => {
  const result = person(requirement({ rent: "400", heating: "100" }, "kdu_warmwasser_abzug_anteil = 0.20\nkdu_bg_grenze = 0.00\n"));
  assert.equal(result.kdu_warmwasser_abzug, "0.00");
  assert.equal(result.mehrbedarf_warmwasser, "0.00");
  assert.equal(result.kdu, "500.00");
  assert.equal(result.gesamtbedarf, "1063.00");
  assert.equal(result.zahlbetrag, "1063.00");
});
test("Die alten Warmwasser-Kürzungswerte 20/480/1043 EUR werden beanstandet", () => {
  const result = requirement({ rent: "400", heating: "100" }, "kdu_warmwasser_abzug_anteil = 0.20\nkdu_bg_grenze = 0.00\n",
    "p1_kdu_warmwasser_abzug=20.00\np1_kdu=480.00\np1_zahlbetrag=1043.00\n");
  assert.equal(result.sourceFindings.filter(finding => /p1\.(kdu_warmwasser_abzug|kdu|zahlbetrag):/.test(finding)).length, 3);
});
test("Altparameter bleibt validiert, obwohl er den Bedarf nicht mehr mindert", () => {
  const result = requirement({}, "kdu_warmwasser_abzug_anteil = 1.01\nkdu_bg_grenze = 0.00\n");
  assert.equal(result.calculations.length, 0);
  assert.ok(result.missingInputs.some(value => value.includes("KdU-Parameter außerhalb zulässiger Grenzen")));
});
