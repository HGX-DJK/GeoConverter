package com.hgx.converter.fromshape;

import com.hgx.model.FeatureData;
import com.hgx.converter.DataWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ExcelDataWriter implements DataWriter {

    private final File file;
    private Workbook workbook;

    public ExcelDataWriter(File file) {
        this.file = file;
    }

    @Override
    public void write(List<FeatureData> features, String[] columnNames) throws IOException {
        String filename = file.getName().toLowerCase();

        if (filename.endsWith(".xlsx")) {
            workbook = new XSSFWorkbook();
        } else if (filename.endsWith(".xls")) {
            workbook = new HSSFWorkbook();
        } else {
            throw new IOException("Unsupported Excel format: " + filename);
        }

        Sheet sheet = workbook.createSheet("Data");

        // 创建表头行
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columnNames.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columnNames[i]);
        }

        // 写入数据行
        for (int rowNum = 0; rowNum < features.size(); rowNum++) {
            FeatureData feature = features.get(rowNum);
            Row row = sheet.createRow(rowNum + 1);
            String[] values = feature.getAttributeValues();

            for (int colNum = 0; colNum < values.length; colNum++) {
                Cell cell = row.createCell(colNum);
                cell.setCellValue(values[colNum]);
            }
        }

        // 自动调整列宽
        for (int i = 0; i < columnNames.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // 写入文件
        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }
    }

    @Override
    public void close() throws IOException {
        if (workbook != null) {
            workbook.close();
        }
    }
}