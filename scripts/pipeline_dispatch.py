"""Resolve ticket policy without launching workers unless explicitly requested."""
import argparse
import json
import re
from pathlib import Path
import sys

import yaml

from pipeline_execution import execute, normalize_touches

ROOT = Path(__file__).resolve().parents[1]
TIERS = ['low', 'medium', 'high']
EFFORTS = ['low', 'medium', 'high', 'xhigh', 'max']


def load_ticket(path):
    text = Path(path).read_text()
    if not text.startswith('---\n'):
        raise ValueError('Ticket requires YAML frontmatter')
    lines = text.splitlines()
    end = next((i for i in range(1, len(lines)) if lines[i] == '---'), None)
    if end is None:
        raise ValueError('Unterminated frontmatter')
    ticket = yaml.safe_load('\n'.join(lines[1:end]))
    if not isinstance(ticket, dict):
        raise ValueError('Frontmatter must be a mapping')
    for field in ('id', 'tier', 'touches', 'consumes', 'produces',
                  'requirements', 'acceptance_tests', 'depends_on', 'status'):
        if field not in ticket:
            raise ValueError(f'Missing ticket field: {field}')
    if ticket['tier'] not in TIERS:
        raise ValueError('Invalid tier')
    for field in ('touches', 'consumes', 'produces', 'requirements',
                  'acceptance_tests', 'depends_on'):
        if not isinstance(ticket[field], list):
            raise ValueError(f'{field} must be a list')
    for identifier in [ticket['id'], *ticket['depends_on']]:
        if not isinstance(identifier, str) or not re.fullmatch(r'T-[A-Za-z0-9_-]+', identifier):
            raise ValueError('Invalid ticket identifier')
    for name in ticket['touches']:
        if not isinstance(name, str) or Path(name).is_absolute() or '..' in Path(name).parts:
            raise ValueError('touches must contain repository-relative paths')
    validate_acceptance_ids(ticket)
    ticket['touches'] = normalize_touches(ticket['touches'])
    return ticket


def validate_acceptance_ids(ticket):
    for field, prefix in (('requirements', 'REQ-'), ('acceptance_tests', 'AT-')):
        values = ticket[field]
        if not values or any(not isinstance(value, str) or not value.startswith(prefix) for value in values):
            raise ValueError(f'{field} must contain nonempty {prefix} identifiers')


def resolve(config, ticket, model=None, capacity=None, failures=0, rounds=0):
    if failures < 0 or rounds < 0:
        raise ValueError('Counts cannot be negative')
    tier = ticket['tier']
    raw_offset = ticket.get('effort_override')
    offset = 0 if raw_offset is None else raw_offset
    if type(offset) is not int or abs(offset) > 1:
        raise ValueError('effort_override must be null or an integer within +/-1')
    limits = config['limits']
    step = limits['failures_before_effort_up']
    promotion_at = step + limits['failures_before_promotion']
    promoted = False
    # Two initial failures, two at increased effort, then promotion.
    while failures >= promotion_at or (tier == 'low' and rounds >= limits['review_rounds']):
        if tier == 'high':
            if high_exhausted(config, offset, failures):
                return {'action': 'escalate', 'target': 'main_designer', 'tier': tier}
            break
        tier = TIERS[TIERS.index(tier) + 1]
        failures = max(0, failures - promotion_at)
        rounds = 0
        promoted = True
    selected, effort = select_implementer(config, tier, offset, failures,
                                           None if promoted else model, capacity)
    implementer = dict(selected, effort=EFFORTS[effort])
    review = config['review'][tier][selected['model']]
    reviewer = dict(review, vendor='openai' if selected['vendor'] == 'claude' else 'claude')
    return {'action': 'dispatch', 'tier': tier, 'promoted': promoted,
            'implementer': implementer, 'reviewer': reviewer,
            'merge_judge': config['merge_judge'][tier],
            'merge_requires': ['gate_pass', 'cross_review_approved'] +
                              ([] if tier == 'low' else ['judge_approved'])}


def high_exhausted(config, offset, failures):
    base = EFFORTS.index(config['implement']['high'][0]['effort']) + offset
    exhausted_at = (EFFORTS.index('max') - base + 1) * config['limits']['failures_before_effort_up']
    return failures >= exhausted_at


def select_implementer(config, tier, offset, failures, model, capacity):
    if capacity and TIERS.index(tier) > TIERS.index(capacity):
        raise ValueError('Upward pickup is forbidden')
    candidates = config['implement'][tier]
    selected = next((item for item in candidates
                     if model is None or item['model'] == model), None)
    if selected is None:
        raise ValueError('Implementer model is not allowed at this tier')
    base = EFFORTS.index(selected['effort']) + offset
    if base < 0 or base >= EFFORTS.index('max'):
        raise ValueError('Ticket effort override is outside allowed range')
    automatic_steps = failures // config['limits']['failures_before_effort_up']
    ceiling = EFFORTS.index('max' if tier == 'high' else 'xhigh')
    effort = min(base + automatic_steps, ceiling)
    if EFFORTS[effort] == 'max' and tier != 'high':
        raise ValueError('max is only allowed during high-tier escalation')
    return selected, effort


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('ticket', type=Path)
    parser.add_argument('--model')
    parser.add_argument('--capacity', choices=TIERS)
    parser.add_argument('--failures', type=int, default=0)
    parser.add_argument('--review-rounds', type=int, default=0)
    parser.add_argument('--execute', action='store_true')
    parser.add_argument('--run', help='Existing Orca Run ID')
    parser.add_argument('--repo', help='Exact Orca repo selector')
    args = parser.parse_args()
    config = yaml.safe_load((ROOT / '.pipeline/agents.yaml').read_text())
    ticket = load_ticket(args.ticket)
    result = resolve(config, ticket, args.model, args.capacity,
                     args.failures, args.review_rounds)
    if args.execute:
        if not args.run or not args.repo:
            raise ValueError('Live dispatch requires --run and --repo')
        if result['action'] != 'dispatch' or ticket['status'] != 'ready':
            raise ValueError('Only ready, dispatchable tickets may execute')
        for dependency in ticket['depends_on']:
            dependency_ticket = load_ticket(ROOT / f'.pipeline/tickets/{dependency}.md')
            if dependency_ticket['status'] != 'merged':
                raise ValueError(f'Dependency not merged: {dependency}')
        result['receipt'] = execute(ROOT, ticket, args.ticket.read_text(), result,
                                    args.run, args.repo, config['limits']['concurrent_implementers'])
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, OSError, RuntimeError, yaml.YAMLError) as error:
        print(f'failed: {error}', file=sys.stderr)
        sys.exit(2)
