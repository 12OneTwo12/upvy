# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Upvy is an educational short-form video platform that transforms scroll time into learning time. The platform combines TikTok-style vertical scrolling with educational content to provide engaging learning experiences.

**Philosophy**: Fun yet meaningful content that enables natural growth without the burden of traditional learning.

## Repository Structure

This is a monorepo containing three main components:

```
upvy/
├── upvy-backend/       # Kotlin + Spring WebFlux backend
├── upvy-frontend/      # React Native + Expo mobile app
├── upvy-ai-crawler/    # AI content generation pipeline
├── manifest-ops/       # Kubernetes deployment manifests
└── docs/               # Project documentation
```

## Development Commands

### Backend (upvy-backend)

```bash
cd upvy-backend

# Build and run
./gradlew bootRun

# Run tests
./gradlew test

# Run tests with coverage report
./gradlew test jacocoTestReport
# Coverage report: build/reports/jacoco/test/html/index.html

# Build API documentation (Spring REST Docs)
./gradlew build
# API docs: build/docs/asciidoc/index.html

# Code quality checks
./gradlew detekt                    # Kotlin static analysis
./gradlew check                     # Run all checks (tests + detekt + coverage)

# JOOQ code generation (from DDL)
./gradlew generateJooq
# Generated code: src/main/generated/me/onetwo/upvy/jooq/generated/

# Build JAR
./gradlew bootJar
# Output: build/libs/upvy-backend-*.jar
```

### Frontend (upvy-frontend)

```bash
cd upvy-frontend

# Install dependencies
npm install

# Start development server
npm start

# Run on specific platform
npm run android                     # Android emulator/device
npm run ios                         # iOS simulator/device
npm run web                         # Web browser

# Testing
npm test                            # Run tests once
npm run test:watch                  # Watch mode
npm run test:coverage               # With coverage report

# EAS Build (Expo Application Services)
eas build --platform android        # Production Android build
eas build --platform ios            # Production iOS build
eas build --profile preview         # Preview build
```

### AI Crawler (upvy-ai-crawler)

```bash
cd upvy-ai-crawler

# Build and run
./gradlew bootRun

# Run tests
./gradlew test

# Access backoffice UI
# Navigate to: http://localhost:8080/backoffice
```

## Architecture

### Backend Architecture

**Stack**: Kotlin 1.9.x + Spring Boot 3.5.x + WebFlux (Reactive)

**Key Design Patterns**:
- **Domain-Driven Design**: Code organized by domain (auth, content, feed, user, etc.)
- **Reactive Programming**: All I/O operations use reactive streams (WebFlux, R2DBC, Redis Reactive)
- **Type-Safe SQL**: JOOQ for compile-time SQL validation
- **Event-Driven**: Spring Events for decoupled async operations

**Layer Structure** (within each domain):
```
domain/
├── controller/         # REST API endpoints (WebFlux handlers)
├── service/           # Business logic layer
├── repository/        # Data access layer (R2DBC + JOOQ)
├── model/             # Domain entities
├── dto/               # Request/Response DTOs
├── event/             # Domain events
└── exception/         # Domain-specific exceptions
```

**Infrastructure Layer** (`infrastructure/`):
- `security/` - JWT authentication, OAuth2 integration
- `storage/` - AWS S3 file upload (presigned URLs)
- `redis/` - Feed caching, session storage
- `manticore/` - Full-text search integration
- `notification/` - FCM push notifications
- `event/` - Spring Event async processing
- `config/` - Application-wide configuration

**Database**:
- **MySQL 8.0+** with **R2DBC** (reactive JDBC)
- **JOOQ** for type-safe queries (code generated from `src/main/resources/sql/create-table-sql.sql`)
- **Redis** for caching and session storage
- All timestamps stored as `DATETIME(6)` in UTC, mapped to `Instant` via custom converter

**Testing**:
- **JUnit 5** + **MockK** for unit tests
- **Testcontainers** for integration tests (MySQL, Redis)
- **Spring REST Docs** for API documentation
- **ArchUnit** for architecture validation
- **Target coverage**: 79%+ (see JaCoCo reports)

