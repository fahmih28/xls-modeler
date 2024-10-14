package com.rabbani.xls.engine;

import org.apache.poi.ss.usermodel.Cell;

public interface Serializer<T> {

    void serialize(T data, Cell cell);

}
