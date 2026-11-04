package nexus.io.tio.chat.service.common;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;
import nexus.io.hook.HookCan;
import nexus.io.redis.Redis;
import nexus.io.tio.utils.thread.TioThreadUtils;

/**
 * Redis 上的延时任务：任务保存在 Redis 中，进程重启不会丢失；多个实例同时轮询时，
 * ZREM 成功的实例才执行，同一任务只执行一次。同一个 key 再次安排会替换原来的任务。
 */
@Slf4j
public class DelayedTaskService {
  private static final String QUEUE = "chat:delayed:queue:";
  private static final String PAYLOAD = "chat:delayed:payload:";
  private static final long POLL_MILLIS = 500;
  private static final int BATCH = 100;

  private final Map<String, Consumer<String>> handlers = new ConcurrentHashMap<>();
  private ScheduledExecutorService poller;

  /** 登记任务类型的处理器，并开始轮询这个类型 */
  public synchronized void register(String type, Consumer<String> handler) {
    handlers.put(type, handler);
    if (poller == null) {
      poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "chat-delayed-task");
        thread.setDaemon(true);
        return thread;
      });
      poller.scheduleWithFixedDelay(this::poll, POLL_MILLIS, POLL_MILLIS, TimeUnit.MILLISECONDS);
      HookCan.me().addDestroyMethod(poller::shutdownNow);
    }
  }

  public void schedule(String type, String key, String payload, long delayMillis) {
    double due = System.currentTimeMillis() + delayMillis;
    Redis.call(jedis -> {
      jedis.hset(PAYLOAD + type, key, payload);
      return jedis.zadd(QUEUE + type, due, key);
    });
  }

  public void cancel(String type, String key) {
    Redis.call(jedis -> {
      jedis.zrem(QUEUE + type, key);
      return jedis.hdel(PAYLOAD + type, key);
    });
  }

  private void poll() {
    for (Map.Entry<String, Consumer<String>> entry : handlers.entrySet()) {
      try {
        pollType(entry.getKey(), entry.getValue());
      } catch (Exception e) {
        log.error("poll delayed tasks of {} failed", entry.getKey(), e);
      }
    }
  }

  private void pollType(String type, Consumer<String> handler) {
    double now = System.currentTimeMillis();
    List<String> keys = Redis.call(jedis -> List.copyOf(jedis.zrangeByScore(QUEUE + type, 0, now, 0, BATCH)));
    for (String key : keys) {
      Long claimed = Redis.call(jedis -> jedis.zrem(QUEUE + type, key));
      if (claimed == null || claimed == 0) {
        continue;
      }
      String payload = Redis.call(jedis -> {
        String value = jedis.hget(PAYLOAD + type, key);
        jedis.hdel(PAYLOAD + type, key);
        return value;
      });
      TioThreadUtils.submit(() -> run(type, handler, payload));
    }
  }

  private void run(String type, Consumer<String> handler, String payload) {
    try {
      handler.accept(payload);
    } catch (Exception e) {
      log.error("delayed task of {} failed: {}", type, payload, e);
    }
  }
}
