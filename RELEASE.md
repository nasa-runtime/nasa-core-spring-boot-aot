# Maven Central 发布

## 前置条件

- POM 使用非 SNAPSHOT 版本，目标 `nasa-core` 版本与 README 一致。
- Central namespace、发布 token 和 GPG 签名密钥已经配置在 Maven `central` server 与本地签名环境中。
- 中英文 README、贡献指南、安全策略、许可证、sources jar 和 javadoc jar 均属于发布归档。

## 本地验收

```bash
mvn clean verify
mvn package
jar tf target/nasa-core-spring-boot-aot-1.0.4.jar
jar tf target/nasa-core-spring-boot-aot-1.0.4-sources.jar
jar tf target/nasa-core-spring-boot-aot-1.0.4-javadoc.jar
```

从最终 jar 核对 `META-INF/spring/aot.factories`、README、许可证和实现类；从 POM 核对坐标、项目 URL、SCM、
开发者与许可证。确认依赖树没有 SNAPSHOT。

## 发布顺序

1. 完成目标分支提交并推送。
2. 等待远端 CI 全部通过，核对待发布 tag、分支与远端提交 SHA 完全一致。
3. 获得维护者对该坐标和提交的明确发布授权。
4. 执行 `mvn -Pcentral-release clean deploy`，检查签名与 Central 校验结果。
5. 在 Central Portal 发布已验证的 deployment；`autoPublish` 保持关闭，避免未经复核自动公开。
6. 从 Maven Central 回读 POM、jar、sources、javadoc、README 和校验和，并用一个空 Maven 项目解析坐标。

不可变坐标出现文档或归档遗漏时，不以本地改动代替线上状态；应停止验收并准备新的补丁版本。任何公开上传
都必须在第 3 步授权后进行。
