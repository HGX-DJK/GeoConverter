package com.hgx;

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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "shp-converter", description = "Convert between CSV/Excel and ESRI Shapefile format")
public class Application implements Callable<Integer> {

    @Option(names = {"-i", "--input"}, description = "Input file (CSV, Excel, or Shapefile)", required = true)
    private File inputFile;

    @Option(names = {"-o", "--output"}, description = "Output file or directory", required = true)
    private File output;

    @Option(names = {"-g", "--geometry-column"}, description = "Name of geometry column (auto-detect if not specified)")
    private String geometryColumn;

    @Option(names = {"-t", "--geometry-type"}, description = "Force geometry type: POINT, LINESTRING, POLYGON")
    private GeometryType forcedGeometryType;

    @Option(names = {"-e", "--encoding"}, description = "File encoding (default: UTF-8)")
    private String encoding = "UTF-8";

    @Option(names = {"-h", "--help"}, usageHelp = true)
    private boolean help;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Application()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!inputFile.exists()) {
            System.err.println("Error: Input file not found: " + inputFile);
            return 1;
        }

        String fileName = inputFile.getName().toLowerCase();
        if (fileName.endsWith(".shp")) {
            // SHP to CSV/Excel conversion
            return convertShpToOther();
        } else if (fileName.endsWith(".csv") || fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            // CSV/Excel to SHP conversion
            return convertOtherToShp();
        } else {
            System.err.println("Error: Unsupported file format. Use .csv, .xls, .xlsx, or .shp");
            return 1;
        }
    }

    private Integer convertOtherToShp() throws Exception {
        System.out.println("=== CSV/Excel to Shapefile Converter ===");
        System.out.println("Input:  " + inputFile.getAbsolutePath());
        System.out.println("Output: " + output.getAbsolutePath());

        if (!output.exists() && !output.mkdirs()) {
            System.err.println("Error: Cannot create output directory");
            return 1;
        }

        DataReader reader;
        String fileName = inputFile.getName().toLowerCase();
        if (fileName.endsWith(".csv")) {
            reader = new CsvDataReader(inputFile, encoding);
        } else if (fileName.endsWith(".xls") || fileName.endsWith(".xlsx")) {
            reader = new ExcelDataReader(inputFile);
        } else {
            System.err.println("Error: Unsupported file format. Use .csv, .xls, or .xlsx");
            return 1;
        }

        List<FeatureData> features;
        try {
            System.out.println("Reading input file...");
            features = reader.readAll();
            System.out.println("  Read " + features.size() + " rows");
        } finally {
            reader.close();
        }

        String[] columnNames = reader.getColumnNames();
        int geometryColumnIndex;

        if (geometryColumn != null) {
            geometryColumnIndex = -1;
            for (int i = 0; i < columnNames.length; i++) {
                if (columnNames[i].equalsIgnoreCase(geometryColumn)) {
                    geometryColumnIndex = i;
                    break;
                }
            }
            if (geometryColumnIndex < 0) {
                System.err.println("Error: Specified geometry column '" + geometryColumn + "' not found");
                return 1;
            }
        } else {
            System.out.println("Detecting geometry column...");
            geometryColumnIndex = GeometryColumnDetector.detectByName(columnNames);
            if (geometryColumnIndex < 0) {
                String[][] sampleRows = features.stream()
                    .map(FeatureData::getAttributeValues)
                    .toArray(String[][]::new);
                geometryColumnIndex = GeometryColumnDetector.detectBySampling(
                    columnNames, sampleRows, new WktGeometryParser());
            }
            if (geometryColumnIndex < 0) {
                System.err.println("Error: Could not detect geometry column. Please specify with -g option.");
                return 1;
            }
            System.out.println("  Detected geometry column: " + columnNames[geometryColumnIndex]);
        }

        System.out.println("Parsing WKT geometries...");
        WktGeometryParser parser = new WktGeometryParser();
        int validCount = 0;
        int invalidCount = 0;

        for (int i = 0; i < features.size(); i++) {
            FeatureData feature = features.get(i);
            String wkt = feature.getAttributeValues()[geometryColumnIndex];
            try {
                Geometry geometry = parser.parse(wkt);
                feature.setGeometry(geometry);
                validCount++;
            } catch (WktGeometryParser.WktParseException e) {
                invalidCount++;
                System.err.println("  Warning: Row " + (i + 2) + " - Invalid WKT: " + e.getMessage());
            }
        }

        System.out.println("  Valid geometries: " + validCount);
        System.out.println("  Invalid geometries: " + invalidCount);

        if (validCount == 0) {
            System.err.println("Error: No valid geometries found");
            return 1;
        }

        GeometryType geometryType = forcedGeometryType;
        if (geometryType == null) {
            for (FeatureData feature : features) {
                Geometry geom = feature.getGeometry();
                if (geom != null) {
                    if (geom instanceof Point) geometryType = GeometryType.POINT;
                    else if (geom instanceof LineString) geometryType = GeometryType.LINESTRING;
                    else if (geom instanceof Polygon) geometryType = GeometryType.POLYGON;
                    break;
                }
            }
        }

        List<String> attributeColumns = new ArrayList<>();
        for (int i = 0; i < columnNames.length; i++) {
            if (i != geometryColumnIndex) {
                attributeColumns.add(columnNames[i]);
            }
        }

        System.out.println("Writing Shapefile...");
        File outputShp = new File(output, getBaseName(inputFile) + ".shp");
        ShapefileWriter shpWriter = new ShapefileWriter();
        shpWriter.write(features, outputShp, geometryType, attributeColumns);

        System.out.println("Success! Output: " + outputShp.getAbsolutePath());
        return 0;
    }

    private Integer convertShpToOther() throws Exception {
        System.out.println("=== Shapefile to CSV/Excel Converter ===");
        System.out.println("Input:  " + inputFile.getAbsolutePath());
        System.out.println("Output: " + output.getAbsolutePath());

        // 读取SHP文件
        ShapefileReader reader = new ShapefileReader(inputFile);
        List<FeatureData> features;
        try {
            System.out.println("Reading Shapefile...");
            features = reader.readAll();
            System.out.println("  Read " + features.size() + " features");
        } finally {
            reader.close();
        }

        String[] columnNames = reader.getColumnNames();

        // 写入目标文件
        DataWriter writer;
        String outputName = output.getName().toLowerCase();
        if (outputName.endsWith(".csv")) {
            writer = new CsvDataWriter(output, encoding);
        } else if (outputName.endsWith(".xls") || outputName.endsWith(".xlsx")) {
            writer = new ExcelDataWriter(output);
        } else {
            System.err.println("Error: Unsupported output format. Use .csv, .xls, or .xlsx");
            return 1;
        }

        try {
            System.out.println("Writing output file...");
            writer.write(features, columnNames);
        } finally {
            writer.close();
        }

        System.out.println("Success! Output: " + output.getAbsolutePath());
        return 0;
    }

    private String getBaseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
