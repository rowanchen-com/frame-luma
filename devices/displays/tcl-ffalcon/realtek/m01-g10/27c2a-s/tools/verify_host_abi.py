"""Verify our frozen host classes after a release build; no device connection."""
import hashlib
import json
from pathlib import Path

module = Path(__file__).resolve().parents[1]
contract = json.loads((module / 'tools/host-abi.json').read_text(encoding='utf-8'))
classes = module / 'build/intermediates/javac/release/compileReleaseJavaWithJavac/classes/com/xiaomi/mitv/light/tcl/host'
for name, expected in contract['classes'].items():
    target = classes / name
    assert target.is_file(), f'Build the release variant first: missing {name}'
    assert hashlib.sha256(target.read_bytes()).hexdigest() == expected, f'Frozen host class changed: {name}'
print(f"Host compatibility passed: {len(contract['classes'])} frozen classes match the installed prototype contract.")
