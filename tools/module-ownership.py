#!/usr/bin/env python3
"""Validate ownership and emit literal line claims for the root graph."""
import re
import sys
from pathlib import Path


def generate(root, order, output):
    whole, lines, records = {}, {}, []
    for module in Path(order).read_text().splitlines():
        fields = {}
        claims = []
        # Preserve CR rather than letting text-mode newline conversion hide it.
        source = (Path(root) / 'modules' / module / 'module.properties').read_bytes().decode('utf-8')
        for raw in source.split('\n'):
            if raw.startswith('#') or '=' not in raw:
                continue
            key, value = raw.split('=', 1)
            if key in ('owns_files', 'owns_props', 'reassert_props', 'owns_generated_dirs') and key in fields:
                raise ValueError(f'duplicate ownership key: {module}:{key}')
            if key.startswith('owns_lines'):
                if not re.fullmatch(r'owns_lines\.[1-9][0-9]*', key) or key in fields:
                    raise ValueError(f'invalid or duplicate line key: {module}:{key}')
                path, sep, stock = value.partition(':')
                if not sep or any(ord(c) < 32 or ord(c) == 127 for c in stock):
                    raise ValueError(f'invalid stock line: {module}:{key}')
                claims.append((key.split('.')[1], path, stock))
            fields[key] = value
        reassert = fields.get('reassert_props', '')
        if reassert:
            keys = reassert.split(',')
            owned = fields.get('owns_props', '').split(',')
            if (fields.get('restore') == 'reboot' or len(keys) != len(set(keys)) or
                    any(not re.fullmatch(r'[a-zA-Z0-9_.-]+', key) or key not in owned for key in keys)):
                raise ValueError(f'invalid reassert_props: {module}')
        generated = list(filter(None, fields.get('owns_generated_dirs', '').split(',')))
        if len(generated) != len(set(generated)) or fields.get('restore') == 'reboot' and generated or any(
                not re.fullmatch(r'/data/data/[A-Za-z0-9._]+/app_hmg-seogaemo-[A-Za-z0-9_-]+', path)
                for path in generated):
            raise ValueError(f'invalid owns_generated_dirs: {module}')
        for path in filter(None, fields.get('owns_files', '').split(',')):
            validate_path(path)
            if path in whole or any(p == path for p, _ in lines):
                raise ValueError(f'whole-file ownership conflict: {path}')
            whole[path] = module
        for number, path, stock in sorted(claims, key=lambda c: int(c[0])):
            validate_path(path)
            if path in whole:
                raise ValueError(f'whole-file ownership conflict: {path}')
            if (path, stock) in lines:
                raise ValueError(f'duplicate line ownership: {path}:{stock}')
            if fields.get('restore') == 'reboot':
                raise ValueError(f'reboot restore cannot own persistent targets: {module}')
            lines[path, stock] = module
            records.append(f'{module}\t{number}\t{path}:{stock}\n')
    Path(output).write_text(''.join(records))


def validate_path(path):
    if not re.fullmatch(r'/[A-Za-z0-9/._+\-]+', path) or '..' in path or '//' in path or path.endswith('/'):
        raise ValueError(f'invalid ownership path: {path}')


if __name__ == '__main__':
    try:
        generate(*sys.argv[1:])
    except ValueError as error:
        sys.exit(f'gen-modules: {error}')
