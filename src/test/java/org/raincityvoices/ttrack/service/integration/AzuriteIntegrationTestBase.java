package org.raincityvoices.ttrack.service.integration;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.raincityvoices.ttrack.service.MediaUrlProvider;
import org.raincityvoices.ttrack.service.util.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.azure.ai.speech.transcription.TranscriptionClient;
import com.azure.core.credential.AzureNamedKeyCredential;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;

import org.mockito.Mockito;
import org.springframework.context.annotation.Import;

/**
 * Base class for hermetic integration tests.
 *
 * <p>These tests run the full Spring Boot application against an
 * <a href="https://github.com/Azure/Azurite">Azurite</a> container (the official
 * Azure Storage emulator) started by Testcontainers, so they need no Azure
 * account, no {@code az login}, and no pre-seeded cloud data. They run the same
 * way locally (Docker Desktop) and in CI (Docker on the runner).
 *
 * <p>Request authentication relies on the application's existing behavior of
 * auto-authenticating localhost callers as an admin, so no Clerk token is
 * needed. Beans that depend on services Azurite cannot provide are replaced by
 * {@link TestBeans} below:
 * <ul>
 *   <li>Table and Blob clients are rebuilt against Azurite using its well-known
 *       development account key (a shared-key credential rather than Azure AD).</li>
 *   <li>Key Vault ({@link SecretClient}), the Clerk SDK, and the Cognitive
 *       Services {@link TranscriptionClient} are stubbed so the context loads
 *       offline.</li>
 *   <li>{@link MediaUrlProvider} is stubbed because real download URLs require
 *       User Delegation SAS, which Azurite does not support. Replacing it with a
 *       deterministic stub is a temporary measure; see the project backlog for
 *       the plan to test real download URLs.</li>
 * </ul>
 */
@Testcontainers
@ActiveProfiles("test")
@Import(AzuriteIntegrationTestBase.TestBeans.class)
public abstract class AzuriteIntegrationTestBase {

    /** Azurite's fixed development account name. */
    static final String AZURITE_ACCOUNT = "devstoreaccount1";
    /** Azurite's fixed, publicly documented development account key. */
    static final String AZURITE_KEY =
        "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    static final int BLOB_PORT = 10000;
    static final int TABLE_PORT = 10002;

