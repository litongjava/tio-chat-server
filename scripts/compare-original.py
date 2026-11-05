#!/usr/bin/env python3
"""Sends the same requests to the original backend and to this one and compares the answers.

usage: compare-original.py <HuLa-Server dir> <original url> <original token> <this url> <this token> <output json> [writes]
With "writes" POST and PUT calls are sent too, with ids that do not exist; both databases get the new rows.
The two databases hold different rows, so values are not compared: only whether a call succeeds and the fields
of the first record. The original writes null fields into its JSON, so a missing field here is a real difference.
"""
import json, re, sys, urllib.parse, urllib.request, urllib.error

import hula_endpoints

src, orig, orig_token, here, here_token, out = sys.argv[1:7]
WRITES = len(sys.argv) > 7 and sys.argv[7] == 'writes'
endpoints, classes, all_fields = hula_endpoints.load(src)
SKIP = re.compile(r'updateDefApp|onlineUsers|logout|/kick|offline|clear|/stream|/notify|portal|/ws/|sse|upload|/export|/import|download|generatorCode|defGenProject/generate|delete|remove|exit|/test|/del$|switchTenant|password|Password|anyTenant/(login|register|refresh)|sendEmail|sendSms|deleteAll')
ORIG_HEADERS = {'tenantId': '1', 'userId': '61170828529941', 'uid': '1', 'systemType': '1'}


def call(base, token, method, path, body, extra=None):
    data = None if body is None else json.dumps(body).encode()
    headers = {'Content-Type': 'application/json', 'token': token}
    headers.update(extra or {})
    request = urllib.request.Request(base + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, response.read().decode('utf-8', 'replace')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'replace')
    except Exception as e:
        return 0, str(e)


def sample(name, tp):
    low = name.lower()
    if tp in ('Long', 'long'):
        return 999999
    if tp in ('Integer', 'int', 'Short', 'Byte'):
        return 10 if 'size' in low else 1
    if tp in ('Boolean', 'boolean'):
        return False
    if tp.startswith(('List', 'Set', 'Collection')):
        return []
    if tp == 'LocalDateTime':
        return '2026-01-01 00:00:00'
    if tp == 'LocalDate':
        return '2026-01-01'
    return 'x'


def first_record(data):
    if isinstance(data, dict):
        for key in ('records', 'list'):
            if isinstance(data.get(key), list):
                return data[key][0] if data[key] else None
        return data
    if isinstance(data, list):
        return data[0] if data else None
    return None


def parse(text):
    try:
        doc = json.loads(text)
    except Exception:
        return None
    return doc if isinstance(doc, dict) else None


def real_id(base, token, mod_path, prefix, extra):
    status, text = call(base, token, 'POST', mod_path + prefix + '/page', {'size': 1, 'current': 1, 'model': {}}, extra)
    doc = parse(text)
    record = first_record(doc.get('data')) if doc else None
    return record.get('id') if isinstance(record, dict) else None


result = {'both_ok': 0, 'both_fail': 0, 'orig_only_ok': [], 'here_only_ok': [], 'fields': [], 'orig_500': [], 'skipped': 0}
for (method, mod, path), e in sorted(endpoints.items(), key=lambda kv: (kv[0][1], kv[0][2])):
    key = '%s /%s%s' % (method, mod, path)
    if SKIP.search(path) or method == 'DELETE':
        result['skipped'] += 1
        continue
    if not WRITES and (method == 'PUT' or method == 'POST' and not re.search(r'/(page|query|tree|findByIds|list|search|all|check|detail)\b', path)):
        # writes would change the databases differently, only reads are compared
        result['skipped'] += 1
        continue
    prefix = re.sub(r'/(\{[^}]*\}|detail)$', '', path)
    query = {}
    for name, tp, required in e['params']:
        if required or method == 'GET':
            query[name] = sample(name, tp)
    payload = None
    if e['body']:
        cls, is_list = e['body']
        payload = [999999] if is_list else {f: sample(f, t) for f, t, req in all_fields(cls) if req} if cls in classes else {}
    elif method == 'POST':
        payload = {'size': 5, 'current': 1, 'model': {}} if path.endswith('/page') else [999999] if path.endswith('findByIds') else {}
    answers = []
    for base, token, mod_path, extra in ((orig, orig_token, '', ORIG_HEADERS), (here, here_token, '/api/' + mod, None)):
        rid = 999999
        if method == 'GET' and ('{' in path or path.endswith('/detail')):
            rid = real_id(base, token, mod_path, prefix, extra) or 999999
        full = re.sub(r'\{[^}]*\}', str(rid), path)
        q = dict(query)
        if path.endswith('/detail'):
            q['id'] = rid
        qs = urllib.parse.urlencode({k: v for k, v in q.items() if not isinstance(v, list)})
        status, text = call(base, token, method, mod_path + full + ('?' + qs if qs else ''), payload, extra)
        answers.append((status, parse(text), text))
    (status_a, doc_a, text_a), (status_b, doc_b, text_b) = answers
    ok_a = bool(doc_a) and doc_a.get('code') == 200
    ok_b = bool(doc_b) and doc_b.get('code') == 200
    if doc_a and doc_a.get('code') in (500, -1, -4) or status_a >= 500:
        result['orig_500'].append({'endpoint': key, 'text': text_a[:140], 'here': doc_b.get('code') if doc_b else status_b})
    if ok_a and ok_b:
        result['both_ok'] += 1
        rec_a, rec_b = first_record(doc_a.get('data')), first_record(doc_b.get('data'))
        if isinstance(rec_a, dict) and isinstance(rec_b, dict):
            only_a, only_b = sorted(set(rec_a) - set(rec_b)), sorted(set(rec_b) - set(rec_a))
            if only_a or only_b:
                result['fields'].append({'endpoint': key, 'missing_here': only_a, 'extra_here': only_b})
        elif type(doc_a.get('data')) is not type(doc_b.get('data')) and doc_a.get('data') is not None and doc_b.get('data') is not None:
            result['fields'].append({'endpoint': key, 'type': [type(doc_a.get('data')).__name__, type(doc_b.get('data')).__name__]})
    elif ok_a:
        result['orig_only_ok'].append({'endpoint': key, 'here': text_b[:160], 'orig': text_a[:80]})
    elif ok_b:
        result['here_only_ok'].append({'endpoint': key, 'orig': text_a[:160]})
    else:
        result['both_fail'] += 1
json.dump(result, open(out, 'w'), ensure_ascii=False, indent=1)
print('both ok', result['both_ok'], 'both fail', result['both_fail'], 'only original ok', len(result['orig_only_ok']), 'only here ok', len(result['here_only_ok']),
      'field differences', len(result['fields']), 'original server errors', len(result['orig_500']), 'skipped', result['skipped'])
