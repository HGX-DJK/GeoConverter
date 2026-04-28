package com.hgx.converter;

import com.hgx.model.FeatureData;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

public interface DataWriter extends Closeable {

    void write(List<FeatureData> features, String[] columnNames) throws IOException;

}
