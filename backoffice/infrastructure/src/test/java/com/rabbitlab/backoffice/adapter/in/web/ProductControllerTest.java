package com.rabbitlab.backoffice.adapter.in.web;

import com.rabbitlab.backoffice.application.port.in.CountStockCommand;
import com.rabbitlab.backoffice.application.port.in.CountStockUseCase;
import com.rabbitlab.backoffice.application.port.in.CreateProductCommand;
import com.rabbitlab.backoffice.application.port.in.CreateProductUseCase;
import com.rabbitlab.backoffice.application.port.in.DeactivateProductUseCase;
import com.rabbitlab.backoffice.application.port.in.UpdateProductCommand;
import com.rabbitlab.backoffice.application.port.in.UpdateProductUseCase;
import com.rabbitlab.backoffice.domain.Sku;
import com.rabbitlab.backoffice.domain.product.Price;
import com.rabbitlab.backoffice.domain.product.ProductInactiveException;
import com.rabbitlab.backoffice.domainservice.ConcurrentUpdateException;
import com.rabbitlab.backoffice.domainservice.DuplicateSkuException;
import com.rabbitlab.backoffice.domainservice.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * P2-M6 — REST inbound adapter. Sadece HTTP ↔ use case çevirisini test ediyoruz; use case'ler sahte.
 * Controller iş kuralı içermez: JSON'u komuta çevirir, domain hatasını HTTP koduna çevirir.
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    private static final Sku SKU = new Sku("SKU-1");

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    CreateProductUseCase createProduct;

    @MockitoBean
    UpdateProductUseCase updateProduct;

    @MockitoBean
    DeactivateProductUseCase deactivateProduct;

    @MockitoBean
    CountStockUseCase countStock;

    @Test
    void should_create_product() {
        assertThat(mvc.post().uri("/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku": "SKU-1", "name": "Kupa", "description": "Seramik kupa", "price": 100, "currency": "TRY"}"""))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "/products/SKU-1");

        verify(createProduct).create(new CreateProductCommand(SKU, "Kupa", "Seramik kupa",
                new Price(new BigDecimal("100"), "TRY")));
    }

    @Test
    void should_reject_invalid_product_without_calling_use_case() {
        assertThat(mvc.post().uri("/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku": "SKU-1", "name": "Kupa", "price": -5, "currency": "TRY"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("price");

        verifyNoInteractions(createProduct);
    }

    @Test
    void should_answer_conflict_for_duplicate_sku() {
        doThrow(new DuplicateSkuException(SKU)).when(createProduct).create(any());

        assertThat(mvc.post().uri("/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"sku": "SKU-1", "name": "Kupa", "price": 100, "currency": "TRY"}"""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail").asString().contains("SKU-1");
    }

    @Test
    void should_update_product() {
        assertThat(mvc.put().uri("/products/SKU-1").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Kupa", "description": null, "price": 120, "currency": "TRY"}"""))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(updateProduct).update(new UpdateProductCommand(SKU, "Kupa", null,
                new Price(new BigDecimal("120"), "TRY")));
    }

    @Test
    void should_answer_not_found_for_unknown_product() {
        doThrow(new ProductNotFoundException(SKU)).when(updateProduct).update(any());

        assertThat(mvc.put().uri("/products/SKU-1").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Kupa", "price": 120, "currency": "TRY"}"""))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void should_answer_conflict_when_product_is_inactive_or_changed_concurrently() {
        doThrow(new ProductInactiveException(SKU)).when(deactivateProduct).deactivate(SKU);
        assertThat(mvc.post().uri("/products/SKU-1/deactivate")).hasStatus(HttpStatus.CONFLICT);

        doThrow(new ConcurrentUpdateException("Ürün", "SKU-1", 1)).when(updateProduct).update(any());
        assertThat(mvc.put().uri("/products/SKU-1").contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "Kupa", "price": 120, "currency": "TRY"}"""))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void should_deactivate_product() {
        assertThat(mvc.post().uri("/products/SKU-1/deactivate")).hasStatus(HttpStatus.NO_CONTENT);

        verify(deactivateProduct).deactivate(SKU);
    }

    @Test
    void should_count_stock() {
        assertThat(mvc.put().uri("/products/SKU-1/stock").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\": 8}"))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(countStock).count(new CountStockCommand(SKU, 8));
    }
}
