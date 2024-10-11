package com.rabbani.xls.engine;

public interface MapperFactory {

    <T> Mapper<T> get(Class<T> clz);

}
