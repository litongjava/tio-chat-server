#!/usr/bin/env bash
# Shared helpers for smoke tests. Requires server on :18760 and redis container "tio-chat-redis".
HOST=${HOST:-http://localhost:18760}
AUTH=$(printf 'luohuo_web_pro:luohuo_web_pro_secret' | base64)
REDIS="docker exec tio-chat-redis redis-cli"
json() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval(sys.argv[1]))" "$1"; }

# register_user <email>  -> registers (ignores "exists") and prints "token uid"
register_user() {
  local email=$1
  curl -s -XPOST "$HOST/api/oauth/anyTenant/sendEmailCode" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"templateCode\":\"REGISTER_EMAIL\"}" >/dev/null
  local code=$($REDIS get "luohuo:captcha:REGISTER_EMAIL:$email")
  curl -s -XPOST "$HOST/api/oauth/anyTenant/registerByEmail" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"code\":\"$code\",\"key\":\"REGISTER_EMAIL\",\"password\":\"123456\",\"confirmPassword\":\"123456\",\"nickName\":\"${email%%@*}\",\"systemType\":2}" >/dev/null
  login_user "$email" "${2:-PC}" "${3:-c1}"
}

# login_user <email> [deviceType] [clientId] -> prints "token uid"
login_user() {
  curl -s -XPOST "$HOST/api/oauth/anyTenant/login" -H 'Content-Type: application/json' -H "Authorization: $AUTH" \
    -d "{\"account\":\"$1\",\"password\":\"123456\",\"systemType\":2,\"deviceType\":\"${2:-PC}\",\"clientId\":\"${3:-c1}\"}" \
    | json "d['data']['token'] + ' ' + str(d['data']['uid'])"
}

api() { # api <METHOD> <path> <token> [json]
  curl -s -X"$1" "$HOST$2" -H 'Content-Type: application/json' -H "token: $3" ${4:+-d "$4"}
}

MYSQL="docker exec tio-chat-mysql mysql -uroot -probot_123456# --default-character-set=utf8mb4 -N tio_chat"
# grant_admin <uid> -> gives the user the TENANT_ADMIN role (the role check uses the im uid as employee id)
grant_admin() {
  $MYSQL -e "insert into base_employee_role_rel(id, role_id, employee_id, create_by, create_time, is_del, tenant_id)
    select $(date +%s%N | cut -c1-18), id, $1, 1, now(), 0, 1 from base_role where code='TENANT_ADMIN'
    and not exists (select 1 from base_employee_role_rel r where r.employee_id=$1 and r.role_id=base_role.id and r.is_del=0)" 2>/dev/null
}
