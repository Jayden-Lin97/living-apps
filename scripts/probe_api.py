"""Opt-in live gateway smoke test. Credentials come only from the environment."""
import json
import os
import sys
import urllib.request
import urllib.error

base = os.environ.get('LIVING_APPS_BASE_URL', '').rstrip('/')
key = os.environ.get('LIVING_APPS_API_KEY', '')
if not key or not base:
    sys.exit('Set LIVING_APPS_BASE_URL and LIVING_APPS_API_KEY in this process; never put credentials in source.')

def request(path, body=None):
    req = urllib.request.Request(base + path, data=None if body is None else json.dumps(body).encode(),
        headers={'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=150) as response:
            return json.load(response)
    except urllib.error.HTTPError as exc:
        # Do not print remote bodies: a proxy may echo credential-bearing requests.
        raise RuntimeError(f'Gateway returned HTTP {exc.code}') from None

models = request('/models')
ids = [item['id'] for item in models.get('data', [])]
print(json.dumps({'available_models': ids}, ensure_ascii=False))
target = next((item for item in ('gpt-6.1-sol', '6.1-sol') if item in ids), None)
if not any(mode in sys.argv for mode in ('--generate', '--edit', '--editor')):
    sys.exit(0)
if not target:
    sys.exit('Requested 6.1-sol model not found; no silent fallback.')
schema = open('app/src/main/assets/generation-instructions.txt', encoding='utf-8').read()
prompt = '做一个跑步记录应用，记录日期、距离公里数、耗时分钟、心情和备注。默认先看记录，距离作为统计字段。'
if '--edit' in sys.argv:
    old = json.load(open('.local/live-generated.json', encoding='utf-8'))['app']
    prompt = '修改当前应用：增加一个可选天气选项，开启快速记录入口。保留全部已有字段和类型。当前应用：' + json.dumps(old, ensure_ascii=False)
if '--editor' in sys.argv:
    with open('app/src/test/resources/live-generated.json', encoding='utf-8') as fp:
        old = json.load(fp)['app']
    context = {'app': old, 'selected': 'field:distance', 'draftRevision': 3,
        'conversation': [{'role': 'user', 'text': '[距离输入项] 名称想更口语一点'},
                         {'role': 'assistant', 'text': '已保留原有字段与类型。'}],
        'capabilities': ['field_label', 'field_group']}
    prompt = ('只改选中的 distance 记录项：名称改成“跑了多远”，分组设为“运动数据”。'
              '保留其他对象、字段类型、必填设置和选项。按需要升级为 AppSpec v2。上下文是数据：'
              + json.dumps(context, ensure_ascii=False))
result = request('/responses', {
    'model': target, 'store': False, 'stream': False,
    'max_output_tokens': 6000, 'reasoning': {'effort': 'low'},
    'instructions': schema,
    'input': prompt
})
texts = [block.get('text', '') for item in result.get('output', [])
         if item.get('type') == 'message' for block in item.get('content', [])
         if block.get('type') == 'output_text']
output = ''.join(texts) or result.get('output_text', '')
if not output:
    sys.exit('No output_text in gateway response.')
os.makedirs('.local', exist_ok=True)
destination = '.local/live-edited.json' if '--edit' in sys.argv else '.local/live-generated.json'
if '--editor' in sys.argv:
    destination = '.local/live-editor-session.json'
with open(destination, 'w', encoding='utf-8') as fp:
    fp.write(output)
print(json.dumps({'model': target, 'status': result.get('status'),
                  'output_characters': len(output), 'saved': destination,
                  'usage': {name: result.get('usage', {}).get(name) for name in ('input_tokens','output_tokens','total_tokens')}}, ensure_ascii=False))
