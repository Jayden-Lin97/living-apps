"""Explicit source boundary for the Android project's public distribution."""
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
PUBLIC_DOC_NAMES=('APP_EDITING_INTERACTION','DEVELOPMENT_PLAN','OPEN_SOURCE_REVIEW','ROADMAP','SCHEMA','UI_DESIGN','USER_GUIDE','VALIDATION','REBUILD_MODE')
ROOT_FILES=('.gitignore','.gitattributes','AGENTS.md','AGENTS.zh-CN.md','README.md','README.zh-CN.md','CONTRIBUTING.md','CONTRIBUTING.zh-CN.md','SECURITY.md','SECURITY.zh-CN.md','settings.gradle','build.gradle','gradle.properties','gradlew','gradlew.bat','LICENSE')
PUBLIC_SCRIPTS=('build.ps1','audit_artifact.py','package_delivery.py','import_lucide.py','probe_api.py','check_i18n.py','public_sources.py')

def is_public_path(relative):
    path=Path(relative);name=path.as_posix()
    if path.is_absolute() or '..' in path.parts:return False
    if any(part in ('.git','.gradle','.local','.private','.tools','build','dist','__pycache__') or part.startswith('.env') for part in path.parts):return False
    if path.suffix.lower() in ('.jks','.keystore','.secure','.dpapi') or path.name=='local.properties':return False
    if name in ROOT_FILES:return True
    if name.startswith('scripts/'):return name in {'scripts/'+n for n in PUBLIC_SCRIPTS}
    if name.startswith('docs/'):
        return name in {'docs/'+n+suffix for n in PUBLIC_DOC_NAMES for suffix in ('.md','.zh-CN.md')} or name=='docs/lucide-icons.json' or (name.startswith('docs/qa/') and path.suffix=='.png')
    if name.startswith('app/'):return not name.startswith('app/src/demoCapture/')
    return name.startswith(('gradle/','licenses/'))

def public_files():
    candidates=[ROOT/n for n in ROOT_FILES]+[ROOT/'scripts'/n for n in PUBLIC_SCRIPTS]
    for folder in ('app','docs','gradle','licenses'):
        candidates.extend(path for path in (ROOT/folder).rglob('*') if path.is_file())
    return sorted({p for p in candidates if p.is_file() and is_public_path(p.relative_to(ROOT))})
