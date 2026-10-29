"""Offline checks for the public Android documentation's language pairs and links."""
from pathlib import Path
import json,re
from public_sources import ROOT,public_files

paths=public_files();allowed={p.resolve() for p in paths}
defaults=[p for p in paths if p.suffix=='.md' and not p.name.endswith('.zh-CN.md')]
for path in defaults:
    cn=path.with_name(path.stem+'.zh-CN.md')
    assert cn.resolve() in allowed,f'Missing Chinese edition: {path.name}'
    en_text=path.read_text(encoding='utf-8');cn_text=cn.read_text(encoding='utf-8')
    assert f'[简体中文]({cn.name})' in en_text and '**English**' in en_text,path
    assert f'[English]({path.name})' in cn_text and '**简体中文**' in cn_text,cn
    for page,text,chinese in ((path,en_text,False),(cn,cn_text,True)):
        for url in re.findall(r'\]\(([^\s)]+)',text):
            if re.match(r'\w+://',url) or url.startswith(('#','mailto:')):continue
            target=(page.parent/url.split('#')[0]).resolve()
            assert target in allowed,f'Link leaves public source: {page.name}: {url}'
            if target.suffix=='.md' and target not in (path.resolve(),cn.resolve()):
                assert target.name.endswith('.zh-CN.md')==chinese,f'Cross-language link: {page.name}: {url}'
print(json.dumps({'public_markdown_pairs':len(defaults),'broken_or_private_links':0}))
