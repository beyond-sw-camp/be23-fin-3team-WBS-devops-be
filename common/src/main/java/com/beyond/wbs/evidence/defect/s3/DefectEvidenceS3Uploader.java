package com.beyond.wbs.evidence.defect.s3;

import com.beyond.wbs.evidence.defect.domain.DefectEvidenceSourceType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 불량 사진 전용 S3 업로더.
 *
 * 흐름(서버 경유):
 *  1) 클라이언트 multipart → 서버 수신
 *  2) 서버가 {@link #uploadBytes} 로 S3 PUT
 *  3) 다운로드는 {@link #presignDownload} 로 presigned GET URL 발급 (브라우저 직접)
 *
 * 지시서 PDF와 같은 버킷을 재사용하되, 키 prefix(defect-evidence/)로 분리.
 */
@Component
@ConditionalOnProperty(name = "aws.s3.defect-evidence-bucket")
public class DefectEvidenceS3Uploader {

    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration downloadTtl;

    public DefectEvidenceS3Uploader(
        S3Client s3Client,
        @Value("${aws.s3.defect-evidence-bucket}") String bucket,
        @Value("${aws.s3.defect-evidence-download-ttl-seconds:300}") long downloadTtlSeconds,
        @Value("${aws.credentials.access-key}") String accessKey,
        @Value("${aws.credentials.secret-key}") String secretKey,
        @Value("${aws.region}") String region
    ) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.downloadTtl = Duration.ofSeconds(downloadTtlSeconds);
        this.s3Presigner = S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
            .build();
    }

    public String buildKey(UUID clientId, DefectEvidenceSourceType sourceType,
                           UUID sourceId, UUID evidenceId, String mimeType) {
        // defect-evidence/ prefix — 지시서 PDF와 같은 버킷에 둘 때 분리·필터·라이프사이클 정책 적용 용이.
        return "defect-evidence/%s/%s/%s/%s/%s.%s".formatted(
            clientId,
            sourceType.name().toLowerCase(),
            LocalDate.now().format(YYYY_MM),
            sourceId,
            evidenceId,
            extensionOf(mimeType)
        );
    }

    /**
     * 서버가 보유한 byte[]를 S3에 업로드.
     * 호출 측은 사전에 mime/size 검증을 마쳐야 함.
     */
    public UploadResult uploadBytes(String s3Key, String contentType, byte[] bytes) {
        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build(),
            RequestBody.fromBytes(bytes));
        return new UploadResult(s3Key, (long) bytes.length, sha256Hex(bytes));
    }

    public String presignDownload(String s3Key) {
        GetObjectRequest get = GetObjectRequest.builder()
            .bucket(bucket)
            .key(s3Key)
            .build();
        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
            .signatureDuration(downloadTtl)
            .getObjectRequest(get)
            .build();
        return s3Presigner.presignGetObject(presign).url().toString();
    }

    /**
     * 객체 존재 검증 — 운영자 화면에서 무결성 점검할 때 사용.
     * 객체 없으면 null.
     */
    public HeadResult headOrNull(String s3Key) {
        try {
            HeadObjectResponse head = s3Client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(s3Key).build());
            String etag = head.eTag() != null ? head.eTag().replace("\"", "") : null;
            return new HeadResult(head.contentLength(), etag);
        } catch (NoSuchKeyException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public void delete(String s3Key) {
        s3Client.deleteObject(b -> b.bucket(bucket).key(s3Key));
    }

    private static String extensionOf(String mimeType) {
        if (mimeType == null) return "bin";
        return switch (mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpg";
            case "image/png"               -> "png";
            case "image/webp"              -> "webp";
            case "image/heic", "image/heif"-> "heic";
            default                        -> "bin";
        };
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record UploadResult(String s3Key, long fileSize, String sha256) {}
    public record HeadResult(Long size, String etag) {}
}
