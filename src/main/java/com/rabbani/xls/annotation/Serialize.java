package com.rabbani.xls.annotation;

import com.rabbani.xls.engine.Serializer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Serialize {

    String param() default "";

    Class<? extends Serializer> value();

}
