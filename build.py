"""Build Cycle Clock from src/app.html.

  python3 build.py [artifact_out.html]

Writes the offline web app (index.html, sw.js, manifest, icons) at the repo root, refreshes the
Android launcher icon, and, if a path is given, the preview page for claude.ai.
The Android app is built by GitHub Actions from index.html (.github/workflows/android.yml).
"""
import json, os, re, sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.abspath(__file__))
VERSION = 'cycle-clock-v5'            # bump on every release so installed copies update
BG = '#EEF1F8'
src = open(os.path.join(ROOT, 'src', 'app.html'), encoding='utf-8').read()
assert '/*PWA*/false' in src

if len(sys.argv) > 1:                 # preview page: the host adds the document skeleton
    open(sys.argv[1], 'w', encoding='utf-8').write(src)

title = re.search(r'<title>.*?</title>', src).group(0)
body = src.replace(title, '', 1).replace('/*PWA*/false', 'true')
head = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
{title}
<meta name="cc-version" content="{VERSION}">
<meta name="theme-color" content="{BG}">
<meta name="color-scheme" content="light">
<meta name="apple-mobile-web-app-capable" content="yes">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-status-bar-style" content="default">
<meta name="apple-mobile-web-app-title" content="Cycle Clock">
<link rel="manifest" href="manifest.webmanifest">
<link rel="apple-touch-icon" href="icon-180.png">
<link rel="icon" type="image/png" href="icon-192.png">
<style>:root{{padding-top:env(safe-area-inset-top,0px);padding-bottom:env(safe-area-inset-bottom,0px)}}</style>
</head>
<body>
"""
open(os.path.join(ROOT, 'index.html'), 'w', encoding='utf-8').write(head + body + '\n</body>\n</html>\n')

sw = """// Cycle Clock offline cache: serve from cache, refresh in the background.
const CACHE = '%s';
const FILES = ['./', './index.html', './manifest.webmanifest', './icon-180.png', './icon-192.png', './icon-512.png'];
self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(FILES)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  e.respondWith(caches.open(CACHE).then(cache =>
    cache.match(req, { ignoreSearch: true }).then(hit => {
      const fresh = fetch(req).then(res => {
        if (res && res.ok) cache.put(req, res.clone());
        return res;
      }).catch(() => hit || cache.match('./index.html'));
      return hit || fresh;
    })
  ));
});
""" % VERSION
open(os.path.join(ROOT, 'sw.js'), 'w').write(sw)

manifest = {
    "name": "Cycle Clock", "short_name": "Cycle Clock",
    "description": "Plan bedtimes and wake-up times around your sleep cycles.",
    "start_url": "./", "scope": "./", "display": "standalone", "orientation": "any",
    "background_color": BG, "theme_color": BG,
    "icons": [
        {"src": "icon-192.png", "sizes": "192x192", "type": "image/png"},
        {"src": "icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any"},
        {"src": "icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "maskable"},
    ],
}
open(os.path.join(ROOT, 'manifest.webmanifest'), 'w').write(json.dumps(manifest, indent=2))

def lerp(a, b, t): return tuple(round(x + (y - x) * t) for x, y in zip(a, b))

def icon(size):
    """Dawn sky, five sleep-cycle arcs going from night blue to sunrise, a crescent in the middle."""
    S = 2048
    top, bottom = (214, 225, 255), (255, 226, 212)
    im = Image.new('RGB', (S, S))
    d = ImageDraw.Draw(im)
    for y in range(S):
        d.line([(0, y), (S, y)], fill=lerp(top, bottom, y / S))
    cx = cy = S / 2
    r, w = S * 0.33, int(S * 0.06)
    box = [cx - r, cy - r, cx + r, cy + r]
    night, sky, sun = (61, 79, 216), (122, 155, 255), (224, 78, 42)
    n, gap = 5, 12
    step = 360 / n
    for i in range(n):
        a0 = -90 + gap / 2 + i * step
        col = sun if i == n - 1 else lerp(night, sky, i / (n - 2))
        d.arc(box, a0, a0 + step - gap, fill=col, width=w)
    mr = S * 0.16
    moon = Image.new('L', (S, S), 0)
    md = ImageDraw.Draw(moon)
    md.ellipse([cx - mr, cy - mr, cx + mr, cy + mr], fill=255)
    off = mr * 0.62
    md.ellipse([cx - mr + off, cy - mr - off * 0.55, cx + mr + off, cy + mr - off * 0.55], fill=0)
    im.paste(night, (0, 0), moon)
    return im.resize((size, size), Image.LANCZOS)

for s in (180, 192, 512):
    icon(s).save(os.path.join(ROOT, f'icon-{s}.png'), optimize=True)
mip = os.path.join(ROOT, 'android', 'app', 'src', 'main', 'res', 'mipmap-xxxhdpi', 'ic_launcher.png')
if os.path.isdir(os.path.dirname(mip)):
    icon(192).save(mip, optimize=True)
print('built', VERSION, os.path.getsize(os.path.join(ROOT, 'index.html')), 'bytes')
