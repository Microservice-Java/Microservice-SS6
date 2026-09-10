# SS6 - Bài Tập 3: Khắc Phục Cascading Failure Do Thiếu Timeout Trong RestTemplate

Tài liệu báo cáo phân tích cơ chế sự cố lây lan dây chuyền (**Cascading Failure**), giải pháp khắc phục bằng Timeout + Fallback, kết quả kiểm chứng kiểm thử, và đề xuất các biện pháp kiến trúc nâng cao cho VietMart.

---

## 1. Phân Tích Danh Sách Lỗi Trong Lớp `StockCheckClient` Ban Đầu

```java
// CODE ĐANG GÂY CASCADING FAILURE
@Component
public class StockCheckClient {
    @Autowired
    private RestTemplate restTemplate; // Lỗi 1: Khởi tạo Bean không có @LoadBalanced, Lỗi 3: Không có timeout

    public StockInfo checkStock(Long productId) {
        // Lỗi 2: Hardcode IP vật lý 192.168.0.12:8082
        return restTemplate.getForObject("http://192.168.0.12:8082/api/stock/{pid}", StockInfo.class, productId);
    }
}
```

### Danh sách 3 lỗi:
1. **Lỗi 1: Bean RestTemplate không có `@LoadBalanced`**: Không tra cứu tên dịch vụ qua Eureka Registry.
2. **Lỗi 2: Hardcode địa chỉ IP và Port vật lý (`http://192.168.0.12:8082/...`)**: Phá vỡ cơ chế tự động mở rộng và cân bằng tải động.
3. **Lỗi 3 (Nghiêm trọng nhất): RestTemplate không cấu hình Timeout**: Mặc định thời gian chờ kết nối và đọc là vô hạn (`infinite timeout`).

---

## 2. Phân Tích Cơ Chế Cascading Failure Theo Từng Bước (Step-by-Step RCA)

```text
[product-service] (Treo DB / Chậm 30s)
       │
       ▼ (Treo Thread 30s)
[inventory-service] (Tomcat 200 Threads bị lấp đầy 100% -> Crash)
       │
       ▼ (Treo Thread chờ inventory-service)
[order-service] (Sập nghẽn dây chuyền)
       │
       ▼
[Toàn bộ hệ thống VietMart ngừng hoạt động]
```

### Quá trình sập nghẽn dây chuyền từng bước:
- **Bước 1 (Sự cố ban đầu)**: `product-service` gặp sự cố (ví dụ: truy vấn DB chậm, nghẽn kết nối), làm thời gian phản hồi API tồn kho tăng từ 50ms lên 30s+.
- **Bước 2 (Giữ Thread vĩnh viễn)**: Mỗi request từ `inventory-service` gọi sang `product-service` qua `RestTemplate` thiếu timeout sẽ bị ngưng trệ và giữ chặt 1 Tomcat Worker Thread trong 30s.
- **Bước 3 (Cạn kệt Thread Pool)**: Vào giờ cao điểm có 50 request/giây. Chỉ sau vài giây (`50 req/s × 30s = 1500 threads`), toàn bộ Tomcat Worker Thread Pool (mặc định 200 threads) của `inventory-service` bị chiếm dụng 100% (Thread Starvation).
- **Bước 4 (`inventory-service` ngưng hoạt động)**: `inventory-service` không còn thread rảnh nào để phục vụ bất kỳ API nào khác. Người dùng gọi tới `inventory-service` đều nhận lỗi `504 Gateway Timeout`.
- **Bước 5 (Lan truyền sự cố)**: `order-service` (dịch vụ gọi sang `inventory-service`) tiếp tục bị treo thread do chờ `inventory-service`. Sự cố lây lan dây chuyền từ `product-service` -> `inventory-service` -> `order-service` -> Sập toàn bộ hệ thống VietMart.

---

## 3. Mã Nguồn Đã Sửa Đổi Chuẩn

### 3.1. Cấu Hình RestTemplate Có Timeout (`RestTemplateConfig.java`)
- **Connect Timeout**: `1 giây` (`Duration.ofSeconds(1)`)
- **Read Timeout**: `2 giây` (`Duration.ofSeconds(2)`)

