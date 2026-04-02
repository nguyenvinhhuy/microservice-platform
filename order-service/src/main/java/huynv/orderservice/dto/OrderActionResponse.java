package huynv.orderservice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class OrderActionResponse {
    private UUID orderId;
    private String status;
    private String message;
}
