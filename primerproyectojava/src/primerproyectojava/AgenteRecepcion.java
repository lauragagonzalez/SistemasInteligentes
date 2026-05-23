package primerproyectojava;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class AgenteRecepcion extends Agent {

    private static final long serialVersionUID = 1L;

    private JTextField campoNombre;
    private JTextField campoApellido;
    private JTextField campoDni;
    private JTextField campoFechaNacimiento;
    private JTextField campoTelefono;
    private JTextArea areaLog;
    private JButton botonEnviar;

    @Override
    protected void setup() {
        System.out.println("Recepcion: agente iniciado.");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("recepcion");
        sd.setName("servicio-recepcion");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("Recepcion registrada en DF");
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> crearVentana());

        addBehaviour(new CyclicBehaviour(this) {
            private static final long serialVersionUID = 1L;

            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    SwingUtilities.invokeLater(() ->
                        areaLog.append("[Confirmación] " + msg.getContent() + "\n")
                    );
                } else {
                    block();
                }
            }
        });
    }

    private boolean validarDNI(String dni) {
        return dni.matches("\\d{8}[A-Za-z]");
    }

    private boolean validarTelefono(String telefono) {
        return telefono.matches("\\d{9}");
    }

    // Devuelve null si es válida, o el mensaje de error si no lo es
    private String validarFecha(String fecha) {
        if (!fecha.matches("\\d{2}/\\d{2}/\\d{4}")) {
            return "Formato incorrecto (debe ser DD/MM/YYYY)";
        }
        try {
        	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT);
            LocalDate fechaNac = LocalDate.parse(fecha, formatter); // <-- CAMBIADO
            LocalDate hoy = LocalDate.now();
            if (fechaNac.isAfter(hoy)) {
                return "La fecha de nacimiento no puede ser futura";
            }
            if (fechaNac.isBefore(hoy.minusYears(120))) {
                return "Fecha de nacimiento no válida (más de 120 años)";
            }
            return null; // válida

        } catch (DateTimeParseException e) {
            return "Fecha inexistente (ej: 30/02/2020 no existe))";
        }
    }

    private void crearVentana() {
        JFrame ventana = new JFrame("Recepción del Hospital");
        ventana.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        ventana.setSize(480, 520);
        ventana.setLocationRelativeTo(null);
        ventana.setLayout(new BorderLayout(10, 10));

        JPanel panelFormulario = new JPanel(new GridLayout(6, 2, 8, 8));
        panelFormulario.setBorder(BorderFactory.createTitledBorder("Datos del paciente"));
        panelFormulario.setBackground(new Color(245, 245, 250));

        campoNombre          = new JTextField();
        campoApellido        = new JTextField();
        campoDni             = new JTextField();
        campoFechaNacimiento = new JTextField("DD/MM/YYYY");
        campoTelefono        = new JTextField();

        panelFormulario.add(new JLabel("  Nombre:"));
        panelFormulario.add(campoNombre);
        panelFormulario.add(new JLabel("  Apellido:"));
        panelFormulario.add(campoApellido);
        panelFormulario.add(new JLabel("  DNI / ID:"));
        panelFormulario.add(campoDni);
        panelFormulario.add(new JLabel("  Fecha nacimiento:"));
        panelFormulario.add(campoFechaNacimiento);
        panelFormulario.add(new JLabel("  Teléfono:"));
        panelFormulario.add(campoTelefono);

        botonEnviar = new JButton("Registrar y enviar paciente");
        botonEnviar.setBackground(new Color(70, 130, 180));
        botonEnviar.setForeground(Color.WHITE);
        botonEnviar.setFont(botonEnviar.getFont().deriveFont(Font.BOLD));
        panelFormulario.add(new JLabel());
        panelFormulario.add(botonEnviar);

        areaLog = new JTextArea(8, 40);
        areaLog.setEditable(false);
        areaLog.setFont(new Font("Monospaced", Font.PLAIN, 12));
        areaLog.setBackground(new Color(30, 30, 30));
        areaLog.setForeground(new Color(180, 255, 180));
        JScrollPane scroll = new JScrollPane(areaLog);
        scroll.setBorder(BorderFactory.createTitledBorder("Log de actividad"));

        botonEnviar.addActionListener(e -> enviarPaciente());

        ventana.add(panelFormulario, BorderLayout.CENTER);
        ventana.add(scroll, BorderLayout.SOUTH);
        ventana.setVisible(true);
    }

    private void enviarPaciente() {
        String nombre   = campoNombre.getText().trim();
        String apellido = campoApellido.getText().trim();
        String dni      = campoDni.getText().trim();
        String fecha    = campoFechaNacimiento.getText().trim();
        String telefono = campoTelefono.getText().trim();
        
        
       
        StringBuilder errores = new StringBuilder();

        if (nombre.isEmpty()) {
            errores.append("- Nombre vacío\n");
        }
        if (apellido.isEmpty()) {
            errores.append("- Apellido vacío\n");
        }
        if (!validarDNI(dni)) {
            errores.append("- DNI inválido (formato: 12345678A)\n");
        }

        String errorFecha = validarFecha(fecha);
        if (errorFecha != null) {
            errores.append("- Fecha: ").append(errorFecha).append("\n");
        }

        if (!validarTelefono(telefono)) {
            errores.append("- Teléfono inválido (debe tener 9 dígitos)\n");
        }

        if (errores.length() > 0) {
            JOptionPane.showMessageDialog(null,
                    "Corrige los siguientes errores:\n\n" + errores,
                    "Error de validación",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        
        // Convertir 
        String fechaConvertida;
        try {
            DateTimeFormatter entrada = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            DateTimeFormatter salida  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            fechaConvertida = LocalDate.parse(fecha, entrada).format(salida);
        } catch (Exception e) {
            fechaConvertida = fecha;
        }

        String contenidoPaciente = dni + "," + nombre + "," + apellido + ",," + fechaConvertida + "," + telefono;


        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID("clasificador", AID.ISLOCALNAME));
        msg.setContent(contenidoPaciente);
        send(msg);

        areaLog.append("[Enviado] " + nombre + " " + apellido + " (DNI: " + dni + ")\n");

        campoNombre.setText("");
        campoApellido.setText("");
        campoDni.setText("");
        campoFechaNacimiento.setText("DD/MM/YYYY");
        campoTelefono.setText("");
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println("Recepcion: agente terminado.");
    }
}
