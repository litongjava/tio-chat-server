#!/usr/bin/env bash
# Code generator: permissions, import and field sync, preview of every template, then the generated backend is
# compiled and called, and a generated project is compiled and started.
cd "$(dirname "$0")"; source ./lib.sh; set -e
ROOT=$(cd .. && pwd); TS=$(date +%s%N | cut -c1-16); WORK=$(mktemp -d); JAVA=${JAVA:-java}; JAVAC=${JAVAC:-javac}
JAR="$ROOT/tio-chat-generator/target/tio-chat-generator.jar"
G=/api/generator/defGenTable
read T U < <(register_user "gen$TS@example.com"); read TA UA < <(register_user "genadmin$TS@example.com"); grant_admin "$UA"
TA=$(login_user "genadmin$TS@example.com" | cut -d' ' -f1)
A="gdemo_${TS: -8}"; TR="${A}_tree"; SB="${A}_sub"
trap '$MYSQL -e "drop table if exists $A, $TR, $SB" 2>/dev/null; [ -n "$DEMO" ] && kill -9 $DEMO 2>/dev/null; [ -n "$PROJ" ] && kill -9 $PROJ 2>/dev/null; rm -rf "$WORK"' EXIT
$MYSQL -e "create table $A(id bigint primary key comment '主键', name varchar(64) not null comment '名称', status varchar(8) comment '状态 #${A^}StatusEnum{ON:1,\"启用\";OFF:0,\"停用\";}', amount decimal(10,2) comment '金额', enabled bit(1) default b'1' comment '启用', birthday date comment '生日', remark text comment '备注', create_time datetime, create_by bigint, update_time datetime, update_by bigint, is_del tinyint(1) not null default 0) comment '示例;说明'" 2>/dev/null
$MYSQL -e "create table $TR(id bigint primary key, parent_id bigint, sort_value int, label varchar(64) comment '名称', create_time datetime, create_by bigint, update_time datetime, update_by bigint) comment '树'" 2>/dev/null
$MYSQL -e "create table $SB(id bigint primary key, main_id bigint, label varchar(64) comment '名称') comment '从表'" 2>/dev/null

echo "== permissions"
api POST $G/page "$T" '{}' | json "d['code']"
curl -s -XPOST "$HOST$G/page" -H 'Content-Type: application/json' -d '{}' | json "d['code']"
curl -s -XPOST "$HOST/api/generator/defGenProject/anno/getProperties" | json "d['code']"
api POST /api/generator/defGenProject/anno/getProperties "$TA" | json "sorted(d['data'].keys())"

echo "== import"
api POST $G/selectTableList "$TA" '{"model":{}}' | json "sum(1 for t in d['data'] if t['name'].startswith('$A'))"
api POST $G/importCheck "$TA" "[\"$A\",\"$TR\",\"$SB\"]" | json "d['code'], d['data']"
api POST $G/importTable "$TA" "{\"tableNames\":[\"$A\",\"$TR\",\"$SB\"]}" | json "d['code']"
api POST $G/importCheck "$TA" "[\"$A\"]" | json "d['code'], d['msg'].replace('$A','T')"
api POST $G/importTable "$TA" "{\"tableNames\":[\"$A\"]}" | json "d['code']"
echo "rows after importing again: $($MYSQL -e "select count(*) from def_gen_table where name='$A' and is_del=0" 2>/dev/null)"
api POST $G/importTable "$TA" '{"tableNames":["no_such_table_x"]}' | json "d['code'], d['msg']"
tid() { api POST $G/query "$TA" "{\"name\":\"$1\"}" | json "[t['id'] for t in d['data'] if t['name']=='$1'][0]"; }
ID=$(tid "$A"); IDT=$(tid "$TR"); IDS=$(tid "$SB")
columns() { api POST /api/generator/defGenTableColumn/page "$TA" "{\"size\":50,\"sort\":\"sortValue\",\"order\":\"ascending\",\"model\":{\"tableId\":\"$1\"}}"; }
columns "$ID" | json "[(c['name'],c['javaType'],c['tsType'],c['component'],c['queryType'],bool(c['isEdit'])) for c in sorted(d['data']['records'],key=lambda c:c['sortValue'])]"
api GET "$G/detail?id=$ID" "$TA" | json "d['data']['tplType'], d['data']['popupType'], d['data']['entitySuperClass'], d['data']['swaggerComment'], d['data']['menuName']"

