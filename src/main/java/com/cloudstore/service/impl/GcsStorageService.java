package com.cloudstore.service.impl;

import com.cloudstore.config.GcsProperties;
import com.cloudstore.exception.StorageOperationException;
import com.cloudstore.service.StorageService;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Stores file contents in Google Cloud Storage. Only metadata (storage key) is
 * persisted in PostgreSQL - the actual bytes never touch the application DB.
 * Credentials are supplied externally (see GcsConfig) and are never committed to Git.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GcsStorageService implements StorageService {

    private final Storage storage;
    private final GcsProperties properties;

    @Override
    public String upload(String storageKey, MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return upload(storageKey, is, file.getSize(), file.getContentType());
        } catch (IOException e) {
            log.error("GCS upload failed for key {}", storageKey);
            throw new StorageOperationException("Failed to upload file to storage", e);
        }
    }

    @Override
    public String upload(String storageKey, InputStream content, long size, String contentType) {
        try {
            BlobId blobId = BlobId.of(properties.bucketName(), storageKey);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(contentType != null ? contentType : "application/octet-stream")
                    .build();
            storage.createFrom(blobInfo, content);
            return storageKey;
        } catch (IOException e) {
            log.error("GCS upload failed for key {}", storageKey);
            throw new StorageOperationException("Failed to upload file to storage", e);
        }
    }

    @Override
    public InputStream download(String storageKey) {
        Blob blob = storage.get(BlobId.of(properties.bucketName(), storageKey));
        if (blob == null || !blob.exists()) {
            throw new StorageOperationException("File content not found in storage", null);
        }
        return java.nio.channels.Channels.newInputStream(blob.reader());
    }

    @Override
    public void delete(String storageKey) {
        boolean deleted = storage.delete(BlobId.of(properties.bucketName(), storageKey));
        if (!deleted) {
            log.warn("GCS object {} was already absent during delete", storageKey);
        }
    }

    @Override
    public URL generateSignedDownloadUrl(String storageKey, Duration validity) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.bucketName(), storageKey)).build();
        try {
            return storage.signUrl(blobInfo, validity.toMinutes(), TimeUnit.MINUTES,
                    Storage.SignUrlOption.withV4Signature());
        } catch (Exception e) {
            log.error("Failed to generate signed URL for key {}", storageKey);
            throw new StorageOperationException("Failed to generate download link", e);
        }
    }

    @Override
    public String buildStorageKey(String ownerId, String fileId, int version) {
        return "users/%s/files/%s/v%d/%s".formatted(ownerId, fileId, version, UUID.randomUUID());
    }
}
