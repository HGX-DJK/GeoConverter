package com.hgx.converter.toshape;

import com.hgx.model.FeatureData;
import com.hgx.model.GeometryType;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.feature.DefaultFeatureCollection;
import org.locationtech.jts.geom.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.*;

public class ShapefileWriter {

    private final GeometryFactory geometryFactory;
    private final int srid;

    public ShapefileWriter() {
        this(4326);
    }

    public ShapefileWriter(int srid) {
        this.geometryFactory = new GeometryFactory();
        this.srid = srid;
    }

    private static final int BATCH_SIZE = 10000;

    public void write(List<FeatureData> features, File outputFile,
                      GeometryType geometryType, List<String> attributeColumns)
            throws IOException {
        write(features, outputFile, geometryType, attributeColumns, "GBK");
    }

    public void write(List<FeatureData> features, File outputFile,
                      GeometryType geometryType, List<String> attributeColumns,
                      String encoding)
            throws IOException {

        if (features.isEmpty()) {
            throw new IOException("No features to write");
        }

        SimpleFeatureType schema = buildFeatureType(geometryType, attributeColumns);

        ShapefileDataStoreFactory factory = new ShapefileDataStoreFactory();
        Map<String, Serializable> params = new HashMap<>();
        params.put("url", outputFile.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);
        params.put("charset", encoding);

        ShapefileDataStore dataStore = (ShapefileDataStore) factory.createDataStore(params);
        dataStore.setCharset(Charset.forName(encoding));
        dataStore.createSchema(schema);

        // 创建 .cpg 文件指定编码
        createCpgFile(outputFile, encoding);

        Transaction transaction = new DefaultTransaction();
        try {
            String typeName = dataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
            SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
            featureStore.setTransaction(transaction);

            List<SimpleFeature> batch = new ArrayList<>(BATCH_SIZE);
            int fid = 0;

            for (FeatureData featureData : features) {
                if (featureData.getGeometry() != null) {
                    batch.add(buildFeature(schema, featureData, attributeColumns, fid++));

                    if (batch.size() >= BATCH_SIZE) {
                        flushBatch(featureStore, batch);
                        batch.clear();
                    }
                }
            }

            if (!batch.isEmpty()) {
                flushBatch(featureStore, batch);
            }

            transaction.commit();

        } catch (IOException e) {
            transaction.rollback();
            throw e;
        } finally {
            transaction.close();
            dataStore.dispose();
        }
    }

    private void createCpgFile(File shpFile, String encoding) throws IOException {
        File cpgFile = new File(shpFile.getParent(), shpFile.getName().replaceAll("\\.shp$", ".cpg"));
        try (FileOutputStream fos = new FileOutputStream(cpgFile);
             OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8")) {
            writer.write(encoding);
        }
    }

    private void flushBatch(SimpleFeatureStore featureStore, List<SimpleFeature> batch) throws IOException {
        DefaultFeatureCollection collection = new DefaultFeatureCollection();
        for (SimpleFeature feature : batch) {
            collection.add(feature);
        }
        featureStore.addFeatures(collection);
    }

    private SimpleFeatureType buildFeatureType(GeometryType geometryType,
                                               List<String> attributeColumns) {
        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("Feature");

        String geometryAttributeName = "the_geom";
        Class<? extends Geometry> geometryClass;
        if (geometryType == null) {
            geometryClass = Geometry.class;
        } else {
            switch (geometryType) {
                case POINT:
                    geometryClass = Point.class;
                    break;
                case LINESTRING:
                    geometryClass = LineString.class;
                    break;
                case POLYGON:
                    geometryClass = Polygon.class;
                    break;
                case MULTIPOINT:
                    geometryClass = MultiPoint.class;
                    break;
                case MULTILINESTRING:
                    geometryClass = MultiLineString.class;
                    break;
                case MULTIPOLYGON:
                    geometryClass = MultiPolygon.class;
                    break;
                default:
                    geometryClass = Geometry.class;
            }
        }
        builder.add(geometryAttributeName, geometryClass);

        Set<String> usedNames = new HashSet<>();
        usedNames.add("the_geom");
        for (String col : attributeColumns) {
            String shortName = makeShortName(col, usedNames);
            usedNames.add(shortName);
            builder.add(shortName, String.class);
        }

        return builder.buildFeatureType();
    }

    private String makeShortName(String original, Set<String> used) {
        String shortName = original.length() > 10 ? original.substring(0, 10) : original;
        shortName = shortName.replaceAll("[^a-zA-Z0-9_]", "_");

        if (!used.contains(shortName)) {
            return shortName;
        }
        for (int i = 1; i < 100; i++) {
            String candidate = shortName.length() >= 8
                ? shortName.substring(0, 8) + i
                : shortName + i;
            if (!used.contains(candidate)) {
                return candidate;
            }
        }
        return "attr_" + Math.abs(original.hashCode() % 10000);
    }

    private SimpleFeature buildFeature(SimpleFeatureType schema, FeatureData data,
                                        List<String> attributeColumns, int fid) {
        org.geotools.feature.simple.SimpleFeatureBuilder featureBuilder =
            new org.geotools.feature.simple.SimpleFeatureBuilder(schema);

        featureBuilder.add(data.getGeometry());

        for (String col : attributeColumns) {
            featureBuilder.add(data.getAttributeValue(col));
        }

        return featureBuilder.buildFeature(fid + "");
    }
}