echo "== required configuration"
api POST "$G/previewCode?id=$ID&template=BACKEND" "$TA" | json "d['code'], d['msg'].replace('$A','T')"
api PUT $G "$TA" "{\"tableIdList\":[\"$ID\",\"$IDT\",\"$IDS\"],\"serviceName\":\"demo\",\"moduleName\":\"gen\",\"parent\":\"demo.app\",\"plusApplicationName\":\"devOperation\",\"plusModuleName\":\"demo\",\"menuApplicationId\":\"1\",\"popupType\":\"MODAL\"}" | json "d['code']"
api GET "$G/detail?id=$ID" "$TA" | json "d['data']['serviceName'], d['data']['entityName'].startswith('Gdemo'), d['data']['popupType']"
api PUT $G "$TA" "{\"id\":\"$IDT\",\"tplType\":\"TREE\",\"entitySuperClass\":\"TREE_ENTITY\",\"treeName\":\"label\",\"treeParentId\":\"0\"}" | json "d['code'], d['data']['tplType'], d['data']['entitySuperClass']"

echo "== preview: every target, template type and popup type"
check() { # check <id> <template>: number of rendered files and any render error
  api POST "$G/previewCode?id=$1&template=$2" "$TA" | python3 -c "
import sys,json
d=json.load(sys.stdin)['data']
bad=[k for k,v in d.items() if 'FreeMarker' in v or 'freemarker' in v or v.startswith('The following') or ' was null or missing' in v or 'can\'t compare' in v]
print('$2', len(d), 'errors:', [(k, d[k][:150]) for k in bad])"
}
for tpl in BACKEND WEB_PLUS WEB_SOYBEAN WEB_VBEN5; do check "$ID" $tpl; done
api PUT $G "$TA" "{\"id\":\"$ID\",\"popupType\":\"JUMP\"}" >/dev/null
for tpl in WEB_PLUS WEB_SOYBEAN WEB_VBEN5; do check "$ID" $tpl; done
api PUT $G "$TA" "{\"id\":\"$ID\",\"popupType\":\"DRAWER\"}" >/dev/null
check "$ID" WEB_PLUS
for tpl in BACKEND WEB_PLUS WEB_SOYBEAN WEB_VBEN5; do check "$IDT" $tpl; done
api PUT $G "$TA" "{\"id\":\"$ID\",\"tplType\":\"MAIN_SUB\",\"subId\":\"$IDS\",\"subJavaFieldName\":\"mainId\",\"popupType\":\"MODAL\"}" >/dev/null
for tpl in BACKEND WEB_PLUS; do check "$ID" $tpl; done
api PUT $G "$TA" "{\"id\":\"$ID\",\"popupType\":\"JUMP\"}" >/dev/null
check "$ID" WEB_PLUS
api PUT $G "$TA" "{\"id\":\"$ID\",\"tplType\":\"SIMPLE\",\"popupType\":\"MODAL\"}" >/dev/null
echo "bad template: $(api POST "$G/previewCode?id=$ID&template=NOPE" "$TA" | json "d['code'], d['msg']")"

