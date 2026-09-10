# SS6 - Bài Tập 2: Chuyển Đổi ProductServiceClientRT Sang FeignClient

Báo cáo so sánh chi tiết giữa lập trình gọi REST API theo kiểu Imperative (`RestTemplate`) và Declarative (`Spring Cloud OpenFeign`), phân tích ưu điểm của `FallbackFactory`, và hướng dẫn sử dụng mã nguồn trong dự án VietMart.

---

## 1. Bảng So Sánh Số Dòng Code & Sự Khác Biệt

| Tiêu Chí So Sánh | RestTemplate (`ProductServiceClientRT`) | OpenFeign (`ProductClient`) |
| :--- | :--- | :--- |
| **Phong cách lập trình** | Mệnh lệnh (Imperative / Procedural) | Khai báo (Declarative / Interface-driven) |
| **Số dòng code (Client)** | ~40 dòng code Java | ~15 dòng code (Interface) |
| **Tỷ lệ giảm thiểu code** | Cần viết class hoàn chỉnh + try-catch | **Giảm hơn 60%** lượng mã thừa (boilerplate) |
| **Tạo URL & Nối tham số** | Phải tự viết chuỗi URL `"http://.../{id}"` | Dùng annotation chuẩn Spring Web `@GetMapping` |
| **Cân bằng tải (Load Balancing)** | Cần tự tạo Bean có `@LoadBalanced` | Tự động tích hợp với Eureka qua tên `name = "service-name"` |
| **Xử lý sự cố (Fallback)** | Phải bọc khối `try-catch` thủ công cho từng hàm | Tích hợp chuẩn qua `fallbackFactory` riêng biệt |

---

## 2. Phân Tích Lập Luận: Khi Nào Nên Sử Dụng Mỗi Cách?

### 2.1. Khi nào nên dùng OpenFeign (Declarative)?
- **Giao tiếp nội bộ giữa các Microservices (Internal Microservice Communication)**: Trong cùng hệ sinh thái Spring Cloud, FeignClient là lựa chọn tối ưu nhất nhờ cú pháp interface ngắn gọn, đồng bộ phong cách code của cả team, và tích hợp sẵn Eureka Service Discovery + LoadBalancer.
- **Giảm thiểu Boilerplate Code**: Giúp lập trình viên tập trung vào nghiệp vụ (business logic) thay vì phải tự viết các đoạn code gọi HTTP, map DTO và quản lý kết nối thủ công.
- **Tích hợp Circuit Breaker & Fallback**: Khi cần cơ chế xử lý lỗi dự phòng (Fallback/Resilience) đồng bộ và dễ bảo trì trên toàn bộ hệ thống.

### 2.2. Khi nào nên dùng RestTemplate (Imperative)?
- **Tích hợp dịch vụ bên ngoài (Third-party External APIs)**: Khi gọi tới các hệ thống ngoài tổ chức (ví dụ: Cổng thanh toán VNPay/ZaloPay, API Thời tiết, API Bản đồ) không nằm trong Eureka Service Registry.
- **Cần tùy biến HTTP request cấp thấp (Low-level Control)**: Khi cần can thiệp sâu vào luồng HTTP (custom headers động, stream dữ liệu binary kích thước lớn, thao tác raw bytes socket, custom ssl context).
- **Dự án Spring Boot đơn lẻ (Non-Spring Cloud Apps)**: Khi ứng dụng không cần các tính năng định danh dịch vụ động của Spring Cloud.

---

## 3. Ưu Điểm Của `FallbackFactory` So Với Lớp `Fallback` Thông Thường

Khi cấu hình xừ lý lỗi dự phòng trong OpenFeign:
- **Dùng `fallback = ProductClientFallback.class` thông thường**: Lớp fallback chỉ trả về dữ liệu mặc định nhưng **KHÔNG BIẾT lý do tại sao gọi API thất bại** (không nhận được exception).
- **Dùng `fallbackFactory = ProductClientFallbackFactory.class`**: Phương thức `create(Throwable cause)` truyền vào đối tượng `Throwable cause`. Giúp đội kỹ thuật:
  1. Ghi log chi tiết chính xác nguyên nhân gây lỗi (`Timeout`, `500 Server Error`, `Connection Refused`).
  2. Quyết định chiến lược fallback linh hoạt theo từng loại ngoại lệ cụ thể trước khi trả dữ liệu dự phòng cho client.

---

## 4. Chi Tiết Khai Báo Mã Nguồn

### 4.1. Interface `ProductClient.java`
```java
package com.vietmart.order.client;

import com.vietmart.order.dto.ProductInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@FeignClient(name = "product-service", fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

    @GetMapping("/api/products/{id}")
    ProductInfo getById(@PathVariable("id") Long id);

    @GetMapping("/api/products")
    List<ProductInfo> getAll();
}
```

### 4.2. Interface `UserClient.java`
```java
package com.vietmart.order.client;

import com.vietmart.order.dto.UserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service")
public interface UserClient {

    @GetMapping("/api/users/{userId}")
    UserInfo getUserById(@PathVariable("userId") Long userId);
}
```

### 4.3. Lớp `ProductClientFallbackFactory.java`
```java
package com.vietmart.order.client;

import com.vietmart.order.dto.ProductInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        log.error("ProductClient fallback triggered. Reason: {}", cause.getMessage(), cause);

        return new ProductClient() {
            @Override
            public ProductInfo getById(Long id) {
                log.warn("Fallback triggered for getById({}) due to error: {}", id, cause.getMessage());
                return ProductInfo.builder()
                        .id(id)
                        .name("Unknown Product (Fallback)")
                        .price(null)
                        .status("UNAVAILABLE")
                        .build();
            }

            @Override
            public List<ProductInfo> getAll() {
                log.warn("Fallback triggered for getAll() due to error: {}", cause.getMessage());
                return Collections.emptyList();
            }
        };
    }
}
```

---

## 5. Hướng Dẫn Khởi Chạy & Kiểm Thử Unit Test

### Lệnh chạy Unit Test:
```bash
cd order-service
./gradlew test
```
Bộ test `ProductClientFallbackFactoryTest.java` sẽ kiểm tra tính chính xác của dữ liệu dự phòng khi dịch vụ gặp sự cố.
