package com.rabbitlab.backoffice.application.port.in;

/** Ürün yöneticisi yeni ürün oluşturur. Ürün aktif ve stoksuz doğar. */
public interface CreateProductUseCase {

    void create(CreateProductCommand command);
}
