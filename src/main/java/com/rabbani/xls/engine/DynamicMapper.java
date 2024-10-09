package com.rabbani.xls.engine;

import com.rabbani.xls.util.UncheckedConsumer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public abstract class DynamicMapper<T> {

    protected final Map<String, ColumnMapper<T>> columnMapperRegister;

    public DynamicMapper() {
        columnMapperRegister = new HashMap<>();
    }

    protected abstract T mappedInstance();

    public Instance<T> mapper(Row row){
        return new ImplReader(row);
    }

    public Instance<T> mapper(List<String> row){
        return new ImplReader(row);
    }

    private class ImplReader implements Instance<T>{
        private List<ColumDesignator<T>> columnDesignators = new ArrayList<>();;

        public ImplReader(List<String> columnNames) {
            int i = 0;
            for(String columnName:columnNames){
                ColumnMapper<T> mapper = columnMapperRegister.get(columnName);
                if(mapper != null) {
                    columnDesignators.add(new ColumDesignator<>(mapper, i));
                }
                i++;
            }
        }

        public ImplReader(Row row) {
            int i = 0;
            for(Cell cell:row){
                String cellValue = cell.getStringCellValue();
                if(cellValue != null) {
                    ColumnMapper<T> mapper = columnMapperRegister.get(cellValue);
                    if (mapper != null) {
                        columnDesignators.add(new ColumDesignator<>(mapper,i));
                    }
                }

                i++;
            }
        }

        @Override
        public T read(Row row,ErrorHandler errorHandler) {
            T value = mappedInstance() ;
            for(ColumDesignator<T> columnDesignator: columnDesignators){
                ColumnMapper<T> mapper = columnDesignator.mapper;
                Cell cell = row.getCell(columnDesignator.column);
                try {
                    mapper.reader.accept(cell, value);
                }
                catch (Exception e){
                    if(errorHandler != null) {
                        errorHandler.handle(mapper.name,cell,e);
                    }
                }
            }
            return value;
        }

        @Override
        public void write(Row row, T value,ErrorHandler errorHandler) {
            for(ColumDesignator<T> columnDesignator: columnDesignators){
                ColumnMapper<T> mapper = columnDesignator.mapper;
                Cell cell = row.createCell(columnDesignator.column);
                try {
                    mapper.writer.accept(cell,value);
                }
                catch (Exception e){
                    if(errorHandler != null) {
                        errorHandler.handle(mapper.name,cell,e);
                    }
                }
            }
        }
    }

    public static final class ColumnMapper<T>{
        final String name;
        final UncheckedConsumer<Cell,T> reader;
        final UncheckedConsumer<Cell,T> writer;

        public ColumnMapper(String name,UncheckedConsumer<Cell, T> reader, UncheckedConsumer<Cell, T> writer) {
            this.name = name;
            this.reader = reader;
            this.writer = writer;
        }
    }

    public static class ColumDesignator<T>{
        final ColumnMapper<T> mapper;
        final int column;

        public ColumDesignator(ColumnMapper<T> mapper, int column) {
            this.mapper = mapper;
            this.column = column;
        }
    }

    public interface Instance<T>{
        void write(Row row,T value,ErrorHandler errorHandler);
        T read(Row row,ErrorHandler errorHandler);
    }
}
