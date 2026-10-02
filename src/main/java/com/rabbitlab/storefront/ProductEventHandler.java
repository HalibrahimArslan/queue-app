package com.rabbitlab.storefront;

import com.rabbitlab.event.ProductEvent;

/**
 * Consumer'ın mesajı çözdükten sonra çağırdığı iş mantığı. Consumer'ı Catalog'a sıkı sıkıya
 * bağlamak yerine bu küçük arayüze bağlıyoruz; testte hata fırlatan bir handler verebiliyoruz.
 * (Faz 2'de bunun adı "inbound port" olacak.)
 */
@FunctionalInterface
public interface ProductEventHandler {

    void handle(ProductEvent event);
}
