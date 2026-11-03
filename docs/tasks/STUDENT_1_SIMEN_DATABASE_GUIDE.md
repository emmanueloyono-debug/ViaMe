# ViaMe — Collaborator Implementation Guide
## Student 1: SIMEN (System Analyst & Database Engineer)

- **Project:** ViaMe — Intelligent Multi-Role Urban Transport & Taxi Booking System (Project 5)
- **Course:** Java Programming II (Institut Saint Jean - ISJ, 2026/2027)
- **Role:** System Analyst & Database Engineer
- **Lead Architect & Reviewer:** Emmanuel Oyono (Student 2 — Backend Core Engineer)
- **Assigned Git Branch:** `feature/simen-database-analysis`

---

## ⚠️ STRICT GIT & MERGE GOVERNANCE POLICY

> [!CAUTION]
> **DO NOT PUSH DIRECTLY TO `main`!**
> 1. All your work must be committed exclusively to your designated branch: `feature/simen-database-analysis`.
> 2. When your deliverables are tested and complete, create a **Pull Request (PR)** targeting `main`.
> 3. **Verification Before Merge:** Emmanuel Oyono will run automated verification and architectural audits using the Antigravity engine before any merge.
> 4. **Only Emmanuel Oyono is authorized to merge Pull Requests into `main`.** Direct pushes to `main` will be rejected.

### Your Branch Workflow:
```bash
# 1. Checkout your working branch from latest main
git checkout main
git pull origin main
git checkout -b feature/simen-database-analysis

# 2. Make your changes, test your SQL scripts
# 3. Stage and commit with descriptive messages
git add database/schema.sql docs/
git commit -m "docs(db): complete 3NF normalization proof, trigger verification, and expanded Douala seed data"

# 4. Push to your branch and open a Pull Request
git push -u origin feature/simen-database-analysis
```

---

## 🎯 Scope of Work & Detailed Deliverables

As **Student 1 (System Analyst & Database Engineer)**, you are responsible for the physical and analytical integrity of the database, the UML specification consistency, and the academic defense of the database design.

### 1. Database Schema & Triggers (`database/schema.sql`)
- [ ] **Verify 3NF Relational Tables:**
  Ensure all 11 tables adhere to strict third normal form without anomalous redundancies:
  `user`, `passenger_profile`, `driver_profile`, `vehicle`, `pricing_rule`, `ride`, `fare`, `payment`, `rating`, `session`, `audit_log`.
- [ ] **Maintain Automated Triggers:**
  - `trg_after_rating_insert`: Recalculates driver average rating (`rating_avg`) upon new rating insertion and clamps between `1.00` and `5.00`.
  - Ensure trigger logic does not conflict with Java application logic in `RatingDAOImpl.java` and `TripDAOImpl.java`.
- [ ] **Expand Douala Urban Seed Data:**
  - Add realistic Douala GPS coordinates for pickup/dropoff testing:
    - *Akwa Palace Hotel:* `4.051056, 9.767869`
    - *Bonanjo Administrative Zone:* `4.043500, 9.691200`
    - *Bonamoussadi Market:* `4.075833, 9.718611`
    - *Makepe Saint Tropez:* `4.092100, 9.735400`
    - *Douala International Airport:* `4.006111, 9.719444`
    - *Deido Grand Moulin:* `4.062500, 9.711111`
    - *Ndokoti Intersection:* `4.045000, 9.742000`
  - Ensure test users have verified BCrypt password hashes (password: `Password123!` -> hash: `$2a$12$tBBD95yv.dV7CBE.MUZH5usNco5O9EJ4Wxx4y9FA54J.pf5zLPE1e`).
  - Populate both `CASH` and `WALLET` payment sample records with platform commission splits (20% platform / 80% driver).

### 2. Formal 3NF Normalization Proof (`docs/3NF_NORMALIZATION_JUSTIFICATION.md`)
Create a comprehensive markdown document proving why the ViaMe database is strictly normalized to 3NF:
- [ ] **1NF Justification:** All attributes are atomic (no repeating groups, no comma-separated values, GPS coordinates decomposed into distinct `lat` and `lng` decimals).
- [ ] **2NF Justification:** Elimination of partial functional dependencies; all non-key attributes fully functionally depend on the primary key (demonstrated on junction entities like `rating` and `fare`).
- [ ] **3NF Justification:** Elimination of transitive functional dependencies:
  - *Proof 1:* Why vehicle details (`make`, `model`, `plate_number`) are separated from `driver_profile` into the `vehicle` table.
  - *Proof 2:* Why pricing rule configuration (`base_fare`, `per_km`, `per_min`, `surge_multiplier`) is separated from `fare` and `ride` into `pricing_rule`.
  - *Proof 3:* Why financial ledger records in `payment` reference `ride_id` directly without storing redundant driver bank balances.

