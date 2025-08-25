**Sample Brokerage API**

A RESTful API for simulating brokerage operations, including order management, asset tracking, and customer interactions. Built with Spring Boot.

**Features**

**Order Management:** Create, cancel, and match buy/sell orders.

**Asset Tracking:** Manage customer assets and balances.

**Swagger UI:** Interactive API documentation for easy exploration.

**Unit Tests:** Comprehensive test coverage for all endpoints.

**Technologies**

Java 17

Spring Boot 3

Spring Security 6

JUnit 5

Swagger UI

**Getting Started**

1. Clone the repository:<br>
git clone https://github.com/a-aydin/sample-brokerage-api.git

2. Navigate to the project directory:<br>
cd sample-brokerage-api

3. Build and run the application:<br>
./mvnw spring-boot:run

4. Access the Swagger UI at:<br>
http://localhost:8080/swagger-ui/index.html

**API Endpoints**

POST /api/orders: Create a new order.

GET /api/orders/{id}: Retrieve order details.

PUT /api/orders/{id}/cancel: Cancel an existing order.

PUT /api/orders/{id}/match: Match an order.
