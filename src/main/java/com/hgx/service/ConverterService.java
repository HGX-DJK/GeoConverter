package com.hgx.service;

import com.hgx.converter.DataReader;
import com.hgx.converter.DataWriter;
import com.hgx.converter.GeometryColumnDetector;
import com.hgx.converter.WktGeometryParser;
import com.hgx.converter.toshape.CsvDataReader;
import com.hgx.converter.toshape.ExcelDataReader;
import com.hgx.converter.toshape.ShapefileWriter;
import com.hgx.converter.fromshape.ShapefileReader;
import com.hgx.converter.fromshape.CsvDataWriter;
import com.hgx.converter.fromshape.ExcelDataWriter;
import com.hgx.model.FeatureData;
import com.hgx.model.GeometryType;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class ConverterService {

    public File convertToShapefile(File inputFile, File outputDir, String geometryColumn, GeometryType geometryType, String encoding) throws Exception {
        if (!inputFile.exists()) {
            throw new IOException("Input file not found: " + inputFile);
        }
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Cannot create output directory");
        }

        DataReader reader;
        String fileName = inputFile.getName().toLowerCase();
        if (fileName.endsWith(".csv")) {
            reader = new CsvDataReader(inputFile, encoding);
        } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            reader = new ExcelDataReader(inputFile);
        } else {
            throw new IOException("Unsupported file format. Use .csv, .xls, or .xlsx");
        }

        final List<FeatureData> features;
        try {
            features = reader.readAll();
        } finally {
            reader.close();
        }

        final String[] columnNames = reader.getColumnNames();
        int geometryColumnIndex;

        if (geometryColumn != null && !geometryColumn.trim().isEmpty()) {
            geometryColumnIndex = -1;
            for (int i = 0; i < columnNames.length; i++) {
                if (columnNames[i].equalsIgnoreCase(geometryColumn)) {
                    geometryColumnIndex = i;
                    break;
                }
            }
            if (geometryColumnIndex < 0) {
                throw new IOException("Specified geometry column '" + geometryColumn + "' not found");
            }
        } else {
            geometryColumnIndex = GeometryColumnDetector.detectByName(columnNames);
            if (geometryColumnIndex < 0) {
                String[][] sampleRows = features.stream()
                    .map(FeatureData::getAttributeValues)
                    .toArray(String[][]::new);
                geometryColumnIndex = GeometryColumnDetector.detectBySampling(
                    columnNames, sampleRows, new WktGeometryParser());
            }
            if (geometryColumnIndex < 0) {
                throw new IOException("Could not detect geometry column. Please specify with geometryColumn parameter.");
            }
        }

        final int size = features.size();
        final Geometry[] geometries = new Geometry[size];

        int finalGeometryColumnIndex = geometryColumnIndex;
        IntStream.range(0, size).parallel().forEach(i -> {
            WktGeometryParser threadParser = new WktGeometryParser();
            final String wkt = features.get(i).getAttributeValues()[finalGeometryColumnIndex];
            try {
                geometries[i] = threadParser.parse(wkt);
            } catch (WktGeometryParser.WktParseException e) {
                // Skip invalid geometries
            }
        });

        int validCount = 0;
        for (int i = 0; i < size; i++) {
            if (geometries[i] != null) {
                features.get(i).setGeometry(geometries[i]);
                validCount++;
            }
        }

        if (validCount == 0) {
            throw new IOException("No valid geometries found");
        }

        GeometryType detectedGeometryType = geometryType;
        if (detectedGeometryType == null) {
            for (FeatureData feature : features) {
                Geometry geom = feature.getGeometry();
                if (geom != null) {
                    if (geom instanceof org.locationtech.jts.geom.MultiPolygon) {
                        detectedGeometryType = GeometryType.MULTIPOLYGON;
                    } else if (geom instanceof org.locationtech.jts.geom.MultiLineString) {
                        detectedGeometryType = GeometryType.MULTILINESTRING;
                    } else if (geom instanceof org.locationtech.jts.geom.MultiPoint) {
                        detectedGeometryType = GeometryType.MULTIPOINT;
                    } else if (geom instanceof Point) {
                        detectedGeometryType = GeometryType.POINT;
                    } else if (geom instanceof LineString) {
                        detectedGeometryType = GeometryType.LINESTRING;
                    } else if (geom instanceof Polygon) {
                        detectedGeometryType = GeometryType.POLYGON;
                    }
                    if (detectedGeometryType != null) break;
                }
            }
            if (detectedGeometryType == null) {
                detectedGeometryType = GeometryType.POINT;
            }
        }

        List<String> attributeColumns = new ArrayList<>();
        for (int i = 0; i < columnNames.length; i++) {
            if (i != geometryColumnIndex) {
                attributeColumns.add(columnNames[i]);
            }
        }

        File outputShp = new File(outputDir, getBaseName(inputFile) + ".shp");
        ShapefileWriter shpWriter = new ShapefileWriter();
        shpWriter.write(features, outputShp, detectedGeometryType, attributeColumns, encoding);

        return outputShp;
    }

    public File convertFromShapefile(File inputFile, File outputFile, String encoding) throws Exception {
        if (!inputFile.exists()) {
            throw new IOException("Input file not found: " + inputFile);
        }

        // 读取SHP文件
        ShapefileReader reader = new ShapefileReader(inputFile);
        List<FeatureData> features;
        try {
            features = reader.readAll();
        } finally {
            reader.close();
        }

        String[] columnNames = reader.getColumnNames();

        // 写入目标文件
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
