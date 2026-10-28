"""Verify the built APK's package, fixed certificate, registered modules and native-free layout."""
from pathlib import Path
from zipfile import ZipFile
import hashlib,json,os,re,subprocess,sys
ROOT=Path(__file__).resolve().parents[1]
apk=ROOT/'app/build/outputs/apk/release/app-release.apk'
policy=json.loads((ROOT/'signing/policy.json').read_text(encoding='utf-8'))
properties={}
local=ROOT/'local.properties'
if local.exists():
    for line in local.read_text(encoding='utf-8').splitlines():
        if '=' in line:
            key,value=line.split('=',1);properties[key]=value
sdk=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or properties.get('sdk.dir',''))
tools=sdk/'build-tools/34.0.0'
java=Path(os.environ['JAVA_HOME'])/'bin'/('java.exe' if os.name=='nt' else 'java')
def run(args):
    r=subprocess.run([str(a) for a in args],capture_output=True,timeout=30)
    if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace'))
    return r.stdout.decode(errors='replace')
signature=run([java,'-jar',tools/'lib/apksigner.jar','verify','--verbose','--print-certs',apk])
certs=re.findall(r'^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-f]{64})',signature,re.M)
assert certs==[policy['certificate_sha256']],'Certificate changed'
badging=run([tools/('aapt2.exe' if os.name=='nt' else 'aapt2'),'dump','badging',apk])
assert f"package: name='{policy['application_id']}'" in badging
version=int(re.search(r"versionCode='(\d+)'",badging).group(1));assert version>=policy['minimum_version_code']
assert 'leanback-launchable-activity:' in badging,'TV launcher entry missing'
with ZipFile(apk) as z:
    assert z.testzip() is None
    native=[n for n in z.namelist() if n.startswith('lib/') and n.endswith('.so')]
    if native:
        by_abi={abi:{n.split('/')[-1] for n in native if n.startswith('lib/'+abi+'/')} for abi in ('armeabi-v7a','arm64-v8a')}
        assert by_abi['armeabi-v7a'] and by_abi['armeabi-v7a']==by_abi['arm64-v8a'],'Native libraries need corresponding ARM32/64 variants'
    modules=json.loads(z.read('assets/device-catalog.json'))
    expected={json.loads(p.read_text(encoding='utf-8'))['id'] for p in (ROOT/'devices').rglob('module.json')}
    assert {m['id'] for m in modules}==expected,'Compiled registry differs from source modules'
result={'version_code':version,'application_id':policy['application_id'],'certificate_sha256':certs[0],
    'apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'apk_bytes':apk.stat().st_size,
    'device_modules':[m['id'] for m in modules],'tv_launcher':True,'native_libraries':native,
    'device_actions_performed_by_this_script':False,'scope':'APK package/signature/content checks only; hardware tests are separate'}
out=ROOT/'.local';out.mkdir(exist_ok=True);(out/'apk-verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
