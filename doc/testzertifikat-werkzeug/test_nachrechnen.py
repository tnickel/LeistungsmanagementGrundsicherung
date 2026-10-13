"""Gegenproben zu Nullwerten, Pflichtfeldern und korrekter Blockzählung."""
import os
import pathlib
import re
import subprocess
import sys
import tempfile
import unittest

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


if __name__ == "__main__":
    unittest.main()
