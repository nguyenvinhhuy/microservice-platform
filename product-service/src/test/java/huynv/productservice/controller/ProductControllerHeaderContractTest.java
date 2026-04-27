package huynv.productservice.controller;

import huynv.productservice.dto.ProductDTO;
import huynv.productservice.model.IdempotencyKey;
import huynv.productservice.service.IdempotencyService;
import huynv.productservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerHeaderContractTest {

    @Mock
    private ProductService productService;

    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private ProductController productController;

    @Test
    void shouldPreferIdempotencyKeyHeaderForCreate() {
        UUID idempotencyKey = UUID.randomUUID();

        productController.createProduct(new ProductDTO(), idempotencyKey, UUID.randomUUID());

        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(idempotencyService).getKey(captor.capture());
        assertEquals(idempotencyKey, captor.getValue());
    }

    @Test
    void shouldReturnCachedStatusForDuplicateCreate() {
        UUID requestId = UUID.randomUUID();
        when(idempotencyService.getKey(requestId)).thenReturn(Optional.of(IdempotencyKey.builder()
                .idempotencyKey(requestId)
                .responseStatus(HttpStatus.CREATED.value())
                .responseBody("")
                .createdAt(OffsetDateTime.now())
                .build()));

        var response = productController.createProduct(new ProductDTO(), null, requestId);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(productService, never()).createProduct(any());
    }

    @Test
    void shouldAllowCreateWithoutAnyIdempotencyHeader() {
        productController.createProduct(new ProductDTO(), null, null);

        verify(productService).createProduct(any());
        verify(idempotencyService, never()).getKey(any());
    }
}

