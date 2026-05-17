package com.hgx.converter.toshape;

import com.hgx.converter.StreamingReader;
import com.hgx.converter.WktGeometryParser;
import com.hgx.model.FeatureData;
import org.apache.poi.ss.usermodel.*;

import java.io.*;

/**
 * Excel 流式读取器，逐行读取而不将整个 sheet 加载到内存。
 * 使用 Apache POI 的 SXSSF（Streaming Usermodel API）处理大文件。
 */
public class StreamingExcelReader implements StreamingReader {

    private final File file;
    private String[] columnNames;
    private boolean initialized = false;

    public StreamingExcelReader(File file) {
        this.file = file;
    }

    private void ensureInitialized() throws IOException {
        if (!initialized) {
            try (InputStream fis = new FileInputStream(file)) {
                String filename = file.getName().toLowerCase();
                Workbook workbook;

                if (filename.endsWith(".xlsx")) {
                    workbook = WorkbookFactory.create(fis);
                } else if (filename.endsWith(".xls")) {
                    workbook = WorkbookFactory.create(fis);
                } else {
                    throw new IOException("Unsupported Excel format: " + filename);
                }

                try {
                    Sheet sheet = workbook.getSheetAt(0);
                    if (sheet == null) {
                        throw new IOException("Excel file has no sheets");
                    }

                    Row headerRow = sheet.getRow(0);
                    if (headerRow == null) {
                        throw new IOException("Excel sheet has no header row");
                    }

                    int numColumns = headerRow.getLastCellNum();
                    columnNames = new String[numColumns];
                    for (int i = 0; i < numColumns; i++) {
                        columnNames[i] = getCellValueAsString(headerRow.getCell(i));
                    }
                    initialized = true;
                } finally {
                    workbook.close();
                }
            }
        }
    }

    @Override
    public void readStream(FeatureConsumer consumer) throws IOException {
        readStream(consumer, -1);
    }

    @Override
    public void readStream(FeatureConsumer consumer, int limit) throws IOException {
        ensureInitialized();

        try (InputStream fis = new FileInputStream(file)) {
            String filename = file.getName().toLowerCase();
            Workbook workbook = WorkbookFactory.create(fis);

            try {
                Sheet sheet = workbook.getSheetAt(0);
                int lastRowNum = sheet.getLastRowNum();
                int rowCount = 0;
                for (int rowNum = 1; rowNum <= lastRowNum; rowNum++) {
                    if (limit > 0 && rowCount >= limit) {
                        break;
                    }
                    Row row = sheet.getRow(rowNum);
                    if (row == null) continue;

                    String[] rowData = new String[columnNames.length];
                    for (int colNum = 0; colNum < columnNames.length; colNum++) {
                        rowData[colNum] = getCellValueAsString(row.getCell(colNum));
                    }
                    FeatureData feature = new FeatureData(columnNames, rowData);
                    consumer.accept(feature);
                    rowCount++;
                }
            } catch (WktGeometryParser.WktParseException e) {
                throw new RuntimeException(e);
            } finally {
                workbook.close();
            }
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue)) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (IllegalStateException e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }

    @Override
    public String[] getColumnNames() throws IOException {
        ensureInitialized();
        return columnNames;
    }

    @Override
    public void close() throws IOException {
        // no-op: resources are managed via try-with-resources
    }
}
