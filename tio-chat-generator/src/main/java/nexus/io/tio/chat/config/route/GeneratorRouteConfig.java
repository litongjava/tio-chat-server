package nexus.io.tio.chat.config.route;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.handler.generator.ColumnSyncFieldHandler;
import nexus.io.tio.chat.handler.generator.DownloadZipHandler;
import nexus.io.tio.chat.handler.generator.FieldTemplateHandler;
import nexus.io.tio.chat.handler.generator.FindTableListHandler;
import nexus.io.tio.chat.handler.generator.GeneratorCodeHandler;
import nexus.io.tio.chat.handler.generator.ImportCheckHandler;
import nexus.io.tio.chat.handler.generator.ImportTableHandler;
import nexus.io.tio.chat.handler.generator.OverrideStrategyHandler;
import nexus.io.tio.chat.handler.generator.PreviewCodeHandler;
import nexus.io.tio.chat.handler.generator.ProjectDefHandler;
import nexus.io.tio.chat.handler.generator.ProjectDownloadHandler;
import nexus.io.tio.chat.handler.generator.ProjectGenerateHandler;
import nexus.io.tio.chat.handler.generator.ProjectPropertiesHandler;
import nexus.io.tio.chat.handler.generator.SelectTableListHandler;
import nexus.io.tio.chat.handler.generator.SyncFieldHandler;
import nexus.io.tio.chat.handler.generator.TableCopyHandler;
import nexus.io.tio.chat.handler.generator.TableDeleteHandler;
import nexus.io.tio.chat.handler.generator.TableDetailHandler;
import nexus.io.tio.chat.handler.generator.TableGetHandler;
import nexus.io.tio.chat.handler.generator.TablePageHandler;
import nexus.io.tio.chat.handler.generator.TableQueryHandler;
import nexus.io.tio.chat.handler.generator.TableSaveHandler;
import nexus.io.tio.chat.handler.generator.TableUpdateHandler;
import nexus.io.tio.chat.service.generator.GenTableService;
import nexus.io.tio.http.server.router.HttpRequestRouter;

/**
 * 代码生成接口，只有管理员可以访问
 */
public class GeneratorRouteConfig {
  private static final String P = "/api/generator";

  public void config(HttpRequestRouter r) {
    String t = P + "/defGenTable";
    r.post(t + "/selectTableList", new SelectTableListHandler()::handle);
    r.post(t + "/importCheck", new ImportCheckHandler()::handle);
    r.post(t + "/importTable", new ImportTableHandler()::handle);
    r.post(t + "/syncField", new SyncFieldHandler()::handle);
    r.post(t + "/findTableList", new FindTableListHandler()::handle);
    r.post(t + "/findByIds", new FindTableListHandler()::handle);
    r.post(t + "/previewCode", new PreviewCodeHandler()::handle);
    r.post(t + "/generatorCode", new GeneratorCodeHandler()::handle);
    r.get(t + "/downloadZip", new DownloadZipHandler()::handle);
    r.get(t + "/getFieldTemplate", new FieldTemplateHandler()::handle);
    r.get(t + "/getDefFileOverrideStrategy", new OverrideStrategyHandler()::handle);
    r.post(t, new TableSaveHandler()::handle);
    r.post(t + "/copy", new TableCopyHandler()::handle);
    r.put(t, new TableUpdateHandler()::handle);
    r.delete(t, new TableDeleteHandler()::handle);
    r.get(t + "/detail", new TableDetailHandler()::handle);
    r.post(t + "/query", new TableQueryHandler()::handle);
    r.post(t + "/page", new TablePageHandler()::handle);
    r.get(t + "/{id:[0-9]+}", new TableGetHandler()::handle);

    String c = P + "/defGenTableColumn";
    r.post(c + "/syncField", new ColumnSyncFieldHandler()::handle);
    Aop.get(CrudRouteRegistrar.class).register(r, P, GenTableService.COLUMN);

    String g = P + "/defGenProject";
    r.post(g + "/getDef", new ProjectDefHandler()::handle);
    r.post(g + "/anno/getProperties", new ProjectPropertiesHandler()::handle);
    r.post(g + "/download", new ProjectDownloadHandler()::handle);
    r.post(g + "/generator", new ProjectGenerateHandler()::handle);
  }
}
