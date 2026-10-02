package com.rabbitlab.backoffice.application.port.out;

import java.util.function.Supplier;

/**
 * Transaction sınırı bir port. Application katmanı "bu iş tek parça olsun" der; nasıl olacağını
 * (Spring, JDBC...) bilmez. Alternatif {@code @Transactional} olurdu ama o, Spring'i bu katmana sokar.
 */
public interface Transaction {

    <T> T execute(Supplier<T> work);

    default void execute(Runnable work) {
        execute(() -> {
            work.run();
            return null;
        });
    }
}
