#!/usr/bin/env bash
# Generic admin CRUD on def_parameter and paging on def_dict / def_user.
cd "$(dirname "$0")"; source ./lib.sh; set -e
read T U < <(register_user "admin$(date +%s)@example.com")
grant_admin "$U"
api POST /api/base/defDict/page "$T" '{"model":{"name":"性别"},"size":3,"current":1}' | json "d['code'], d['data']['total'], [(r['key'], r['name']) for r in d['data']['records']]"
api POST /api/base/defUser/page "$T" '{"model":{},"extra":{"createTime_st":"2020-01-01"},"size":2,"current":1,"sort":"createTime","order":"descending"}' | json "d['code'], d['data']['total'], 'password' in d['data']['records'][0]"
# deleted rows are kept (is_del=1) and key_ is unique, so every run uses its own key
PK="smoke_key_$(date +%s%N)"
ID=$(api POST /api/base/defParameter "$T" "{\"key\":\"$PK\",\"value\":\"v1\",\"name\":\"smoke\",\"paramType\":\"10\",\"state\":true}" | json "d['data']['id']"); echo created=$ID
api GET /api/base/defParameter/$ID "$T" | json "d['code'], d['data']['key'], d['data']['value']"
api PUT /api/base/defParameter "$T" "{\"id\":$ID,\"value\":\"v2\"}" | json "d['code'], d['data']['value']"
api POST "/api/base/defParameter/copy?id=$ID" "$T" | json "d['code'], d['msg']"
api POST /api/base/defParameter/query "$T" "{\"key\":\"$PK\"}" | json "d['code'], len(d['data'])"
api POST /api/base/defParameter/findByIds "$T" "[$ID]" | json "d['code'], len(d['data'])"
api DELETE /api/base/defParameter "$T" "[$ID]" | json "d['code'], d['data']"
api GET /api/base/defParameter/$ID "$T" | json "d['code'], d['data']"
