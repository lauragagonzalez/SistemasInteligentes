package primerproyectojava;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

public class AgenteClasificador extends Agent {

    private static final long serialVersionUID = 1L;

    private Map<String, String>  especialidadPorPaciente = new HashMap<>();
    private Map<String, String>  prioridadPorPaciente    = new HashMap<>();
    private Map<String, String>  motivoPorPaciente       = new HashMap<>();
    private Map<String, String>  fechaCitaPorPaciente    = new HashMap<>();
    private Map<String, String>  idPorDni                = new HashMap<>();
    private Map<String, Integer> colasPorEspecialidad    = new HashMap<>();
    private Map<String, Boolean> tieneCitaPorPaciente    = new HashMap<>();
    private Map<String, String>  salaPorDoctor           = new HashMap<>();

    private final List<String> colaEspera = new ArrayList<>();

    @Override
    protected void setup() {
        System.out.println("Hola! El Agente Clasificador [" + getAID().getName() + "] esta listo.");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("clasificador");
        sd.setName("servicio-clasificacion");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("Clasificador registrado en DF");
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        cargarPacientes();
        cargarDatosClasificacion();
        crearAgentesMedicos();

        addBehaviour(new CyclicBehaviour(this) {
            private static final long serialVersionUID = 1L;

            @Override
            public void action() {
                MessageTemplate filtro = MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                        MessageTemplate.MatchSender(new AID("recepcion", AID.ISLOCALNAME))
                );

                ACLMessage msg = myAgent.receive(filtro);

                if (msg != null) {
                    String paciente = msg.getContent();
                    System.out.println("\nClasificador: he recibido el paciente:");
                    System.out.println(paciente);

                    String especialidad = clasificarPaciente(paciente);
                    String prioridad    = calcularPrioridad(paciente);

                    System.out.println("Clasificador: especialidad asignada = " + especialidad);
                    System.out.println("Clasificador: prioridad asignada = "    + prioridad);

                    guardarPacienteNuevo(paciente, especialidad, prioridad);

                    boolean tieneCita = pacienteTieneCita(paciente);

                    String mensajeParaMedico = paciente
                            + ",especialidad_asignada=" + especialidad
                            + ",prioridad_asignada="    + prioridad
                            + ",cita_previa="           + (tieneCita ? "SI" : "NO");

                    String nombreMedico = buscarMedicoEnDF(especialidad);

                    if (nombreMedico != null) {
                        enviarAlMedico(nombreMedico, mensajeParaMedico);
                    } else {
                        insertarEnColaOrdenada(mensajeParaMedico);
                        imprimirCola();

                        int enCola = colasPorEspecialidad.getOrDefault(especialidad, 0) + 1;
                        colasPorEspecialidad.put(especialidad, enCola);

                        System.out.println("Clasificador: medico no disponible para " + especialidad
                                + ". EN ESPERA. Cola = " + colaEspera.size());

                        // --- NUEVO: notificar al monitor que el paciente está en espera ---
                        int posicionEnCola = colaEspera.indexOf(mensajeParaMedico);
                        int segundosEspera = (posicionEnCola + 1) * 90;

                        String nombreMonitor = buscarMonitorEnDF();
                        if (nombreMonitor != null) {
                            String msgMonitor = "EN_ESPERA," + mensajeParaMedico
                                    + ",espera_estimada=" + segundosEspera + "s";
                            ACLMessage avisoMonitor = new ACLMessage(ACLMessage.INFORM);
                            avisoMonitor.addReceiver(new AID(nombreMonitor, AID.ISLOCALNAME));
                            avisoMonitor.setContent(msgMonitor);
                            send(avisoMonitor);
                            System.out.println("Clasificador: EN_ESPERA enviado al monitor para "
                                    + mensajeParaMedico.split(",")[0]);
                        }
                        // --- FIN NUEVO ---

                        // Recalcular y notificar tiempos a todos los de la cola
                        recalcularTiemposEspera();
                    }

                } else {
                    block();
                }
            }
        });

