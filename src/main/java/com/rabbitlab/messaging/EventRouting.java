package com.rabbitlab.messaging;

import com.rabbitlab.event.ProductCreated;
import com.rabbitlab.event.ProductDeactivated;
import com.rabbitlab.event.ProductEvent;
import com.rabbitlab.event.ProductUpdated;
import com.rabbitlab.event.StockUpdated;

/**
 * Her event tipinin routing key'i. Format: {@code <konu>.<olay>}.
 * Sealed interface sayesinde yeni bir event eklenip burası unutulursa kod derlenmez.
 */
public final class EventRouting {

    private EventRouting() {
    }

    public static String routingKeyOf(ProductEvent event) {
        return switch (event) {
            case ProductCreated ignored -> "product.created";
            case ProductUpdated ignored -> "product.updated";
            case ProductDeactivated ignored -> "product.deactivated";
            case StockUpdated ignored -> "stock.updated";
        };
    }
}
