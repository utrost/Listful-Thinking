#!/usr/bin/env python3
import importlib.util
from pathlib import Path
import sqlite3
import tempfile
import sys
sys.dont_write_bytecode = True

spec = importlib.util.spec_from_file_location('backup', Path(__file__).with_name('backup-db.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
with tempfile.TemporaryDirectory() as directory:
    source = Path(directory) / 'live.sqlite'
    target = Path(directory) / 'snapshot.sqlite'
    with sqlite3.connect(source) as live:
        live.execute('PRAGMA journal_mode=WAL')
        live.execute('CREATE TABLE items(name TEXT)')
        live.execute("INSERT INTO items VALUES ('kept')")
        live.commit()
        module.backup(source, target)
        live.execute("INSERT INTO items VALUES ('later')")
        live.commit()
        with sqlite3.connect(target) as restored:
            assert restored.execute('SELECT name FROM items').fetchall() == [('kept',)]
            assert restored.execute('PRAGMA integrity_check').fetchone() == ('ok',)
        try:
            module.backup(source, target)
            raise AssertionError('Existing backups must not be overwritten')
        except FileExistsError:
            pass
print('Online backup and restore checks passed')
