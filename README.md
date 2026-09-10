<div align="center">

# 🛡️ SupportDesk CRM

### Enterprise Customer Ingestion & Concurrency-Controlled Support Pipeline

[![Java 17](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot 3](https://img.shields.io/badge/Spring_Boot-3.2-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![SQLite](https://img.shields.io/badge/SQLite-3-003B57?style=for-the-badge&logo=sqlite&logoColor=white)](https://www.sqlite.org/)
[![Bootstrap 5](https://img.shields.io/badge/Bootstrap-5.3-7952B3?style=for-the-badge&logo=bootstrap&logoColor=white)](https://getbootstrap.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

<p align="center">
  <b>A production-grade, multi-channel support desk designed to eliminate race conditions and filter ticket redundancy.</b>
</p>

[Explore REST API](#-rest-api-reference) • [Architecture Highlights](#-key-architectural-decisions) • [Quickstart](#-local-installation--setup) • [Repository](https://github.com/silently-feel/support-desk-crm)

</div>

---

## 📌 Executive Summary

SupportDesk CRM is an enterprise-oriented ticketing and triage engine built on **Spring Boot 3** and **SQLite**. It
addresses the two most common operational pitfalls in high-throughput customer service portals: **silent write
overwrites between concurrent agents** and **database spam caused by impatient duplicate customer inquiries**.

### 🔄 Ingestion & Triage Architecture

| Pipeline Stage                | Detection Rule                                    | Engine Action                                        | Operational Result                                        |
|:------------------------------|:--------------------------------------------------|:-----------------------------------------------------|:----------------------------------------------------------|
| **📥 Stage 1: Intake**        | Incoming customer payload                         | Parse `customer_email`, `subject`, `description`     | Ingestion pipeline initialized                            |
| **♻️ Stage 2: Deduplication** | Match on `email` + `subject` + `description`      | Idempotent drop of duplicate write                   | Returns existing `ticket_id` (zero DB bloat)              |
| **📝 Stage 3: Follow-Up**     | Match on `email` + `subject` with revised details | Intercept active ticket and append to notes timeline | Preserves single thread context without splitting tickets |
| **✨ Stage 4: Provisioning**   | New email OR distinct subject query               | Auto-increment sequence counter (`TKT-001` format)   | New active ticket routed to triage queue                  |
| **🛡️ Operations Shield**     | Simultaneous agent status & note updates          | JPA `@Version` validation check                      | Throws `409 Conflict` / red toast on stale write          |

---

## ⚡ Key Architectural Decisions

### 1. Concurrency Shield (Optimistic Locking)

> [!IMPORTANT]  
> In high-concurrency environments, multiple support agents can open the same ticket at identical timestamps. Without
> locking, **Agent B's late submission silently erases Agent A's investigation notes**.

* **Implementation:** Annotated the `Ticket` domain model with JPA `@Version private Long version;`.
* **Conflict Handling (Web UI):** When an agent attempts to persist a change with a stale version, the service catches
  `OptimisticLockingFailureException` and serves a dynamic, floating <kbd>Conflict Detected</kbd> red toast warning
  without clobbering existing notes.
* **Conflict Handling (REST API):** Dispatches standard `HTTP 409 Conflict` containing JSON diagnostic telemetry.

---

### 2. Triaging & Smart Deduplication Pipeline

> [!TIP]  
> Instead of rejecting submissions or creating duplicate noise, the ingestion logic categorizes incoming payloads into
> three distinct operations:

| Submission Type        | Detection Condition                                                 | Action Taken                                                                      | UI / API Status           |
|:-----------------------|:--------------------------------------------------------------------|:----------------------------------------------------------------------------------|:--------------------------|
| **Exact Duplicate**    | Match on `email` + `subject` + `description` on non-`CLOSED` ticket | Silently drops duplicate write; re-links customer to existing ID                  | `200 OK` (Reused ID)      |
| **Customer Follow-up** | Match on `email` + `subject`, but **new description text**          | Injects new text as an internal note: `[Customer Follow-up / Additional Details]` | `200 OK` (Appended)       |
| **Brand New Issue**    | New `email` OR distinct `subject`                                   | Auto-increments sequence counter and generates new ticket                         | `201 Created` (`TKT-xxx`) |

---

### 3. Real-Time SLA Triage Sorting

The console supports instant, client-side triage ordering without hitting the SQLite persistence layer:

* <kbd>FIFO Queue</kbd>: Renders tickets in absolute chronological order of receipt (oldest first) to enforce SLA
  targets.
* <kbd>Newest First</kbd>: Monitors immediate incoming support traffic.
* <kbd>Urgency / Active Stack</kbd>: Floats pending inquiries (`OPEN` $\rightarrow$ `IN_PROGRESS`) to the top while
  pushing resolved (`CLOSED`) cases down.

---

## 🛠️ Technology Matrix

| Layer                | Component                        | Description                                                               |
|:---------------------|:---------------------------------|:--------------------------------------------------------------------------|
| **Runtime & Core**   | `OpenJDK 17` + `Spring Boot 3.x` | Enterprise application backbone with zero-configuration embedded runtime  |
| **Data Access**      | `Spring Data JPA` / `Hibernate`  | Object-relational mapping, custom JPQL filters, and `@Version` management |
| **Persistence**      | `SQLite 3` via Community Dialect | Lightweight zero-config embedded storage; isolated per deployment         |
| **Presentation**     | `Thymeleaf` + `Bootstrap 5.3`    | Responsive server-side template engine with instant DOM filter scripts    |
| **Input Validation** | `Jakarta Validation`             | `@NotBlank`, `@Email`, and custom boundary constraint triggers            |

---

## 📡 REST API Reference

### Endpoints Overview

```http
POST   /api/tickets              # Ingest ticket or append follow-up
GET    /api/tickets              # Query catalog with ?status= & ?search=
GET    /api/tickets/{ticket_id}  # Read ticket details + chronological notes
PUT    /api/tickets/{ticket_id}  # Update lifecycle status & version verification

Ingest Ticket (POST /api/tickets)
Request:

JSON
{
  "customer_name": "Aarav Sharma",
  "customer_email": "aarav.sharma@payquick.in",
  "subject": "UPI Autopay mandate settlement discrepancy",
  "description": "Customer UPI mandate debited INR 1499 via NPCI switch, but dashboard shows pending."
}
Response (201 Created or 200 OK):

JSON
{
  "ticket_id": "TKT-001",
  "created_at": "2026-09-11T00:15:00"
}
Update Status with Concurrency Guard (PUT /api/tickets/{ticket_id})
Request:

JSON
{
  "status": "IN_PROGRESS",
  "notes": "Verified against NPCI switch logs. Re-initiating batch settlement.",
  "version": 1
}
Response (200 OK):

JSON
{
  "success": true,
  "version": 2,
  "updated_at": "2026-09-11T00:30:00"
}
[!WARNING]

If the version sent does not match the active entity version in the database, the server prevents the write and returns:

JSON
{
  "error": "Conflict",
  "message": "Ticket has been modified by another user. Please refresh and review changes."
}
💻 Local Installation & Setup
Prerequisites
JDK 17 or higher installed (java -version)

Maven 3.8+ (or use the built-in Maven wrapper)

Step-by-Step Execution
Clone the Repository:

Bash
git clone https://github.com/silently-feel/support-desk-crm.git
cd support-desk-crm
Compile and Verify Dependencies:

Bash
mvn clean install -DskipTests
Start the Application:

Bash
mvn spring-boot:run
Access the Web Console:
Open your browser to: http://localhost:8080/tickets

🧪 Seeding Realistic Indian Enterprise Data
To populate your local instance with unique enterprise scenarios (covering UPI reconciliation, TRAI DLT template failures, GST e-invoices, and DigiLocker timeouts), run this in PowerShell:

PowerShell
$tickets = @(
    @{n="Aarav Sharma"; e="aarav.sharma@payquick.in"; s="UPI Autopay mandate settlement discrepancy"; d="Customer UPI mandate debited INR 1499 via NPCI switch, but our merchant dashboard still reflects status as pending settlement."},
    @{n="Pooja Deshmukh"; e="pooja@deshmukhenterprises.co.in"; s="Missing GSTIN on Tax Invoice #INV-2026-901"; d="Our corporate GSTIN 27AAACD4589F1Z5 was omitted from the August quarterly software invoice. Please reissue the GSTR-1 compliant invoice."},
    @{n="Vikramaditya Iyer"; e="v.iyer@chennaifinance.org"; s="OTP delivery failure due to TRAI DLT template mismatch"; d="Critical login OTP SMS to Jio and Airtel subscribers are dropping with error code DLT-1004. Please check registered headers."}
)


foreach ($t in $tickets) {$body = @{ customer_name = $t.n; customer_email =$t.e; subject = $t.s; description =$t.d } | ConvertTo-Json -Compress
    Invoke-RestMethod -Uri "http://localhost:8080/api/tickets" -Method Post -ContentType "application/json" -Body $body
}


📂 Project Structure
support-desk-crm/
├── src/main/java/com/support/crm/
│   ├── controller/
│   │   ├── TicketRestController.java    # REST API endpoints with HTTP 409 conflict handling
│   │   └── TicketWebController.java     # Thymeleaf dashboard & toast alert management
│   ├── dto/
│   │   ├── CreateTicketRequest.java     # Input validation payload
│   │   ├── CreateTicketResponse.java    # Standardized ticket reference
│   │   ├── TicketDetailResponse.java    # Complete chronological view with entity version
│   │   ├── TicketSummaryResponse.java   # Lightweight row projection for dashboard
│   │   └── UpdateTicketRequest.java     # Form payload binding status, notes, and version
│   ├── model/
│   │   ├── Note.java                    # Chronological audit notes mapped to ticket
│   │   ├── Ticket.java                  # Core entity decorated with JPA @Version
│   │   └── TicketStatus.java            # Strict lifecycle state enum [OPEN, IN_PROGRESS, CLOSED]
│   ├── repository/
│   │   ├── NoteRepository.java          # JPA data interface for notes
│   │   └── TicketRepository.java        # Custom JPQL search & active inquiry queries
│   └── service/
│       └── TicketService.java           # Deduplication heuristics & concurrency validation
└── src/main/resources/
    ├── templates/tickets/
    │   ├── dashboard.html               # Main support console with search & sorting
    │   ├── detail.html                  # Conversation timeline & status editor
    │   └── create.html                  # Clean intake form
    └── application.properties           # SQLite connection & logging configuration