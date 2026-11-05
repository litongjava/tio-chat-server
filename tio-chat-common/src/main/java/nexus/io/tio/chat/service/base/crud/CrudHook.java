package nexus.io.tio.chat.service.base.crud;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.db.activerecord.Row;

/**
 * 一个资源在通用增删改查之外的业务规则：校验、填充和级联。写入方法都在同一个事务里执行
 */
public interface CrudHook {

  /** row 是请求内容对应的列，还没有 id */
  default void beforeSave(Map<String, Object> body, Row row) {
  }

  default void afterSave(Map<String, Object> body, Row row) {
  }

  default void beforeUpdate(Long id, Map<String, Object> body, Row row) {
  }

  default void afterUpdate(Long id, Map<String, Object> body) {
  }

  default void beforeDelete(Collection<Long> ids) {
  }

  /** row 是复制出来的列，还没有 id */
  default void beforeCopy(Kv source, Row row) {
  }

  default void afterCopy(Long sourceId, Row row) {
  }

  /** 列表查询的附加条件，model 是请求中的查询条件 */
  default void appendWhere(Map<String, Object> model, StringBuilder where, List<Object> params) {
  }

  /** 补充分页和列表接口返回的内容 */
  default void decorateList(List<Kv> records) {
  }

  /** 补充详情接口返回的内容 */
  default void decorateDetail(Kv vo) {
  }
}
