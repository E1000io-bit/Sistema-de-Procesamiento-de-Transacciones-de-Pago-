# Sistema de Procesamiento de Transacciones de Pago

Trabajo Práctico Final — Programación Concurrente y Paralela 2026 (FCEFyN, UNC).

## Integrantes

- Álvaro Bastía
- Carlos Britez
- Sol Género
- Emilio Tarrago
- Hebe Azul Usandivaras

## Requisitos

- Java 17 o superior
- Maven 3.9

## Compilar y correr

```bash
mvn compile exec:java -Dexec.args="aleatoria"

mvn compile exec:java -Dexec.args="priorizada"
```

## Solo correr (una vez compilado)

```bash
mvn exec:java -Dexec.args="aleatoria"

mvn exec:java -Dexec.args="priorizada"
```

O generando el jar:

```bash
mvn package
java -jar target/procesamiento-transacciones-pago-1.0-SNAPSHOT.jar
```

## Tests

```bash
mvn test
```

## Estructura

```
src/main/java/tp/   código fuente (Main)
src/test/java/tp/   tests (JUnit 5)
logs/               logs de ejecución (no se suben al repo)
docs/diagramas/     diagramas de clases y de secuencia
```
