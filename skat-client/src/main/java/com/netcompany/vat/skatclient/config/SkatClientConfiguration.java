package com.netcompany.vat.skatclient.config;

import com.netcompany.vat.skatclient.PeppolClient;
import com.netcompany.vat.skatclient.PeppolClientStub;
import com.netcompany.vat.skatclient.SkatClient;
import com.netcompany.vat.skatclient.SkatClientStub;
import com.netcompany.vat.skatclient.ViesClient;
import com.netcompany.vat.skatclient.ViesClientStub;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the skat-client module.
 *
 * <p>Phase 1: Registers stub beans for {@link SkatClient}, {@link ViesClient}, and
 * {@link PeppolClient}. No real API connections are made.
 *
 * <p>Phase 2 upgrade path:
 * <ol>
 *   <li>Replace {@link SkatClientStub} with {@code SkatClientImpl} (WebClient + real SKAT API)</li>
 *   <li>Replace {@link ViesClientStub} with {@code ViesClientImpl} (WebClient + EU VIES API)</li>
 *   <li>Replace {@link PeppolClientStub} with {@code PeppolClientImpl} (PEPPOL BIS 3.0)</li>
 * </ol>
 */
@Configuration
@EnableConfigurationProperties({SkatClientProperties.class, ViesClientProperties.class})
public class SkatClientConfiguration {

    @Bean
    public SkatClient skatClient(SkatClientProperties properties) {
        return new SkatClientStub(properties);
    }

    @Bean
    public ViesClient viesClient(ViesClientProperties properties) {
        return new ViesClientStub(properties);
    }

    @Bean
    public PeppolClient peppolClient() {
        return new PeppolClientStub();
    }
}
