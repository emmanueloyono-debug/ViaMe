# ViaMe: Physical Relational Database 3NF Normalization Justification & Analytical Specification

**Project:** ViaMe — Intelligent Multi-Role Urban Transport & Taxi Booking System  
**Institution:** Institut Saint Jean (ISJ) — Yaoundé, Cameroon  
**Course:** Java Programming II (2026/2027)  
**Author:** Student 1: SIMEN (System Analyst & Database Engineer)  
**Lead Architect & Reviewer:** Emmanuel Oyono (Student 2 — Backend Core Engineer)  
**Database Name:** `viame_db`  
**Storage Engine:** MySQL 8.0+ InnoDB (`utf8mb4_unicode_ci`)  

---

## 1. Executive Summary & Relational Foundations

The physical schema of **ViaMe** consists of 11 relational tables engineered to satisfy Boyce-Codd / Third Normal Form (3NF) relational constraints while preserving performance for real-time dispatching.

```
+----------------------------------------------------------------------------------------------------+
|                                      VIAME RELATIONAL DOMAIN MAP                                   |
|                                                                                                    |
|  [ user ] <--- (1:1) ---> [ passenger_profile ]                                                   |
|     ^     <--- (1:1) ---> [ driver_profile ] <--- (1:1) ---> [ vehicle ]                          |
|     |                                 ^                                                            |
|     +--- (1:N) ---> [ session ]       |                                                            |
|     +--- (1:N) ---> [ audit_log ]     |                                                            |
|                                       |                                                            |
|  [ pricing_rule ]                     |                                                            |
|         ^                             |                                                            |
|         | (1:N)                       | (1:N)                                                      |
|         +----------------> [ ride ] <-+                                                            |
|                              |  ^                                                                  |
|                      (1:1)   |  | (1:N)                                                            |
|           [ fare ] <---------+  +-------- [ rating ]                                               |
|           [ payment ] <------+ (1:N)                                                               |
+----------------------------------------------------------------------------------------------------+
```

### 1.1 The 11 Canonical Relational Tables
1. `user`: Core authentication, system-wide credential identity, and RBAC role definition (`PASSENGER`, `DRIVER`, `ADMIN`).
2. `passenger_profile`: Specialization extension table for passenger attributes (wallet balance, default payment mode).
3. `driver_profile`: Specialization extension table for driver certification, rating, live coordinate telemetry, and availability state.
4. `vehicle`: Physical asset registered to an approved driver (`make`, `model`, `plate_number`, seating capacity).
5. `pricing_rule`: Parameterized dynamic tariff matrices (base fare, distance rate, time rate, surge thresholds).
6. `ride`: Central lifecycle entity managing geospatial dispatching, state machine milestones, and optimistic concurrency locks (`version`).
7. `fare`: Immutable computed financial breakdown generated for a concluded or ongoing trip.
8. `payment`: Digital transaction settlement ledger supporting `CASH`, `WALLET`, `CARD`, and `MOBILE_MONEY`.
9. `rating`: Bidirectional peer-evaluation feedback records with numeric scores ($1 \le \text{score} \le 5$) and qualitative reviews.
10. `session`: Secure token storage table for active authenticated sessions with IP tracking and expiration stamps.
11. `audit_log`: Append-only compliance ledger tracking administrative operations, tariff mutations, and security events.

---

## 2. First Normal Form (1NF) Proof

### 2.1 Formal Definition of 1NF
A relation $R$ is in **First Normal Form (1NF)** if and only if:
1. Every attribute domain contains only atomic (indivisible) values.
2. There are no repeating groups or multivalued arrays stored within a single tuple.
3. Every tuple possesses a designated Primary Key ($PK$) ensuring uniqueness.

### 2.2 Application to ViaMe Physical Schema

