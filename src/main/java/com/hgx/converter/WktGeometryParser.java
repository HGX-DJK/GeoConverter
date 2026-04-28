package com.hgx.converter;

import com.hgx.model.GeometryType;
import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.util.ArrayList;
import java.util.List;

public class WktGeometryParser {

    private final GeometryFactory geometryFactory;
    private final WKTReader wktReader;

    public WktGeometryParser() {
        this.geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
        this.wktReader = new WKTReader(geometryFactory);
    }

    public Geometry parse(String wkt) throws WktParseException {
        if (wkt == null || wkt.trim().isEmpty()) {
            throw new WktParseException("WKT string is null or empty");
        }

        // 去除字符串两端的引号
        String trimmedWkt = wkt.trim();
        if (trimmedWkt.startsWith("\"") && trimmedWkt.endsWith("\"") || trimmedWkt.startsWith("'") && trimmedWkt.endsWith("'")) {
            trimmedWkt = trimmedWkt.substring(1, trimmedWkt.length() - 1);
        }

        try {
            return wktReader.read(trimmedWkt);
        } catch (ParseException e) {
            throw new WktParseException("Failed to parse WKT: " + wkt, e);
        }
    }

    /**
     * 判断字符串是否可能是有效的 WKT 几何
     */
    public boolean canParse(String wkt) {
        if (wkt == null || wkt.trim().isEmpty()) {
            return false;
        }
        String trimmedWkt = wkt.trim();
        if (trimmedWkt.startsWith("\"") && trimmedWkt.endsWith("\"") || trimmedWkt.startsWith("'") && trimmedWkt.endsWith("'")) {
            trimmedWkt = trimmedWkt.substring(1, trimmedWkt.length() - 1);
        }
        String upper = trimmedWkt.toUpperCase();
        return upper.startsWith("POINT") || upper.startsWith("LINESTRING") || upper.startsWith("POLYGON")
                || upper.startsWith("MULTIPOINT") || upper.startsWith("MULTILINESTRING") || upper.startsWith("MULTIPOLYGON");
    }

    public GeometryType detectGeometryType(String wkt) {
        if (wkt == null || wkt.trim().isEmpty()) {
            return null;
        }
        // 去除字符串两端的引号
        String trimmedWkt = wkt.trim();
        if (trimmedWkt.startsWith("\"") && trimmedWkt.endsWith("\"") || trimmedWkt.startsWith("'") && trimmedWkt.endsWith("'")) {
            trimmedWkt = trimmedWkt.substring(1, trimmedWkt.length() - 1);
        }
        String upper = trimmedWkt.toUpperCase();
        if (upper.startsWith("MULTIPOINT")) {
            return GeometryType.MULTIPOINT;
        } else if (upper.startsWith("MULTILINESTRING")) {
            return GeometryType.MULTILINESTRING;
        } else if (upper.startsWith("MULTIPOLYGON")) {
            return GeometryType.MULTIPOLYGON;
        } else if (upper.startsWith("POINT")) {
            return GeometryType.POINT;
        } else if (upper.startsWith("LINESTRING")) {
            return GeometryType.LINESTRING;
        } else if (upper.startsWith("POLYGON")) {
            return GeometryType.POLYGON;
        }
        return null;
    }

    public List<ParsedGeometryResult> parseBatch(String[] wktStrings) {
        List<ParsedGeometryResult> results = new ArrayList<>();
        for (int i = 0; i < wktStrings.length; i++) {
            try {
                Geometry geometry = parse(wktStrings[i]);
                GeometryType type = getGeometryType(geometry);
                results.add(new ParsedGeometryResult(i, geometry, type, null));
            } catch (WktParseException e) {
                results.add(new ParsedGeometryResult(i, null, null, e.getMessage()));
            }
        }
        return results;
    }

    private GeometryType getGeometryType(Geometry geometry) {
        if (geometry instanceof Point) {
            return GeometryType.POINT;
        } else if (geometry instanceof LineString) {
            return GeometryType.LINESTRING;
        } else if (geometry instanceof Polygon) {
            return GeometryType.POLYGON;
        }
        return null;
    }

    public static class ParsedGeometryResult {
        public final int index;
        public final Geometry geometry;
        public final GeometryType type;
        public final String error;

        public ParsedGeometryResult(int index, Geometry geometry, GeometryType type, String error) {
            this.index = index;
            this.geometry = geometry;
            this.type = type;
            this.error = error;
        }

        public boolean isValid() {
            return geometry != null;
        }
    }

    public static class WktParseException extends Exception {
        public WktParseException(String message) {
            super(message);
        }

        public WktParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
