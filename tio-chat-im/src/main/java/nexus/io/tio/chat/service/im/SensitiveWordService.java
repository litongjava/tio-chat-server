package nexus.io.tio.chat.service.im;

import java.util.ArrayList;
import java.util.List;

import nexus.io.db.activerecord.Db;

/**
 * 敏感词过滤，词库来自 im_sensitive_word
 */
public class SensitiveWordService {
  private volatile List<String> words;

  public List<String> words() {
    if (words == null) {
      refresh();
    }
    return words;
  }

  public synchronized void refresh() {
    List<String> list = new ArrayList<>();
    List<String> rows = Db.query("select word from im_sensitive_word");
    for (String word : rows) {
      if (word != null && !word.isBlank()) {
        list.add(word.trim());
      }
    }
    words = list;
  }

  public boolean hasSensitiveWord(String text) {
    if (text == null || text.isEmpty()) {
      return false;
    }
    for (String word : words()) {
      if (text.contains(word)) {
        return true;
      }
    }
    return false;
  }

  public String filter(String text) {
    if (text == null || text.isEmpty()) {
      return text;
    }
    String result = text;
    for (String word : words()) {
      if (result.contains(word)) {
        result = result.replace(word, "*".repeat(word.length()));
      }
    }
    return result;
  }
}
