package nexus.io.tio.chat.config.route;

import nexus.io.tio.chat.handler.system.ConfigBatchUpdateHandler;
import nexus.io.tio.chat.handler.system.ConfigInitHandler;
import nexus.io.tio.chat.handler.system.ConfigListHandler;
import nexus.io.tio.chat.handler.system.ConfigUpdateHandler;
import nexus.io.tio.chat.handler.system.MapReverseHandler;
import nexus.io.tio.chat.handler.system.MapStaticHandler;
import nexus.io.tio.chat.handler.system.MapTranslateHandler;
import nexus.io.tio.chat.handler.system.ModelListHandler;
import nexus.io.tio.chat.handler.system.OssTokenHandler;
import nexus.io.tio.chat.handler.system.StorageProviderHandler;
import nexus.io.tio.chat.handler.system.TranslateSegmentHandler;
import nexus.io.tio.chat.handler.system.TranslateStreamHandler;
import nexus.io.tio.chat.handler.system.TranslateTextHandler;
import nexus.io.tio.http.server.router.HttpRequestRouter;

public class SystemRouteConfig {
  private static final String P = "/api/system";

  public void config(HttpRequestRouter r) {
    r.get(P + "/anyTenant/config/init", new ConfigInitHandler()::handle);
    r.get(P + "/anyTenant/ossToken", new OssTokenHandler()::handle);
    r.get(P + "/anyTenant/storage/provider", new StorageProviderHandler()::handle);
    r.get(P + "/anyTenant/config/list", new ConfigListHandler()::handle);
    r.put(P + "/anyTenant/config/update", new ConfigUpdateHandler()::handle);
    r.put(P + "/anyTenant/config/batchUpdate", new ConfigBatchUpdateHandler()::handle);
    r.get(P + "/model/list", new ModelListHandler()::handle);
    r.get(P + "/anyTenant/map/coord/translate", new MapTranslateHandler()::handle);
    r.get(P + "/anyTenant/map/geocoder/reverse", new MapReverseHandler()::handle);
    r.get(P + "/anyTenant/map/static", new MapStaticHandler()::handle);
    r.post(P + "/anyTenant/translate/text", new TranslateTextHandler()::handle);
    r.post(P + "/anyTenant/translate/segment", new TranslateSegmentHandler()::handle);
    r.get(P + "/anyTenant/translate/stream", new TranslateStreamHandler()::handle);
  }
}
