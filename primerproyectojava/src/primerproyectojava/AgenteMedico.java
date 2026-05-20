package primerproyectojava;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

public class AgenteMedico extends Agent {

    private static final long serialVersionUID = 1L;
    private String especialidad = "medicina_general";
    private String sala = "Sala-desconocida";

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            especialidad = args[0].toString().toLowerCase().trim();
        }
        
        if (args != null && args.length > 1) {
            sala = args[1].toString().trim();
        } else {
            sala = "Sala-" + getLocalName().replace("medico_", "");
        }

        System.out.println("Medico: El doctor [" + getLocalName() + "] ha llegado. Especialidad: "
                + especialidad + ". Sala: " + sala);
        registrarEnDF();

        addBehaviour(new CyclicBehaviour(this) {

            private static final long serialVersionUID = 1L;
            private ACLMessage msgEnProceso = null;
            private long tiempoFinConsulta  = 0;
            private String nombreMonitor    = null;

            @Override
            public void action() {

                if (msgEnProceso != null) {
                    if (System.currentTimeMillis() < tiempoFinConsulta) {
                        block(500);
                        return;
                    }

                    registrarEnDF();
                    System.out.println("Medico [" + getLocalName() + "]: disponible de nuevo.");

                    ACLMessage respuesta = msgEnProceso.createReply();
                    respuesta.setPerformative(ACLMessage.INFORM);
                    respuesta.setContent("Paciente atendido correctamente.");
                    myAgent.send(respuesta);

                    if (nombreMonitor != null) {
                        ACLMessage notifFin = new ACLMessage(ACLMessage.INFORM);
                        notifFin.addReceiver(new AID(nombreMonitor, AID.ISLOCALNAME));
                        notifFin.setContent("ATENDIDO," + msgEnProceso.getContent()
                        	+ ",medico=" + getLocalName()
                        	+ ",sala=" + sala);
                        myAgent.send(notifFin);
                        System.out.println("Medico [" + getLocalName() + "]: monitor notificado -> ATENDIDO");
                    }

                    msgEnProceso = null;
                    return;
                }

                MessageTemplate filtro = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                    MessageTemplate.MatchSender(new AID("clasificador", AID.ISLOCALNAME))
                );

                ACLMessage msg = myAgent.receive(filtro);
                if (msg == null) {
                    block();
                    return;
                }

                String contenido = msg.getContent();
                System.out.println("\nMedico [" + getLocalName() + "]: paciente entrando a consulta...");
                System.out.println("Medico [" + getLocalName() + "]: datos -> " + contenido);

                // Desregistrarse del DF — ocupado
                try {
                    DFService.deregister(myAgent);
                    System.out.println("Medico [" + getLocalName() + "]: ocupado, desregistrado del DF.");
                } catch (FIPAException e) {
                    e.printStackTrace();
                }

                nombreMonitor = buscarMonitorEnDF();
                if (nombreMonitor != null) {
                    ACLMessage notifEntrada = new ACLMessage(ACLMessage.INFORM);
                    notifEntrada.addReceiver(new AID(nombreMonitor, AID.ISLOCALNAME));
                    notifEntrada.setContent("EN_CONSULTA," + contenido
                            + ",medico=" + getLocalName()
                            + ",sala=" + sala);
                    myAgent.send(notifEntrada);
                    System.out.println("Medico [" + getLocalName() + "]: monitor avisado -> EN consulta");
                }

                int segundosConsulta = 90 + (int)(Math.random() * 5);
                System.out.println("Medico [" + getLocalName() + "]: consultando durante " + segundosConsulta + " segundos...");
                tiempoFinConsulta = System.currentTimeMillis() + (segundosConsulta * 1000L);
                msgEnProceso = msg;
            }
        });
    }

    private void registrarEnDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());
            ServiceDescription sd = new ServiceDescription();
            sd.setType("medico");
            sd.setName("medico-" + especialidad);
            sd.addProperties(new jade.domain.FIPAAgentManagement.Property("especialidad", especialidad));
            dfd.addServices(sd);
            DFService.register(this, dfd);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
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

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println("Medico [" + getLocalName() + "]: agente terminado.");
    }
}
