package org.raincityvoices.ttrack.service.config;

import java.io.File;
import java.io.IOException;
import java.time.Clock;

import org.apache.commons.io.FileUtils;
import org.raincityvoices.ttrack.service.storage.DiskCachingMediaStorage;
import org.raincityvoices.ttrack.service.storage.DiskCachingMediaStorage.RemoteStorage;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.raincityvoices.ttrack.service.util.DefaultFileManager;
import org.raincityvoices.ttrack.service.util.FileManager;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.azure.security.keyvault.secrets.SecretClient;

import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Configuration
@ConfigurationProperties("service")
@RequiredArgsConstructor
@Slf4j
public class ServiceConfig {

    private final SecretClient secretClient;

    @Data
    public static class CacheConfig {
         private File directory;
    }

    @Getter @Setter
    private CacheConfig cache;

    @Bean
    public MediaStorage mediaStorage(RemoteStorage remoteStorage) throws IOException {
        FileUtils.forceMkdir(cache.directory);
        return new DiskCachingMediaStorage(remoteStorage, cache.directory);
    }

    @Bean
    public FileManager fileManager() {
        return new DefaultFileManager();
    }

    @Bean
    public Clock clock() { return Clock.systemUTC(); }
}
