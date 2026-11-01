package nexus.io.tio.chat.service.base;

import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

import nexus.io.jfinal.aop.Aop;
import nexus.io.tio.chat.facade.BaseFacade;

/**
 * BaseFacade 的本地实现
 */
public class BaseFacadeLocal implements BaseFacade {

  @Override
  public List<String> employeeRoleCodes(Long employeeId) {
    return Aop.get(BaseAdminService.class).employeeRoleCodes(employeeId);
  }

  @Override
  public boolean isAdmin(Long employeeId) {
    return Aop.get(BaseAdminService.class).isAdmin(employeeId);
  }

  @Override
  public List<String> employeeResourceCodes(Long employeeId, Long applicationId) {
    return Aop.get(BaseAdminService.class).employeeResourceCodes(employeeId, applicationId);
  }

  @Override
  public Map<String, List<Kv>> dictOptions(List<Map<String, Object>> queries) {
    return Aop.get(BaseExtService.class).dictOptions(queries);
  }

  @Override
  public Map<String, String> params(List<String> keys) {
    return Aop.get(BaseExtService.class).params(keys);
  }

  @Override
  public Kv visibleResource(Long employeeId, Long applicationId) {
    return Aop.get(BaseExtService.class).visibleResource(employeeId, applicationId);
  }
}
