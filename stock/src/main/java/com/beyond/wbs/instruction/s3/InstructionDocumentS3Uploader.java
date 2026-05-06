package com.beyond.wbs.instruction.s3;

import com.beyond.wbs.document.instruction.domain.InstructionDocumentType;
import com.beyond.wbs.document.instruction.render.exception.InstructionUploadException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
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
 * stock 도메인의 지시서 PDF를 S3에 업로드하고 presigned URL을 발급한다.
 *
 * 공통 인프라 설정은 common의 AwsS3Config가 담당하고,
 * 이 클래스는 stock 전용 저장 규칙과 버킷 프로퍼티만 책임진다.
 */
@Component
@ConditionalOnProperty(name = "aws.s3.instruction-bucket")
public class InstructionDocumentS3Uploader {

    private static final DateTimeFormatter YYYY_MM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration presignTtl;

    public InstructionDocumentS3Uploader(
        S3Client s3Client,
        S3Presigner s3Presigner,
        @Value("${aws.s3.instruction-bucket}") String bucket,
        @Value("${aws.s3.instruction-presign-ttl-seconds:300}") long presignTtlSeconds
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
        this.presignTtl = Duration.ofSeconds(presignTtlSeconds);
    }

    public UploadResult upload(UUID clientId, InstructionDocumentType docType,
                               UUID sourceId, int version, byte[] pdfBytes) {
        String key = buildKey(clientId, docType, sourceId, version);
        String sha256 = sha256Hex(pdfBytes);
        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/pdf")
                    .contentLength((long) pdfBytes.length)
                    .build(),
                RequestBody.fromBytes(pdfBytes));
            return new UploadResult(key, pdfBytes.length, sha256);
        } catch (Exception e) {
            throw new InstructionUploadException(
                "S3 업로드 실패: bucket=" + bucket + " key=" + key, e);
        }
    }

    public String presignDownloadUrl(String s3Key) {
        return presignDownloadUrl(s3Key, presignTtl);
    }

    public String presignDownloadUrl(String s3Key, Duration ttl) {
        GetObjectRequest get = GetObjectRequest.builder()
            .bucket(bucket)
            .key(s3Key)
            .build();
        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
            .signatureDuration(ttl)
            .getObjectRequest(get)
            .build();
        return s3Presigner.presignGetObject(presign).url().toString();
    }

    public String buildKey(UUID clientId, InstructionDocumentType docType,
                           UUID sourceId, int version) {
        return "%s/%s/%s/%s_v%d.pdf".formatted(
            clientId,
            docType.getCode(),
            LocalDate.now().format(YYYY_MM),
            sourceId,
            version
        );
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
}
