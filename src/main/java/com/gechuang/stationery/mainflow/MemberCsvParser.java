package com.gechuang.stationery.mainflow;

import java.util.ArrayList;
import java.util.List;

final class MemberCsvParser {

    private MemberCsvParser() {
    }

    static List<List<String>> parse(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        boolean afterQuote = false;
        boolean recordStarted = false;

        for (int i = 0; i < csv.length(); i++) {
            char current = csv.charAt(i);
            if (quoted) {
                if (current == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                        afterQuote = true;
                    }
                } else {
                    cell.append(current);
                }
                continue;
            }

            if (afterQuote && current != ',' && current != '\r' && current != '\n') {
                if (current != ' ' && current != '\t') {
                    throw new IllegalArgumentException("CSV 引号格式不正确");
                }
                continue;
            }
            if (current == '"') {
                if (cell.length() != 0 || afterQuote) {
                    throw new IllegalArgumentException("CSV 引号格式不正确");
                }
                quoted = true;
                recordStarted = true;
            } else if (current == ',') {
                record.add(cell.toString());
                cell.setLength(0);
                afterQuote = false;
                recordStarted = true;
            } else if (current == '\r' || current == '\n') {
                if (recordStarted || !record.isEmpty() || cell.length() > 0) {
                    record.add(cell.toString());
                    records.add(List.copyOf(record));
                }
                record = new ArrayList<>();
                cell.setLength(0);
                afterQuote = false;
                recordStarted = false;
                if (current == '\r' && i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                cell.append(current);
                recordStarted = true;
            }
        }

        if (quoted) {
            throw new IllegalArgumentException("CSV 引号未闭合");
        }
        if (recordStarted || !record.isEmpty() || cell.length() > 0) {
            record.add(cell.toString());
            records.add(List.copyOf(record));
        }
        return records;
    }
}
