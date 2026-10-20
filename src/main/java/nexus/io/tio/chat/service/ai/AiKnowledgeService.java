package nexus.io.tio.chat.service.ai;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Db;
import nexus.io.db.activerecord.Row;
import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.chat.utils.HttpClients;
import nexus.io.tio.chat.utils.Values;
import nexus.io.tio.utils.json.JsonUtils;
import nexus.io.tio.utils.snowflake.SnowflakeIdUtils;

/**
 * 知识库：文档导入、按长度切片、关键字检索与对话召回
 */
public class AiKnowledgeService {
  public static final String KNOWLEDGE = "ai_knowledge";
  public static final String DOCUMENT = "ai_knowledge_document";
  public static final String SEGMENT = "ai_knowledge_segment";
  private static final int DEFAULT_SEGMENT_TOKENS = 500;

  private AiCrudService crud() {
    return Aop.get(AiCrudService.class);
  }

  public long createDocument(Long knowledgeId, String name, String url, Integer segmentMaxTokens) {
    ChatErrors.require(crud().rawRow(KNOWLEDGE, knowledgeId) != null, "知识库不存在");
    String content = url == null || url.isBlank() ? "" : HttpClients.get(url);
    int maxTokens = segmentMaxTokens == null ? DEFAULT_SEGMENT_TOKENS : segmentMaxTokens;
    long id = crud().create(DOCUMENT, Kv.by("knowledgeId", knowledgeId).set("name", name).set("url", url).set("content", content)
        .set("contentLength", content.length()).set("tokens", content.length()).set("segmentMaxTokens", maxTokens)
        .set("retrievalCount", 0).set("status", 0), false);
    saveSegments(knowledgeId, id, content, maxTokens);
    return id;
  }

  @SuppressWarnings("unchecked")
  public List<Long> createDocuments(Map<String, Object> body) {
    Long knowledgeId = Values.toLong(body.get("knowledgeId"));
    Integer maxTokens = Values.toInt(body.get("segmentMaxTokens"));
    List<Long> ids = new ArrayList<>();
    if (body.get("list") instanceof List) {
      for (Object item : (List<Object>) body.get("list")) {
        Map<String, Object> doc = (Map<String, Object>) item;
        ids.add(createDocument(knowledgeId, String.valueOf(doc.get("name")), (String) doc.get("url"), maxTokens));
      }
    }
    return ids;
  }

  private void saveSegments(long knowledgeId, long documentId, String content, int maxTokens) {
    Db.update("update ai_knowledge_segment set deleted=1 where document_id=?", documentId);
    for (String piece : split(content, maxTokens)) {
      Row row = Row.by("id", SnowflakeIdUtils.id()).set("knowledge_id", knowledgeId).set("document_id", documentId)
          .set("content", piece).set("content_length", piece.length()).set("tokens", piece.length()).set("retrieval_count", 0)
          .set("status", 0).set("creator", "").set("updater", "").set("create_time", LocalDateTime.now())
          .set("update_time", LocalDateTime.now()).set("deleted", 0).set("tenant_id", 1L);
      Db.save(SEGMENT, row);
    }
  }

  /**
   * 按段落切分，单段超过上限时按长度截断
   */
  public List<String> split(String content, int maxTokens) {
    List<String> result = new ArrayList<>();
    if (content == null || content.isBlank()) {
      return result;
    }
    StringBuilder current = new StringBuilder();
    for (String paragraph : content.split("\\n\\s*\\n")) {
      String text = paragraph.trim();
      if (text.isEmpty()) {
        continue;
      }
      if (current.length() + text.length() + 1 > maxTokens && current.length() > 0) {
        result.add(current.toString());
        current.setLength(0);
      }
      while (text.length() > maxTokens) {
        result.add(text.substring(0, maxTokens));
        text = text.substring(maxTokens);
      }
      current.append(current.length() > 0 ? "\n" : "").append(text);
    }
    if (current.length() > 0) {
      result.add(current.toString());
    }
    return result;
  }

  public List<Kv> splitUrl(String url, Integer segmentMaxTokens) {
    String content = HttpClients.get(url);
    List<Kv> result = new ArrayList<>();
    for (String piece : split(content, segmentMaxTokens == null ? DEFAULT_SEGMENT_TOKENS : segmentMaxTokens)) {
      result.add(Kv.by("content", piece).set("contentLength", piece.length()).set("tokens", piece.length()));
    }
    return result;
  }

  public List<Kv> processList(List<Long> documentIds) {
    List<Kv> result = new ArrayList<>();
    for (Long documentId : documentIds) {
      long count = Db.queryLong("select count(*) from ai_knowledge_segment where document_id=? and deleted=0", documentId);
      result.add(Kv.by("documentId", documentId).set("count", count).set("embeddingCount", count));
    }
    return result;
  }

  /**
   * 关键字检索：按命中词数排序，返回前 topK 条
   */
  public List<Kv> search(Long knowledgeId, String content, Integer topK) {
    Kv knowledge = crud().rawRow(KNOWLEDGE, knowledgeId);
    int k = topK != null ? topK : knowledge != null && Values.toInt(knowledge.get("top_k")) != null ? Values.toInt(knowledge.get("top_k")) : 3;
    List<Kv> segments = Db.findMaps("select * from ai_knowledge_segment where knowledge_id=? and deleted=0 and status=0", knowledgeId);
    List<String> words = keywords(content);
    List<Kv> scored = new ArrayList<>();
    for (Kv segment : segments) {
      String text = segment.getStr("content");
      int score = 0;
      for (String word : words) {
        if (text != null && text.contains(word)) {
          score++;
        }
      }
      if (score > 0) {
        scored.add(Kv.by("id", segment.getLong("id")).set("documentId", segment.getLong("document_id"))
            .set("knowledgeId", knowledgeId).set("content", text).set("contentLength", segment.get("content_length"))
            .set("tokens", segment.get("tokens")).set("score", words.isEmpty() ? 0 : (double) score / words.size()));
      }
    }
    scored.sort(Comparator.comparing((Kv s) -> (Double) s.get("score")).reversed());
    List<Kv> top = scored.subList(0, Math.min(k, scored.size()));
    for (Kv segment : top) {
      Db.update("update ai_knowledge_segment set retrieval_count=retrieval_count+1 where id=?", segment.getLong("id"));
    }
    return new ArrayList<>(top);
  }

  private List<String> keywords(String content) {
    List<String> words = new ArrayList<>();
    if (content == null) {
      return words;
    }
    for (String word : content.split("[\\s,，。！？!?、;；:：]+")) {
      if (word.length() >= 2) {
        words.add(word);
      }
    }
    if (words.isEmpty() && !content.isBlank()) {
      words.add(content.trim());
    }
    return words;
  }

  /**
   * 角色关联的知识库召回片段
   */
  public List<String> recallForRole(Long roleId, String content) {
    List<String> result = new ArrayList<>();
    if (roleId == null) {
      return result;
    }
    String ids = Db.queryStr("select knowledge_ids from ai_chat_role where id=?", roleId);
    if (ids == null || ids.isBlank()) {
      return result;
    }
    for (Object id : JsonUtils.parseArray(ids, Object.class)) {
      for (Kv segment : search(Values.toLong(id), content, null)) {
        result.add(segment.getStr("content"));
      }
    }
    return result;
  }
}
