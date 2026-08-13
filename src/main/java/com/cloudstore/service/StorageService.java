package com.cloudstore.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

public interface StorageService {

    /** Uploads content and returns the storage key (object name) used in GCS. */
    String upload(String storageKey, MultipartFile file);

    /** Uploads raw bytes (used for chunked/resumable assembly). */
    String upload(String storageKey, InputStream content, long size, String contentType);

    InputStream download(String storageKey);

    void delete(String storageKey);

    URL generateSignedDownloadUrl(String storageKey, Duration validity);

    String buildStorageKey(String ownerId, String fileId, int version);
}
