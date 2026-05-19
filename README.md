# Sistema Multiagente Hospitalario — JADE

Sistema multiagente desarrollado con JADE y Eclipse que simula la gestión de pacientes en un hospital. Los agentes se comunican entre sí para recepcionar, clasificar y atender pacientes, mostrando el resultado en tiempo real a través de una interfaz gráfica.


## Requisitos de instalación

### Software necesario

| Herramienta | Versión recomendada | Descarga |
|-------------|---------------------|---------|
| Java JDK | 8 o superior | https://www.oracle.com/java/technologies/downloads/ |
| Eclipse IDE | 2022 o superior | https://www.eclipse.org/downloads/ |
| JADE | 4.6.0 | https://jade.tilab.com/ |



## Estructura del proyecto

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

![Diagrama realizado con drawio]("C:\Users\laura\Downloads\SI.drawio.png")

## Datos de ejemplo

Introduce los siguientes pacientes para verificar el funcionamiento completo del sistema:

| DNI | Nombre || |  |

## Declaración de uso de IA

En el desarrollo de esta práctica se ha utilizado Claude  como herramienta de asistencia en programación.

El uso de IA se ha limitado a:

- Detección y corrección de errores de compilación en el código Java
- Implementación y ajuste de las interfaces gráficas
- Asistencia en la transcripción y adaptación del código Java debido a la falta de experiencia previa con el lenguaje.

- El uso de IA solo se uso como herramienta de apoyo, no sustituye el tarbajo de desarrollo y compresión realizado por los miembros del grupo.

