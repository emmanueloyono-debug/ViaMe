# ViaMe — Collaborator Implementation Guide
## Student 3: DANIELLA (UI/UX & Mobile / Desktop Application Developer)

- **Project:** ViaMe — Intelligent Multi-Role Urban Transport & Taxi Booking System (Project 5)
- **Course:** Java Programming II (Institut Saint Jean - ISJ, 2026/2027)
- **Role:** UI/UX & Mobile (Android) + Desktop (JavaFX) Developer
- **Lead Architect & Reviewer:** Emmanuel Oyono (Student 2 — Backend Core Engineer)
- **Assigned Git Branch:** `feature/daniella-mobile-ui`

---

## ⚠️ STRICT GIT & MERGE GOVERNANCE POLICY

> [!CAUTION]
> **DO NOT PUSH DIRECTLY TO `main`!**
> 1. All your work must be committed exclusively to your designated branch: `feature/daniella-mobile-ui`.
> 2. When your features are tested and complete, create a **Pull Request (PR)** targeting `main`.
> 3. **Verification Before Merge:** Emmanuel Oyono will run automated build verification, UI unit tests, and endpoint integration checks using Antigravity before approving any merge.
> 4. **Only Emmanuel Oyono is authorized to merge Pull Requests into `main`.** Direct pushes to `main` are blocked.

### Your Branch Workflow:
```bash
# 1. Checkout your working branch from latest main
git checkout main
git pull origin main
git checkout -b feature/daniella-mobile-ui

# 2. Develop your Android and JavaFX screens, test them against RestApiServer
# 3. Stage and commit with clean conventional commits
git add android/ src/main/java/com/viame/controller/
git commit -m "feat(ui): implement Android mobile client and wire JavaFX passenger/driver dashboards"

# 4. Push to your branch and open a Pull Request
git push -u origin feature/daniella-mobile-ui
```

---

## 🎯 Scope of Work & Detailed Deliverables

As **Student 3**, you are responsible for the user-facing interfaces across two platforms:
1. **The Android Mobile App** (Passenger & Driver mobile screens communicating strictly over REST API).
2. **The JavaFX Desktop Simulation Consoles** (Interactive multi-window simulation for in-class examiner defense).

---

### PART A: Android Mobile Application (`android/` directory)

> [!IMPORTANT]
> **Mandatory Exam Rule (Page 1 of Specification):**  
> *"The Android app must not connect directly to MySQL. Use Android -> REST API -> Java backend -> MySQL."*  
> Under NO circumstances should MySQL JDBC drivers (`com.mysql.cj.jdbc.Driver`) be imported or used on Android! All data operations must flow through `RestApiServer` (port `8080`).

#### 1. REST API Integration (`BaseUrl: http://10.0.2.2:8080` for Android Emulator or your PC's LAN IP for physical device):
Use standard Retrofit, OkHttp, or `HttpURLConnection` to consume the 17 REST endpoints built by Emmanuel:

| Action | HTTP Method & Path | Payload / Parameters |
| :--- | :--- | :--- |
| **Login** | `POST /api/auth/login` | `{"email": "...", "password": "..."}` |
| **Register Passenger** | `POST /api/auth/register/passenger` | `{"email": "...", "password": "...", "phone": "...", "fullName": "..."}` |
| **Register Driver** | `POST /api/auth/register/driver` | `{"email": "...", "password": "...", "phone": "...", "fullName": "...", "licence": "...", "plate": "...", "make": "...", "model": "..."}` |
| **Fare Estimate** | `POST /api/fare/estimate` | `{"pickupLat": 4.051, "pickupLng": 9.767, "dropoffLat": 4.075, "dropoffLng": 9.718}` |
| **Nearby Drivers** | `GET /api/drivers/available?lat=4.051&lng=9.767&radius=5.0` | None |
| **Request Ride** | `POST /api/trips/request` | `{"passengerId": 1, "pickupLat": 4.051, "pickupLng": 9.767, "pickupAddress": "Akwa", "dropoffLat": 4.075, "dropoffLng": 9.718, "dropoffAddress": "Bonamoussadi", "paymentMethod": "WALLET"}` |
| **Accept Ride (Driver)** | `POST /api/trips/accept` | `{"tripId": 10, "driverId": 2, "vehicleId": 1}` |
| **Update Trip Status** | `POST /api/trips/status` | `{"tripId": 10, "status": "IN_PROGRESS"}` |
| **Cancel Ride** | `POST /api/trips/cancel` | `{"tripId": 10, "cancelledBy": "PASSENGER", "reason": "Changed plans"}` |
| **Wallet Top-Up** | `POST /api/wallet/topup` | `{"passengerId": 1, "amount": 5000.00}` |

