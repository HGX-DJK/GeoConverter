package com.hgx.converter.toshape;

import com.hgx.model.FeatureData;
import com.hgx.converter.DataReader;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class CsvDataReader implements DataReader {

    private final File file;
    private final String encoding;
    private String[] columnNames;

    public CsvDataReader(File file, String encoding) {
        this.file = file;
        this.encoding = encoding;
    }

    @Override
    public List<FeatureData> readAll() throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), encoding))) {

            CSVReader reader = new CSVReaderBuilder(bufferedReader).build();

            columnNames = reader.readNext();
            if (columnNames == null) {
                throw new IOException("CSV file is empty: " + file.getAbsolutePath());
            }

            List<FeatureData> features = new ArrayList<>();
            String[] row;
            while ((row = reader.readNext()) != null) {
                features.add(new FeatureData(columnNames, row));
            }
            return features;

        } catch (CsvValidationException e) {
            throw new IOException("CSV parsing error: " + e.getMessage(), e);
        }
    }

    @Override
    public String[] getColumnNames() {
        return columnNames;
    }

    @Override
    public void close() throws IOException {
    }
}
