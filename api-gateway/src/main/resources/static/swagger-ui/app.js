"use strict";

const form = document.getElementById("credentials");
const tokenInput = document.getElementById("token");
const serviceInput = document.getElementById("service");
const statusElement = document.getElementById("status");
let generation = 0;

document.getElementById("clear").addEventListener("click", () => {
  generation++;
  tokenInput.value = "";
  document.getElementById("swagger-ui").replaceChildren();
  statusElement.textContent = "Acceso cerrado.";
});

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const current = ++generation;
  const token = tokenInput.value.trim().replace(/^Bearer\s+/i, "");
  document.getElementById("swagger-ui").replaceChildren();
  statusElement.textContent = "Cargando contrato...";
  try {
    const response = await fetch("/docs/" + serviceInput.value + "/v3/api-docs", {
      headers: { Authorization: "Bearer " + token },
      cache: "no-store"
    });
    if (!response.ok) {
      throw new Error("HTTP " + response.status + ". Revisá la vigencia y el rol del token.");
    }
    const spec = await response.json();
    if (current !== generation) return;
    spec.servers = [{ url: window.location.origin }];
    const ui = SwaggerUIBundle({
      spec,
      dom_id: "#swagger-ui",
      persistAuthorization: false,
      validatorUrl: null,
      onComplete: () => {
        if (current === generation) ui.preauthorizeApiKey("bearerAuth", token);
      },
      requestInterceptor: (request) => {
        const target = new URL(request.url, window.location.origin);
        if (current !== generation || target.origin !== window.location.origin) {
          throw new Error("Cargá nuevamente la API para realizar esta petición.");
        }

        return request;
      }
    });
    statusElement.textContent = "API cargada. Usá Try it out y Execute. Las operaciones modifican los datos reales.";
  } catch (error) {
    if (current === generation) statusElement.textContent = error.message;
  }
});

