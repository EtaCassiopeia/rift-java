package io.github.achirdlabs.rift.spring;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Minimal Spring Boot configuration so {@code @SpringBootTest} has a context to bootstrap. */
@SpringBootConfiguration
@EnableConfigurationProperties(UserClientProps.class)
class RiftTestApp {
}
