#!/usr/bin/env python3
"""Compare the endpoints declared by the original backend with the routes registered here.

usage: audit-routes.py <HuLa-Server dir> <service log dir>
Prints, per module, the endpoints of the original backend that have no route here.
"""
import os, re, sys, collections

src, logs = sys.argv[1], sys.argv[2]
MODULES = {'luohuo-oauth': 'oauth', 'luohuo-im': 'im', 'luohuo-base': 'base', 'luohuo-system': 'system',
           'luohuo-ai': 'ai', 'luohuo-ws': 'ws', 'luohuo-generator': 'generator', 'luohuo-gateway': 'gateway'}
ANN = re.compile(r'@(Get|Post|Put|Delete|Patch|Request)Mapping\s*(\((?:[^()]|\([^()]*\))*\))?')
METHODS = {'Get': 'GET', 'Post': 'POST', 'Put': 'PUT', 'Delete': 'DELETE', 'Patch': 'PATCH'}
# endpoints inherited from the generic controllers: base class -> (method, path)
SAVE = [('POST', '')]
UPDATE = [('PUT', '')]
DELETE = [('DELETE', '')]
QUERY = [('POST', '/page'), ('POST', '/query'), ('GET', '/{}'), ('GET', '/detail'), ('POST', '/findByIds')]
COPY = [('POST', '/copy')]
INHERITED = {'SuperController': SAVE + UPDATE + DELETE + QUERY + COPY, 'SuperCacheController': SAVE + UPDATE + DELETE + QUERY + COPY + [('POST', '/refreshCache'), ('POST', '/clearCache')],
             'SuperReadController': QUERY, 'SuperWriteController': SAVE + UPDATE + DELETE + COPY, 'SuperPoiController': SAVE + UPDATE + DELETE + QUERY + COPY}


def paths(args):
    if not args:
        return ['']
    found = re.findall(r'"([^"]*)"', re.sub(r'(produces|consumes|name|headers|params)\s*=\s*(\{[^}]*\}|"[^"]*"|[\w.]+)', '', args))
    return found or ['']


def norm(path):
    path = re.sub(r'\{[^}]*\}', '{}', '/' + path.strip('/'))
    return re.sub(r'/+', '/', path).rstrip('/') or '/'


declared = collections.defaultdict(set)
for root, _, files in os.walk(os.path.join(src, 'luohuo-cloud')):
    mod = next((m for part in root.split(os.sep) for k, m in MODULES.items() if part == k), None)
    # src/test and the flex/test packages hold the original's own test controllers
    if not mod or '/src/test/' in root or '/flex/test' in root or '/generator/config' in root:
        continue
    for name in files:
        if not name.endswith('.java'):
            continue
        text = open(os.path.join(root, name), encoding='utf-8', errors='ignore').read()
        text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
        text = re.sub(r'^\s*//.*$', '', text, flags=re.M)
        if '@RestController' not in text and '@Controller' not in text:
            continue
        cm = re.search(r'\bclass\s+\w+[^{]*\{', text)
        if not cm:
            continue
        head, body = text[:cm.start()], text[cm.start():]
        rm = re.findall(r'@RequestMapping\s*(\((?:[^()]|\([^()]*\))*\))?', head)
        bases = paths(rm[-1]) if rm else ['']
        for base in bases:
            for key, inherited in INHERITED.items():
                if re.search(r'extends\s+' + key + r'\b', cm.group(0)):
                    for method, sub in inherited:
                        declared[mod].add((method, norm(base + '/' + sub)))
            for kind, args in ANN.findall(body):
                methods = [METHODS[kind]] if kind in METHODS else (re.findall(r'RequestMethod\.(\w+)', args or '') or ['ANY'])
                for sub in paths(args):
                    for method in methods:
                        declared[mod].add((method, norm(base + '/' + sub)))

registered = set()
for name in os.listdir(logs):
    if name.endswith('.log'):
        for method, path in re.findall(r'"(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS) (/[^"]*)"\s*:', open(os.path.join(logs, name), errors='ignore').read()):
            registered.add((method, norm(path)))


def has(mod, method, path):
    for prefix in ('/api/%s' % mod, '/api', ''):
        target = norm(prefix + path)
        if method == 'ANY':
            if any(p == target for _, p in registered):
                return True
        elif (method, target) in registered:
            return True
    return False


total = covered = 0
for mod in sorted(declared):
    missing = sorted(e for e in declared[mod] if not has(mod, *e))
    total += len(declared[mod])
    covered += len(declared[mod]) - len(missing)
    print('== %s: %d declared, %d missing' % (mod, len(declared[mod]), len(missing)))
    for method, path in missing:
        print('   %-6s %s' % (method, path))
print('TOTAL %d declared, %d covered, %d missing; %d routes registered here' % (total, covered, total - covered, len(registered)))
