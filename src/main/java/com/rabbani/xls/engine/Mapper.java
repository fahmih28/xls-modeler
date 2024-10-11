package com.rabbani.xls.engine;

import com.rabbani.xls.util.UncheckedConsumer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public abstract class Mapper<T> {

    protected final Map<String, ColumnMapper<T>> columnMapperRegister;

    protected boolean caseSensitive;

    protected Supplier<T> instanceFactory;

    protected Mapper() {
        columnMapperRegister = new HashMap<>();
    }

    public Instance<T> mapper(Row row) {
        return new Impl(row);
    }

    public Instance<T> mapper(List<String> row) {
        return new Impl(row);
    }

    public abstract Instance<T> mapper();

    private class Impl implements Instance<T> {
        private List<ColumDesignator<T>> columnDesignators = new ArrayList<>();
        ;
        private int column;

        public Impl(List<String> columnNames) {
            column = 0;
            for (String columnName : columnNames) {
                if (!caseSensitive) {
                    columnName = columnName.toLowerCase();
                }

                ColumnMapper<T> mapper = columnMapperRegister.get(columnName);
                if (mapper != null) {
                    columnDesignators.add(new ColumDesignator<>(mapper, column));
                    column++;
                }

            }
        }

        public Impl(Row row) {
            int i = 0;
            for (Cell cell : row) {
                String cellValue = cell.getStringCellValue();
                if (cellValue != null) {
                    if (!caseSensitive) {
                        cellValue = cellValue.toLowerCase();
                    }

                    ColumnMapper<T> mapper = columnMapperRegister.get(cellValue);
                    if (mapper != null) {
                        columnDesignators.add(new ColumDesignator<>(mapper, i));
                    }
                }

                i++;
            }
        }

        @Override
        public T read(Row row, ErrorHandler errorHandler) {
            T value = instanceFactory.get();
            for (int i = 0; i < column; i++) {
                ColumDesignator<T> columnDesignator = columnDesignators.get(i);
                ColumnMapper<T> mapper = columnDesignator.mapper;
                Cell cell = row.getCell(columnDesignator.column);
                try {
                    mapper.reader.accept(cell, value);
                } catch (Exception e) {
                    if (errorHandler != null) {
                        errorHandler.handle(mapper.name, cell, e);
                    }
                }
            }
            return value;
        }

        @Override
        public void write(Row row, T value, ErrorHandler errorHandler) {
            for (int i = 0; i < column; i++) {
                ColumDesignator<T> columnDesignator = columnDesignators.get(i);
                ColumnMapper<T> mapper = columnDesignator.mapper;
                Cell cell = row.createCell(columnDesignator.column);
                try {
                    mapper.writer.accept(cell, value);
                } catch (Exception e) {
                    if (errorHandler != null) {
                        errorHandler.handle(mapper.name, cell, e);
                    }
                }
            }
        }
    }

    public static final class ColumnMapper<T> {
        final String name;
        final UncheckedConsumer<Cell, T> reader;
        final UncheckedConsumer<Cell, T> writer;

        public ColumnMapper(String name, UncheckedConsumer<Cell, T> reader, UncheckedConsumer<Cell, T> writer) {
            this.name = name;
            this.reader = reader;
            this.writer = writer;
        }
    }

    public static class ColumDesignator<T> {
        final ColumnMapper<T> mapper;
        final int column;

        public ColumDesignator(ColumnMapper<T> mapper, int column) {
            this.mapper = mapper;
            this.column = column;
        }
    }

    public interface Instance<T> {
        void write(Row row, T value, ErrorHandler errorHandler);

        T read(Row row, ErrorHandler errorHandler);
    }
}
