package com.tasks;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TasksApplication {

    public static void main(String[] args) {
        SpringApplication.run(TasksApplication.class, args);
    }

    /**
     * Connects to the 'tasks' blob container.
     * DefaultAzureCredential = your "az login" on the laptop, the managed identity on App Service.
     * Needs role: Storage Blob Data Contributor on the storage account.
     */
    @Bean
    public BlobContainerClient tasksContainer(@Value("${storage.account-name}") String accountName,
                                              @Value("${storage.container}") String container) {
        return new BlobServiceClientBuilder()
                .endpoint("https://" + accountName + ".blob.core.windows.net")
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient()
                .getBlobContainerClient(container);
    }
}
