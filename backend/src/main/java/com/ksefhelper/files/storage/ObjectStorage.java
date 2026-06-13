package com.ksefhelper.files.storage;

import java.io.IOException;
import java.io.OutputStream;

public interface ObjectStorage {
    void put(String key, byte[] bytes, String contentType, String checksum);

    byte[] read(String key);

    default void writeTo(String key, OutputStream output) throws IOException {
        output.write(read(key));
    }

    void delete(String key);

    boolean exists(String key);
}
