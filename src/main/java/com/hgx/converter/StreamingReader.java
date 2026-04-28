package com.hgx.converter;

import com.hgx.model.FeatureData;

import java.io.IOException;

/**
 * 流式数据读取接口，支持大数据量的游标式处理。
 * 与 DataReader.readAll() 不同，这里不会一次性加载全部数据到内存，
 * 而是通过回调逐行消费数据。
 */
public interface StreamingReader extends AutoCloseable {

    /**
     * 流式读取数据，通过回调逐行消费
     * @param consumer 数据消费者，每读取一行调用一次
     * @throws IOException if read error occurs
     */
    void readStream(FeatureConsumer consumer) throws IOException;

    /**
     * 获取列名（需在 readStream 之前调用或在使用前预加载）
     */
    String[] getColumnNames() throws IOException;

    /**
     * 数据消费者接口
     */
    @FunctionalInterface
    interface FeatureConsumer {
        /**
         * 接收一行要素数据
         * @param feature 要素数据
         * @throws IOException if processing error occurs
         */
        void accept(FeatureData feature) throws IOException, WktGeometryParser.WktParseException;
    }
}
