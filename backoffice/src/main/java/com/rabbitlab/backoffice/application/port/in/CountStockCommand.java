package com.rabbitlab.backoffice.application.port.in;

import com.rabbitlab.backoffice.domain.Sku;

public record CountStockCommand(Sku sku, int quantity) {
}
