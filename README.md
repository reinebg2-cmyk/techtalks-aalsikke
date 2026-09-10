# Aal Sikke

**TechTalks Summer Internship 2026 — Team Java D**

An adaptive management platform that organizes a user's Student and Work life in one place — one account, connected planning across both contexts.

## Team
- Reine Bou Ghanem
- Ashraf Rasbieh
- Yara Abou Ezzeddine
- Zeinab Kassir

**Mentor:** Khaled Frayji

## Concept
AAl sikke is designed as a shared-core platform that can activate different life-management contexts (Student, Work, and eventually Business, Traveler, Teacher, General User). For this internship, scope is intentionally narrowed to a focused MVP.

**Target segment:** University students who also work part-time or freelance.

**Positioning:** An adaptive management platform that organizes a user's personal, academic, professional, business, and travel life in one place — one user, multiple roles, one connected platform.

## Approved V1 Scope
- Secure authentication (each user sees only their own data)
- Student and Work as **planning contexts** within one account (not separate permission roles)
- Task categories with deadlines, priorities, completion status, and reminders
- One unified daily/weekly dashboard and calendar
- Scheduling-conflict warnings (triggered by commitments with start/end times, including scheduled task blocks — not by deadlines alone)
- Simple weekly review
- In-app reminders shown on app open (external notifications deferred)

**Explicitly excluded from V1:** profession-specific modules, finance management, document storage, AI features, payments, full offline sync, business-owner tools, travel planning.

## Tech Stack
- **Backend:** Java, Spring Boot, Spring Security, JWT authentication, role-based authorization
- **API:** REST
- **Database:** MySQL/PostgreSQL (team to choose one), Spring Data JPA, Hibernate
- **Other:** File upload/management, scheduled tasks (planned for later phases)

## Differentiation
Competitor products (e.g. Tarteeb) already offer many life categories, calendars, and offline support. Aal Sikke's differentiation is not module count but **connected cross-role planning** — a clearer, more coherent experience specifically for working students juggling both academic and job commitments in one view.

## Process & Status
- Assignment 1 (Product Discovery & Market Validation) submitted, revised per mentor feedback, and **approved for implementation**.
- Still required alongside development: ~5 focused interviews with the target segment, and a tested clickable Student+Work flow with 10 target users (anonymized notes + a brief summary of findings, no recordings needed).
- Team workflow: shared GitHub repo + Jira Scrum. Sprint 1 focuses on adding Student and Work commitments and viewing them together.
- Each team member should have a specific assigned contribution (tracked in project docs, not "All Tasks" for everyone).

