package primerproyectojava;
 
import jade.core.AID;
 
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
 
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.PriorityQueue;
 
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
    private Map<String, String>  motivoPorPaciente       = new HashMap<>();
    private Map<String, String>  fechaCitaPorPaciente    = new HashMap<>();
    private Map<String, String>  idPorDni                = new HashMap<>();
    private Map<String, Boolean> tieneCitaPorPaciente    = new HashMap<>();
    private Map<String, String>  medicoPorPaciente       = new HashMap<>();
 
    private long contadorLlegada = 0;
 
    private static class PacienteEnCola {
        String mensaje;
        int prioridad;
        long ordenLlegada;
 
        PacienteEnCola(String mensaje, int prioridad, long ordenLlegada) {
            this.mensaje = mensaje;
            this.prioridad = prioridad;
            this.ordenLlegada = ordenLlegada;
        }
    }
 
    // Una PriorityQueue por especialidad
    private final Map<String, PriorityQueue<PacienteEnCola>> colasPorEspecialidad = new HashMap<>();
 
    private PriorityQueue<PacienteEnCola> obtenerCola(String especialidad) {
        return colasPorEspecialidad.computeIfAbsent(especialidad, k ->
            new PriorityQueue<>((p1, p2) -> {
                int cmp = Integer.compare(p2.prioridad, p1.prioridad);
                return cmp != 0 ? cmp : Long.compare(p1.ordenLlegada, p2.ordenLlegada);
            })
        );
    }
 
    private int valorPrioridad(String prioridad) {
        switch (prioridad) {
            case "URGENCIA": return 5;
            case "CITA":     return 4;
            case "ALTA":     return 3;
            case "MEDIA":    return 2;
            case "NORMAL":   return 1;
            default:         return 0;
        }
    }
 
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
        crearAgenteUrgencias();
 
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
 
                    // URGENCIAS 
                    if (esUrgencia(paciente)) {
                        System.out.println("Clasificador: URGENCIA detectada → derivando a AgenteUrgencias");
                        enviarAUrgencias(paciente);
                        return;
                    }
 
                    // Flujo normal 
                    String especialidad = clasificarPaciente(paciente);
                    String prioridad    = calcularPrioridad(paciente);
                    System.out.println("Clasificador: especialidad asignada = " + especialidad);
                    System.out.println("Clasificador: prioridad asignada = "    + prioridad);
 
                    guardarPacienteNuevo(paciente);
 
                    boolean tieneCita = pacienteTieneCita(paciente);
                    String  dni       = paciente.split(",")[0].trim();
                    String  patientId = idPorDni.get(dni);
 
                    boolean tieneMedicoAsignado = patientId != null
                            && medicoPorPaciente.containsKey(patientId);
                    String medicoAsignadoStr = tieneMedicoAsignado
                            ? "medico_" + medicoPorPaciente.get(patientId)
                            : "cualquiera";
 
                    String mensajeParaMedico = paciente
                            + ",especialidad_asignada=" + especialidad
                            + ",prioridad_asignada="    + prioridad
                            + ",cita_previa="           + (tieneCita ? "SI" : "NO")
                            + ",medico_asignado="       + medicoAsignadoStr;
 
                    if (tieneMedicoAsignado) {
                        String medicoLibre = buscarMedicoEspecificoEnDF(medicoAsignadoStr);
                        if (medicoLibre != null) {
                            enviarAlMedico(medicoLibre, mensajeParaMedico);
                            recalcularTiemposEspera();
                        } else {
                            encolarPaciente(mensajeParaMedico, especialidad, prioridad);
                            imprimirColas();
                            recalcularTiemposEspera();
                            notificarEspera(mensajeParaMedico, especialidad, prioridad, dni);
                        }
                    } else {
                        String nombreMedico = buscarMedicoEnDF(especialidad);
                        if (nombreMedico != null) {
                            enviarAlMedico(nombreMedico, mensajeParaMedico);
                            recalcularTiemposEspera();
                        } else {
                            encolarPaciente(mensajeParaMedico, especialidad, prioridad);
                            imprimirColas();
                            recalcularTiemposEspera();
                            notificarEspera(mensajeParaMedico, especialidad, prioridad, dni);
                        }
                    }
 
                } else {
                    block();
                }
            }
        });
 
        addBehaviour(new TickerBehaviour(this, 5_000) {
            private static final long serialVersionUID = 1L;
 
            @Override
            protected void onTick() {
                boolean huboAsignacion = false;
 
                for (Map.Entry<String, PriorityQueue<PacienteEnCola>> entrada
                        : colasPorEspecialidad.entrySet()) {
 
                    String especialidad = entrada.getKey();
                    PriorityQueue<PacienteEnCola> cola = entrada.getValue();
 
                    while (!cola.isEmpty()) {
                        PacienteEnCola candidato = null;
                        String medicoParaCandidato = null;

                        // Buscar el primero de la cola que tenga médico disponible
                        for (PacienteEnCola p : cola) {
                            String medicoAsignado = extraerCampo(p.mensaje, "medico_asignado=");
                            String medicoDisponible;
                            if ("cualquiera".equals(medicoAsignado) || "-".equals(medicoAsignado)) {
                                medicoDisponible = buscarMedicoEnDF(especialidad);
                            } else {
                                medicoDisponible = buscarMedicoEspecificoEnDF(medicoAsignado);
                            }
                            if (medicoDisponible != null) {
                                candidato = p;
                                medicoParaCandidato = medicoDisponible; // guardar para no buscar dos veces
                                break;
                            }
                        }
 
                        if (candidato == null) break;
 
                        cola.remove(candidato);
                        enviarAlMedico(medicoParaCandidato, candidato.mensaje);
                        huboAsignacion = true;

                        notificarSalidaDeCola(candidato.mensaje, medicoParaCandidato);
 
                        System.out.println("Clasificador: asignado desde cola [" + especialidad + "] → "
                                + candidato.mensaje.split(",")[0] + " → " + medicoParaCandidato);
                    }
                }
 
                if (huboAsignacion) {
                    System.out.println("Clasificador: reasignacion completada.");
                    imprimirColas();
                }
 
                recalcularTiemposEspera();
            }
        });
    }
 

    private boolean esUrgencia(String paciente) {
        for (String parte : paciente.split(",")) {
            if (parte.trim().equalsIgnoreCase("urgencia=SI")) return true;
        }
        return false;
    }
 
    private void enviarAUrgencias(String paciente) {
        String nombreUrgencias = buscarUrgenciasEnDF();
        if (nombreUrgencias != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(new AID(nombreUrgencias, AID.ISLOCALNAME));
            msg.setContent(paciente);
            send(msg);
            System.out.println("Clasificador: paciente enviado a " + nombreUrgencias);
        } else {
            System.out.println("Clasificador: AgenteUrgencias no encontrado en DF — descartado.");
        }
    }
 
    private String buscarUrgenciasEnDF() {
        try {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("urgencias");
            template.addServices(sd);
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) return result[0].getName().getLocalName();
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        return null;
    }
 
 
    private void crearAgenteUrgencias() {
        try {
            ContainerController cc = getContainerController();
            AgentController ac = cc.createNewAgent(
                "urgencias",
                "primerproyectojava.AgenteUrgencias",
                new Object[]{}
            );
            ac.start();
            System.out.println("Clasificador: AgenteUrgencias creado.");
        } catch (StaleProxyException e) {
            System.out.println("Clasificador: error creando AgenteUrgencias");
            e.printStackTrace();
        }
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
 
 
    private void encolarPaciente(String mensajeParaMedico, String especialidad, String prioridad) {
        obtenerCola(especialidad).add(
            new PacienteEnCola(mensajeParaMedico, valorPrioridad(prioridad), contadorLlegada++)
        );
    }
 
    private int posicionEnCola(String especialidad, String mensaje) {
        PriorityQueue<PacienteEnCola> cola = colasPorEspecialidad.get(especialidad);
        if (cola == null) return 1;
 
        PriorityQueue<PacienteEnCola> copia = new PriorityQueue<>(cola);
        int posicion = 1;
        while (!copia.isEmpty()) {
            if (copia.poll().mensaje.equals(mensaje)) return posicion;
            posicion++;
        }
        return posicion;
    }
 
    private void imprimirColas() {
        System.out.println("\n--- COLAS POR ESPECIALIDAD ---");
        if (colasPorEspecialidad.isEmpty()) {
            System.out.println("(todas las colas vacías)");
            return;
        }
        for (Map.Entry<String, PriorityQueue<PacienteEnCola>> entrada
                : colasPorEspecialidad.entrySet()) {
            String especialidad = entrada.getKey();
            PriorityQueue<PacienteEnCola> cola = entrada.getValue();
            if (cola.isEmpty()) continue;
            System.out.println("  [" + especialidad + "] — " + cola.size() + " en espera:");
            PriorityQueue<PacienteEnCola> copia = new PriorityQueue<>(cola);
            int pos = 1;
            while (!copia.isEmpty()) {
                String msg = copia.poll().mensaje;
                String dni       = msg.split(",")[0];
                String prioridad = extraerCampo(msg, "prioridad_asignada=");
                String cita      = extraerCampo(msg, "cita_previa=");
                String medico    = extraerCampo(msg, "medico_asignado=");
                System.out.println("    " + pos++ + ". [" + prioridad + "] DNI=" + dni
                        + " | cita=" + cita + " | medico=" + medico);
            }
        }
    }
 
    private void recalcularTiemposEspera() {
        String nombreMonitor = buscarMonitorEnDF();
        if (nombreMonitor == null) return;
 
        int totalEnviados = 0;
 
        for (Map.Entry<String, PriorityQueue<PacienteEnCola>> entrada
                : colasPorEspecialidad.entrySet()) {
            PriorityQueue<PacienteEnCola> copia = new PriorityQueue<>(entrada.getValue());
            int posicion = 1;
            while (!copia.isEmpty()) {
                String mensaje = copia.poll().mensaje;
                String dni = mensaje.split(",")[0].trim();
 
                ACLMessage aviso = new ACLMessage(ACLMessage.INFORM);
                aviso.addReceiver(new AID(nombreMonitor, AID.ISLOCALNAME));
                aviso.setContent("ACTUALIZAR_ESPERA," + dni + ",posicion=" + posicion++);
                send(aviso);
                totalEnviados++;
            }
        }
 
        if (totalEnviados > 0) {
            System.out.println("Clasificador: posiciones recalculadas para "
                    + totalEnviados + " pacientes.");
        }
    }

    private void notificarSalidaDeCola(String mensajePaciente, String nombreMedico) {
        String nombreMonitor = buscarMonitorEnDF();
        if (nombreMonitor == null) return;
        String dni = mensajePaciente.split(",")[0].trim();
        ACLMessage aviso = new ACLMessage(ACLMessage.INFORM);
        aviso.addReceiver(new AID(nombreMonitor, AID.ISLOCALNAME));
        // Usamos EN_CONSULTA para que el monitor lo mueva del estado "En espera"
        // al estado "En consulta", igual que hace el AgenteMedico al recibirlo.
        aviso.setContent("EN_CONSULTA_DESDE_COLA," + mensajePaciente
                + ",medico=" + nombreMedico);
        send(aviso);
        System.out.println("Clasificador: EN_CONSULTA_DESDE_COLA enviado al monitor para " + dni);
    }
 
    private void notificarEspera(String mensajeParaMedico, String especialidad,
                                  String prioridad, String dni) {
        int posicion = posicionEnCola(especialidad, mensajeParaMedico);
        System.out.println("Clasificador: médico no disponible para " + especialidad
                + ". EN ESPERA. Posición en cola [" + especialidad + "] = " + posicion);
        String nombreMonitor = buscarMonitorEnDF();
        if (nombreMonitor != null) {
            ACLMessage avisoMonitor = new ACLMessage(ACLMessage.INFORM);
            avisoMonitor.addReceiver(new AID(nombreMonitor, AID.ISLOCALNAME));
            avisoMonitor.setContent("EN_ESPERA," + mensajeParaMedico + ",posicion=" + posicion);
            send(avisoMonitor);
            System.out.println("Clasificador: EN_ESPERA enviado al monitor para " + dni);
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
        } catch (FIPAException e) { e.printStackTrace(); }
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
        } catch (FIPAException e) { e.printStackTrace(); }
        return null;
    }
 
    private String buscarMedicoEspecificoEnDF(String nombreMedico) {
        try {
            DFAgentDescription template = new DFAgentDescription();
            template.setName(new AID(nombreMedico, AID.ISLOCALNAME));
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) return nombreMedico;
        } catch (FIPAException e) { e.printStackTrace(); }
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
                    if (!dni.isEmpty()) idPorDni.put(dni, patientId);
                }
            }
            sc.close();
            System.out.println("Clasificador: pacientes cargados = " + idPorDni.size());
        } catch (Exception e) {
            System.out.println("Clasificador: error leyendo patients.csv");
            e.printStackTrace();
        }
    }
 
    private void guardarPacienteNuevo(String paciente) {
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
                .max().orElse(0);
        return String.format("P%03d", max + 1);
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
                    String especialidad   = especialidadPorDoctor.get(doctorId);
                    if (especialidad != null && debeActualizarCita(patientId, fechaCita, motivoConsulta)) {
                        especialidadPorPaciente.put(patientId, especialidad);
                        motivoPorPaciente.put(patientId, motivoConsulta);
                        fechaCitaPorPaciente.put(patientId, fechaCita);
                        tieneCitaPorPaciente.put(patientId, true);
                        medicoPorPaciente.put(patientId, doctorId);
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
        if (patientId != null
                && tieneCitaPorPaciente.getOrDefault(patientId, false)
                && especialidadPorPaciente.containsKey(patientId)) {
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
        if (datos.length < 1) return "NORMAL";
 
        String dni       = datos[0].trim();
        String patientId = idPorDni.get(dni);
 
        if (patientId != null && tieneCitaPorPaciente.getOrDefault(patientId, false)) {
            return "CITA";
        }
 
        int puntuacion = 1;
        if (datos.length >= 5) {
            try {
                int edad = java.time.LocalDate.now().getYear()
                        - Integer.parseInt(datos[4].trim().substring(0, 4));
                if (edad < 5 || edad > 65) puntuacion += 1;
                if (edad > 75)             puntuacion += 1;
            } catch (Exception e) {
                return "NORMAL";
            }
        }
 
        if (puntuacion >= 3) return "ALTA";
        if (puntuacion == 2) return "MEDIA";
        return "NORMAL";
    }
 
    private boolean pacienteTieneCita(String paciente) {
        String[] datos   = paciente.split(",");
        if (datos.length < 1) return false;
        String dni       = datos[0].trim();
        String patientId = idPorDni.get(dni);
        return patientId != null && tieneCitaPorPaciente.getOrDefault(patientId, false);
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
 
    private String extraerCampo(String mensaje, String campo) {
        for (String parte : mensaje.split(",")) {
            if (parte.startsWith(campo)) return parte.replace(campo, "").trim();
        }
        return "-";
    }
 
    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException e) { e.printStackTrace(); }
        System.out.println("Clasificador: agente terminado.");
    }
}
