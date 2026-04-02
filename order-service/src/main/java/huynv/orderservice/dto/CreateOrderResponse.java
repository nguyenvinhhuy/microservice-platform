package huynv.orderservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CreateOrderResponse {
    private UUID orderId;
    private String status;
}
