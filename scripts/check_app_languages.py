"""Offline catalog coverage and explicit-copy boundaries; never calls a translation API."""
from pathlib import Path
import json,re,xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
catalog=json.loads((ROOT/'app/src/main/resources/dev/livingapps/core/messages.en.json').read_text(encoding='utf-8'))
token=re.compile(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',re.S)
formats=lambda s:re.findall(r'%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]',s)
for source,english in catalog.items():
    assert isinstance(english,str) and (english.strip() or source==' 项'),source
    assert not re.search('[\u4e00-\u9fff]',english),'Chinese in English catalog'
    assert formats(source)==formats(english),'Changed format placeholders'

# These are comparisons against source-app/data values, not display copy.
raw={
 'RebuildSpec.java':{'密码','验证码','支付','付款','转账','购买','充值','删除','注销','发送','提交订单'},
 'RebuildAccessibilityService.java':{'收银台','确认支付'},
 'EditorControls.java':{'是','否'},'MigrationPlan.java':{'是','否'},
 'MainActivity.java':{'简体中文'},
 'Messages.java':{'正在等待模型；可取消，或继续本地调整','正在'},
}
checked=0
for path in (ROOT/'app/src/main/java').rglob('*.java'):
    text=path.read_text(encoding='utf-8')
    matches=[m for m in token.finditer(text) if m.group().startswith('"')]
    i=0
    while i<len(matches):
        first=last=matches[i];value=json.loads(first.group());i+=1
        while i<len(matches) and re.fullmatch(r'\s*\+\s*',text[last.end():matches[i].start()]):
            last=matches[i];value+=json.loads(last.group());i+=1
        if not re.search('[\u4e00-\u9fff]',value):continue
        if value in raw.get(path.name,set()):continue
        # Built-in content is localized on creation, never while reading a saved AppSpec.
        if path.name=='Templates.java':assert value in catalog,(path.name,value);continue
        assert re.search(r'\btr\s*\($',text[:first.start()].rstrip()),f'Unmarked copy: {path.name}: {value}'
        assert value in catalog,f'Missing English: {path.name}: {value}'
        checked+=1
    if path.name not in ('Messages.java','Templates.java'):
        for call in re.finditer(r'\btr\s*\(',text):
            assert text[call.end():].lstrip().startswith('"'),f'Dynamic translation input: {path.name}'

resource_count=0
for filename in ('languages.xml','rebuild_strings.xml'):
    en={e.attrib['name']:''.join(e.itertext()) for e in ET.parse(ROOT/'app/src/main/res/values'/filename).getroot()}
    zh={e.attrib['name']:''.join(e.itertext()) for e in ET.parse(ROOT/'app/src/main/res/values-zh-rCN'/filename).getroot()}
    assert en.keys()==zh.keys(),filename
    for key in en:assert formats(en[key])==formats(zh[key]),key
    resource_count+=len(en)
locales=ET.parse(ROOT/'app/src/main/res/xml/locales_config.xml').getroot()
assert {e.attrib['{http://schemas.android.com/apk/res/android}name'] for e in locales}=={'en','zh-CN'}
print(json.dumps({'catalog_entries':len(catalog),'explicit_copy_calls':checked,'android_resource_pairs':resource_count,'missing_translations':0,'dynamic_user_text_translation':False}))
