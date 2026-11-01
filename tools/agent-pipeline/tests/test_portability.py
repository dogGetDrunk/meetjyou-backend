"""Integration coverage for unrelated repositories and an independent installation."""
import importlib.util
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import yaml

ENGINE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ENGINE / 'scripts'))
from pipeline_cli import initialize
from pipeline_config import load_policy, project_settings, role_text, verify_policy
from pipeline_gate import run_project_gate
from pipeline_runtime import Orca
from pipeline_execution import state_dir
from pipeline_tasks import approved_intent, new_task

SPEC = importlib.util.spec_from_file_location('installer', ENGINE / 'install.py')
INSTALLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(INSTALLER)


class PortableTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name)

    def repo(self, name):
        root = self.base / name
        root.mkdir()
        subprocess.run(['git', 'init', '-q', str(root)], check=True)
        return root

    def init(self, root, code=0):
        command = [sys.executable, '-c', f'import sys; print("portable gate"); sys.exit({code})']
        return initialize(root, [command], 'upstream/trunk', 'squash')

    def test_two_projects_share_policy_but_not_intent_or_claims(self):
        first, second = self.repo('python-service'), self.repo('web-client')
        for root in (first, second):
            self.assertFalse(self.init(root)['intent_approved'])
            self.assertFalse((root / '.pipeline/agents.yaml').exists())
            self.assertFalse((root / '.pipeline/prompts').exists())
            self.assertEqual(list((root / '.pipeline/tasks').glob('*/approval.json')), [])
            new_task(root, '20261102-shared-key', 'same key in two repos', 'M')
            with self.assertRaises(ValueError):
                approved_intent(root, '20261102-shared-key')
        (state_dir(first) / 'claims.json').write_text('{"T-001": {"state": "unknown"}}')
        self.assertFalse((state_dir(second) / 'claims.json').exists())
        verify_policy()
        self.assertEqual(load_policy()['limits']['concurrent_implementers'], 2)

    def test_reinitialization_preserves_existing_state(self):
        root = self.repo('existing')
        self.init(root)
        new_task(root, '20261102-existing', 'existing', 'M')
        intent = root / '.pipeline/tasks/20261102-existing/intent.md'
        intent.write_text('Existing approved intent')
        with self.assertRaises(ValueError):
            self.init(root)
        self.assertEqual(intent.read_text(), 'Existing approved intent')

    def test_invalid_gate_does_not_write_project_files(self):
        root = self.repo('invalid')
        for gate in ([], 'npm test', [[]], [['echo', None]]):
            with self.assertRaises(ValueError):
                initialize(root, gate, 'upstream/trunk', 'squash')
            self.assertFalse((root / '.pipeline').exists())

    def test_worker_gate_uses_coordinator_settings_and_worker_cwd(self):
        root, worker = self.repo('coordinator'), self.repo('worker')
        command = [sys.executable, '-c', 'from pathlib import Path; print(Path.cwd().name)']
        initialize(root, [command], 'upstream/trunk', 'squash')
        code, output = run_project_gate(root, worker)
        self.assertEqual(code, 0)
        self.assertTrue(output.rstrip().endswith('worker'))
        self.init(worker, 91)
        self.assertEqual(run_project_gate(root, worker)[0], 0)

    def test_failed_gate_stops_remaining_commands(self):
        root = self.repo('failure')
        self.init(root, 23)
        path = root / '.pipeline/project.yaml'
        config = yaml.safe_load(path.read_text())
        config['gate'].append([sys.executable, '-c', 'raise RuntimeError("must not execute")'])
        path.write_text(yaml.safe_dump(config))
        code, output = run_project_gate(root)
        self.assertEqual(code, 23)
        self.assertNotIn('must not execute', output)

    def test_remote_default_branch_and_project_rules_reach_workers(self):
        root = self.repo('custom')
        self.init(root)
        (root / 'AGENTS.md').write_text('Custom project rules')
        text = role_text(root, 'implementer')
        self.assertIn('upstream/trunk', text)
        self.assertIn('AGENTS.md', text)
        self.assertIn('Custom project rules', text)
        runtime = Orca(root)
        plan = {'implementer': {'vendor': 'openai', 'model': 'model', 'effort': 'low'}}
        with patch.object(runtime, 'call', return_value={}) as call:
            runtime.start(text, plan, 'run', 'repo')
        argv = call.call_args.args
        self.assertEqual(argv[argv.index('--base-branch') + 1], 'upstream/trunk')

    def test_standalone_release_runs_after_copy_without_meetjyou_source(self):
        release = self.base / 'independent-install'
        shutil.copytree(ENGINE, release, ignore=shutil.ignore_patterns('__pycache__'))
        root = self.repo('unrelated')
        self.init(root)
        nested = root / 'nested'
        nested.mkdir()
        launcher = self.base / 'local'
        INSTALLER.publish_launcher(launcher, release, Path(sys.executable))
        binary = launcher / 'bin/agent-pipeline'
        for command in ('policy', 'gate'):
            result = subprocess.run([str(binary), command], cwd=nested,
                                    capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn(str(ENGINE), binary.read_text())

    def test_global_instructions_preserve_text_and_are_idempotent(self):
        path = self.base / 'AGENTS.md'
        original = '# Custom rules\n\n- Keep this rule\n'
        path.write_text(original)
        INSTALLER.update_instructions(path)
        once = path.read_text()
        INSTALLER.update_instructions(path)
        self.assertEqual(path.read_text(), once)
        self.assertTrue(once.startswith(original.rstrip()))
        self.assertEqual(once.count(INSTALLER.BEGIN), 1)

    def test_task_command_installs_for_both_agents_and_spares_user_files(self):
        home = self.base / 'home'
        user_owned = home / '.codex/prompts/task.md'
        user_owned.parent.mkdir(parents=True)
        user_owned.write_text('my own prompt')
        first = INSTALLER.install_commands(home)
        self.assertEqual(first, [home / '.claude/commands/task.md'])
        self.assertEqual(user_owned.read_text(), 'my own prompt')
        managed = (home / '.claude/commands/task.md').read_text()
        self.assertIn(INSTALLER.COMMAND_MARKER, managed)
        self.assertEqual(INSTALLER.install_commands(home), first)
        self.assertEqual((home / '.claude/commands/task.md').read_text(), managed)

    def test_policy_change_cannot_use_original_approval(self):
        defaults = self.base / 'policy'
        shutil.copytree(ENGINE / 'defaults', defaults)
        (defaults / 'agents.yaml').write_text('limits: {}')
        with patch('pipeline_config.DEFAULTS', defaults):
            with self.assertRaises(ValueError):
                verify_policy()

    def test_missing_project_never_falls_back_to_gradle(self):
        root = self.repo('uninitialized')
        with self.assertRaises(ValueError):
            project_settings(root)
        with self.assertRaises(ValueError):
            run_project_gate(root)
