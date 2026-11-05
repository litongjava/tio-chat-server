#!/usr/bin/env bash
# Download the front-end code generated for every target, template type and popup type into <dir>/<target>-<type>-<popup>.
#   scripts/gen-web-samples.sh <dir>
cd "$(dirname "$0")"; source ./lib.sh; set -e
OUT=${1:?dir}; TS=$(date +%s%N | cut -c1-16); G=/api/generator/defGenTable
read TA UA < <(register_user "genweb$TS@example.com"); grant_admin "$UA"; TA=$(login_user "genweb$TS@example.com" | cut -d' ' -f1)
A="gweb_${TS: -8}"; TR="${A}_tree"; SB="${A}_item"
trap '$MYSQL -e "drop table if exists $A, $TR, $SB" 2>/dev/null; true' EXIT
$MYSQL -e "create table $A(id bigint primary key comment '主键', name varchar(64) not null comment '名称', status varchar(8) comment '状态 #${A^}StatusEnum{ON:1,\"启用\";OFF:0,\"停用\";}', kind varchar(8) comment '类型 @Echo(api = x, dictType = \"gweb_kind\")', amount decimal(10,2) comment '金额', enabled bit(1) default b'1' comment '启用', birthday date comment '生日', at datetime comment '时间', remark text comment '备注', create_time datetime, create_by bigint, update_time datetime, update_by bigint, is_del tinyint(1) not null default 0) comment '示例'" 2>/dev/null
$MYSQL -e "create table $TR(id bigint primary key, parent_id bigint, sort_value int, label varchar(64) comment '名称', create_time datetime, create_by bigint, update_time datetime, update_by bigint) comment '树'" 2>/dev/null
$MYSQL -e "create table $SB(id bigint primary key, main_id bigint, label varchar(64) comment '名称') comment '从表'" 2>/dev/null
api POST $G/importTable "$TA" "{\"tableNames\":[\"$A\",\"$TR\",\"$SB\"]}" >/dev/null
tid() { api POST $G/query "$TA" "{\"name\":\"$1\"}" | json "[t['id'] for t in d['data'] if t['name']=='$1'][0]"; }
ID=$(tid "$A"); IDT=$(tid "$TR"); IDS=$(tid "$SB")
api PUT $G "$TA" "{\"tableIdList\":[\"$ID\",\"$IDT\",\"$IDS\"],\"serviceName\":\"base\",\"moduleName\":\"gen\",\"parent\":\"demo.app\",\"plusApplicationName\":\"devOperation\",\"plusModuleName\":\"gentest\",\"menuApplicationId\":\"1\",\"popupType\":\"MODAL\"}" >/dev/null
api PUT $G "$TA" "{\"id\":\"$ID\",\"entityName\":\"GenMain\"}" >/dev/null
api PUT $G "$TA" "{\"id\":\"$IDT\",\"entityName\":\"GenTree\",\"tplType\":\"TREE\",\"entitySuperClass\":\"TREE_ENTITY\",\"treeName\":\"label\",\"treeParentId\":\"0\"}" >/dev/null
api PUT $G "$TA" "{\"id\":\"$IDS\",\"entityName\":\"GenItem\"}" >/dev/null
fetch() { # fetch <id> <target> <name>
  mkdir -p "$OUT/$3"; curl -s -o "$OUT/$3.zip" "$HOST$G/downloadZip?ids=$1&template=$2" -H "token: $TA"
  (cd "$OUT/$3" && unzip -qo "../$3.zip"); rm "$OUT/$3.zip"; echo "$3: $(find "$OUT/$3" -type f | wc -l) files"
}
for target in WEB_PLUS WEB_SOYBEAN WEB_VBEN5; do
  for popup in MODAL DRAWER JUMP; do
    api PUT $G "$TA" "{\"id\":\"$ID\",\"tplType\":\"SIMPLE\",\"popupType\":\"$popup\"}" >/dev/null
    fetch "$ID" $target "$target-simple-$popup"
    api PUT $G "$TA" "{\"id\":\"$ID\",\"tplType\":\"MAIN_SUB\",\"subId\":\"$IDS\",\"subJavaFieldName\":\"mainId\",\"popupType\":\"$popup\"}" >/dev/null
    fetch "$ID" $target "$target-mainsub-$popup"
  done
  fetch "$IDT" $target "$target-tree-MODAL"
done
api DELETE $G "$TA" "[\"$ID\",\"$IDT\",\"$IDS\"]" >/dev/null
