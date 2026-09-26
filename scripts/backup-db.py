#!/usr/bin/env python3
"""Create and verify a consistent SQLite snapshot, including a running database."""
import os
from pathlib import Path
import sqlite3
import sys
import tempfile


def backup(source: Path, destination: Path) -> None:
    source = source.resolve(strict=True)
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        raise FileExistsError(f"Refusing to replace existing backup: {destination}")
    descriptor, temporary = tempfile.mkstemp(prefix='.listful-backup-', dir=destination.parent)
    os.close(descriptor)
    try:
        with sqlite3.connect(source.as_uri() + '?mode=ro', uri=True, timeout=30) as live:
            with sqlite3.connect(temporary) as snapshot:
                live.backup(snapshot)
                if snapshot.execute('PRAGMA integrity_check').fetchall() != [('ok',)]:
                    raise RuntimeError('Backup integrity check failed')
        # Hard-link publishes atomically and never overwrites another backup.
        os.link(temporary, destination)
    finally:
        os.unlink(temporary)


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('Usage: backup-db.py SOURCE.sqlite BACKUP.sqlite')
    backup(Path(sys.argv[1]), Path(sys.argv[2]))
    print(f'Verified backup: {sys.argv[2]}')
