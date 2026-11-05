#!/usr/bin/env bash
# Code generator options: the generated backend is compiled and called for main-sub, tree, every super class, tenant,
# echo, chain and column constants; tables are imported from a throwaway PostgreSQL container.
cd "$(dirname "$0")"; source ./lib.sh; set -e
ROOT=$(cd .. && pwd); TS=$(date +%s%N | cut -c1-16); WORK=$(mktemp -d); JAVA=${JAVA:-java}; JAVAC=${JAVAC:-javac}
JAR="$ROOT/tio-chat-generator/target/tio-chat-generator.jar"
G=/api/generator/defGenTable; PORT=18792; PG=gen-pg-$TS
read TA UA < <(register_user "genopt$TS@example.com"); grant_admin "$UA"
TA=$(login_user "genopt$TS@example.com" | cut -d' ' -f1)
P="gopt_${TS: -8}"; M="${P}_main"; S="${P}_item"; TR="${P}_tree"; RO="${P}_read"; WO="${P}_write"; PO="${P}_poi"; CA="${P}_cache"; NO="${P}_none"; TE="${P}_tenant"; EC="${P}_echo"
ALL="$M $S $TR $RO $WO $PO $CA $NO $TE $EC"
trap '$MYSQL -e "drop table if exists $(echo $ALL | tr " " ",")" 2>/dev/null; $MYSQL -e "delete from def_dict where key_ like \"gopt%\" or parent_key like \"gopt%\"" 2>/dev/null; [ -n "$DEMO" ] && kill -9 $DEMO 2>/dev/null; docker rm -f $PG >/dev/null 2>&1; rm -rf "$WORK"; true' EXIT
$MYSQL -e "create table $M(id bigint primary key, name varchar(64) comment '名称', create_time datetime, is_del tinyint(1) not null default 0) comment '主表'" 2>/dev/null
$MYSQL -e "create table $S(id bigint primary key, main_id bigint, label varchar(64) comment '名称') comment '从表'" 2>/dev/null
$MYSQL -e "create table $TR(id bigint primary key, parent_id bigint, sort_value int, label varchar(64) comment '名称') comment '树'" 2>/dev/null
for t in $RO $WO $PO $CA $NO; do $MYSQL -e "create table $t(id bigint primary key, name varchar(64) comment '名称') comment '示例'" 2>/dev/null; done
$MYSQL -e "create table $TE(id bigint primary key, name varchar(64) comment '名称', tenant_id bigint) comment '租户'" 2>/dev/null
$MYSQL -e "create table $EC(id bigint primary key, state varchar(8) comment '状态 #${P^}StateEnum{ON:1,\"启用\";OFF:0,\"停用\";}', kind varchar(8) comment '类型 @Echo(api = x, dictType = \"gopt_kind\")') comment '回显'" 2>/dev/null
D=$(date +%s%N | cut -c1-18)
$MYSQL -e "insert into def_dict(id,parent_id,parent_key,classify,key_,name,state,sort_value,create_time,create_by,update_time,update_by,tenant_id) values ($D,0,'','20','gopt_kind','类型',1,1,now(),1,now(),1,1),($((D+1)),$D,'gopt_kind','20','a','甲',1,1,now(),1,now(),1,1)" 2>/dev/null

