
# 🛡️ SupportDesk CRM

### Enterprise Customer Ingestion, Concurrency Shield & Automated Notification Engine

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot 4.1](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL 8.0](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Multi--Stage-2496ED?style=for-the-badge&logo=docker&logoColor=white)](https://www.docker.com/)
[![Gmail SMTP](https://img.shields.io/badge/Gmail-SMTP%20TLS-EA4335?style=for-the-badge&logo=gmail&logoColor=white)](https://mail.google.com/)
[![Bootstrap 5.3](https://img.shields.io/badge/Bootstrap-5.3-7952B3?style=for-the-badge&logo=bootstrap&logoColor=white)](https://getbootstrap.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

<p align="center">
  <b>A production-grade, multi-threaded customer service CRM built to eliminate agent write collisions, prevent database spam, and dispatch automated lifecycle email notifications.</b>
</p>

[Quick Evolution Tracker](#-1-evolution-tracker-what-why--how) • [Database Migration Journey](#-2-database-migration-journey-sqlite--mysql-80) • [Concurrency Shield](#-3-concurrency-shield-optimistic-locking) • [Email Notification Engine](#-4-automated-email-notification-engine) • [REST API Reference](#-5-rest-api-reference) • [Docker & Deployment](#-6-docker--render-cloud-deployment) • [Local Installation](#-7-local-installation--setup)

</div>

---

## 📊 1. Evolution Tracker: What, Why & How

| Dimension / Component | Initial Prototype | Production Upgrade | Kyu Badla? (Architectural Reason) |
| :--- | :--- | :--- | :--- |
| **Java Platform** | Java 17 | **OpenJDK 21 (LTS)** | Performance improvements, modern runtime efficiency, aur container resource management. |
| **Framework** | Spring Boot 3.2 | **Spring Boot 4.1.1** | Jakarta EE updates, enhanced security patches, aur optimized bean lifecycle handling. |
| **Persistence Store** | SQLite 3 (`crm.db`) | **MySQL 8.0 (InnoDB)** | SQLite single-file write lock model web concurrency me `Hikari Connection Timeout` throw kar raha tha. MySQL row-level locking aur high-concurrency provide karta hai. |
| **Dialect Engine** | Community SQLite Dialect | **Hibernate MySQLDialect** | SQLite dialect me `Cannot add UNIQUE column` aur auto-increment mismatch problems solve kiye gaye. |
| **ID Strategy** | SQLite RowID implicit | **`GenerationType.IDENTITY`** | Multi-table relational integrity aur auto-increment IDs bina sequence contention ke ensure karne ke liye. |
| **Notifications** | Silent Database Writes | **Automated Gmail SMTP Engine** | Complainant ko ticket reference track link aur resolution status real-time deliver karne ke liye. |
| **Secrets & Configs** | Hardcoded properties | **12-Factor Variables (`DB_*`, `MAIL_*`)** | Git push par credentials expose na hon aur Render/AWS par direct env-injection se run ho sake. |
| **Deployment Model** | Local host execution | **Multi-Stage Alpine Docker Container** | Cloud environments (Render) par filesystem loss se protection aur uniform Asia/Kolkata timezone support. |

---

## 🗄️ 2. Database Migration Journey (SQLite ➔ MySQL 8.0)

### Problem Architecture (Why SQLite Broke)

```text
               [ Client HTTP Traffic ]
                          │
                          ▼
            [ Tomcat Worker Thread Pool ]
                          │
         ┌────────────────┴────────────────┐
         ▼                                 ▼
[Thread A: Save Ticket]           [Thread B: Load User]
         │                                 │
         ▼                                 ▼
    [Takes Write Lock]            [Waits for Database]
         │                                 │
         ▼                                 ▼
🔒  [ SQLite Database File ] ◄─────────────┘ (Blocked!)
         │
         ▼ (Held > 3000ms)
    💥 [ HikariPool Connection Timeout: HTTP 500 ]

```

### Issues Diagnosed & Solved

* **HikariCP Connection Starvation:** SQLite locks the entire database file during writes. When filters (`GlobalUserControllerAdvice`) attempted concurrent reads, HikariCP exhausted its 30-second timeout, crashing sessions.
* **DDL Column Alteration Lockout:** Adding `email` and `mobile_number` columns failed because SQLite does not support `ALTER TABLE ADD COLUMN ... UNIQUE` on existing tables.
* **Ephemeral Cloud Loss:** Platforms like Render wipe container filesystems upon restart or sleep, permanently erasing an embedded `crm.db` file.

### Comparative Resolution Matrix

| Engineering Vector | Legacy SQLite 3 | Production MySQL 8.0 | Operational Impact |
| --- | --- | --- | --- |
| **Locking Granularity** | Full database file lock | **Row-level locking (InnoDB)** | Readers never block writers; zero lockouts under load |
| **Connection Pool Size** | Forced `pool-size = 1` | **`maximum-pool-size = 10`** | Multiple threads execute queries concurrently |
| **Schema Evolution** | Rejects `ALTER UNIQUE` | **Native DDL alter support** | Dynamic schema updates without dropping data |
| **Primary Key ID** | Implicit RowID sequence | **`GenerationType.IDENTITY`** | Native `AUTO_INCREMENT` primary keys |
| **Cloud Durability** | Local file wiped on restart | **External Cloud MySQL** | Persistent storage across all redeployments |

---

## 🛡️ 3. Concurrency Shield: Optimistic Locking

### The Concurrency Threat (Silent Write Overwrite)

```text
[Time: 10:00 AM] ──► Agent A loads TKT-001 (Version: 1)  ──────────────┐
                                                                         │
[Time: 10:01 AM] ──► Agent B loads TKT-001 (Version: 1)  ──────┐        │
                                                               │        │
[Time: 10:05 AM] ──► Agent A completes investigation:         │        │
                     Status: CLOSED                            │        │
                     Note: "Refund processed via gateway"      │        │
                     Database Commits ─────────────────────────┼────────┘
                     Status = CLOSED | Version = 2             │
                                                               │
[Time: 10:08 AM] ──► Agent B finishes typing (late):          │
                     Status: IN_PROGRESS                       │
                     Note: "Contacted bank partner"            │
                     Attempting Save ──────────────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
   WITHOUT Concurrency Shield            WITH Concurrency Shield
   ──────────────────────────            ───────────────────────
   ❌ Agent A's work is erased!          🛡️ Version mismatch caught!
   Status rewinds to IN_PROGRESS.        Write is blocked instantly.
   Refund audit notes are lost.          Alert: "Conflict Detected".

```

### Technical Solution: JPA `@Version`

```java
@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ticketId;

    // Concurrency Guard Token
    @Version
    @Column(nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketNote> notes = new ArrayList<>();
}

```

### Concurrency Guard Lifecycle

| Sequence Step | Workflow Stage | System Operation | State / Version Check |
| --- | --- | --- | --- |
| **01** | **Read Snapshot** | Agent opens ticket console (`/tickets/{id}`). Form embeds snapshot version token. | Form hidden field: `version = 1` |
| **02** | **First Commit** | Agent A submits resolution. Version matches DB state. | `WHERE id = ? AND version = 1` ➔ DB Version becomes `2` |
| **03** | **Stale Submission** | Agent B submits late with cached snapshot `version = 1`. | Hibernate executes: `UPDATE ... WHERE version = 1` |
| **04** | **Detection** | Zero rows updated because active DB version is already `2`. | Hibernate throws `OptimisticLockingFailureException` |
| **05** | **Shield Trigger** | Transaction rolls back immediately. Database write aborted. | UI serves Conflict Detected toast / API returns `HTTP 409` |

---

## 📧 4. Automated Email Notification Engine

```text
[Customer Ingestion Form] ──► [Ticket Generated: TKT-001] ──► [Receipt Email Dispatched]
                                                                        │
                                                                  (Agent Action)
                                                                        ▼
[Customer Inbox] ◄─── [Resolution Email Dispatched] ◄─── [Agent Closes Ticket]

```

### Dual-Stage Notification Pipeline

| Pipeline Stage | Operational Trigger | Customer Received Email Content |
| --- | --- | --- |
| **Stage 1: Acknowledgment** | Form submitted on `/portal/inquiry` | Confirmation of receipt, assigned Reference ID (`TKT-xxx`), and direct link to live tracking portal. |
| **Stage 2: Resolution Notice** | Agent marks status as `CLOSED` / `RESOLVED` | Final notification that case has been solved along with operational remarks. |

### Dynamic 12-Factor Configuration (`application.properties`)

```properties
# Gmail SMTP Gateway
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.connectiontimeout=5000
spring.mail.properties.mail.smtp.timeout=5000

```

---

## 📡 5. REST API Reference

| Method | Endpoint | Description | Response Status |
| --- | --- | --- | --- |
| `POST` | `/api/tickets` | Ingest ticket payload (Deduplication + Thread Appending) | `201 Created` / `200 OK` |
| `GET` | `/api/tickets` | Query ticket catalog with `?status=` and `?search=` filters | `200 OK` |
| `GET` | `/api/tickets/{ticket_id}` | Fetch ticket record with full chronological note timeline | `200 OK` |
| `PUT` | `/api/tickets/{ticket_id}` | Update lifecycle status with `@Version` concurrency verification | `200 OK` / `409 Conflict` |

---

## 🐳 6. Docker & Render Cloud Deployment

```dockerfile
# Stage 1: Build JAR using Java 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY support-crm/pom.xml .
RUN mvn dependency:go-offline -B

COPY support-crm/src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache tzdata
ENV TZ="Asia/Kolkata"

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Kolkata"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

```

### Deploying to Render Cloud

| Step | Action on Render Dashboard | Details |
| --- | --- | --- |
| **1** | **Create New Service** | Select **New Web Service** and authorize your GitHub repository. |
| **2** | **Select Runtime** | Choose **Docker** (Render auto-detects the root `Dockerfile`). |
| **3** | **Inject Environment Variables** | Add `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`. |
| **4** | **Trigger Deploy** | Click **Create Web Service**. Render compiles the container and exposes it over public HTTPS. |

---

## 💻 7. Local Installation & Setup

```powershell
# 1. Database Setup
# Run in MySQL: CREATE DATABASE crm_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 2. Set Local Env Variables
$env:DB_URL="jdbc:mysql://localhost:3306/crm_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="your_mysql_password"
$env:MAIL_USERNAME="your-email@gmail.com"
$env:MAIL_PASSWORD="your-16-character-app-password"

# 3. Build & Run
.\mvnw clean package -DskipTests
.\mvnw spring-boot:run

```

| Portal View | Access Endpoint | Access Permissions |
| --- | --- | --- |
| **Public Grievance Intake** | `http://localhost:8080/portal/inquiry` | Open to Public |
| **Ticket Status Tracking** | `http://localhost:8080/portal/track` | Reference ID Required |
| **Operations Agent Console** | `http://localhost:8080/tickets` | Authenticated (`admin` / `admin123`) |

---

## 📂 8. Project Structure

```text
support-desk-crm/
├── Dockerfile                         # Production multi-stage Alpine build
├── pom.xml                            # Java 21, MySQL, Spring Mail, JPA dependencies
├── src/main/java/com/support/crm/
│   ├── config/                        # Security, audit logging & web configuration
│   ├── controller/
│   │   ├── TicketRestController.java  # REST endpoints with HTTP 409 conflict handling
│   │   └── TicketWebController.java   # Thymeleaf UI controller & toast dispatcher
│   ├── model/
│   │   ├── Ticket.java                # Core ticket entity with JPA @Version shield
│   │   ├── TicketNote.java            # Relational chronological timeline notes
│   │   └── User.java                  # Agent auth profile entity
│   ├── repository/
│   │   ├── TicketRepository.java      # Custom JPQL query & search filters
│   │   └── UserRepository.java        # User data access
│   └── service/
│       ├── NotificationService.java   # Real-time Gmail SMTP notification engine
│       └── TicketService.java         # Ingestion, deduplication & lifecycle business logic
└── src/main/resources/
    ├── application.properties         # 12-Factor externalized environment bindings
    └── templates/                     # Bootstrap 5 responsive customer & agent views

```

---


## 🌐 Live Application & Endpoints

- **Base URL:** [https://support-desk-crm-qs00.onrender.com](https://support-desk-crm-qs00.onrender.com)
- **Tickets Dashboard:** [https://support-desk-crm-qs00.onrender.com/tickets](https://support-desk-crm-qs00.onrender.com/tickets)
- **Create New Ticket:** [https://support-desk-crm-qs00.onrender.com/tickets/new](https://support-desk-crm-qs00.onrender.com/tickets/new)
- **Customer Portal (Public Inquiries):** [https://support-desk-crm-qs00.onrender.com/portal/inquiry](https://support-desk-crm-qs00.onrender.com/portal/inquiry)
- **Ticket Tracking:** [https://support-desk-crm-qs00.onrender.com/portal/track](https://support-desk-crm-qs00.onrender.com/portal/track)
- **User Profile:** [https://support-desk-crm-qs00.onrender.com/profile](https://support-desk-crm-qs00.onrender.com/profile)

---

### 🔐 Authentication & Admin Endpoints

- **Login Page:** [https://support-desk-crm-qs00.onrender.com/login](https://support-desk-crm-qs00.onrender.com/login)
- **Sign Up / Registration:** [https://support-desk-crm-qs00.onrender.com/signup](https://support-desk-crm-qs00.onrender.com/signup)
- **Admin Dashboard:** [https://support-desk-crm-qs00.onrender.com/tickets/admin/dashboard](https://support-desk-crm-qs00.onrender.com/tickets/admin/dashboard)
- **Admin Ticket Management:** [https://support-desk-crm-qs00.onrender.com/tickets/admin/all-tickets](https://support-desk-crm-qs00.onrender.com/tickets/admin/all-tickets)

---

### 🔑 Demo / Admin Credentials

| Role | Username / Email | Password |
|---|---|---|
| **Admin** | `admin` *(or admin@example.com)* | `admin123` *(replace with your configured secret)* |
| **Agent / User** | Self-register at `/signup` | Configured during signup |

## 📄 License

This project is open-source and released under the [MIT License](https://www.google.com/search?q=LICENSE&utm_source=gemini).
'@ -Encoding utf8

