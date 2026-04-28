package com.hgx.converter.fromshape;

import com.hgx.model.FeatureData;
import com.hgx.converter.DataWriter;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;

public class CsvDataWriter implements DataWriter {

    private final File file;
    private final String encoding;

    public CsvDataWriter(File file, String encoding) {
        this.file = file;
        this.encoding = encoding;
    }

    @Override
    public void write(List<FeatureData> features, String[] columnNames) throws IOException {
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), encoding);
             CSVWriter csvWriter = new CSVWriter(writer)) {

            // 写入表头
            csvWriter.writeNext(columnNames);

            // 写入数据行
            for (FeatureData feature : features) {
                csvWriter.writeNext(feature.getAttributeValues());
            }
        }
    }

    @Override
    public void close() throws IOException {
        // 不需要关闭任何资源，因为资源已经在try-with-resources中关闭
    }
}