#### 2. Android Screens to Implement:
- [ ] **Login & Registration Activity:**
  - Dynamic toggle between Passenger and Driver registration.
  - Role-based navigation to Passenger Home or Driver Home.
  - Demo quick-fill buttons for quick presentation during oral defense.
- [ ] **Passenger Screen:**
  - Douala hotspot location pickers (Akwa, Bonanjo, Bonamoussadi, Makepe, Airport, Deido, Ndokoti).
  - Route preview with distance, estimated time, surge multiplier, and total fare in XAF.
  - Payment method selector (`DIGITAL WALLET` vs `CASH`).
  - Real-time ride tracker displaying status progress:  
    `REQUESTED` $\to$ `ASSIGNED` $\to$ `DRIVER_EN_ROUTE` $\to$ `ARRIVED` $\to$ `IN_PROGRESS` $\to$ `COMPLETED`.
  - Assigned driver profile card (Driver name, car make & model, license plate, phone number, star rating).
  - Digital wallet balance display & top-up dialog.
  - Post-ride 1-to-5 star rating & feedback dialog.
- [ ] **Driver Screen:**
  - `ONLINE` / `OFFLINE` duty toggle switch.
  - Ride broadcast radar dialog with a 30-second countdown timer and "Accept Ride" button.
  - Sequential ride action buttons:
    $$\text{En Route} \longrightarrow \text{Arrived at Pickup} \longrightarrow \text{Start Ride} \longrightarrow \text{Complete Ride}$$
  - Earnings overview showing gross revenue, 20% platform commission, and 80% driver payout.

---

### PART B: JavaFX Desktop Simulation Consoles

To allow the examiners to test concurrent multi-role booking on a single laptop, finish the JavaFX controllers:

#### 1. Passenger Console (`PassengerDashboardController.java` & `PassengerDashboard.fxml`):
- [ ] Connect to `FareCalculationService` to compute real-time fare estimates upon selecting pickup/dropoff points.
- [ ] Call `TripService.requestTrip(...)` to create trips in the database.
- [ ] Wire simulated status polling or event listener to update the ride progress bar (`ProgressBar` or step badges).
- [ ] Provide wallet top-up modal calling `PaymentService.topUpWallet(...)`.
- [ ] Wire post-trip rating submission calling `RatingService.rateDriver(...)`.

#### 2. Driver Console (`DriverDashboardController.java` & `DriverDashboard.fxml`):
- [ ] Wire the `ONLINE` / `OFFLINE` toggle calling `DriverDAO.updateAvailability(...)`.
- [ ] Implement incoming ride alert card with 30s countdown timer using JavaFX `Timeline` or `ScheduledExecutorService`.
- [ ] Implement the sequential state transition buttons calling `TripService`:
  - `acceptTrip(...)` $\to$ `driverEnRoute(...)` $\to$ `driverArrived(...)` $\to$ `startTrip(...)` $\to$ `completeTrip(...)`.
- [ ] Display daily earnings counter updated automatically from `driver.getTotalEarnings()`.

---

## ✅ Acceptance Checklist (Before Requesting a Merge)

