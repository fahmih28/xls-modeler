package com.rabbani.xls.annotation;

import com.rabbani.xls.engine.Deserializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Deserialize {

    String param() default "";

    Class<? extends Deserializer> value();

}
