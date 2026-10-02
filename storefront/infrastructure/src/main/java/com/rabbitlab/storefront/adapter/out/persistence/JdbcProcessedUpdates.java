package com.rabbitlab.storefront.adapter.out.persistence;

import com.rabbitlab.storefront.application.ProcessedUpdates;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@link ProcessedUpdates}'in PostgreSQL uygulaması (tablo: processed_message). */
@Component
class JdbcProcessedUpdates implements ProcessedUpdates {

    private final JdbcClient jdbc;

    JdbcProcessedUpdates(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Aynı kimlik eşzamanlı gelirse ikinci insert, birincinin transaction'ı bitene kadar bekler, sonra hiçbir şey yapmaz. */
    @Override
    public boolean markProcessed(String updateId) {
        return jdbc.sql("insert into processed_message (message_id) values (?) on conflict do nothing")
                .param(updateId)
                .update() == 1;
    }
}
