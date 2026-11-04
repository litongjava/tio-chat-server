package nexus.io.tio.chat.service.generator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.jfinal.kit.Kv;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import nexus.io.tio.chat.model.generator.GenerateReq;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.utils.environment.EnvUtils;

/**
 * 按表配置渲染模板：预览、打包下载、写到本地目录
 */
@Slf4j
public class GenCodeService {
  private static final Configuration CONFIG = new Configuration(Configuration.VERSION_2_3_31);

  static {
    CONFIG.setDefaultEncoding(StandardCharsets.UTF_8.name());
    CONFIG.setClassForTemplateLoading(GenCodeService.class, "/");
    CONFIG.setNumberFormat("computer");
  }

  public static Map<String, String> fieldTemplates() {
    return GenTemplates.fieldTemplates();
  }

  public static Map<String, String> defaultStrategies() {
    return GenTemplates.defaultStrategies();
  }

  /** 一个要生成的文件 */
  private record GenFile(String template, String path, String code) {
  }

  /** 预览：模板路径 -> 代码；从表的模板带 sub- 前缀，单个模板出错时显示错误信息 */
  public Map<String, String> preview(Long id, String template) {
    Kv table = GenModel.loadTable(id);
    Map<String, String> result = new LinkedHashMap<>();
    Map<String, Object> subModel = null;
    Map<String, String> subResult = new LinkedHashMap<>();
    if (GenEnums.TPL_MAIN_SUB.equals(table.getStr("tplType"))) {
      Kv subTable = GenModel.loadTable(table.getLong("subId"));
      subModel = GenModel.build(subTable, null);
      previewAll(subModel, GenTemplates.subList(template), template, subResult);
    }
    Map<String, Object> model = GenModel.build(table, subModel);
    previewAll(model, GenTemplates.list(template, table.getStr("tplType"), table.getStr("popupType")), template, result);
    subResult.forEach((key, value) -> result.put("sub-" + key, value));
    return result;
  }

  @SuppressWarnings("unchecked")
  private void previewAll(Map<String, Object> model, List<String> templates, String templateType, Map<String, String> result) {
    for (String template : templates) {
      try {
        result.put(template, render(template, model));
      } catch (IOException | TemplateException e) {
        log.info("render {} failed", template, e);
        result.put(template, e.getMessage());
      }
    }
    if (GenTemplates.BACKEND.equals(templateType)) {
      for (Kv enumType : (List<Kv>) model.get("enumList")) {
        model.put("enumType", enumType);
        String key = "/templates/java/" + enumType.getStr("enumName") + ".java.ftl";
        try {
          result.put(key, render(GenTemplates.ENUM, model));
        } catch (IOException | TemplateException e) {
          log.info("render enum {} failed", key, e);
          result.put(key, e.getMessage());
        }
      }
    }
  }

  private String render(String template, Map<String, Object> model) throws IOException, TemplateException {
    Template tpl = CONFIG.getTemplate(template);
    StringWriter writer = new StringWriter();
    tpl.process(model, writer);
    return writer.toString();
  }

  /** 一张表（含从表）要生成的全部文件。任何一个模板出错都中止，不留下只生成了一半的代码 */
  @SuppressWarnings("unchecked")
  private List<GenFile> files(Long id, String templateType) {
    Kv table = GenModel.loadTable(id);
    List<GenFile> files = new ArrayList<>();
    Map<String, Object> subModel = null;
    Kv subTable = null;
    if (GenEnums.TPL_MAIN_SUB.equals(table.getStr("tplType"))) {
      subTable = GenModel.loadTable(table.getLong("subId"));
      subModel = GenModel.build(subTable, null);
      addFiles(files, subModel, GenTemplates.subList(templateType), templateType, subTable, null);
    }
    Map<String, Object> model = GenModel.build(table, subModel);
    addFiles(files, model, GenTemplates.list(templateType, table.getStr("tplType"), table.getStr("popupType")), templateType, table,
        subTable);
    return files;
  }

