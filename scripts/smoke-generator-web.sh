#!/usr/bin/env bash
# Generated front-end code of every target, template type and popup type goes through the Vue and TypeScript compilers.
# WEB_OUT=<dir> keeps the code, e.g. to compile it inside a real admin project.
cd "$(dirname "$0")"; source ./lib.sh; set -e
ROOT=$(cd .. && pwd); TS=$(date +%s%N | cut -c1-16); WORK=$(mktemp -d); NM=${NM:-$ROOT/../HuLa/node_modules}; NODE=${NODE:-node}; G=/api/generator/defGenTable
PNPM="$NM/.pnpm"; MODS=$(dirname "$(dirname "$(ls -d "$PNPM"/@vue+compiler-sfc*/node_modules/@vue/compiler-sfc | tail -1)")"); TSM=$(ls -d "$PNPM"/typescript@*/node_modules | tail -1)
mkdir -p "$WORK/mods/node_modules"; ln -s "$MODS/@vue" "$WORK/mods/node_modules/@vue"; ln -s "$TSM/typescript" "$WORK/mods/node_modules/typescript"
read T U < <(register_user "genweb$TS@example.com"); grant_admin "$U"; T=$(login_user "genweb$TS@example.com" | cut -d' ' -f1)
A="gweb_${TS: -8}"; TR="${A}_tree"; SB="${A}_item"
trap '$MYSQL -e "drop table if exists $A, $TR, $SB" 2>/dev/null; rm -rf "$WORK"; true' EXIT
$MYSQL -e "create table $A(id bigint primary key comment '主键', name varchar(64) not null comment '名称', status varchar(8) comment '状态 #${A^}StatusEnum{ON:1,\"启用\";OFF:0,\"停用\";}', kind varchar(8) comment '类型 @Echo(api = x, dictType = \"gweb_kind\")[a-甲 b-乙]', amount decimal(10,2) comment '金额', enabled bit(1) default b'1' comment '启用', birthday date comment '生日', at_time datetime comment '时间', remark text comment '备注', create_time datetime, create_by bigint, update_time datetime, update_by bigint, is_del tinyint(1) not null default 0) comment '示例;说明'" 2>/dev/null
$MYSQL -e "create table $TR(id bigint primary key, parent_id bigint, sort_value int, label varchar(64) comment '名称', create_time datetime, create_by bigint, update_time datetime, update_by bigint) comment '树'" 2>/dev/null
$MYSQL -e "create table $SB(id bigint primary key, main_id bigint, label varchar(64) comment '名称', cnt int comment '数量', enabled bit(1) comment '启用') comment '从表'" 2>/dev/null
api POST $G/importTable "$T" "{\"tableNames\":[\"$A\",\"$TR\",\"$SB\"]}" >/dev/null
tid() { api POST $G/query "$T" "{\"name\":\"$1\"}" | json "[t['id'] for t in d['data'] if t['name']=='$1'][0]"; }
ID=$(tid "$A"); IDT=$(tid "$TR"); IDS=$(tid "$SB")
api PUT $G "$T" "{\"tableIdList\":[\"$ID\",\"$IDT\",\"$IDS\"],\"serviceName\":\"demo\",\"moduleName\":\"gen\",\"parent\":\"demo.app\",\"plusApplicationName\":\"devOperation\",\"plusModuleName\":\"demo\",\"menuApplicationId\":\"1\",\"popupType\":\"MODAL\"}" >/dev/null
api PUT $G "$T" "{\"id\":\"$IDT\",\"tplType\":\"TREE\",\"entitySuperClass\":\"TREE_ENTITY\",\"treeName\":\"label\",\"treeParentId\":\"0\"}" >/dev/null
api POST "/api/generator/defGenTableColumn/page" "$T" "{\"size\":50,\"model\":{\"tableId\":\"$ID\"}}" >/dev/null
rc=0
check() { # check <name> <id> <template>
  local d="$WORK/$1"; mkdir -p "$d"
  curl -s -o "$d.zip" "$HOST$G/downloadZip?ids=$2&template=$3" -H "token: $T"
  (cd "$d" && unzip -qo "$d.zip")
  [ -n "$WEB_OUT" ] && { mkdir -p "$WEB_OUT/$1"; cp -r "$d/." "$WEB_OUT/$1/"; }
  printf '%-28s ' "$1"; "$NODE" check-web-code.mjs "$d" "$WORK/mods" || rc=1
}
set +e
for tpl in WEB_PLUS WEB_SOYBEAN WEB_VBEN5; do
  check "$tpl-simple-modal" "$ID" $tpl
  check "$tpl-tree" "$IDT" $tpl
done
api PUT $G "$T" "{\"id\":\"$ID\",\"popupType\":\"DRAWER\"}" >/dev/null; check WEB_PLUS-simple-drawer "$ID" WEB_PLUS
api PUT $G "$T" "{\"id\":\"$ID\",\"popupType\":\"JUMP\"}" >/dev/null; for tpl in WEB_PLUS WEB_SOYBEAN WEB_VBEN5; do check "$tpl-simple-jump" "$ID" $tpl; done
api PUT $G "$T" "{\"id\":\"$ID\",\"tplType\":\"MAIN_SUB\",\"subId\":\"$IDS\",\"subJavaFieldName\":\"mainId\",\"popupType\":\"MODAL\"}" >/dev/null
for tpl in WEB_PLUS WEB_SOYBEAN WEB_VBEN5; do check "$tpl-main-sub-modal" "$ID" $tpl; done
api PUT $G "$T" "{\"id\":\"$ID\",\"popupType\":\"JUMP\"}" >/dev/null; check WEB_PLUS-main-sub-jump "$ID" WEB_PLUS
api DELETE $G "$T" "[\"$ID\",\"$IDT\",\"$IDS\"]" >/dev/null
exit $rc
