package nexus.io.tio.chat.handler.ai;

import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;

import nexus.io.tio.chat.utils.ChatResp;
import nexus.io.tio.http.common.HttpRequest;
import nexus.io.tio.http.common.HttpResponse;

/** GET /api/ai/chat-role/category-list */
public class AiChatRoleCategoryListHandler {
  public HttpResponse handle(HttpRequest request) {
    List<Kv> options = new ArrayList<>();
    for (String category : new String[] { "AI助手", "写作", "编程开发", "学习教育", "生活娱乐", "商务办公", "创意设计", "数据分析", "翻译", "其他" }) {
      options.add(Kv.by("label", category).set("value", category));
    }
    return ChatResp.ok(options);
  }
}