### 3. Entity-Relationship & Use Case Specifications
- [ ] **Review and Update `docs/uml/ViaMe_ERD.puml`:**
  Ensure the PlantUML ERD accurately reflects foreign keys, cardinality (1:1, 1:N), and delete cascades (`ON DELETE CASCADE` for profiles, `ON DELETE SET NULL` for audit logs).
- [ ] **Use Case Mapping Matrix:**
  Map all 10 use cases (UC-1 to UC-10) to the underlying database tables and transaction isolation requirements.

### 4. Defense Preparation: Database Examination Rubric
You will be questioned in the oral defense on:
1. **Pessimistic Row Locking:** How `SELECT ... FOR UPDATE` in `TripDAOImpl.java` interacts with MySQL InnoDB row-level locks on the `ride` table to prevent two drivers from accepting the same ride simultaneously.
2. **ACID Transactions:** How `connection.setAutoCommit(false)` and `connection.rollback()` guarantee that a wallet debit, driver credit, and trip completion either all succeed together or leave no orphaned money.
3. **Index Optimization:** Why the composite index `idx_driver_availability (availability)` and indexes on `email` and `role` are critical for real-time sub-millisecond query execution.

---

## ✅ Acceptance Checklist (Before Requesting a Merge)

Before opening your Pull Request for Emmanuel to review, verify:
- [ ] `mysql -u root -p < database/schema.sql` executes cleanly on MySQL 8.0 without any errors or warnings.
- [ ] All foreign key constraints (`fk_passenger_user`, `fk_driver_user`, `fk_vehicle_driver`, etc.) exist and work as intended.
- [ ] The trigger `trg_after_rating_insert` compiles and automatically updates `driver_profile.rating_avg` upon a test rating insert.
- [ ] `docs/3NF_NORMALIZATION_JUSTIFICATION.md` is complete, readable, and properly formatted with mathematical functional dependency notations ($X \to Y$).
- [ ] No Java files in `src/main` or `src/test` have been modified or deleted.

---

## 🤖 PREPARED PROMPT (IF USING AI ASSISTANTS)

If you use ChatGPT, Claude, Gemini, or GitHub Copilot to help you write SQL scripts, normalization proofs, or documentation, **copy and paste the following prompt verbatim**:

```markdown
You are an expert Database Architect and Systems Analyst assisting Student 1 (Simen) on the academic project "ViaMe: Intelligent Multi-Role Urban Transport & Taxi Booking System" (Java Programming II, Institut Saint Jean).

PROJECT CONTEXT & ARCHITECTURAL BOUNDARIES:
- Platform: Hybrid Android mobile app + JavaFX desktop simulation + Java 21 REST API backend + MySQL 8.0 (InnoDB).
- Database Name: `viame_db` (utf8mb4).
- The backend core is already 85% implemented by Student 2 (Emmanuel Oyono) with 12 DAOs, 9 Services, and a full REST API server (`RestApiServer.java`).
- The database uses 100% parameterized JDBC queries. No direct database access is allowed from the Android mobile app.

TASK ASSIGNMENT FOR STUDENT 1 (SIMEN):
1. Review `database/schema.sql` containing the 11 tables:
   - `user`, `passenger_profile`, `driver_profile`, `vehicle`, `pricing_rule`, `ride`, `fare`, `payment`, `rating`, `session`, `audit_log`.
2. Expand seed data with authentic Douala hotspots (Akwa, Bonanjo, Bonamoussadi, Makepe, Airport, Deido, Ndokoti) with correct GPS coordinates and verified BCrypt password hashes (password: Password123!).
3. Write a rigorous academic document `docs/3NF_NORMALIZATION_JUSTIFICATION.md` detailing:
   - First Normal Form (1NF) proofs (atomicity of attributes).
   - Second Normal Form (2NF) proofs (elimination of partial dependencies).
   - Third Normal Form (3NF) proofs with functional dependency equations (A -> B) demonstrating why `vehicle` is decoupled from `driver_profile` and `pricing_rule` from `fare`.
4. Explain the execution mechanics of MySQL trigger `trg_after_rating_insert` and how InnoDB row-level locking (`SELECT ... FOR UPDATE`) resolves concurrency race conditions (TC-CONC-01).

STRICT CONSTRAINTS:
- Do not alter existing table names or column names, as 12 Java DAOs already depend on them.
- Ensure all SQL scripts use standard MySQL 8.0 syntax with InnoDB engine and explicit foreign key constraints.
- Output clean, professional markdown and SQL code ready for academic presentation and oral defense.
```
