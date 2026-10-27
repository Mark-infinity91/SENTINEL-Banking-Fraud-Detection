# SENTINEL — Banking Fraud Detection System

SENTINEL is a Java-based banking demonstration application featuring account transfers, configurable fraud detection rules, investigator alerts, and transaction recovery handling.

## Features

- Customer and investigator interfaces
- Banking transaction processing
- Configurable fraud detection rules
- High-amount transaction detection
- Repeated-small-transfer detection
- Fraud alert generation and risk scoring
- Transfer rollback and recovery handling
- JDBC database connectivity with MySQL

## Technology Stack

- Java
- Java Swing
- MySQL
- JDBC
- MySQL Connector/J

## Project Structure

- `src/` — Java application source files and tests
- `lib/` — External dependencies
- `bin/` — Default compiled output directory
- `buildcheck_final/` — Verification build output

## Database Configuration

Configure these environment variables before running the application:

- `SENTINEL_DB_URL`
- `SENTINEL_DB_USER`
- `SENTINEL_DB_PASSWORD`

For local testing, the approved database URL is:

`jdbc:mysql://localhost:3306/sentinel_test_db`

Use the database guard already implemented in the source code. Do not point rollback or fraud tests at a production database.

## Build

From the project root in Windows Command Prompt:

```bat
javac -Xlint:all -cp "lib\mysql-connector-j-26.7.0.jar" -d buildcheck_final src\*.java
```

## Run Tests

After compiling, run individual tests using:

```bat
java -cp "buildcheck_final;lib\mysql-connector-j-26.7.0.jar" FraudEngineRollbackTest
java -cp "buildcheck_final;lib\mysql-connector-j-26.7.0.jar" RepeatedSmallTransfersTest
java -cp "buildcheck_final;lib\mysql-connector-j-26.7.0.jar" TransferRollbackTest
java -cp "buildcheck_final;lib\mysql-connector-j-26.7.0.jar" TransferRecoveryLogicTest
java -cp "buildcheck_final;lib\mysql-connector-j-26.7.0.jar" TransferCommittedRecoveryTest
java -cp "buildcheck_final;lib\mysql-connector-j-26.7.0.jar" TransferUnresolvedRecoveryTest
```

These are separate test runs, not a single automated test suite.

## Current Verification Status

The application compiles successfully. Fraud detection, rollback, and transfer recovery tests have passed in the local test environment. Compilation currently reports warnings that should be reviewed separately.

## Important Notes

- This is a demonstration project, not a certified production banking system.
- Run database tests only against the approved local test database.
- Review configuration, access controls, transaction handling, and deployment security before any production use.
