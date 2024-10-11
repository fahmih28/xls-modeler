package com.rabbani.xls.util;

import java.util.HashMap;
import java.util.Map;

public class IdentifierUtils {

    private final Map<String, Integer> uniqueIndex = new HashMap<>();

    public String createName(String name) {
        int lastDotIndex = name.lastIndexOf(".");
        if (lastDotIndex != -1) {
            name = name.substring(lastDotIndex + 1);
        }
        Integer unique = uniqueIndex.getOrDefault(name, 0);
        StringBuilder result = new StringBuilder();
        char prev = ' ';
        for (char chr : name.toCharArray()) {
            if (Character.isUpperCase(chr) || Character.isDigit(chr)) {
                if (prev != '_') {
                    result.append("_");
                }
                result.append(Character.toLowerCase(chr));
            } else if (Character.isLowerCase(chr)) {
                result.append(chr);
                prev = chr;
            } else {
                if (prev != '_') {
                    result.append("_");
                }
                prev = '_';
            }

        }
        if (prev != '_') {
            result.append("_");
        }
        result.append(unique);
        uniqueIndex.put(name, unique + 1);
        return result.toString();
    }


}
