# SS6 - Bài Tập 4: Phân Tích Hợp Đồng API Và Tác Động Khi FeignClient Interface Thay Đổi

Tài liệu báo cáo phân tích rủi ro hợp đồng API (API Contract Coupling), rủi ro Breaking Changes khi thay đổi cấu trúc JSON response và URL path, 2 chiến lược API Versioning thực tế, và giải pháp Migration DTO tương thích ngược bằng `@JsonAlias`.

---

## 1. Phân Tích Triệu Chứng Khi `product-service` Đổi Tên Trường `"name"` -> `"productName"` (Không Có Migration Plan)

### Cơ chế hoạt động của Jackson Deserializer:
Khi `product-service` (v2) trả về JSON mới:
```json
{
  "id": 101,
  "productName": "Tai nghe Bluetooth Sony",
  "price": 2500000
}
```
Nhưng DTO cũ tại 3 client (`order-service`, `inventory-service`, `report-service`) vẫn là:
```java
public record ProductInfo(Long id, String name, Long price) {}
```
Do tên thuộc tính trong JSON (`"productName"`) không khớp với tên trường trong DTO (`"name"`), bộ giải mã Jackson **không ném ngoại lệ** mà âm thầm gán giá trị **`null`** cho trường `name`.

### Triệu chứng cụ thể tại 3 service:
1. **`order-service`**:
   - Tên sản phẩm hiển thị trên hóa đơn và đơn đặt hàng của khách hàng bị `null` (hoặc hiển thị `"Sản phẩm: null"`).
   - Đơn hàng được tạo thành công nhưng dữ liệu tên sản phẩm trong CSDL bị rác/rỗng.
2. **`inventory-service`**:
   - Thẻ kho và danh mục tồn kho không hiển thị được tên mặt hàng.
   - Khi chạy logic kiểm tra (ví dụ: `productInfo.name().trim().toUpperCase()`), ứng dụng lập tức ném ngoại lệ **`NullPointerException` (NPE)** ngắt đột ngột luồng xử lý kho.
3. **`report-service`**:
   - Báo cáo doanh thu theo sản phẩm bị sai lệch nhóm.
   - Báo cáo danh sách "Top 10 Sản Phẩm Bán Chạy" hiển thị toàn bộ tên sản phẩm là `null`, gây sai lệch số liệu kinh doanh nghiêm trọng.

---

## 2. Phân Tích Triệu Chứng Khi Đổi Path Từ `/api/products/{id}` Sang `/api/v2/products/{id}` (Không Backward Compatibility)

### Cơ chế gây lỗi:
- `product-service` đã gỡ bỏ endpoint cũ `/api/products/{id}`.
- Khi FeignClient tại 3 service gửi HTTP GET request đến `/api/products/{id}`, server `product-service` phản hồi ngay mã **HTTP `404 NOT FOUND`**.

### Triệu chứng cụ thể:
- OpenFeign client lập tức ném ngoại lệ:
  ```text
  feign.FeignException$NotFound: [404 Not Found] during [GET] to [http://product-service/api/products/101]
  ```
- Cả 3 service gọi API hoàn toàn thất bại. Nếu có cài đặt Fallback/Circuit Breaker, ứng dụng lập tức kích hoạt phương thức dự phòng. Nếu không có Fallback, toàn bộ tính năng liên quan ở cả 3 service bị sập hoàn toàn (**HTTP `500 Internal Server Error`** cho người dùng cuối).

---

## 3. Đề Xuất 2 Chiến Lược API Versioning & Tradeoffs

### Chiến Lược 1: URI Path Versioning (`/api/v1/...` vs `/api/v2/...`)
- **Cách triển khai**:
  - Duy trì endpoint v1: `@GetMapping("/api/v1/products/{id}")` trả về DTO v1 (có trường `"name"`).
  - Tạo endpoint v2: `@GetMapping("/api/v2/products/{id}")` trả về DTO v2 (có trường `"productName"`).
- **Tradeoffs (Đánh đổi)**:
  - *Ưu điểm*: Đơn giản, rõ ràng trên router, cực kỳ dễ cấu hình định tuyến tại API Gateway, hoàn toàn tương thích ngược (Backward Compatible). Các client cũ chưa nâng cấp vẫn chạy bình thường.
  - *Nhược điểm*: Gây nhân bản mã nguồn (code duplication) ở phía Provider nếu duy trì quá nhiều phiên bản cũ cùng lúc.

### Chiến Lược 2: Header-based Versioning (`Accept` Header hoặc Custom Header `X-API-Version`)
- **Cách triển khai**:
  - Giữ nguyên URL `/api/products/{id}`.
  - Phân biệt phiên bản qua Header:
    - Client v1 gửi: `Accept: application/vnd.vietmart.v1+json` -> Trả về JSON v1 (`"name"`).
    - Client v2 gửi: `Accept: application/vnd.vietmart.v2+json` (hoặc `X-API-Version: 2`) -> Trả về JSON v2 (`"productName"`).
- **Tradeoffs (Đánh đổi)**:
  - *Ưu điểm*: Giữ URL RESTful sạch sẽ (Clean REST URL Design), hỗ trợ linh hoạt Content Negotiation.
  - *Nhược điểm*: Phức tạp trong cấu hình Gateway Routing và HTTP Caching (Cache Keys phải bao gồm cả Header).

---

## 4. Thiết Kế Migration DTO Phía Client Tương Thích Ngược (`MigrationProductInfo.java`)

Sử dụng annotation `@JsonAlias` của Jackson để DTO phía client chấp nhận **CẢ format cũ (`"name"`) lẫn format mới (`"productName"`)** mà không bị `null`:

```java
package com.vietmart.order.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationProductInfo {

    private Long id;

    // Annotation @JsonAlias cho phép Jackson đọc CẢ "productName" lẫn "name" gán vào thuộc tính name
    @JsonAlias({"productName", "name"})
    private String name;

    private BigDecimal price;
}
```

---

## 5. Kết Quả Kiểm Chứng Unit Test

Tệp test: `src/test/java/com/vietmart/order/dto/MigrationProductInfoTest.java`

- **Test 1 (`test_deserialize_legacy_json_format`)**: Giải mã chuỗi JSON v1 (`{"id": 101, "name": "Tai nghe Bluetooth Sony"}`) -> Trường `name` nhận đúng giá trị `"Tai nghe Bluetooth Sony"` (Không bị null).
- **Test 2 (`test_deserialize_new_json_format`)**: Giải mã chuỗi JSON v2 (`{"id": 101, "productName": "Tai nghe Bluetooth Sony"}`) -> `@JsonAlias` tự động map `"productName"` vào biến `name` nhận đúng giá trị `"Tai nghe Bluetooth Sony"` (Không bị null).

### Lệnh chạy Unit Test:
```bash
cd order-service
./gradlew test
```
