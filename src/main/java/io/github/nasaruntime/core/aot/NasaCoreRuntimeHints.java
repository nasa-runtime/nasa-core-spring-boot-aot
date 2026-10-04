package io.github.nasaruntime.core.aot;

import io.github.nasaruntime.core.annotation.ThreadPool;
import io.github.nasaruntime.core.base.Initialization;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;

/**
 * 业务作用：为 nasa-core 自身类型和应用业务反射合同生成 Spring AOT 运行时提示。
 */
public final class NasaCoreRuntimeHints implements RuntimeHintsRegistrar, BeanFactoryInitializationAotProcessor {

    private static final String CORE_PACKAGE = "io.github.nasaruntime.core";

    /**
     * 业务作用：登记 nasa-core 内建类型的反射与 Java Serialization 元数据，确保核心容器和协议载体可进入原生镜像。
     *
     * @param hints       当前 AOT 提示集合
     * @param classLoader 应用类加载器
     *                    返回：无返回值；提示写入当前构建上下文。
     */
    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        List<Class<?>> coreTypes = NasaAotClassScanner.scan(
                classLoader,
                Set.of(CORE_PACKAGE),
                Set.of("io.github.nasaruntime.core.aot", "io.github.nasaruntime.core.feature"));
        for (Class<?> type : coreTypes) {
            registerType(hints, type);
        }
    }

    /**
     * 业务作用：在 Spring BeanDefinition 已确定后捕获安全的应用基础包，并延迟到代码生成阶段登记业务类型。
     *
     * @param beanFactory 应用 BeanFactory
     * @return 负责写入业务反射提示的 AOT contribution
     */
    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(
            ConfigurableListableBeanFactory beanFactory) {
        Set<String> packages = NasaAotApplicationPackages.resolve(beanFactory);
        ClassLoader classLoader = beanFactory.getBeanClassLoader();
        return (generationContext, beanFactoryInitializationCode) -> registerApplicationTypes(
                generationContext.getRuntimeHints(), classLoader, packages);
    }

    /**
     * 业务作用：扫描明确的应用包并登记具体业务类型，同时精确补充核心库按接口或成员注解动态调用的入口。
     *
     * @param hints       当前 AOT 提示集合
     * @param classLoader 应用类加载器
     * @param packages    已解析的应用基础包
     *                    返回：无返回值；扫描到的稳定业务类型会写入 hints。
     */
    static void registerApplicationTypes(
            RuntimeHints hints, ClassLoader classLoader, Set<String> packages) {
        for (Class<?> type : NasaAotClassScanner.scan(classLoader, packages)) {
            if (type.isAnnotation() || type.isSynthetic()) {
                continue;
            }
            registerType(hints, type);
            registerInitializationMethods(hints.reflection(), type);
            registerThreadPoolMembers(hints.reflection(), type);
        }
    }

    /**
     * 业务作用：为单个类型登记完整成员元数据，并在其声明 Java 序列化合同时登记序列化提示。
     *
     * @param hints 当前 AOT 提示集合
     * @param type  待登记类型
     *              返回：无返回值；接口和抽象类只登记反射元数据。
     */
    @SuppressWarnings("unchecked")
    private static void registerType(RuntimeHints hints, Class<?> type) {
        hints.reflection().registerType(type, MemberCategory.values());
        if (Serializable.class.isAssignableFrom(type)
                && !type.isInterface()
                && !Modifier.isAbstract(type.getModifiers())) {
            hints.serialization().registerType((Class<? extends Serializable>) type);
        }
    }

    /**
     * 业务作用：为 Initialization 实现保留三个生命周期方法，支持运行期按统一初始化顺序动态调用。
     *
     * @param hints 反射提示集合
     * @param type  待检查业务类型
     *              返回：无返回值；非 Initialization 类型不登记。
     */
    private static void registerInitializationMethods(ReflectionHints hints, Class<?> type) {
        if (!Initialization.class.isAssignableFrom(type)) {
            return;
        }
        for (String name : new String[]{"before", "initialize", "after"}) {
            Method method = ClassUtils.getMethod(type, name);
            hints.registerMethod(method, ExecutableMode.INVOKE);
        }
    }

    /**
     * 业务作用：登记业务类型继承层次中由 ThreadPoolInitializer 动态读写或调用的成员。
     *
     * @param hints 反射提示集合
     * @param type  待检查业务类型
     *              返回：无返回值；未标注成员不登记额外入口。
     */
    private static void registerThreadPoolMembers(ReflectionHints hints, Class<?> type) {
        ReflectionUtils.doWithFields(type, field -> registerThreadPoolField(hints, field));
        ReflectionUtils.doWithMethods(type, method -> registerThreadPoolMethod(hints, method));
    }

    /**
     * 业务作用：保留带 ThreadPool 注解字段的读写元数据，使运行期能够复验已有执行器并按需注入。
     *
     * @param hints 反射提示集合
     * @param field 待检查字段
     *              返回：无返回值；未标注字段不登记。
     */
    private static void registerThreadPoolField(ReflectionHints hints, Field field) {
        if (AnnotatedElementUtils.findMergedAnnotation(field, ThreadPool.class) != null) {
            hints.registerField(field);
        }
    }

    /**
     * 业务作用：保留带 ThreadPool 注解单参数方法的调用入口，与运行期 setter 注入边界一致。
     *
     * @param hints  反射提示集合
     * @param method 待检查方法
     *               返回：无返回值；未标注或参数数量不符的方法不登记。
     */
    private static void registerThreadPoolMethod(ReflectionHints hints, Method method) {
        if (method.getParameterCount() == 1
                && AnnotatedElementUtils.findMergedAnnotation(method, ThreadPool.class) != null) {
            hints.registerMethod(method, ExecutableMode.INVOKE);
        }
    }
}
