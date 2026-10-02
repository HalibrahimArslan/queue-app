package com.rabbitlab.storefront.adapter.in.web;

import com.rabbitlab.storefront.application.port.in.BrowseCatalogUseCase;
import com.rabbitlab.storefront.domain.CatalogItem;
import com.rabbitlab.storefront.domain.Price;
import com.rabbitlab.storefront.domain.Sku;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** P2-M6 — Vitrini okuyan REST adapter'ı. */
@WebMvcTest(CatalogController.class)
class CatalogControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    BrowseCatalogUseCase catalog;

    private static CatalogItem kupa(int stock, boolean active) {
        return CatalogItem.restore(new Sku("SKU-1"), "Kupa", "Seramik kupa", new Price(new BigDecimal("100"), "TRY"),
                stock, active, 2, 1);
    }

    @Test
    void should_list_visible_items() {
        when(catalog.visibleItems()).thenReturn(List.of(kupa(0, true)));

        assertThat(mvc.get().uri("/catalog")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                [{"sku": "SKU-1", "name": "Kupa", "description": "Seramik kupa", "price": 100.00,
                  "currency": "TRY", "stock": 0, "outOfStock": true, "active": true}]""");
    }

    @Test
    void should_show_single_item_even_when_inactive() {
        when(catalog.find(new Sku("SKU-1"))).thenReturn(Optional.of(kupa(5, false)));

        assertThat(mvc.get().uri("/catalog/SKU-1")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                {"sku": "SKU-1", "stock": 5, "outOfStock": false, "active": false}""");
    }

    @Test
    void should_answer_not_found_for_unknown_item() {
        when(catalog.find(new Sku("SKU-9"))).thenReturn(Optional.empty());

        assertThat(mvc.get().uri("/catalog/SKU-9")).hasStatus(HttpStatus.NOT_FOUND);
    }
}
