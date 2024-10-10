package com.rabbani.xls.engine;

import org.apache.poi.ss.usermodel.Cell;

public abstract class DerSer<T> {

    protected DerSer(String param){
    }

    public abstract void apply(Cell cell,String name, T value);

    public static final class None extends DerSer<Object> {

        public None(String param) {
            super(param);
        }

        @Override
        public void apply(Cell cell,String name, Object value) {
        }
    }
}
