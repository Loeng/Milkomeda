package com.github.yizzuide.milkomeda.sundial;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;


/**
 * 数据源注解
 * @author jsq 786063250@qq.com
 * @since 3.4.0
 * Create at 2020/5/8
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Sundial {
    /**
     * 选择数据源Key
     * @return String
     */
    @AliasFor("key")
    String value() default DynamicRouteDataSource.MASTER_KEY;

    /**
     * 监选择数据源Key
     * @return  String
     */
    @AliasFor("value")
    String key() default DynamicRouteDataSource.MASTER_KEY;
}
