package com.rabbitlab.storefront.adapter.in.messaging;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Inbox: işlenmiş mesaj kimlikleri. Çağıranın transaction'ında çalışmalı.
 *
 * <p>Bizim senaryomuzda versiyonlar zaten tekrar gelen mesajı zararsız kılıyor; inbox ikinci bir
 * kemer: tekrar gelen mesaj için iş hiç yapılmaz. Doğal versiyonu olmayan mesajlarda (ör. "e-posta
 * gönder") tek güvence budur.
 */
@Component
class ProcessedMessages {

    private final JdbcClient jdbc;

    ProcessedMessages(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return ilk kez görüldüyse {@code true}. Aynı kimlik eşzamanlı gelirse ikinci insert, birincinin
     * transaction'ı bitene kadar bekler ve sonra hiçbir şey yapmaz.
     */
    boolean markProcessed(String messageId) {
        return jdbc.sql("insert into processed_message (message_id) values (?) on conflict do nothing")
                .param(messageId)
                .update() == 1;
    }
}
