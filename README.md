# InstaPay API

The **InstaPay API** service is a Spring Boot-based RESTful application that allows users to send money instantly. It is designed with high availability, transactional integrity, and robust error handling in mind.

## Features

- **Balance Checks**: Validates that the sender's account has sufficient funds before processing a transaction.
- **Concurrency Handling**: Uses optimistic locking (via the `@Version` annotation on the Account entity) to prevent double spending and duplicate notifications.
- **Transactional Processing**: Ensures that account balance updates and transaction records are performed atomically.
- **Asynchronous Notifications**: Publishes transaction events to Apache Kafka for asynchronous processing (e.g., sending notifications).
- **Data Persistence**: Stores accounts and transactions in a PostgreSQL database.
- **Fault Tolerance**: Implements retry mechanisms and circuit breakers (using Spring Retry and Resilience4j) with fallback strategies to handle transient errors.
- **API Documentation**: Automatically generated Swagger/OpenAPI documentation for the REST endpoints.
- **Containerization**: Fully containerized using Docker and docker-compose, making the application ready for cloud deployment.

## Technical Details

- **Framework**: Spring Boot with Maven
- **Java Version**: 17
- **Database**: PostgreSQL
- **Messaging**: Apache Kafka
- **Containerization**: Docker (with docker-compose)
- **Testing**: Unit, integration, resilience, and end-to-end tests (using Testcontainers)

## Getting Started

### Prerequisites

- Java 17
- Maven
- Docker & Docker Compose

### Clone the Repository

```bash
git clone https://github.com/jkarcsi/insta-pay.git
cd insta-pay

## Build the Project

mvn clean install
```

## Run with Docker Compose
Use the provided `docker-compose.yml` file to start PostgreSQL, Kafka (with Zookeeper), and the Payment API service:

```sh
docker-compose up --build
```

The API will be accessible at [http://localhost:8080](http://localhost:8080).

> **Note:** Two predefined accounts are available by default:
> - **'acc1'** with an initial balance of 1000
> - **'acc2'** with an initial balance of 500

## API Endpoints
### Transfer Payment
**Endpoint:** `POST /api/payments/transfer`

**Description:** Processes a payment transaction between two accounts.

**Request Body Example:**
```json
{
  "fromAcct": "acc1",
  "toAcct": "acc2",
  "amount": 200.00
}
```

**Response:** Returns the details of the transaction.

### Transaction check
**Endpoint:** `GET /api/payments/transactions/{id}`

**Description:** Retrieves the details for a specific transaction.


### Account balance check
**Endpoint:** `GET /api/payments/account/{accountId}/balance`

**Description:** Retrieves the balance for a specific account.

## Logging
This application uses **SLF4J** and **Logback** for logging. The class-level logs are injected via Lombok’s `@Slf4j` annotation, which automatically provides a `log` field in each class (e.g., `PaymentService`). You can customize the log pattern, level, and other details in one of two ways:

1. **Via `logback-spring.xml`** (recommended for more complex setups).
2. **Via `application.properties`** using `logging.pattern.console` for quick pattern adjustments.

## API Documentation
Swagger/OpenAPI documentation is generated automatically. Access the Swagger UI at:

[http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

## Testing
The project includes:

- **Unit Tests:** For individual service methods and business logic.
- **Integration Tests:** Using Testcontainers to run PostgreSQL and Kafka.
- **End-to-End (E2E) Tests:** To validate the full workflow from REST endpoints to database and messaging.
- **Resilience Tests:** Includes scenarios for retry and circuit breaker handling.

To run the tests:

```sh
mvn test
```

## Fault Tolerance Mechanisms
- **Retryable:** Automatically retries failed payment operations (except for business exceptions like insufficient funds).
- **Circuit Breaker:** Prevents cascading failures by short-circuiting calls to unstable external systems.
- **Fallback:** Provides fallback methods (using `@Recover`) if operations fail after retries.

## Optimistic Locking
Optimistic locking is implemented on the `Account` entity via the `@Version` annotation to prevent double spending. This mechanism ensures that concurrent transactions on the same account result in an exception, triggering retry or fallback behavior.

## License
MIT

## Contact
For questions or support, please contact:
**Karoly Jugovits** – [jugovitskaroly@gmail.com](mailto:jugovitskaroly@gmail.com)