#### Decomposition of Geospatial Coordinates
In unnormalized transport databases, GPS locations are frequently concatenated as strings (e.g., `"4.051056,9.767869"`), which violates atomicity and prevents spatial arithmetic. In ViaMe:
- Pickup locations are decomposed into two numeric attributes:
  $$\text{pickup\_lat} \in \mathbb{R} \quad (\text{DECIMAL}(9,6)), \quad \text{pickup\_lng} \in \mathbb{R} \quad (\text{DECIMAL}(9,6))$$
- Dropoff locations are decomposed identically:
  $$\text{dropoff\_lat} \in \mathbb{R} \quad (\text{DECIMAL}(9,6)), \quad \text{dropoff\_lng} \in \mathbb{R} \quad (\text{DECIMAL}(9,6))$$
- Driver live telemetry in `driver_profile`:
  $$\text{current\_lat} \in \mathbb{R} \quad (\text{DECIMAL}(9,6)), \quad \text{current\_lng} \in \mathbb{R} \quad (\text{DECIMAL}(9,6))$$

This decomposition permits standard Euclidean and Haversine distance computations directly in SQL queries:
$$d = 2R \arcsin\left(\sqrt{\sin^2\left(\frac{\Delta\phi}{2}\right) + \cos(\phi_1)\cos(\phi_2)\sin^2\left(\frac{\Delta\lambda}{2}\right)}\right)$$

#### Elimination of Repeating Groups
- **Driver Vehicles:** Rather than storing `vehicle_1`, `vehicle_2` columns in `driver_profile`, vehicle records reside in the discrete entity `vehicle`.
- **Payment Attempts:** Instead of embedding multiple payment attempts or transaction logs inside `ride`, each transaction is an atomic row in `payment`.
- **Ratings:** Rather than storing passenger ratings and driver reviews as comma-separated text inside `ride`, every rating is an atomic record in `rating`.

**Conclusion on 1NF:** All 11 tables strictly satisfy 1NF.

---

## 3. Second Normal Form (2NF) Proof

### 3.1 Formal Definition of 2NF
A relation $R$ is in **Second Normal Form (2NF)** if and only if:
1. It is in First Normal Form (1NF).
2. Every non-prime attribute is **fully functionally dependent** on the entire primary key:
   $$\forall X \subset PK, \quad X \not\to Y \quad \text{for any non-prime attribute } Y$$
   *(No partial dependencies on composite keys).*

### 3.2 Evaluation of ViaMe Physical Schema
In ViaMe, **every table uses a single-attribute surrogate primary key** (e.g., `id BIGINT AUTO_INCREMENT` or `user_id BIGINT` in 1:1 subtype tables, or `token_hash VARCHAR(64)` in `session`).

Because the primary key of each relation consists of exactly one attribute ($|PK| = 1$):
$$\nexists X \subset PK \quad \text{such that } X \neq \emptyset \text{ and } X \neq PK$$

By mathematical definition, a partial functional dependency cannot exist when the candidate key is atomic (unary). Therefore, all single-key tables (`user`, `passenger_profile`, `driver_profile`, `vehicle`, `pricing_rule`, `ride`, `fare`, `payment`, `session`, `audit_log`) are automatically in 2NF.

### 3.3 Proof on Junction and Relational Entities (`rating` and `fare`)

#### Analysis of `rating`
Even if `rating` had been modeled with a composite candidate key $\{ \text{ride\_id}, \text{rater\_id} \}$:
- Candidate Key: $\{ \text{ride\_id}, \text{rater\_id} \}$
- Dependencies:
  $$\{ \text{ride\_id}, \text{rater\_id} \} \to \text{ratee\_id}$$
  $$\{ \text{ride\_id}, \text{rater\_id} \} \to \text{score}$$
  $$\{ \text{ride\_id}, \text{rater\_id} \} \to \text{comment}$$
  $$\{ \text{ride\_id}, \text{rater\_id} \} \to \text{created\_at}$$

