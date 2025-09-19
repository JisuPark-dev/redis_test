# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Spring Boot application demonstrating three different concurrency control mechanisms for product management:
1. **Optimistic Locking** - Using JPA `@Version`
2. **Pessimistic Locking** - Using database-level locks
3. **Redis Distributed Locking** - Using Redisson for distributed systems

## Build and Run Commands

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun

# Clean and rebuild
./gradlew clean build

# Run tests
./gradlew test

# Run specific test
./gradlew test --tests "TestClassName.methodName"
```

## Required Infrastructure

Before running the application:
1. **MySQL** - Must be running on localhost:3306
   - Database: `redis_test` 
   - Credentials: root / 1234qwer!!
2. **Redis** - Must be running on localhost:6379

## Architecture Overview

### Concurrency Control Implementations

The codebase implements the same product CRUD operations using three different concurrency strategies:

1. **Optimistic Locking** (`com.study.redis_test.controller`, `service`)
   - Uses `Product` entity with `@Version` field
   - Throws `OptimisticLockingFailureException` on concurrent modifications
   - Best for low-contention scenarios

2. **Pessimistic Locking** (`com.study.redis_test.pessimistic.*`)
   - Uses `ProductPlain` entity without version field
   - Repository methods use `@Lock(LockModeType.PESSIMISTIC_WRITE)`
   - Blocks at database level until lock is released

3. **Redis Distributed Locking** (`com.study.redis_test.redislock.*`)
   - Uses same `ProductPlain` entity
   - `RedisLockService` wraps operations with Redisson locks
   - Suitable for distributed/microservice architectures

### Key Components

- **RedisLockService**: Central service for distributed lock management
  - Default timeout: 10s wait, 3s lease
  - Lock keys follow pattern: `product:lock:{id}` or `product:stock:lock:{id}`

- **Entity Auditing**: Both entities use JPA auditing for timestamps
  - Enabled via `@EnableJpaAuditing` in main application class

- **DTOs**: Shared across all implementations
  - `ProductCreateRequest`, `ProductUpdateRequest` (includes version for optimistic)
  - `ProductResponse`, `ProductPlainResponse`

### API Endpoint Structure

All three implementations expose identical REST endpoints:
- Base paths: `/api/products`, `/api/pessimistic/products`, `/api/redis-lock/products`
- Operations: Create, Read (by ID/SKU), Update, Delete, Stock adjustment

### Database Schema

Two tables with identical structure except for version field:
- `product_optlock` - includes `version` column for optimistic locking
- `product_plain` - no version column, used for pessimistic/Redis locking

Both have unique constraint on `sku` field and indexes on `stock_qty`.

## Development Guidelines

When modifying concurrency control logic:
1. Test with concurrent requests to verify behavior
2. For Redis locks, ensure proper lock release in finally blocks
3. Consider timeout values based on operation complexity

When adding new endpoints:
1. Maintain consistency across all three implementations
2. Use appropriate DTO classes for request/response
3. Handle concurrency-specific exceptions appropriately