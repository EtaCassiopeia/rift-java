package io.github.achirdlabs.rift.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds {@code user-client.*} so the property-injection gate proves real @ConfigurationProperties binding. */
@ConfigurationProperties(prefix = "user-client")
public class UserClientProps {

    private String baseUrl;
    private Integer port;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }
}
