package com.rabbitlab.backoffice.domain.inventory;

import com.rabbitlab.backoffice.domain.AggregateRoot;
import com.rabbitlab.backoffice.domain.Require;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.event.StockUpdated;

/**
 * Stok aggregate'i: bir SKU'nun depodaki miktarı.
 *
 * <p>Depo görevlisi stoğu SAYAR ve sonucu girer ({@link #count}); "2 azalt" gibi fark komutu yok.
 * Mutlak değer + versiyon, Storefront'ta tekrar gelen ve geç gelen mesajları zararsız kılar.
 */
public final class Inventory extends AggregateRoot {

    private final Sku sku;
    private int quantity;
    private long version;

    private Inventory(Sku sku, int quantity, long version) {
        this.sku = Require.notNull(sku, "sku");
        this.quantity = quantity;
        this.version = version;
    }

    /** Yeni ürün için boş stok. Event yok: Storefront ürünü zaten "Tükendi" olarak oluşturur. */
    public static Inventory open(Sku sku) {
        return new Inventory(sku, 0, 0);
    }

    public void count(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Stok negatif olamaz: " + quantity);
        }
        if (this.quantity == quantity) {
            return;
        }
        this.quantity = quantity;
        this.version++;
        record(new StockUpdated(sku, quantity, version));
    }

    public Sku sku() {
        return sku;
    }

    public int quantity() {
        return quantity;
    }

    public long version() {
        return version;
    }
}
