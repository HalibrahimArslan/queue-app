package com.rabbitlab.storefront;

/**
 * Katalogda olmayan bir ürün için mesaj geldi. Genelde geçicidir: ürünün kendisi
 * ({@code ProductCreated}) henüz gelmemiş olabilir. Bu yüzden tekrar denemeye değer.
 */
public class UnknownProductException extends RuntimeException {

    public UnknownProductException(String sku) {
        super("Katalogda bilinmeyen ürün: " + sku);
    }
}
