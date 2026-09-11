package com.lpn.aibi.llmorchestrator.bi.infrastructure.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonProperty;

@Component
public class BiOoxmlWorkbookWriter {

    public byte[] write(List<Sheet> sheets) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                put(zip, "[Content_Types].xml", contentTypes(sheets.size()));
                put(zip, "_rels/.rels", ROOT_RELS);
                put(zip, "xl/workbook.xml", workbookXml(sheets));
                put(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
                put(zip, "xl/styles.xml", STYLES_XML);
                for (int index = 0; index < sheets.size(); index += 1) {
                    put(zip, "xl/worksheets/sheet%d.xml".formatted(index + 1), sheetXml(sheets.get(index).rows()));
                }
            }
            return output.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to generate BI workbook", ex);
        }
    }

    public Sheet sheet(String name, Object value) {
        if (value instanceof Iterable<?> iterable) {
            return new Sheet(name, rowsFromIterable(iterable));
        }
        if (value instanceof Map<?, ?> map) {
            return new Sheet(name, rowsFromMap(map));
        }
        return new Sheet(name, rowsFromObject(value));
    }

    public Sheet contextSheet(Map<String, Object> values) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Champ", "Valeur"));
        values.forEach((key, value) -> rows.add(List.of(key, value)));
        return new Sheet("Context", rows);
    }

    public Sheet rowsSheet(String name, List<List<Object>> rows) {
        return new Sheet(name, rows.isEmpty() ? List.of(List.of("Aucune donnee")) : rows);
    }

    private static List<List<Object>> rowsFromIterable(Iterable<?> iterable) {
        List<Object> values = new ArrayList<>();
        iterable.forEach(values::add);
        if (values.isEmpty()) {
            return List.of(List.of("Aucune donnee"));
        }

        Object first = values.get(0);
        if (first != null && first.getClass().isRecord()) {
            List<String> headers = componentNames(first.getClass());
            List<List<Object>> rows = new ArrayList<>();
            rows.add(new ArrayList<>(headers));
            for (Object value : values) {
                rows.add(recordValues(value));
            }
            return rows;
        }

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Valeur"));
        values.forEach(value -> rows.add(List.of(value)));
        return rows;
    }

    private static List<List<Object>> rowsFromObject(Object value) {
        if (value == null) {
            return List.of(List.of("Champ", "Valeur"), List.of("valeur", ""));
        }
        if (!value.getClass().isRecord()) {
            return List.of(List.of("Valeur"), List.of(value));
        }

        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Champ", "Valeur"));
        flattenRecord("", value, rows);
        return rows;
    }

    private static List<List<Object>> rowsFromMap(Map<?, ?> map) {
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of("Champ", "Valeur"));
        map.forEach((key, value) -> rows.add(List.of(key, value)));
        return rows;
    }

    private static void flattenRecord(String prefix, Object value, List<List<Object>> rows) {
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            Object componentValue = invoke(component, value);
            String name = prefix.isBlank() ? jsonName(component) : prefix + "." + jsonName(component);
            if (componentValue != null && componentValue.getClass().isRecord()) {
                flattenRecord(name, componentValue, rows);
            } else {
                rows.add(List.of(name, componentValue));
            }
        }
    }

    private static List<String> componentNames(Class<?> recordType) {
        List<String> names = new ArrayList<>();
        for (RecordComponent component : recordType.getRecordComponents()) {
            names.add(jsonName(component));
        }
        return names;
    }

    private static List<Object> recordValues(Object value) {
        List<Object> values = new ArrayList<>();
        for (RecordComponent component : value.getClass().getRecordComponents()) {
            Object componentValue = invoke(component, value);
            values.add(formatValue(componentValue));
        }
        return values;
    }

    private static Object invoke(RecordComponent component, Object value) {
        try {
            return component.getAccessor().invoke(value);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to read " + component.getName(), ex);
        }
    }

    private static String jsonName(RecordComponent component) {
        JsonProperty property = component.getAnnotation(JsonProperty.class);
        if (property != null && !property.value().isBlank()) {
            return property.value();
        }
        return camelToSnake(component.getName());
    }

    private static String camelToSnake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String sheetXml(List<List<Object>> rows) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        xml.append("<sheetData>");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex += 1) {
            int rowNumber = rowIndex + 1;
            xml.append("<row r=\"").append(rowNumber).append("\">");
            List<Object> cells = rows.get(rowIndex);
            for (int columnIndex = 0; columnIndex < cells.size(); columnIndex += 1) {
                Object value = cells.get(columnIndex);
                String ref = columnName(columnIndex) + rowNumber;
                int style = rowIndex == 0 ? 1 : 0;
                appendCell(xml, ref, value, style);
            }
            xml.append("</row>");
        }
        xml.append("</sheetData>");
        xml.append("</worksheet>");
        return xml.toString();
    }

    private static void appendCell(StringBuilder xml, String ref, Object value, int style) {
        Object formatted = formatValue(value);
        if (formatted instanceof Number number) {
            xml.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"><v>")
                    .append(number)
                    .append("</v></c>");
            return;
        }
        xml.append("<c r=\"").append(ref).append("\" t=\"inlineStr\" s=\"").append(style).append("\"><is><t xml:space=\"preserve\">")
                .append(escape(String.valueOf(formatted == null ? "" : formatted)))
                .append("</t></is></c>");
    }

    private static Object formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal.stripTrailingZeros();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Instant || value instanceof LocalDate || value instanceof TemporalAccessor) {
            return value.toString();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            iterable.forEach(item -> values.add(String.valueOf(formatValue(item))));
            return String.join(", ", values);
        }
        return value.toString();
    }

    private static String columnName(int zeroBasedIndex) {
        int value = zeroBasedIndex + 1;
        StringBuilder name = new StringBuilder();
        while (value > 0) {
            int remainder = (value - 1) % 26;
            name.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return name.toString();
    }

    private static void put(ZipOutputStream zip, String path, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(content.strip().getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes(int sheetCount) {
        StringBuilder overrides = new StringBuilder();
        for (int index = 1; index <= sheetCount; index += 1) {
            overrides.append("<Override PartName=\"/xl/worksheets/sheet")
                    .append(index)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                %s
                </Types>
                """.formatted(overrides);
    }

    private static String workbookXml(List<Sheet> sheets) {
        StringBuilder sheetXml = new StringBuilder();
        for (int index = 0; index < sheets.size(); index += 1) {
            sheetXml.append("<sheet name=\"")
                    .append(escape(safeSheetName(sheets.get(index).name())))
                    .append("\" sheetId=\"")
                    .append(index + 1)
                    .append("\" r:id=\"rId")
                    .append(index + 1)
                    .append("\"/>");
        }
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets>%s</sheets>
                </workbook>
                """.formatted(sheetXml);
    }

    private static String workbookRels(int sheetCount) {
        StringBuilder relationships = new StringBuilder();
        for (int index = 1; index <= sheetCount; index += 1) {
            relationships.append("<Relationship Id=\"rId")
                    .append(index)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(index)
                    .append(".xml\"/>");
        }
        relationships.append("<Relationship Id=\"rId")
                .append(sheetCount + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                %s
                </Relationships>
                """.formatted(relationships);
    }

    private static String safeSheetName(String name) {
        String safe = name.replaceAll("[\\[\\]:*?/\\\\]", " ").trim();
        return safe.length() > 31 ? safe.substring(0, 31) : safe;
    }

    private static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public record Sheet(String name, List<List<Object>> rows) {
    }

    private static final String ROOT_RELS = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
            """;

    private static final String STYLES_XML = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <fonts count="2">
            <font><sz val="11"/><name val="Calibri"/></font>
            <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
            </fonts>
            <fills count="3">
            <fill><patternFill patternType="none"/></fill>
            <fill><patternFill patternType="gray125"/></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FF0878D1"/><bgColor indexed="64"/></patternFill></fill>
            </fills>
            <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
            <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
            <cellXfs count="2">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
            <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
            </cellXfs>
            </styleSheet>
            """;
}
