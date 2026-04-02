package huynv.productservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductAttributeDTO {
    private Long id;

    @NotBlank(message = "Attribute name cannot be blank")
    @Size(max = 100, message = "Attribute name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Attribute value cannot be blank")
    @Size(max = 255, message = "Attribute value cannot exceed 255 characters")
    private String value;
}
