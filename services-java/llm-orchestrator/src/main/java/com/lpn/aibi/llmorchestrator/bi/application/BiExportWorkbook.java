package com.lpn.aibi.llmorchestrator.bi.application;

import java.time.Instant;

public record BiExportWorkbook(
        byte[] content,
        String filename,
        Instant generatedAt) {
}
