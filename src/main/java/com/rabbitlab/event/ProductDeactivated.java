package com.rabbitlab.event;

/** Ürün satıştan kaldırıldı. Silme yok; ürün pasif olur. Ürün bilgisi versiyonunu taşır. */
public record ProductDeactivated(String sku, long version) implements ProductEvent {

    public ProductDeactivated {
        Require.notBlank(sku, "sku");
        Require.positive(version, "version");
    }
}
