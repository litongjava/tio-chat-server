package ${modelPackage};

<#list imports as item>
import ${item};
</#list>
<#if imports?has_content>

</#if>
<#if table.isLombok>
<#if table.isChain>
import lombok.experimental.Accessors;
</#if>
import lombok.Data;

</#if>
<#if mainSub && sub.modelPackage != modelPackage>
import ${sub.modelPackage}.${sub.table.entityName};

</#if>
/**
 * ${(table.swaggerComment!table.name)?replace("*/", "* /")}
 *
 * @author ${author}
 * @date ${datetime}
 */
<#if table.isLombok>
@Data
<#if table.isChain>
@Accessors(chain = true)
</#if>
</#if>
public class ${table.entityName} {
<#if table.isColumnConstant>
<#list modelFields as field>
  public static final String COLUMN_${field.name?upper_case} = "${field.name}";
</#list>

</#if>
<#list modelFields as field>
  /** ${(field.swaggerComment!field.name)?replace("*/", "* /")} */
  private ${field.modelType} ${field.javaField};
</#list>
<#if mainSub>
  /** 要新增的从表数据 */
  private List<${sub.table.entityName}> insertList;
  /** 要修改的从表数据 */
  private List<${sub.table.entityName}> updateList;
  /** 要删除的从表数据的 id */
  private List<${sub.pkField.javaType}> deleteList;
</#if>
<#if !table.isLombok>
<#list modelFields as field>

  public ${field.modelType} get${field.javaField?cap_first}() {
    return ${field.javaField};
  }

  public <#if table.isChain>${table.entityName}<#else>void</#if> set${field.javaField?cap_first}(${field.modelType} ${field.javaField}) {
    this.${field.javaField} = ${field.javaField};
<#if table.isChain>
    return this;
</#if>
  }
</#list>
<#if mainSub>

  public List<${sub.table.entityName}> getInsertList() {
    return insertList;
  }

  public void setInsertList(List<${sub.table.entityName}> insertList) {
    this.insertList = insertList;
  }

  public List<${sub.table.entityName}> getUpdateList() {
    return updateList;
  }

  public void setUpdateList(List<${sub.table.entityName}> updateList) {
    this.updateList = updateList;
  }

  public List<${sub.pkField.javaType}> getDeleteList() {
    return deleteList;
  }

  public void setDeleteList(List<${sub.pkField.javaType}> deleteList) {
    this.deleteList = deleteList;
  }
</#if>
</#if>
}
