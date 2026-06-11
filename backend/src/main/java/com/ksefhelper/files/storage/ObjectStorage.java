package com.ksefhelper.files.storage;

public interface ObjectStorage {
    void put(String key, byte[] bytes, String contentType, String checksum);

    byte[] read(String key);

    void delete(String key);

    boolean exists(String key);
}
