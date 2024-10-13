package com.rabbani.xls.engine;

import javafx.scene.control.Cell;

public interface Deserializer<T> {

    T convert(Cell cell);
}
