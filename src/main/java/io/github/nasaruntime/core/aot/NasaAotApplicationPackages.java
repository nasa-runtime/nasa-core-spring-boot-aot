package io.github.nasaruntime.core.aot;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 业务作用：从已经建立的 Spring BeanDefinition 元数据解析应用扫描边界，供各 Nasa AOT 适配器共享，
 * 避免实例化启动类或从宽泛的 {@code com} 根包遍历第三方依赖。
 */
public final class NasaAotApplicationPackages {

    public static final String SCAN_PACKAGES_PROPERTY = "nasa.aot.scan-packages";

    /**
     * 业务作用：私有化构造，本类只提供确定性的应用包解析入口。
     * <p>
     * 参数说明：无。
     * 返回：不对外提供实例。
     */
    private NasaAotApplicationPackages() {}

    /**
     * 业务作用：合并 Boot 自动配置包、启动类显式扫描包和构建期追加包，形成 AOT 业务类型扫描的唯一边界。
     *
     * @param beanFactory 已完成 BeanDefinition 注册的应用 BeanFactory
     * @return 去重且保持发现顺序的包名集合
     * @throws IllegalStateException 无法确定任何安全扫描包时抛出，防止静默生成不完整镜像
     */
    public static Set<String> resolve(ConfigurableListableBeanFactory beanFactory) {
        LinkedHashSet<String> packages = new LinkedHashSet<>();
        if (AutoConfigurationPackages.has(beanFactory)) {
            addPackages(packages, AutoConfigurationPackages.get(beanFactory));
        }
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName, false);
            if (beanType != null) {
                addConfigurationPackages(packages, ClassUtils.getUserClass(beanType));
            }
        }
        addDelimitedPackages(packages, System.getProperty(SCAN_PACKAGES_PROPERTY));
        if (packages.isEmpty()) {
            throw new IllegalStateException("Cannot determine application packages; set -D"
                    + SCAN_PACKAGES_PROPERTY + "=com.example.app during process-aot");
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(packages));
    }

    /**
     * 业务作用：从启动配置类型读取组件扫描声明，并在未显式声明时采用启动类所在包。
     *
     * @param packages 当前扫描包集合
     * @param type     BeanDefinition 解析出的用户类型
     *                 返回：无返回值；非启动配置类型不会改变集合。
     */
    private static void addConfigurationPackages(Set<String> packages, Class<?> type) {
        MergedAnnotations annotations = MergedAnnotations.from(
                type, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);
        MergedAnnotation<SpringBootApplication> application = annotations.get(SpringBootApplication.class);
        if (application.isPresent()) {
            String[] names = application.getStringArray("scanBasePackages");
            Class<?>[] markers = application.getClassArray("scanBasePackageClasses");
            addPackages(packages, Arrays.asList(names));
            addMarkerPackages(packages, markers);
            if (names.length == 0 && markers.length == 0) {
                addPackage(packages, type.getPackageName());
            }
            return;
        }
        if (annotations.isPresent(SpringBootConfiguration.class)) {
            addPackage(packages, type.getPackageName());
        }
    }

    /**
     * 业务作用：把显式 marker class 转换为其所在包，支持类型安全的 scanBasePackageClasses 配置。
     *
     * @param packages 当前扫描包集合
     * @param markers  作为扫描边界的 marker class
     *                 返回：无返回值；每个合法 marker 包会加入集合。
     */
    private static void addMarkerPackages(Set<String> packages, Class<?>[] markers) {
        for (Class<?> marker : markers) {
            addPackage(packages, marker.getPackageName());
        }
    }

    /**
     * 业务作用：批量归一并加入候选包，忽略空值但拒绝过宽的顶级命名空间。
     *
     * @param packages 当前扫描包集合
     * @param names    候选包名
     *                 返回：无返回值；合法包名会加入集合。
     */
    private static void addPackages(Set<String> packages, Collection<String> names) {
        for (String name : names) {
            addPackage(packages, name);
        }
    }

    /**
     * 业务作用：解析逗号分隔的构建期追加包，允许非常规目录布局显式扩大扫描范围。
     *
     * @param packages 当前扫描包集合
     * @param value    逗号分隔配置值，允许为空
     *                 返回：无返回值；合法配置项会加入集合。
     */
    private static void addDelimitedPackages(Set<String> packages, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        addPackages(packages, Arrays.asList(value.split(",")));
    }

    /**
     * 业务作用：校验单个扫描包不能退化为常见公共顶级根，避免把整个依赖 classpath 纳入反射闭包。
     *
     * @param packages  当前扫描包集合
     * @param candidate 候选包名
     *                  返回：无返回值；合法包名会加入集合，危险根包会抛出异常。
     */
    private static void addPackage(Set<String> packages, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        String packageName = candidate.trim();
        if (!packageName.contains(".") || Set.of("com", "org", "io", "net").contains(packageName)) {
            throw new IllegalStateException("AOT scan package is too broad: " + packageName);
        }
        packages.add(packageName);
    }
}
