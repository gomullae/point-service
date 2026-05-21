# Point Service

무료 포인트의 적립, 적립취소, 사용, 사용취소를 제공하는 Spring Boot API입니다.

## Tech Stack

- Java 21
- Spring Boot 3.5.0
- Spring Data JPA
- Spring Retry
- H2
- Caffeine Cache
- Gradle

## Build & Run

```bash
cd point-service
./gradlew clean build
./gradlew bootRun
```

- API: `http://localhost:8080`
- H2 Console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:pointdb`
- Username: `sa`
- Password: 없음

## API

| 기능 | Method | Path |
|---|---|---|
| 포인트 적립 | POST | `/api/v1/points/grants` |
| 적립 취소 | POST | `/api/v1/points/grants/{pointKey}/cancel` |
| 포인트 사용 | POST | `/api/v1/points/usages` |
| 사용 취소 | POST | `/api/v1/points/usages/{pointKey}/cancel` |
| 잔액 조회 | GET | `/api/v1/points/{userId}/balance` |
| 적립 이력 조회 | GET | `/api/v1/points/{userId}/history` |
| 관리자 수기 지급 | POST | `/api/v1/admin/points/grants` |
| 설정 조회 | GET | `/api/v1/admin/configs` |
| 설정 변경 | PUT | `/api/v1/admin/configs/{configKey}` |

## 테스트

```bash
./gradlew test
```

| 파일 | 유형 | 주요 검증 항목 |
|---|---|---|
| `PointGrantServiceTest` | 단위 | 적립·적립취소, 멱등성, 한도 초과, 중복 키 충돌 |
| `PointUsageServiceTest` | 단위 | 사용·사용취소, lazy 만료 정리, 복원 순서, CANCEL_RESTORE 생성 |
| `PointConfigServiceTest` | 단위 | 설정 조회·변경, 캐시 eviction |
| `PointControllerTest` | 통합 | 적립·취소·사용·잔액 조회 HTTP 레이어 |
| `AdminControllerTest` | 통합 | 수기 지급·설정 변경 HTTP 레이어 |
| `PointUsageConcurrencyTest` | 동시성 | 동시 사용 요청 시 잔액 초과 차감 없음 검증 |

## 설계에서 고려한 부분

### 1. 원장 추적

`point_grant`는 적립 원장, `point_usage`는 주문 단위 사용 이벤트, `point_usage_detail`은 어떤 적립분이 어떤 주문에서 얼마 사용되었는지를 기록합니다.  
이 구조를 통해 특정 적립 포인트가 1원 단위로 어디에 사용되었는지 추적할 수 있습니다.

사용 취소도 별도 이벤트로 남깁니다. `point_usage_cancel_detail`은 어떤 취소가 어떤 사용 상세를 복원했는지 기록하며, 원본 적립 복원과 신규 적립 생성을 구분합니다.

### 2. 성능 개선과 대용량 트래픽 대비

잔액 판단은 매번 `point_grant`를 합산하지 않고 `point_account.balance`를 기준으로 처리합니다.  
`point_account`는 사용자 단위 잔액과 version을 함께 가지므로, 잔액 조회와 사용 가능 금액 검증에서 불필요한 집계 쿼리를 줄일 수 있습니다.

포인트 사용 시에도 사용 가능한 모든 적립 건을 한 번에 가져오지 않습니다.  
`MANUAL 우선 → 만료일 오름차순 → 생성순` 기준으로 필요한 적립 건만 일정 크기씩 조회하며, 요청 금액을 채우면 더 이상 조회하지 않습니다.

만료 포인트는 별도 스케줄러에만 의존하지 않고, 잔액 조회/적립/사용 시점에 lazy cleanup 방식으로 반영합니다.  
만료된 잔여 적립분을 정리하고 그 금액만큼 `point_account.balance`에서 차감해 account balance의 의미를 유지합니다.

만료 여부는 `point_grant.status` 컬럼이 아닌 `expiry_date >= CURRENT_DATE` 날짜 비교로 판단합니다.  
`EXPIRED` 상태를 별도로 두지 않은 이유는, status를 추가하면 만료 시점에 레코드를 일괄 업데이트하는 배치가 필요해지기 때문입니다.  
날짜 비교 방식은 스케줄러 없이도 항상 일관된 판단을 보장하며, 당일(`expiry_date == today`)까지 사용 가능하게 처리합니다.

### 3. 조회 성능과 캐시 전략

과제 구현에서는 설정값 조회 비용을 줄이기 위해 Caffeine Local Cache를 사용했습니다.  
적립 한도, 보유 한도, 기본 만료일 같은 정책값은 자주 읽히지만 자주 바뀌지 않기 때문에 캐시 효과가 큽니다.

실운영에서 인스턴스가 여러 대라면 Local Cache만으로는 설정 변경 전파 시점이 인스턴스마다 달라질 수 있습니다.  
이 경우 Redis 기반 분산 캐시 또는 설정 변경 이벤트 기반 cache eviction을 적용하는 것이 좋습니다.

이력 조회나 관리자 리포트처럼 지연을 허용할 수 있는 조회 트래픽이 커지면 Read Replica 분리를 고려할 수 있습니다.  
다만 잔액 조회와 포인트 사용 가능 여부 판단은 복제 지연에 민감하므로 Writer DB 기준으로 처리하는 것이 안전합니다.

### 4. 관리자 엔드포인트 분리

일반 적립(`POST /api/v1/points/grants`)과 수기 지급(`POST /api/v1/admin/points/grants`)을 별도 엔드포인트로 분리했습니다.  
단일 엔드포인트에서 클라이언트가 `grant_type`을 직접 전달하면 일반 사용자가 `MANUAL` 요청을 조작해 전송하는 위변조가 가능합니다.  
엔드포인트를 분리함으로써 `AUTO` / `MANUAL` 구분을 서버에서 고정하고, 인증/인가 레이어에서 경로 기준으로 권한을 제어할 수 있습니다.

### 5. 멱등성

적립과 사용 요청은 `pointKey`를 멱등성 키로 사용합니다.  
동일한 `pointKey`로 같은 요청이 다시 들어오면 기존 결과를 반환하고, 같은 `pointKey`에 다른 요청 내용이 들어오면 충돌로 거절합니다.

적립 요청의 동일성 판단에는 사용자, 금액, 적립 타입을 포함합니다.  
만료일은 서버 정책에서 계산되는 값이므로 동일성 판단에 포함하지 않습니다.  
사용 요청의 동일성 판단에는 사용자, 주문번호, 사용 금액을 포함합니다.

이를 통해 클라이언트의 네트워크 재시도나 중복 호출이 발생해도 동일 요청은 중복 처리되지 않고, 실수로 같은 key를 다른 요청에 재사용하는 경우는 명확히 차단됩니다.

### 6. 동시성

동일 사용자의 적립/사용/취소가 동시에 들어오면 잔액 초과 차감이 발생할 수 있습니다.  
이를 막기 위해 `point_account`에 JPA `@Version`을 두고 낙관적 락으로 충돌을 감지합니다.

충돌이 발생하면 트랜잭션은 롤백되고, `@Retryable`을 통해 최신 상태를 다시 읽어 재처리합니다.  
사용자별 포인트 계정 row가 분리되어 있어 서로 다른 사용자의 요청은 충돌하지 않으며, 같은 사용자의 동시 요청만 충돌 대상이 됩니다.

이 구조는 매 요청마다 DB row를 blocking하는 비관적 락보다 일반적인 트래픽에서 락 비용이 낮고, 충돌이 발생한 경우에만 재시도 비용을 지불합니다.  
동시에 사용 요청이 들어왔을 때 잔액을 초과해서 차감하지 않음을 `PointUsageConcurrencyTest`에서 검증합니다.

### 7. 사용 취소 복원

사용 취소는 사용 당시 차감된 순서의 역순(`use_sequence` 내림차순)으로 복원합니다.  
현금성/비현금성 포인트로 비유하면, 비현금성을 먼저 소진하도록 설계했을 때 취소 시에는 현금성(AUTO)부터 복원하는 것이 비즈니스에 자연스럽다고 판단하여 LIFO 방식을 채택했습니다.  
부분 취소 시 AUTO 포인트가 먼저 복원되어 MANUAL 우선 사용 정책의 사이클이 유지됩니다.

원본 적립분이 아직 미만료라면 원본 적립의 잔여 금액을 복원하고, 이미 만료되었다면 기존 적립을 되살리지 않고 `CANCEL_RESTORE` 타입의 신규 적립을 생성합니다.  
이 방식은 취소 시점의 상태를 반영하면서도, 사용 당시 어떤 적립분이 차감되었는지에 대한 추적성을 유지합니다.
