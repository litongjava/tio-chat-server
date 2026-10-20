#!/usr/bin/env bash
# Admin extension endpoints and oauth dictionary/resource endpoints.
cd "$(dirname "$0")"; source ./lib.sh; set -e
read T U < <(register_user "adm$(date +%s)@example.com")
api POST /api/base/defResource/tree "$T" '{}' | json "d['code'], len(d['data']), 'children' in d['data'][0]"
api POST /api/base/baseOrg/tree "$T" '{}' | json "d['code'], len(d['data'])"
api GET "/api/base/defDict/check?key=GLOBAL_SEX" "$T" | json "d['code'], d['data']"
api GET "/api/base/defUser/checkEmail?email=nobody@x.com" "$T" | json "d['code'], d['data']"
api POST /api/base/baseRole/pageMyRole "$T" '{"model":{"scopeType":"1","scope":"2","employeeId":1},"size":5,"current":1}' | json "d['code'], d['data']['total']"
api GET "/api/base/baseRole/resourceList?roleId=1" "$T" | json "d['code'], type(d['data']).__name__"
api POST /api/base/defUser/onlineUsers/page "$T" '{"model":{},"size":5,"current":1}' | json "d['code'], d['data']['total'] >= '1'"
api GET /api/base/defApplication/findApplicationResourceList "$T" | json "d['code'], len(d['data'])"
api GET /api/base/defTenant/all "$T" | json "d['code'], len(d['data'])"
api POST /api/oauth/anyUser/dict/findDictMapByType "$T" '[{"type":"GLOBAL_SEX"}]' | json "d['code'], d['data']['GLOBAL_SEX']"
api POST /api/oauth/anyTenant/enums/findEnumMapByType "$T" '[{"type":"HttpMethod"},{"type":"LoginStatusEnum"}]' | json "d['code'], {k: len(v) for k, v in d['data'].items()}"
api POST /api/oauth/anyUser/parameter/findParamMapByKey "$T" '["LoginPolicy"]' | json "d['code'], d['data']"
api GET "/api/oauth/anyone/visible/resource?employeeId=1" "$T" | json "d['code'], len(d['data']['routerList']), d['data']['roleList']"
api GET "/api/oauth/anyTenant/gitee/authorize-url?redirect=http://x" "$T" | json "d['code'], d['data'][:40]"
api DELETE "/api/base/defLoginLog/clear?type=4" "$T" | json "d['code'], d['data']"
