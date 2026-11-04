package ${enumPackage};

/**
 * ${enumType.enumName}
 *
 * @author ${author}
 * @date ${datetime}
 */
public enum ${enumType.enumName} {
<#list enumType.items as item>
  ${item.key}("${item.code?j_string}", "${item.desc?j_string}")<#if item?has_next>,<#else>;</#if>
</#list>

  private final String code;
  private final String desc;

  ${enumType.enumName}(String code, String desc) {
    this.code = code;
    this.desc = desc;
  }

  public String getCode() {
    return code;
  }

  public String getDesc() {
    return desc;
  }
}
