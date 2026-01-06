package org.raincityvoices.ttrack.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties("azure")
public class AzureClients {

    @Data
    public static class AzureServiceConfig {
        private String endpoint;
    }

    @Getter @Setter
    private AzureServiceConfig tables;
    @Getter @Setter
    private AzureServiceConfig blobs;

    @Bean
    DefaultAzureCredential defaultCredential() {
        return new DefaultAzureCredentialBuilder().build();
    }

    @Bean
    public TableClient songsTableClient() {
        return new TableClientBuilder()
            .credential(defaultCredential())
            .endpoint(tables.endpoint)
            .tableName("Songs")
            .buildClient();
    }

    @Bean
    public TableClient asyncTasksTableClient() {
        return new TableClientBuilder()
            .credential(defaultCredential())
            .endpoint(tables.endpoint)
            .tableName("AsyncTasks")
            .buildClient();
    }

    @Bean
    public BlobContainerClient mediaContainerClient() {
        return new BlobContainerClientBuilder()
            .credential(defaultCredential())
            .endpoint(blobs.endpoint)
            .containerName("song-media")
            .buildClient();
    }

    @Bean
    public BlobContainerClient dataContainerClient() {
        return new BlobContainerClientBuilder()
            .credential(defaultCredential())
            .endpoint(blobs.endpoint)
            .containerName("song-timed-data")
            .buildClient();
    }

    @Bean
    public BlobContainerClient tempFileContainerClient() {
        return new BlobContainerClientBuilder()
            .credential(defaultCredential())
            .endpoint(blobs.endpoint)
            .containerName("temp-files")
            .buildClient();
    }

    @Bean
    public BlobServiceClient blobServiceClient() {
        return new BlobServiceClientBuilder()
            .credential(defaultCredential())
            .endpoint(blobs.endpoint)
            .buildClient();
    }

    @Bean
    public SecretClient secretClient() {
        return new SecretClientBuilder()
            .credential(defaultCredential())
            .vaultUrl("https://ttracksecrets.vault.azure.net/")
            .buildClient();
    }
}
