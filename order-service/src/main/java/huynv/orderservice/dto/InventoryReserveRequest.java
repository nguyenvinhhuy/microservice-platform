package huynv.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReserveRequest {
    private UUID orderId;
    private Long tenantId;
    private List<InventoryReserveItem> items;
    private BigDecimal amount;
    private String currency;
    private String paymentProvider;
    private String idempotencyKey;
    private String correlationId;
    private String traceId;
}
