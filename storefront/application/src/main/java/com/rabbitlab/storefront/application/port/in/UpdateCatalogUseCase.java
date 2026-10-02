package com.rabbitlab.storefront.application.port.in;

/**
 * Backoffice'ten gelen bir değişikliği kataloğa uygular. Idempotent: aynı değişiklik iki kez
 * uygulanırsa sonuç değişmez.
 *
 * @throws com.rabbitlab.storefront.domainservice.UnknownProductException ürün katalogda yoksa (tekrar denenebilir)
 * @throws com.rabbitlab.storefront.domain.InvalidValueException değişiklik kurallara aykırıysa (tekrar denemek işe yaramaz)
 */
public interface UpdateCatalogUseCase {

    void apply(CatalogUpdate update);
}
