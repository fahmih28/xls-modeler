package com.rabbani.xls.engine;

import org.apache.poi.ss.usermodel.Cell;

public interface ErrorHandler {
    void handle(String columnName,Cell cell, Throwable e);
}
