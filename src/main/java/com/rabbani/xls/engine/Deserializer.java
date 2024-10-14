package com.rabbani.xls.engine;

import org.apache.poi.ss.usermodel.Cell;

public interface Deserializer<T> {

    T deserialize(Cell cell);
}
