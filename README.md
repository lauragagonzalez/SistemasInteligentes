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

| Patient ID | Nombre | Fecha Nacimiento | Especialidad | Motivo | Estado | Teléfono | DNI |
|------------|---------|------------------|---------------|---------|---------|-----------|-------------|
| P001 | David Williams | 1955-06-04 | Oncología | Cancer Screening | Scheduled | 693958518 | 12345670A |
| P039 | Jane Wilson | 1950-12-12 | Oncología | Chemotherapy Review | Scheduled | 927113133 | 20202020C |
| P045 | Linda Miller | 1966-04-25 | Geriatría | Blood Pressure Check | Scheduled | 757961653 | 26262626I |
| P003 | Laura Jones | 1977-08-21 | Pediatría | Child Fever | Scheduled | 839702984 | 12345672C |
| P037 | Robert Williams | 1999-02-05 | Pediatría | Child Emergency | Scheduled | 888680019 | 18181818A |
| P016 | Michael Taylor | 2000-07-22 | Dermatología | Acne Treatment | Scheduled | 722338059 | 67890123F |
| P024 | Sarah Brown | 1991-11-04 | Dermatología | Skin Infection | Scheduled | 719677744 | 44444444N |
| P005 | David Wilson | 1960-06-23 | Dermatología | Dermatology Therapy | Scheduled | 773446315 | 12345674E |
| P010 | Michael Taylor | 2001-10-13 | Oncología | Oncology Checkup | Scheduled | 708139673 | 12345679J |
| P014 | Alex Taylor | 1968-02-27 | Medicina General | General Practice Visit | Scheduled | 729226251 | 45678901D |
| P036 | Michael Wilson | 1997-12-26 | Medicina General | General Follow-up | Scheduled | 854561304 | 17171717Z |
| P029 | David Smith | 2005-05-15 | Medicina General | Routine Checkup | Scheduled | 892360767 | 99999999S |

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

