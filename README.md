# FIX Protocol Acceptor Service

## Overview

This project implements a **FIX Protocol Acceptor** service using **QuickFIX/J**. It serves as a server-side application designed to accept FIX connections, handle incoming orders, and simulate execution reports.

The application is built to support **FIX 5.0** over **FIXT 1.1** sessions and includes automated simulation features for testing and development purposes.

## Features

- **FIX Session Support**: Accepts connections using `FIXT.1.1` (Session Layer) and `FIX.5.0` (Application Layer).
- **Environment Configuration**: Fully configurable via Environment Variables or a `.env` file, supporting both local development and containerized deployments.
- **Automated Simulation**: Automatically generates and sends `ExecutionReport` messages (Symbol: `AOT`) every 10 seconds to connected clients.
- **Docker Support**: Containerized with **Docker** and **Docker Compose** for easy deployment.

## Prerequisites

- **Java Development Kit (JDK)**: Version 17 or higher.
- **Maven**: Version 3.8.0 or higher.
- **Docker** & **Docker Compose** (Optional, for containerized execution).

## Configuration

The application configuration can be managed through **Environment Variables**. These can be defined in a `.env` file in the project root or passed directly to the Docker container.

| Variable Name                  | Description                                      | Default Value |
| ------------------------------ | ------------------------------------------------ | ------------- |
| `FIX_PORT`                     | The port the acceptor listens on.                | `9876`        |
| `SENDER_COMP_ID`               | The CompID of this server (Acceptor).            | `MY_SERVER`   |
| `TARGET_COMP_ID`               | The CompID of the connecting client (Initiator). | `MY_CLIENT`   |
| `BEGIN_STRING`                 | The FIX Session Protocol version.                | `FIXT.1.1`    |
| `DEFAULT_APPL_VER_ID`          | The FIX Application Protocol version.            | `FIX.5.0`     |
| `START_TIME`                   | Session start time (UTC).                        | `00:00:00`    |
| `END_TIME`                     | Session end time (UTC).                          | `00:00:00`    |
| `USE_DATA_DICTIONARY`          | Enable/Disable data dictionary validation.       | `Y`           |
| `VALIDATE_USER_DEFINED_FIELDS` | Validate user-defined fields (Tag > 5000).       | `N`           |

## Getting Started

### 1. Running Locally (Maven)

Ensure your `.env` file is properly configured in the project root (optional, defaults will be used if missing).

```bash
# Clean and Compile
mvn clean compile

# Run the Application
mvn exec:java -Dexec.mainClass="mfec.fixprotocol.acceptor.app.App"
```

### 2. Running with Docker Compose (Recommended)

The project includes a `docker-compose.yml` file for simplified deployment.

```bash
# Build and Run
docker-compose up --build
```

To modify configuration, edit the `environment` section in `docker-compose.yml` or your local `.env` file before running.

### 3. Building a Fat JAR

To package the application and all its dependencies into a single executable JAR file:

```bash
mvn clean package
```

The resulting artifact will be located at `target/fix.acceptor-1.0-SNAPSHOT.jar`.

## Project Structure

- `src/main/resources/acceptor.cfg`: QuickFIX/J configuration template.
- `src/main/resources/*.xml`: FIX Data Dictionaries.
- `Dockerfile`: Multi-stage Docker build configuration (JDK 17).
- `docker-compose.yml`: Definition for the Docker service environment.

## License

This project is proprietary software. Unauthorized copying of this file, via any medium, is strictly prohibited.
# fix-protocol-server
