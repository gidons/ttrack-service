package org.raincityvoices.ttrack.service.config;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.raincityvoices.ttrack.service.storage.DiskCachingMediaStorage;
import org.raincityvoices.ttrack.service.storage.DiskCachingMediaStorage.RemoteStorage;
import org.raincityvoices.ttrack.service.storage.MediaStorage;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties("service")
public class ServiceConfig {

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
}
