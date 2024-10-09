package com.rabbani.xls.annotation;

import com.rabbani.xls.engine.DerSer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Col {
    String value();

    Serializer serializer() default @Serializer(DerSer.None.class);

    Deserializer deserializer() default @Deserializer(DerSer.None.class);
}
