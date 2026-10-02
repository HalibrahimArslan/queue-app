package com.rabbitlab.storefront.config;

import com.rabbitlab.storefront.application.CatalogService;
import com.rabbitlab.storefront.application.port.out.CatalogRepository;
import com.rabbitlab.storefront.application.port.out.Transaction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Application servisleri framework'süz; burada bean yapılıyor. */
@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {

    @Bean
    CatalogService catalogService(CatalogRepository repository, Transaction transaction) {
        return new CatalogService(repository, transaction);
    }
}
