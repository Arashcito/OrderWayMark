package com.orderwaymark.olist;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Minimal RFC-4180-ish reader: header row, comma separated, optional double quotes ("" escapes). */
final class Csv {
    private Csv() {
    }

    /** Streams rows as header-name -> value maps. Multi-line quoted fields are not supported (Olist doesn't need them). */
    static void read(Path file, Consumer<Map<String, String>> rowHandler) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String headerLine = r.readLine();
            if (headerLine == null) return;
            List<String> header = parseLine(stripBom(headerLine));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cells = parseLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < header.size(); i++) row.put(header.get(i), i < cells.size() ? cells.get(i) : "");
                rowHandler.accept(row);
            }
        }
    }

    static List<String> parseLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }
}
