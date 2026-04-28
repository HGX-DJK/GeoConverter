package com.hgx.model;

public enum GeometryType {
    POINT("Point", "Point"),
    LINESTRING("LineString", "LineString"),
    POLYGON("Polygon", "Polygon"),
    MULTIPOINT("MultiPoint", "MultiPoint"),
    MULTILINESTRING("MultiLineString", "MultiLineString"),
    MULTIPOLYGON("MultiPolygon", "MultiPolygon");

    private final String wktName;
    private final String className;

    GeometryType(String wktName, String className) {
        this.wktName = wktName;
        this.className = className;
    }

    public String getWktName() {
        return wktName;
    }

    public String getClassName() {
        return className;
    }
}
