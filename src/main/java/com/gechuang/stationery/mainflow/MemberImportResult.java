package com.gechuang.stationery.mainflow;

import java.util.List;

public record MemberImportResult(
        int totalRows,
        int importedCount,
        int failedCount,
        List<RowError> errors,
        boolean errorsTruncated
) {

    public record RowError(int rowNumber, String mobile, String message) {
    }
}
