package huynv.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductImageDTO {
    private Long id;

    @NotBlank(message = "Image URL cannot be blank")
    private String url;

    private boolean isPrimary;

    @Min(value = 0, message = "Sort order cannot be negative")
    private int sortOrder;
}