    @Container
    static final GenericContainer<?> AZURITE =
        new GenericContainer<>(DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:latest"))
            .withExposedPorts(BLOB_PORT, TABLE_PORT)
            .withCommand("azurite", "--blobHost", "0.0.0.0", "--tableHost", "0.0.0.0")
            .waitingFor(Wait.forListeningPorts(BLOB_PORT, TABLE_PORT)
                .withStartupTimeout(Duration.ofSeconds(60)));

    static String blobEndpoint() {
        return String.format("http://%s:%d/%s",
            AZURITE.getHost(), AZURITE.getMappedPort(BLOB_PORT), AZURITE_ACCOUNT);
    }

    static String tableEndpoint() {
        return String.format("http://%s:%d/%s",
            AZURITE.getHost(), AZURITE.getMappedPort(TABLE_PORT), AZURITE_ACCOUNT);
    }

    /**
     * Point the application's {@code azure.*} endpoint properties at the running
     * Azurite container. These are resolved after the container has started, so
     * they must be registered dynamically.
     */
    @DynamicPropertySource
    static void azureProperties(DynamicPropertyRegistry registry) {
        registry.add("azure.blobs.endpoint", AzuriteIntegrationTestBase::blobEndpoint);
        registry.add("azure.tables.endpoint", AzuriteIntegrationTestBase::tableEndpoint);
    }

    @Autowired
    protected TestRestTemplate rest;

    // ---------------------------------------------------------------------
    // JSON request helpers.
    //
    // These fetch the raw response body as a String and parse it with
    // JsonUtils, rather than letting TestRestTemplate deserialize the response
    // directly (e.g. getForObject(url, Song.class)).
    //
    // We hit a strange issue where TestRestTemplate's own deserialization of a
    // Song dropped the `id` field, even though the exact same response body
    // parsed correctly with every mapper we tried directly (JsonUtils, the
    // application's context ObjectMapper, and TestRestTemplate's own Jackson
    // converter). The following reproduces it against a running app:
    //
    //     String raw = rest.postForEntity("/songs", song, String.class).getBody();
    //     // raw contains e.g. {"id":"6bcb8532", ...}
    //     Song viaString = JsonUtils.fromJson(raw, Song.class);      // id = "6bcb8532"
    //     Song viaEntity = rest.postForEntity("/songs", song, Song.class).getBody();
    //     // viaEntity.getId() is EMPTY, despite the identical body
    //
    // The serialized JSON is correct and parses correctly everywhere we care
    // about, so we are knowingly leaving this as-is and routing all response
    // parsing through JsonUtils via the helpers below.
    // ---------------------------------------------------------------------

    /** GET the given path and parse the JSON response body as the given type. */
    protected <T> T getJson(String path, Class<T> type, Object... uriVars) {
        return parse(rest.getForEntity(path, String.class, uriVars).getBody(), type);
    }

    /** GET the given path and parse the JSON response body as a list of the given element type. */
    protected <T> List<T> getJsonList(String path, Class<T> elementType, Object... uriVars) {
        return parseList(rest.getForEntity(path, String.class, uriVars).getBody(), elementType);
    }

    /** POST the given body to the given path and parse the JSON response body as the given type. */
    protected <T> T postJson(String path, Object body, Class<T> type, Object... uriVars) {
        return parse(rest.postForEntity(path, body, String.class, uriVars).getBody(), type);
    }

    /** POST the given body to the given path and return the response entity with the raw body. */
    protected ResponseEntity<String> postRaw(String path, Object body, Object... uriVars) {
        return rest.postForEntity(path, body, String.class, uriVars);
    }

    private static <T> T parse(String json, Class<T> type) {
        try {
            return JsonUtils.fromJson(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse response as " + type.getSimpleName() + ": " + json, e);
        }
    }

    private static <T> List<T> parseList(String json, Class<T> elementType) {
        JavaType listType = JsonUtils.MAPPER.getTypeFactory().constructCollectionType(List.class, elementType);
        try {
            return JsonUtils.MAPPER.readValue(json, listType);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse response as List<" + elementType.getSimpleName() + ">: " + json, e);
        }
    }

    /**
     * Supplies the Azure-backed beans for tests. The production {@code AzureClients}
     * configuration is excluded under the {@code test} profile, so these beans are
     * the sole providers; each method name matches the bean name the application
     * injects by name (e.g. {@code mediaContainerClient}), so wiring resolves
     * exactly as it does in production.
     */
    @TestConfiguration
    static class TestBeans {

        /** Credential type expected by the Blob SDK builders. */
        private static StorageSharedKeyCredential blobKey() {
            return new StorageSharedKeyCredential(AZURITE_ACCOUNT, AZURITE_KEY);
        }

        /** Credential type expected by the Tables SDK builder. */
        private static AzureNamedKeyCredential tableKey() {
            return new AzureNamedKeyCredential(AZURITE_ACCOUNT, AZURITE_KEY);
        }

        private static TableClient tableClient(String tableName) {
            TableClient client = new TableClientBuilder()
                .credential(tableKey())
                .endpoint(tableEndpoint())
                .tableName(tableName)
                .buildClient();
            client.createTable();
            return client;
        }

        private static BlobContainerClient containerClient(String containerName) {
            BlobContainerClient client = new BlobContainerClientBuilder()
                .credential(blobKey())
                .endpoint(blobEndpoint())
                .containerName(containerName)
                .buildClient();
            client.createIfNotExists();
            return client;
        }

        @Bean
        TableClient songsTableClient() {
            return tableClient("Songs");
        }

        @Bean
        TableClient asyncTasksTableClient() {
            return tableClient("AsyncTasks");
        }

        @Bean
        BlobContainerClient mediaContainerClient() {
            return containerClient("song-media");
        }

        @Bean
        BlobContainerClient dataContainerClient() {
            return containerClient("song-timed-data");
        }

        @Bean
        BlobContainerClient tempFileContainerClient() {
            return containerClient("temp-files");
        }

        /**
         * Only used for User Delegation SAS, which Azurite does not support. No
         * test exercises it, so an unconfigured client is sufficient; the stub
         * {@link MediaUrlProvider} below keeps it off the request paths.
         */
        @Bean
        BlobServiceClient blobServiceClient() {
            return new BlobServiceClientBuilder()
                .credential(blobKey())
                .endpoint(blobEndpoint())
                .buildClient();
        }

        /**
         * Key Vault is unavailable in tests. {@code SecurityConfig} reads the
         * Clerk API key from this client at startup, so the mock returns a dummy
         * secret; the key is never used because tests authenticate via the
         * localhost path rather than through Clerk.
         */
        @Bean
        SecretClient secretClient() {
            SecretClient mock = Mockito.mock(SecretClient.class);
            Mockito.when(mock.getSecret(Mockito.anyString()))
                .thenReturn(new KeyVaultSecret("ClerkAPIKeyTest", "test-clerk-api-key"));
            return mock;
        }

        /** Cognitive Services is unavailable in tests and unused by these tests. */
        @Bean
        TranscriptionClient trancriptionClient() {
            return Mockito.mock(TranscriptionClient.class);
        }

        /**
         * Deterministic stand-in for real (SAS-signed) media URLs, which require
         * Azure AD User Delegation keys unsupported by Azurite.
         */
        @Bean
        MediaUrlProvider mediaUrlProvider() {
            return (songId, fileId) -> URI.create(
                String.format("https://stub.invalid/media/%s/%s", songId, fileId));
        }
    }
}