**Important**: NEVER use blocking I/O in backend code. Always use reactive types (`Mono`, `Flux`).

### Frontend Architecture

**Stack**: React Native 0.81.x + Expo 54 + TypeScript 5.9.x

**Key Libraries**:
- **State Management**: Zustand (client state), React Query (server state)
- **Navigation**: React Navigation 7.x
- **Styling**: NativeWind (Tailwind CSS for React Native)
- **Forms**: React Hook Form + Zod validation
- **i18n**: i18next (supports Korean, English, Japanese)

**Directory Structure**:
```
src/
├── api/               # API client (axios) and endpoint functions
├── components/        # Reusable UI components
├── config/           # App configuration
├── hooks/            # Custom React hooks
├── locales/          # i18n translation files (ko/en/ja)
├── navigation/       # Navigation configuration
├── screens/          # Screen components (Home, Feed, Profile, etc.)
├── stores/           # Zustand stores
├── types/            # TypeScript type definitions
└── utils/            # Utility functions
```

**API Integration**:
- All API calls in `src/api/*.api.ts`
- Centralized client configuration in `src/api/client.ts`
- React Query hooks for data fetching and caching

### AI Crawler Architecture

**Stack**: Kotlin 1.9.x + Spring Boot 3.x + Spring Batch 5.x

**Pipeline** (5-step batch job):
1. **Crawl**: YouTube CC-licensed content search (YouTube Data API v3 + yt-dlp)
2. **Transcribe**: Speech-to-text with timestamps (Google STT Chirp)
3. **Analyze**: Extract highlight segments with AI (Vertex AI Gemini)
4. **Edit**: Automated video trimming and vertical format conversion (FFmpeg)
5. **Review**: Quality scoring and admin approval queue

**Quality Scoring**: Educational value, relevance, short-form suitability, predicted quality (70+ score → approval queue)

**Backoffice**: Thymeleaf-based admin UI for content approval/rejection workflow

## Testing Philosophy

This project heavily uses **TDD (Test-Driven Development)** to maximize AI collaboration efficiency:

1. Write scenario-based test cases first
2. Implement the feature to pass tests
3. Code review and refinement
4. Refactor with test safety net
5. Verify all tests pass

**Backend Test Structure**:
- Unit tests: Mock dependencies with MockK
- Integration tests: Use Testcontainers for real database/Redis
- Controller tests: Spring WebTestClient for REST API testing
- Repository tests: R2DBC reactive queries with test database

**Frontend Test Structure**:
- Component tests: React Native Testing Library
- API tests: Mock axios responses
- Hook tests: Test custom hooks in isolation

## Common Development Patterns

### Backend: Reactive WebFlux Controllers

Controllers return `Mono<T>` or `Flux<T>`:

```kotlin
@RestController
@RequestMapping("/api/v1/feed")
class FeedController(private val feedService: FeedService) {

    @GetMapping
    fun getPersonalizedFeed(
        @AuthenticationPrincipal userId: String,
        @RequestParam cursor: String?
    ): Mono<FeedResponse> {
        return feedService.getPersonalizedFeed(userId, cursor)
    }
}
```

### Backend: JOOQ Query Pattern

Always use type-safe JOOQ DSL instead of string SQL:

```kotlin
return dslContext
    .selectFrom(CONTENTS)
    .where(CONTENTS.ID.eq(contentId))
    .and(CONTENTS.DELETED_AT.isNull())
    .fetchOne()
    ?.into(Content::class.java)
```

### Backend: Soft Delete Pattern

All domain entities support soft delete via `deletedAt` timestamp:
- NEVER use hard delete in production code
- Filter queries with `.and(TABLE.DELETED_AT.isNull())`
- Deletion sets `deletedAt = Instant.now()`

### Backend: PathVariable Enum Handling

**CRITICAL**: Always use lowercase for Enum PathVariables in tests and frontend API calls.

Backend has `StringToEnumConverterFactory` that converts case-insensitively, but:
- **Test code**: Use `.lowercase()` - e.g., `Category.PROGRAMMING.name.lowercase()`
- **Frontend**: Use `.toLowerCase()` - e.g., `category.toLowerCase()`

