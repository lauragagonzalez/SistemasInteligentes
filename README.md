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

SE PUEDE VER EL DIAGRAMA EN EL PDF SUBIDO 'ESQUEMA.DRAWIO.PDF


## Datos de ejemplo
Introduce los siguientes pacientes para verificar el funcionamiento completo del sistema:

| Appointment ID | Patient ID | Nombre | Apellido | DNI       | F. nacimiento | Teléfono  | Especialidad     |
|----------------|------------|--------|----------|-----------|---------------|-----------|------------------|
| A037           | P005       | David  | Wilson   | 12345674E | 23/06/1960    | 773446315 | Oncología        |
| A019           | P014       | Alex   | Taylor   | 45678901D | 27/02/1968    | 729226251 | Medicina general |
| A039           | P019       | Sarah  | Miller   | 90123456I | 24/05/1975    | 861805886 | Pediatría        |
| A012           | P003       | Laura  | Jones    | 12345672C | 21/08/2018    | 839702984 | Pediatría        |
| A016           | P037       | Robert | Williams | 18181818A | 05/02/2017    | 888680019 | Pediatría        |
| A017           | P022       | John   | Brown    | 22222222L | 10/05/1955    | 622109957 | Oncología        |
| A013           | P012       | Laura  | Davis    | 23456789B | 08/12/1947    | 813566604 | Oncología        |
| A001           | P034       | Alex   | Smith    | 15151515X | 26/01/1950    | 837465773 | Geriatría        |
| A003           | P048       | Emily  | Miller   | 29292929L | 24/03/1983    | 872098938 | Dermatología     |
| A042           | P045       | Linda  | Miller   | 26262626I | 25/04/1966    | 757961653 | Dermatología     |



Luego puedes porbra a inventarle alguno qu ellegue sin appointment como por ejemplo:
| Nombre   | Apellido   | DNI       | Fecha de nacimiento | Teléfono     |
|----------|------------|-----------|---------------------|--------------|
| Laura    | Martínez   | 48219375K | 1999-03-12          | 612 345 987 | MEDICINA GENERAL
| Carlos   | Gómez      | 73918462T | 1946-11-27          | 678 901 234 | GERIATRIA
| Ana      | Rodríguez  | 56192748H | 2001-06-08          | 623 778 145 | MEDICINA GENERAL
| David    | López      | 90481726M | 2015-09-21          | 655 439 820 | PEDIATRIA
| Sofía    | Hernández  | 31874659P | 2017-01-15          | 699 120 553 | PEDIATRIA



## Declaración de uso de IA

En el desarrollo de esta práctica se ha utilizado Claude  como herramienta de asistencia en programación.

El uso de IA se ha limitado a:

- Detección y corrección de errores de compilación en el código Java
- Implementación y ajuste de las interfaces gráficas
- Asistencia en la transcripción y adaptación del código Java debido a la falta de experiencia previa con el lenguaje.

- El uso de IA solo se uso como herramienta de apoyo, no sustituye el tarbajo de desarrollo y compresión realizado por los miembros del grupo.

