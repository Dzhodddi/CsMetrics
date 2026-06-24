# 📊 Metrics Collection Service 📊
*The system is a Java server application that provides an HTTP API on a dedicated port and encrypted packages TCP exchange in the background. The service runs in the background on a schedule to collect metrics from servers (agents) and stores them in a time-series-oriented database. 
Clients can view the collected data through a separate, intuitive, and simplified interface.*

## Tech Task
See here [https://drive.google.com/file/d/18sSi5CrrTByBRD0VDkBOxKKv0bX2TIPe/view?usp=sharing](https://drive.google.com/file/d/18sSi5CrrTByBRD0VDkBOxKKv0bX2TIPe/view?usp=sharing)

## Features
- **Metrics Collection** — a planner polls all recorded metrics at a configured recording interval to save the retrieved values. It supports DB and HTTP API metric types.
- **Secure API** — a REST API on a dedicated port where all requests and responses are transmitted as custom binary packets, rather than plain JSON over HTTP.
- **Security** — all passwords are stored in a hashed format using SHA-512 and a salt. The performance of sensitive data such as card number and CVV is encrypted using AES for untrusted readers.
- **Data Management** — metrics are written to the database in a write-first (append-only) mode without updates.
- **Roles** — the system supports an "Admin" role with full access to all resources and agent management, as well as a "Reader" role limited to exploring metrics in table and graphical view.

## Tech Stack
- **Language:** Java 17+
- **Database:** PostgreSQL
- **Connection pool:** HikariCP
- **Build tool:** Maven

## Project Structure
The project is divided into three Maven modules.

1. `core/` — core module of the application focused on metrics collection and encrypted packages TCP exchange
2. `server/` — main module for all endpoints (authorization, secure cards, users, metrics data) and communication with frontend
3. `db/` — module for database boot

## Configuration
Set up your docker and use `docker compose up --build` command to start the process.
