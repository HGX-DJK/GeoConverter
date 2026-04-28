package com.hgx.converter;

import com.hgx.model.GeometryType;

import java.util.HashSet;
import java.util.Set;

public class GeometryColumnDetector {

    private static final String[] GEOMETRY_COLUMN_INDICATORS = {
        "geometry", "geom", "the_geom", "shape", "wkt", "location",
        "geom_wkt", "geometry_wkt", "shape_wkt", "the_geometry"
    };

    public static int detectByName(String[] columnNames) {
        for (int i = 0; i < columnNames.length; i++) {
            String name = columnNames[i].toLowerCase().trim();
            for (String indicator : GEOMETRY_COLUMN_INDICATORS) {
                if (name.equals(indicator) || name.contains(indicator)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static int detectBySampling(String[] columnNames, String[][] sampleValues,
                                        WktGeometryParser wktParser) {
        int[] validCounts = new int[columnNames.length];

        int sampleSize = Math.min(sampleValues.length, 10);
        for (int row = 0; row < sampleSize; row++) {
            for (int col = 0; col < sampleValues[row].length && col < columnNames.length; col++) {
                String value = sampleValues[row][col];
                GeometryType type = wktParser.detectGeometryType(value);
                if (type != null) {
                    try {
                        wktParser.parse(value);
                        validCounts[col]++;
                    } catch (WktGeometryParser.WktParseException e) {
                    }
                }
            }
        }

        int bestColumn = -1;
        int maxCount = 0;
        for (int col = 0; col < validCounts.length; col++) {
            if (validCounts[col] > maxCount) {
                maxCount = validCounts[col];
                bestColumn = col;
            }
        }

        if (maxCount > sampleSize / 2) {
            return bestColumn;
        }
        return -1;
    }

    public static int autoDetect(String[] columnNames, String[][] allRows,
                                 WktGeometryParser wktParser) {
        int byName = detectByName(columnNames);
        if (byName >= 0) {
            return byName;
        }

        if (allRows != null && allRows.length > 0) {
            return detectBySampling(columnNames, allRows, wktParser);
        }

        return -1;
    }
}
