package nexus.io.tio.chat.handler.im.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.service.im.ImUserInfoService;
import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.chat.utils.ParameterValidationUtils;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** POST /api/im/user/badges/batch */
public class ItemInfoBatchHandler {
  public HttpResponse handle(HttpRequest request) {
    Kv body = ParameterValidationUtils.bodyKv(request);
    List<Kv> reqList = new ArrayList<>();
    Object raw = body.get("reqList");
    if (raw instanceof List) {
      for (Object item : (List<?>) raw) {
        if (item instanceof Map) {
          reqList.add(Kv.create().set((Map<?, ?>) item));
        }
      }
    }
    ImUserInfoService service = Aop.get(ImUserInfoService.class);
    return ChatResp.ok(service.getItemInfo(reqList));
  }
}
