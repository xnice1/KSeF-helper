package com.ksefhelper.files.storage;

import com.ksefhelper.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalObjectStorageTest {
    @Test
    void storesReadsRestoresAndDeletesObjects() throws Exception {
        Path root = Files.createTempDirectory("ksef-local-storage-");
        LocalObjectStorage storage = new LocalObjectStorage(root.toString());
        byte[] original = "<invoice>one</invoice>".getBytes();
        byte[] backup = original.clone();

        storage.put("organization/invoice.xml", original, "application/xml", "checksum");
        assertThat(storage.exists("organization/invoice.xml")).isTrue();
        assertThat(storage.read("organization/invoice.xml")).isEqualTo(original);

        storage.delete("organization/invoice.xml");
        assertThat(storage.exists("organization/invoice.xml")).isFalse();
        assertThatThrownBy(() -> storage.read("organization/invoice.xml"))
                .isInstanceOf(NotFoundException.class);

        storage.put("organization/invoice.xml", backup, "application/xml", "checksum");
        assertThat(storage.read("organization/invoice.xml")).isEqualTo(original);
    }

    @Test
    void rejectsKeysOutsideTheStorageRoot() throws Exception {
        Path root = Files.createTempDirectory("ksef-local-storage-");
        LocalObjectStorage storage = new LocalObjectStorage(root.toString());

        assertThatThrownBy(() -> storage.put("../escape.xml", new byte[]{1}, "application/xml", "checksum"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
