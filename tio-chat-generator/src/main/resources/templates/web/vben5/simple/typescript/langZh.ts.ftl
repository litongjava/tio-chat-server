{
  "table": { "title": "${(table.swaggerComment!)?json_string}" },
<#list allFields as field>
  "${field.javaField}": "${(field.swaggerComment!)?json_string}"<#if field_has_next>,</#if>
</#list>
}
