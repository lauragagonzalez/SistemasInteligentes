package primerproyectojava;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public class AgenteUrgencias extends Agent {

    private static final long serialVersionUID = 1L;

    private static final int NUM_BOXES = 3;

    private static class EntradaCola {
        final String mensaje;
        final long   llegada;

        EntradaCola(String mensaje) {
            this.mensaje = mensaje;
            this.llegada = System.currentTimeMillis();
        }

        double puntuacion() {
            long segundosEspera = (System.currentTimeMillis() - llegada) / 1000;
            return puntosEdad(mensaje) + (segundosEspera / 30.0);
        }

        static int puntosEdad(String msg) {
            String[] partes = msg.split(",");
            try {
                int anio = Integer.parseInt(partes[4].trim().substring(0, 4));
                int edad = java.time.LocalDate.now().getYear() - anio;
                if (edad < 5  || edad > 70) return 10;
                if (edad < 16 || edad > 60) return 7;
                return 3;
            } catch (Exception e) {
                return 3;
            }
        }

        static String nivelUrgencia(String msg) {
            int pts = puntosEdad(msg);
            if (pts == 10) return "URGENCIA_ALTA";
            if (pts == 7)  return "URGENCIA_MEDIA";
            return "URGENCIA_BAJA";
        }
    }

    private final List<EntradaCola> cola = new ArrayList<>();
    private final boolean[] boxLibre = new boolean[NUM_BOXES];

    @Override
    protected void setup() {
        System.out.println("Urgencias: agente iniciado con " + NUM_BOXES + " boxes.");

        for (int i = 0; i < NUM_BOXES; i++) {
            boxLibre[i] = true;
        }

        registrarEnDF();

        addBehaviour(new CyclicBehaviour(this) {
            private static final long serialVersionUID = 1L;

            @Override
            public void action() {
                MessageTemplate filtro = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchSender(new AID("clasificador", AID.ISLOCALNAME))
                );
                ACLMessage msg = myAgent.receive(filtro);
                if (msg != null) {
                    synchronized (cola) {
                        cola.add(new EntradaCola(msg.getContent()));
                    }
                    System.out.println("Urgencias: paciente encolado → " + msg.getContent().split(",")[0]);
                    notificarEsperaMonitor(msg.getContent());
                    recalcularPosicionesMonitor();
                    despachar();
                } else {
                    block();
                }
            }
        });


        addBehaviour(new TickerBehaviour(this, 5000) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onTick() {
                synchronized (cola) {
                    cola.sort(Comparator.comparingDouble(EntradaCola::puntuacion).reversed());
                }
                despachar();
                recalcularPosicionesMonitor();
            }
        });


        for (int i = 0; i < NUM_BOXES; i++) {
            addBehaviour(new BoxBehaviour(this, i));
        }
    }


    synchronized void despachar() {
        for (int i = 0; i < NUM_BOXES; i++) {
            if (!boxLibre[i]) continue;
            EntradaCola siguiente;
            synchronized (cola) {
                if (cola.isEmpty()) return;
                siguiente = cola.remove(0);
            }
            boxLibre[i] = false;
            iniciarAtencion(i, siguiente.mensaje);
        }
    }

    private void iniciarAtencion(int boxId, String mensajePaciente) {
        System.out.println("Urgencias: Box-" + (boxId + 1) + " atiende → "
                + mensajePaciente.split(",")[0]);

        String nivel = EntradaCola.nivelUrgencia(mensajePaciente);
        String monitorNombre = buscarMonitor();
        if (monitorNombre != null) {
            ACLMessage notif = new ACLMessage(ACLMessage.INFORM);
            notif.addReceiver(new AID(monitorNombre, AID.ISLOCALNAME));
            notif.setContent("EN_CONSULTA," + mensajePaciente
                    + ",especialidad_asignada=urgencias"
                    + ",prioridad_asignada=" + nivel
                    + ",medico=urgencias-box" + (boxId + 1)
                    + ",sala=Urgencias-Box" + (boxId + 1));
            send(notif);
        }

        BoxBehaviour box = getBoxBehaviour(boxId);
        if (box != null) box.asignarPaciente(mensajePaciente, nivel);
    }


    private final BoxBehaviour[] boxes = new BoxBehaviour[NUM_BOXES];

    private BoxBehaviour getBoxBehaviour(int id) { return boxes[id]; }

    private class BoxBehaviour extends CyclicBehaviour {
        private static final long serialVersionUID = 1L;

        private final int    boxId;
        private String       pacienteActual = null;
        private String       nivelActual    = "URGENCIA_ALTA";
        private long         tiempoFin      = 0;

        BoxBehaviour(Agent a, int id) {
            super(a);
            this.boxId = id;
        }

        @Override
        public void onStart() {
            boxes[boxId] = this;
        }

        synchronized void asignarPaciente(String mensaje, String nivel) {
            this.pacienteActual = mensaje;
            this.nivelActual    = nivel;
            int segundos = 60 + (int)(Math.random() * 60); // 1-2 min
            this.tiempoFin = System.currentTimeMillis() + (segundos * 1000L);
            System.out.println("Urgencias Box-" + (boxId + 1) + ": atendiendo ["
                    + nivel + "] durante " + segundos + "s");
            restart();
        }

        @Override
        public void action() {
            if (pacienteActual == null) { block(); return; }
            if (System.currentTimeMillis() < tiempoFin) { block(500); return; }

            // Consulta terminada
            String monitorNombre = buscarMonitor();
            if (monitorNombre != null) {
                ACLMessage notif = new ACLMessage(ACLMessage.INFORM);
                notif.addReceiver(new AID(monitorNombre, AID.ISLOCALNAME));
                notif.setContent("ATENDIDO," + pacienteActual
                        + ",especialidad_asignada=urgencias"
                        + ",prioridad_asignada=" + nivelActual
                        + ",medico=urgencias-box" + (boxId + 1)
                        + ",sala=Urgencias-Box" + (boxId + 1));
                myAgent.send(notif);
            }

            System.out.println("Urgencias Box-" + (boxId + 1) + ": paciente atendido ["
                    + nivelActual + "] → " + pacienteActual.split(",")[0]);
            pacienteActual = null;
            boxLibre[boxId] = true;

            registrarEnDF();
            despachar();
            block();
        }
    }


    private void notificarEsperaMonitor(String mensaje) {
        String monitorNombre = buscarMonitor();
        if (monitorNombre == null) return;
        int posicion;
        synchronized (cola) { posicion = cola.size(); }
        String nivel = EntradaCola.nivelUrgencia(mensaje);
        ACLMessage notif = new ACLMessage(ACLMessage.INFORM);
        notif.addReceiver(new AID(monitorNombre, AID.ISLOCALNAME));
        notif.setContent("EN_ESPERA," + mensaje
                + ",especialidad_asignada=urgencias"
                + ",prioridad_asignada=" + nivel
                + ",posicion=" + posicion);
        send(notif);
    }

    private void recalcularPosicionesMonitor() {
        String monitorNombre = buscarMonitor();
        if (monitorNombre == null) return;
        synchronized (cola) {
            int pos = 1;
            for (EntradaCola e : cola) {
                String dni = e.mensaje.split(",")[0].trim();
                ACLMessage notif = new ACLMessage(ACLMessage.INFORM);
                notif.addReceiver(new AID(monitorNombre, AID.ISLOCALNAME));
                notif.setContent("ACTUALIZAR_ESPERA," + dni + ",posicion=" + pos++);
                send(notif);
            }
        }
    }


    private void registrarEnDF() {
        try {
            try { DFService.deregister(this); } catch (FIPAException ignored) {}
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("urgencias");
            sd.setName("servicio-urgencias");
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
    }

    private String buscarMonitor() {
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

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException e) { e.printStackTrace(); }
        System.out.println("Urgencias: agente terminado.");
    }
}
