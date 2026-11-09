package com.ucamp.app.domain.shop;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ucamp.app.common.lock.ShopLock;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import com.ucamp.app.domain.shop.service.ShopImageStorage;
import com.ucamp.app.domain.shop.service.ShopImageCleanupService;
import com.ucamp.app.domain.shop.service.ShopImageObjectStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopImageIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.shop-image.storage", () -> "s3");
        registry.add("app.shop-image.s3.bucket", () -> "test-shop-images");
    }

    @MockitoBean
    private S3Client s3;

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestRestTemplate http;
    @Autowired
    private ShopImageCleanupService cleanupService;
    @Autowired
    private ShopImageObjectStorage objectStorage;
    @Autowired
    private Clock clock;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ShopLock shopLock;

    @BeforeEach
    void setUp() {
        // AWS 연결 없이 실제 S3 저장소 어댑터·서비스·DB·HTTP 계층을 함께 검증한다.
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            try (var input = body.contentStreamProvider().newStream()) {
                objects.put(request.key(), input.readAllBytes());
            }
            return PutObjectResponse.builder().build();
        });
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenAnswer(invocation -> {
            GetObjectRequest request = invocation.getArgument(0);
            byte[] bytes = objects.get(request.key());
            if (bytes == null) {
                throw S3Exception.builder().statusCode(404).build();
            }
            return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes);
        });
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenAnswer(invocation -> {
            DeleteObjectRequest request = invocation.getArgument(0);
            objects.remove(request.key());
            return DeleteObjectResponse.builder().build();
        });
        jdbc.update("INSERT INTO CATEGORY (CATEGORY_ID, NAME) VALUES (9701, '이미지 테스트')");
        LocalDateTime time = LocalDateTime.of(2026, 10, 26, 12, 0);
        // OWNER_1은 인증 공통 stub의 shopId=1을 사용한다. 계정 값은 테스트 전용 더미다.
        jdbc.update("""
                INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE,
                    ADDRESS, LATITUDE, LONGITUDE, PHONE, STATUS, CREATED_AT, UPDATED_AT)
                VALUES (1, 9701, 'CHANGE_ME', 'CHANGE_ME', '이미지 테스트', '11680',
                    '서울', 37.5, 127.0, '02-000-0000', 'ACTIVE', ?, ?)
                """, time, time);
    }

    @ParameterizedTest
    @ValueSource(strings = {"shop", "treatment"})
    @DisplayName("S3 업로드 중 트랜잭션과 예약용 매장 잠금을 유지하지 않는다")
    void upload_slowStorage_allowsReservationLock(String target) throws Exception {
        if (target.equals("treatment")) {
            createTreatment();
        }
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean transactionActive = new AtomicBoolean(true);
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown();
            if (!release.await(15, TimeUnit.SECONDS)) {
                throw new IllegalStateException("테스트 업로드 대기 시간 초과");
            }
            PutObjectRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            try (var input = body.contentStreamProvider().newStream()) {
                objects.put(request.key(), input.readAllBytes());
            }
            return PutObjectResponse.builder().build();
        });
        String path = target.equals("shop") ? "/api/v1/shops/me/image"
                : "/api/v1/shops/me/treatments/9701/image";
        try (var executor = Executors.newFixedThreadPool(2)) {
            var upload = executor.submit(() -> mvc.perform(multipart(HttpMethod.PUT, path)
                    .file(ShopImageFixture.image("png")).header("X-Test-User", "OWNER_1")).andReturn());
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(transactionActive.get()).isFalse();
                var reservation = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                    shopLock.lock(1L);
                    return true;
                }));
                assertThat(reservation.get(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
            assertThat(upload.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"shop", "treatment"})
    @DisplayName("업로드 중 대상이 삭제되어도 새 S3 파일을 정리한다")
    void upload_targetDisappears_removesNewFile(String target) throws Exception {
        if (target.equals("treatment")) {
            createTreatment();
        }
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            try (var input = body.contentStreamProvider().newStream()) {
                objects.put(request.key(), input.readAllBytes());
            }
            jdbc.update(target.equals("shop") ? "DELETE FROM SHOP WHERE SHOP_ID = 1"
                    : "DELETE FROM TREATMENT WHERE TREATMENT_ID = 9701");
            return PutObjectResponse.builder().build();
        });
        String path = target.equals("shop") ? "/api/v1/shops/me/image"
                : "/api/v1/shops/me/treatments/9701/image";
        mvc.perform(multipart(HttpMethod.PUT, path).file(ShopImageFixture.image("png"))
                .header("X-Test-User", "OWNER_1")).andExpect(status().isNotFound());
        assertThat(objects).isEmpty();
    }

    private void createTreatment() {
        jdbc.update("""
                INSERT INTO TREATMENT (TREATMENT_ID, SHOP_ID, NAME, REGULAR_PRICE, PRICE,
                    LAST_MINUTE_PRICE, DURATION_MINUTES, TREATMENT_TYPE, STATUS, CREATED_AT, UPDATED_AT)
                VALUES (9701, 1, '이미지 테스트', 30000, 20000, 10000, 30, 'NORMAL', 'ACTIVE', ?, ?)
                """, LocalDateTime.now(clock), LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("시술 이미지 업로드·교체·삭제 후 과거 URL과 객체를 정리한다")
    void treatmentImage_fullLifecycle_cleansPreviousObjects() throws Exception {
        createTreatment();
        String path = "/api/v1/shops/me/treatments/9701/image";
        String previousUrl = null;
        for (String format : new String[] {"png", "jpg"}) {
            MockMultipartFile file = ShopImageFixture.image(format);
            String response = mvc.perform(multipart(HttpMethod.PUT, path).file(file)
                            .header("X-Test-User", "OWNER_1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.treatmentId").value(9701))
                    .andReturn().getResponse().getContentAsString();
            String url = objectMapper.readTree(response).path("data").path("imageUrl").asText();
            mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(file.getBytes()))
                    .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
            assertThat(jdbc.queryForObject("SELECT IMAGE_URL FROM TREATMENT WHERE TREATMENT_ID=9701",
                    String.class)).isEqualTo(url);
            if (previousUrl != null) {
                mvc.perform(get(previousUrl)).andExpect(status().isNotFound());
            }
            assertThat(objects).hasSize(1);
            previousUrl = url;
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(delete(path).header("X-Test-User", "OWNER_1"))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
        }
        mvc.perform(get(previousUrl)).andExpect(status().isNotFound());
        assertThat(objects).isEmpty();
    }

    @Test
    @DisplayName("시술 이미지의 비로그인·역할·소유권·없는 시술·잘못된 파일을 거부한다")
    void treatmentImage_invalidRequests_doNotStoreFiles() throws Exception {
        createTreatment();
        String path = "/api/v1/shops/me/treatments/9701/image";
        mvc.perform(multipart(HttpMethod.PUT, path).file(ShopImageFixture.image("png")))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(path)).andExpect(status().isUnauthorized());
        for (String actor : new String[] {"CUSTOMER_1", "ADMIN"}) {
            mvc.perform(multipart(HttpMethod.PUT, path).file(ShopImageFixture.image("png"))
                    .header("X-Test-User", actor)).andExpect(status().isForbidden());
            mvc.perform(delete(path).header("X-Test-User", actor)).andExpect(status().isForbidden());
        }
        jdbc.update("""
                INSERT INTO SHOP (SHOP_ID, CATEGORY_ID, LOGIN_ID, PASSWORD_HASH, NAME, REGION_CODE,
                    ADDRESS, LATITUDE, LONGITUDE, PHONE, STATUS, CREATED_AT, UPDATED_AT)
                SELECT 2, CATEGORY_ID, 'CHANGE_ME_2', 'CHANGE_ME', NAME, REGION_CODE,
                    ADDRESS, LATITUDE, LONGITUDE, PHONE, STATUS, CREATED_AT, UPDATED_AT
                FROM SHOP WHERE SHOP_ID = 1
                """);
        // 매장 2가 있어도 OWNER_2는 매장 1의 시술을 찾거나 변경할 수 없다.
        mvc.perform(multipart(HttpMethod.PUT, path).file(ShopImageFixture.image("png"))
                .header("X-Test-User", "OWNER_2")).andExpect(status().isNotFound());
        mvc.perform(delete(path).header("X-Test-User", "OWNER_2")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/shops/me/treatments/999999/image")
                .header("X-Test-User", "OWNER_1")).andExpect(status().isNotFound());
        mvc.perform(multipart(HttpMethod.PUT, path).header("X-Test-User", "OWNER_1"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(HttpMethod.PUT, path).file(ShopImageFixture.image("png"))
                        .file(ShopImageFixture.image("png")).header("X-Test-User", "OWNER_1"))
                .andExpect(status().isBadRequest());
        assertThat(objects).isEmpty();
    }

    @Test
    @DisplayName("시술 이미지 DB 커밋 실패 시 기존 URL과 파일을 유지하고 새 파일을 정리한다")
    void treatmentImage_commitFailure_keepsPreviousImage() throws Exception {
        createTreatment();
        String path = "/api/v1/shops/me/treatments/9701/image";
        String response = mvc.perform(multipart(HttpMethod.PUT, path).file(ShopImageFixture.image("png"))
                        .header("X-Test-User", "OWNER_1"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String url = objectMapper.readTree(response).path("data").path("imageUrl").asText();
        jdbc.execute("ALTER TABLE TREATMENT ADD CONSTRAINT CK_TEST_TREATMENT_IMAGE CHECK "
                + "(TREATMENT_ID <> 9701 OR IMAGE_URL = '" + url + "')");
        mvc.perform(multipart(HttpMethod.PUT, path).file(ShopImageFixture.image("jpg"))
                        .header("X-Test-User", "OWNER_1"))
                .andExpect(status().is5xxServerError());
        assertThat(jdbc.queryForObject("SELECT IMAGE_URL FROM TREATMENT WHERE TREATMENT_ID=9701",
                String.class)).isEqualTo(url);
        assertThat(objects).hasSize(1);
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("ALTER TABLE SHOP DROP CONSTRAINT IF EXISTS CK_TEST_SHOP_IMAGE");
        jdbc.execute("ALTER TABLE TREATMENT DROP CONSTRAINT IF EXISTS CK_TEST_TREATMENT_IMAGE");
        jdbc.update("DELETE FROM SHOP_IMAGE_CLEANUP");
        jdbc.update("DELETE FROM TREATMENT WHERE TREATMENT_ID = 9701");
        jdbc.update("DELETE FROM SHOP WHERE SHOP_ID IN (1, 2)");
        jdbc.update("DELETE FROM CATEGORY WHERE CATEGORY_ID = 9701");
        objects.clear();
    }

    @Test
    @DisplayName("업로드·공개 조회·교체·삭제를 수행하고 과거 URL은 접근할 수 없다")
    void image_fullLifecycle_persistsAndServesCurrentOnly() throws Exception {
        MockMultipartFile png = ShopImageFixture.image("png");
        String originalUrl = upload(png);
        mvc.perform(get(originalUrl)).andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png.getBytes()))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));

        MockMultipartFile jpg = ShopImageFixture.image("jpg");
        String replacedUrl = upload(jpg);
        assertThat(replacedUrl).isNotEqualTo(originalUrl);
        mvc.perform(get(originalUrl)).andExpect(status().isNotFound());
        mvc.perform(get(replacedUrl)).andExpect(status().isOk()).andExpect(content().bytes(jpg.getBytes()));

        mvc.perform(delete("/api/v1/shops/me/image").header("X-Test-User", "OWNER_1"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get(replacedUrl)).andExpect(status().isNotFound());
        assertThat(imageUrl()).isNull();
        assertThat(objects).isEmpty();
    }

    @Test
    @DisplayName("S3 저장 후 응답이 유실되어도 새 객체를 정리하고 기존 이미지·DB를 유지한다")
    void upload_s3ResponseLost_keepsPreviousAndRemovesNewObject() throws Exception {
        MockMultipartFile png = ShopImageFixture.image("png");
        String url = upload(png);
        doAnswer(invocation -> {
            PutObjectRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            try (var input = body.contentStreamProvider().newStream()) {
                objects.put(request.key(), input.readAllBytes());
            }
            throw SdkClientException.create("Simulated response loss");
        }).when(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/me/image").file(ShopImageFixture.image("jpg"))
                .header("X-Test-User", "OWNER_1")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
        assertThat(imageUrl()).isEqualTo(url);
        assertThat(objects).hasSize(1);
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(png.getBytes()));
    }

    @Test
    @DisplayName("S3 업로드 후 실제 DB 제약 위반으로 커밋이 실패하면 새 객체만 삭제하고 기존 이미지를 유지한다")
    void upload_databaseConstraintFailure_rollsBackAndDeletesNewS3Object() throws Exception {
        MockMultipartFile original = ShopImageFixture.image("png");
        String url = upload(original);
        String originalKey = objects.keySet().iterator().next();
        rejectJpgInDatabase();

        uploadJpgExpectDatabaseFailure();

        assertThat(imageUrl()).isEqualTo(url);
        assertThat(objects).containsOnlyKeys(originalKey);
        assertThat(cleanupCount()).isZero();
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(original.getBytes()));
    }

    @Test
    @DisplayName("DB 저장과 S3 정리가 함께 실패하면 작업을 별도 DB 트랜잭션으로 남겨 나중에 새 객체만 정리한다")
    void upload_databaseAndDeleteFailure_persistsRetryAndKeepsOriginal() throws Exception {
        String url = upload(ShopImageFixture.image("png"));
        String originalKey = objects.keySet().iterator().next();
        rejectJpgInDatabase();
        doThrow(SdkClientException.create("Simulated delete failure"))
                .when(s3).deleteObject(any(DeleteObjectRequest.class));

        uploadJpgExpectDatabaseFailure();

        assertThat(imageUrl()).isEqualTo(url);
        assertThat(objects).hasSize(2);
        assertThat(cleanupCount()).isEqualTo(1);
        restoreS3Delete();
        retryDueCleanups();
        assertThat(objects).containsOnlyKeys(originalKey);
        assertThat(cleanupCount()).isZero();
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("교체 커밋 후 이전 S3 삭제가 실패해도 성공 응답과 새 이미지를 유지하고 이전 객체만 재시도한다")
    void upload_oldObjectDeleteFailure_keepsCommittedImageAndRetriesOldObject() throws Exception {
        String oldUrl = upload(ShopImageFixture.image("png"));
        doThrow(SdkClientException.create("Simulated delete failure"))
                .when(s3).deleteObject(any(DeleteObjectRequest.class));
        String newUrl = upload(ShopImageFixture.image("jpg"));
        assertThat(imageUrl()).isEqualTo(newUrl);
        assertThat(objects).hasSize(2);
        assertThat(cleanupCount()).isEqualTo(1);
        restoreS3Delete();
        retryDueCleanups();
        assertThat(objects).hasSize(1);
        assertThat(cleanupCount()).isZero();
        mvc.perform(get(oldUrl)).andExpect(status().isNotFound());
        mvc.perform(get(newUrl)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("정리 작업 중복 등록을 막고 DB에서 현재 사용하는 이미지는 재시도 시에도 삭제하지 않는다")
    void cleanup_currentImage_cancelsTaskWithoutDeletingObject() throws Exception {
        String url = upload(ShopImageFixture.image("png"));
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        cleanupService.enqueue(objectStorage.scope(), fileName);
        cleanupService.enqueue(objectStorage.scope(), fileName);
        assertThat(cleanupCount()).isEqualTo(1);
        retryDueCleanups();
        assertThat(cleanupCount()).isZero();
        assertThat(objects).hasSize(1);
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("누락·위장·손상·용량 초과로 교체가 실패해도 기존 이미지가 조회된다")
    void upload_invalidFile_keepsPreviousUrlAndBytes() throws Exception {
        MockMultipartFile png = ShopImageFixture.image("png");
        String url = upload(png);
        MockMultipartFile[] invalidFiles = {
                new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
                new MockMultipartFile("file", "bad.txt", "text/plain", "text".getBytes()),
                new MockMultipartFile("file", "bad.png", "image/png", "not an image".getBytes()),
                new MockMultipartFile("file", "bad.jpg", "image/jpeg", png.getBytes()),
                new MockMultipartFile("file", "big.png", "image/png", new byte[ShopImageStorage.MAX_BYTES + 1])
        };
        for (MockMultipartFile file : invalidFiles) {
            mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/me/image").file(file)
                    .header("X-Test-User", "OWNER_1")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_400"));
        }
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/me/image").header("X-Test-User", "OWNER_1"))
                .andExpect(status().isBadRequest());
        assertThat(imageUrl()).isEqualTo(url);
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(png.getBytes()));
    }

    @Test
    @DisplayName("비로그인은 401, 고객·관리자는 403이며 이미지가 유지된다")
    void mutate_unauthorized_keepsImage() throws Exception {
        String url = upload(ShopImageFixture.image("png"));
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/me/image").file(ShopImageFixture.image("png")))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/shops/me/image")).andExpect(status().isUnauthorized());
        for (String user : new String[] {"CUSTOMER_1", "ADMIN"}) {
            mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/me/image").file(ShopImageFixture.image("png"))
                    .header("X-Test-User", user)).andExpect(status().isForbidden());
            mvc.perform(delete("/api/v1/shops/me/image").header("X-Test-User", user))
                    .andExpect(status().isForbidden());
        }
        assertThat(imageUrl()).isEqualTo(url);
    }

    @Test
    @DisplayName("실제 HTTP multipart 파서의 5MB 초과 오류도 공통 400 응답이며 기존 이미지를 유지한다")
    void upload_httpMultipartLimit_returnsCommonBadRequest() throws Exception {
        String url = upload(ShopImageFixture.image("png"));
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(new byte[ShopImageStorage.MAX_BYTES + 1]) {
            @Override
            public String getFilename() {
                return "large.png";
            }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Test-User", "OWNER_1");
        var response = http.exchange("/api/v1/shops/me/image", HttpMethod.PUT,
                new HttpEntity<>(body, headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("COMMON_400");
        assertThat(imageUrl()).isEqualTo(url);
    }

    @Test
    @DisplayName("한 요청의 파일 중복과 boundary가 없는 손상 multipart 요청은 400이며 기존 이미지를 유지한다")
    void upload_multipleOrMalformedMultipart_returnsBadRequest() throws Exception {
        String url = upload(ShopImageFixture.image("png"));
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/me/image").file(ShopImageFixture.image("png"))
                .file(ShopImageFixture.image("jpg")).header("X-Test-User", "OWNER_1"))
                .andExpect(status().isBadRequest());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set("X-Test-User", "OWNER_1");
        var response = http.exchange("/api/v1/shops/me/image", HttpMethod.PUT,
                new HttpEntity<>("invalid multipart", headers), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().path("error").path("code").asText()).isEqualTo("COMMON_400");
        assertThat(imageUrl()).isEqualTo(url);
        assertThat(objects).hasSize(1);
    }

    private String upload(MockMultipartFile file) throws Exception {
        String response = mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/me/image").file(file)
                .header("X-Test-User", "OWNER_1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("imageUrl").asText();
    }

    private String imageUrl() {
        return jdbc.queryForObject("SELECT IMAGE_URL FROM SHOP WHERE SHOP_ID = 1", String.class);
    }

    private void rejectJpgInDatabase() {
        // 테스트 전용 H2 제약으로 실제 flush·commit 경로의 실패를 만든다. 운영 스키마는 변경하지 않는다.
        jdbc.execute("""
                ALTER TABLE SHOP ADD CONSTRAINT CK_TEST_SHOP_IMAGE
                CHECK (IMAGE_URL IS NULL OR IMAGE_URL NOT LIKE '%.jpg')
                """);
    }

    private void uploadJpgExpectDatabaseFailure() throws Exception {
        mvc.perform(multipart(HttpMethod.PUT, "/api/v1/shops/me/image").file(ShopImageFixture.image("jpg"))
                .header("X-Test-User", "OWNER_1")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("COMMON_500"));
    }

    private int cleanupCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM SHOP_IMAGE_CLEANUP", Integer.class);
    }

    private void restoreS3Delete() {
        doAnswer(invocation -> {
            DeleteObjectRequest request = invocation.getArgument(0);
            objects.remove(request.key());
            return DeleteObjectResponse.builder().build();
        }).when(s3).deleteObject(any(DeleteObjectRequest.class));
    }

    private void retryDueCleanups() {
        jdbc.update("UPDATE SHOP_IMAGE_CLEANUP SET NEXT_ATTEMPT_AT = ?", LocalDateTime.now(clock).minusMinutes(1));
        for (Long id : cleanupService.findDueIds()) {
            cleanupService.retry(id);
        }
    }
}