Before opening your Pull Request for Emmanuel to review, verify:
- [ ] The Android project builds cleanly in Android Studio without compilation errors.
- [ ] No direct JDBC or MySQL driver dependencies are added to the Android project (`build.gradle`).
- [ ] The Android app successfully communicates with `RestApiServer` at `http://10.0.2.2:8080` (or local IP) for login, ride estimation, booking, and wallet top-up.
- [ ] `mvn clean compile` succeeds on the Java project without breaking any existing classes.
- [ ] All 34 unit tests in `src/test` continue to pass cleanly.
- [ ] JavaFX Passenger and Driver dashboard controllers do not contain any hardcoded raw SQL queries (they must delegate strictly to Services).

---

## 🤖 PREPARED PROMPT (IF USING AI ASSISTANTS)

If you use ChatGPT, Claude, Gemini, or GitHub Copilot to help you write Android or JavaFX code, **copy and paste the following prompt verbatim**:

```markdown
You are an expert Mobile & Desktop Frontend Engineer assisting Student 3 (Daniella) on the academic project "ViaMe: Intelligent Multi-Role Urban Transport & Taxi Booking System" (Java Programming II, Institut Saint Jean).

PROJECT ARCHITECTURAL BOUNDARIES:
- Backend: Java 21, 5-tier architecture, MySQL 8.0.
- Mandatory Spec Requirement: The Android mobile application MUST NEVER connect directly to MySQL. It must communicate exclusively through the built-in HTTP REST API Server (`RestApiServer.java`) listening on port 8080.
- Desktop Simulation: JavaFX 17+ with FXML and MVC architecture.

EXISTING BACKEND SERVICES & REST API (ALREADY IMPLEMENTED BY STUDENT 2):
1. `RestApiServer` endpoints (Base URL: http://10.0.2.2:8080 on Android emulator):
   - POST /api/auth/login, POST /api/auth/register/passenger, POST /api/auth/register/driver
   - POST /api/fare/estimate, GET /api/drivers/available
   - POST /api/trips/request, POST /api/trips/accept, POST /api/trips/status, POST /api/trips/cancel
   - POST /api/wallet/topup
2. JavaFX Controllers in `src/main/java/com/viame/controller/`:
   - `PassengerDashboardController.java`
   - `DriverDashboardController.java`
   - Associated FXMLs in `src/main/resources/com/viame/views/`
3. Backend Services available for JavaFX controllers:
   - `TripService`, `FareCalculationService`, `PaymentService`, `RatingService`, `AuthenticationService`.

TASK ASSIGNMENT FOR STUDENT 3 (DANIELLA):
1. Create the Android mobile application in `android/` using Retrofit/OkHttp:
   - Passenger flow: Login -> Douala route selection -> Fare estimate -> Request ride -> Live status tracking -> Driver profile card -> Wallet topup -> Rate driver.
   - Driver flow: Login -> Duty toggle (ONLINE/OFFLINE) -> Incoming ride radar alert with 30s countdown -> Accept trip -> Sequential status buttons (En Route -> Arrived -> Start Ride -> Complete Ride) -> Earnings display.
2. Complete the JavaFX desktop controllers (`PassengerDashboardController.java` and `DriverDashboardController.java`):
   - Wire location dropdowns with Douala hotspots (Akwa, Bonanjo, Bonamoussadi, Makepe, Airport, Deido, Ndokoti).
   - Display real-time dynamic fare calculation preview with surge multiplier.
   - Implement state transition progress bar matching `TripStatus` enum:
     REQUESTED -> ASSIGNED -> DRIVER_EN_ROUTE -> ARRIVED -> IN_PROGRESS -> COMPLETED.
   - Display driver earnings breakdown (gross, 20% commission deduction, 80% net earnings).

STRICT CONSTRAINTS:
- ZERO direct MySQL/JDBC code in the Android application.
- ZERO raw SQL queries in JavaFX controllers (always call Services or DAOs).
- Provide clean, robust error handling with user alerts for insufficient wallet balance and ride cancellation penalties.
```
