package com.hgx.converter.toshape;

import com.hgx.model.FeatureData;
import com.hgx.converter.DataReader;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ExcelDataReader implements DataReader {

    private final File file;
    private String[] columnNames;
    private Workbook workbook;

    public ExcelDataReader(File file) {
        this.file = file;
    }

    @Override
    public List<FeatureData> readAll() throws IOException {
        try (InputStream fis = new FileInputStream(file)) {
            String filename = file.getName().toLowerCase();

            if (filename.endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(fis);
            } else if (filename.endsWith(".xls")) {
                workbook = new HSSFWorkbook(fis);
            } else {
                throw new IOException("Unsupported Excel format: " + filename);
            }

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

            List<FeatureData> features = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            for (int rowNum = 1; rowNum <= lastRowNum; rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (row == null) continue;

                String[] rowData = new String[numColumns];
                for (int colNum = 0; colNum < numColumns; colNum++) {
                    rowData[colNum] = getCellValueAsString(row.getCell(colNum));
                }
                features.add(new FeatureData(columnNames, rowData));
            }
            return features;

        } finally {
            if (workbook != null) {
                try { workbook.close(); } catch (IOException e) { /* ignore */ }
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
    public String[] getColumnNames() {
        return columnNames;
    }

    @Override
    public void close() throws IOException {
        if (workbook != null) {
            workbook.close();
        }
    }
}
