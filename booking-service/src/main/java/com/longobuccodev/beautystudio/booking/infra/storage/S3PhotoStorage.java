package com.longobuccodev.beautystudio.booking.infra.storage;

import com.longobuccodev.beautystudio.booking.core.exception.StorageUnavailableException;
import com.longobuccodev.beautystudio.booking.core.storage.PhotoStorage;
import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.context.event.EventListener;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Fotos no MinIO pela API S3. Toda chamada passa por tempo limite e circuit breaker: com o MinIO fora do ar,
 * {@link StorageUnavailableException} (503). O bucket é criado na subida, com leitura pública só de objeto.
 */
public class S3PhotoStorage implements PhotoStorage {

    private static final Logger log = LoggerFactory.getLogger(S3PhotoStorage.class);

    private final S3Client s3;
    private final String bucket;
    private final String mediaUrl;
    private final CircuitBreaker breaker;

    public S3PhotoStorage(BookingProperties properties, CircuitBreakerFactory<?, ?> breakers) {
        BookingProperties.Storage cfg = properties.storage();
        Duration timeout = cfg.timeout();
        this.s3 = S3Client.builder()
                .endpointOverride(cfg.endpoint())
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(cfg.accessKey(), cfg.secretKey())))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(3)).socketTimeout(timeout))
                .overrideConfiguration(c -> c.apiCallTimeout(timeout.plusSeconds(2)))
                .build();
        this.bucket = cfg.bucket();
        String base = properties.publicUrls().mediaUrl();
        this.mediaUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.breaker = breakers.create("minio");
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        call(() -> s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType)
                        .cacheControl("public, max-age=31536000, immutable"),
                RequestBody.fromBytes(content)));
    }

    @Override
    public void delete(String key) {
        call(() -> s3.deleteObject(b -> b.bucket(bucket).key(key)));
    }

    @Override
    public String publicUrl(String key) {
        return key == null ? null : mediaUrl + "/media/" + bucket + "/" + key;
    }

    /** Cria o bucket e a política de leitura pública; falha só gera aviso (o MinIO pode subir depois). */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket() {
        try {
            try {
                s3.headBucket(b -> b.bucket(bucket));
            } catch (NoSuchBucketException e) {
                s3.createBucket(b -> b.bucket(bucket));
                log.info("Bucket {} criado", bucket);
            }
            s3.putBucketPolicy(b -> b.bucket(bucket).policy(publicReadPolicy(bucket)));
        } catch (RuntimeException e) {
            log.warn("Nao foi possivel preparar o bucket {}: {}", bucket, e.getMessage());
        }
    }

    static String publicReadPolicy(String bucket) {
        return """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},\
                "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}""".formatted(bucket);
    }

    private void call(Runnable action) {
        Supplier<Object> supplier = () -> {
            action.run();
            return null;
        };
        breaker.run(supplier, error -> {
            throw new StorageUnavailableException(error);
        });
    }
}
