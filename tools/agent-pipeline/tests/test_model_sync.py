"""Guards against model IDs drifting between agents.yaml and the spec document."""
from pathlib import Path
import re
import unittest

import yaml

DEFAULTS = Path(__file__).resolve().parents[1] / 'defaults'
MODEL_ID = re.compile(r'\b(?:claude-[a-z]+-\d+-\d+|gpt-\d+(?:\.\d+)?-[a-z]+)\b')


def configured_models(config):
    models = {config['roles'][role]['model'] for role in config['roles']}
    for candidates in config['implement'].values():
        models.update(item['model'] for item in candidates)
    for tier in config['review'].values():
        for implemented, reviewer in tier.items():
            models.update((implemented, reviewer['model']))
    models.update(judge['model'] for judge in config['merge_judge'].values() if judge)
    return models


class ModelSyncTest(unittest.TestCase):
    def setUp(self):
        self.config = yaml.safe_load((DEFAULTS / 'agents.yaml').read_text())
        self.spec = (DEFAULTS / 'agent-pipeline-spec.md').read_text()

    def test_every_configured_model_is_documented_in_spec(self):
        missing = configured_models(self.config) - set(MODEL_ID.findall(self.spec))
        self.assertEqual(missing, set(), 'Add these model IDs to the spec (section 4)')

    def test_spec_names_no_model_missing_from_config(self):
        stale = set(MODEL_ID.findall(self.spec)) - configured_models(self.config)
        self.assertEqual(stale, set(), 'Spec mentions models that agents.yaml no longer uses')
