package com.sulf.dyndb.config;

import com.sulf.dyndb.persistence.domain.IdempotencyItem;
import com.sulf.dyndb.persistence.domain.PaymentEventItem;
import com.sulf.dyndb.persistence.domain.PaymentItem;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
@EnableConfigurationProperties(DynamoDbProperties.class)
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(DynamoDbProperties properties) {
        return DynamoDbClient.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        properties.accessKey(),
                        properties.secretAccessKey()
                )))
                .build();

    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(
            DynamoDbClient dynamoDbClient
    ) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbTable<PaymentItem> paymentTable(
            DynamoDbEnhancedClient enhancedClient,
            DynamoDbProperties properties
    ) {
        return enhancedClient.table(
                properties.tableName(),
                TableSchema.fromBean(PaymentItem.class));

    }

    @Bean
    public DynamoDbTable<PaymentEventItem> paymentEventTable(
            DynamoDbEnhancedClient enhancedClient,
            DynamoDbProperties properties
    ) {
        return enhancedClient.table(properties.tableName(),
                TableSchema.fromBean(PaymentEventItem.class));
    }

    @Bean
    public DynamoDbTable<IdempotencyItem> idempotencyTable(
            DynamoDbEnhancedClient enhancedClient,
            DynamoDbProperties properties
    ) {
        return enhancedClient.table(
                properties.tableName(),
                TableSchema.fromBean(IdempotencyItem.class)
        );
    }

}
