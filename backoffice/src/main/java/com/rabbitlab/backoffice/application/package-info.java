/**
 * Use case'ler: "Backoffice ne yapabilir?" sorusunun cevabı.
 *
 * <ul>
 *   <li>{@code port.in}: dış dünyanın uygulamayı çağırdığı arayüzler (REST, test, CLI...).</li>
 *   <li>{@code port.out}: uygulamanın dış dünyadan istedikleri (kaydet, outbox'a yaz, transaction aç).</li>
 * </ul>
 * Bu paket de framework bilmez. Port'ların uygulamaları {@code adapter} paketinde.
 */
package com.rabbitlab.backoffice.application;
