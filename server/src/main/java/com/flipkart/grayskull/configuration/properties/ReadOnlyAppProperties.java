package com.flipkart.grayskull.configuration.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Getter
@Setter
@ConfigurationProperties(prefix = "server.read-only")
@RefreshScope
public class ReadOnlyAppProperties {
    private boolean enabled;
}
