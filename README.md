# nasa-core-spring-boot-aot

`nasa-core-spring-boot-aot` 为使用 `nasa-core` 的 Spring Boot 应用生成 AOT 与 GraalVM native-image
提示。它在构建期发现应用真实基础包，保留 `Initialization`、`@ThreadPool`、Java Serialization 以及
通用反射工具会动态访问的业务类型；无需修改 `nasa-core`，也不会把扫描根固定为 `com`。

[English](README.en.md)

## 核心价值

- 让 `nasa-core` 的动态反射合同进入 Spring AOT 生成结果，而不要求每个业务项目维护 reachability metadata。
- 从 Spring 已解析的应用结构确定扫描边界，兼容 `com`、`org`、`io` 以及企业自定义域名包。
- 在构建期拒绝未知或过宽的扫描范围，避免悄悄产出启动后才失败的原生镜像。
- 作为 Redis 与 Saga AOT 适配器共享的扫描基础设施，统一包发现、类加载和诊断语义。

## 运行架构

```text
Spring BeanDefinition / AutoConfigurationPackages
                    │
                    ▼
        NasaAotApplicationPackages
                    │ 安全基础包
                    ▼
           NasaAotClassScanner
                    │
         ┌──────────┼──────────┐
         ▼          ▼          ▼
   reflection   serialization  动态成员
                               Initialization / @ThreadPool
```

Spring AOT 通过 `META-INF/spring/aot.factories` 自动装载处理器。处理器先读取
`AutoConfigurationPackages`，再遍历 BeanDefinition 的类型元数据寻找 `@SpringBootApplication` 或
`@SpringBootConfiguration`。对 `@SpringBootApplication`，它依次合并 `scanBasePackages`、
`scanBasePackageClasses`；两者均为空时使用启动类所在包。这个过程不会创建业务 Bean。

扫描始终限定在发现的应用包内。`com`、`org`、`io`、`net` 等公共顶级根会被拒绝，因此依赖数量不会决定
扫描结果。类型按类名排序后登记，使相同输入产生稳定提示。

## 兼容性

- JDK 21 或更高版本
- Spring Boot 3.5.x / Spring Framework 6.2.x
- `nasa-core` 1.0.4
- Maven 3.6.3 或更高版本

适配器版本与目标库版本保持一致。升级 `nasa-core` 时，应同时选择针对该版本发布的适配器。

## 使用

```xml
<dependency>
    <groupId>io.github.nasa-runtime</groupId>
    <artifactId>nasa-core-spring-boot-aot</artifactId>
    <version>1.0.4</version>
</dependency>
```

正常位于启动类包或其子包的业务代码不需要额外配置。AOT 构建示例：

```bash
./mvnw -Pnative native:compile
```

若业务类型不在 Boot 自动配置包内，可追加逗号分隔的包。Spring Boot Maven Plugin 会在独立 JVM 中执行
`process-aot`，因此 Maven 构建需要通过 `spring-boot.aot.jvmArguments` 透传：

```bash
./mvnw -Dspring-boot.aot.jvmArguments="-Dnasa.aot.scan-packages=com.acme.shared,dev.partner.contracts" \
  -Pnative native:compile
```

直接调用 AOT JVM 时可设置 `-Dnasa.aot.scan-packages=...`。配置值必须是至少两段的具体包名，不能使用
通配符，也不能配置公共顶级根。

## 登记内容

- `nasa-core` 自有稳定类型的反射元数据，以及具体 `Serializable` 类型的序列化元数据。
- 应用基础包内非注解、非合成业务类型的构造器、字段、方法和嵌套类型。
- `Initialization` 实现的 `before`、`initialize`、`after` 调用入口。
- `@ThreadPool` 字段及单参数方法的动态访问入口。
- 具体且非抽象的 `Serializable` 业务类型。

完整成员登记是 `nasa-core` 通用反射 API 能接收任意业务类型这一合同的直接结果。若应用包过大，应拆分
启动包或通过 marker class 精确声明扫描边界，以控制构建时间和镜像元数据体积。

## 失败语义与观测

- 无法推导应用包时，AOT 构建失败，并提示设置 `nasa.aot.scan-packages`。
- 包名过宽时，AOT 构建失败，不会回退到 classpath 全量扫描。
- 扫描资源不可读或业务类型依赖不完整时，AOT 构建失败，并在异常中给出包名或类型名。
- 处理器只在构建期生成 hints，不启动线程、不连接外部服务、不改变运行期业务状态。

构建日志中的异常类型和 `target/spring-aot/` 下生成的 native 配置是主要诊断入口。

## 不解决的问题

本项目不替代 Spring Boot 自身的 AOT 推导，不提供 GraalVM 工具链，不为任意第三方库补充 hints，也不改变
`nasa-core` 的 JVM 行为。JNI、动态字节码、脚本引擎或应用自行加载的 classpath 资源仍由应用负责声明。

## 构建与发布

```bash
mvn clean verify
```

贡献、安全报告和 Maven Central 发布门禁分别见 [CONTRIBUTING.md](CONTRIBUTING.md)、
[SECURITY.md](SECURITY.md) 与 [RELEASE.md](RELEASE.md)。本项目采用 Apache-2.0 OR MIT 双许可证。
