#!/usr/bin/env python3
"""Call every endpoint declared by the original backend and compare the responses with its result classes.

usage: audit-parity.py <HuLa-Server dir> <admin token> <uid> <output json> [base url]
Path variables use real ids from the page endpoints for GET and an id that does not exist for writes. Reports:
- server errors (HTTP 5xx or code 500) with the request, these are bugs
- routes that answer 404
- fields of the result class missing from a non empty response, except the checked differences in KNOWN below
"""
import json, os, re, sys, urllib.request, urllib.error, collections, time

import hula_endpoints

src, token, uid, out = sys.argv[1:5]
base = sys.argv[5] if len(sys.argv) > 5 else 'http://localhost:18760'
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

endpoints, classes, all_fields = hula_endpoints.load(src)
SKIP = re.compile(r'logout|/kick|offline|clear|/stream|/notify|portal|/ws/|sse|upload|/export|/import|download|generatorCode|defGenProject/generate|delete-by-admin|exit|removeAll|/test')


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
