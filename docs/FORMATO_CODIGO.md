# Formato del código

Se utiliza google-java-format 1.29.0 como criterio de presentación del código Java, compatible con JDK 21. Aplica sangría de dos espacios, espacios consistentes, separación de instrucciones y distribución de parámetros, anotaciones, bloques y expresiones largas.

Se preservan deliberadamente el orden y contenido de imports, los literales largos y los Javadoc. No se sustituyen imports comodín ni se renombran clases, métodos o variables. Se adopta el formato de la herramienta, no se afirma cumplimiento de todas las reglas no visuales de Google Java Style.

Referencia: https://github.com/google/google-java-format

## Aplicar y comprobar

Desde la raíz, con JDK 21 en PATH:

~~~powershell
.\scripts\Format-Java.ps1
.\scripts\Format-Java.ps1 -Check
~~~

La primera ejecución descarga el JAR desde Maven Central a LOCALAPPDATA/AuxiRuta/formatters y verifica su SHA-256 fijado. Las siguientes usan esa caché. No añade dependencias al backend ni modifica sus POM. -Check no modifica archivos y devuelve error si hay diferencias de formato.

Solo alcanza archivos .java dentro de src de los cuatro módulos, incluidos tests; excluye target y herramientas distribuidas.

## SQL y otros archivos

Los ocho scripts SQL de los microservicios tienen columnas separadas, sangría de dos espacios y separación entre sentencias. Se conservaron consultas, tipos, defaults, restricciones, valores y orden de ejecución. Format-Java.ps1 no formatea SQL.

Los POM, YAML, Dockerfiles y scripts de infraestructura conservan su formato y funcionamiento actuales. Los literales SQL/JSON dentro de Java no se reescriben para no alterar su contenido; una cadena larga puede superar el ancho habitual.

## Verificación del 10 de noviembre de 2026

- Los 100 archivos Java conservan la misma secuencia de tokens reconocida por el scanner del compilador JDK 21, incluidos los literales; solo cambió su presentación.
- Los ocho scripts SQL conservan la secuencia de contenido no blanco, preservando comentarios y literales.
- La compilación/verificación de los cuatro módulos aprobó 102 pruebas aisladas.
- -Check confirmó formato estable en una segunda pasada.
- Las pruebas integrales quedaron omitidas: Docker no estaba iniciado. No se presenta esta ejecución como una nueva regresión integral.

El PDF AUXIRUTA_CODIGO_EXPLICADO.pdf es una instantánea anterior al reformateo: el comportamiento descrito se conserva, pero sus números de línea y hashes corresponden a esa edición. No se regeneró el PDF con este cambio.
