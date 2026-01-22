# MediDrone Delivery System

A drone-based medication delivery system with live tracking and automated pathfinding.

Created as part of Coursework 3 - Student Selected Implementation for Informatics Large Practical. 

## Overview
This project extends the ILP drone delivery service with **real-time tracking for medication deliveries** in Edinburgh.  
Key features:
- Users can request medication delivery to any Edinburgh address.
- Live drone visualization on a map with real-time path updates.
- Collection notifications when the drone arrives.
- Designed for residents with limited access to pharmacies.

## Innovation & Benefits
- **Live Tracking:** Drone position updates every 250ms via WebSocket.
- **Path Visualization:** Completed path in grey, remaining path in blue.
- **Automated Medication Handling:** Checks medication temperature requirements in the H2 database.
- **Event-Driven System:** Real-time status updates (QUEUED → IN-TRANSIT → ARRIVED → COLLECTED → COMPLETED).

The system demonstrates a **proof-of-concept real-time drone delivery application** with a complete customer interface and automated order processing.

### Future Enhancements
1. Multi-drone support.
2. Persistent database storage.
3. User authentication.
4. Realistic drone physics (battery, altitude, weather conditions).


## Installation Instructions 

## Prerequisites

- **Java 21** or higher
- **Maven 3.6+**
- **Google Maps API Key** (for geocoding)

## Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd cw3-ilp
```

### 2. Configure Google Maps API Key

Set up a Google Maps API key, ensuring geocoding is enabled. 
Set your Google Maps API key as an environment variable:

**Windows (Command Prompt):**
```cmd
set GOOGLE_MAPS_API_KEY=your_api_key_here
```

**Windows (PowerShell):**
```powershell
$env:GOOGLE_MAPS_API_KEY="your_api_key_here"
```

**macOS/Linux:**
```bash
export GOOGLE_MAPS_API_KEY=your_api_key_here
```

Alternatively, create a `.env` file in the project root:
```
GOOGLE_MAPS_API_KEY=your_api_key_here
```

### 3. Build the Project

```bash
mvn clean install
```

## Running the Application

### Option 1: Using Maven

```bash
mvn spring-boot:run
```

### Option 2: Using JAR

```bash
mvn clean package
java -jar target/cw1-ILP-0.0.1-SNAPSHOT.jar
```

## Accessing the Application

Once running, open your browser and navigate to:

- **Customer Portal**: http://localhost:8080/customer-portal.html

## Optional: Enable H2 Console (Development Only)

To view the in-memory database during development:

```bash
# Windows (Command Prompt)
set H2_CONSOLE_ENABLED=true

# Windows (PowerShell)
$env:H2_CONSOLE_ENABLED="true"

# macOS/Linux
export H2_CONSOLE_ENABLED=true
```

Then access the console at: http://localhost:8080/h2-console

**Connection settings:**
- JDBC URL: `jdbc:h2:mem:medidrone`
- Username: `sa`
- Password: (leave blank)

## Features

- Real-time drone tracking 
- Automated pathfinding, which avoids restricted areas
- Service point selection based on proximity
- Live map visualization with Leaflet.js
- Order queue processing system
- Medication inventory management

## Technology Stack

- **Backend**: Spring Boot 3.5.6, Java 21
- **Database**: H2 (in-memory)
- **Frontend**: Vanilla JavaScript, Leaflet.js
- **APIs**: Google Maps Geocoding API
- **Build Tool**: Maven

## Troubleshooting

### Port Already in Use

If port 8080 is already in use, specify a different port:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

### Google Maps API Errors

Ensure your API key has the following APIs enabled:
- Geocoding API
- Maps JavaScript API

### Database Issues

The H2 database is recreated on each startup. If you need persistence, modify `application.properties`:
```properties
spring.jpa.hibernate.ddl-auto=update
```