```java
package com.vietmart.inventory.config;

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
                .setConnectTimeout(Duration.ofSeconds(1))
                .setReadTimeout(Duration.ofSeconds(2))
                .build();
    }
}
```

### 3.2. Lớp Client An Toàn & Fallback (`StockCheckClient.java`)
- **Service ID URL**: `"http://product-service/api/stock/{pid}"`
- **Fallback**: Trả về `StockInfo.unavailable(productId)` khi bắt ngoại lệ `ResourceAccessException`.

```java
package com.vietmart.inventory.client;

import com.vietmart.inventory.dto.StockInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockCheckClient {

    private final RestTemplate restTemplate;
    private static final String PRODUCT_STOCK_URL = "http://product-service/api/stock/{pid}";

    public StockInfo checkStock(Long productId) {
        try {
            log.info("Checking stock from product-service for productId: {}", productId);
            return restTemplate.getForObject(PRODUCT_STOCK_URL, StockInfo.class, productId);

        } catch (ResourceAccessException e) {
            log.error("Timeout or network failure when checking stock for productId: {}. Error: {}", productId, e.getMessage());
            return StockInfo.unavailable(productId);

        } catch (Exception e) {
            log.error("Unexpected error when checking stock for productId: {}. Error: {}", productId, e.getMessage());
            return StockInfo.unavailable(productId);
        }
    }
}
```

---

## 4. Kiểm Chứng Bằng Integration Test

Tệp test: `src/test/java/com/vietmart/inventory/client/StockCheckClientTimeoutTest.java`

- **Kịch bản**: Mô phỏng `product-service` bị chậm 5 giây và ném `ResourceAccessException` (Read Timeout 2s).
- **Kết quả kiểm chứng**:
  - Lời gọi `checkStock()` kết thúc và trả về kết quả Fallback (`status = "UNAVAILABLE"`, `availableQuantity = 0`) trong thời gian **~2200 ms (< 3000 ms)**.
  - Ngắt đứt hoàn toàn việc treo thread vĩnh viễn, bảo vệ Thread Pool của `inventory-service`.

---

## 5. Phân Tích Mở Rộng & Đề Xuất Biện Pháp Bổ Sung

### 5.1. Phân tích nguy cơ còn lại sau khi cài Timeout (2s)
Khi lưu lượng request nạp vào cực lớn (ví dụ 1000 req/s vào giờ cao điểm), cho dù mỗi thread chỉ chờ tối đa 2s rồi timeout, việc liên tục khởi tạo 1000 kết nối chờ trong 2s vẫn có thể làm **tải CPU / Socket / Thread tăng đột biến**, gây ảnh hưởng tới `order-service`.

### 5.2. Các biện pháp bổ sung khuyến nghị (Architectural Safeguards)

1. **Circuit Breaker (Mô hình Ngắt Mạch - Resilience4j)**:
   - *Cơ chế*: Giám sát tỷ lệ thất bại/timeout. Khi tỷ lệ vượt ngưỡng (ví dụ: 50% timeout trong 10s), Circuit Breaker tự động chuyển sang trạng thái **`OPEN`**.
   - *Lợi ích*: Khi trạng thái là `OPEN`, mọi request gửi tới `product-service` đều bị từ chối **NGAY LẬP TỨC (`0 ms`)** và trả về Fallback DTO mà không cần tốn thời gian gọi HTTP nữa, giải phóng 100% tài nguyên mạng và thread.

2. **Bulkhead Pattern (Cách Ly Luồng Trữ Lượng)**:
   - *Cơ chế*: Giới hạn số lượng Thread Pool / Semaphore riêng cho từng Client (ví dụ: `StockCheckClient` chỉ được dùng tối đa 20 threads).
   - *Lợi ích*: Dù `product-service` có bị nghẽn, tối đa chỉ 20 threads bị ảnh hưởng. 180 threads còn lại của Tomcat vẫn rảnh rỗi phục vụ các tính năng khác của `inventory-service`.

3. **Rate Limiting & Gateway Throttling**:
   - Giới hạn lưu lượng request tối đa per second (RPS) nạp vào hệ thống tại API Gateway để bảo vệ các dịch vụ phía sau khỏi đợt bùng nổ traffic bất thường.
