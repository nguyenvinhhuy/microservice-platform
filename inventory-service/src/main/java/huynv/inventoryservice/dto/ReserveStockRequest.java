package huynv.inventoryservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
public class ReserveStockRequest {
    private UUID orderId;
    private List<ReservationItem> items;
    private BigDecimal amount;
    private String currency;
    private String paymentProvider;
    private String idempotencyKey;
    private String correlationId;
    private String traceId;
}
