app.name=${applicationName}
server.port=${serverPort?c}
server.shutdown.hook=true
<#if cloud>

nacos.server-addr=127.0.0.1:8848
nacos.namespace=hula-dev
nacos.username=nacos
nacos.password=nacos
nacos.discovery.ip=127.0.0.1
</#if>