Notice that `score` or `comment` cannot be determined by `ride\_id` alone (since both passenger and driver can rate the same ride), nor by `rater\_id` alone (a user submits different scores for different rides). Full functional dependency holds across the entire key:
$$\text{ride\_id} \not\to \text{score}, \quad \text{rater\_id} \not\to \text{score}$$

#### Analysis of `fare`
- Primary Key: $\text{id}$ (with candidate key $\text{ride\_id}$)
- Dependencies:
  $$\text{ride\_id} \to \{ \text{base\_fare}, \text{distance\_component}, \text{time\_component}, \text{surge\_multiplier}, \text{total\_amount}, \text{currency} \}$$
No attribute depends on a proper subset of any candidate key.

**Conclusion on 2NF:** All 11 tables strictly satisfy 2NF.

---

## 4. Third Normal Form (3NF) Proof & Architectural Justifications

### 4.1 Formal Definition of 3NF
A relation $R$ is in **Third Normal Form (3NF)** if and only if:
1. It is in Second Normal Form (2NF).
2. For every non-trivial functional dependency $X \to Y$:
   - $X$ is a superkey of $R$, OR
   - $Y$ is a prime attribute (a member of a candidate key of $R$).

Equivalently, **no non-prime attribute is transitively dependent on the primary key**:
$$\nexists Z \quad \text{such that } PK \to Z \land Z \to Y \quad (Z \not\to PK, Y \notin PK)$$

### 4.2 Proof 1: Separation of `vehicle` from `driver_profile`

#### The Denormalized Anti-Pattern (2NF Violation / 3NF Transitive Dependency):
Suppose driver and vehicle attributes were merged into a single relation:
$$\text{DRIVER\_VEHICLE}(\underline{\text{user\_id}}, \text{full\_name}, \text{licence\_number}, \text{plate\_number}, \text{make}, \text{model}, \text{seats})$$

Here, the primary key is $\text{user\_id}$. However, the vehicle plate number uniquely determines vehicle specifications:
$$\text{plate\_number} \to \{ \text{make}, \text{model}, \text{seats} \}$$

This produces a transitive dependency:
$$\text{user\_id} \to \text{plate\_number} \to \{ \text{make}, \text{model}, \text{seats} \}$$

#### Resulting Relational Anomalies if Not Separated:
1. **Insertion Anomaly:** A new fleet vehicle cannot be registered in the system before a driver is recruited and verified.
2. **Update Anomaly:** If a driver updates their vehicle or if vehicle specifications change, updating the row risks desynchronizing fleet records.
3. **Deletion Anomaly:** If a driver account is suspended or deleted (`DELETE FROM driver_profile WHERE user_id = ?`), the vehicle asset metadata is lost from the database.

#### The 3NF Normalized Solution in ViaMe:
We decompose into two distinct relations:
$$\text{driver\_profile}(\underline{\text{user\_id}}, \text{full\_name}, \text{licence\_number}, \text{verification\_status}, \text{rating\_avg}, \text{availability}, \text{current\_lat}, \text{current\_lng}, \text{total\_earnings})$$
$$\text{vehicle}(\underline{\text{id}}, \text{driver\_id}, \text{plate\_number}, \text{make}, \text{model}, \text{seats}, \text{status})$$

- In `driver_profile`: All non-key attributes functionally depend strictly on $\text{user\_id}$.
- In `vehicle`: $\text{id}$ is the primary key. $\text{plate\_number}$ is a candidate key. Both $\{\text{id}\}$ and $\{\text{plate\_number}\}$ are superkeys.
  $$\text{id} \to \{ \text{driver\_id}, \text{plate\_number}, \text{make}, \text{model}, \text{seats}, \text{status} \}$$
  $$\text{plate\_number} \to \{ \text{id}, \text{driver\_id}, \text{make}, \text{model}, \text{seats}, \text{status} \}$$
No transitive dependencies exist.

---

### 4.3 Proof 2: Separation of `pricing_rule` from `fare` and `ride`

