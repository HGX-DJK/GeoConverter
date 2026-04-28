package com.hgx.model;

import org.locationtech.jts.geom.Geometry;

public class FeatureData {
    private Geometry geometry;
    private final String[] attributeNames;
    private final String[] attributeValues;

    public FeatureData(String[] attributeNames, String[] attributeValues) {
        this.attributeNames = attributeNames;
        this.attributeValues = attributeValues;
    }

    public Geometry getGeometry() {
        return geometry;
    }

    public void setGeometry(Geometry geometry) {
        this.geometry = geometry;
    }

    public String[] getAttributeNames() {
        return attributeNames;
    }

    public String[] getAttributeValues() {
        return attributeValues;
    }

    public String getAttributeValue(String columnName) {
        for (int i = 0; i < attributeNames.length; i++) {
            if (attributeNames[i].equalsIgnoreCase(columnName)) {
                return attributeValues[i];
            }
        }
        return null;
    }
}
