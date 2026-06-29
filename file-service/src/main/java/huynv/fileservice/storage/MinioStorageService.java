package huynv.fileservice.storage;

import huynv.eventinfra.resilience.ResilienceExecutor;
import huynv.fileservice.exception.StorageOperationException;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implements the object storage abstraction using MinIO through the AWS S3-compatible SDK.
 */
@Service
public class MinioStorageService implements ObjectStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final ResilienceExecutor resilienceExecutor;

    /**
     * Creates a storage service backed by an S3-compatible client and presigner.
     *
     * @param s3Client S3-compatible client used for object operations.
     * @param s3Presigner S3-compatible presigner used for direct client URLs.
     * @param resilienceExecutor Shared resilience executor used to protect storage calls.
     */
    public MinioStorageService(S3Client s3Client, S3Presigner s3Presigner, ResilienceExecutor resilienceExecutor) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
        this.s3Presigner = Objects.requireNonNull(s3Presigner, "s3Presigner");
        this.resilienceExecutor = Objects.requireNonNull(resilienceExecutor, "resilienceExecutor");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ensureBucketExists(String bucket) {
        resilienceExecutor.execute("file-storage-bucket", () -> {
            try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (NoSuchBucketException ex) {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404) {
                    s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                    return null;
                }
                throw storageException("STORAGE_BUCKET_CHECK_FAILED", "Failed to ensure the storage bucket exists.", ex);
            } catch (Exception ex) {
                throw storageException("STORAGE_BUCKET_CHECK_FAILED", "Failed to ensure the storage bucket exists.", ex);
            }
            return null;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StoredObjectInfo upload(String bucket, String objectKey, Path filePath, String contentType) {
        return resilienceExecutor.execute("file-storage-upload", () -> {
            try {
            ensureBucketExists(bucket);
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            s3Client.putObject(request, RequestBody.fromFile(filePath));
            return statObject(bucket, objectKey);
            } catch (StorageOperationException ex) {
                throw ex;
            } catch (Exception ex) {
                throw storageException("STORAGE_UPLOAD_FAILED", "Failed to upload the object to MinIO.", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StoredObjectInfo statObject(String bucket, String objectKey) {
        return resilienceExecutor.execute("file-storage-stat", () -> {
            try {
            HeadObjectResponse response = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(objectKey).build()
            );
            return new StoredObjectInfo(bucket, objectKey, response.contentLength(), response.contentType(), response.eTag());
            } catch (NoSuchKeyException ex) {
                throw storageException("STORAGE_OBJECT_NOT_FOUND", "The storage object does not exist.", ex);
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404) {
                    throw storageException("STORAGE_OBJECT_NOT_FOUND", "The storage object does not exist.", ex);
                }
                throw storageException("STORAGE_STAT_FAILED", "Failed to inspect the storage object.", ex);
            } catch (Exception ex) {
                throw storageException("STORAGE_STAT_FAILED", "Failed to inspect the storage object.", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PresignedUploadDetails generatePresignedUpload(String bucket, String objectKey, String contentType, Duration expiresIn) {
        return resilienceExecutor.execute("file-storage-presign-upload", () -> {
            try {
            ensureBucketExists(bucket);
            PutObjectRequest objectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                    PutObjectPresignRequest.builder().signatureDuration(expiresIn).putObjectRequest(objectRequest).build()
            );
            return new PresignedUploadDetails(
                    bucket,
                    objectKey,
                    presigned.url().toString(),
                    Map.of("Content-Type", contentType),
                    Instant.now().plus(expiresIn)
            );
            } catch (Exception ex) {
                throw storageException("STORAGE_PRESIGN_UPLOAD_FAILED", "Failed to generate a pre-signed upload URL.", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MultipartUploadInitiation initiateMultipartUpload(String bucket, String objectKey, String contentType) {
        return resilienceExecutor.execute("file-storage-initiate-multipart", () -> {
            try {
                ensureBucketExists(bucket);
                CreateMultipartUploadResponse response = s3Client.createMultipartUpload(
                        CreateMultipartUploadRequest.builder()
                                .bucket(bucket)
                                .key(objectKey)
                                .contentType(contentType)
                                .build()
                );
                return new MultipartUploadInitiation(response.uploadId());
            } catch (Exception ex) {
                throw storageException("STORAGE_MULTIPART_INITIATE_FAILED", "Failed to initiate the multipart upload.", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PresignedMultipartUploadPartDetails generatePresignedMultipartUploadPart(String bucket, String objectKey, String uploadId, int partNumber, Duration expiresIn) {
        return resilienceExecutor.execute("file-storage-presign-multipart-part", () -> {
            try {
                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build();
                PresignedUploadPartRequest presignedRequest = s3Presigner.presignUploadPart(
                        UploadPartPresignRequest.builder()
                                .signatureDuration(expiresIn)
                                .uploadPartRequest(uploadPartRequest)
                                .build()
                );
                return new PresignedMultipartUploadPartDetails(
                        bucket,
                        objectKey,
                        uploadId,
                        partNumber,
                        presignedRequest.url().toString(),
                        Map.of(),
                        Instant.now().plus(expiresIn)
                );
            } catch (Exception ex) {
                throw storageException("STORAGE_PRESIGN_MULTIPART_PART_FAILED", "Failed to generate a pre-signed multipart part URL.", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StoredObjectInfo completeMultipartUpload(String bucket, String objectKey, String uploadId, List<MultipartCompletedPart> parts) {
        return resilienceExecutor.execute("file-storage-complete-multipart", () -> {
            try {
                CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                        .parts(parts.stream()
                                .map(part -> CompletedPart.builder().partNumber(part.partNumber()).eTag(part.eTag()).build())
                                .toList())
                        .build();
                s3Client.completeMultipartUpload(
                        CompleteMultipartUploadRequest.builder()
                                .bucket(bucket)
                                .key(objectKey)
                                .uploadId(uploadId)
                                .multipartUpload(completedMultipartUpload)
                                .build()
                );
                return statObject(bucket, objectKey);
            } catch (Exception ex) {
                throw storageException("STORAGE_MULTIPART_COMPLETE_FAILED", "Failed to complete the multipart upload.", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void abortMultipartUpload(String bucket, String objectKey, String uploadId) {
        resilienceExecutor.execute("file-storage-abort-multipart", () -> {
            try {
                s3Client.abortMultipartUpload(
                        AbortMultipartUploadRequest.builder()
                                .bucket(bucket)
                                .key(objectKey)
                                .uploadId(uploadId)
                                .build()
                );
            } catch (Exception ex) {
                throw storageException("STORAGE_MULTIPART_ABORT_FAILED", "Failed to abort the multipart upload.", ex);
            }
            return null;
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PresignedDownloadDetails generatePresignedDownload(String bucket, String objectKey, Duration expiresIn) {
        return resilienceExecutor.execute("file-storage-presign-download", () -> {
            try {
            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(
                    GetObjectPresignRequest.builder()
                            .signatureDuration(expiresIn)
                            .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(objectKey).build())
                            .build()
            );
            return new PresignedDownloadDetails(bucket, objectKey, presigned.url().toString(), Instant.now().plus(expiresIn));
            } catch (Exception ex) {
                throw storageException("STORAGE_PRESIGN_DOWNLOAD_FAILED", "Failed to generate a pre-signed download URL.", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DownloadedObject download(String bucket, String objectKey) {
        return resilienceExecutor.execute("file-storage-download", () -> {
            try {
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(objectKey).build()
            );
            return new DownloadedObject(response, response.response().contentLength(), response.response().contentType());
            } catch (NoSuchKeyException ex) {
                throw storageException("STORAGE_OBJECT_NOT_FOUND", "The storage object does not exist.", ex);
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404) {
                    throw storageException("STORAGE_OBJECT_NOT_FOUND", "The storage object does not exist.", ex);
                }
                throw storageException("STORAGE_DOWNLOAD_FAILED", "Failed to download the object from MinIO.", ex);
            } catch (Exception ex) {
                throw storageException("STORAGE_DOWNLOAD_FAILED", "Failed to download the object from MinIO.", ex);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteObject(String bucket, String objectKey) {
        resilienceExecutor.execute("file-storage-delete", () -> {
            try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
            } catch (Exception ex) {
                throw storageException("STORAGE_DELETE_FAILED", "Failed to delete the object from MinIO.", ex);
            }
            return null;
        });
    }

    /**
     * Wraps a lower-level storage exception in a stable application exception.
     *
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error detail.
     * @param cause Lower-level storage exception.
     * @return Returns a wrapped storage operation exception.
     */
    private StorageOperationException storageException(String errorCode, String message, Exception cause) {
        if (cause instanceof StorageOperationException storageOperationException) {
            return storageOperationException;
        }
        if (cause instanceof AwsServiceException awsServiceException && awsServiceException.getMessage() != null) {
            return new StorageOperationException(errorCode, message + " Cause: " + awsServiceException.getMessage());
        }
        return new StorageOperationException(errorCode, message + (cause.getMessage() == null ? "" : " Cause: " + cause.getMessage()));
    }
}