#### The Denormalized Anti-Pattern:
Suppose tariff formulas were stored directly inside the `ride` or `fare` tables:
$$\text{RIDE}(\underline{\text{id}}, \text{passenger\_id}, \dots, \text{base\_fare}, \text{per\_km}, \text{per\_min}, \text{surge\_multiplier}, \text{tariff\_name})$$

Here:
$$\text{tariff\_name} \to \{ \text{base\_fare}, \text{per\_km}, \text{per\_min} \}$$
This creates a transitive dependency:
$$\text{ride.id} \to \text{tariff\_name} \to \{ \text{base\_fare}, \text{per\_km}, \text{per\_min} \}$$

#### Resulting Relational Anomalies:
1. **Redundancy:** If 50,000 rides execute in Douala under the standard day tariff, the base fare (500 XAF), per-km rate (200 XAF), and per-minute rate (25 XAF) would be duplicated 50,000 times.
2. **Update Anomaly:** If the city municipality mandates an increase in per-km fare from 200 to 220 XAF, updating past ride rows would rewrite historical accounting data, corrupting prior earnings reports.

#### The 3NF Normalized Solution in ViaMe:
1. `pricing_rule` stores the authoritative, configurable pricing parameters:
   $$\text{pricing\_rule}(\underline{\text{id}}, \text{name}, \text{base\_fare}, \text{per\_km}, \text{per\_min}, \text{surge\_multiplier}, \text{active}, \text{valid\_from})$$
   Here, $\text{id}$ is a superkey. All non-key attributes functionally depend solely on $\text{id}$.

2. `fare` acts as an **immutable historical financial snapshot** linked to `ride`:
   $$\text{fare}(\underline{\text{id}}, \text{ride\_id}, \text{base\_fare}, \text{distance\_component}, \text{time\_component}, \text{surge\_multiplier}, \text{total\_amount}, \text{currency})$$
   - $\text{fare.ride\_id}$ is unique (1:1 with `ride`).
   - The values stored in `fare` are finalized at the exact moment of trip completion and remain fixed forever, immune to future changes in `pricing_rule`.

---

### 4.4 Proof 3: Separation of `payment` from Driver Accounts (Ledger vs. Balance)

#### The Denormalized Anti-Pattern:
Suppose driver wallet balances and banking credentials were stored directly on the payment row:
$$\text{PAYMENT}(\underline{\text{id}}, \text{ride\_id}, \text{amount}, \text{method}, \text{driver\_id}, \text{driver\_running\_balance}, \text{driver\_bank\_account})$$

Here:
$$\text{driver\_id} \to \{ \text{driver\_bank\_account}, \text{driver\_running\_balance} \}$$
Creating the transitive dependency:
$$\text{payment.id} \to \text{driver\_id} \to \{ \text{driver\_running\_balance}, \text{driver\_bank\_account} \}$$

#### Relational Anomalies:
1. **Loss of Auditability:** Updating `driver_running_balance` across historical payment rows creates write contention and race conditions.
2. **Inconsistency:** If two payments settle concurrently, reading and updating a balance on the payment record leads to lost update anomalies.

#### The 3NF Normalized Solution in ViaMe:
1. `payment` is an append-only settlement ledger:
   $$\text{payment}(\underline{\text{id}}, \text{ride\_id}, \text{amount}, \text{method}, \text{status}, \text{transaction\_ref}, \text{paid\_at})$$
   $$\text{payment.id} \to \{ \text{ride\_id}, \text{amount}, \text{method}, \text{status}, \text{transaction\_ref}, \text{paid\_at} \}$$
   Since $\text{payment.id}$ is the primary key, every attribute depends on it directly.
2. The platform/driver commission split (20% platform / 80% driver) is a **pure dynamic business projection**, calculated at query time:
   $$\text{Platform\_Commission} = \text{payment.amount} \times 0.20$$
   $$\text{Driver\_Net\_Share} = \text{payment.amount} \times 0.80$$
   Not storing these derived values preserves 3NF and eliminates redundancy.