        addBehaviour(new TickerBehaviour(this, 3000) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onTick() {
                if (colaEspera.isEmpty()) return;

                List<String> pendientes = new ArrayList<>(colaEspera);
                colaEspera.clear();

                for (String mensajeParaMedico : pendientes) {
                    String esp = "medicina_general";
                    for (String parte : mensajeParaMedico.split(",")) {
                        if (parte.startsWith("especialidad_asignada=")) {
                            esp = parte.replace("especialidad_asignada=", "").trim();
                        }
                    }

                    String medicoDisponible = buscarMedicoEnDF(esp);
                    if (medicoDisponible != null) {
                        enviarAlMedico(medicoDisponible, mensajeParaMedico);
                        int restantes = colasPorEspecialidad.getOrDefault(esp, 1) - 1;
                        colasPorEspecialidad.put(esp, Math.max(0, restantes));
                    } else {
                        insertarEnColaOrdenada(mensajeParaMedico);
                        System.out.println("Clasificador: sin medico libre para " + esp + ", vuelve a cola.");
                    }
                }

                if (colaEspera.size() < pendientes.size()) {
                    System.out.println("Clasificador: cola vaciada parcialmente. Quedan = " + colaEspera.size());
                }
            }
        });
    }

    private void recalcularTiemposEspera() {
        String nombreMonitor = buscarMonitorEnDF();
        if (nombreMonitor == null) return;

        for (int i = 0; i < colaEspera.size(); i++) {
            String mensaje = colaEspera.get(i);
            String dni = mensaje.split(",")[0];
            int segundosEspera = (i + 1) * 90; // 90s por consulta

            ACLMessage aviso = new ACLMessage(ACLMessage.INFORM);
            aviso.addReceiver(new AID(nombreMonitor, AID.ISLOCALNAME));
            aviso.setContent("ACTUALIZAR_ESPERA," + dni + ",espera_estimada=" + segundosEspera + "s");
            send(aviso);
        }

        System.out.println("Clasificador: tiempos de espera recalculados para " + colaEspera.size() + " pacientes.");
    }

    private void crearAgentesMedicos() {
        try {
            ContainerController cc = getContainerController();
            Scanner sc = new Scanner(new File("archive/doctors.csv"), "UTF-8");
            if (sc.hasNextLine()) sc.nextLine();
            if (sc.hasNextLine()) sc.nextLine();

            while (sc.hasNextLine()) {
                String linea = sc.nextLine().trim();
                if (linea.isEmpty()) continue;
                String[] datos = linea.split(";");
                if (datos.length < 4) continue;

                String doctorId     = datos[0].trim();
                String especialidad = normalizarEspecialidad(datos[3].trim());
                String nombreAgente = "medico_" + doctorId;
                String sala = datos.length >= 9 ? datos[8].trim() : "Sala-" + doctorId;

                try {
                    AgentController ac = cc.createNewAgent(
                        nombreAgente,
                        "primerproyectojava.AgenteMedico",
                        new Object[]{ especialidad, sala }
                    );
                    ac.start();
                    System.out.println("Clasificador: agente creado -> " + nombreAgente + " (" + especialidad + ")");
                } catch (StaleProxyException e) {
                    System.out.println("Clasificador: error creando agente " + nombreAgente);
                }
            }
            sc.close();
        } catch (Exception e) {
            System.out.println("Clasificador: error leyendo doctors.csv");
            e.printStackTrace();
        }
    }

    private void enviarAlMedico(String nombreMedico, String contenido) {
        ACLMessage mensajeMedico = new ACLMessage(ACLMessage.REQUEST);
        mensajeMedico.addReceiver(new AID(nombreMedico, AID.ISLOCALNAME));
        mensajeMedico.setContent(contenido);
        send(mensajeMedico);
        System.out.println("Clasificador: paciente enviado a " + nombreMedico
                + " → " + contenido.split(",")[0]);
    }

    private String buscarMonitorEnDF() {
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("monitor");
            template.addServices(sd);
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) return result[0].getName().getLocalName();
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String buscarMedicoEnDF(String especialidad) {
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("medico");
            sd.setName("medico-" + especialidad);
            template.addServices(sd);
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) return result[0].getName().getLocalName();
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void cargarPacientes() {
        try {
            Scanner sc = new Scanner(new File("archive/patients.csv"), "UTF-8");

            while (sc.hasNextLine()) {
                String linea = sc.nextLine().trim();
                if (linea.isEmpty()) continue;
                String[] datos = linea.split(";");

                if (datos.length >= 12) {
                    String patientId = datos[0].trim();
                    String dni       = datos[11].trim();
                    if (!dni.isEmpty()) {
                        idPorDni.put(dni, patientId);
                    }
                }
            }
            sc.close();
            System.out.println("Clasificador: pacientes cargados = " + idPorDni.size());

        } catch (Exception e) {
            System.out.println("Clasificador: error leyendo patients.csv");
            e.printStackTrace();
        }
    }

    private void guardarPacienteNuevo(String paciente, String especialidad, String prioridad) {
        String[] datos = paciente.split(",");
        if (datos.length < 1) return;

        String dni = datos[0].trim();
        if (idPorDni.containsKey(dni)) {
            System.out.println("Clasificador: paciente ya registrado (DNI: " + dni + ")");
            return;
        }

        String nuevoId  = generarNuevoId();
        String nombre   = datos.length > 1 ? datos[1].trim() : "";
        String apellido = datos.length > 2 ? datos[2].trim() : "";
        String fechaNac = datos.length > 4 ? datos[4].trim() : "";
        String telefono = datos.length > 5 ? datos[5].trim() : "";
        String hoy      = java.time.LocalDate.now().toString();

        try (FileWriter fw = new FileWriter("archive/patients.csv", true)) {
        	fw.write(nuevoId + ";" + nombre + ";" + apellido + ";;"
        	        + fechaNac + ";" + telefono + ";;;" + hoy + ";;;" + dni + "\n");
            idPorDni.put(dni, nuevoId);
            System.out.println("Clasificador: nuevo paciente guardado " + nuevoId + " (DNI: " + dni + ")");
        } catch (Exception e) {
            System.out.println("Clasificador: error al guardar nuevo paciente");
            e.printStackTrace();
        }
    }

    private String generarNuevoId() {
        int max = idPorDni.values().stream()
                .filter(id -> id.matches("P\\d+"))
                .mapToInt(id -> Integer.parseInt(id.substring(1)))
                .max()
                .orElse(0);
        return String.format("P%03d", max + 1);
    }

    private String normalizarEspecialidad(String especialidad) {
        if (especialidad == null) return "medicina_general";
        switch (especialidad.toLowerCase().trim()) {
            case "cardiology":       case "cardiologia":      return "cardiologia";
            case "pediatrics":       case "pediatria":        return "pediatria";
            case "geriatrics":       case "geriatria":        return "geriatria";
            case "oncology":         case "oncologia":        return "oncologia";
            case "neurology":        case "neurologia":       return "neurologia";
            case "traumatology":     case "traumatologia":    return "traumatologia";
            case "psychiatry":       case "psiquiatria":      return "psiquiatria";
            case "dermatology":      case "dermatologia":     return "dermatologia";
            case "gynecology":       case "ginecologia":      return "ginecologia";
            case "general practice": case "medicina_general": return "medicina_general";
            default: return especialidad.toLowerCase().trim();
        }
    }

    private void cargarDatosClasificacion() {
        Map<String, String> especialidadPorDoctor = new HashMap<>();
        try {
            Scanner scDoctors = new Scanner(new File("archive/doctors.csv"), "UTF-8");
            if (scDoctors.hasNextLine()) scDoctors.nextLine();
            if (scDoctors.hasNextLine()) scDoctors.nextLine();

            while (scDoctors.hasNextLine()) {
                String linea = scDoctors.nextLine().trim();
                if (linea.isEmpty()) continue;
                String[] datos = linea.split(";");
                if (datos.length >= 4) {
                    String doctorId               = datos[0].trim();
                    String especialidadNormalizada = normalizarEspecialidad(datos[3].trim());
                    especialidadPorDoctor.put(doctorId, especialidadNormalizada);
                    String sala = datos.length >= 9 ? datos[8].trim() : "Sala-" + doctorId;
                    salaPorDoctor.put(doctorId, sala);
                }
            }
            scDoctors.close();
            System.out.println("Clasificador: doctores cargados = " + especialidadPorDoctor.size());
        } catch (Exception e) {
            System.out.println("Clasificador: error leyendo doctors.csv");
            e.printStackTrace();
        }

        try {
            Scanner scAppointments = new Scanner(new File("archive/appointments.csv"), "UTF-8");
            if (scAppointments.hasNextLine()) scAppointments.nextLine();

            while (scAppointments.hasNextLine()) {
                String linea = scAppointments.nextLine().trim();
                if (linea.isEmpty()) continue;
                String[] datos = linea.split(";");
                if (datos.length >= 6) {
                    String patientId      = datos[1].trim();
                    String doctorId       = datos[2].trim();
                    String fechaCita      = datos[3].trim();
                    String motivoConsulta = datos[5].trim();

                    String especialidad = especialidadPorDoctor.get(doctorId);
                    if (especialidad != null && debeActualizarCita(patientId, fechaCita, motivoConsulta)) {
                        especialidadPorPaciente.put(patientId, especialidad);
                        prioridadPorPaciente.put(patientId, calcularPrioridadDesdeMotivo(motivoConsulta, especialidad));
                        motivoPorPaciente.put(patientId, motivoConsulta);
                        fechaCitaPorPaciente.put(patientId, fechaCita);
                        tieneCitaPorPaciente.put(patientId, true);
                    }
                }
            }
            scAppointments.close();
            System.out.println("Clasificador: citas cargadas = " + especialidadPorPaciente.size());
        } catch (Exception e) {
            System.out.println("Clasificador: error leyendo appointments.csv");
            e.printStackTrace();
        }
    }

    private boolean debeActualizarCita(String patientId, String nuevaFecha, String nuevoMotivo) {
        if (!fechaCitaPorPaciente.containsKey(patientId)) return true;
        String motivoActual = motivoPorPaciente.get(patientId);
        if (nuevoMotivo.equalsIgnoreCase("Emergency") && !motivoActual.equalsIgnoreCase("Emergency")) return true;
        if (motivoActual.equalsIgnoreCase("Emergency") && !nuevoMotivo.equalsIgnoreCase("Emergency")) return false;
        return nuevaFecha.compareTo(fechaCitaPorPaciente.get(patientId)) > 0;
    }

    private String clasificarPaciente(String paciente) {
        String[] datos = paciente.split(",");
        if (datos.length < 5) {
            System.out.println("Clasificador: mensaje ignorado porque no tiene formato de paciente.");
            return "desconocida";
        }
        String dni       = datos[0].trim();
        String patientId = idPorDni.get(dni);
        if (patientId != null && especialidadPorPaciente.containsKey(patientId)) {
            System.out.println("Clasificador: paciente encontrado por DNI " + dni + " → " + patientId);
            System.out.println("Clasificador: cita usada = " + fechaCitaPorPaciente.get(patientId)
                    + ", motivo = " + motivoPorPaciente.get(patientId));
            return especialidadPorPaciente.get(patientId);
        }
        System.out.println("Clasificador: DNI " + dni + " no tiene cita previa, clasificando por edad.");
        return clasificarPorEdad(paciente);
    }

    private String clasificarPorEdad(String paciente) {
        String[] datos = paciente.split(",");
        try {
            int anioNacimiento = Integer.parseInt(datos[4].trim().substring(0, 4));
            int edadAproximada = java.time.LocalDate.now().getYear() - anioNacimiento;
            System.out.println("Clasificador: edad aproximada = " + edadAproximada);
            if (edadAproximada < 16) return "pediatria";
            if (edadAproximada > 75) return "geriatria";
            return "medicina_general";
        } catch (Exception e) {
            System.out.println("Clasificador: error al calcular especialidad por edad.");
            return "desconocida";
        }
    }

    private String calcularPrioridad(String paciente) {
        String[] datos = paciente.split(",");
        if (datos.length < 1) return "DESCONOCIDA";

        String dni       = datos[0].trim();
        String patientId = idPorDni.get(dni);
        int puntuacion   = 1;

        if (patientId != null && prioridadPorPaciente.containsKey(patientId)) {
            String prioridadBase = prioridadPorPaciente.get(patientId);
            if      (prioridadBase.equalsIgnoreCase("ALTA"))  puntuacion = 3;
            else if (prioridadBase.equalsIgnoreCase("MEDIA")) puntuacion = 2;
            else                                               puntuacion = 1;
        }

        if (patientId != null && tieneCitaPorPaciente.getOrDefault(patientId, false)) puntuacion += 1;

        if (datos.length >= 5) {
            try {
                int edad = java.time.LocalDate.now().getYear()
                        - Integer.parseInt(datos[4].trim().substring(0, 4));
                if (edad < 5 || edad > 75) puntuacion += 1;
                else if (edad > 65)        puntuacion += 1;
            } catch (Exception e) {
                return "DESCONOCIDA";
            }
        }

        if (patientId != null && tieneCitaPorPaciente.getOrDefault(patientId, false)) return "CITA";
        if (puntuacion >= 3) return "ALTA";
        if (puntuacion == 2) return "MEDIA";
        return "NORMAL";
    }

    private String calcularPrioridadDesdeMotivo(String motivoConsulta, String especialidad) {
        if (motivoConsulta.equalsIgnoreCase("Emergency"))                             return "ALTA";
        if (especialidad.equalsIgnoreCase("oncologia"))                               return "ALTA";
        if (motivoConsulta.equalsIgnoreCase("Therapy")
         || motivoConsulta.equalsIgnoreCase("Consultation"))                          return "MEDIA";
        return "NORMAL";
    }

    private boolean pacienteTieneCita(String paciente) {
        String[] datos   = paciente.split(",");
        if (datos.length < 1) return false;
        String dni       = datos[0].trim();
        String patientId = idPorDni.get(dni);
        return patientId != null && tieneCitaPorPaciente.getOrDefault(patientId, false);
    }

    private int nivelPrioridadCola(String mensaje) {
        if (mensaje.contains("cita_previa=SI") || mensaje.contains("prioridad_asignada=CITA")) return 4;
        if (mensaje.contains("prioridad_asignada=ALTA"))  return 3;
        if (mensaje.contains("prioridad_asignada=MEDIA")) return 2;
        return 1;
    }

    private void insertarEnColaOrdenada(String mensaje) {
        int nivelNuevo = nivelPrioridadCola(mensaje);
        int posicion   = 0;
        while (posicion < colaEspera.size()) {
            if (nivelNuevo > nivelPrioridadCola(colaEspera.get(posicion))) break;
            posicion++;
        }
        colaEspera.add(posicion, mensaje);
    }

    private String extraerCampo(String mensaje, String campo) {
        for (String parte : mensaje.split(",")) {
            if (parte.startsWith(campo)) return parte.replace(campo, "").trim();
        }
        return "-";
    }

    private void imprimirCola() {
        System.out.println("\n--- COLA DE ESPERA ACTUAL ---");
        if (colaEspera.isEmpty()) { System.out.println("(vacía)"); return; }
        for (int i = 0; i < colaEspera.size(); i++) {
            String mensaje      = colaEspera.get(i);
            String dni          = mensaje.split(",")[0];
            String prioridad    = extraerCampo(mensaje, "prioridad_asignada=");
            String cita         = extraerCampo(mensaje, "cita_previa=");
            String especialidad = extraerCampo(mensaje, "especialidad_asignada=");
            System.out.println((i + 1) + ". DNI=" + dni
                    + " | prioridad=" + prioridad
                    + " | cita=" + cita
                    + " | especialidad=" + especialidad);
        }
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException e) { e.printStackTrace(); }
        System.out.println("Clasificador: agente terminado.");
    }
}