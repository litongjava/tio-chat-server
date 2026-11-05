#!/usr/bin/env python3
"""Call every endpoint declared by the original backend and compare the responses with its result classes.

usage: audit-parity.py <HuLa-Server dir> <admin token> <uid> <output json> [base url]
Path variables use real ids from the page endpoints for GET and an id that does not exist for writes. Reports:
- server errors (HTTP 5xx or code 500) with the request, these are bugs
- routes that answer 404
- fields of the result class missing from a non empty response, except the checked differences in KNOWN below
"""
import json, os, re, sys, urllib.request, urllib.error, collections, time

src, token, uid, out = sys.argv[1:5]
base = sys.argv[5] if len(sys.argv) > 5 else 'http://localhost:18760'
MODULES = {'luohuo-oauth': 'oauth', 'luohuo-im': 'im', 'luohuo-base': 'base', 'luohuo-system': 'system', 'luohuo-ai': 'ai', 'luohuo-generator': 'generator'}
ANN = re.compile(r'@(Get|Post|Put|Delete|Patch|Request)Mapping\s*(\((?:[^()]|\([^()]*\))*\))?')
METHODS = {'Get': 'GET', 'Post': 'POST', 'Put': 'PUT', 'Delete': 'DELETE', 'Patch': 'PATCH'}
SKIP = re.compile(r'logout|/kick|offline|clear|/stream|/notify|portal|/ws/|sse|upload|/export|/import|download|generatorCode|defGenProject/generate|delete-by-admin|exit|removeAll|/test')

# Fields of a result class the original backend does not fill on these endpoints either, or that its tables do not have.
ALL = None
KNOWN = {
    # inner classes of the response are read as fields of the outer class; the nested values are there
    'AdminStatsResp': ALL, 'ChatMessageResp': ALL, 'FriendCheckResp': ALL,
    # the original only sets name and comment on this list
    'DefGenTable': ALL,
    # the entity has columns that the install scripts of the original do not create
    'DefTenantResultVO': ALL, 'DefDatasourceConfigResultVO': {'dbPrefix'}, 'BaseEmployeeResultVO': {'isDefault'},
    # null in the original: no code fills them on these endpoints
    'AiChatRoleRespVO': {'model', 'modelName'}, 'MemberResp': {'roleId'},
    # filled only by other endpoints: operation log detail, msg get, resource detail, my applications
    'BaseOperationLogResultVO': {'exDetail', 'params', 'result'}, 'ExtendMsgResultVO': {'recipientList'},
    'DefResourceResultVO': {'resourceApiList'}, 'DefApplicationResultVO': {'expirationTime', 'state'},
    # logic delete column, the client does not read it
    'AiChatConversationDO': {'deleted'},
}

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


def call(method, path, body=None):
    url = base + path
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url, data=data, method=method, headers={'Content-Type': 'application/json', 'token': token})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, r.read().decode('utf-8', 'replace')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'replace')
    except Exception as e:
        return 0, str(e)


def sample(name, tp):
    low = name.lower()
    if tp in ('Long', 'long'):
        return int(uid) if low in ('uid', 'userid', 'targetuid') else 999999
    if tp in ('Integer', 'int', 'Short', 'Byte'):
        return 10 if 'size' in low else 1
    if tp in ('Boolean', 'boolean'):
        return False
    if tp.startswith('List') or tp.startswith('Set') or tp.startswith('Collection'):
        return []
    if tp in ('LocalDateTime',):
        return '2026-01-01 00:00:00'
    if tp == 'LocalDate':
        return '2026-01-01'
    return 'x'


def records(data):
    if isinstance(data, dict):
        for key in ('records', 'list'):
            if isinstance(data.get(key), list):
                return data[key]
        return [data]
    return data if isinstance(data, list) else []


# real ids per resource prefix, from the page endpoints
ids = {}
for (m, mod, p), e in sorted(endpoints.items(), key=lambda kv: kv[0][2]):
    if p.endswith('/page') and m == 'POST' and not SKIP.search(p):
        status, text = call('POST', '/api/%s%s' % (mod, p), {'size': 5, 'current': 1, 'model': {}})
        try:
            rec = records(json.loads(text).get('data'))
            if rec and isinstance(rec[0], dict) and rec[0].get('id') is not None:
                ids[(mod, p[:-5])] = rec[0]['id']
        except Exception:
            pass

result = {'errors': [], 'missing_route': [], 'missing_fields': [], 'skipped': [], 'ok': 0, 'other': collections.Counter()}
for (m, mod, p), e in sorted(endpoints.items(), key=lambda kv: (kv[0][1], kv[0][2])):
    key = '%s /api/%s%s' % (m, mod, p)
    if SKIP.search(p):
        result['skipped'].append(key)
        continue
    prefix = re.sub(r'/(\{[^}]*\}|detail)$', '', p)
    rid = ids.get((mod, prefix), 999999) if m == 'GET' else 999999
    path = re.sub(r'\{[^}]*\}', str(rid), p)
    query, payload = {}, None
    for name, tp, required in e['params']:
        if required or m == 'GET':
            query[name] = sample(name, tp)
    if p.endswith('/detail'):
        query['id'] = rid
    if e['body']:
        cls, is_list = e['body']
        if is_list:
            payload = [999999]
        else:
            payload = {f: sample(f, t) for f, t, req in all_fields(cls) if req} if cls in classes else {}
    elif m in ('POST', 'PUT', 'DELETE'):
        payload = {'size': 5, 'current': 1, 'model': {}} if p.endswith('/page') else [999999] if m == 'DELETE' or p.endswith('findByIds') else {}
    qs = '&'.join('%s=%s' % (k, urllib.request.quote(str(v))) for k, v in query.items() if not isinstance(v, list))
    status, text = call(m, '/api/%s%s%s' % (mod, path, '?' + qs if qs else ''), payload)
    try:
        doc = json.loads(text)
    except Exception:
        doc = None
    code = doc.get('code') if isinstance(doc, dict) else None
    if status >= 500 or code == 500 or status == 0:
        result['errors'].append({'endpoint': key, 'path': path, 'query': qs, 'body': payload, 'status': status, 'text': text[:200]})
        continue
    if status == 404:
        result['missing_route'].append(key)
        continue
    result['ok'] += 1
    result['other'][str(code)] += 1
    if doc and e['result'] and code == 200:
        rec = records(doc.get('data'))
        if rec and isinstance(rec[0], dict):
            expected = {f for f, _, _ in all_fields(e['result'])}
            missing = set(expected) - set(rec[0].keys()) - {'echoMap', 'serialVersionUID'}
            if e['result'] in KNOWN:
                known = KNOWN[e['result']]
                missing = set() if known is ALL else missing - known
            missing = sorted(missing)
            if missing:
                result['missing_fields'].append({'endpoint': key, 'result': e['result'], 'missing': missing})

print('declared', len(endpoints), 'called', result['ok'] + len(result['errors']) + len(result['missing_route']), 'skipped', len(result['skipped']))
print('errors', len(result['errors']), 'missing routes', len(result['missing_route']), 'with missing fields', len(result['missing_fields']))
print('response codes', dict(result['other']))
json.dump(result, open(out, 'w'), ensure_ascii=False, indent=1)
