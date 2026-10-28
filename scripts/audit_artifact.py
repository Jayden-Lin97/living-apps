"""Audit public source, staged paths and APK without printing matching material."""
import hashlib,ipaddress,json,re,subprocess,zipfile
from public_sources import ROOT,is_public_path,public_files

credential=re.compile(rb'(?:sk-(?:ws-)?[A-Za-z0-9_.-]{24,}|AKID[A-Za-z0-9]{25,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)')
operational=re.compile(rb'(?:/srv/|/etc/nginx|tencentcloud|TENCENTCLOUD_|inv-[a-z0-9]{8,}|[A-Z]:[/\\]Users[/\\][^/\\\x00\r\n]+)',re.I)
ipv4=re.compile(rb'(?<![\d.])(?:\d{1,3}\.){3}\d{1,3}(?![\d.])')
def findings(data):
    result=[]
    if credential.search(data):result.append('credential pattern')
    if operational.search(data):result.append('operational or personal path')
    for match in ipv4.finditer(data):
        try:address=ipaddress.ip_address(match.group().decode())
        except ValueError:continue
        if str(address) not in ('127.0.0.1','10.0.2.2') and not address.is_loopback:
            result.append('nonlocal IPv4 literal');break
    return result
issues=[]
def audit(name,data):
    if name=='scripts/audit_artifact.py':return
    problems=findings(data)
    if problems:issues.append({'file':name,'findings':problems})
for path in public_files():
    audit(path.relative_to(ROOT).as_posix(),path.read_bytes())
git=subprocess.run(['git','ls-files','-z'],cwd=ROOT,capture_output=True)
if git.returncode==0:
    for name in git.stdout.decode().split('\0'):
        if name and not is_public_path(name):issues.append({'file':name,'findings':['outside public allowlist']})
apk=ROOT/'dist/living-apps-0.3.0-debug.apk'
with zipfile.ZipFile(apk) as archive:
    entries=archive.namelist()
    assert 'AndroidManifest.xml' in entries and 'classes.dex' in entries
    assert not any('live-generated' in n or 'live-edited' in n or 'api-key' in n.lower() for n in entries),'Private or test files in APK'
    for name in entries:
        audit('APK:'+name,archive.read(name))
source=ROOT/'dist/living-apps-0.3.0-source.zip'
if source.exists():
    with zipfile.ZipFile(source) as archive:
        expected={'living-apps/'+p.relative_to(ROOT).as_posix():p for p in public_files()}
        assert set(archive.namelist())==set(expected),'Source archive does not match public allowlist'
        for name in archive.namelist():
            data=archive.read(name)
            assert data==expected[name].read_bytes(),'Stale source archive'
            audit(name.removeprefix('living-apps/'),data)
result={'apk':str(apk.relative_to(ROOT)),'bytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'publication_issues':issues,'main_apk_contains_test_fixtures':False}
(ROOT/'dist/artifact-audit.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
if issues:raise SystemExit(1)
