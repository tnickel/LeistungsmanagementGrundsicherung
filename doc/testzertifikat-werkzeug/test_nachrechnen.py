"""Gegenproben zu Nullwerten, Pflichtfeldern und korrekter Blockzählung."""
import os
import pathlib
import re
import subprocess
import sys
import tempfile
import unittest
from decimal import Decimal as D
from nachrechnen_100 import rechne_block

HERE = pathlib.Path(__file__).parent


class DeterministicRecalculationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        text = (HERE / "pruefmenge-positiv-100faelle.md").read_text(encoding="utf-8")
        cls.sample = next(b for b in re.split(r"(?=^## TF )", text, flags=re.M) if b.startswith("## TF "))

    def run_sample(self, text):
        with tempfile.TemporaryDirectory(prefix="sweet-recalculation-test-") as directory:
            source = pathlib.Path(directory) / "input.md"
            report = pathlib.Path(directory) / "deterministic.md"
            source.write_text(text, encoding="utf-8")
            env = dict(os.environ, PYTHONIOENCODING="utf-8")
            process = subprocess.run([sys.executable, str(HERE / "nachrechnen_100.py"), str(source), "--max", "1", "--protokoll", str(report)],
                                     capture_output=True, text=True, encoding="utf-8", env=env, timeout=15)
            return process, report.read_text(encoding="utf-8") if report.exists() else ""

    def test_reference_and_honest_provenance(self):
        process, report = self.run_sample(self.sample)
        self.assertEqual(0, process.returncode, process.stderr + process.stdout)
        self.assertIn("kind = DETERMINISTIC_RECALCULATION", report)
        self.assertNotIn("GLM-5.3", report)
        self.assertNotIn("mitgegeben =", report)

    def test_zeroed_allowances_fail(self):
        changed = re.sub(r"(^p\d+_mehrbedarf_\w+=)[\d.]+", r"\g<1>0.00", self.sample, flags=re.M)
        process, _ = self.run_sample(changed)
        self.assertEqual(1, process.returncode)

    def test_wrong_person_payment_fails(self):
        changed = re.sub(r"(^p\d+_zahlbetrag=)[\d.]+", r"\g<1>999999.99", self.sample, flags=re.M)
        self.assertEqual(1, self.run_sample(changed)[0].returncode)

    def test_missing_numeric_field_fails(self):
        changed = re.sub(r"^p1_regelbedarf=.+\n", "", self.sample, flags=re.M)
        self.assertEqual(1, self.run_sample(changed)[0].returncode)

    def test_missing_block_fails(self):
        self.assertEqual(1, self.run_sample(self.sample.replace("block: ", "broken: "))[0].returncode)

    def test_two_bad_blocks_are_zero_of_two(self):
        changed = re.sub(r"(^bg_gesamtbedarf=)[\d.]+", r"\g<1>999999.99", self.sample, flags=re.M)
        process, report = self.run_sample(changed)
        self.assertEqual(1, process.returncode)
        self.assertIn("0/2 Rechenblöcke bestätigt", report)

    def test_new_budget_semantics_do_not_treat_workflow_as_subtraction(self):
        changed = self.sample.replace("budget_rest_erwartet=", "budget_rest_hypothetisch=")
        changed = changed.replace("budget_rest_hypothetisch=290.00", "budget_rest_hypothetisch=290.00\nbudget_rest_erwartet=1248.71")
        self.assertEqual(0, self.run_sample(changed)[0].returncode)

    def test_pure_minor_base_is_once_ten_thousand(self):
        block = "block: Minderjährige\nzahlungsmonat = 2026-10\nvermoegen = 10500.00\nbudget_start = -\n"
        block += self.input(1, 4) + self.input(2, 6)
        result = rechne_block(block)
        self.assertEqual(D("10000.00"), result["schon"])
        self.assertEqual(D("500.00"), result["verw"])
        self.assertEqual(D("1328.00"), result["zahl"])

    def test_legacy_central_parameter_has_no_effect_and_shared_limit_still_applies(self):
        block = "block: KdU\nzahlungsmonat = 2026-10\nvermoegen = 0.00\nbudget_start = -\nkdu_warmwasser_abzug_anteil = 0.075\nkdu_bg_grenze = 600.00\n"
        result = rechne_block(block + self.input(1, 2) + self.input(2, 2, True))
        self.assertEqual(D("0.00"), result["personen"][0]["kdu_warmwasser_abzug"])
        self.assertEqual(D("500.00"), result["personen"][0]["kdu"])
        self.assertEqual(D("0.00"), result["personen"][1]["kdu_warmwasser_abzug"])
        self.assertEqual(D("100.00"), result["personen"][1]["kdu"])
        self.assertEqual(D("1623.64"), result["zahl"])
        with self.assertRaises(ValueError):
            rechne_block(block.replace("kdu_bg_grenze = 600.00\n", "") + self.input(1, 2))

    def test_four_five_six_children_compare_both_alternatives(self):
        for u7, u16, minors, expected in [(1, 2, 4, "270.24"), (0, 3, 5, "337.80"), (1, 3, 6, "337.80")]:
            with self.subTest(children=(u7, u16, minors)):
                entry = self.input(1, 1).replace("kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0",
                        f"kinder_u7={u7}, kinder_u16={u16}, kinder_minderjaehrig={minors}")
                result = rechne_block("block: Gold\nzahlungsmonat = 2026-10\nvermoegen = 0.00\nbudget_start = -\n" + entry)
                self.assertEqual(D(expected), result["personen"][0]["mehrbedarf_alleinerziehend"])

    def test_central_twenty_percent_parameter_does_not_reduce_heating_need(self):
        result = rechne_block("block: Zentral\nzahlungsmonat = 2026-10\nvermoegen = 0.00\nbudget_start = -\nkdu_warmwasser_abzug_anteil = 0.20\nkdu_bg_grenze = 0.00\n" + self.input(1, 1))
        self.assertEqual(D("0.00"), result["personen"][0]["kdu_warmwasser_abzug"])
        self.assertEqual(D("1063.00"), result["zahl"])

    @staticmethod
    def input(person, rbs, ww=False):
        return (f"p{person}_eingaben: rbs={rbs}, merkzeichen_g=false, schwanger=false, entbindungsmonat=-, kinder_u7=0, kinder_u16=0, kinder_minderjaehrig=0, warmwasser={str(ww).lower()}, "
                "kdu_miete=350.00, kdu_betrieb=50.00, kdu_heizung=100.00, rente=0.00, erwerb=0.00, abz_steuern=0.00, abz_sv=0.00, abz_vers=0.00, abz_erwerb=0.00\n")


if __name__ == "__main__":
    unittest.main()
