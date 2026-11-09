# Auditoría de conformidad de AuxiRuta

Fecha: 9 de noviembre de 2026. Estado actual: hallazgos H1–H4 atendidos conforme a las decisiones posteriores y regresión conjunta aprobada. Véase el cierre al final del documento. Los hallazgos y conclusiones iniciales se conservan como registro histórico del estado previo a las correcciones.

## Método y límites

Se contrastaron las decisiones finales del chat y DECISIONES_ARQUITECTONICAS.md (AR-MVP-5, secciones 5.1–5.11) con evaluación, propuestas, confirmación, reconciliación, ejecución, persistencia, seguridad y material de entrega. Se ejecutó nuevamente Verify-AuxiRuta.ps1 sin integración: 99 pruebas aisladas aprobadas (12 commons, 24 Fleet, 62 Assistance, 1 artefactos). Las 24 pruebas optativas de Gateway figuran omitidas, no aprobadas en esta ejecución. Los resultados integrales de fase 10 son antecedentes; no se repitieron en esta auditoría.

Los hallazgos se establecen por inspección de las condiciones y llamadas del código. Los escenarios siguientes describen reproducciones que deben incorporarse como pruebas; no se presentan como pruebas nuevas ya ejecutadas. No se releyeron los PDF originales de la cátedra: la comprobación curricular utiliza los requisitos transcritos en el chat y la documentación consolidada. No constituye una garantía de ausencia de otros defectos.

## Hallazgos

### H1 — Alta: se permite repetir recursos fallidos al cambiar de intento

La sección 5.6 prohíbe volver a ofrecer una pareja grúa/operador rechazada, vencida o fallida en esa solicitud. AssignmentStore.java:41 limita la exclusión por search_attempt_number y guarda solo tow_truck_id. Al incrementar el intento se olvida la exclusión; además se distingue por grúa, no por pareja.

Escenario: una pareja rechaza una propuesta en el intento 1; otra acepta y luego vence su despacho; en el intento 2, la primera pareja vuelve a ser seleccionable. También puede volver a ofrecerse el mismo recurso cuyo despacho venció. Phase9IntegrationTest.twoReassignmentsThenCloseWithoutRestoringCancellation reutiliza el recurso y verifica el contador, por lo que no demuestra la prohibición acordada.

Corrección requerida: mantener exclusión por solicitud y pareja para resultados fallidos efectivamente ofrecidos, conservando la posibilidad de reconsiderar conflictos sin retención creada. Probar rechazo en un intento y selección en otro, y una misma grúa con distinto operador si se permite desvincularla libremente.

### H2 — Alta: una ubicación antigua provoca liberación automática

La sección 5.4 establece que una ubicación vencida impide acciones que requieran vigencia, sin liberar servicios existentes. ReservationService.statusQuery llama a eligibility; eligibility marca el compromiso no elegible al superar 120 segundos de ubicación. AutomationStore.reconcile interpreta cualquier eligible=false como motivo de reemplazo y encola RELEASE.

Escenario: aceptar con ubicación reciente, permanecer ASSIGNED sin despachar ni actualizar ubicación y superar 120 segundos, todavía dentro de los 300 segundos de despacho. La siguiente reconciliación puede liberar la reserva e iniciar reemplazo únicamente por antigüedad de ubicación. Esto introduce una exigencia de actualización que contradice la regla específica.

Corrección requerida: distinguir dato temporalmente antiguo de pérdida definitiva de aptitud. La ubicación antigua debe impedir despachar hasta actualizarla; el plazo de despacho sigue corriendo. Probar por separado ubicación vencida, actualización posterior y vencimiento real del despacho.

### H3 — Alta: la aceptación puede usar una ruta de una ubicación anterior

La sección 5.5 permite reutilizar rutas solo con iguales coordenadas y datos vigentes. AssignmentStore.respond (línea 58) encola CONFIRM sin revalidación vial. ReservationService.confirm revalida recursos y ubicación actual, pero utiliza estimatedServiceEndAt del payload original y no consulta rutas. La ubicación sí puede actualizarse con una propuesta vigente.

Escenario: evaluar y proponer; el operador actualiza la grúa a otro punto dentro de cobertura antes de aceptar; la nueva aproximación supera el límite vial o el presupuesto de llegada. Fleet puede confirmar porque valida ubicación/cobertura, pero no el recorrido nuevo. DispatchValidator lo detectará al despachar: ese control posterior no subsana una aceptación basada en condiciones distintas.

Corrección requerida: revalidar la evidencia de rutas y planificación de la propuesta antes de confirmar, verificando que la ubicación utilizada siga siendo la actual, sin mantener una transacción abierta durante HTTP. Mantener la recuperación de confirmaciones ya realizadas y la carrera aceptación/cancelación. Probar cambio de coordenadas y rutas vencidas durante la ventana de aceptación.

### H4 — Media: la suite refleja parte de la implementación y no toda la línea base

Las pruebas de fases 7–10 verifican casos importantes, pero no contienen las tres combinaciones anteriores. En particular, la prueba de reemplazos acepta repetir la misma pareja. Aprobar la suite actual no demuestra la regla documental de no repetición.

