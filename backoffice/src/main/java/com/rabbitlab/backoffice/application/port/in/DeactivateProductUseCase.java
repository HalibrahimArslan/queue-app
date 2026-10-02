package com.rabbitlab.backoffice.application.port.in;

import com.rabbitlab.backoffice.domain.Sku;

/** Ürün yöneticisi ürünü satıştan kaldırır. Silme yok. */
public interface DeactivateProductUseCase {

    void deactivate(Sku sku);
}
