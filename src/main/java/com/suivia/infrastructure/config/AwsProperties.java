package com.suivia.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "suivia.aws")
public class AwsProperties {
    private String s3Bucket;
    private String sqsQueue;
}
