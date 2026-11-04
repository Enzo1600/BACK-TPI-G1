# AuxiRuta — Diagramas C1 y C2

Línea base AR-MVP-5, 4 de noviembre de 2026. Fuente vigente del diseño; implementación pendiente. Los archivos AuxiRuta_C1.png y AuxiRuta_C2.png son antecedentes históricos y quedan sustituidos por estos diagramas.

## C1 — Contexto

```mermaid
flowchart LR
    D[Conductor / DRIVER] -->|Vehículos y solicitudes propias| S[AuxiRuta]
    O[Operador / OPERATOR] -->|Propuestas y etapas propias| S
    A[Administrador / ADMIN] -->|Administración de recursos y consulta global| S
    S -->|Identidad OIDC| K[Keycloak]
    S -->|Distancias y tiempos por calles| R[OpenRouteService / API externa]
```

La selección, los vencimientos y las búsquedas alternativas previas al despacho se deciden mediante reglas automáticas. ADMIN administra recursos y consulta; el alta de operadores pertenece a la empresa.

## C2 — Contenedores

```mermaid
flowchart TB
    U[DRIVER / OPERATOR / ADMIN] -->|REST y bearer JWT| G[API Gateway / WebFlux :8000]
    U -->|Autenticación OIDC| K[Keycloak :8080]
    G -->|HTTP / JWT humano| A[assistance-service / MVC :8082]
    G -->|HTTP / JWT humano| F[fleet-service / MVC :8081]
    A -->|REST interno / client credentials| F
    A -->|HTTPS / API key| R[OpenRouteService externo]
    G -.->|Validación JWT| K
    A -.->|Validación JWT y token técnico| K
    F -.->|Validación JWT| K
    subgraph P[PostgreSQL / un contenedor :5432]
      AD[(assistance_db)]
      FD[(fleet_db)]
      KD[(keycloak_db)]
    end
    A --> AD
    F --> FD
    K --> KD
    C[auxiruta-commons / JAR Maven] -.->|Dependencia| A
    C -.->|Dependencia| F
```

Assistance administra vehículos, solicitudes, evaluación, selección, estados y tareas de recuperación. Fleet administra operadores, equipos y reservas exclusivas. El JAR común es una dependencia, no un contenedor. Solo Gateway y Keycloak publican puertos al host. No existe una base adicional para logs ni notificaciones.

Desde DISPATCHED se mantiene la asignación hasta la entrega.
