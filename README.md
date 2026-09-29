# DomainProbe

DomainProbe is a web application for investigating a domain's registration, DNS, network, email, and website security configuration. Enter a domain or URL to generate a structured report, review the findings in the browser, and export the report as a PDF. An optional AI Probe explains the report in plain language and gives section-by-section recommendations.

## What it checks

- **Domain registration:** RDAP registration details, registrar, dates, and availability information.
- **DNS:** A, AAAA, CNAME, MX, NS, TXT, CAA, and SOA records.
- **IP and ASN:** network and organization information for resolved IP addresses.
- **DNSSEC:** DNSSEC records and configuration details.
- **Email authentication:** DMARC policy information.
- **SSL and HTTPS:** certificate validity and coverage, HTTPS response, HSTS, and HTTP-to-HTTPS behavior.
- **HTTP and website:** response details, redirects, security headers, and cookies.
- **Technology detection:** technologies inferred from the website response.
- **AI Probe:** an optional AI-generated summary, findings, and recommendations based on the collected report.

The report page supports copying individual result cards and downloading a PDF. A domain can be shared using the `?domain=example.com` query parameter.

## How it works

The React frontend sends a domain to the Spring Boot API. The backend normalizes and validates the input, then gathers data from RDAP, Google DNS, IPinfo, and the target website. Individual lookups may fail independently, so a report can still include the sections whose data was available. When requested, the AI endpoint sends the report to Groq for a plain-language analysis.

```text
React + Vite frontend
        | HTTP / JSON
        v
Spring Boot REST API
   |-- RDAP
   |-- Google Public DNS
   |-- IPinfo
   |-- Domain website (HTTP / TLS)
   `-- Groq (optional AI Probe)
```

## Technology

| Area | Technologies |
| --- | --- |
| Frontend | React, Vite, JavaScript, jsPDF |
| Backend | Java 17, Spring Boot, Spring WebMVC, WebFlux, Jakarta Validation, Maven |
| External data | IANA RDAP bootstrap, Google Public DNS, IPinfo |
| AI analysis | Groq API |

## Repository layout

```text
domain-probe/
├── backend/domainprobe/       # Spring Boot API
│   ├── src/main/java/         # Controllers, services, DTOs, and configuration
│   └── src/main/resources/    # Spring configuration
└── frontend/domain-probe/     # React + Vite web application
    └── src/                   # UI, API client, and PDF report generation
```

## Requirements

- Java 17 or later
- Maven
- Node.js and npm
- An IPinfo token for IP and ASN lookups
- A Groq API key to use AI Probe

## Run locally

Start the backend first. From `domain-probe/backend/domainprobe`, configure the environment variables below and run:

```bash
mvn spring-boot:run
```

The API listens on `http://localhost:8080` by default. The frontend currently calls this address directly.

In another terminal, start the frontend:

```bash
cd domain-probe/frontend/domain-probe
npm install
npm run dev
```

Open the local URL printed by Vite in your browser. For a production frontend bundle, run `npm run build`; `npm run preview` serves the built bundle locally.

### Backend configuration

Set credentials as environment variables rather than placing secrets in source-controlled configuration:

| Variable | Purpose |
| --- | --- |
| `IPINFO_TOKEN` | IPinfo lookups for IP and ASN details |
| `GROQ_API_KEY` | AI Probe generation |
| `GEMINI_API_KEY` | Gemini configuration value; the current AI Probe service uses Groq |
| `RDAP_BOOTSTRAP_URL` | Optional RDAP bootstrap endpoint; defaults to IANA's DNS bootstrap file |
| `RDAP_TIMEOUT` | Optional RDAP request timeout; defaults to `5s` |
| `DNS_BASE_URL` | Optional DNS-over-HTTPS endpoint; defaults to `https://dns.google/resolve` |
| `SSL_TIMEOUT` | Optional SSL lookup timeout; defaults to `5s` |

Example in PowerShell (set real credentials in your own terminal):

```powershell
$env:IPINFO_TOKEN = "your-ipinfo-token"
$env:GROQ_API_KEY = "your-groq-api-key"
mvn spring-boot:run
```

The backend configuration resolves these variables through `application.properties`. Do not commit API keys or other secrets.

## API

### Analyze a domain

```http
GET /api/v1/domains/analyze?domainName=example.com
```

Returns a JSON envelope containing the report, including the normalized domain, analysis timestamp, and available result sections.

### Generate an AI Probe

```http
POST /api/v1/ai/probe
Content-Type: application/json
```

Send the domain report returned by the analyze endpoint as the request body. The response contains the AI summary, overall status, section findings, and recommendations.

## Notes

- DomainProbe reports publicly observable data at the time of analysis. External services can rate-limit requests or return incomplete results, and DNS or registration data may change.
- AI Probe depends on Groq credentials and service availability. The rest of the report can be used without generating an AI analysis.
- The AI explanation is informational. Review important security or registration findings with the relevant service provider or a qualified professional.
