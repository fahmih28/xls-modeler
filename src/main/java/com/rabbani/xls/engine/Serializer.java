package com.rabbani.xls.engine;

public interface Serializer<T> {

    String convert(T data);
    
}
