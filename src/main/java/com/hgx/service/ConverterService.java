package com.hgx.service;

import com.hgx.converter.*;
import com.hgx.converter.toshape.*;
import com.hgx.converter.fromshape.ShapefileReader;
import com.hgx.converter.fromshape.CsvDataWriter;
import com.hgx.converter.fromshape.ExcelDataWriter;
import com.hgx.model.FeatureData;
import com.hgx.model.GeometryType;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConverterService {

    private static final int GEOMETRY_SAMPLE_SIZE = 100;

    public File convertToShapefile(File inputFile, File outputDir, String geometryColumn, GeometryType geometryType, String encoding) throws Exception {
        if (!inputFile.exists()) {
            throw new IOException("Input file not found: " + inputFile);
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Cannot create output directory");
        }

        String fileName = inputFile.getName().toLowerCase();
        boolean isCsv = fileName.endsWith(".csv");
        boolean isExcel = fileName.endsWith(".xls") || fileName.endsWith(".xlsx");

        if (!isCsv && !isExcel) {
            throw new IOException("Unsupported file format. Use .csv, .xls, or .xlsx");
        }

        // 创建流式读取器
        StreamingReader reader;
        if (isCsv) {
            reader = new StreamingCsvReader(inputFile, encoding);
        } else {
            reader = new StreamingExcelReader(inputFile);
        }

        // 预读取列名
        String[] columnNames = reader.getColumnNames();
        if (columnNames == null || columnNames.length == 0) {
            throw new IOException("No columns found in file");
        }

        // 检测几何列
        int geometryColumnIndex = detectGeometryColumn(reader, geometryColumn, columnNames);

        // 重新打开读取器进行完整流式处理
        if (isCsv) {
            reader = new StreamingCsvReader(inputFile, encoding);
        } else {
            reader = new StreamingExcelReader(inputFile);
        }

        // 采样检测几何类型
        GeometryType detectedGeometryType = detectGeometryType(reader, geometryColumnIndex, geometryType);

        // 再次重新打开读取器进行完整流式写入
        if (isCsv) {
            reader = new StreamingCsvReader(inputFile, encoding);
        } else {
            reader = new StreamingExcelReader(inputFile);
        }

        // 属性列（排除几何列）
        List<String> attributeColumns = new ArrayList<>();
        for (int i = 0; i < columnNames.length; i++) {
            if (i != geometryColumnIndex) {
                attributeColumns.add(columnNames[i]);
            }
        }

        File outputShp = new File(outputDir, getBaseName(inputFile) + ".shp");
        ShapefileWriter shpWriter = new ShapefileWriter();
        WktGeometryParser geometryParser = new WktGeometryParser();
        shpWriter.writeStream(reader, outputShp, detectedGeometryType, attributeColumns, encoding, geometryParser, geometryColumnIndex);

        return outputShp;
    }

    private int detectGeometryColumn(StreamingReader reader, String specifiedColumn, String[] columnNames) throws IOException {
        if (specifiedColumn != null && !specifiedColumn.trim().isEmpty()) {
            for (int i = 0; i < columnNames.length; i++) {
                if (columnNames[i].equalsIgnoreCase(specifiedColumn)) {
                    return i;
                }
            }
            throw new IOException("Specified geometry column '" + specifiedColumn + "' not found");
        }

        // 按列名自动检测
        int index = GeometryColumnDetector.detectByName(columnNames);
        if (index >= 0) {
            return index;
        }

        // 采样检测
        final int[] detectedIndex = {-1};
        final WktGeometryParser parser = new WktGeometryParser();

        try {
            reader.readStream(feature -> {
                if (detectedIndex[0] < 0) {
                    String[] values = feature.getAttributeValues();
                    for (int i = 0; i < values.length; i++) {
                        if (values[i] != null && parser.canParse(values[i])) {
                            detectedIndex[0] = i;
                            break;
                        }
                    }
                }
            });
        } catch (IOException e) {
            // rethrow
        }

        if (detectedIndex[0] < 0) {
            throw new IOException("Could not detect geometry column. Please specify with geometryColumn parameter.");
        }

        return detectedIndex[0];
    }

    private GeometryType detectGeometryType(StreamingReader reader, int geometryColumnIndex, GeometryType specifiedType) throws IOException {
        if (specifiedType != null) {
            return specifiedType;
        }

        final GeometryType[] detectedType = {null};
        final WktGeometryParser parser = new WktGeometryParser();
        final int[] sampleCount = {0};

        reader.readStream(feature -> {
            if (detectedType[0] == null && sampleCount[0] < GEOMETRY_SAMPLE_SIZE) {
                String wkt = feature.getAttributeValues()[geometryColumnIndex];
                Geometry geom = parser.parse(wkt);
                if (geom != null) {
                    if (geom instanceof MultiPolygon) {
                        detectedType[0] = GeometryType.MULTIPOLYGON;
                    } else if (geom instanceof MultiLineString) {
                        detectedType[0] = GeometryType.MULTILINESTRING;
                    } else if (geom instanceof MultiPoint) {
                        detectedType[0] = GeometryType.MULTIPOINT;
                    } else if (geom instanceof Point) {
                        detectedType[0] = GeometryType.POINT;
                    } else if (geom instanceof LineString) {
                        detectedType[0] = GeometryType.LINESTRING;
                    } else if (geom instanceof Polygon) {
                        detectedType[0] = GeometryType.POLYGON;
                    }
                }
                sampleCount[0]++;
            }
        });

        if (detectedType[0] == null) {
            return GeometryType.POINT;
        }
        return detectedType[0];
    }

    public File convertFromShapefile(File inputFile, File outputFile, String encoding) throws Exception {
        if (!inputFile.exists()) {
            throw new IOException("Input file not found: " + inputFile);
        }

        ShapefileReader reader = new ShapefileReader(inputFile);
        List<FeatureData> features;
        try {
            features = reader.readAll();
        } finally {
            reader.close();
        }

        String[] columnNames = reader.getColumnNames();

        DataWriter writer;
        String outputName = outputFile.getName().toLowerCase();
        if (outputName.endsWith(".csv")) {
            writer = new CsvDataWriter(outputFile, encoding);
        } else if (outputName.endsWith(".xls") || outputName.endsWith(".xlsx")) {
            writer = new ExcelDataWriter(outputFile);
        } else {
            throw new IOException("Unsupported output format. Use .csv, .xls, or .xlsx");
        }

        try {
            writer.write(features, columnNames);
        } finally {
            writer.close();
        }

        return outputFile;
    }

    private String getBaseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
