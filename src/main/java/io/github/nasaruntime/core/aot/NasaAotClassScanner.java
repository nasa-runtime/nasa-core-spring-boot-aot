package io.github.nasaruntime.core.aot;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务作用：在 Spring AOT 构建 classpath 上枚举受控基础包内的 class，向三个独立适配器提供一致的扫描语义。
 */
public final class NasaAotClassScanner {

    /**
     * 业务作用：私有化构造，本类只提供无状态扫描能力。
     * <p>
     * 参数说明：无。
     * 返回：不对外提供实例。
     */
    private NasaAotClassScanner() {}

    /**
     * 业务作用：扫描多个应用基础包并加载其中可形成稳定类名的业务类型，供构建期检查注解和成员结构。
     *
     * @param classLoader AOT 应用类加载器
     * @param packages    已通过安全边界校验的基础包
     * @return 按类名排序且去重的类型列表
     * @throws IllegalStateException 资源读取或业务类加载失败时抛出并指出具体类型
     */
    public static List<Class<?>> scan(ClassLoader classLoader, Collection<String> packages) {
        return scan(classLoader, packages, Set.of());
    }

    /**
     * 业务作用：扫描多个应用基础包，同时在类加载前排除已知可选集成包，避免缺失可选依赖阻断其它提示生成。
     *
     * @param classLoader             AOT 应用类加载器
     * @param packages                已通过安全边界校验的基础包
     * @param excludedPackagePrefixes 不应进入类加载阶段的完整包名前缀
     * @return 按类名排序且去重的类型列表
     * @throws IllegalStateException 资源读取或未排除业务类加载失败时抛出并指出具体类型
     */
    public static List<Class<?>> scan(
            ClassLoader classLoader,
            Collection<String> packages,
            Collection<String> excludedPackagePrefixes) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(classLoader);
        CachingMetadataReaderFactory readers = new CachingMetadataReaderFactory(resolver);
        Map<String, Class<?>> classes = new LinkedHashMap<>();
        for (String packageName : packages) {
            scanPackage(classLoader, resolver, readers, packageName, excludedPackagePrefixes, classes);
        }
        ArrayList<Class<?>> result = new ArrayList<>(classes.values());
        result.sort(Comparator.comparing(Class::getName));
        return List.copyOf(result);
    }

    /**
     * 业务作用：读取单个基础包的 class 资源，过滤编译器生成的瞬态类型并加入全局去重表。
     *
     * @param classLoader             应用类加载器
     * @param resolver                classpath 资源解析器
     * @param readers                 缓存的 class 元数据读取器
     * @param packageName             当前基础包
     * @param excludedPackagePrefixes 类加载前排除的完整包名前缀
     * @param classes                 已发现类型表
     *                                返回：无返回值；发现结果写入 classes。
     */
    private static void scanPackage(
            ClassLoader classLoader,
            PathMatchingResourcePatternResolver resolver,
            CachingMetadataReaderFactory readers,
            String packageName,
            Collection<String> excludedPackagePrefixes,
            Map<String, Class<?>> classes) {
        String packagePath = ClassUtils.convertClassNameToResourcePath(packageName);
        String pattern = PathMatchingResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                + packagePath + "/**/*.class";
        try {
            for (Resource resource : resolver.getResources(pattern)) {
                MetadataReader reader = readers.getMetadataReader(resource);
                String className = reader.getClassMetadata().getClassName();
                if (isStableTypeName(className)
                        && !isExcluded(className, excludedPackagePrefixes)) {
                    classes.computeIfAbsent(className, name -> loadClass(classLoader, name));
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("Cannot scan AOT application package " + packageName, error);
        }
    }

    /**
     * 业务作用：排除没有稳定业务身份的 package/module 描述类和运行期生成代理。
     *
     * @param className class 元数据中的二进制名
     * @return 适合进入 AOT 类型判断时返回 true
     */
    private static boolean isStableTypeName(String className) {
        return !className.endsWith("package-info")
                && !className.endsWith("module-info")
                && !className.contains("$$");
    }

    /**
     * 业务作用：按包边界判断类型是否属于可选集成，防止相似字符串前缀误排除其它业务包。
     *
     * @param className               class 元数据中的二进制名
     * @param excludedPackagePrefixes 待排除的完整包名前缀
     * @return 类型处于任一排除包或其子包时返回 true
     */
    private static boolean isExcluded(
            String className, Collection<String> excludedPackagePrefixes) {
        for (String prefix : excludedPackagePrefixes) {
            if (className.startsWith(prefix + ".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 业务作用：以不触发静态初始化的方式加载业务类型，使注解和成员检查不会提前执行应用代码。
     *
     * @param classLoader 应用类加载器
     * @param className   待加载二进制类名
     * @return 已加载但未初始化的 Class
     * @throws IllegalStateException 类型依赖不完整或无法链接时抛出
     */
    private static Class<?> loadClass(ClassLoader classLoader, String className) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (Throwable error) {
            throw new IllegalStateException("Cannot load AOT application type " + className, error);
        }
    }
}
