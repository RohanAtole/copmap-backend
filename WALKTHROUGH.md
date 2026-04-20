# 🏛️ CopMap Backend: Architecture & Design Walkthrough

This document provides a deep-dive into the engineering decisions and technical implementation of the CopMap Backend.

---

## 1. Problem Understanding
Police field operations (Patrolling, Bandobast, Nakabandi) suffer from a "visibility gap." Supervisors (SHOs) often don't know the real-time location of their officers, and officers lack a quick way to trigger alerts (SOS) that carry exact location context.

**CopMap solves this by creating a reliable, real-time bridge between field GPS data and the station dashboard.**

---

## 2. Core Architecture: The "Hot-Cold" Storage Pattern

We use a hybrid storage strategy to balance audit requirements with real-time performance:

- **The "Cold" Layer (PostgreSQL + PostGIS)**: 
  - Every single GPS ping is stored in the `location_pings` table. 
  - **Why?** This provides a tamper-proof audit trail for post-incident investigation.
  - **Spatial index**: We use PostGIS for indexing these points, allowing for future "geofencing" and "proximity search" queries.

- **The "Hot" Layer (Redis)**:
  - We only store the *latest* known position of each officer in Redis (`officer:location:{id}`).
  - **Why?** Sub-millisecond read time. The Live Map dashboard can query 100 officers' positions every 5 seconds without ever touching (and slowing down) the primary database.
  - **Automatic Expiry**: We use Redis TTL (Time-To-Live). If an officer's phone stops sending pings (loss of signal), the key expires. Our background job detects this and triggers an "Officer Offline" alert.

---

## 3. Security & Identity Framework

### JWT with Rotating Refresh Tokens
We implemented a robust authentication system using JSON Web Tokens.
- **Access Tokens**: Short-lived (24h) for security.
- **Refresh Tokens**: Stored in DB, single-use, and revoked on logout to prevent replay attacks.

### Circular Dependency Resolution
A common issue in Spring Security is the "Chicken and Egg" problem between the `SecurityConfig` and the `JwtFilter`. We resolved this using **`@Lazy` injection** for the `UserDetailsService`, ensuring the application boots fast and remains maintainable.

### Role-Based Access Control (RBAC)
- `SUPER_ADMIN`: System-wide access.
- `STATION_OFFICER`: Focused on planning and monitoring their specific station.
- `OFFICER`: Mobile-first interface for their own assignments.

---

## 4. Real-time Communication Engine

We use **WebSockets with the STOMP protocol** to broadcast updates:
1. **Officer sends Ping** via REST API.
2. **Backend validates** it and updates Redis/DB.
3. **Backend broadcasts** the update to a specific topic: `/topic/operations/{id}/location`.
4. **Dashboard** (listening to that topic) updates the icon on the map instantly without a page refresh.

---

## 5. Technical Trade-offs & Decisions

### Why Monolith first?
We structured the project using "Service-Oriented Design" within a monolith. This allows a 5-minute refactor into microservices (Auth, Location, Operation) once the traffic hits a threshold (e.g., thousands of officers), while keeping development simple for now.

### INET Type Mapping
We used the native PostgreSQL `INET` type for storing IP addresses in audit logs. This is more efficient and provides better validation than simple strings, though it required custom Hibernate column mapping.

---

## 6. Future Roadmap
- **Geofencing**: Alert the SHO if a patrol officer leaves their assigned beat.
- **Push Notifications**: Real integration with FCM (Firebase) for cross-platform status alerts.
- **Offline Sync**: Buffer GPS pings on the mobile app when NAT density is low and sync them in batches.

---
