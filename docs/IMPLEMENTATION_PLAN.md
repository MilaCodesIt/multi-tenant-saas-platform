# Multi-Tenant SaaS Platform: Implementation Plan

> Expert-level implementation guide for building a production-grade multi-tenant SaaS platform using Scala 3, ZIO 2, and PostgreSQL Row-Level Security.

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Technology Decisions](#technology-decisions)
3. [Phase Breakdown](#phase-breakdown)
4. [Phase 0: Foundation](#phase-0-foundation--dev-environment)
5. [Phase 1: Single-Tenant API](#phase-1-single-tenant-api)
6. [Phase 2: Multi-Tenancy + RLS](#phase-2-multi-tenancy--row-level-security)
7. [Phase 3: Authentication + Feature Flags](#phase-3-authentication--feature-flags)
8. [Phase 4: Billing Integration](#phase-4-billing-integration-stripe)
9. [Phase 5: Observability + Hardening](#phase-5-observability--hardening)
10. [Testing Strategy](#testing-strategy)
11. [Risk Assessment](#risk-assessment)
12. [Definition of Done](#definition-of-done)

---

## Architecture Overview

### System Diagram

```
+-------------------------------------------------------------------------+
|                           API Gateway Layer                              |
|  +-------------+  +--------------+  +-------------+  +---------------+  |
|  | Auth Filter |->|Tenant Router |->|Rate Limiter |->| Request Logger|  |
|  +-------------+  +--------------+  +-------------+  +---------------+  |
+-------------------------------------------------------------------------+
                                    |
+-------------------------------------------------------------------------+
|                          Application Layer                               |
|  +------------------------------------------------------------------+   |
|  |                    Effect System (ZIO 2)                          |   |
|  |  +-------------+  +-------------+  +------------------------+    |   |
|  |  | TenantCtx   |  |FeatureFlags |  |  Observability Layer   |    |   |
|  |  | (ZIO Env)   |  |  (Reader)   |  |  (OpenTelemetry)       |    |   |
|  |  +-------------+  +-------------+  +------------------------+    |   |
|  +------------------------------------------------------------------+   |
|                                                                          |
|  +------------------------------------------------------------------+   |
|  |                      Service Layer                                |   |
|  |  +---------------+  +----------------+  +--------------------+   |   |
|  |  |TenantService  |  |ResourceService |  |  SubscriptionSvc   |   |   |
|  |  +---------------+  +----------------+  +--------------------+   |   |
|  +------------------------------------------------------------------+   |
+-------------------------------------------------------------------------+
                                    |
+-------------------------------------------------------------------------+
|                         Infrastructure Layer                             |
|  +-----------------+  +-----------------+  +-------------------------+  |
|  |   PostgreSQL    |  |     Redis       |  |      Stripe API         |  |
|  |  (Row-Level     |  |  (Sessions,     |  |   (Billing, Usage       |  |
|  |   Security)     |  |   Rate Limits)  |  |    Metering)            |  |
|  +-----------------+  +-----------------+  +-------------------------+  |
+-------------------------------------------------------------------------+
```

### Core Data Flow

```
Request
    -> JWT Validation
    -> Tenant Extraction
    -> RLS Context Set
    -> Feature Flag Check
    -> Rate Limit Check
    -> Business Logic
    -> Response
```

### Key Architectural Decisions

1. **Effect System**: ZIO 2 chosen over Cats Effect for its superior error handling (typed errors in the E channel) and ZLayer for dependency injection which maps naturally to tenant context propagation.

2. **Multi-Tenancy Strategy**: Shared database with Row-Level Security (RLS) rather than database-per-tenant for cost efficiency and simpler operations while maintaining strong isolation.

3. **Tenant Context**: Propagated via ZIO's environment (`R` channel) ensuring compile-time safety that all tenant-scoped operations have access to tenant information.

4. **Feature Flags**: Redis-backed for low latency, with fallback to in-memory defaults for resilience.

---

## Technology Decisions

### Core Stack

| Component | Technology | Rationale | Tradeoffs |
|-----------|------------|-----------|-----------|
| **Language** | Scala 3.3.x | Modern syntax, better type inference, union types | Smaller ecosystem than Scala 2 |
| **Effect System** | ZIO 2.x | ZLayer perfect for tenant context; typed errors | Larger runtime, steeper learning curve |
| **HTTP** | zio-http 3.x | Native ZIO, performant, middleware model | Less mature than http4s |
| **Database** | PostgreSQL 15+ | RLS, JSONB, excellent extension ecosystem | Need to manage RLS policies carefully |
| **DB Access** | Quill | Compile-time SQL generation, type safety | Some Scala 3 rough edges |
| **Config** | Ciris | Pure FP, great error accumulation | Steeper curve than PureConfig |
| **Auth** | JWT (zio-jwt) | Stateless, scalable | Token management complexity |
| **Billing** | Stripe | Industry standard, excellent DX | Cost at scale |
| **Feature Flags** | Custom (Redis) | Learning opportunity, simple enough | No UI initially |
| **Observability** | OpenTelemetry | Vendor-neutral, industry standard | Setup complexity |
| **Cache** | Redis | Fast, pub/sub for invalidation | Another service to manage |

### Build & Deploy

| Component | Technology | Rationale |
|-----------|------------|-----------|
| Build | sbt 1.9.x | Standard Scala build tool |
| Packaging | sbt-native-packager | Docker image generation |
| CI/CD | GitHub Actions | Free tier, good ecosystem |
| Containerization | Docker | Industry standard |
| Local Dev | Docker Compose | Reproducible environment |

---

## Phase Breakdown

```
Phase 0: Foundation (Week 1)
    |
    v
Phase 1: Single-Tenant API (Weeks 2-3)
    |
    v
Phase 2: Multi-Tenancy + RLS (Weeks 4-5)
    |
    v
Phase 3: Auth + Feature Flags (Weeks 6-7)
    |
    v
Phase 4: Billing Integration (Weeks 8-9)
    |
    v
Phase 5: Observability + Hardening (Weeks 10-12)
```

---

## Phase 0: Foundation & Dev Environment

**Duration**: 1 week
**Goal**: Establish project structure, development environment, and CI pipeline.

### Deliverables

- [ ] Scala 3 + ZIO 2 multi-module sbt project
- [ ] Docker Compose with PostgreSQL, Redis, Jaeger
- [ ] GitHub Actions CI pipeline (compile, test, format)
- [ ] Health check endpoint
- [ ] Configuration loading with Ciris

### Key Files

#### `modules/core/src/main/scala/com/multitenant/saas/domain/models.scala`

```scala
package com.multitenant.saas.domain

import java.time.Instant
import java.util.UUID

// Phantom types for type-safe IDs
opaque type TenantId = UUID
object TenantId:
  def apply(uuid: UUID): TenantId = uuid
  def generate: TenantId = UUID.randomUUID()
  extension (id: TenantId) def value: UUID = id

opaque type UserId = UUID
object UserId:
  def apply(uuid: UUID): UserId = uuid
  def generate: UserId = UUID.randomUUID()
  extension (id: UserId) def value: UUID = id

opaque type ResourceId = UUID
object ResourceId:
  def apply(uuid: UUID): ResourceId = uuid
  def generate: ResourceId = UUID.randomUUID()
  extension (id: ResourceId) def value: UUID = id

// Domain entities
final case class Tenant(
  id: TenantId,
  name: String,
  slug: String,
  plan: Plan,
  createdAt: Instant,
  updatedAt: Instant
)

enum Plan:
  case Free, Starter, Professional, Enterprise

final case class User(
  id: UserId,
  tenantId: TenantId,
  email: String,
  name: String,
  role: Role,
  createdAt: Instant
)

enum Role:
  case Owner, Admin, Member, Viewer

final case class Resource(
  id: ResourceId,
  tenantId: TenantId,
  name: String,
  data: Map[String, String],
  createdBy: UserId,
  createdAt: Instant,
  updatedAt: Instant
)
```

#### `modules/config/src/main/scala/com/multitenant/saas/config/AppConfig.scala`

```scala
package com.multitenant.saas.config

import ciris.*
import ciris.refined.*
import eu.timepit.refined.types.net.PortNumber
import eu.timepit.refined.types.string.NonEmptyString
import zio.*
import scala.concurrent.duration.*

final case class DatabaseConfig(
  host: NonEmptyString,
  port: PortNumber,
  name: NonEmptyString,
  user: NonEmptyString,
  password: Secret[NonEmptyString],
  maxConnections: Int
)

final case class RedisConfig(
  host: NonEmptyString,
  port: PortNumber
)

final case class HttpConfig(
  host: NonEmptyString,
  port: PortNumber
)

final case class AuthConfig(
  secret: Secret[NonEmptyString],
  tokenExpiry: FiniteDuration,
  refreshWindow: FiniteDuration
)

final case class StripeConfig(
  secretKey: Secret[NonEmptyString],
  webhookSecret: Secret[NonEmptyString],
  prices: StripePrices
)

final case class StripePrices(
  starter: String,
  professional: String,
  enterprise: String
)

final case class AppConfig(
  database: DatabaseConfig,
  redis: RedisConfig,
  http: HttpConfig,
  auth: AuthConfig,
  stripe: StripeConfig,
  environment: Environment
)

enum Environment:
  case Development, Staging, Production
```

### Technical Challenges

1. **sbt multi-module dependency management**: Ensure correct inter-module dependencies
2. **ZIO 2 + Scala 3 compatibility**: Pin compatible library versions
3. **Docker networking**: Services must communicate correctly

### Definition of Done

- `sbt compile` succeeds across all modules
- `docker-compose up` starts all infrastructure services
- `curl localhost:8080/health` returns `{"status": "ok"}`
- GitHub Actions CI passes on push

---

## Phase 1: Single-Tenant API

**Duration**: 2 weeks
**Goal**: Build a functional API without multi-tenancy to establish core patterns.

### Deliverables

- [ ] Domain models with typed IDs
- [ ] Typed error hierarchy (`AppError`)
- [ ] Database schema with Flyway migrations
- [ ] Repository layer with Quill
- [ ] Service layer (tagless final style)
- [ ] REST API for CRUD operations
- [ ] Request/response JSON codecs
- [ ] Unit tests for domain logic

### Key Files

#### `modules/core/src/main/scala/com/multitenant/saas/errors/AppError.scala`

```scala
package com.multitenant.saas.errors

import com.multitenant.saas.domain.*

sealed trait AppError extends Throwable:
  def message: String
  override def getMessage: String = message

object AppError:
  // Domain errors
  sealed trait DomainError extends AppError

  final case class NotFound(entityType: String, id: String) extends DomainError:
    val message = s"$entityType with id $id not found"

  final case class AlreadyExists(entityType: String, field: String, value: String) extends DomainError:
    val message = s"$entityType with $field '$value' already exists"

  final case class ValidationError(errors: List[String]) extends DomainError:
    val message = s"Validation failed: ${errors.mkString(", ")}"

  // Auth errors
  sealed trait AuthError extends AppError

  case object InvalidToken extends AuthError:
    val message = "Invalid or expired token"

  case object InsufficientPermissions extends AuthError:
    val message = "Insufficient permissions for this operation"

  final case class TenantMismatch(expected: TenantId, actual: TenantId) extends AuthError:
    val message = s"Tenant mismatch: expected ${expected.value}, got ${actual.value}"

  // Infrastructure errors
  sealed trait InfraError extends AppError

  final case class DatabaseError(cause: Throwable) extends InfraError:
    val message = s"Database error: ${cause.getMessage}"

  final case class ExternalServiceError(service: String, cause: Throwable) extends InfraError:
    val message = s"External service '$service' error: ${cause.getMessage}"
```

#### `modules/core/src/main/scala/com/multitenant/saas/services/ResourceService.scala`

```scala
package com.multitenant.saas.services

import com.multitenant.saas.domain.*
import com.multitenant.saas.errors.AppError
import zio.*

trait ResourceService:
  def create(name: String, data: Map[String, String]): IO[AppError, Resource]
  def get(id: ResourceId): IO[AppError, Resource]
  def list(limit: Int, offset: Int): IO[AppError, List[Resource]]
  def update(id: ResourceId, name: Option[String], data: Option[Map[String, String]]): IO[AppError, Resource]
  def delete(id: ResourceId): IO[AppError, Unit]

object ResourceService:
  def create(name: String, data: Map[String, String]): ZIO[ResourceService, AppError, Resource] =
    ZIO.serviceWithZIO(_.create(name, data))

  def get(id: ResourceId): ZIO[ResourceService, AppError, Resource] =
    ZIO.serviceWithZIO(_.get(id))

  def list(limit: Int, offset: Int): ZIO[ResourceService, AppError, List[Resource]] =
    ZIO.serviceWithZIO(_.list(limit, offset))

  def update(id: ResourceId, name: Option[String], data: Option[Map[String, String]]): ZIO[ResourceService, AppError, Resource] =
    ZIO.serviceWithZIO(_.update(id, name, data))

  def delete(id: ResourceId): ZIO[ResourceService, AppError, Unit] =
    ZIO.serviceWithZIO(_.delete(id))
```

#### `modules/api/src/main/scala/com/multitenant/saas/api/routes/ResourceRoutes.scala`

```scala
package com.multitenant.saas.api.routes

import com.multitenant.saas.domain.*
import com.multitenant.saas.services.ResourceService
import com.multitenant.saas.errors.AppError
import zio.*
import zio.http.*
import zio.json.*

final case class CreateResourceRequest(name: String, data: Map[String, String])
object CreateResourceRequest:
  given JsonDecoder[CreateResourceRequest] = DeriveJsonDecoder.gen

final case class UpdateResourceRequest(name: Option[String], data: Option[Map[String, String]])
object UpdateResourceRequest:
  given JsonDecoder[UpdateResourceRequest] = DeriveJsonDecoder.gen

final case class ResourceResponse(
  id: String,
  name: String,
  data: Map[String, String],
  createdAt: String,
  updatedAt: String
)
object ResourceResponse:
  given JsonEncoder[ResourceResponse] = DeriveJsonEncoder.gen
  def from(r: Resource): ResourceResponse =
    ResourceResponse(r.id.value.toString, r.name, r.data, r.createdAt.toString, r.updatedAt.toString)

object ResourceRoutes:
  def apply(): Routes[ResourceService, Response] =
    Routes(
      Method.POST / "api" / "v1" / "resources" -> handler { (req: Request) =>
        for
          body <- req.body.asString
          parsed <- ZIO.fromEither(body.fromJson[CreateResourceRequest])
                       .mapError(_ => Response.badRequest("Invalid JSON"))
          resource <- ResourceService.create(parsed.name, parsed.data)
                       .mapError(errorToResponse)
        yield Response.json(ResourceResponse.from(resource).toJson).status(Status.Created)
      }.mapError(identity),

      Method.GET / "api" / "v1" / "resources" / string("id") -> handler { (id: String, req: Request) =>
        for
          resourceId <- ZIO.attempt(ResourceId(java.util.UUID.fromString(id)))
                          .mapError(_ => Response.badRequest("Invalid UUID"))
          resource <- ResourceService.get(resourceId)
                        .mapError(errorToResponse)
        yield Response.json(ResourceResponse.from(resource).toJson)
      }.mapError(identity),

      Method.GET / "api" / "v1" / "resources" -> handler { (req: Request) =>
        val limit = req.url.queryParams.get("limit").flatMap(_.headOption).flatMap(_.toIntOption).getOrElse(20)
        val offset = req.url.queryParams.get("offset").flatMap(_.headOption).flatMap(_.toIntOption).getOrElse(0)
        for
          resources <- ResourceService.list(limit, offset)
                         .mapError(errorToResponse)
        yield Response.json(resources.map(ResourceResponse.from).toJson)
      }.mapError(identity)
    )

  private def errorToResponse(error: AppError): Response = error match
    case AppError.NotFound(entityType, id) =>
      Response.json(s"""{"error": "not_found", "message": "$entityType with id $id not found"}""")
        .status(Status.NotFound)
    case AppError.AlreadyExists(entityType, field, value) =>
      Response.json(s"""{"error": "conflict", "message": "$entityType with $field '$value' already exists"}""")
        .status(Status.Conflict)
    case AppError.ValidationError(errors) =>
      Response.json(s"""{"error": "validation", "message": "${errors.mkString(", ")}"}""")
        .status(Status.BadRequest)
    case _: AppError.AuthError =>
      Response.json(s"""{"error": "unauthorized", "message": "${error.message}"}""")
        .status(Status.Unauthorized)
    case _: AppError.InfraError =>
      Response.json(s"""{"error": "internal", "message": "An internal error occurred"}""")
        .status(Status.InternalServerError)
```

### Technical Challenges

1. **Quill + Scala 3 macros**: May need workarounds for certain query patterns
2. **ZIO-json codec derivation**: Ensure proper given instances
3. **Error channel propagation**: Getting typed errors to flow cleanly through handlers

### Definition of Done

- All CRUD endpoints respond correctly via curl
- Unit tests for domain validation pass
- Integration test hits real PostgreSQL
- Error responses return appropriate HTTP status codes

---

## Phase 2: Multi-Tenancy + Row-Level Security

**Duration**: 2 weeks
**Goal**: Add tenant isolation via RLS and tenant context propagation.

### Deliverables

- [ ] TenantContext type for ZIO environment
- [ ] PostgreSQL RLS policies
- [ ] Tenant-aware connection pool
- [ ] Tenant extraction middleware (subdomain/header)
- [ ] Tenant management endpoints
- [ ] Multi-tenant integration tests

### Key Files

#### `modules/core/src/main/scala/com/multitenant/saas/context/TenantContext.scala`

```scala
package com.multitenant.saas.context

import com.multitenant.saas.domain.*
import zio.*

final case class TenantContext(
  tenantId: TenantId,
  tenant: Tenant,
  currentUser: User
)

object TenantContext:
  def get: URIO[TenantContext, TenantContext] = ZIO.service[TenantContext]
  def tenantId: URIO[TenantContext, TenantId] = get.map(_.tenantId)
  def tenant: URIO[TenantContext, Tenant] = get.map(_.tenant)
  def currentUser: URIO[TenantContext, User] = get.map(_.currentUser)

type TenantIO[+E, +A] = ZIO[TenantContext, E, A]
```

#### `modules/database/src/main/scala/com/multitenant/saas/database/TenantAwareDataSource.scala`

```scala
package com.multitenant.saas.database

import com.multitenant.saas.domain.TenantId
import zio.*
import javax.sql.DataSource
import java.sql.Connection

trait TenantAwareDataSource:
  def withTenantConnection[A](tenantId: TenantId)(f: Connection => Task[A]): Task[A]

final case class TenantAwareDataSourceLive(dataSource: DataSource) extends TenantAwareDataSource:
  override def withTenantConnection[A](tenantId: TenantId)(f: Connection => Task[A]): Task[A] =
    ZIO.acquireReleaseWith(
      ZIO.attempt(dataSource.getConnection)
    )(conn =>
      ZIO.attempt(conn.close()).orDie
    ) { conn =>
      for
        _ <- ZIO.attempt {
          val stmt = conn.createStatement()
          stmt.execute(s"SET app.current_tenant_id = '${tenantId.value}'")
          stmt.close()
        }
        result <- f(conn)
        _ <- ZIO.attempt {
          val stmt = conn.createStatement()
          stmt.execute("RESET app.current_tenant_id")
          stmt.close()
        }
      yield result
    }

object TenantAwareDataSource:
  val layer: ZLayer[DataSource, Nothing, TenantAwareDataSource] =
    ZLayer.fromFunction(TenantAwareDataSourceLive.apply)
```

#### PostgreSQL RLS Migration

```sql
-- V002__row_level_security.sql

ALTER TABLE app.resources ENABLE ROW LEVEL SECURITY;
ALTER TABLE app.users ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE FUNCTION app.current_tenant_id() RETURNS UUID AS $$
BEGIN
  RETURN NULLIF(current_setting('app.current_tenant_id', TRUE), '')::UUID;
END;
$$ LANGUAGE plpgsql STABLE;

CREATE POLICY tenant_isolation_resources ON app.resources
  USING (tenant_id = app.current_tenant_id());

CREATE POLICY tenant_insert_resources ON app.resources
  FOR INSERT WITH CHECK (tenant_id = app.current_tenant_id());

CREATE POLICY tenant_update_resources ON app.resources
  FOR UPDATE USING (tenant_id = app.current_tenant_id());

CREATE POLICY tenant_delete_resources ON app.resources
  FOR DELETE USING (tenant_id = app.current_tenant_id());

CREATE POLICY tenant_isolation_users ON app.users
  USING (tenant_id = app.current_tenant_id());
```

### Technical Challenges

1. **RLS debugging**: Empty results could be RLS or genuinely empty
2. **Connection pooling**: Must set context per-connection, not per-pool
3. **Transaction boundaries**: Tenant context must persist across transaction

### Definition of Done

- Tenant A cannot read Tenant B's resources
- Tenant isolation tests pass
- RLS policies verified in psql
- Tenant extracted from both subdomain and header

---

## Phase 3: Authentication + Feature Flags

**Duration**: 2 weeks
**Goal**: Implement JWT auth, OAuth2, and feature flag system.

### Deliverables

- [ ] JWT generation and validation service
- [ ] OAuth2 login flow (Google)
- [ ] Session management with Redis
- [ ] Feature flag service (Redis-backed)
- [ ] RBAC middleware
- [ ] Auth integration tests

### Key Files

#### `modules/auth/src/main/scala/com/multitenant/saas/auth/JwtService.scala`

```scala
package com.multitenant.saas.auth

import com.multitenant.saas.domain.*
import com.multitenant.saas.config.AuthConfig
import zio.*
import zio.json.*
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtZIOJson}
import java.time.Instant

final case class JwtPayload(
  sub: String,
  tid: String,
  email: String,
  role: String,
  iat: Long,
  exp: Long
)

object JwtPayload:
  given JsonEncoder[JwtPayload] = DeriveJsonEncoder.gen
  given JsonDecoder[JwtPayload] = DeriveJsonDecoder.gen

sealed trait AuthError extends Throwable
object AuthError:
  case object InvalidToken extends AuthError
  case object ExpiredToken extends AuthError
  case object MalformedToken extends AuthError

trait JwtService:
  def generate(user: User): UIO[String]
  def validate(token: String): IO[AuthError, JwtPayload]
  def refresh(token: String): IO[AuthError, String]
```

#### `modules/features/src/main/scala/com/multitenant/saas/features/FeatureFlagService.scala`

```scala
package com.multitenant.saas.features

import com.multitenant.saas.domain.*
import com.multitenant.saas.context.TenantContext
import zio.*
import zio.json.*

final case class Feature(
  key: String,
  defaultEnabled: Boolean,
  description: String,
  enabledForPlans: Set[Plan] = Set.empty,
  enabledForTenants: Set[TenantId] = Set.empty,
  enabledForUsers: Set[UserId] = Set.empty,
  percentageRollout: Option[Int] = None
)

object Feature:
  given JsonEncoder[Feature] = DeriveJsonEncoder.gen
  given JsonDecoder[Feature] = DeriveJsonDecoder.gen

trait FeatureFlagService:
  def isEnabled(key: String): ZIO[TenantContext, Nothing, Boolean]
  def getAllFeatures: UIO[List[Feature]]
  def setFeature(feature: Feature): UIO[Unit]
  def deleteFeature(key: String): UIO[Unit]

object FeatureFlags:
  val BetaDashboard = "beta_dashboard"
  val AdvancedAnalytics = "advanced_analytics"
  val BulkExport = "bulk_export"
  val ApiRateLimit = "api_rate_limit"

  def whenEnabled[R, E, A](key: String)(effect: ZIO[R, E, A]): ZIO[R & TenantContext & FeatureFlagService, E, Option[A]] =
    for
      enabled <- ZIO.serviceWithZIO[FeatureFlagService](_.isEnabled(key))
      result <- if enabled then effect.map(Some(_)) else ZIO.succeed(None)
    yield result
```

### Technical Challenges

1. **OAuth2 state management**: CSRF prevention in OAuth flow
2. **Token refresh strategy**: Silent refresh vs explicit refresh endpoint
3. **Feature flag cache invalidation**: When to re-fetch from Redis

### Definition of Done

- JWT auth protects all API endpoints
- OAuth2 login with Google works end-to-end
- Feature flags toggleable without redeploy
- RBAC prevents unauthorized operations

---

## Phase 4: Billing Integration (Stripe)

**Duration**: 2 weeks
**Goal**: Integrate Stripe for subscription management and usage-based billing.

### Deliverables

- [ ] Stripe customer creation on tenant signup
- [ ] Subscription management (create, upgrade, cancel)
- [ ] Usage-based billing (metering events)
- [ ] Webhook handling
- [ ] Billing portal integration
- [ ] Subscription sync tests

### Key Files

#### `modules/billing/src/main/scala/com/multitenant/saas/billing/StripeService.scala`

```scala
package com.multitenant.saas.billing

import com.multitenant.saas.domain.*
import com.multitenant.saas.billing.domain.*
import zio.*

trait StripeService:
  def createCustomer(tenant: Tenant, email: String): Task[StripeCustomer]
  def createSubscription(tenantId: TenantId, priceId: String): Task[Subscription]
  def getSubscription(tenantId: TenantId): Task[Option[Subscription]]
  def cancelSubscription(tenantId: TenantId, atPeriodEnd: Boolean): Task[Unit]
  def updateSubscription(tenantId: TenantId, newPriceId: String): Task[Subscription]
  def recordUsage(event: UsageEvent): Task[Unit]
  def createBillingPortalSession(tenantId: TenantId, returnUrl: String): Task[String]
```

#### `modules/billing/src/main/scala/com/multitenant/saas/billing/domain/BillingModels.scala`

```scala
package com.multitenant.saas.billing.domain

import com.multitenant.saas.domain.*
import java.time.Instant

final case class StripeCustomer(
  tenantId: TenantId,
  customerId: String,
  createdAt: Instant
)

final case class Subscription(
  id: String,
  tenantId: TenantId,
  plan: Plan,
  status: SubscriptionStatus,
  currentPeriodStart: Instant,
  currentPeriodEnd: Instant,
  cancelAtPeriodEnd: Boolean
)

enum SubscriptionStatus:
  case Active, PastDue, Canceled, Incomplete, Trialing

final case class UsageEvent(
  tenantId: TenantId,
  metric: UsageMetric,
  quantity: Long,
  timestamp: Instant,
  idempotencyKey: String
)

enum UsageMetric:
  case ApiCalls
  case StorageGB
  case ComputeMinutes
  case ActiveUsers
```

### Technical Challenges

1. **Webhook idempotency**: Stripe may send duplicate events
2. **Usage aggregation**: May want to batch events rather than send per-request
3. **Proration**: Mid-cycle plan changes require careful handling

### Definition of Done

- Tenant signup creates Stripe customer
- Subscription via Checkout works
- Usage events appear in Stripe dashboard
- Webhooks update tenant plan in real-time

---

## Phase 5: Observability + Hardening

**Duration**: 2-3 weeks
**Goal**: Add production observability, security hardening, and performance optimization.

### Deliverables

- [ ] OpenTelemetry tracing
- [ ] Prometheus metrics
- [ ] Structured logging with correlation IDs
- [ ] Rate limiting (per-tenant)
- [ ] Security headers and CORS
- [ ] Input validation
- [ ] Health checks and readiness probes
- [ ] Load testing

### Key Files

#### `modules/observability/src/main/scala/com/multitenant/saas/observability/Metrics.scala`

```scala
package com.multitenant.saas.observability

import zio.*
import zio.metrics.*

object AppMetrics:
  val httpRequestsTotal = Metric.counter("http_requests_total")
    .tagged("method", "path", "status", "tenant")

  val httpRequestDuration = Metric.histogram(
    "http_request_duration_seconds",
    MetricKeyType.Histogram.Boundaries.linear(0.01, 0.05, 20)
  ).tagged("method", "path", "tenant")

  val resourcesCreated = Metric.counter("resources_created_total")
    .tagged("tenant")

  val activeUsers = Metric.gauge("active_users")
    .tagged("tenant", "plan")

  val dbConnectionPoolSize = Metric.gauge("db_connection_pool_size")
  val dbConnectionPoolUsed = Metric.gauge("db_connection_pool_used")
```

#### `modules/api/src/main/scala/com/multitenant/saas/api/middleware/RateLimitMiddleware.scala`

```scala
package com.multitenant.saas.api.middleware

import com.multitenant.saas.context.TenantContext
import com.multitenant.saas.domain.Plan
import zio.*
import zio.http.*

final case class RateLimitConfig(
  windowSeconds: Int,
  limits: Map[Plan, Int]
)

object RateLimitMiddleware:
  def apply(config: RateLimitConfig): HandlerAspect[TenantContext, Unit] =
    HandlerAspect.interceptHandler(
      Handler.fromFunctionZIO[Request] { request =>
        for
          ctx <- TenantContext.get
          limit = config.limits.getOrElse(ctx.tenant.plan, 100)
          // Redis-based rate limiting implementation
          allowed <- checkRateLimit(ctx.tenantId, limit, config.windowSeconds)
          _ <- ZIO.unless(allowed)(
                 ZIO.fail(
                   Response.status(Status.TooManyRequests)
                     .addHeader("Retry-After", config.windowSeconds.toString)
                 )
               )
        yield (request, ())
      }
    )(Handler.identity)
```

### Technical Challenges

1. **Trace context propagation**: Ensuring traces span across async boundaries
2. **Metrics cardinality**: Avoiding high-cardinality label values
3. **Rate limit accuracy**: Distributed rate limiting across instances

### Definition of Done

- Traces visible in Jaeger UI
- Metrics scrapable by Prometheus
- Rate limiting works per-tenant
- All security headers present
- Load test shows acceptable p99 latency

---

## Testing Strategy

### Testing Pyramid

```
           /\
          /  \        E2E Tests (5%)
         /----\       - Full API flows
        /      \      - Tenant isolation
       /--------\     Integration (20%)
      /          \    - Database
     /------------\   - Redis
    /              \  - Stripe (test mode)
   /----------------\ Unit (75%)
  /                  \- Domain logic
 /--------------------\- Validation
```

### By Layer

| Layer | What to Test | How |
|-------|--------------|-----|
| Domain | Validation, business logic | Pure unit tests, property-based |
| Services | Use case orchestration | Mocked dependencies |
| Repository | Query correctness, RLS | Testcontainers |
| HTTP | Request parsing, responses | Route unit tests |
| Integration | End-to-end flows | Full stack with Testcontainers |
| Security | Auth, tenant isolation | Dedicated security suite |

### Test Infrastructure

```scala
// Testcontainers setup
object TestContainers:
  val postgres: ZLayer[Scope, Throwable, PostgreSQLContainer] =
    ZLayer.scoped {
      ZIO.acquireRelease(
        ZIO.attemptBlocking {
          val container = PostgreSQLContainer(
            dockerImageNameOverride = DockerImageName.parse("postgres:15-alpine")
          )
          container.start()
          container
        }
      )(container => ZIO.attemptBlocking(container.stop()).orDie)
    }

  val redis: ZLayer[Scope, Throwable, GenericContainer] =
    ZLayer.scoped {
      ZIO.acquireRelease(
        ZIO.attemptBlocking {
          val container = GenericContainer(
            dockerImage = "redis:7-alpine",
            exposedPorts = Seq(6379)
          )
          container.start()
          container
        }
      )(container => ZIO.attemptBlocking(container.stop()).orDie)
    }
```

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| RLS misconfiguration | Medium | Critical | Extensive integration tests, security audit |
| Scala 3 library compatibility | Medium | Medium | Pin versions, have fallback options |
| ZIO learning curve | High | Medium | Build incrementally, study docs |
| Stripe webhook reliability | Low | High | Idempotency keys, retry handling |
| Connection pool exhaustion | Medium | High | Monitor metrics, proper resource management |
| OAuth2 security holes | Low | Critical | Use battle-tested library, security review |

### Scope Creep Warnings

- Admin UI -> Defer to later phase
- Multi-region support -> Out of scope
- Custom OAuth providers -> Stick with Google initially
- Real-time WebSockets -> Separate project

---

## Definition of Done

### Per-Phase

Each phase is complete when:

1. All deliverables are implemented
2. Unit tests pass
3. Integration tests pass
4. Code review completed
5. Documentation updated
6. No critical or high-severity bugs

### Project Complete

The project is production-ready when:

1. All phases complete
2. Security audit passed
3. Load testing shows acceptable performance
4. Monitoring and alerting configured
5. Runbook documented
6. Deployment pipeline tested

---

## References

- [ZIO Documentation](https://zio.dev)
- [PostgreSQL Row-Level Security](https://www.postgresql.org/docs/current/ddl-rowsecurity.html)
- [Stripe API Reference](https://stripe.com/docs/api)
- [OpenTelemetry Specification](https://opentelemetry.io/docs/specs/)
- [Quill Documentation](https://getquill.io)
