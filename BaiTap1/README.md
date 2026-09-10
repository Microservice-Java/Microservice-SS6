# SS6 - Bài Tập 1: Sửa Lỗi Hardcode URL Trong RestTemplate Của order-service

Tài liệu báo cáo phân tích chi tiết 3 lỗi nghiêm trọng trong đoạn code `ProductServiceClientRT` ban đầu, giải pháp khắc phục chuẩn Spring Cloud, và hướng dẫn chạy bộ Unit Test bằng JUnit 5 & Mockito.

---

## 1. Báo Cáo Phân Tích 3 Lỗi Nghiêm Trọng & Hậu Quả Production

### Lỗi 1: Tự khởi tạo `new RestTemplate()` thay vì dùng Bean `@LoadBalanced`
- **Phân tích kỹ thuật**: Khi tự khởi tạo bằng `new RestTemplate()`, đối tượng này không do Spring IoC Container quản lý và không được gắn `LoadBalancerInterceptor` của Spring Cloud LoadBalancer.
- **Hậu quả Production**: Phá vỡ hoàn toàn cơ chế Service Discovery và Cân bằng tải qua Eureka. Nếu thay IP thành service-id (`http://product-service`), `RestTemplate` thuần sẽ cố gắng phân giải tên `product-service` bằng DNS của hệ điều hành thay vì tra cứu danh sách IP từ Eureka Server, dẫn đến lỗi `java.net.UnknownHostException: product-service`.

### Lỗi 2: Hardcode địa chỉ IP và Port vật lý (`http://192.168.1.45:8082/...`)
- **Phân tích kỹ thuật**: Gắn cứng địa chỉ IP máy dev (`192.168.1.45:8082`) trực tiếp trong mã nguồn Java.
- **Hậu quả Production**: Trong môi trường Microservices/Cloud (Docker, Kubernetes, AWS), địa chỉ IP của container thay đổi liên tục mỗi khi được khởi tạo lại hoặc tự động mở rộng (Auto-scaling). Việc hardcode làm ứng dụng ngắt kết nối ngay khi deploy lên môi trường Staging/Production (`Connection Refused`). Nếu `product-service` có 10 instances, 100% lượng tải vẫn bị dồn vào 1 IP duy nhất, làm phá vỡ khả năng mở rộng (Scalability).

### Lỗi 3: Không cấu hình Timeout (`connectTimeout` & `readTimeout`)
- **Phân tích kỹ thuật**: RestTemplate mặc định có thời gian chờ kết nối và đọc dữ liệu là **vô hạn (`infinite / -1`)**.
- **Hậu quả Production**: Nếu `product-service` gặp sự cố (treo database, nghẽn mạng, treo thread), các request gửi từ `order-service` sẽ bị ngưng trệ và giữ kết nối vĩnh viễn. Điều này dẫn đến việc cạn kệt Worker Thread Pool của `order-service` (Thread Starvation), khiến `order-service` ngừng phản hồi tất cả người dùng và gây ra sự cố sập nghẽn dây chuyền toàn bộ hệ thống (**Cascading Failure**).

---

## 2. Giải Pháp Sửa Đổi Chuẩn Spring Cloud

### 2.1. Cấu Hình Bean `RestTemplate` Với `@LoadBalanced` & Timeout (`RestTemplateConfig.java`)

```java
package com.vietmart.order.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2)) // Connect Timeout: 2s
                .setReadTimeout(Duration.ofSeconds(3))    // Read Timeout: 3s
                .build();
    }
}
```

### 2.2. Lớp Client Tương Tác Chuẩn & Xử Lý Ngoại Lệ Phân Biệt (`ProductServiceClientRT.java`)

- **Định tuyến qua Service ID**: `"http://product-service/api/products/{id}"`
- **Xử lý phân biệt ngoại lệ**:
  - `HttpClientErrorException.NotFound` (404): Log warning và trả về `null`.
  - `ResourceAccessException` (Timeout/Network failure): Log error và trả về **Fallback DTO** với `status = "UNAVAILABLE"`.

```java
package com.vietmart.order.client;

import com.vietmart.order.dto.ProductInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductServiceClientRT {

    private final RestTemplate restTemplate;
    private static final String PRODUCT_SERVICE_URL = "http://product-service/api/products/{id}";

    public ProductInfo getById(Long productId) {
        try {
            log.info("Sending request to product-service for productId: {}", productId);
            return restTemplate.getForObject(PRODUCT_SERVICE_URL, ProductInfo.class, productId);

        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Product not found with id: {}. Response: {}", productId, e.getMessage());
            return null;

        } catch (ResourceAccessException e) {
            log.error("Timeout or connection failure calling product-service for productId: {}. Error: {}", productId, e.getMessage());
            return buildFallbackProductInfo(productId, "UNAVAILABLE");

        } catch (Exception e) {
            log.error("Unexpected error calling product-service for productId: {}. Error: {}", productId, e.getMessage());
            return buildFallbackProductInfo(productId, "ERROR");
        }
    }

    private ProductInfo buildFallbackProductInfo(Long productId, String status) {
        return ProductInfo.builder()
                .id(productId)
                .name("Unknown Product (Fallback)")
                .price(null)
                .status(status)
                .build();
    }
}
```

---

## 3. Bộ Unit Test (JUnit 5 & Mockito)

Tệp test tại: `src/test/java/com/vietmart/order/client/ProductServiceClientRTTest.java`

1. **`test_getById_Success`**: Kiểm tra Happy Path khi `restTemplate` trả về đối tượng `ProductInfo` thành công.
2. **`test_getById_Timeout_ReturnsFallback`**: Kiểm tra Fallback Path khi `restTemplate` ném `ResourceAccessException` (Timeout), xác nhận đối tượng DTO fallback được trả về.
3. **`test_getById_NotFound_ReturnsNull`**: Kiểm tra khi `restTemplate` ném `HttpClientErrorException.NotFound` (404), xác nhận kết quả trả về `null`.

### Lệnh chạy Unit Test:
```bash
cd order-service
./gradlew test
```
