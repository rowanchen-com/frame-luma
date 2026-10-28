"""Offline, standard-library module contract checks. Never opens ADB or vendor services."""
from pathlib import Path
import json,re,sys

ROOT=Path(__file__).resolve().parents[1]
ids=set();matches=set();count=0
for descriptor in sorted((ROOT/'devices').rglob('module.json')):
    folder=descriptor.parent;module=json.loads(descriptor.read_text(encoding='utf-8'))
    relative=folder.relative_to(ROOT/'devices').as_posix()
    assert re.fullmatch(r'[a-z0-9-]+(?:/[a-z0-9-]+)*',relative),relative
    assert module['schema_version']==1 and module['api_version']==1,relative
    assert module['kind'] in ('display','light'),relative
    assert re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*',module['id']),relative
    assert module['id'] not in ids,'Duplicate module id';ids.add(module['id'])
    match=(module['kind'],json.dumps(module['match'],sort_keys=True))
    assert match not in matches,'Duplicate exact matching rule';matches.add(match)
    entry=module['entry_class'];assert entry.startswith('com.xiaomi.mitv.light.devices.'),entry
    source=folder/'src/main/java'/Path(*entry.split('.')).with_suffix('.java')
    assert source.is_file(),source
    for required in ['README.md','build.gradle.kts','docs/validation.md']:
        assert (folder/required).is_file(),f'{relative}: missing {required}'
    for required in ['src','config','tests','docs/images']:
        assert (folder/required).is_dir(),f'{relative}: missing {required}'
    assert list((folder/'tests').rglob('*Test.java')),f'{relative}: no module contract test'
    for config in (folder/'config').rglob('*.json'):json.loads(config.read_text(encoding='utf-8'))
    states=module.get('capabilities',{}).get('inputs',{})
    assert all(v in ('verified','pending','unsupported') for v in states.values()),relative
    count+=1
assert count>0,'No device modules found'
print(f'Module checks passed: {count} self-contained modules, unique identities, configuration and test/docs entries present.')
