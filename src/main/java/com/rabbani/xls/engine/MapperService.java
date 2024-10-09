package com.rabbani.xls.engine;

import java.util.ServiceLoader;

public class MapperService {
    private MapperFactory mapperFactory;

    public MapperService() {
        mapperFactory = ServiceLoader.load(MapperFactory.class).iterator().next();
    }

    public <T> DynamicMapper<T> getDynamicMapper(Class<T> type) {
        return mapperFactory.getDynamic(type);
    }
}
