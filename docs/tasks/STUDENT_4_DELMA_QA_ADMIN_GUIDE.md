# ViaMe — Collaborator Implementation Guide
## Student 4: DELMA (QA, Security & Deployment Engineer)

- **Project:** ViaMe — Intelligent Multi-Role Urban Transport & Taxi Booking System (Project 5)
- **Course:** Java Programming II (Institut Saint Jean - ISJ, 2026/2027)
- **Role:** QA, Security & Deployment Engineer (Admin Console & BI Visualization Lead)
- **Lead Architect & Reviewer:** Emmanuel Oyono (Student 2 — Backend Core Engineer)
- **Assigned Git Branch:** `feature/delma-qa-admin`

---

## ⚠️ STRICT GIT & MERGE GOVERNANCE POLICY

> [!CAUTION]
> **DO NOT PUSH DIRECTLY TO `main`!**
> 1. All your work must be committed exclusively to your designated branch: `feature/delma-qa-admin`.
> 2. When your features and test runs are complete, create a **Pull Request (PR)** targeting `main`.
> 3. **Verification Before Merge:** Emmanuel Oyono will run automated test suites, check code formatting, and verify architectural integrity using Antigravity before approving any merge.
> 4. **Only Emmanuel Oyono is authorized to merge Pull Requests into `main`.** Direct pushes to `main` are blocked.

### 🚀 MANDATORY FIRST STEP: Update Your Local Repository
Before opening IntelliJ IDEA / Cursor or writing any code, pull the latest code and this guide into your local workspace:
```bash
# 1. Switch to main and pull the latest backend and task files
git checkout main
git pull origin main

# 2. Create and switch to your personal feature branch
git checkout -b feature/delma-qa-admin

# 3. Verify that docs/tasks/STUDENT_4_DELMA_QA_ADMIN_GUIDE.md is present
```

### Your Daily Branch Workflow:
```bash
# 1. Develop Admin UI, wire charts, write QA tests, and prepare defense slides
# 2. Stage and commit with clean conventional commits
git add src/main/java/com/viame/controller/AdminDashboardController.java src/main/resources/com/viame/views/AdminConsole.fxml docs/
git commit -m "feat(admin): wire 5 BI visual charts, driver verification AD-2, tariff config AD-3, and defense slides"

# 3. Push to your branch and open a Pull Request
git push -u origin feature/delma-qa-admin
```

---

## 🎯 Scope of Work & Detailed Deliverables

As **Student 4**, you are responsible for:
1. The **Admin Console** with live fleet monitoring, driver verification (`AD-2`), tariff management (`AD-3`), and **5 Business Intelligence visual charts**.
2. **Quality Assurance & Security Verification** (validating 34+ tests, running `TC-CONC-01` concurrency tests, and ensuring zero SQL injection).
3. **Deployment, Packaging & Oral Defense Slide Deck**.

---

### 1. Admin Console & Visual BI Dashboards (`AdminDashboardController.java` & `AdminConsole.fxml`)

The backend reporting engine is already fully implemented by Emmanuel in [`ReportService.java`](src/main/java/com/viame/service/ReportService.java). Your task is to connect the JavaFX UI elements to `ReportService`:

- [ ] **Fleet Monitoring Tab:**
  - `TableView` displaying active rides in Douala (Trip ID, Passenger Name, Driver Name, Pickup, Dropoff, Distance, Current Status).
  - Live indicator of available online drivers across the city.
- [ ] **Driver Onboarding Verification Table (`AD-2` Workflow):**
  - Display drivers with `verification_status = PENDING`.
  - Show driver full name, phone number, licence number, vehicle plate, make, model, and seat count.
  - Action buttons:
    - **Approve Driver:** Updates driver status to `APPROVED` and vehicle to `ACTIVE` (calling `DriverDAO` and `VehicleDAO`).
    - **Reject Driver:** Updates driver status to `REJECTED` with audit trail logging.
- [ ] **Dynamic Pricing & Surge Tariff Configurator (`AD-3` Workflow):**
  - Form displaying current active pricing rule parameters: Base Fare, Per-Km Rate, Per-Minute Rate, and Surge Multiplier (1.0x to 3.0x).
  - Validation: Ensure all numbers are non-negative and surge is clamped between 1.00 and 3.00.
  - "Save Tariff" button: Deactivates previous rule, inserts new `pricing_rule` record with `valid_from = NOW()`, and writes audit log entry.
- [ ] **5 Business Intelligence (BI) Visual Charts (Spec Section 7):**
  Wire JavaFX charts directly to `ReportService` methods:
  1. *Daily Revenue & Commission Ledger:* `BarChart<String, Number>` plotting Platform Commission (20%) vs Driver Payouts (80%) by date using `reportService.dailyRevenue(from, to)`.
  2. *Driver Performance Ranking Matrix:* `TableView<DriverPerformanceRow>` displaying completed rides, completion rate %, and average star ratings using `reportService.driverPerformance(from, to)`.
  3. *Peak Demand & Hourly Bookings:* `LineChart<Number, Number>` plotting total ride requests across 0–23h and surge spikes using `reportService.peakHours(from, to)`.
  4. *Trip Cancellation Breakdown:* `PieChart` visualizing cancellation share by cancelling party (Passenger vs Driver vs System) using `reportService.cancellations(from, to)`.
  5. *Customer Loyalty Segmentation:* `BarChart<String, Number>` displaying customer volume per tier (`GOLD`, `SILVER`, `BRONZE`) using `reportService.customerLoyalty(from, to)`.
  - Provide a **"Export to CSV"** button for each report invoking `ReportService.toCsv(...)` with a JavaFX `FileChooser`.