3. `driver_profile.total_earnings` acts as an aggregated account cache updated within the same ACID transaction boundary as payment confirmation.

---

## 5. Functional Dependency (FD) Canonical Catalog

| Table | Primary Key | Candidate Keys | Functional Dependencies ($X \to Y$) | Normal Form |
| :--- | :--- | :--- | :--- | :--- |
| `user` | `id` | `email`, `phone` | `id` $\to$ {`email`, `phone`, `password_hash`, `password_salt`, `role`, `status`, `created_at`}<br>`email` $\to$ {`id`, `phone`, ...}<br>`phone` $\to$ {`id`, `email`, ...} | **3NF** |
| `passenger_profile` | `user_id` | `user_id` | `user_id` $\to$ {`full_name`, `default_payment_method`, `wallet_balance`} | **3NF** |
| `driver_profile` | `user_id` | `user_id`, `licence_number` | `user_id` $\to$ {`full_name`, `licence_number`, `verification_status`, `rating_avg`, `availability`, `current_lat`, `current_lng`, `total_earnings`}<br>`licence_number` $\to$ {`user_id`, ...} | **3NF** |
| `vehicle` | `id` | `plate_number`, `driver_id` | `id` $\to$ {`driver_id`, `plate_number`, `make`, `model`, `seats`, `status`}<br>`plate_number` $\to$ {`id`, `driver_id`, ...} | **3NF** |
| `pricing_rule` | `id` | `id` | `id` $\to$ {`name`, `base_fare`, `per_km`, `per_min`, `surge_multiplier`, `active`, `valid_from`} | **3NF** |
| `ride` | `id` | `id` | `id` $\to$ {`passenger_id`, `driver_id`, `vehicle_id`, `pricing_rule_id`, `pickup_lat`, `pickup_lng`, `pickup_address`, `dropoff_lat`, `dropoff_lng`, `dropoff_address`, `distance_km`, `duration_min`, `status`, `requested_at`, `accepted_at`, `started_at`, `completed_at`, `cancelled_at`, `version`} | **3NF** |
| `fare` | `id` | `ride_id` | `id` $\to$ {`ride_id`, `base_fare`, `distance_component`, `time_component`, `surge_multiplier`, `total_amount`, `currency`}<br>`ride_id` $\to$ {`id`, ...} | **3NF** |
| `payment` | `id` | `transaction_ref` | `id` $\to$ {`ride_id`, `amount`, `method`, `status`, `transaction_ref`, `paid_at`}<br>`transaction_ref` $\to$ {`id`, ...} | **3NF** |
| `rating` | `id` | `id` | `id` $\to$ {`ride_id`, `rater_id`, `ratee_id`, `score`, `comment`, `created_at`} | **3NF** |
| `session` | `token_hash` | `token_hash` | `token_hash` $\to$ {`user_id`, `expires_at`, `ip_address`} | **3NF** |
| `audit_log` | `id` | `id` | `id` $\to$ {`user_id`, `action`, `entity`, `entity_id`, `created_at`} | **3NF** |

---

## 6. Use Case to Database Table Mapping Matrix (UC-1 to UC-10)

As specified in the ViaMe Software Architecture and Task Guide, the following matrix correlates the 10 core use cases with the underlying physical database tables, CRUD operations, and required transaction isolation levels:

| Use Case ID & Name | Primary Actor | Tables Touched | SQL Operations | Concurrency & Transaction Isolation |
| :--- | :--- | :--- | :--- | :--- |
| **UC-01:** User Registration & Account Provisioning | Guest (Passenger or Driver) | `user`, `passenger_profile`, `driver_profile`, `vehicle`, `audit_log` | `INSERT INTO user`<br>`INSERT INTO passenger_profile`<br>`INSERT INTO driver_profile`<br>`INSERT INTO vehicle` | **READ COMMITTED**<br>Atomic multi-table insert inside single JDBC transaction. Enforces `UNIQUE(email, phone, licence_number)`. |
| **UC-02:** User Authentication & Session Establishment | Registered User | `user`, `session`, `audit_log` | `SELECT password_hash FROM user WHERE email = ?`<br>`INSERT INTO session`<br>`INSERT INTO audit_log` | **READ COMMITTED**<br>Indexed lookup on `idx_user_email` ($O(1)$ B-Tree traversal). Replaces prior session token. |
| **UC-03:** Trip Fare Estimation | Passenger | `pricing_rule` | `SELECT base_fare, per_km, per_min, surge_multiplier FROM pricing_rule WHERE active = TRUE` | **READ COMMITTED**<br>Read-only memory cache / direct query; no locking required. |
| **UC-04:** Trip Request Creation & Broadcast | Passenger | `ride`, `passenger_profile`, `pricing_rule`, `audit_log` | `INSERT INTO ride (...) VALUES (..., 'REQUESTED', version=1)` | **READ COMMITTED**<br>Inserts trip with initial state `REQUESTED`. Indexed on `idx_ride_status`. |
| **UC-05:** Driver Trip Acceptance & Lock | Driver | `ride`, `driver_profile`, `vehicle` | `SELECT status, version FROM ride WHERE id = ? FOR UPDATE`<br>`SELECT availability FROM driver_profile WHERE user_id = ? FOR UPDATE`<br>`UPDATE ride SET status = 'ASSIGNED', driver_id = ?, vehicle_id = ?, version = version + 1`<br>`UPDATE driver_profile SET availability = 'ON_TRIP'` | **REPEATABLE READ / SERIALIZABLE**<br>**Pessimistic row-level lock** (`FOR UPDATE`) prevents two drivers from accepting the identical ride concurrently (TC-CONC-01). |
| **UC-06:** Ride Execution (Arrival to Destination) | Driver | `ride`, `driver_profile`, `audit_log` | `UPDATE ride SET status = ?, started_at = ?, version = version + 1 WHERE id = ? AND version = ?`<br>`UPDATE ride SET status = 'COMPLETED', completed_at = ? WHERE id = ?` | **READ COMMITTED**<br>**Optimistic Concurrency Control (OCC)** using `version` column guarantees idempotent state transitions. |
| **UC-07:** Atomic Payment Settlement | System / Driver / Passenger | `payment`, `fare`, `ride`, `passenger_profile`, `driver_profile` | `INSERT INTO fare (...)`<br>`INSERT INTO payment (...)`<br>`UPDATE passenger_profile SET wallet_balance = wallet_balance - ?`<br>`UPDATE driver_profile SET total_earnings = total_earnings + ?`<br>`UPDATE ride SET status = 'COMPLETED'` | **REPEATABLE READ**<br>Strict ACID multi-table transaction with `conn.setAutoCommit(false)` and `conn.rollback()` ensuring no orphaned funds. |
| **UC-08:** Driver & Passenger Rating / Review | Passenger & Driver | `rating`, `driver_profile` (via Trigger) | `INSERT INTO rating (ride_id, rater_id, ratee_id, score, comment)`<br>*(Trigger `trg_after_rating_insert` fires automatically)* | **READ COMMITTED**<br>Automated trigger recalculates `driver_profile.rating_avg` clamped between `1.00` and `5.00`. |
| **UC-09:** Digital Wallet Top-Up | Passenger | `passenger_profile`, `audit_log` | `SELECT wallet_balance FROM passenger_profile WHERE user_id = ? FOR UPDATE`<br>`UPDATE passenger_profile SET wallet_balance = wallet_balance + ?`<br>`INSERT INTO audit_log` | **REPEATABLE READ**<br>Row-level lock prevents double-credit anomalies during simultaneous top-ups. |
| **UC-10:** Business Intelligence & Executive Reporting | Admin / Dispatcher | `ride`, `fare`, `payment`, `driver_profile`, `rating`, `user` | `SELECT ... FROM ride r JOIN fare f ON ... JOIN payment p ON ... GROUP BY ...` | **READ COMMITTED**<br>MVCC consistent non-blocking read snapshot; no table locks imposed on operational dispatch. |

