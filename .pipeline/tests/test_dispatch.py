"""Policy tests and subprocess rehearsal; no provider calls or merges."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts'))
SPEC = importlib.util.spec_from_file_location('dispatch', ROOT / 'scripts/pipeline_dispatch.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)
CONFIG = yaml.safe_load((ROOT / '.pipeline/agents.yaml').read_text())


class DispatchTest(unittest.TestCase):
    def test_every_implementer_has_opposite_reviewer_and_correct_judge(self):
        for tier, models in CONFIG['implement'].items():
            for model in models:
                with self.subTest(tier=tier, model=model['model']):
                    plan = MODULE.resolve(CONFIG, {'tier': tier}, model['model'])
                    self.assertNotEqual(plan['implementer']['vendor'], plan['reviewer']['vendor'])
                    self.assertEqual(plan['merge_judge'] is None, tier == 'low')
                    self.assertIn('gate_pass', plan['merge_requires'])
                    self.assertIn('cross_review_approved', plan['merge_requires'])

    def test_pickup_is_downward_only(self):
        for capacity in MODULE.TIERS:
            for tier in MODULE.TIERS:
                if MODULE.TIERS.index(tier) > MODULE.TIERS.index(capacity):
                    with self.assertRaises(ValueError):
                        MODULE.resolve(CONFIG, {'tier': tier}, capacity=capacity)
                else:
                    MODULE.resolve(CONFIG, {'tier': tier}, capacity=capacity)

    def test_two_failures_raise_effort_four_promote(self):
        plan = MODULE.resolve(CONFIG, {'tier': 'low'}, failures=2)
        self.assertEqual(plan['implementer']['effort'], 'medium')
        plan = MODULE.resolve(CONFIG, {'tier': 'low'}, failures=4)
        self.assertEqual(plan['tier'], 'medium')
        self.assertTrue(plan['promoted'])
        self.assertEqual(MODULE.resolve(CONFIG, {'tier': 'high'}, failures=6)['action'], 'escalate')

    def test_third_low_review_promotes_and_reassigns_judge(self):
        plan = MODULE.resolve(CONFIG, {'tier': 'low'}, rounds=3)
        self.assertEqual(plan['tier'], 'medium')
        self.assertIsNotNone(plan['merge_judge'])

    def test_invalid_model_offset_and_counts_rejected(self):
        for kwargs in ({'model': 'unknown'}, {'failures': -1}, {'rounds': -1}):
            with self.assertRaises(ValueError):
                MODULE.resolve(CONFIG, {'tier': 'low'}, **kwargs)
        for offset in (2, -2, 'high', True):
            with self.assertRaises(ValueError):
                MODULE.resolve(CONFIG, {'tier': 'low', 'effort_override': offset})
        plan = MODULE.resolve(CONFIG, {'tier': 'high'}, failures=4)
        self.assertEqual(plan['implementer']['effort'], 'max')

    def test_three_dummy_tickets_cli_and_execution_block(self):
        template = (ROOT / '.pipeline/tickets/T-template.md').read_text()
        with tempfile.TemporaryDirectory() as directory:
            for tier in MODULE.TIERS:
                path = Path(directory) / f'{tier}.md'
                path.write_text(template.replace('tier: high', f'tier: {tier}'))
                command = [str(ROOT / 'scripts/dispatch.sh'), str(path)]
                receipt = subprocess.run(command, capture_output=True, text=True, check=True)
                self.assertEqual(json.loads(receipt.stdout)['tier'], tier)
                blocked = subprocess.run(command + ['--execute'], capture_output=True, text=True)
                self.assertEqual(blocked.returncode, 2)

    def test_gate_propagates_gradle_success_and_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'scripts').mkdir()
            (root / '.pipeline/tests').mkdir(parents=True)
            gate = root / 'scripts/gate.sh'
            gate.write_text((ROOT / 'scripts/gate.sh').read_text())
            gradle = root / 'gradlew'
            for code in (0, 23):
                gradle.write_text(f'#!/bin/sh\nexit {code}\n')
                gradle.chmod(0o755)
                result = subprocess.run(['bash', str(gate)], capture_output=True)
                self.assertEqual(result.returncode, code)

    def test_missing_ticket_fields_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'bad.md'
            path.write_text('---\nid: T-001\ntier: low\n---\n')
            with self.assertRaises(ValueError):
                MODULE.load_ticket(path)


if __name__ == '__main__':
    unittest.main()
