package com.sulf.dyndb.config;

import com.sulf.dyndb.persistence.domain.PaymentItem;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Component
public class DynamoDbTableInitializer implements CommandLineRunner {

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbTable<PaymentItem> paymentTables;

    public DynamoDbTableInitializer(DynamoDbClient dynamoDbClient, DynamoDbTable<PaymentItem> paymentTables) {
        this.dynamoDbClient = dynamoDbClient;
        this.paymentTables = paymentTables;
    }

    @Override
    public void run(String... args) throws Exception {
        boolean exists = dynamoDbClient.listTables()
                .tableNames()
                .contains(paymentTables.tableName());

        if (!exists) {
            paymentTables.createTable();
            System.out.println("Created table: " + paymentTables.tableName());
        }
    }
}
