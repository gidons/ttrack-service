package org.raincityvoices.ttrack.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableClientBuilder;
import com.azure.identity.AzureCliCredential;
import com.azure.identity.AzureCliCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;

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
    AzureCliCredential cliCredential() {
        AzureCliCredential cred = new AzureCliCredentialBuilder().build();
        return cred;
    }

    @Bean
    public TableClient songsTableClient() {
        return new TableClientBuilder()
            .credential(cliCredential())
            .endpoint(tables.endpoint)
            .tableName("Songs")
            .buildClient();
    }

    @Bean
    public TableClient asyncTasksTableClient() {
        return new TableClientBuilder()
            .credential(cliCredential())
            .endpoint(tables.endpoint)
            .tableName("AsyncTasks")
            .buildClient();
    }

    @Bean
    public BlobContainerClient mediaContainerClient() {
        return new BlobContainerClientBuilder()
            .credential(cliCredential())
            .endpoint(blobs.endpoint)
            .containerName("song-media")
            .buildClient();
    }
}
