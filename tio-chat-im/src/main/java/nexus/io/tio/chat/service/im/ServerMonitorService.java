package nexus.io.tio.chat.service.im;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.jfinal.kit.Kv;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.CentralProcessor.TickType;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OSFileStore;
import oshi.util.Util;

/**
 * 服务监控：CPU、内存、JVM、操作系统与磁盘，字段和单位与原接口一致
 */
public class ServerMonitorService {
  private static final long GB = 1024 * 1024 * 1024;
  private static final long MB = 1024 * 1024;
  private static final long KB = 1024;
  private static final long CPU_SAMPLE_MILLIS = 1000;
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public Kv snapshot() {
    SystemInfo info = new SystemInfo();
    return Kv.by("cpu", cpu(info.getHardware().getProcessor())).set("mem", mem(info.getHardware().getMemory()))
        .set("jvm", jvm()).set("sys", sys()).set("sysFiles", files(info));
  }

  private Kv cpu(CentralProcessor processor) {
    long[] prev = processor.getSystemCpuLoadTicks();
    Util.sleep(CPU_SAMPLE_MILLIS);
    long[] now = processor.getSystemCpuLoadTicks();
    double sys = delta(now, prev, TickType.SYSTEM);
    double user = delta(now, prev, TickType.USER);
    double wait = delta(now, prev, TickType.IOWAIT);
    double idle = delta(now, prev, TickType.IDLE);
    double total = sys + user + wait + idle + delta(now, prev, TickType.NICE) + delta(now, prev, TickType.IRQ)
        + delta(now, prev, TickType.SOFTIRQ) + delta(now, prev, TickType.STEAL);
    return Kv.by("cpuNum", processor.getLogicalProcessorCount()).set("total", round(total * 100, 2))
        .set("sys", percent(sys, total)).set("used", percent(user, total)).set("wait", percent(wait, total))
        .set("free", percent(idle, total));
  }

  private double delta(long[] now, long[] prev, TickType type) {
    return now[type.getIndex()] - prev[type.getIndex()];
  }

  private Kv mem(GlobalMemory memory) {
    double total = memory.getTotal();
    double free = memory.getAvailable();
    double used = total - free;
    return Kv.by("total", round(total / GB, 2)).set("used", round(used / GB, 2)).set("free", round(free / GB, 2))
        .set("usage", percent(used, total));
  }

  private Kv jvm() {
    Runtime runtime = Runtime.getRuntime();
    double total = runtime.totalMemory();
    double free = runtime.freeMemory();
    long startMillis = ManagementFactory.getRuntimeMXBean().getStartTime();
    return Kv.by("total", round(total / MB, 2)).set("max", round((double) runtime.maxMemory() / MB, 2))
        .set("free", round(free / MB, 2)).set("used", round((total - free) / MB, 2))
        .set("usage", percent(total - free, total)).set("name", ManagementFactory.getRuntimeMXBean().getVmName())
        .set("version", System.getProperty("java.version")).set("home", System.getProperty("java.home"))
        .set("startTime", TIME.format(Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault())))
        .set("runTime", runTime(startMillis))
        .set("inputArgs", ManagementFactory.getRuntimeMXBean().getInputArguments().toString());
  }

  private String runTime(long startMillis) {
    Duration duration = Duration.ofMillis(System.currentTimeMillis() - startMillis);
    return duration.toDays() + "天" + duration.toHoursPart() + "小时" + duration.toMinutesPart() + "分钟";
  }

  private Kv sys() {
    String name = "未知";
    String ip = "127.0.0.1";
    try {
      InetAddress local = InetAddress.getLocalHost();
      name = local.getHostName();
      ip = local.getHostAddress();
    } catch (UnknownHostException ignored) {
      // keep the defaults
    }
    return Kv.by("computerName", name).set("computerIp", ip).set("userDir", System.getProperty("user.dir"))
        .set("osName", System.getProperty("os.name")).set("osArch", System.getProperty("os.arch"));
  }

  private List<Kv> files(SystemInfo info) {
    List<Kv> result = new ArrayList<>();
    for (OSFileStore store : info.getOperatingSystem().getFileSystem().getFileStores()) {
      long total = store.getTotalSpace();
      long free = store.getUsableSpace();
      long used = total - free;
      result.add(Kv.by("dirName", store.getMount()).set("sysTypeName", store.getType()).set("typeName", store.getName())
          .set("total", size(total)).set("free", size(free)).set("used", size(used))
          .set("usage", total == 0 ? 0D : percent(used, total)));
    }
    return result;
  }

  private String size(long bytes) {
    if (bytes >= GB) {
      return String.format("%.1f GB", (float) bytes / GB);
    }
    if (bytes >= MB) {
      float value = (float) bytes / MB;
      return String.format(value > 100 ? "%.0f MB" : "%.1f MB", value);
    }
    if (bytes >= KB) {
      float value = (float) bytes / KB;
      return String.format(value > 100 ? "%.0f KB" : "%.1f KB", value);
    }
    return bytes + " B";
  }

  private double percent(double part, double total) {
    if (total == 0) {
      return 0D;
    }
    return round(round(part / total, 4) * 100, 2);
  }

  private double round(double value, int scale) {
    return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
  }
}
