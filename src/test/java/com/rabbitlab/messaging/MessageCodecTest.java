package com.rabbitlab.messaging;

import com.rabbitlab.event.ProductCreated;
import com.rabbitlab.event.ProductEvent;
import com.rabbitlab.event.StockUpdated;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mesaj gövdesi JSON, mesaj tipi ise AMQP'nin "type" özelliğinde taşınır.
 * Kavram: kuyrukta dolaşan şey bir Java nesnesi değil, byte dizisidir. Üretici ile tüketici
 * arasındaki tek ortak nokta bu byte dizisinin formatıdır (mesaj sözleşmesi / contract).
 */
class MessageCodecTest {

    private final MessageCodec codec = new MessageCodec();

    @Test
    void should_restore_same_event_when_encoded_and_decoded() {
        ProductCreated event = new ProductCreated("SKU-1", "Kupa", "Seramik kupa", new BigDecimal("100.50"), "TRY", 1);

        ProductEvent decoded = codec.decode(codec.typeOf(event), codec.encode(event));

        assertThat(decoded).isEqualTo(event);
    }

    @Test
    void should_use_simple_class_name_as_message_type() {
        assertThat(codec.typeOf(new StockUpdated("SKU-1", 3, 1))).isEqualTo("StockUpdated");
    }

    @Test
    void should_reject_message_when_type_is_unknown() {
        assertThatThrownBy(() -> codec.decode("OrderPlaced", "{}".getBytes(UTF_8)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessageContaining("OrderPlaced");
    }

    @Test
    void should_reject_message_when_body_is_not_json() {
        assertThatThrownBy(() -> codec.decode("StockUpdated", "bozuk".getBytes(UTF_8)))
                .isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void should_reject_message_when_stock_is_negative() {
        byte[] body = "{\"sku\":\"SKU-1\",\"quantity\":-5,\"version\":1}".getBytes(UTF_8);

        assertThatThrownBy(() -> codec.decode("StockUpdated", body))
                .isInstanceOf(InvalidMessageException.class);
    }
}
