package primerproyectojava;

import jade.core.Runtime;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;

public class Main {
    public static void main(String[] args) throws Exception {

        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.GUI, "true");

        AgentContainer container = rt.createMainContainer(p);

        AgentController monitor      = container.createNewAgent("monitor",      "primerproyectojava.AgenteMonitor",      null);
        AgentController clasificador = container.createNewAgent("clasificador",  "primerproyectojava.AgenteClasificador", null);
        AgentController medico       = container.createNewAgent("medico",        "primerproyectojava.AgenteMedico",       null);
        AgentController recepcion    = container.createNewAgent("recepcion",     "primerproyectojava.AgenteRecepcion",    null);

        monitor.start();
        clasificador.start();
        medico.start();
        recepcion.start();
    }
}