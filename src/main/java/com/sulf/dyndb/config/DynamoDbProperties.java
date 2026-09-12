package com.sulf.dyndb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;

@ConfigurationProperties(prefix = "aws.dynamodb")
public record DynamoDbProperties(
        URI endpoint,
        String region,
        String accessKey,
        String secretAccessKey,
        String tableName
) {
}
