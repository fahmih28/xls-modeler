package com.rabbani.xls.engine;

import java.util.ServiceLoader;

public class MapperService {

    private MapperFactory mapperFactory;

    private static MapperService INSTANCE;

    public static MapperService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MapperService();
        }
        return INSTANCE;
    }

    private MapperService() {
        mapperFactory = ServiceLoader.load(MapperFactory.class).iterator().next();
    }

    public <T> Mapper<T> getMapper(Class<T> type) {
        return mapperFactory.get(type);
    }
}
