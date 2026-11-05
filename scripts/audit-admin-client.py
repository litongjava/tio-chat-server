#!/usr/bin/env python3
"""Check that every API call of the HuLa-Admin front end has a route here.

usage: audit-admin-client.py <HuLa-Admin dir> <service log dir>
"""
import glob, os, re, sys

admin, logs = sys.argv[1], sys.argv[2]
routes = set()
for name in os.listdir(logs):
    if name.endswith('.log'):
        for method, path in re.findall(r'"(GET|POST|PUT|DELETE|PATCH) (/[^"]*)"\s*:', open(os.path.join(logs, name), errors='ignore').read()):
            routes.add((method, re.sub(r'\{[^}]*\}', '{}', path)))
ok, missing = 0, []
for file in glob.glob(os.path.join(admin, 'src/api/*.ts')):
    text = open(file).read()
    for call in re.finditer(r'request<[^(]*>\(\{(.*?)\n\s*\}\)', text, re.S):
        body = call.group(1)
        url = re.search(r"url:\s*[`'\"]([^`'\"]+)[`'\"]", body)
        module = re.search(r'RequestModule\.(\w+)', body)
        method = re.search(r"method:\s*['\"](\w+)", body)
        if not url or not module:
            continue
        path = '/api/%s%s' % (module.group(1).lower(), re.sub(r'\$\{[^}]*\}', '{}', url.group(1)).split('?')[0])
        key = ((method.group(1) if method else 'get').upper(), path)
        if key in routes:
            ok += 1
        else:
            missing.append(key)
print('covered', ok, 'missing', len(missing))
for method, path in sorted(set(missing)):
    print('  ', method, path)
sys.exit(1 if missing else 0)