---

## 7. Defense Rubric & Oral Examination Preparation

### 7.1 Question 1: Pessimistic Row Locking (`SELECT ... FOR UPDATE`)
**Question:** *How does `SELECT ... FOR UPDATE` in `TripDAOImpl.java` interact with MySQL InnoDB row-level locks on the `ride` table to prevent two drivers from accepting the same ride simultaneously?*

**Academic Answer:**
1. **The Race Condition:** When two nearby drivers simultaneously tap "Accept" on the same broadcasted trip, two concurrent threads execute `lockAndAssignTrip(tripId, driverId, vehicleId)`.
2. **InnoDB Exclusive Lock ($X$-Lock):**
   ```sql
   SELECT status, version FROM ride WHERE id = ? FOR UPDATE;
   ```
   Under InnoDB, `FOR UPDATE` places an exclusive ($X$) row lock on the clustered index record of the specific `ride` primary key.
3. **Mutual Exclusion:** Thread 1 acquires the $X$-lock first. Thread 2 is forced into a waiting state on the InnoDB lock queue until Thread 1 commits or rolls back.
4. **State Re-verification:** Inside the lock boundary, Thread 1 verifies that `status == 'REQUESTED'`. It executes the update:
   ```sql
   UPDATE ride SET status = 'ASSIGNED', driver_id = ?, vehicle_id = ?, version = version + 1 WHERE id = ?;
   UPDATE driver_profile SET availability = 'ON_TRIP' WHERE user_id = ?;
   ```
   Thread 1 commits, releasing the lock.
5. **Rejection of Second Attempt:** Thread 2 immediately unblocks, reads the now-committed row state, finds `status == 'ASSIGNED'` (not `'REQUESTED'`), cleanly aborts, and returns `false`. Only one driver successfully claims the trip.

---

### 7.2 Question 2: ACID Transactions & Atomicity (`setAutoCommit(false)`)
**Question:** *How do `connection.setAutoCommit(false)` and `connection.rollback()` guarantee that a wallet debit, driver credit, and trip completion either all succeed together or leave no orphaned money?*

**Academic Answer:**
1. **The Vulnerability of Auto-Commit:** In default MySQL mode, each SQL statement commits immediately. If passenger wallet debit succeeds ($-2,500\text{ XAF}$) but a database constraint or network failure aborts the driver credit ($+2,000\text{ XAF}$), money disappears from the passenger without reaching the driver.
2. **Transaction Demarcation (`TransactionManager.java`):**
   ```java
   conn.setAutoCommit(false); // Begins ACID transaction boundary
   try {
       // 1. Debit passenger wallet
       passengerDAO.updateWalletBalance(conn, passengerId, -fareAmount);
       // 2. Credit driver earnings (80% net)
       driverDAO.addEarnings(conn, driverId, fareAmount.multiply(new BigDecimal("0.80")));
       // 3. Record payment ledger entry
       paymentDAO.insert(conn, payment);
       // 4. Update ride to COMPLETED
       tripDAO.updateTripStatus(conn, rideId, TripStatus.COMPLETED);
       
       conn.commit(); // Atomically writes all changes to Redo Log
   } catch (SQLException | RuntimeException e) {
       conn.rollback(); // Restores all rows to pre-transaction state via Undo Log
       throw e;
   }
   ```
3. **InnoDB Undo/Redo Engine:** Changes are held in the InnoDB buffer pool and tracked in the `undo log`. If any step fails, `rollback()` uses the undo log pages to revert modified rows to their exact initial state, ensuring complete **Atomicity** and **Consistency**.

