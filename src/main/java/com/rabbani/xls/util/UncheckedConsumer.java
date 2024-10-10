package com.rabbani.xls.util;

public interface UncheckedConsumer<T,V> {

    void accept(T t,V v) throws Exception;

}
