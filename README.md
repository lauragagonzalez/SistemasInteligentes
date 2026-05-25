# Sistema Multiagente Hospitalario — JADE

Sistema multiagente desarrollado con JADE y Eclipse que simula la gestión de pacientes en un hospital. Los agentes se comunican entre sí para recepcionar, clasificar y atender pacientes, mostrando el resultado en tiempo real a través de una interfaz gráfica.


## Requisitos de instalación

### Software necesario

| Herramienta | Versión recomendada | Descarga |
|-------------|---------------------|---------|
| Java JDK | 8 o superior | https://www.oracle.com/java/technologies/downloads/ |
| Eclipse IDE | 2022 o superior | https://www.eclipse.org/downloads/ |
| JADE | 4.6.0 | https://jade.tilab.com/ |




## Instrucciones de ejecución

### Paso 1 — Importar el proyecto en Eclipse

1. Abre Eclipse
2. **File → Import → Existing Projects into Workspace**
3. Selecciona la carpeta del proyecto
4. Click **Finish**

### Paso 2 — Ejecutar

1. Click derecho sobre `Main.java`
2. **Run As → Java Application**

### Paso 3 — Usar el sistema

Al arrancar se abren automáticamente dos ventanas:

- **Ventana de Recepción** — rellena los datos del paciente y pulsa *"Registrar y enviar paciente"*
- **Ventana del Monitor** — muestra en tiempo real los pacientes con su especialidad, médico asignado, prioridad y estado


## Arquitectura del sistema

El sistema está compuesto por cuatro agentes que se comunican mediante mensajes ACL:

| Agente | Rol | Tipo |
|--------|-----|------|
| `AgenteRecepcion` | Recoge los datos del paciente mediante formulario GUI | Percepción |
| `AgenteClasificador` | Asigna prioridad (ALTA / MEDIA / NORMAL) y especialidad médica | Procesamiento |
| `AgenteMedico` | Atiende al paciente según especialidad (múltiples instancias) | Actuador |
| `AgenteMonitor` | Muestra en tiempo real el estado de todos los pacientes | Visualización |


### Diagrama 
### Flujo de mensajes

1. El recepcionista rellena el formulario → `AgenteRecepcion` envía `REQUEST` al clasificador.
2. `AgenteClasificador` determina prioridad y especialidad → busca un médico disponible en el DF → envía `REQUEST` al médico.
3. `AgenteClasificador` notifica al monitor con `EN_ESPERA`.
4. `AgenteMedico` recibe al paciente → notifica al monitor con `EN_CONSULTA` → al finalizar notifica con `ATENDIDO`.

SE PUEDE VER EL DIAGRAMA EN EL PDF SUBIDO 'SI.DRAWIO.PDF



# Flujo de pruebas del sistema (casos progresivos)

Este flujo está diseñado para comprobar el comportamiento del sistema de clasificación, colas, especialidades y urgencias.


## FASE 1 — Medicina General (cita vs sin cita)

### Pacientes de prueba

| Doctor ID| Patient ID | Nombre | Apellido | DNI       | Fecha nacimiento | Teléfono  | Especialidad        | Cita previa |
|----------|------------|--------|----------|-----------|------------------|-----------|---------------------|-------------|
| -        | -          | Laura  | Martínez | 48219375K | 12/03/1999       | 612345987 | Medicina general    | NO          |
| -        | -          | Ana    | Rodríguez| 56192748H | 08/06/2001       | 623778145 | Medicina general    | NO          |
| -        | -          | Carlos | Gómez    | 73918462T | 27/11/1946       | 678901234 | Medicina general    | NO          |
| D011     | P014       | Alex   | Taylor   | 45678901D | 27/02/1968       | 729226251 | Medicina general    | SI          |
| D011     | P033       | Michael | Wilson  | 14141414W | 06/02/1970       | 792321404 | Medicina general    | SI          |


Lo que debería pasar sería que los pacientes con cita se clasifiquen según el médico que tengan asignado, se les asigne prioridad 
CITA y se envíen directamente al médico correspondiente si está disponible. En cambio, los pacientes sin cita se clasifican 
en función de su edad (en este caso todos a medicina general) y pasan a la cola de medicina_general, donde esperan su turno según 
el orden de prioridad establecido.

La idea es introuducir primeor los que no tienen cita para comprobar que se quedan en la cola de medicina general, y 
luego introducir los que sí tienen cita para comprobar que se les da prioridad y se ponen al inicio de la cola.



## FASE 2 — Pediatría (colas independientes)

### Pacientes de prueba

