package com.netcompany.vat.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the VAT System REST API.
 *
 * <p>Component scan covers both the API package and the persistence package so that
 * all repository beans registered in {@code PersistenceConfiguration} are available
 * to the application context.
 */
@SpringBootApplication(scanBasePackages = {
        "com.netcompany.vat.api",
        "com.netcompany.vat.persistence"
})
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
