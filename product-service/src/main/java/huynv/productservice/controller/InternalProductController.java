package huynv.productservice.controller;

import huynv.productservice.dto.ProductDTO;
import huynv.productservice.service.ProductService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes internal product read endpoints for synchronous order orchestration.
 */
@RestController
@RequestMapping(path = "/internal/products", produces = MediaType.APPLICATION_JSON_VALUE)
public class InternalProductController {

    private final ProductService productService;

    /**
     * Creates an internal product controller used by trusted service-to-service calls.
     *
     * @param productService Product service used to load tenant-scoped product data.
     * @return initializes an internal product controller.
     */
    public InternalProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Returns one product by identifier for internal validation calls.
     *
     * @param id Product identifier to load.
     * @return Returns the product snapshot for order validation.
     */
    @GetMapping("/{id}")
    public ProductDTO getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }
}

