package ${modelPackage};

<#list imports as item>
import ${item};
</#list>
<#if imports?has_content>

</#if>
<#if table.isLombok>
import lombok.Data;

</#if>
/**
 * ${(table.swaggerComment!table.name)?replace("*/", "* /")}
 *
 * @author ${author}
 * @date ${datetime}
 */
<#if table.isLombok>
@Data
</#if>
public class ${table.entityName} {
<#list modelFields as field>
  /** ${(field.swaggerComment!field.name)?replace("*/", "* /")} */
  private ${field.modelType} ${field.javaField};
</#list>
<#if !table.isLombok>
<#list modelFields as field>

  public ${field.modelType} get${field.javaField?cap_first}() {
    return ${field.javaField};
  }

  public void set${field.javaField?cap_first}(${field.modelType} ${field.javaField}) {
    this.${field.javaField} = ${field.javaField};
  }
</#list>
</#if>
}
