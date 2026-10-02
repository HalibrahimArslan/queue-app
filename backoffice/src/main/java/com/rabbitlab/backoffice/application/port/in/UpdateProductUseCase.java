package com.rabbitlab.backoffice.application.port.in;

/** Ürün yöneticisi ad, açıklama veya fiyatı günceller. */
public interface UpdateProductUseCase {

    void update(UpdateProductCommand command);
}
