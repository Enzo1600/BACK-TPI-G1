# Demostración de privacidad entre usuarios

## Cuentas persistentes

| Cuenta | Rol | Contraseña local | Token Postman |
|---|---|---|---|
| driver.demo | DRIVER | DEMO_DRIVER_PASSWORD del .env | driver_token |
| driver2.demo | DRIVER | DEMO_DRIVER_PASSWORD del .env | driver2_token |
| operator.demo | OPERATOR | DEMO_OPERATOR_PASSWORD del .env | operator_token |
| operator2.demo | OPERATOR | DEMO_OPERATOR_PASSWORD del .env | operator2_token |

Las cuentas de cada rol comparten la contraseña configurada para ese rol solamente por simplicidad de esta demostración local. Sus identidades sub son diferentes y sus datos no se comparten. No son usuarios temporales de las suites integrales.

Las nuevas instalaciones importan las identidades desde el realm. En instalaciones existentes, reiniciar Keycloak no vuelve a importar un realm ya creado: ejecutar la preparación siguiente agrega las cuentas faltantes sin borrar el volumen. Las contraseñas existentes no se restablecen.

## Preparación

Con Docker Desktop y los contenedores iniciados, desde la raíz:

~~~powershell
.\scripts\Start-AuxiRuta.ps1 -Profile docker -NoBuild
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.privacySeed=true" "-Dtest=PrivacyDemoSetupTest"
~~~

En un clon nuevo construir sin -NoBuild e instalar Commons inicialmente, como indica el tutorial general. La preparación habitual DemoDataSetupTest también ejecuta esta preparación adicional después de preparar al operador principal.

PrivacyDemoSetupTest:
- Crea las identidades que falten y verifica que tengan únicamente su rol de negocio.
- Crea los perfiles de operador que falten, sin alterar perfiles existentes.
- Crea DEMODRV1 para driver.demo y DEMODRV2 para driver2.demo, si no existen.
- Verifica consultas propias 200, consultas y ediciones cruzadas 404, conservación de datos y ausencia del vehículo ajeno en los listados.
- Ejecuta mediante Java las diez solicitudes de la colección de privacidad.
- Imprime los IDs de vehículos y operadores, sin contraseñas ni tokens.

Se puede repetir: conserva IDs, datos y contraseñas. No crea solicitudes ni consulta ORS. No libera servicios ni modifica recursos ocupados. Si una patente reservada a esta demostración ya pertenece a otro usuario, falla en vez de apropiarse del vehículo.

El segundo operador se crea sin grúa, sin disponibilidad y sin habilitación operativa. Esto permite demostrar identidad/privacidad sin introducir un candidato en la colección principal. Para convertirlo en prestador operativo, ADMIN debe habilitarlo y vincularle una grúa siguiendo las reglas habituales.

## Postman: mostrar 200 y 404 sobre el mismo vehículo

1. Importar docs/api/AuxiRuta-privacidad.postman_collection.json.
2. Importar el entorno actualizado docs/api/Local.postman_environment.json, o agregar driver2_token y operator2_token al entorno existente como valores secretos locales. No sobrescribir tokens vigentes innecesariamente.
3. Obtener los cuatro access tokens mediante el mismo PKCE descrito en TUTORIAL_POSTMAN_Y_PRESENTACION.md. Para las nuevas cuentas iniciar sesión como driver2.demo y operator2.demo.
4. Seleccionar el entorno local y comprobar base_url = http://localhost:8000.
5. En Variables de la colección de privacidad, completar privacy_vehicle1_id con el ID impreso para DEMODRV1 y privacy_vehicle2_id con el ID impreso para DEMODRV2. Los UUID cambian entre instalaciones.
6. Abrir la primera solicitud y pulsar Send: driver.demo consulta su vehículo y recibe 200.
7. Abrir la segunda: es el mismo vehículo con driver2_token; devuelve 404.
8. La tercera intenta modificar ese vehículo con driver2_token: devuelve 404 y no modifica datos.
9. Los pasos 4–6 repiten la demostración en sentido contrario.
10. Los pasos 7–10 muestran perfiles distintos y consultas de asignaciones propias de cada operador.

También se puede ejecutar esta colección en orden con Runner, una iteración. No depende de ventanas de propuestas. La comprobación de identidades distintas del paso 8 usa el ID capturado en el paso 7; ejecutar primero ese paso. Esta colección no pretende sustituir la de 47 pasos.

El 404 deliberado oculta si el vehículo existe. El 403 se reserva para el rol sin permiso: por ejemplo, OPERATOR intentando consultar la colección de vehículos de conductores. Una lista de vehículos con DRIVER devuelve 200 y solo los propios, no un 404 por cada vehículo ajeno.

## Mostrar aislamiento entre operadores con una asignación

Se necesita una propuesta o servicio real de operator.demo; la preparación de privacidad no crea uno automáticamente.

1. Con operator_token, consultar GET /api/v1/operator/assignments.
2. Elegir un id perteneciente a ese operador, incluso de un servicio anterior, y consultar GET /api/v1/assignments/{id}: debe responder 200.
3. Duplicar esa petición, conservar el mismo id y cambiar únicamente Authorization a Bearer {{operator2_token}}: debe responder 404.
4. Consultar GET /api/v1/operator/assignments con operator2_token: devuelve 200, pero no incluye las asignaciones de operator.demo.
5. Para mostrar que tampoco puede aceptar una propuesta ajena, usar POST /api/v1/assignments/{id}/acceptance con operator2_token y una Idempotency-Key nueva: debe devolver 404. Usar una propuesta del primer operador y no cambiar su token por error.

No usar GET /api/v1/operators/{id} como prueba de propiedad entre operadores: esa ruta es administrativa y un OPERATOR obtiene 403 por rol. GET /api/v1/operators/me toma la identidad directamente del token, por lo que cada operador ve su propio perfil sin enviar un id.

## Persistencia y validación

Los usuarios, perfiles y vehículos permanecen después de detener Docker. Las pruebas integrales temporales no eliminan estas cuentas. No ejecutar down -v para preparar la presentación.

Se verificó la preparación repetida contra el entorno local, con los mismos UUID, y las diez peticiones publicadas mediante Java. No se ejecutó el sandbox de Postman en esta comprobación; sus scripts realizan las verificaciones visuales de códigos y capturas para la presentación.
