package nexus.io.tio.chat.service.generator;

import java.util.LinkedHashMap;
import java.util.Map;

import com.jfinal.kit.Kv;

/**
 * 代码生成表上的枚举列：数据库保存编码，接口使用枚举名称
 */
final class GenEnums {
  static final String TPL_SIMPLE = "01";
  static final String TPL_TREE = "02";
  static final String TPL_MAIN_SUB = "03";
  static final String POPUP_MODAL = "01";
  static final String POPUP_DRAWER = "02";
  static final String POPUP_JUMP = "03";
  static final String SUPER_CLASS = "01";
  static final String SUPER_POI_CLASS = "02";
  static final String ENTITY_SUPER_ENTITY = "01";
  static final String ENTITY_ENTITY = "02";
  static final String ENTITY_TREE = "03";
  static final String GEN_DIRECT = "01";

  private static final Map<String, Map<String, String>> FIELDS = new LinkedHashMap<>();

  static {
    FIELDS.put("tplType", Map.of("SIMPLE", TPL_SIMPLE, "TREE", TPL_TREE, "MAIN_SUB", TPL_MAIN_SUB));
    FIELDS.put("popupType", Map.of("MODAL", POPUP_MODAL, "DRAWER", POPUP_DRAWER, "JUMP", POPUP_JUMP));
    FIELDS.put("superClass", Map.of("SUPER_CLASS", SUPER_CLASS, "SUPER_POI_CLASS", SUPER_POI_CLASS, "SUPER_CACHE_CLASS", "03",
        "SUPER_SIMPLE_CLASS", "04", "SUPER_READ_CLASS", "05", "SUPER_WRITE_CLASS", "06", "NONE_CS", "07", "NONE", "08"));
    FIELDS.put("entitySuperClass",
        Map.of("SUPER_ENTITY", ENTITY_SUPER_ENTITY, "ENTITY", ENTITY_ENTITY, "TREE_ENTITY", ENTITY_TREE, "NONE", "04"));
    FIELDS.put("genType", Map.of("GEN", GEN_DIRECT, "ZIP", "02"));
  }

  private GenEnums() {
  }

  /** 请求中的枚举名称换成编码，已经是编码时不变 */
  static Map<String, Object> toDb(Map<String, Object> body) {
    Map<String, Object> result = new LinkedHashMap<>(body);
    FIELDS.forEach((field, values) -> {
      Object value = result.get(field);
      if (value != null && values.containsKey(value.toString())) {
        result.put(field, values.get(value.toString()));
      }
    });
    return result;
  }

  /** 响应中的编码换成枚举名称 */
  static Kv toApi(Kv vo) {
    FIELDS.forEach((field, values) -> {
      Object value = vo.get(field);
      if (value != null) {
        values.forEach((name, code) -> {
          if (code.equals(value.toString())) {
            vo.set(field, name);
          }
        });
      }
    });
    return vo;
  }
}
