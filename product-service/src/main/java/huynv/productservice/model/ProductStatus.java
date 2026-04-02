package huynv.productservice.model;

public enum ProductStatus {
    DRAFT,        // mới tạo, chưa public
    ACTIVE,       // đang bán
    OUT_OF_STOCK, // hết hàng
    INACTIVE,     // tạm ngưng
    DELETED       // soft delete
}
