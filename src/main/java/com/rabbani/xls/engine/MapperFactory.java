package com.rabbani.xls.engine;

public interface MapperFactory {

    <T> DynamicMapper<T> getDynamic(Class<T> clz);

}