### Frontend: API Client Pattern

All API calls go through centralized client in `src/api/client.ts`:

```typescript
// src/api/feed.api.ts
export const getFeed = async (cursor?: string): Promise<FeedResponse> => {
  const response = await client.get('/feed', { params: { cursor } });
  return response.data;
};
```

Use React Query hooks for data fetching:

```typescript
const { data, isLoading } = useQuery({
  queryKey: ['feed', cursor],
  queryFn: () => getFeed(cursor),
});
```

### Frontend: Zustand Store Pattern

Client-side state management:

```typescript
import { create } from 'zustand';

interface AuthStore {
  isAuthenticated: boolean;
  setAuthenticated: (value: boolean) => void;
}

export const useAuthStore = create<AuthStore>((set) => ({
  isAuthenticated: false,
  setAuthenticated: (value) => set({ isAuthenticated: value }),
}));
```

## Important Implementation Notes

### Backend

1. **WebFlux Reactive Context**: All database/Redis/HTTP operations must be reactive
2. **JOOQ Code Generation**: Run `./gradlew generateJooq` after schema changes
3. **JWT Authentication**: Access tokens expire in 1 hour, refresh tokens in 14 days
4. **OAuth2 Flow**: Custom Tabs-based Google OAuth for mobile app
5. **Spring Events**: Use for async operations (notifications, analytics, etc.)
6. **Presigned URLs**: S3 uploads use client-side presigned URLs (no file upload to backend)

### Frontend

1. **Deep Linking**: App handles `upvy://` scheme for OAuth callbacks
2. **Optimistic Updates**: UI updates immediately for likes/saves/follows
3. **Video Player**: Uses expo-video for short-form video playback
4. **i18n**: Content language filtering based on user language preference
5. **Push Notifications**: FCM tokens managed via Expo Notifications

### Database Schema

- Generated JOOQ code in: `upvy-backend/src/main/generated/`
- Source DDL: `upvy-backend/src/main/resources/sql/create-table-sql.sql`
- All tables have audit trail: `createdAt`, `updatedAt`, `deletedAt` (soft delete)
- Foreign keys use UUID (stored as BINARY(16) in MySQL)

## Development Workflow

1. **Branch Strategy**: Work on `dev` branch, merge to `main` for production
2. **Commit Messages**: Follow conventional commits (feat:, fix:, chore:, etc.)
3. **Code Review**: Run tests and linters before committing
4. **Backend**: Ensure `./gradlew check` passes (tests + detekt + coverage)
5. **Frontend**: Ensure `npm test` passes and TypeScript compiles

## Environment Variables

### Backend Required

- `DB_PASSWORD` - MySQL password
- `REDIS_PASSWORD` - Redis password
- `JWT_SECRET` - JWT signing secret
- `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` - OAuth2 credentials
- `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` - S3 credentials
- `SMTP_PASSWORD` - Email service password

### Frontend Required

- Backend API URL configured in `src/api/client.ts`
- OAuth redirect URIs match backend configuration

### AI Crawler Required

- `GCP_PROJECT_ID` - Google Cloud project for Vertex AI
- `GOOGLE_APPLICATION_CREDENTIALS` - Service account JSON path
- `YOUTUBE_API_KEY` - YouTube Data API key
- `AWS_*` - S3 credentials (same as backend)
- Database credentials (same as backend)

## Key Features

- **Smart Feed**: Item-based collaborative filtering + Redis caching
- **Search**: Manticore Search for full-text search (contents + creators)
- **Social**: Likes, comments, follows, shares with optimistic UI updates
- **Content Creation**: Video trimming, thumbnail selection, S3 upload
- **Notifications**: FCM push notifications with user preferences
- **Analytics**: Creator analytics (views, likes, comments, saves, shares)
- **Moderation**: Content/user reporting and blocking system

## AI Collaboration

This project actively experiments with AI-in-the-Loop development:
- **Level 4 (Local Agent)**: For implementation tasks
- **Level 2-3 (Intent-based Chat)**: For architecture and design

TDD workflow maximizes AI efficiency by providing clear success criteria via tests.
