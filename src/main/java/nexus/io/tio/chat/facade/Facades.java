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

  public static void setBase(BaseFacade facade) {
    base = facade;
  }

  public static void setIm(ImFacade facade) {
    im = facade;
  }
}
