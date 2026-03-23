# Thank You Board

[![GitHub release](https://img.shields.io/github/v/release/bbruneel/ThankYouBoard?include_prereleases&sort=semver)](https://github.com/bbruneel/ThankYouBoard/releases)
[![Build status](https://github.com/bbruneel/ThankYouBoard/actions/workflows/build.yml/badge.svg)](https://github.com/bbruneel/ThankYouBoard/actions/workflows/build.yml)
[![Codecov](https://codecov.io/gh/bbruneel/ThankYouBoard/graph/badge.svg)](https://codecov.io/gh/bbruneel/ThankYouBoard)
[![Contributors](https://img.shields.io/github/contributors/bbruneel/ThankYouBoard)](https://github.com/bbruneel/ThankYouBoard/graphs/contributors)

A modern, full-stack application for creating and sharing "Thank You" message boards. 
Built with a **Java 25 + Spring Boot 4** or a feature parity **Java 25 AWS Serverless** backend and a **React 19 + Vite 7** frontend.

---

## 🚀 Features

- **Personalized Message Boards**: Authenticated users can create and manage their own message boards.
- **Anonymous Posting**: Anyone with the board link can read and add posts anonymously.
- **Rich Media**: Supports text messages, user-uploaded images, and Giphy GIF integration.
- **PDF Export**: Generate high-quality PDF exports of message boards.
- **Modern UI**: A responsive, premium interface with light/dark theme support and smooth micro-interactions.
- **Dual Backend Support**: Run as a traditional Spring Boot application or deploy as serverless AWS Lambda functions.

---

## 🛠 Tech Stack

| Component | Technology |
|-----------|------------|
| **Frontend** | React 19, Vite 7, TypeScript |
| **Backend** | Java 25, Spring Boot 4.0.x |
| **Database** | PostgreSQL, Hibernate ORM 7.2, Flyway |
| **Security** | Auth0 (JWT) |
| **Infrastructure**| AWS CDK (App Runner / Lambda + DynamoDB) |

---

## 🏁 Quick Start

### Prerequisites
- **Java 25** and **Maven 3.9+**
- **Node.js** and **npm**
- **Auth0 Tenant** (for authentication): https://auth0.com/docs/get-started
- **PostgreSQL Database** (e.g., [Supabase](https://supabase.com) — *Note: Free tier requires IPv6 support*)
- *(Optional)* **Giphy API Key** (for GIF search): https://developers.giphy.com/docs/api/
- *(Optional)* **Grafana Cloud** (for traces, metrics, and logs): https://grafana.com/docs/grafana-cloud/otlp/

### 1. Backend Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/bbruneel/ThankYouBoard.git
   cd ThankYouBoard
   ```
2. Configure your environment by creating `src/main/resources/application-secret.properties`:
   ```properties
   spring.datasource.password=your-postgres-password
   AUTH0_DOMAIN=your-tenant.us.auth0.com
   AUTH0_AUDIENCE=urn:your-api
   
   # Optional configurations
   # GIPHY_API_KEY=your-giphy-key
   # GRAFANA_OTLP_HEADERS=Basic <your-base64-credentials>
   ```
3. Start the server:
   ```bash
   mvn spring-boot:run
   ```
   *The backend will be available at `http://localhost:8080`.*

### 2. Frontend Setup
1. Open a new terminal and navigate to the `frontend` folder:
   ```bash
   cd frontend
   npm install
   ```
2. Configure frontend environment variables by creating `.env.local` in the `frontend` directory:
   ```env
   VITE_AUTH0_DOMAIN=your-tenant.us.auth0.com
   VITE_AUTH0_CLIENT_ID=your-auth0-client-id
   VITE_AUTH0_AUDIENCE=urn:your-api
   ```
3. Start the Vite dev server:
   ```bash
   npm run dev
   ```
   *The application will be available at `http://localhost:5173`.*

---

## 🧪 Testing

- **Backend (JUnit 5 + H2):** 
  ```bash
  mvn test
  ```
- **Frontend (Playwright E2E):** 
  Start the backend with the E2E profile:
  ```bash
  mvn spring-boot:run -Pe2e -Dspring-boot.run.profiles=e2e
  ```
  Then run the tests in the frontend directory:
  ```bash
  cd frontend
  npm run test:e2e
  ```

---

## 📖 Documentation & Resources

For detailed information on the system architecture, API endpoints, AWS infrastructure, deployment instructions, and configuration limits please refer to:
- [`CONFIGURATION.md`](CONFIGURATION.md) - Full reference for environment variables and application limits.
- [`AGENTS.md`](AGENTS.md) - General architecture, local development guidelines, and UUID identifier policy.
- [`DEPLOYMENT.md`](DEPLOYMENT.md) - AWS deployment configuration using CDK.

---

## 📄 License
See repository license information.
