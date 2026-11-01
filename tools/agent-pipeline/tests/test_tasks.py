"""Per-task folders against real git repositories with a local bare remote."""
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ENGINE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ENGINE / 'scripts'))
from pipeline_cli import initialize  # noqa: E402
from pipeline_dispatch import load_ticket  # noqa: E402
from pipeline_tasks import (TEMPLATE_MARKER, approve_intent, approved_intent, find_ticket,  # noqa: E402
                            new_task, task_base_ref, task_key_of, ticket_intent)

KEY = '20261102-party-sync'
TICKET = f'''---
id: T-{KEY}-01
title: sync
tier: low
effort_override: null
depends_on: []
requirements: [REQ-1]
acceptance_tests: [AT-1]
touches: [src/a.kt]
consumes: []
produces: [x]
status: ready
---
'''


def git(cwd, *args):
    return subprocess.run(['git', *args], cwd=cwd, check=True, capture_output=True, text=True).stdout


class TaskFolderTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        base = Path(temporary.name)
        self.remote, self.root = base / 'remote.git', base / 'repo'
        git(base, 'init', '-q', '--bare', '-b', 'main', str(self.remote))
        git(base, 'init', '-q', '-b', 'main', str(self.root))
        git(self.root, 'config', 'user.email', 't@example.com')
        git(self.root, 'config', 'user.name', 't')
        git(self.root, 'remote', 'add', 'origin', str(self.remote))
        initialize(self.root, [['true']], 'origin/main', 'merge')
        self.publish('init')

    def publish(self, message, branch='main'):
        git(self.root, 'add', '-A')
        git(self.root, 'commit', '-q', '-m', message)
        git(self.root, 'push', '-q', 'origin', f'HEAD:{branch}')
        git(self.root, 'fetch', '-q', 'origin')

    def write_intent(self, key=KEY, size='M'):
        new_task(self.root, key, 'party sync', size, {'issue': 'o/r#223'})
        intent = self.root / f'.pipeline/tasks/{key}/intent.md'
        intent.write_text('# Intent\n\n## 해결할 문제\n정원 동기화\n')
        return intent

    def test_new_task_copies_template_and_refuses_duplicates_and_bad_keys(self):
        result = new_task(self.root, KEY, 'party sync', 'M', {'notion': 'https://n'})
        folder = self.root / f'.pipeline/tasks/{KEY}'
        for name in ('intent.md', 'spec.md', 'decisions.md', 'result.md', 'logs/retries.md',
                     'tickets/_TICKET-TEMPLATE.md', 'task.yaml'):
            self.assertTrue((folder / name).is_file(), name)
        self.assertEqual(result['ticket_prefix'], f'T-{KEY}-')
        self.assertIn(TEMPLATE_MARKER, (folder / 'intent.md').read_text())
        with self.assertRaises(ValueError):
            new_task(self.root, KEY, 'again', 'M')
        for key in ('party-sync', '2026-11-02-x', '20261102-Upper', '20261102-../x'):
            with self.assertRaises(ValueError):
                new_task(self.root, key, 'bad', 'M')

    def test_template_intent_cannot_be_approved(self):
        new_task(self.root, KEY, 'party sync', 'M')
        with self.assertRaises(ValueError):
            approve_intent(self.root, KEY, 'human', 'chat')

    def test_approval_is_immutable_and_hashes_the_intent(self):
        intent = self.write_intent()
        record = approve_intent(self.root, KEY, 'human', 'chat: 진행해')
        stored = json.loads((intent.parent / 'approval.json').read_text())
        self.assertEqual(stored['intent_sha256'], record['intent_sha256'])
        with self.assertRaises(ValueError):
            approve_intent(self.root, KEY, 'human', 'again')

    def test_approval_counts_only_once_identical_on_base(self):
        intent = self.write_intent()
        approve_intent(self.root, KEY, 'human', 'chat')
        with self.assertRaises(ValueError) as local_only:
            approved_intent(self.root, KEY)
        self.assertIn('not on origin/main', str(local_only.exception))
        self.publish('docs: intent')
        approved_intent(self.root, KEY)
        intent.write_text(intent.read_text() + 'edit after merge\n')
        with self.assertRaises(ValueError):
            approved_intent(self.root, KEY)

    def test_small_task_builds_on_its_own_pushed_branch(self):
        self.write_intent(size='S')
        approve_intent(self.root, KEY, 'human', 'chat')
        self.assertEqual(task_base_ref(self.root, KEY), f'origin/task/{KEY}')
        git(self.root, 'checkout', '-q', '-b', f'task/{KEY}')
        self.publish('docs: intent', branch='main-not-used')
        with self.assertRaises(ValueError):
            approved_intent(self.root, KEY)
        git(self.root, 'push', '-q', 'origin', f'HEAD:task/{KEY}')
        git(self.root, 'fetch', '-q', 'origin')
        approved_intent(self.root, KEY)

    def cli(self, *args):
        command = [sys.executable, str(ENGINE / 'scripts/pipeline_cli.py'), *args]
        return subprocess.run(command, cwd=self.root, capture_output=True, text=True)

    def test_cli_task_lifecycle(self):
        created = self.cli('new-task', KEY, '--title', '정원 동기화', '--size', 'M', '--issue', 'o/r#223')
        self.assertEqual(created.returncode, 0, created.stderr)
        self.assertEqual(json.loads(created.stdout)['ticket_prefix'], f'T-{KEY}-')
        refused = self.cli('approve-intent', KEY, '--approver', 'human', '--evidence', 'chat')
        self.assertEqual(refused.returncode, 2)
        self.assertIn('template', refused.stderr)
        (self.root / f'.pipeline/tasks/{KEY}/intent.md').write_text('# Intent\n')
        approved = self.cli('approve-intent', KEY, '--approver', 'human', '--evidence', 'chat')
        self.assertEqual(approved.returncode, 0, approved.stderr)
        rows = json.loads(self.cli('tasks').stdout)
        self.assertEqual((rows[0]['key'], rows[0]['links']), (KEY, {'issue': 'o/r#223'}))
        self.assertIn('not on origin/main', rows[0]['intent'])
        self.publish('docs: intent')
        self.assertEqual(json.loads(self.cli('tasks').stdout)[0]['intent'], 'approved')

    def test_ticket_lookup_and_prefix_rules(self):
        self.write_intent()
        approve_intent(self.root, KEY, 'human', 'chat')
        card = self.root / f'.pipeline/tasks/{KEY}/tickets/T-{KEY}-01.md'
        card.write_text(TICKET)
        self.publish('docs: intent and ticket')
        self.assertEqual(find_ticket(self.root, f'T-{KEY}-01'), card)
        self.assertEqual(task_key_of(card), KEY)
        self.assertEqual(load_ticket(card)['id'], f'T-{KEY}-01')
        ticket_intent(self.root, f'T-{KEY}-01')
        stray = card.with_name('T-001.md')
        stray.write_text(TICKET.replace(f'T-{KEY}-01', 'T-001'))
        with self.assertRaises(ValueError):
            load_ticket(stray)
        self.assertIsNone(find_ticket(self.root, 'T-001'))


if __name__ == '__main__':
    unittest.main()