| Doctor ID| Patient ID | Nombre | Apellido | DNI       | Fecha nacimiento | Teléfono  | Especialidad | Cita previa |
|----------|------------|--------|----------|-----------|------------------|-----------|--------------|-------------|
| -        | -          | David  | López    | 90481726M | 21/09/2015       | 655439820 | Pediatría    | NO          |
| -        | -          | Sofía  | Hernández| 31874659P | 15/01/2017       | 699120553 | Pediatría    | NO          |
| D002     | P003       | Laura  | Jones    | 12345672C | 21/08/2018       | 839702984 | Pediatría    | SI          |
| D009     | P037       | Robert | Williams | 18181818A | 05/02/2017       | 888680019 | Pediatría    | SI          |



En este caso el ejemplo es igual que el naterior, pero se hace para demostrar que las colas en cada especialidad funcionan 
de forma independiente, es decir, los pacientes de pediatría no se mezclan con los de medicina general y 
cada especialidad tiene su propia cola.

## FASE 3 — Múltiples especialidades

### Pacientes de prueba

| Doctor ID| Patient ID | Nombre | Apellido | DNI       | Fecha nacimiento | Teléfono  | Especialidad | Cita previa |
|----------|------------|--------|----------|-----------|------------------|-----------|--------------|-------------|
| D007     | P022       | John   | Brown    | 22222222L | 10/05/1955       | 622109957 | Oncología    | SI          |
| D007     | P005       | David  | Wilson   | 12345674E | 23/06/1960       | 773446315 | Oncología    | SI          |
| D010     | P012       | Laura  | Davis    | 23456789B | 08/12/1947       | 813566604 | Oncología    | SI          |
| D006     | P034       | Alex   | Smith    | 15151515X | 26/01/1950       | 837465773 | Geriatría    | SI          |
| D008     | P045       | Linda  | Miller   | 26262626I | 25/04/1966       | 757961653 | Geriatría    | SI          |
| D001     | P048       | Emily  | Miller   | 29292929L | 24/03/1983       | 872098938 | Dermatología | SI          |

En esta fase se introduce la complejidad de manejar múltiples especialidades médicas. 
En este caso, todas estas especialidades únicamente pueden ser accedidas mediante una cita previa, 
lo que implica que los pacientes ya tienen un médico asignado. 

Si se introducen estos pacientes en el sistema, 
se puede observar que aquellos que comparten el mismo doctor 
(por ejemplo, los asociados al D007) no serán derivados a cualquier médico disponible 
de la misma especialidad, sino que permanecerán en espera hasta que ese médico específico 
esté libre. Esto ocurre incluso si existen otros médicos disponibles dentro de la misma especialidad, 
ya que la asignación se realiza de forma estricta por médico y no solo por área médica.


## FASE 4 — URGENCIAS

### Pacientes de prueba

| Nombre   | Apellido  | DNI       | Fecha nacimiento | Teléfono  | Nivel urgencia   |
|----------|-----------|-----------|------------------|-----------|------------------|
| Urgente1 | Test      | 11111111A | 01/01/1935       | 600111111 | URGENCIA MÁXIMA  |
| Urgente2 | Test      | 22222222B | 15/03/1938       | 600222222 | URGENCIA MÁXIMA  |
| Urgente3 | Test      | 33333333C | 20/07/2000       | 600333333 | URGENCIA NORMAL  |
| Urgente4 | Test      | 44444444D | 10/10/1999       | 600444444 | URGENCIA NORMAL  |
| Urgente5 | Test      | 55555555E | 12/05/2020       | 600555555 | URGENCIA MEDIA   |
| Urgente6 | Test      | 66666666F | 02/09/2012       | 600666666 | URGENCIA MEDIA   |

Lo que debería pasar sería que la urgencia se clasifica en función de la edad 
del paciente, de forma que los casos más graves según ese criterio 
se atienden antes que los demás. 

Los pacientes de mayor edad pasan primero al sistema de urgencias y tienen prioridad máxima, 
mientras que los de edad intermedia se consideran urgencia normal y los más jóvenes se clasifican 
como urgencia media, por lo tanto tambien tendrian más prioriddad que los 
que se clasifican en urgencia normal.

## Declaración de uso de IA

En el desarrollo de esta práctica se ha utilizado Claude como herramienta de asistencia en programación.

El uso de IA se ha limitado a:

- Detección y corrección de errores de compilación en el código Java
- Implementación y ajuste de las interfaces gráficas
- Asistencia en la transcripción y adaptación del código Java debido a la falta de experiencia previa con el lenguaje.

El uso de IA solo se uso como herramienta de apoyo, no sustituye el trabajo de desarrollo realizado por los miembros del grupo.