Corrección requerida: agregar pruebas de conformidad independientes de las decisiones actuales del código y corregir los fixtures de reemplazo para proporcionar candidatos distintos. Mantener los casos de concurrencia, recuperación e idempotencia existentes.

## Matriz resumida de alcance

| Compromiso | Evidencia inspeccionada | Evaluación |
|---|---|---|
| Dos microservicios colaborando realmente | FleetClient, reservas en Fleet, evaluación/asignación/ejecución en Assistance | Implementado; sujeto a H1–H3 |
| Gateway e identidad | Reglas por ruta/rol, JWT con audiencias, Keycloak, cliente técnico | Implementado; no se detectó un faltante estructural en lo revisado |
| Vehículos propios y privacidad | RequestService.owned, repositorios por subject, 404, índices y bloqueo por vehículo | Implementado |
| Varias solicitudes del mismo conductor para vehículos distintos | Exclusividad por vehicle_id; no por conductor | Acorde con la decisión final del chat |
| API externa necesaria | ORS para aproximación/traslado, cobertura local, cuotas persistentes | Implementado; aceptación requiere H3 |
| Cobertura, pesos y tiempos | Snapshot, EvaluationService, DispatchValidator, reglas y SQL | Valores principales presentes; no equivale a cobertura exhaustiva de bordes |
| Cancelación antes de primera aceptación | firstAssignedAt, confirmaciones inciertas, transacciones e idempotencia | Implementado en los caminos inspeccionados |
| Ejecución continua después de despacho | ExecutionStore, secuencia e historial; scheduler excluye estados de ejecución | Implementado |
| Búsquedas, cinco ofertas y dos reemplazos | AutomationStore y AssignmentStore | Implementado con diferencias H1 y H2 |
| Recuperación remota | pending_actions, reintentos persistentes, compensación del mismo intento | Implementado; preservar al corregir H3 |
| JAR común | POM y consumo Maven de commons | Implementado |
| Persistencia independiente | SQL/JPA por servicio, tres bases, Keycloak administra su esquema | Implementado |
| REST y documentación | Controladores, OpenAPI públicos, contrato privado y colección | Entregados; requieren mantenerse sincronizados con correcciones |
| Contenerización | Dockerfiles, Compose, volumen y scripts | Despliegue local entregado; no publicación en Internet |
| Eliminación de coordinador, averías y detección de demoras | Roles y máquina de estados revisados | Sin funcionalidades activas de esos conceptos en los caminos inspeccionados |
| Notificaciones | No implementadas | Correctamente excluidas por instrucción posterior del usuario |
| Logging | Historial y registros básicos | Incluido por decisión del equipo; monitoreo avanzado no es faltante del MVP |

## Conclusión y criterio de cierre

No falta un microservicio completo ni una pieza estructural esencial en lo revisado. Sí faltan ajustes de conformidad en selección, reconciliación y aceptación. Prioridad: corregir H1–H3, incorporar H4 y repetir las suites completas y la colección. No modificar la línea base para justificar silenciosamente el comportamiento actual.

Esta revisión no modificó código productivo ni reglas. El informe queda como lista de pendientes; los hallazgos no se consideran resueltos hasta contar con corrección y evidencia de regresión.
## Ubicación bloqueada durante propuesta o servicio — 9 de noviembre de 2026

El operador puede informar coordenadas mientras su grúa esté libre. Desde que Fleet crea una retención HELD, y durante el compromiso CONFIRMED, la actualización devuelve 409 sin modificar coordenadas ni locationUpdatedAt, incluso si se envían las mismas coordenadas. Se asume permanencia en la posición informada hasta el despacho. El bloqueo comparte el bloqueo transaccional de grúa con la reserva para evitar una carrera entre ofrecer y actualizar.

Si el operador rechaza una propuesta, o esta vence o el conductor cancela antes de aceptación, se vuelve a permitir informar ubicación cuando Fleet confirma la liberación o materializa la expiración. Mientras la liberación sea incierta no se permite actualizar. El operador rechaza propuestas: no se incorpora una cancelación operativa posterior a aceptar. Una grúa libre puede actualizar su ubicación aunque todavía tenga vigencia; superar 300 segundos exige actualizarla para nuevas propuestas y confirmaciones.

Este ajuste elimina el cambio de coordenadas entre propuesta y aceptación identificado en H3. No demuestra por sí mismo la vigencia temporal de las rutas cacheadas: ese borde de la auditoría sigue pendiente de verificación específica. H1 continúa pendiente.
## Evaluación conservada durante la propuesta — 9 de noviembre de 2026

Por decisión explícita del usuario, la ruta debe estar vigente al emitir la propuesta y conserva su validez durante toda su ventana de aceptación, sin recalcular por superar luego los 300 segundos. Se asumen posición y condiciones constantes; la posición está bloqueada por Fleet mientras exista HELD/CONFIRMED. No se extienden los plazos de propuesta o búsqueda. La ubicación debe seguir dentro de sus cinco minutos al confirmar y la habilitación debe ser válida. El despacho mantiene su revalidación independiente de rutas.

