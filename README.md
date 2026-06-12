# 📊 Metrics Collection Service 📊
*The system is a Java server application that provides an HTTP API on a dedicated port. The service runs in the background on a schedule to collect metrics from servers (agents) and stores them in a time-series-oriented database. 
Clients can view the collected data through a separate, intuitive, and simplified interface.*

## Tech Task
See here [https://drive.google.com/file/d/18sSi5CrrTByBRD0VDkBOxKKv0bX2TIPe/view?usp=sharing](https://drive.google.com/file/d/18sSi5CrrTByBRD0VDkBOxKKv0bX2TIPe/view?usp=sharing)

## Features
- **Metrics Collection** — a scheduler polls all active agents at a configured interval to save the retrieved values. It supports various metric types: numeric (CPU %, RAM MB, latency ms), boolean (service status), and string (version, status).
- **Secure API** — a REST API on a dedicated port where all requests and responses are transmitted as custom binary packets, rather than plain JSON over HTTP.
- **Security** — all passwords are stored in a hashed format using SHA-512 and a salt. The transmission of sensitive data, such as tokens and passwords, is encrypted using AES/RSA for key exchange.
- **Data Management** — metrics are written to the database in a write-first (append-only) mode without updates. Separate background worker threads asynchronously process, index, and aggregate this data to form summary statistics.
- **Roles** — the system supports an "Admin" role with full access to all resources and agent management , as well as a "User" role limited to viewing aggregated metrics.

## Tech Stack
- **Language:** Java 17+
- **Database:** PostgreSQL
- **Connection pool:** HikariCP
- **Servlet container:** Jetty
- **Build tool:** Maven

## Project Structure
The project is divided into two Maven modules.

1. `core/` — the core module of the application containing common interfaces and utilities
2. `server/` — the main module for the metrics server

## Configuration
