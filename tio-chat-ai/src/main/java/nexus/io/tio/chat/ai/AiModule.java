package nexus.io.tio.chat.ai;

import nexus.io.db.activerecord.Db;
import nexus.io.tio.chat.boot.ChatModule;
import nexus.io.tio.chat.config.route.AiRouteConfig;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * AI 模型、对话、写作、思维导图、知识库与绘图（luohuo-ai-server）
 */
public class AiModule implements ChatModule {
  public static final String SERVICE_NAME = "luohuo-ai-server";
  public static final int DEFAULT_PORT = 18877;

  /** 原后端的安装脚本里没有工作流表，第一次启动时创建 */
  private void createMissingTables() {
    Db.update("""
        create table if not exists ai_workflow (
          id bigint not null auto_increment primary key,
          name varchar(255) not null comment '工作流名称',
          code varchar(255) not null comment '工作流标识',
          graph longtext not null comment '工作流模型',
          remark varchar(255) default null comment '备注',
          status tinyint not null default 0 comment '状态',
          creator varchar(64) default '' comment '创建者',
          create_time datetime not null default current_timestamp comment '创建时间',
          updater varchar(64) default '' comment '更新者',
          update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
          deleted bit(1) not null default b'0' comment '是否删除',
          tenant_id bigint not null default 1 comment '租户编号'
        ) comment='AI 工作流'
        """);
  }

  @Override
  public String serviceName() {
    return SERVICE_NAME;
  }

  @Override
  public void config(HttpRequestRouter router) throws Exception {
    createMissingTables();
    new AiRouteConfig().config(router);
  }
}
