package com.rabbani.xls.processor;

import javax.lang.model.type.TypeMirror;
import java.util.Map;

class MapperScheme {
    String[] values;
    TypeMirror type;
    boolean caseSensitive;
    Map<String, ColumnScheme> properties;

    static class ColumnScheme {
        String label;
        String name;
        TypeMirror type;
        String canonicalPath;
        String serializerIdentifier;
        String deserializerIdentifier;

    }

}