echo "== generated backend compiles and works"
curl -s -o "$WORK/code.zip" "$HOST$G/downloadZip?ids=$ID&template=BACKEND" -H "token: $TA"
mkdir "$WORK/src" "$WORK/classes"; (cd "$WORK/src" && unzip -q ../code.zip && find . -type f | sed 's#.*/##' | sort | tr '\n' ' '); echo
ENT=$(api GET "$G/detail?id=$ID" "$TA" | json "d['data']['entityName']")
cat > "$WORK/DemoMain.java" <<JAVA
import nexus.io.tio.chat.boot.ChatApplication;
import nexus.io.tio.chat.boot.ChatModule;
import nexus.io.tio.http.server.router.HttpRequestRouter;
public class DemoMain {
  public static void main(String[] args) {
    ChatApplication.runService(DemoMain.class, args, "gen-demo", 18790, new ChatModule() {
      public String serviceName() { return "gen-demo"; }
      public void config(HttpRequestRouter router) { new demo.app.gen.config.route.${ENT}RouteConfig().config(router); }
    });
  }
}
JAVA
LOMBOK=$(ls ~/.m2/repository/org/projectlombok/lombok/*/lombok-*.jar | grep -v sources | tail -1)
"$JAVAC" -nowarn -cp "$JAR:$LOMBOK" -d "$WORK/classes" "$WORK/DemoMain.java" $(find "$WORK/src" -name '*.java') 2>&1 | grep -v "^Note" | head -20
echo "compiled classes: $(find "$WORK/classes" -name '*.class' | wc -l)"
"$JAVA" -cp "$WORK/classes:$JAR" DemoMain --nacos.discovery.enabled=false --server.port=18790 > "$ROOT/logs/services/gendemo.log" 2>&1 & DEMO=$!
for _ in $(seq 1 40); do (echo > /dev/tcp/127.0.0.1/18790) 2>/dev/null && break; sleep 1; done; sleep 2
D=http://localhost:18790/api/demo/$(python3 -c "s='$ENT';print(s[0].lower()+s[1:])")
call() { curl -s -X"$1" "$D$2" -H 'Content-Type: application/json' -H "token: $TA" ${3:+-d "$3"}; }
SAVED=$(call POST "" '{"name":"张三","status":"1","amount":12.5,"enabled":true,"birthday":"2000-01-02","remark":"r"}')
echo "$SAVED" | json "d['code'], d['data']['name'], str(d['data']['amount']), d['data']['enabled'], str(d['data']['birthday'])[:10]"
RID=$(echo "$SAVED" | json "d['data']['id']")
call PUT "" "{\"id\":\"$RID\",\"name\":\"李四\"}" | json "d['code'], d['data']['name'], d['data']['status']"
call GET "/detail?id=$RID" | json "d['code'], d['data']['name']"
call POST /page '{"model":{"name":"李"},"current":1,"size":5}' | json "d['code'], d['data']['total'], d['data']['records'][0]['name']"
call POST /page '{"model":{"name":"nobody"}}' | json "d['code'], d['data']['total']"
call DELETE "" "[\"$RID\"]" | json "d['code'], d['data']"
call GET "/detail?id=$RID" | json "d['code'], d['data']"
echo "is_del after delete: $($MYSQL -e "select is_del from $A where id=$RID" 2>/dev/null)"
kill -9 $DEMO; DEMO=
api PUT $G "$TA" "{\"id\":\"$IDT\",\"isLombok\":false,\"childPackageName\":\"sub.pkg\"}" >/dev/null
curl -s -o "$WORK/tree.zip" "$HOST$G/downloadZip?ids=$IDT&template=BACKEND" -H "token: $TA"
mkdir "$WORK/tree" "$WORK/treec"; (cd "$WORK/tree" && unzip -q ../tree.zip)
"$JAVAC" -nowarn -proc:none -cp "$JAR" -d "$WORK/treec" $(find "$WORK/tree" -name '*.java') 2>&1 | grep -v "^Note" | head -10
echo "without lombok, in a child package: $(find "$WORK/treec" -name '*.class' | wc -l) classes"

echo "== field sync"
$MYSQL -e "alter table $A add column extra_note varchar(32) comment '附加', drop column remark" 2>/dev/null
api POST "$G/syncField?id=$ID" "$TA" | json "d['code']"
columns "$ID" | json "sorted(c['name'] for c in d['data']['records'] if c['name'] in ('extra_note','remark'))"
CID=$($MYSQL -e "select id from def_gen_table_column where table_id=$ID and name='name' and is_del=0" 2>/dev/null)
api PUT /api/generator/defGenTableColumn "$TA" "{\"id\":\"$CID\",\"swaggerComment\":\"改过的\"}" | json "d['data']['swaggerComment']"
api POST "$G/syncField?id=$ID" "$TA" >/dev/null
echo "kept by table sync: $($MYSQL -e "select swagger_comment from def_gen_table_column where id=$CID" 2>/dev/null)"
api POST "/api/generator/defGenTableColumn/syncField?tableId=$ID&id=$CID" "$TA" | json "d['code']"
echo "reset by column sync: $($MYSQL -e "select swagger_comment from def_gen_table_column where id=$CID" 2>/dev/null)"

echo "== data source"
DS=$(date +%s%N | cut -c1-18)
$MYSQL -e "insert into def_datasource_config(id,name,username,password,url,driver_class_name,create_by,create_time,update_time,tenant_id) values ($DS,'x','u','p','jdbc:h2:mem:x','org.h2.Driver',1,now(),now(),1)" 2>/dev/null
api POST $G/selectTableList "$TA" "{\"model\":{\"dsId\":\"$DS\"}}" | json "d['code'], d['msg']"
$MYSQL -e "update def_datasource_config set is_del=1 where id=$DS" 2>/dev/null

echo "== other endpoints"
api GET $G/getFieldTemplate "$TA" | json "d['code'], len(d['data'])"
api GET $G/getDefFileOverrideStrategy "$TA" | json "d['data']['entity'], d['data']['service'], d['data']['api'], d['data']['index']"
api POST $G/findTableList "$TA" "[\"$ID\",\"$IDT\"]" | json "d['code'], len(d['data']), d['data'][0]['tplType']"
api GET "$G/$ID" "$TA" | json "d['code'], d['data']['entityName'] == '$ENT'"
api POST "$G/copy" "$TA" "{\"id\":\"$IDS\"}" | json "d['code'], d['data']['name'] == '$SB'"
echo "-- write to a directory, then the override strategies"
api POST "$G/generatorCode" "$TA" "{\"ids\":[\"$ID\"],\"template\":\"BACKEND\"}" | json "d['code'], d['msg'][:12]"
OUT="$WORK/out"
api PUT $G "$TA" "{\"id\":\"$ID\",\"outputDir\":\"$OUT\"}" >/dev/null
api POST "$G/generatorCode" "$TA" "{\"ids\":[\"$ID\"],\"template\":\"BACKEND\"}" | json "d['code'], d['data']"
echo "files written: $(find "$OUT" -type f | wc -l)"
SVC=$(find "$OUT" -name "${ENT}Service.java"); MDL=$(find "$OUT" -name "$ENT.java")
echo "// changed" >> "$SVC"; echo "// changed" >> "$MDL"
api POST "$G/generatorCode" "$TA" "{\"ids\":[\"$ID\"],\"template\":\"BACKEND\"}" >/dev/null
echo "service kept: $(grep -c changed "$SVC"), entity overwritten: $(grep -c changed "$MDL" || true)"
api POST "$G/generatorCode" "$TA" "{\"ids\":[\"$ID\"],\"template\":\"BACKEND\",\"fileOverrideConfig\":{\"/templates/backend/java/service.java.ftl\":\"ADD\"}}" >/dev/null
echo "added beside the service: $(ls "$(dirname "$SVC")" | grep -c '\.add$')"

echo "== generated project"
curl -s -XPOST "$HOST/api/generator/defGenProject/getDef" -H "token: $TA" | json "d['data']['type'], d['data']['serverPort'], d['data']['parent']"
FORM="author=me&type=BOOT&projectPrefix=demo&serviceName=hello&moduleName=hello&parent=demo.app&groupId=demo.app&version=1.0.0&description=示例&serverPort=18791"
curl -s -o "$WORK/p.zip" -XPOST "$HOST/api/generator/defGenProject/download?$FORM" -H "token: $TA"
mkdir "$WORK/p"; (cd "$WORK/p" && unzip -q ../p.zip && find . -type f | sed 's#^\./##' | sort | tr '\n' ' '); echo
(cd "$WORK/p/demo-hello" && mvn -q -o dependency:build-classpath -Dmdep.outputFile=../cp.txt >/dev/null 2>&1 && mvn -q -o compile 2>&1 | grep -E "ERROR|BUILD" | head -5)
"$JAVA" -cp "$WORK/p/demo-hello/target/classes:$(cat "$WORK/p/cp.txt")" demo.app.hello.HelloApp --server.port=18791 > "$ROOT/logs/services/genproject.log" 2>&1 & PROJ=$!
for _ in $(seq 1 30); do (echo > /dev/tcp/127.0.0.1/18791) 2>/dev/null && break; sleep 1; done; sleep 1
curl -s http://localhost:18791/health | json "d['ok'], d['data']"
kill -9 $PROJ; PROJ=
FORM2=$(echo "$FORM" | sed 's/moduleName=hello/moduleName=..%2F..%2Fevil/')
curl -s -XPOST "$HOST/api/generator/defGenProject/download?$FORM2" -H "token: $TA" | json "d['code'], d['msg']"
api PUT $G "$TA" "{\"id\":\"$IDS\",\"moduleName\":\"../../../evil\"}" >/dev/null
curl -s "$HOST$G/downloadZip?ids=$IDS&template=BACKEND" -H "token: $TA" | json "d['code'], d['msg'].replace('$SB','T')"
api PUT $G "$TA" "{\"id\":\"$IDS\",\"moduleName\":\"ok\",\"plusApplicationName\":\"../../../../evil\"}" >/dev/null
curl -s "$HOST$G/downloadZip?ids=$IDS&template=WEB_PLUS" -H "token: $TA" | json "d['code'], d['msg'][:7]"

echo "== delete removes the fields"
api DELETE $G "$TA" "[\"$ID\",\"$IDT\",\"$IDS\"]" | json "d['code'], d['data']"
echo "fields left: $($MYSQL -e "select count(*) from def_gen_table_column where table_id=$ID and is_del=0" 2>/dev/null)"
