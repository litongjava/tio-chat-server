package nexus.io.tio.chat.facade;

/**
 * 跨服务能力的入口。各服务启动时登记本服务的本地实现；未登记的能力由远程实现提供，
 * 因此所有模块打包成一个服务时走本地调用，分服务部署时走服务间调用。
 */
public class Facades {
  private static volatile BaseFacade base;
  private static volatile ImFacade im;

  public static BaseFacade base() {
    if (base == null) {
      throw new IllegalStateException("BaseFacade is not available");
    }
    return base;
  }

  public static ImFacade im() {
    if (im == null) {
      throw new IllegalStateException("ImFacade is not available");
    }
    return im;
  }

  public static final String BASE_SERVICE = "luohuo-base-server";
  public static final String IM_SERVICE = "luohuo-im-server";
  public static final String BASE_PATH = "/internal/facade/base";
  public static final String IM_PATH = "/internal/facade/im";

  /**
   * 本进程没有登记本地实现的能力，改为按服务名远程调用
   */
  public static synchronized void useRemoteForMissing() {
    if (base == null) {
      base = FacadeClient.create(BaseFacade.class, BASE_SERVICE, BASE_PATH);
    }
    if (im == null) {
      im = FacadeClient.create(ImFacade.class, IM_SERVICE, IM_PATH);
    }
  }

  public static void setBase(BaseFacade facade) {
    base = facade;
  }

  public static void setIm(ImFacade facade) {
    im = facade;
  }
}
