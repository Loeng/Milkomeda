package com.github.yizzuide.milkomeda.light;

import com.github.yizzuide.milkomeda.universe.context.ApplicationContextHolder;
import com.github.yizzuide.milkomeda.universe.context.WebContext;
import com.github.yizzuide.milkomeda.universe.el.ELContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.function.Function;

import static com.github.yizzuide.milkomeda.util.ReflectUtil.extractValue;

/**
 * LightCacheAspect
 * <br>
 * Aspect注解会被AbstractAutoProxyCreator创建Proxy对象，而@Async会被AsyncAnnotationBeanPostProcessor创建。
 * 它们都继承了ProxyProcessorSupport，AsyncAnnotationBeanPostProcessor在有Proxy对象时只添加Advice。
 *
 * @see org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator
 * @see org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor
 * @see org.springframework.aop.framework.AbstractAdvisingBeanPostProcessor
 *
 * @author yizzuide
 * @since 2.0.0
 * @version 2.7.0
 * Create at 2019/12/18 14:45
 */
@Order(98)
@Aspect
public class LightCacheAspect {
    public static final String DEFAULT_BEAN_NAME = "lightCache";

    @Autowired
    private LightProperties props;

    @Around("execution(@LightCacheable * *.*(..)) && @annotation(cacheable)")
    public Object cacheableAround(ProceedingJoinPoint joinPoint, LightCacheable cacheable) throws Throwable {
        return applyAround(joinPoint, cacheable, cacheable.condition(), cacheable.value(), cacheable.keyPrefix(), cacheable.key());
    }

    @Around("execution(@LightCacheEvict * *.*(..)) && @annotation(cacheEvict)")
    public Object cacheEvictAround(ProceedingJoinPoint joinPoint, LightCacheEvict cacheEvict) throws Throwable {
        return applyAround(joinPoint, cacheEvict, cacheEvict.condition(), cacheEvict.value(), cacheEvict.keyPrefix(), cacheEvict.key());
    }

    @Around("execution(@LightCachePut * *.*(..)) && @annotation(cachePut)")
    public Object cachePutAround(ProceedingJoinPoint joinPoint, LightCachePut cachePut) throws Throwable {
        return applyAround(joinPoint, cachePut, cachePut.condition(), cachePut.value(), cachePut.keyPrefix(), cachePut.key());
    }

    @SuppressWarnings("unchecked")
    private Object applyAround(ProceedingJoinPoint joinPoint, Annotation annotation, String condition, String cacheBeanName, String prefix, String key) throws Throwable {
        // 检查缓存条件
        if (!StringUtils.isEmpty(condition) && !Boolean.parseBoolean(ELContext.getValue(joinPoint, condition))) {
            return joinPoint.proceed();
        }
        if (StringUtils.isEmpty(key)) {
            throw new IllegalArgumentException(String.format("You must set key before use %s.", annotation.annotationType().getSimpleName()));
        }

        // 解析缓存实例名
        cacheBeanName = extractValue(joinPoint, cacheBeanName);

        // 解析表达式
        String viewId = extractValue(joinPoint, key);
        LightCache cache;
        // 记录是否自定义缓存标识
        boolean customCacheFlag = false;
        if (ApplicationContextHolder.get().containsBean(cacheBeanName)) {
            cache = ApplicationContextHolder.get().getBean(cacheBeanName, LightCache.class);
            customCacheFlag = true;
        } else {
            // 修改Bean name，防止与开发者项目里重复
            cacheBeanName = innerCacheBeanName(cacheBeanName);
            cache = WebContext.registerBean((ConfigurableApplicationContext) ApplicationContextHolder.get(), cacheBeanName, LightCache.class);
        }
        // 针对LightCacheable类型的处理
        if (annotation.annotationType() == LightCacheable.class && cache.getL1MaxCount() == null) {
            LightCacheable cacheable = (LightCacheable) annotation;
            // 如果允许拷贝默认配置，并且没有找到自定义的缓存配置
            if (cacheable.copyDefaultConfig() && !customCacheFlag) {
                LightCache defaultBean = ApplicationContextHolder.get().getBean(DEFAULT_BEAN_NAME, LightCache.class);
                // 拷贝默认的配置
                cache.copyFrom(defaultBean);
                cache.setStrategy(cacheable.discardStrategy());
                cache.setOnlyCacheL1(cacheable.onlyCacheL1());
                cache.setOnlyCacheL2(cacheable.onlyCacheL2());
                // 如果当前有设定过期时间（默认走配置文件）
                if (cacheable.expire() != -1) {
                    // 如果 discardStrategy 为 LazyExpire 策略，设置l1Expire并自动同步配置到l2Expire过期
                    if (cacheable.discardStrategy() == LightDiscardStrategy.LazyExpire) {
                        cache.setL1Expire(cacheable.expire());
                    }
                    // 排行类型策略，设置到二级缓存（该策略一级缓存使用排行丢弃方案）
                    cache.setL2Expire(cacheable.expire());
                }
            }
        }

        // 缓存实例配置（优先级最高）
        if (props.getInstances().containsKey(originCacheBeanName(cacheBeanName))) {
            cache.configFrom(props.getInstances().get(originCacheBeanName(cacheBeanName)));
        }

        // key生成器
        Function<Serializable, String> keyGenerator = id -> prefix + id;

        // 删除类型
        if (annotation.annotationType() == LightCacheEvict.class) {
            // 缓存读写策略 - Cache Aside (先删除数据源，再删除缓存）
            joinPoint.proceed();
            CacheHelper.erase(cache, viewId, keyGenerator);
            return null;
        }

        // 更新类型也是先更新数据源，再更新缓存
        if (annotation.annotationType() == LightCachePut.class) {
            return CacheHelper.put(cache, viewId, keyGenerator, id -> joinPoint.proceed());
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return CacheHelper.get(cache, signature.getReturnType(), viewId, keyGenerator, id -> joinPoint.proceed());
    }

    /**
     * 转为内部缓存Bean名
     * @param cacheBeanName 原bean名
     * @return  内部缓存Bean名
     */
    private String innerCacheBeanName(String cacheBeanName) {
        return DEFAULT_BEAN_NAME + "_" + cacheBeanName;
    }

    private String originCacheBeanName(String innerCacheBeanName) {
        String prefix = DEFAULT_BEAN_NAME + "_";
        if (innerCacheBeanName.startsWith(prefix)) {
            return innerCacheBeanName.substring(prefix.length());
        }
        return innerCacheBeanName;
    }
}