NAMES=$(for t in $ALL; do printf '"%s",' $t; done | sed 's/,$//')
api POST $G/importTable "$TA" "{\"tableNames\":[$NAMES]}" | json "d['code']"
tid() { api POST $G/query "$TA" "{\"name\":\"$1\"}" | json "[t['id'] for t in d['data'] if t['name']=='$1'][0]"; }
IDLIST=""; for t in $ALL; do i=$(tid $t); eval "ID_$t=$i"; IDLIST="$IDLIST\"$i\","; done; IDLIST=${IDLIST%,}
id_of() { eval echo "\$ID_$1"; }
api PUT $G "$TA" "{\"tableIdList\":[$IDLIST],\"serviceName\":\"demo\",\"moduleName\":\"opt\",\"parent\":\"demo.app\",\"plusApplicationName\":\"dev\",\"plusModuleName\":\"demo\",\"menuApplicationId\":\"1\",\"popupType\":\"MODAL\"}" | json "d['code']"
set_table() { api PUT $G "$TA" "{\"id\":\"$(id_of $1)\",$2}" | json "d['code']"; }
set_table $M "\"tplType\":\"MAIN_SUB\",\"subId\":\"$(id_of $S)\",\"subJavaFieldName\":\"mainId\""
set_table $TR '"tplType":"TREE","entitySuperClass":"TREE_ENTITY","treeName":"label","isLombok":false,"isChain":true,"isColumnConstant":true'
set_table $RO '"superClass":"SUPER_READ_CLASS"'
set_table $WO '"superClass":"SUPER_WRITE_CLASS"'
set_table $PO '"superClass":"SUPER_POI_CLASS"'
set_table $CA '"superClass":"SUPER_CACHE_CLASS"'
set_table $NO '"superClass":"NONE"'
set_table $EC '"isChain":true'
ent() { api GET "$G/detail?id=$(id_of $1)" "$TA" | json "d['data']['entityName']"; }
lower() { python3 -c "s='$1';print(s[0].lower()+s[1:])"; }

