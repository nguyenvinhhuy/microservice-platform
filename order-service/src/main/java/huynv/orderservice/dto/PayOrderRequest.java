package huynv.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PayOrderRequest {

    @NotBlank
    private String provider;
}
