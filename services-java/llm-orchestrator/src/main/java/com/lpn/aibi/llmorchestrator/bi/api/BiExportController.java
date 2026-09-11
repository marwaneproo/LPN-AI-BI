package com.lpn.aibi.llmorchestrator.bi.api;

import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.lpn.aibi.llmorchestrator.bi.application.BiExportService;
import com.lpn.aibi.llmorchestrator.bi.application.BiExportWorkbook;
import com.lpn.aibi.llmorchestrator.bi.application.BiQuery;

@RestController
@RequestMapping("/v1/bi/export")
class BiExportController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final BiExportService service;

    BiExportController(BiExportService service) {
        this.service = service;
    }

    @GetMapping(value = "/{page}", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ResponseEntity<StreamingResponseBody> export(
            @PathVariable String page,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "granularity", required = false) String granularity,
            @RequestParam(name = "compare", defaultValue = "false") boolean compare,
            @RequestParam(name = "commercial", required = false) Integer commercial,
            @RequestParam(name = "category", required = false) Integer category,
            @RequestParam(name = "supplier", required = false) Integer supplier,
            @RequestParam(name = "customer", required = false) Integer customer,
            @RequestParam(name = "document_type", required = false) Integer documentType,
            @RequestParam(name = "region", required = false) String region,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "payment_status", required = false) String paymentStatus,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset) {
        BiQuery query = BiQuery.of(from, to, granularity, compare, commercial, category, supplier, customer, documentType, region, city, paymentStatus, limit, offset);
        BiExportWorkbook workbook = service.export(page, query);
        StreamingResponseBody body = outputStream -> writeWorkbook(outputStream, workbook.content());

        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(workbook.filename())
                        .build()
                        .toString())
                .header("X-BI-Export-Page", page)
                .header("X-BI-Generated-At", workbook.generatedAt().toString())
                .body(body);
    }

    private static void writeWorkbook(java.io.OutputStream outputStream, byte[] content) throws IOException {
        outputStream.write(content);
    }
}
