"""Explicit developer tool: import pinned Lucide SVG geometry as native Android vectors."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import json
import re
import time
import urllib.request
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
manifest = root / 'docs/lucide-icons.json'
icons = ('layout-grid sprout settings-2 plus arrow-left chevron-right wand-sparkles '
         'search shield-check history ellipsis pen-line notebook-pen book-open droplets '
         'wallet check arrow-up-right download upload lock leaf clock-3 chart-column '
         'sliders-horizontal circle-help trash-2').split()

def fetch(url):
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'LivingApps-asset-import'})
            with urllib.request.urlopen(req, timeout=20) as response:
                return response.read().decode('utf-8')
        except Exception as error:
            if attempt == 2:
                raise RuntimeError('Cannot fetch ' + url.rsplit('/',1)[-1]) from error
            time.sleep(.4)

if manifest.exists():
    commit = json.loads(manifest.read_text(encoding='utf-8'))['commit']
else:
    commit = 'f12b0de177fbc2a6795e99be065887e72b237123'  # release 0.468.0
assert re.fullmatch('[0-9a-f]{40}', commit)
base = f'https://raw.githubusercontent.com/lucide-icons/lucide/{commit}/'
license_text = fetch(base + 'LICENSE')
(root / 'licenses/Lucide-LICENSE.txt').write_text(license_text, encoding='utf-8')
(root / 'app/src/main/assets/lucide-license.txt').write_text(license_text, encoding='utf-8')

def geometry(element):
    tag = element.tag.split('}')[-1]
    a = element.attrib
    if tag == 'path':
        return a['d']
    if tag == 'line':
        return f'M{a["x1"]},{a["y1"]}L{a["x2"]},{a["y2"]}'
    if tag in ('polyline', 'polygon'):
        p = re.findall(r'-?[\d.]+', a['points'])
        return 'M' + ','.join(p[:2]) + 'L' + ' '.join(','.join(p[i:i+2]) for i in range(2,len(p),2)) + ('Z' if tag == 'polygon' else '')
    if tag in ('circle', 'ellipse'):
        x,y = float(a['cx']),float(a['cy'])
        rx,ry = float(a.get('r',a.get('rx',0))),float(a.get('r',a.get('ry',0)))
        return f'M{x-rx},{y}a{rx},{ry} 0 1,0 {rx*2},0a{rx},{ry} 0 1,0 {-rx*2},0'
    if tag == 'rect':
        x,y,w,h = (float(a.get(k,0)) for k in ('x','y','width','height'))
        r = min(float(a.get('rx',0)),w/2,h/2)
        return f'M{x+r},{y}H{x+w-r}Q{x+w},{y} {x+w},{y+r}V{y+h-r}Q{x+w},{y+h} {x+w-r},{y+h}H{x+r}Q{x},{y+h} {x},{y+h-r}V{y+r}Q{x},{y} {x+r},{y}Z'
    raise ValueError('Unsupported SVG element: ' + tag)

def convert(name):
    svg = ET.fromstring(fetch(base + f'icons/{name}.svg'))
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<!-- Lucide / Feather contributors. ISC / MIT; see assets/lucide-license.txt. -->',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">']
    for element in svg:
        path = geometry(element)
        lines.append(f'    <path android:pathData="{path}" android:fillColor="#00000000" android:strokeColor="#1C3F35" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" />')
    lines.append('</vector>')
    (root / ('app/src/main/res/drawable/ic_' + name.replace('-','_') + '.xml')).write_text('\n'.join(lines)+'\n',encoding='utf-8')

with ThreadPoolExecutor(max_workers=4) as pool:
    list(pool.map(convert, icons))
manifest.write_text(json.dumps({'project':'https://github.com/lucide-icons/lucide','commit':commit,'version':'0.468.0','icons':icons,'licenses':'ISC + MIT for Feather-derived icons'},indent=2)+'\n',encoding='utf-8')
print(f'Imported {len(icons)} native vectors at {commit}; license preserved.')
