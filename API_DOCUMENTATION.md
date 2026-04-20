# 📖 CopMap API Documentation

This document provides a reference for the CopMap Backend REST API. 

**Base URL**: `http://localhost:8080/api/v1`  
**Format**: JSON  
**Auth**: Bearer JWT Token in `Authorization` header.

---

## 🔐 1. Authentication
Endpoints for login, token refresh, and logout.

### [POST] /auth/login
Authenticate an officer.
- **Request Body**:
  ```json
  {
    "badgeNumber": "ADMIN001",
    "password": "Admin@123"
  }
  ```
- **Success Response (200)**: returns `accessToken` and `refreshToken`.

### [POST] /auth/refresh
Rotate an expired access token using a refresh token.
- **Query Param**: `refreshToken` (UUID string)

---

## 📋 2. Operations
Manage Patrols, Bandobast, and Nakabandi.

### [POST] /operations
**Role**: `SUPER_ADMIN`, `STATION_OFFICER`
Create a new deployment.
- **Body**: Includes `type` (PATROL/BANDOBAST), `plannedStart`, `checkpoints`.

### [GET] /operations
List all operations for the station. Includes pagination.

### [POST] /operations/{id}/publish
Transitions DRAFT to PUBLISHED. Alerts assigned officers.

### [POST] /operations/{id}/start
Transitions PUBLISHED to ACTIVE. Monitoring begins.

---

## 📍 3. Location Tracking
Real-time GPS updates and monitoring.

### [POST] /location/ping
**Role**: `OFFICER`
Sends GPS data from mobile app. Updates Redis cache and broadcasts via WebSocket.
- **Body**: `latitude`, `longitude`, `accuracyMeters`, `operationId`.

### [GET] /location/operations/{id}/live
**Role**: `SUPER_ADMIN`, `STATION_OFFICER`
Returns a list of all officers currently on the operation with their last known coordinates. Data is served from **Redis Cache** for <1ms latency.

### [POST] /location/sos
Immediately triggers an SOS alert for the current operation with last known coordinates.

---

## ⚠️ 4. Alerts
Lifecycle of incident management.

### [GET] /alerts/operations/{id}
Retrieve all alerts (Low Battery, SOS, Offline) for a specific operation.

### [POST] /alerts/{id}/resolve
**Role**: `STATION_OFFICER`
Closes an incident after action is taken.

---

## 🛠️ How to use the Postman Collection
1.  Locate `CopMap.postman_collection.json` in the project root.
2.  Import into Postman.
3.  The **Login** request includes a test script that automatically sets the `{{accessToken}}` variable for all other requests.
4.  Run the **Create Patrol** and **Send GPS Ping** requests to see real data populated in the system.
