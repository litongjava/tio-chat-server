# 和原后端、管理后台对比

这里记录两种完整回归之外的验证：把同样的请求发给运行中的原后端，以及用 HuLa-Admin 前端驱动本服务。两者都需要在临时目录准备外部项目，不在 `run-all-tests.sh` 中。

## 运行原后端

原后端需要 JDK 25。构建它的单进程版本：

```shell
mvn -DskipTests -Dgpg.skip install -f luohuo-util/pom.xml
mvn -DskipTests -Dgpg.skip package -f luohuo-cloud/pom.xml -pl luohuo-support/luohuo-boot-server -am
```

把 `docs/install/sql` 下的两个脚本导入独立的 `luohuo_dev`、`luohuo_im_01` 数据库，另起一个密码为 `luo.123456` 的 Redis（端口 16379），然后启动：

```shell
MYSQL_PORT=3306 REDIS_PORT=16379 java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED \
  -jar luohuo-boot-server.jar --server.port=18999 --luohuo.ignore.authEnabled=false --luohuo.ignore.notConfigUriAllow=true \
  --spring.datasource.druid.password=数据库密码 --spring.cloud.nacos.config.enabled=false --spring.cloud.nacos.config.import-check.enabled=false \
  --spring.cloud.nacos.server-addr=127.0.0.1:8848 --spring.cloud.nacos.username=nacos --spring.cloud.nacos.password=nacos \
  --spring.cloud.nacos.discovery.namespace=orig-verify --spring.cloud.nacos.discovery.register-enabled=false
```

单进程版有这些限制，对比结果要结合它们来看：

- 不包含 AI 模块，AI 接口都返回 406，不能对比。
- IM 用户登录时通过远程调用 IM 服务，会失败，所以只能用 `system_type=1` 的后台账号登录。依赖 IM 用户的接口在两边都失败，失败的原因不一定相同。
- 请求要带网关原本添加的 `tenantId`、`userId`、`uid`、`systemType` 请求头，脚本里已经带上。

## 对比响应

```shell
python3 scripts/compare-original.py ../HuLa-Server http://localhost:18999 <原后端 token> http://localhost:18760 <本服务 token> logs/test-results/compare-original.json writes
```

两边的数据不同，所以只比较调用是否成功和第一条记录的字段。原后端的 JSON 包含值为 null 的字段，因此这边缺少的字段是真实差异。`writes` 会把写接口也发出去，两边的数据库都会多出数据。

## 驱动 HuLa-Admin

克隆 HuLa-Admin，把 `.env.dev` 的 `VITE_API_BASE_URL` 改成 `http://localhost:18760/api`，用 Node 22.12 以上版本启动。它的 `User.vue` 引用了仓库中没有的 `src/styles/scss/user.scss`，需要先创建一个空文件。

账号要满足三个条件：`system_type=1`，有 `TENANT_ADMIN` 角色，在 `def_user_tenant_rel` 中有一行。HuLa-Admin 以后台账号登录，登录后要选择租户，否则会退出。

```shell
python3 scripts/client-admin-e2e.py http://127.0.0.1:6140 <账号> <密码>
```

## 前端模板

把生成器配置为 `--generator.project-prefix=lamp`，用 `WEB_OUT=<目录> scripts/smoke-generator-web.sh` 保存生成的代码，复制到 lamp-web（`4.x` 分支）的 `src` 下，用 TypeScript 5.2 运行 `vue-tsc --noEmit --skipLibCheck`。lamp-web 自身有 153 个类型错误，只看生成文件里的错误。

Soybean 和 vben5 的目标项目没有公开的仓库，只通过 `smoke-generator-web.sh` 做编译器语法检查。
