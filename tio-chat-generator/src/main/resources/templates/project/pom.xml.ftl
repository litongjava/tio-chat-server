<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>${groupId}</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>${version}</version>
  <description>${description}</description>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
  </properties>

  <dependencies>
    <dependency>
      <groupId>nexus.io</groupId>
      <artifactId>tio-boot</artifactId>
      <version>${tioBootVersion}</version>
    </dependency>
<#if cloud>
    <dependency>
      <groupId>nexus.io</groupId>
      <artifactId>tio-boot-cloud-nacos</artifactId>
      <version>${tioBootCloudVersion}</version>
    </dependency>
</#if>
<#if seata>
    <!-- 需要分布式事务时使用；客户端的初始化和事务注解需要在项目中自行接入 -->
    <dependency>
      <groupId>io.seata</groupId>
      <artifactId>seata-all</artifactId>
      <version>${seataVersion}</version>
    </dependency>
</#if>
    <dependency>
      <groupId>nexus.io</groupId>
      <artifactId>jfinal-aop</artifactId>
      <version>${jfinalAopVersion}</version>
    </dependency>
    <!-- 生成的后端代码用到的数据库和 Redis -->
    <dependency>
      <groupId>nexus.io</groupId>
      <artifactId>java-db</artifactId>
      <version>${javaDbVersion}</version>
    </dependency>
    <dependency>
      <groupId>com.zaxxer</groupId>
      <artifactId>HikariCP</artifactId>
      <version>5.1.0</version>
    </dependency>
    <dependency>
      <groupId>com.mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <version>8.4.0</version>
    </dependency>
    <dependency>
      <groupId>redis.clients</groupId>
      <artifactId>jedis</artifactId>
      <version>${jedisVersion}</version>
    </dependency>
    <dependency>
      <groupId>ch.qos.logback</groupId>
      <artifactId>logback-classic</artifactId>
      <version>1.3.14</version>
    </dependency>
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <version>1.18.44</version>
      <scope>provided</scope>
    </dependency>
  </dependencies>
</project>
