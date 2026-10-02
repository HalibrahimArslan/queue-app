package com.rabbitlab.backoffice.application.port.in;

/** Depo görevlisi saydığı stoğu girer. */
public interface CountStockUseCase {

    void count(CountStockCommand command);
}
