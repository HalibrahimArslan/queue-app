/**
 * Backoffice → Storefront mesaj sözleşmesi.
 *
 * <p>Faz 1'de aynı record hem domain event'i hem JSON formatıydı. Burada sözleşme ayrı: iki
 * context de bu modüle bağımlıdır ama birbirine bağımlı değildir. Domain nesneleri değişse bile
 * kablodaki format ancak bilinçli bir kararla değişir.
 */
package com.rabbitlab.contract;
