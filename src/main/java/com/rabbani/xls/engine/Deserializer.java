package com.rabbani.xls.engine;

public interface Deserializer<T> {

    T convert(String data);
}
