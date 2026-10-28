"""TCL-specific bounded status measurement. No settings, files or log clearing on TV."""
import argparse
import datetime
import json
import queue
import statistics
import subprocess
import threading
import time
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default='adb')
parser.add_argument('--seconds', type=int, default=8, choices=range(3, 31))
parser.add_argument('--output', type=Path, default=Path('.local/latency.json'))
parser.add_argument('--revision', default='frame-luma-5')
parser.add_argument('--source', choices=['device','own_app'],default='device')
args = parser.parse_args()
base = [args.adb, '-s', args.serial]

def run(*command):
    return subprocess.run(base + list(command), check=True, capture_output=True,
                          timeout=12, encoding='utf-8', errors='replace').stdout.strip()

boot = run('shell', 'cat', '/proc/sys/kernel/random/boot_id')
host_pid = run('shell', 'pidof', 'com.tcl.ttvs.container')
lines = queue.Queue(maxsize=256)
process = subprocess.Popen(base + ['logcat', '-v', 'brief', '-T', '1', '-s',
                                  'TclUnifiedHost:I', 'FrameLumaEngine:W', 'FrameLumaStrip3:W'],
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           encoding='utf-8', errors='replace')

def read_lines():
    for line in process.stdout:
        try:
            lines.put_nowait(line)
        except queue.Full:
            pass

threading.Thread(target=read_lines, daemon=True).start()
statuses = []
seen = set()
until = time.monotonic() + args.seconds
try:
    while time.monotonic() < until:
        run('shell', 'am', 'broadcast', '-a', 'com.xiaomi.mitv.light.tcl.DEBUG_STATUS', '-p', 'com.tcl.ttvs')
        time.sleep(0.4)
        while not lines.empty():
            line = lines.get_nowait()
            if '{' not in line:
                continue
            try:
                value = json.loads(line[line.index('{'):])
            except ValueError:
                continue
            engine = value.get('engine', {})
            if engine.get('engine_revision') != args.revision or value.get('event') != 'status':
                continue
            if engine.get('capture_source') != args.source:
                continue
            sequence = engine.get('batches', 0)
            if engine.get('running') and engine.get('sweeps', 0) > 1 and sequence not in seen:
                seen.add(sequence)
                statuses.append(value)
finally:
    process.terminate()
    try:
        process.wait(timeout=3)
    except subprocess.TimeoutExpired:
        process.kill()

assert len(statuses) >= 3, 'No stable FrameLuma samples; inspect host/app errors instead of claiming a speedup'
engines = [s['engine'] for s in statuses]
assert len({(e['period_ms'],e['progressive'],e['sample_points']) for e in engines}) == 1, 'Settings changed during measurement; repeat after they settle'
assert all(not e.get('error') for e in engines), 'Engine reported an error'
assert all(s.get('sampling_threads') == 1 and s.get('own_usb_fds') == 1 for s in statuses), 'Unexpected resource ownership'
assert run('shell', 'cat', '/proc/sys/kernel/random/boot_id') == boot, 'Device rebooted during measurement'
assert run('shell', 'pidof', 'com.tcl.ttvs.container') == host_pid, 'Host restarted during measurement'
fields = ['capture_elapsed_ms', 'actual_period_ms', 'first_update_ms', 'output_interval_ms', 'output_write_ms']
report = {
    'measured_at': datetime.datetime.now().astimezone().isoformat(),
    'revision': engines[-1]['engine_revision'], 'status_samples': len(statuses),
    'settings_period_ms': engines[-1]['period_ms'], 'progressive': engines[-1]['progressive'],
    'capture_source': engines[-1]['capture_source'],
    'sample_points': engines[-1]['sample_points'],
    'median': {k: statistics.median(e[k] for e in engines) for k in fields},
    'minimum': {k: min(e[k] for e in engines) for k in fields},
    'maximum': {k: max(e[k] for e in engines) for k in fields},
    'single_sampling_thread': True, 'single_usb_fd': True,
    'same_boot_during_measurement': True, 'same_host_during_measurement': True,
    'scope': 'Software sampling and USB send completion; not camera-measured screen-to-photon latency',
}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
