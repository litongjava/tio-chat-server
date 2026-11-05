#!/usr/bin/env python3
"""Parses the controllers of the original backend: endpoints, result classes and their fields."""
import os, re


def load(src):
    MODULES = {'luohuo-oauth': 'oauth', 'luohuo-im': 'im', 'luohuo-base': 'base', 'luohuo-system': 'system', 'luohuo-ai': 'ai', 'luohuo-generator': 'generator'}
    ANN = re.compile(r'@(Get|Post|Put|Delete|Patch|Request)Mapping\s*(\((?:[^()]|\([^()]*\))*\))?')
    METHODS = {'Get': 'GET', 'Post': 'POST', 'Put': 'PUT', 'Delete': 'DELETE', 'Patch': 'PATCH'}
    SKIP = re.compile(r'logout|/kick|offline|clear|/stream|/notify|portal|/ws/|sse|upload|/export|/import|download|generatorCode|defGenProject/generate|delete-by-admin|exit|removeAll|/test')

    classes = {}
    sources = []
    for top in ('luohuo-cloud', 'luohuo-util'):
        for root, _, files in os.walk(os.path.join(src, top)):
            if '/src/test/' in root:
                continue
            for name in files:
                if name.endswith('.java'):
                    path = os.path.join(root, name)
                    text = open(path, encoding='utf-8', errors='ignore').read()
                    text = re.sub(r'/\*.*?\*/', '', text, flags=re.S)
                    text = re.sub(r'^\s*//.*$', '', text, flags=re.M)
                    sources.append((root, name, text))
                    m = re.search(r'\bclass\s+(\w+)(?:<[^{]*?>)?(?:\s+extends\s+(\w+))?[^{]*\{', text)
                    if m:
                        body = text[m.end():]
                        fields = []
                        for f in re.finditer(r'((?:@[\w.]+(?:\((?:[^()]|\([^()]*\))*\))?\s*)*)private\s+(?!static)(?:final\s+)?([\w<>\[\], .?]+?)\s+(\w+)\s*(?:=[^;]*)?;', body):
                            if 'JsonIgnore' not in f.group(1) and 'TableField(exist' not in f.group(1):
                                required = bool(re.search(r'@Not(Null|Blank|Empty)', f.group(1)))
                                fields.append((f.group(3), f.group(2).strip(), required))
                        classes[m.group(1)] = (fields, m.group(2))


    def all_fields(name, seen=None):
        seen = seen or set()
        if name not in classes or name in seen:
            return []
        seen.add(name)
        fields, parent = classes[name]
        return list(fields) + all_fields(parent, seen)


    def paths(args):
        if not args:
            return ['']
        found = re.findall(r'"([^"]*)"', re.sub(r'(produces|consumes|name|headers|params)\s*=\s*(\{[^}]*\}|"[^"]*"|[\w.]+)', '', args))
        return found or ['']


    def norm(path):
        return re.sub(r'/+', '/', '/' + path.strip('/')).rstrip('/') or '/'


    endpoints = {}
    SUPER = {'SuperController', 'SuperCacheController', 'SuperReadController', 'SuperWriteController', 'SuperPoiController'}
    for root, name, text in sources:
        mod = next((m for part in root.split(os.sep) for k, m in MODULES.items() if part == k), None)
        if not mod or '/flex/test' in root or '/generator/config' in root or ('@RestController' not in text and '@Controller' not in text):
            continue
        cm = re.search(r'\bclass\s+\w+[^{]*\{', text)
        if not cm:
            continue
        head, body = text[:cm.start()], text[cm.start():]
        rm = re.findall(r'@RequestMapping\s*(\((?:[^()]|\([^()]*\))*\))?', head)
        bases = paths(rm[-1]) if rm else ['']
        sup = re.search(r'extends\s+(\w+)\s*<([^{]*)>', cm.group(0))
        for b in bases:
            if sup and sup.group(1) in SUPER:
                parts = [p.strip() for p in re.split(r',(?![^<]*>)', sup.group(2))]
                result = parts[-1] if len(parts) >= 7 else parts[2] if len(parts) > 2 else None
                read = sup.group(1) != 'SuperWriteController'
                write = sup.group(1) != 'SuperReadController'
                if read:
                    for m, p, shape in (('POST', '/page', 'page'), ('POST', '/query', 'list'), ('GET', '/{id}', 'one'), ('GET', '/detail', 'one'), ('POST', '/findByIds', 'list')):
                        endpoints[(m, mod, norm(b + p))] = {'result': result, 'params': [], 'body': None, 'inherited': True}
                if write:
                    for m, p in (('POST', ''), ('PUT', ''), ('DELETE', ''), ('POST', '/copy')):
                        endpoints[(m, mod, norm(b + p))] = {'result': None, 'params': [], 'body': None, 'inherited': True}
            for a in ANN.finditer(body):
                kind, args = a.group(1), a.group(2)
                rest = body[a.end():]
                sig = re.search(r'public\s+([^({;=]+?)\s+\w+\s*\(((?:[^()]|\((?:[^()]|\([^()]*\))*\))*)\)', rest)
                if not sig or sig.start() > 600:
                    continue
                ret = re.findall(r'\b([A-Z]\w+)\b', sig.group(1))
                result = next((r for r in ret if r in classes and r not in ('R', 'IPage', 'List', 'Page', 'CursorPageBaseResp', 'PageBaseResp', 'PageResult', 'Map', 'Set', 'Option')), None)
                params, bodycls = [], None
                for p in re.split(r',(?![^<(]*[>)])', sig.group(2)):
                    nm = re.search(r'(\w+)\s*$', p.strip())
                    if not nm or 'hidden = true' in p:
                        continue
                    tp = re.findall(r'\b([A-Z]\w+)\b', re.sub(r'@\w+(\((?:[^()]|\([^()]*\))*\))?', '', p))
                    if '@RequestBody' in p:
                        bodycls = (tp[-1] if tp else None, 'List' in tp)
                    elif '@PathVariable' in p:
                        continue
                    elif tp and tp[-1] in classes:
                        params.extend(all_fields(tp[-1]))
                    else:
                        rn = re.search(r'@RequestParam\s*\((?:value\s*=\s*)?"(\w+)"', p)
                        params.append((rn.group(1) if rn else nm.group(1), tp[-1] if tp else 'String', '@RequestParam' in p and 'required = false' not in p))
                methods = [METHODS[kind]] if kind in METHODS else (re.findall(r'RequestMethod\.(\w+)', args or '') or ['POST'])
                for sub in paths(args):
                    for m in methods:
                        endpoints[(m, mod, norm(b + '/' + sub))] = {'result': result, 'params': params, 'body': bodycls, 'inherited': False}
    return endpoints, classes, all_fields
