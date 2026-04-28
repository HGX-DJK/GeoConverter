package com.hgx.converter.toshape;

import com.hgx.converter.StreamingReader;
import com.hgx.converter.WktGeometryParser;
import com.hgx.model.FeatureData;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import java.io.*;

/**
 * CSV 流式读取器，基于 opencsv 的迭代器逐行读取，
 * 避免一次性加载全部数据到内存。
 */
public class StreamingCsvReader implements StreamingReader {

    private final File file;
    private final String encoding;
    private String[] columnNames;
    private boolean initialized = false;

    public StreamingCsvReader(File file, String encoding) {
        this.file = file;
        this.encoding = encoding;
    }

    /**
     * 初始化列名（在读取流之前调用）
     */
    private void ensureInitialized() throws IOException {
        if (!initialized) {
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(file), encoding))) {
                CSVReader reader = new CSVReaderBuilder(bufferedReader).build();
                columnNames = reader.readNext();
                if (columnNames == null) {
                    throw new IOException("CSV file is empty: " + file.getAbsolutePath());
                }
                initialized = true;
            } catch (CsvValidationException e) {
                throw new IOException("CSV parsing error: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public void readStream(FeatureConsumer consumer) throws IOException {
        readStream(consumer, -1);
    }

    /**
     * 流式读取数据，可选限制行数（用于采样检测）
     * @param consumer 数据消费者
     * @param limit 限制读取的行数（不含表头），-1 表示不限制
     */
    public void readStream(FeatureConsumer consumer, int limit) throws IOException {
        ensureInitialized();

        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), encoding))) {
            CSVReader reader = new CSVReaderBuilder(bufferedReader).build();
            reader.readNext(); // skip header

            String[] row;
            int rowCount = 0;
            while ((row = reader.readNext()) != null) {
                if (limit > 0 && rowCount >= limit) {
                    break;
                }
                FeatureData feature = new FeatureData(columnNames, row);
                consumer.accept(feature);
                rowCount++;
            }

        } catch (CsvValidationException e) {
            throw new IOException("CSV parsing error: " + e.getMessage(), e);
        } catch (WktGeometryParser.WktParseException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String[] getColumnNames() throws IOException {
        ensureInitialized();
        return columnNames;
    }

    @Override
    public void close() throws IOException {
        // 无需关闭，reader 在 try-with-resources 中已关闭
    }
}
