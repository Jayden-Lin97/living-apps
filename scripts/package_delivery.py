"""Package only the public Android product allowlist, never the whole workspace."""
import hashlib,json,zipfile
from public_sources import ROOT,public_files

destination=ROOT/'dist';destination.mkdir(exist_ok=True)
archive_path=destination/'living-apps-0.3.0-source.zip'
files=public_files()
with zipfile.ZipFile(archive_path,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in files:archive.write(path,'living-apps/'+path.relative_to(ROOT).as_posix())
result={'archive':archive_path.name,'source_files':len(files),'bytes':archive_path.stat().st_size,'sha256':hashlib.sha256(archive_path.read_bytes()).hexdigest()}
(destination/'source-package.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
