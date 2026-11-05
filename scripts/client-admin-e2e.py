#!/usr/bin/env python3
"""Drives the HuLa-Admin front end with dsb against this backend: logs in, opens every page, records API calls that fail.

usage: client-admin-e2e.py <admin url> <account> <password>
The account must have system_type=1, the TENANT_ADMIN role and a row in def_user_tenant_rel. See docs/verify-original.md.
"""
import json, os, subprocess, sys, time

url, account, password = sys.argv[1:4]
here = os.path.dirname(os.path.abspath(__file__))


def dsb(*args):
    return subprocess.run(['dsb', *args], capture_output=True, text=True, timeout=120).stdout.strip()


def js(code):
    return dsb('js', code, '--select', 'data.result', '--raw')


hook = '@' + os.path.join(here, 'AdminXhrHook.js')
dsb('run', 'go_to_url', '-p', 'url=%s/login' % url)
time.sleep(3)
js(hook)
dsb('run', 'input_text_by_selector', '-p', 'selector=input[type=text]', '-p', 'text=' + account)
dsb('run', 'input_text_by_selector', '-p', 'selector=input[type=password]', '-p', 'text=' + password)
dsb('run', 'click_element_by_selector', '-p', 'selector=button[type=button]', '-p', 'mode=mouse')
time.sleep(6)
router = "document.querySelector('#app').__vue_app__.config.globalProperties.$router"
routes = list(dict.fromkeys(json.loads(js("JSON.stringify(%s.getRoutes().map(r=>r.path).filter(p=>!/login|catchAll|^\\/$/.test(p)))" % router))))
failed = 0
for path in routes:
    js(hook)
    js("(async()=>{window.__bad=[];window.__calls=0;await %s.push('%s');return 1})()" % (router, path))
    time.sleep(3.5)
    page = json.loads(js("(()=>JSON.stringify({bad:window.__bad||[],calls:window.__calls,rows:document.querySelectorAll('tbody tr').length}))()"))
    failed += len(page['bad'])
    print(path, 'calls', page['calls'], 'rows', page['rows'], page['bad'])
print('api functions')
for name, value in json.loads(js('@' + os.path.join(here, 'AdminApiCalls.js'))).items():
    print(' ', name, '=>', value[:100])
print('failed page calls', failed)
sys.exit(1 if failed else 0)
