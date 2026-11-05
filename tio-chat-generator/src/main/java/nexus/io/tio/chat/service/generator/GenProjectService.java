package nexus.io.tio.chat.service.generator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import nexus.io.tio.chat.model.generator.ProjectGenerateReq;
import nexus.io.tio.chat.utils.ChatErrors;
import nexus.io.tio.utils.environment.EnvUtils;

/**
 * 生成一个可以直接运行的 tio-boot 服务项目；类型 CLOUD 的项目启动后注册到 Nacos
 */
public class GenProjectService {
  private static final Configuration CONFIG = new Configuration(Configuration.VERSION_2_3_31);
  private static final String ROOT = "/templates/project/";

  static {
    CONFIG.setDefaultEncoding(StandardCharsets.UTF_8.name());
    CONFIG.setClassForTemplateLoading(GenProjectService.class, "/");
    CONFIG.setNumberFormat("computer");
  }

  public ProjectGenerateReq getDef() {
    ProjectGenerateReq def = new ProjectGenerateReq();
    def.setAuthor(GenConfig.author());
    def.setType("CLOUD");
    def.setOutputDir(GenConfig.outputDir());
    def.setProjectPrefix(GenConfig.projectPrefix());
    def.setParent(GenConfig.parent());
    def.setGroupId(GenConfig.parent());
    def.setUtilParent("nexus.io");
    def.setUtilGroupId("nexus.io");
    def.setVersion("1.0.0");
    def.setServerPort(8080);
    def.setSeata(false);
    return def;
  }

  public boolean generate(ProjectGenerateReq req) {
    GenCodeService.requireDev();
    ChatErrors.require(req.getOutputDir() != null && !req.getOutputDir().isEmpty(), "请填写输出路径");
    Map<String, String> files = files(req);
    try {
      for (Map.Entry<String, String> file : files.entrySet()) {
        Path target = Path.of(req.getOutputDir()).resolve(file.getKey());
        Files.createDirectories(target.getParent());
        Files.writeString(target, file.getValue());
      }
    } catch (IOException e) {
      throw ChatErrors.of("写入项目失败：" + e.getMessage());
    }
    return true;
  }

  public byte[] download(ProjectGenerateReq req) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
      for (Map.Entry<String, String> file : files(req).entrySet()) {
        zip.putNextEntry(new ZipEntry(file.getKey()));
        zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
    } catch (IOException e) {
      throw ChatErrors.of("打包失败：" + e.getMessage());
    }
    return out.toByteArray();
  }

  private Map<String, String> files(ProjectGenerateReq req) {
    require(req.getAuthor(), "请填写作者");
    require(req.getProjectPrefix(), "请填写项目前缀");
    require(req.getServiceName(), "请填写服务名");
    require(req.getModuleName(), "请填写模块名");
    require(req.getParent(), "请填写基础包");
    require(req.getGroupId(), "请填写服务groupId");
    require(req.getVersion(), "请填写版本号");
    require(req.getDescription(), "请填写服务中文名");
    ChatErrors.require(req.getServerPort() != null, "请填写端口号");
    ChatErrors.require(req.getServiceName().matches("[a-zA-Z][a-zA-Z0-9_-]*"), "服务名只能包含字母、数字、下划线和中划线");
    ChatErrors.require(req.getModuleName().matches("[a-z][a-z0-9_]*"), "模块名只能包含小写字母、数字和下划线");
    ChatErrors.require(req.getParent().matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*"), "基础包不合法");

    String artifact = req.getProjectPrefix() + "-" + req.getServiceName();
    String base = req.getParent() + "." + req.getModuleName();
    String appName = GenColumns.capitalize(GenColumns.javaField(req.getServiceName().replace('-', '_')));
    Map<String, Object> model = new HashMap<>();
    model.put("groupId", req.getGroupId());
    model.put("artifactId", artifact);
    model.put("version", req.getVersion());
    model.put("description", req.getDescription());
    model.put("author", req.getAuthor());
    model.put("basePackage", base);
    model.put("appName", appName);
    model.put("cloud", !"BOOT".equals(req.getType()));
    model.put("serverPort", req.getServerPort());
    String applicationName = req.getApplicationName();
    model.put("applicationName", applicationName == null || applicationName.isEmpty() ? artifact : applicationName);
    model.put("tioBootVersion", EnvUtils.get("generator.tio-boot-version", "2.1.9"));
    model.put("tioBootCloudVersion", EnvUtils.get("generator.tio-boot-cloud-version", "1.0.0"));
    model.put("javaDbVersion", EnvUtils.get("generator.java-db-version", "1.6.2"));
    model.put("jedisVersion", EnvUtils.get("generator.jedis-version", "4.3.1"));
    model.put("seata", Boolean.TRUE.equals(req.getSeata()));
    model.put("seataVersion", EnvUtils.get("generator.seata-version", "2.0.0"));
    model.put("jfinalAopVersion", EnvUtils.get("generator.jfinal-aop-version", JFINAL_AOP_VERSION));

    String src = artifact + "/src/main/java/" + base.replace('.', '/') + "/";
    Map<String, String> files = new LinkedHashMap<>();
    files.put(artifact + "/pom.xml", render("pom.xml.ftl", model));
    files.put(src + appName + "App.java", render("App.java.ftl", model));
    files.put(src + "config/" + appName + "Config.java", render("Config.java.ftl", model));
    files.put(src + "handler/HealthHandler.java", render("HealthHandler.java.ftl", model));
    files.put(artifact + "/src/main/resources/app.properties", render("app.properties.ftl", model));
    Map<String, String> safe = new LinkedHashMap<>();
    files.forEach((path, code) -> safe.put(GenCodeService.safePath(path), code));
    return safe;
  }

  private void require(String value, String message) {
    ChatErrors.require(value != null && !value.isEmpty(), message);
  }

  private String render(String name, Map<String, Object> model) {
    try {
      StringWriter writer = new StringWriter();
      CONFIG.getTemplate(ROOT + name).process(model, writer);
      return writer.toString();
    } catch (IOException | TemplateException e) {
      throw ChatErrors.of("生成项目失败：" + e.getMessage());
    }
  }

  private static final String JFINAL_AOP_VERSION = "1.4.0";
}
