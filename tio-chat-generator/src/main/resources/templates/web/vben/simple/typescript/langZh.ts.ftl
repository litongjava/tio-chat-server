export default {
  table: { title: '${(table.swaggerComment!)?js_string}列表' },
<#list allFields as field>
  ${field.javaField}: '${(field.swaggerComment!)?js_string}',
</#list>
};