  @SuppressWarnings("unchecked")
  private void addFiles(List<GenFile> files, Map<String, Object> model, List<String> templates, String templateType, Kv table,
      Kv subTable) {
    try {
      for (String template : templates) {
        String path = GenTemplates.outputFile(templateType, template, table, subTable, null);
        files.add(new GenFile(template, safePath(path), render(template, model)));
      }
      if (GenTemplates.BACKEND.equals(templateType)) {
        for (Kv enumType : (List<Kv>) model.get("enumList")) {
          model.put("enumType", enumType);
          String path = GenTemplates.outputFile(templateType, GenTemplates.ENUM, table, null, enumType.getStr("enumName"));
          files.add(new GenFile(GenTemplates.ENUM, safePath(path), render(GenTemplates.ENUM, model)));
        }
      }
    } catch (IOException | TemplateException e) {
      log.error("generate {} failed", table.getStr("name"), e);
      throw ChatErrors.of("代码生成异常，请检查【" + table.getStr("name") + "】的配置：" + e.getMessage());
    }
  }

  /** 路径来自表配置，不允许跳出输出目录，也不允许让压缩包解压到目录之外 */
  static String safePath(String path) {
    Path normalized = Path.of(path).normalize();
    ChatErrors.require(!normalized.isAbsolute() && !normalized.startsWith(".."), "生成路径不合法：" + path);
    return normalized.toString().replace('\\', '/');
  }

  /** @return 压缩包内容 */
  public byte[] zip(List<Long> ids, String template) {
    ChatErrors.require(ids != null && !ids.isEmpty(), "请选择需生成表");
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
      for (Long id : ids) {
        for (GenFile file : files(id, template)) {
          zip.putNextEntry(new ZipEntry(file.path()));
          zip.write(file.code().getBytes(StandardCharsets.UTF_8));
          zip.closeEntry();
        }
      }
    } catch (IOException e) {
      throw ChatErrors.of("打包失败：" + e.getMessage());
    }
    return out.toByteArray();
  }

  /** 写到服务器本地目录，只有开发环境可以使用，线上请使用打包下载 */
  public boolean generate(GenerateReq req) {
    requireDev();
    ChatErrors.require(req.getIds() != null && !req.getIds().isEmpty(), "请选择需生成表");
    ChatErrors.require(req.getTemplate() != null, "请选择需生成类型");
    Map<String, String> overrides = req.getFileOverrideConfig() == null ? Map.of() : req.getFileOverrideConfig();
    for (Long id : req.getIds()) {
      Kv table = GenModel.loadTable(id);
      String dir = GenTemplates.outputDir(req.getTemplate(), table);
      ChatErrors.require(dir != null && !dir.isEmpty(), "请先配置代码生成的绝对路径");
      for (GenFile file : files(id, req.getTemplate())) {
        write(dir, file, overrides.getOrDefault(file.template(), GenTemplates.strategy(file.template())));
      }
    }
    return true;
  }

  static void requireDev() {
    ChatErrors.require(EnvUtils.isDev(), "只有本地环境（app.env=dev）能直接生成代码到指定路径，线上部署的程序请使用打包下载");
  }

  private void write(String dir, GenFile file, String strategy) {
    if (GenTemplates.IGNORE.equals(strategy)) {
      return;
    }
    Path target = Path.of(dir).resolve(file.path());
    if (Files.exists(target)) {
      if (GenTemplates.EXIST_IGNORE.equals(strategy)) {
        return;
      }
      if (GenTemplates.ADD.equals(strategy)) {
        target = target.resolveSibling(target.getFileName() + ".add");
      }
    }
    try {
      Files.createDirectories(target.getParent());
      Files.writeString(target, file.code());
    } catch (IOException e) {
      throw ChatErrors.of("写入文件失败：" + target + " " + e.getMessage());
    }
  }
}