---

### 2. Quality Assurance, Security & Concurrency Verification

- [ ] **Run the Complete Test Suite:**
  Verify that all 43 existing unit tests pass:
  ```powershell
  # Compile main and test classes
  $m2 = "$env:USERPROFILE\.m2\repository"
  $cp = (Get-ChildItem -Path $m2 -Recurse -Filter "*.jar" | ForEach-Object { $_.FullName }) -join ";"
  $testCp = "$cp;target\classes;target\test-classes"
  & "$env:USERPROFILE\.jdks\jbr-21.0.11\bin\javac.exe" -cp "$cp" -d target\classes (Get-ChildItem -Path src\main\java -Recurse -Filter "*.java" | ForEach-Object { $_.FullName })
  & "$env:USERPROFILE\.jdks\jbr-21.0.11\bin\javac.exe" -cp "$testCp" -d target\test-classes (Get-ChildItem -Path src\test\java -Recurse -Filter "*.java" | ForEach-Object { $_.FullName })
  ```
- [ ] **Execute Multi-Threaded Concurrency Test (`TC-CONC-01`):**
  - Run [`ConcurrencyRaceConditionTest.java`](src/test/java/com/viame/integration/ConcurrencyRaceConditionTest.java) with a live MySQL instance running (`viame_db`).
  - Demonstrate during oral defense: 5 driver threads simultaneously attempting to accept the same trip, resulting in exactly 1 winner and 4 rejections via MySQL `SELECT ... FOR UPDATE`.
- [ ] **Security Audit:**
  - Confirm 100% of SQL queries across all 12 DAOs use `PreparedStatement` with zero string concatenation.
  - Confirm passwords in `user` table use BCrypt work factor 12 with individual salts.

---

### 3. Packaging, Deployment & Oral Defense Preparation

- [ ] **Maven Packaging Verification:**
  - Verify that `mvn clean package` generates the application artifact.
  - Verify that `mvn javafx:run` launches the application correctly.
- [ ] **Oral Defense Slide Deck (`docs/defense/ORAL_DEFENSE_PRESENTATION.md`):**
  Prepare a professional slide deck covering:
  1. *Project Architecture:* 5-tier decoupled model (Presentation $\to$ Controller $\to$ Service $\to$ DAO $\to$ MySQL).
  2. *Hybrid Ecosystem:* Android Mobile App (REST API) + JavaFX Multi-Role Simulation Suite.
  3. *Five Core Academic Examination Questions:*
     - **OOP Tenets:** Inheritance in User profiles, Polymorphism in `FareStrategy`, Encapsulation in Domain models, Abstraction in DAOs.
     - **Concurrency Locking:** Pessimistic `SELECT ... FOR UPDATE` vs Optimistic `version` column.
     - **SQL Injection Prevention:** 100% Parameterized queries.
     - **3NF Relational Integrity:** Justification of 11 normalized tables and foreign key constraints.
     - **Fault Tolerance & ACID:** Multi-step transactional rollback on payment failures.

---

## ✅ Acceptance Checklist (Before Requesting a Merge)

Before opening your Pull Request for Emmanuel to review, verify:
- [ ] `AdminConsole.fxml` loads without errors when clicking "Admin Console" on the login screen.
- [ ] Driver approval (`AD-2`) changes the driver's status to `APPROVED` and vehicle status to `ACTIVE` in MySQL.
- [ ] Dynamic tariff changes (`AD-3`) successfully insert a new record in `pricing_rule` and log to `audit_log`.
- [ ] All 5 BI visual charts (`BarChart`, `PieChart`, `LineChart`) render correctly with sample data.
- [ ] "Export to CSV" generates a valid, well-formatted CSV file.
- [ ] All 34 existing tests in `src/test/` continue to pass with 0 failures.
- [ ] The oral defense slide outline is committed in `docs/defense/`.

---

## 🤖 HOW TO USE AI ASSISTANTS (IN-IDE VS. WEB CHAT)

Depending on whether you use an **In-IDE AI Assistant** (IntelliJ IDEA AI Assistant, GitHub Copilot, Cursor, Windsurf) or a **Standalone Web Chat** (ChatGPT, Claude, Gemini Web), use the appropriate method below:

### Option A: Using In-IDE AI (IntelliJ IDEA, Cursor, Copilot)
Because the AI directly reads your local files once you run `git pull origin main`, you do **not** need to paste the entire architecture context. Keep `docs/tasks/STUDENT_4_DELMA_QA_ADMIN_GUIDE.md`, [`ReportService.java`](../../src/main/java/com/viame/service/ReportService.java), and [`AdminDashboardController.java`](../../src/main/java/com/viame/controller/AdminDashboardController.java) open in your tabs:

1. **For Wiring the 5 BI JavaFX Charts to ReportService:**
   ```text
   @docs/tasks/STUDENT_4_DELMA_QA_ADMIN_GUIDE.md @ReportService.java @AdminDashboardController.java @AdminConsole.fxml
   You are acting as Student 4 (Delma) on ViaMe.
   Wire the 5 BI visual charts in AdminDashboardController.java to ReportService:
   - BarChart for daily revenue (Platform commission 20% vs driver payout 80%)
   - LineChart for hourly peak demand (0-23h)
   - TableView for driver performance ranking
   - PieChart for trip cancellations
   - BarChart for customer loyalty tiers (Gold, Silver, Bronze)
   - Add CSV export actions calling ReportService.toCsv(...) with FileChooser.
   Do not write raw SQL in controllers.
   ```
2. **For Driver Verification (AD-2) & Dynamic Pricing Configurator (AD-3):**
   ```text
   @docs/tasks/STUDENT_4_DELMA_QA_ADMIN_GUIDE.md @AdminDashboardController.java @DriverDAO.java @VehicleDAO.java @PricingRuleDAO.java
   Implement the driver document approval workflow (AD-2) and the dynamic tariff modifier form (AD-3). Ensure changes write audit log records and validate that surge is between 1.00 and 3.00.
   ```
3. **For the Oral Defense Presentation Slides:**
   ```text
   @docs/tasks/STUDENT_4_DELMA_QA_ADMIN_GUIDE.md
   Generate docs/defense/ORAL_DEFENSE_PRESENTATION.md with structured slides answering the 5 examination rubric questions: OOP tenets, pessimistic row locking (SELECT ... FOR UPDATE), SQL injection neutralization, 3NF normalization justification, and ACID transaction atomicity.
   ```

---

### Option B: Using Standalone Web Chat (ChatGPT, Claude, Gemini Web)
If you copy code out of the IDE into a browser chat, **copy and paste this complete context prompt verbatim**:

```markdown
You are an expert JavaFX & Quality Assurance Engineer assisting Student 4 (Delma) on the academic project "ViaMe: Intelligent Multi-Role Urban Transport & Taxi Booking System" (Java Programming II, Institut Saint Jean).

PROJECT CONTEXT & ARCHITECTURAL BOUNDARIES:
- Platform: Java 21, JavaFX 17+, MySQL 8.0 (InnoDB).
- Backend Architect: Student 2 (Emmanuel Oyono) has already delivered all 12 DAOs, 9 Services, including `ReportService.java` (generating all 5 BI reports and CSV export), and the `TC-CONC-01` concurrency race condition test harness.
- Existing Admin Controller: `src/main/java/com/viame/controller/AdminDashboardController.java`
- Existing Admin View: `src/main/resources/com/viame/views/AdminConsole.fxml`

TASK ASSIGNMENT FOR STUDENT 4 (DELMA):
1. Enhance `AdminDashboardController.java` and `AdminConsole.fxml`:
   - Live Fleet Monitoring: TableView of active rides and available drivers.
   - Driver Verification (AD-2 workflow): TableView of PENDING drivers with "Approve" (updates driver to APPROVED, vehicle to ACTIVE) and "Reject" buttons.
   - Dynamic Pricing Configurator (AD-3 workflow): Form to edit Base Fare, Per-Km, Per-Min, and Surge Multiplier (1.0x-3.0x), inserting a new pricing_rule with audit trail.
   - 5 Business Intelligence Visual Charts wired directly to `ReportService`:
     * Daily Revenue & Commission: BarChart comparing Platform Commission (20%) vs Driver Payouts (80%).
     * Driver Performance Ranking: TableView with completion rate % and ratings.
     * Peak Demand & Hourly Bookings: LineChart (0-23h requests & average surge).
     * Trip Cancellation Breakdown: PieChart by cancelling party.
     * Customer Loyalty Segmentation: BarChart (Gold, Silver, Bronze counts).
   - "Export CSV" buttons invoking `ReportService.toCsv(...)` via JavaFX FileChooser.
2. Prepare the Academic Oral Defense Presentation (`docs/defense/ORAL_DEFENSE_PRESENTATION.md`):
   - Detailed answers to the 5 exam questions: OOP tenets, pessimistic row locking (SELECT ... FOR UPDATE), SQL injection neutralization, 3NF normalization justification, and ACID transaction atomicity.

STRICT CONSTRAINTS:
- Do not write raw SQL queries in `AdminDashboardController.java`; delegate all database operations to `ReportService`, `DriverDAO`, `VehicleDAO`, and `PricingRuleDAO`.
- Ensure JavaFX charts execute calculations cleanly without freezing the UI thread.
- Maintain compatibility with Java 21 and OpenJFX 17+.
```