AssignmentStore.respond ya confirmaba sin solicitar nuevas rutas; se documentó esta decisión en el código y se sustituyó la regla anterior en la especificación consolidada y el documento de reglas. Se agregó una prueba Java de integración que envejece exclusivamente la evidencia de su fixture mientras la propuesta permanece vigente, comprueba el bloqueo de coordenadas, acepta y verifica que no aumenten las llamadas registradas a ORS. No se cambia la hora del sistema ni se agregan endpoints de simulación.

Esta simplificación sustituye la obligación anterior de recalcular al aceptar y atiende el borde temporal de H3. H1 permanece pendiente; el cierre de regresión requiere ejecutar los escenarios correspondientes.
Validación del ajuste: reconstrucción Docker completada y prueba Phase7IntegrationTest#activeProposalKeepsItsRouteEvidenceUntilAcceptance aprobada contra los contenedores reales. La aceptación mantuvo la evidencia de más de 300 segundos sin aumentar routing_calls y el cambio de ubicación devolvió 409. No se ejecutó la suite integral completa en este ajuste; H1 sigue abierto y la regresión global permanece pendiente.

## Rechazo y vencimiento de despacho — 9 de noviembre de 2026

El rechazo explícito excluye al operador de nuevas propuestas para esa misma solicitud, en el intento actual y los posteriores, aunque cambie de grúa. No lo excluye de otras solicitudes. La exclusión usa el registro durable response_kind=REJECT, independiente de que luego se cierre la asignación.

El vencimiento del despacho no constituye rechazo: una vez confirmada la liberación, el mismo operador puede competir en el siguiente intento si mantiene disponibilidad, habilitación, compatibilidad y ubicación vigente. No obtiene una asignación garantizada ni prioridad especial. Se conservan los cupos de propuestas y los dos reemplazos máximos, y la primera aceptación sigue impidiendo cancelar.

La prueba de cinco ofertas se adapta a propuestas vencidas, ya que cinco rechazos del mismo operador no son válidos bajo la regla nueva. Se agrega una prueba que conserva un rechazo real y prepara el segundo intento para comprobar la exclusión. La prueba de dos reemplazos verifica el caso permitido de reutilización tras vencer despacho.
### Resultado de verificación de H1

Las tres pruebas seleccionadas de Phase9IntegrationTest aprobaron el 9 de noviembre de 2026: rejectingOperatorIsExcludedInNextAttempt, twoReassignmentsThenCloseWithoutRestoringCancellation y fiveExpiredOffersExhaustTheSearchWithoutChangingDeadline. Verifican exclusión del operador que rechaza al preparar un segundo intento, reutilización permitida después de vencer el despacho y conservación del cupo de cinco ofertas. El primer escenario prepara el segundo intento mediante SQL sobre su fixture; no pretende reproducir por sí solo todo el recorrido de un segundo operador.

H1 queda atendido según la regla explícitamente reformulada por el usuario. La regresión integral conjunta de todos los ajustes posteriores a la auditoría sigue pendiente; estos tres resultados no se presentan como una ejecución de toda la suite. No se modificaron los límites de cinco ofertas ni de dos reemplazos.

## Cierre de regresión conjunta — 9 de noviembre de 2026

Se ejecutó `scripts/Verify-AuxiRuta.ps1 -Integration -IncludeDemo` con resultado satisfactorio. Aprobaron 102 pruebas aisladas (12 Commons, 27 Fleet, 62 Assistance y 1 de artefactos) y 24 pruebas de integración: BackendIntegrationTest (5), Phase7IntegrationTest (6), Phase8IntegrationTest (3), DeliveryIntegrationTest (2) y Phase9IntegrationTest (8). Las suites optativas omitidas en la compilación inicial se ejecutaron posteriormente con sus indicadores correspondientes; no se cuentan omisiones como aprobaciones.

La ejecución incluyó los contenedores reales, autenticación con Keycloak, consulta real a ORS, concurrencia de aceptación/cancelación, recuperación, ejecución del traslado, automatización y los 47 pasos de la colección publicados, ejecutados por el verificador Java. También se contrastaron los contratos OpenAPI. No se ejecutó Newman en esta regresión.

H1, H2 y H3 quedan atendidos respecto de las reglas reformuladas explícitamente y documentadas en los apartados anteriores. H4 queda atendido mediante las pruebas adicionales de exclusión por rechazo, vigencia de ubicación y conservación de evidencia durante la propuesta, junto con esta regresión conjunta. La prueba de exclusión prepara el segundo intento mediante SQL sobre su fixture; no representa por sí sola una ejecución completa con dos operadores.

Al finalizar se restituyó el perfil normal docker. Los cinco contenedores quedaron en ejecución, con cero reservas HELD/CONFIRMED, cero grúas TEST vinculadas y cero acciones remotas pendientes. No fue necesario modificar código productivo durante esta regresión. Los resultados cierran el pendiente de verificación conjunta, sin constituir una garantía de ausencia de defectos fuera de los escenarios cubiertos.
