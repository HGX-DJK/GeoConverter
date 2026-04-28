package com.hgx.converter.fromshape;

import com.hgx.model.FeatureData;
import com.hgx.converter.DataReader;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.simple.SimpleFeatureTypeImpl;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.GeometryDescriptor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class ShapefileReader implements DataReader {

    private final File file;
    private String[] columnNames;
    private List<FeatureData> features;

    public ShapefileReader(File file) {
        this.file = file;
    }

    @Override
    public List<FeatureData> readAll() throws IOException {
        ShapefileDataStore dataStore = null;
        SimpleFeatureIterator iterator = null;
        try {
            dataStore = new ShapefileDataStore(file.toURI().toURL());

            // 尝试检测编码
            File cpgFile = new File(file.getParent(), file.getName().replaceAll("\\.shp$", ".cpg"));
            if (cpgFile.exists()) {
                String encoding = readFile(cpgFile).trim();
                if (!encoding.isEmpty()) {
                    dataStore.setCharset(Charset.forName(encoding));
                }
            }

            String typeName = dataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = dataStore.getFeatureSource(typeName);
            SimpleFeatureTypeImpl schema = (SimpleFeatureTypeImpl) featureSource.getSchema();

            // 分别获取几何列名和属性列名
            List<String> geometryColumnNames = new ArrayList<>();
            List<String> attributeColumnNames = new ArrayList<>();

            for (AttributeDescriptor descriptor : schema.getAttributeDescriptors()) {
                String name = descriptor.getLocalName();
                if (descriptor instanceof GeometryDescriptor) {
                    geometryColumnNames.add(name);
                } else {
                    attributeColumnNames.add(name);
                }
            }

            // 最终列顺序：几何列在前，属性列在后
            List<String> allColumnNames = new ArrayList<>(geometryColumnNames);
            allColumnNames.addAll(attributeColumnNames);
            columnNames = allColumnNames.toArray(new String[0]);

            // 读取所有要素
            features = new ArrayList<>();
            iterator = featureSource.getFeatures().features();
            int featureCount = 0;
            while (iterator.hasNext()) {
                SimpleFeature feature = iterator.next();
                FeatureData featureData = buildFeatureData(feature, geometryColumnNames, attributeColumnNames);
                features.add(featureData);
                featureCount++;
            }

            return features;
        } finally {
            if (iterator != null) {
                iterator.close();
            }
            if (dataStore != null) {
                dataStore.dispose();
            }
        }
    }

    private String readFile(File file) throws IOException {
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private FeatureData buildFeatureData(SimpleFeature feature,
                                          List<String> geometryColumns,
                                          List<String> attributeColumns) {
        int totalCount = columnNames.length;
        String[] attributeValues = new String[totalCount];
        Geometry geometry = null;

        // 按 columnNames 的顺序填充值
        for (int i = 0; i < totalCount; i++) {
            String columnName = columnNames[i];

            // 检查是否是几何列
            if (geometryColumns.contains(columnName)) {
                Object value = feature.getAttribute(i); // 按索引获取
                if (value instanceof Geometry) {
                    geometry = (Geometry) value;
                    attributeValues[i] = geometry.toText();
                } else {
                    attributeValues[i] = value != null ? value.toString() : "";
                }
            } else {
                // 属性列
                Object value = feature.getAttribute(i); // 按索引获取
                attributeValues[i] = value != null ? value.toString() : "";
            }
        }

        FeatureData featureData = new FeatureData(columnNames, attributeValues);
        featureData.setGeometry(geometry);
        return featureData;
    }

    @Override
    public String[] getColumnNames() {
        return columnNames;
    }

    @Override
    public void close() throws IOException {
    }
}