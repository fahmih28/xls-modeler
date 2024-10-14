package com.rabbani.xls.util;

public interface StringUtils {
    static boolean isEmpty(String text) {
        return text == null || text.trim().isEmpty();
    }
}