---

### 7.3 Question 3: Index Optimization & Sub-Millisecond Execution
**Question:** *Why are the composite and single-column indexes on `availability`, `email`, and `role` critical for real-time sub-millisecond query execution?*

**Academic Answer:**
1. **`idx_driver_availability (availability)` on `driver_profile`:**
   - Real-time dispatching continuously queries:
     ```sql
     SELECT * FROM driver_profile WHERE availability = 'ONLINE' AND verification_status = 'APPROVED';
     ```
   - Without an index, MySQL executes a full table scan ($O(N)$), inspecting every registered driver in Douala on every search.
   - With `idx_driver_availability`, InnoDB performs a B-Tree index range scan ($O(\log N)$), isolating available drivers within microseconds.
2. **`idx_user_email (email)` on `user`:**
   - Login operations require finding credentials by email.
   - A unique B-Tree index guarantees root-to-leaf depth traversals of $\le 3$ disk page reads even across millions of users, yielding $O(1)$ search latency.
3. **`idx_user_role (role)` on `user`:**
   - Used for administrative filtering and dispatcher roster segregation (`WHERE role = 'DRIVER'`).
4. **`idx_ride_status` and `idx_ride_requested_at` on `ride`:**
   - Enables high-frequency polling and sorting of active trips:
     ```sql
     SELECT * FROM ride WHERE status = 'REQUESTED' ORDER BY requested_at DESC;
     ```
   - Eliminates in-memory filesort operations by reading pre-ordered B-Tree index leaf nodes.

---

### 7.4 Question 4: Automated Trigger Mechanics (`trg_after_rating_insert`)
**Question:** *How does the trigger maintain real-time driver reputation without application-level overhead or calculation race conditions?*

**Academic Answer:**
1. **Decoupling Application from Aggregation:** Java application code in `RatingDAOImpl.java` only needs to insert the rating record (`INSERT INTO rating ...`). It does not need to compute averages or make a secondary SQL round-trip to update `driver_profile`.
2. **Database Engine Execution:**
   - The trigger executes inside MySQL's internal InnoDB transaction engine immediately after the row is stored:
     ```sql
     IF EXISTS (SELECT 1 FROM driver_profile WHERE user_id = NEW.ratee_id) THEN
         SELECT AVG(score) INTO avg_score FROM rating WHERE ratee_id = NEW.ratee_id;
         UPDATE driver_profile
         SET rating_avg = GREATEST(1.00, LEAST(5.00, ROUND(avg_score, 2)))
         WHERE user_id = NEW.ratee_id;
     END IF;
     ```
3. **Clamping Guarantee:**
   Using `GREATEST(1.00, LEAST(5.00, ROUND(avg_score, 2)))` guarantees that the resulting float strictly honors the column check constraint:
   `CHECK (rating_avg BETWEEN 1.00 AND 5.00)`.
4. **Transactional Atomicity:** If the rating insert or trigger update fails, both operations roll back together, preventing desynchronized rating scores.

---

## 8. Verification & Acceptance Criteria Sign-Off

- [x] **Relational Schema (11 tables):** Checked and validated against 1NF, 2NF, and 3NF.
- [x] **Trigger Integrity:** `trg_after_rating_insert` compiles cleanly, averages reviews, and clamps within $[1.00, 5.00]$.
- [x] **Douala Seed Data:** Complete coverage of all 7 urban hubs (Akwa, Bonanjo, Bonamoussadi, Makepe, Airport, Deido, Ndokoti) with realistic GPS coordinates and verified BCrypt hashes.
- [x] **Financial Commission Models:** Sample records for both `CASH` and `WALLET` with standard $20\% / 80\%$ split documented.
- [x] **Java Layer Immunity:** Zero modifications made to Java source code in `src/main` or `src/test`.

*Certified by Student 1 (Simen) for review and integration by Lead Architect Emmanuel Oyono.*