echo "== generate and compile"
QIDS=$(for t in $M $TR $RO $WO $PO $CA $NO $TE $EC; do printf 'ids=%s&' $(id_of $t); done)
curl -s -o "$WORK/code.zip" "$HOST$G/downloadZip?${QIDS}template=BACKEND" -H "token: $TA"
mkdir "$WORK/src" "$WORK/classes"; (cd "$WORK/src" && unzip -q ../code.zip)
ROUTES=""; for t in $M $TR $RO $WO $PO $CA $NO $TE $EC; do ROUTES="$ROUTES new demo.app.opt.config.route.$(ent $t)RouteConfig().config(router);"; done
cat > "$WORK/DemoMain.java" <<JAVA
import nexus.io.tio.chat.boot.ChatApplication;
import nexus.io.tio.chat.boot.ChatModule;
import nexus.io.tio.http.server.router.HttpRequestRouter;
public class DemoMain {
  public static void main(String[] args) {
    ChatApplication.runService(DemoMain.class, args, "gen-demo", $PORT, new ChatModule() {
      public String serviceName() { return "gen-demo"; }
      public void config(HttpRequestRouter router) { $ROUTES }
    });
  }
}
JAVA
LOMBOK=$(ls ~/.m2/repository/org/projectlombok/lombok/*/lombok-*.jar | grep -v sources | tail -1)
"$JAVAC" -nowarn -cp "$JAR:$LOMBOK" -d "$WORK/classes" "$WORK/DemoMain.java" $(find "$WORK/src" -name '*.java') 2>&1 | grep -v "^Note" | head -30
echo "compiled: $(find "$WORK/classes" -name 'DemoMain.class' | wc -l)"
TREEJ=$(find "$WORK/src" -name "$(ent $TR).java")
echo "chain setter: $(grep -c "public $(ent $TR) setLabel" "$TREEJ"), column constant: $(grep -c 'COLUMN_LABEL = "label"' "$TREEJ"), no lombok: $(grep -c lombok "$TREEJ" || true)"
echo "lombok chain: $(grep -c 'Accessors(chain = true)' "$(find "$WORK/src" -name "$(ent $EC).java")")"
echo "no service methods with NONE: $(grep -c 'public Kv' "$(find "$WORK/src" -name "$(ent $NO)Service.java")" || true)"
"$JAVA" -cp "$WORK/classes:$JAR" DemoMain --nacos.discovery.enabled=false --server.port=$PORT > "$ROOT/logs/services/genopt.log" 2>&1 & DEMO=$!
for _ in $(seq 1 40); do (echo > /dev/tcp/127.0.0.1/$PORT) 2>/dev/null && break; sleep 1; done; sleep 2
call() { curl -s -X"$2" "http://localhost:$PORT/api/demo/$(lower $(ent $1))$3" -H 'Content-Type: application/json' -H "token: $TA" ${5:+-H "tenantId: $5"} ${5:+-H "x-internal-secret: tio-chat-internal"} ${4:+-d "$4"}; }
status() { curl -s -o /dev/null -w '%{http_code}' -X"$2" "http://localhost:$PORT/api/demo/$(lower $(ent $1))$3" -H 'Content-Type: application/json' -H "token: $TA" -d "${4:-{\}}"; }
count() { $MYSQL -e "$1" 2>/dev/null; }

echo "== main and sub"
OUT=$(call $M POST "" '{"name":"m1","insertList":[{"label":"a"},{"label":"b"}]}')
MID=$(echo "$OUT" | json "d['data']['id']"); echo "$OUT" | json "d['code'], d['data']['name']"
echo "items saved: $(count "select count(*) from $S where main_id=$MID")"
A=$(count "select id from $S where main_id=$MID and label='a'"); B=$(count "select id from $S where main_id=$MID and label='b'")
call $M PUT "" "{\"id\":\"$MID\",\"name\":\"m2\",\"insertList\":[{\"label\":\"c\"}],\"updateList\":[{\"id\":\"$A\",\"label\":\"a2\"}],\"deleteList\":[\"$B\"]}" | json "d['code'], d['data']['name']"
echo "items after update: $(count "select group_concat(label order by label) from $S where main_id=$MID")"
OTHER=$(call $M POST "" '{"name":"other","insertList":[{"label":"x"}]}' | json "d['data']['id']")
X=$(count "select id from $S where main_id=$OTHER")
call $M PUT "" "{\"id\":\"$MID\",\"name\":\"bad\",\"deleteList\":[\"$X\"]}" | json "d['code'], d['msg']"
echo "item of another main kept: $(count "select count(*) from $S where id=$X"), name not changed: $(count "select name from $M where id=$MID")"
COPY=$(call $M POST "/copy?id=$MID" | json "d['data']['id']")
echo "copied items: $(count "select group_concat(label order by label) from $S where main_id=$COPY")"
call $M DELETE "" "[\"$MID\"]" | json "d['code'], d['data']"
echo "items after delete: $(count "select count(*) from $S where main_id=$MID"), main is_del: $(count "select is_del from $M where id=$MID")"
call $M GET "/detail?id=$MID" | json "d['code'], d['data']"
call $M PUT "" "{\"id\":\"$MID\",\"name\":\"gone\"}" | json "d['code'], d['msg']"
call $M POST /copy "{\"id\":\"$MID\"}" | json "d['code'], d['msg']"
call $S POST /page '{"model":{"mainId":"'$COPY'"}}' | json "d['code'], d['data']['total']"

echo "== tree"
ROOT1=$(call $TR POST "" '{"label":"root","sortValue":1}' | json "d['data']['id']")
CH2=$(call $TR POST "" "{\"label\":\"second\",\"parentId\":\"$ROOT1\",\"sortValue\":2}" | json "d['data']['id']")
CH1=$(call $TR POST "" "{\"label\":\"first\",\"parentId\":\"$ROOT1\",\"sortValue\":1}" | json "d['data']['id']")
call $TR POST "" "{\"label\":\"leaf\",\"parentId\":\"$CH1\",\"sortValue\":1}" >/dev/null
call $TR POST /tree '{}' | json "[(n['label'], [(c['label'], [g['label'] for g in c.get('children',[])]) for c in n['children']]) for n in d['data']]"
call $TR POST /query '{"label":"e"}' | json "sorted(n['label'] for n in d['data'])"
call $TR POST /findByIds "[\"$CH1\",\"$CH2\"]" | json "sorted(n['label'] for n in d['data'])"
call $TR GET "/$CH1" | json "d['data']['label']"
call $TR POST /page '{"sort":"sortValue","order":"ascending","size":2}' | json "d['data']['total'], d['data']['records'][0]['sortValue'], len(d['data']['records'])"
call $TR POST /page '{"sort":"name;drop table x"}' | json "d['code']"

echo "== super class decides the endpoints (404 is absent)"
echo "read:  page $(status $RO POST /page) query $(status $RO POST /query) save $(status $RO POST '') delete $(status $RO DELETE '' '[]') copy $(status $RO POST /copy)"
echo "write: page $(status $WO POST /page) detail $(status $WO GET '/detail?id=1') save $(status $WO POST '' '{"name":"w"}') update $(status $WO PUT '' '{"id":"1"}')"
echo "none:  page $(status $NO POST /page) save $(status $NO POST '')"
echo "export only with poi: $(status $RO POST /export) $(status $PO POST /export)"
call $PO POST "" '{"name":"=cmd(\"x\")"}' >/dev/null
call $PO POST /export '{"model":{}}' | python3 -c "import sys;print(sys.stdin.read().replace('﻿','').strip().splitlines())"
call $PO POST /preview '{"model":{}}' | json "d['code'], d['data'].startswith('<table>'), '=cmd' in d['data']"
call $PO POST "" '{"name":"<b>x</b>"}' >/dev/null
call $PO POST /preview '{"model":{}}' | json "'escaped:', '<b>' not in d['data'], '&lt;b&gt;' in d['data']"
printf 'id,name\n,csv1\n,"a,""b"""\n,'"'"'=sum\n' > "$WORK/in.csv"
curl -s -XPOST "http://localhost:$PORT/api/demo/$(lower $(ent $PO))/import" -H "token: $TA" -F "file=@$WORK/in.csv" | json "'imported:', d['code'], d['data']"
echo "rows: $(count "select count(*) from $PO where name in ('csv1','a,\"b\"','=sum')")"
curl -s -o /dev/null -w 'import without poi: %{http_code}\n' -XPOST "http://localhost:$PORT/api/demo/$(lower $(ent $RO))/import" -H "token: $TA" -F "file=@$WORK/in.csv"
CID=$(call $CA POST "" '{"name":"before"}' | json "d['data']['id']")
call $CA GET "/detail?id=$CID" >/dev/null; count "update $CA set name='changed' where id=$CID" >/dev/null
echo "cached: $(call $CA GET "/detail?id=$CID" | json "d['data']['name']")"
echo "in redis: $($REDIS hexists gen:cache:$CA $CID)"
call $CA POST /refreshCache | json "d['code'], d['data']"
echo "after refresh: $(call $CA GET "/detail?id=$CID" | json "d['data']['name']")"
call $CA PUT "" "{\"id\":\"$CID\",\"name\":\"updated\"}" >/dev/null
echo "after update: $(call $CA GET "/detail?id=$CID" | json "d['data']['name']")"

echo "== tenant line"
T1=$(call $TE POST "" '{"name":"t1"}' 1 | json "d['data']['id']")
call $TE POST "" '{"name":"t2"}' 2 >/dev/null
echo "tenant_id saved: $(count "select tenant_id from $TE where id=$T1")"
call $TE POST /page '{}' 1 | json "d['data']['total'], d['data']['records'][0]['name']"
call $TE GET "/detail?id=$T1" "" 2 | json "d['code'], d['data']"
call $TE PUT "" "{\"id\":\"$T1\",\"name\":\"hack\"}" 2 | json "d['code'], d['msg']"
call $TE DELETE "" "[\"$T1\"]" 2 >/dev/null
echo "kept after delete by another tenant: $(count "select name from $TE where id=$T1")"
echo "no tenant header: $(call $TE POST /page '{}' | json "d['code']")"
echo "tenant without the gateway secret: $(curl -s -XPOST "http://localhost:$PORT/api/demo/$(lower $(ent $TE))/page" -H 'Content-Type: application/json' -H "token: $TA" -H 'tenantId: 1' -d '{}' | json "d['code']")"
set_table $TE '"isTenantLine":true' >/dev/null
curl -s "$HOST$G/previewCode?id=$(id_of $TE)&template=BACKEND" -XPOST -H "token: $TA" | json "'tenant_id=?' in ''.join(v for k,v in d['data'].items() if 'service.java' in k)"

echo "== echo"
call $EC POST "" '{"state":"1","kind":"a"}' | json "d['code'], d['data']['echoMap']"
ECHOJ=$(find "$WORK/src" -name "$(ent $EC)Echo.java")
echo "constant: $(grep -c 'String KIND = "gopt_kind"' "$ECHOJ")"
kill -9 $DEMO; DEMO=

echo "== data source option"
set_table $TE '"isDs":true,"dsValue":"\"other\""' >/dev/null
curl -s "$HOST$G/previewCode?id=$(id_of $TE)&template=BACKEND" -XPOST -H "token: $TA" | json "'Db.use(\"other\")' in ''.join(v for k,v in d['data'].items() if 'service.java' in k)"
set_table $TE '"dsValue":"x\");evil(\""' >/dev/null
curl -s "$HOST$G/previewCode?id=$(id_of $TE)&template=BACKEND" -XPOST -H "token: $TA" | json "d['code'], d['msg'].replace('$TE','T')"
set_table $TE '"isDs":false' >/dev/null

echo "== seata"
FORM="author=me&type=BOOT&projectPrefix=demo&serviceName=hello&moduleName=hello&parent=demo.app&groupId=demo.app&version=1.0.0&description=示例&serverPort=18793"
for seata in false true; do
  curl -s -o "$WORK/p$seata.zip" -XPOST "$HOST/api/generator/defGenProject/download?$FORM&seata=$seata" -H "token: $TA"
  echo "seata=$seata pom: $(unzip -p "$WORK/p$seata.zip" demo-hello/pom.xml | grep -c seata-all || true)"
done

echo "== postgresql data source"
IMAGE=$(docker inspect pgmaster --format '{{.Config.Image}}')
docker run -d --rm --name $PG -e POSTGRES_PASSWORD=gen_test -p 15433:5432 "$IMAGE" >/dev/null
for _ in $(seq 1 30); do docker exec $PG pg_isready -U postgres >/dev/null 2>&1 && break; sleep 1; done; sleep 2
docker exec $PG psql -U postgres -q -c "create table gen_pg(id bigserial primary key, big int8, cnt int4, ok boolean not null, price numeric(10,2), note text, name varchar(32), at timestamptz, d date, rate float8); comment on table gen_pg is '示例表'; comment on column gen_pg.name is '名称';"
DS=$(date +%s%N | cut -c1-18)
$MYSQL -e "insert into def_datasource_config(id,name,username,password,url,driver_class_name,create_by,create_time,update_time,tenant_id) values ($DS,'pg','postgres','gen_test','jdbc:postgresql://localhost:15433/postgres','org.postgresql.Driver',1,now(),now(),1)" 2>/dev/null
api POST $G/selectTableList "$TA" "{\"model\":{\"dsId\":\"$DS\"}}" | json "d['code'], [(t['name'],t['comment']) for t in d['data']]"
api POST $G/importTable "$TA" "{\"dsId\":\"$DS\",\"tableNames\":[\"gen_pg\"]}" | json "d['code']"
PID=$(tid gen_pg)
api POST /api/generator/defGenTableColumn/page "$TA" "{\"size\":50,\"sort\":\"sortValue\",\"order\":\"ascending\",\"model\":{\"tableId\":\"$PID\"}}" | json "[(c['name'],c['javaType'],bool(c['isPk']),bool(c['isIncrement'])) for c in sorted(d['data']['records'],key=lambda c:c['sortValue'])]"
api PUT $G "$TA" "{\"id\":\"$PID\",\"serviceName\":\"demo\",\"moduleName\":\"pg\",\"parent\":\"demo.app\",\"plusApplicationName\":\"dev\",\"plusModuleName\":\"demo\",\"menuApplicationId\":\"1\"}" >/dev/null
curl -s "$HOST$G/previewCode?id=$PID&template=BACKEND" -XPOST -H "token: $TA" | json "'paginate' in ''.join(v for k,v in d['data'].items() if 'service.java' in k), 'limit ?' in ''.join(v for k,v in d['data'].items() if 'service.java' in k)"
api POST "$G/syncField?id=$PID" "$TA" | json "d['code']"
api DELETE $G "$TA" "[\"$PID\"]" >/dev/null
$MYSQL -e "update def_datasource_config set url='jdbc:mysql://h/x?allowLoadLocalInfile=true' where id=$DS" 2>/dev/null
api POST $G/selectTableList "$TA" "{\"model\":{\"dsId\":\"$DS\"}}" | json "d['code'], d['msg']"
$MYSQL -e "update def_datasource_config set url='jdbc:oracle:thin:@h:1521/x',driver_class_name='oracle.jdbc.OracleDriver' where id=$DS" 2>/dev/null
api POST $G/selectTableList "$TA" "{\"model\":{\"dsId\":\"$DS\"}}" | json "d['code'], d['msg']"
$MYSQL -e "update def_datasource_config set is_del=1 where id=$DS" 2>/dev/null
api DELETE $G "$TA" "[$IDLIST]" | json "d['code']"
