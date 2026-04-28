package com.hgx.converter;

import com.hgx.model.FeatureData;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public interface DataReader extends Closeable {

    List<FeatureData> readAll() throws IOException;

    String[] getColumnNames();
}
