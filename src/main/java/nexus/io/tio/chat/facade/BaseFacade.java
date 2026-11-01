package nexus.io.tio.chat.facade;

import java.util.List;
import java.util.Map;

import com.jfinal.kit.Kv;

/**
 * 基础服务（luohuo-base-server）对其他服务开放的能力：角色、资源权限、字典与参数。
 * 同进程运行时直接调用本地实现，分服务部署时通过服务名远程调用。
 */
public interface BaseFacade {

  List<String> employeeRoleCodes(Long employeeId);

  boolean isAdmin(Long employeeId);

  List<String> employeeResourceCodes(Long employeeId, Long applicationId);

  Map<String, List<Kv>> dictOptions(List<Map<String, Object>> queries);

  Map<String, String> params(List<String> keys);

  Kv visibleResource(Long employeeId, Long applicationId);
}
