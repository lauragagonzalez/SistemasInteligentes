package primerproyectojava;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class AgenteMonitor extends Agent {

    private static final long serialVersionUID = 1L;

    private DefaultTableModel modeloTabla;
    private JLabel etiquetaTotal;
    private JLabel etiquetaAlta;
    private JLabel etiquetaMedia;
    private JLabel etiquetaNormal;
    private int totalPacientes = 0;
    private int totalAlta      = 0;
    private int totalMedia     = 0;
    private int totalNormal    = 0;

    private Map<String, Integer> filasPorDni   = new HashMap<>();
    private Map<String, long[]>  tiemposPorDni = new HashMap<>();

    @Override
    protected void setup() {
        System.out.println("Monitor: agente iniciado.");

        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("monitor");
        sd.setName("servicio-monitor");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println("Monitor registrado en DF");
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> crearVentana());

        addBehaviour(new CyclicBehaviour(this) {
            private static final long serialVersionUID = 1L;

            @Override
            public void action() {
                MessageTemplate filtro = MessageTemplate.MatchPerformative(ACLMessage.INFORM);
                ACLMessage msg = myAgent.receive(filtro);

                if (msg != null) {
                    String contenido = msg.getContent();
                    System.out.println("Monitor: recibido -> " + contenido);
                    SwingUtilities.invokeLater(() -> procesarMensaje(contenido));
                } else {
                    block();
                }
            }
        });
    }

    private void crearVentana() {
        JFrame ventana = new JFrame("Panel de Control - Hospital");
        ventana.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        ventana.setSize(1050, 560);
        ventana.setLocationRelativeTo(null);
        ventana.setLayout(new BorderLayout(10, 10));
        ventana.getContentPane().setBackground(new Color(240, 240, 245));

        JLabel titulo = new JLabel("  Monitor de Pacientes Atendidos", SwingConstants.LEFT);
        titulo.setFont(new Font("SansSerif", Font.BOLD, 20));
        titulo.setForeground(new Color(50, 80, 140));
        titulo.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 0));
        ventana.add(titulo, BorderLayout.NORTH);

        // COLUMNA MÉDICO AÑADIDA (índice 3)
        String[] columnas = {"DNI / ID", "Nombre", "Especialidad", "Médico", "Sala", "Prioridad", "Espera est.", "Estado"};
        modeloTabla = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        JTable tabla = new JTable(modeloTabla);
        tabla.setRowHeight(28);
        tabla.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tabla.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        tabla.getTableHeader().setBackground(new Color(70, 130, 180));
        tabla.getTableHeader().setForeground(Color.WHITE);
        tabla.setSelectionBackground(new Color(200, 220, 255));

        // Columna Prioridad → ahora índice 4
        tabla.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                String val = value == null ? "" : value.toString();
                if (val.equals("ALTA")) {
                    setBackground(new Color(255, 100, 100));
                    setForeground(Color.WHITE);
                } else if (val.equals("MEDIA")) {
                    setBackground(new Color(255, 200, 80));
                    setForeground(new Color(80, 60, 0));
                } else {
                    setBackground(new Color(100, 200, 130));
                    setForeground(new Color(0, 60, 20));
                }
                if (isSelected) setBackground(new Color(180, 200, 255));
                return this;
            }
        });

        // Columna Espera est. → ahora índice 5
        tabla.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                String val = value == null ? "" : value.toString();
                if (val.equals("-") || val.isEmpty()) {
                    setBackground(new Color(220, 240, 220));
                    setForeground(new Color(0, 120, 40));
                } else if (val.equals("¡Ahora!")) {
                    setBackground(new Color(255, 100, 100));
                    setForeground(Color.WHITE);
                } else {
                    setBackground(new Color(255, 243, 205));
                    setForeground(new Color(130, 90, 0));
                }
                if (isSelected) setBackground(new Color(180, 200, 255));
                return this;
            }
        });

        // Columna Estado → ahora índice 6
        tabla.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                String val = value == null ? "" : value.toString();
                if (val.contains("consulta") || val.contains("espera")) {
                    setBackground(new Color(255, 243, 205));
                    setForeground(new Color(130, 90, 0));
                } else {
                    setBackground(new Color(200, 240, 210));
                    setForeground(new Color(0, 100, 30));
                }
                if (isSelected) setBackground(new Color(180, 200, 255));
                return this;
            }
        });

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        ventana.add(scroll, BorderLayout.CENTER);

        JPanel panelStats = new JPanel(new GridLayout(1, 4, 10, 0));
        panelStats.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        panelStats.setBackground(new Color(240, 240, 245));

        etiquetaTotal  = crearTarjetaStat("Total atendidos", "0", new Color(70,  130, 180));
        etiquetaAlta   = crearTarjetaStat("Prioridad ALTA",  "0", new Color(220, 80,  80));
        etiquetaMedia  = crearTarjetaStat("Prioridad MEDIA", "0", new Color(220, 170, 30));
        etiquetaNormal = crearTarjetaStat("Prioridad NORMAL","0", new Color(60,  180, 100));

        panelStats.add(envolverTarjeta(etiquetaTotal,  "Total atendidos", new Color(70,  130, 180)));
        panelStats.add(envolverTarjeta(etiquetaAlta,   "Prioridad ALTA",  new Color(220, 80,  80)));
        panelStats.add(envolverTarjeta(etiquetaMedia,  "Prioridad MEDIA", new Color(220, 170, 30)));
        panelStats.add(envolverTarjeta(etiquetaNormal, "Prioridad NORMAL",new Color(60,  180, 100)));

        ventana.add(panelStats, BorderLayout.SOUTH);
        ventana.setVisible(true);

        // Timer que actualiza los tiempos de espera cada minuto
        new javax.swing.Timer(60_000, e -> actualizarTiemposEspera()).start();
    }

    private void actualizarTiemposEspera() {
        long ahora = System.currentTimeMillis();
        for (Map.Entry<String, long[]> entry : tiemposPorDni.entrySet()) {
            String dni   = entry.getKey();
            long[] datos = entry.getValue();
            long restanteMs = (datos[1] * 60_000L) - (ahora - datos[0]);
            Integer fila = filasPorDni.get(dni);

            if (fila != null) {
                if (restanteMs <= 0) {
                    modeloTabla.setValueAt("¡Ahora!", fila, 6);
                } else {
                    long minutos = restanteMs / 60_000;
                    modeloTabla.setValueAt(minutos + "min", fila, 6);
                }
            }
        }
    }

    private JLabel crearTarjetaStat(String titulo, String valor, Color color) {
        JLabel lbl = new JLabel(valor, SwingConstants.CENTER);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 32));
        lbl.setForeground(color);
        return lbl;
    }

    private JPanel envolverTarjeta(JLabel lblNumero, String titulo, Color color) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(color, 2, true),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        JLabel lblTitulo = new JLabel(titulo, SwingConstants.CENTER);
        lblTitulo.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblTitulo.setForeground(Color.GRAY);
        panel.add(lblNumero, BorderLayout.CENTER);
        panel.add(lblTitulo, BorderLayout.SOUTH);
        return panel;
    }

    private void procesarMensaje(String contenido) {

        boolean enEspera   = contenido.startsWith("EN_ESPERA,");
        boolean enConsulta = contenido.startsWith("EN_CONSULTA,");
        boolean atendido   = contenido.startsWith("ATENDIDO,");

        String datos = contenido;
        if (enEspera)   datos = contenido.substring("EN_ESPERA,".length());
        if (enConsulta) datos = contenido.substring("EN_CONSULTA,".length());
        if (atendido)   datos = contenido.substring("ATENDIDO,".length());

        String dni          = "?";
        String nombre       = "?";
        String especialidad = "?";
        String medico       = "-";   
        String sala		    = "-";		// NUEVO
        String prioridad    = "?";
        String espera       = "-";

        String[] partes = datos.split(",");
        if (partes.length > 0) dni    = partes[0].trim();
        if (partes.length > 1) nombre = partes[1].trim()
                + (partes.length > 2 ? " " + partes[2].trim() : "");

        for (String parte : partes) {
            if (parte.startsWith("especialidad_asignada="))
                especialidad = parte.replace("especialidad_asignada=", "").trim();
            if (parte.startsWith("prioridad_asignada="))
                prioridad = parte.replace("prioridad_asignada=", "").trim();
            if (parte.startsWith("espera_estimada="))
                espera = parte.replace("espera_estimada=", "").trim();
            if (parte.startsWith("medico="))               
                medico = parte.replace("medico=", "").trim();
            if (parte.startsWith("sala="))				// NUEVO
                sala = parte.replace("sala=", "").trim();
        }

        if (enEspera) {
            try {
                int minutosEspera = Integer.parseInt(espera.replace(" min", "").trim());
                tiemposPorDni.put(dni, new long[]{System.currentTimeMillis(), minutosEspera});
            } catch (NumberFormatException ex) { }

            // médico en col 3, prioridad en col 4, espera en col 5, estado en col 6
            modeloTabla.addRow(new Object[]{dni, nombre, especialidad, medico, sala, prioridad, espera, "En espera"});
            filasPorDni.put(dni, modeloTabla.getRowCount() - 1);

        } else if (enConsulta) {
            tiemposPorDni.remove(dni);
            Integer fila = filasPorDni.get(dni);
            if (fila != null) {
                modeloTabla.setValueAt(medico, fila, 3);      
                modeloTabla.setValueAt(sala,   fila, 4);
                modeloTabla.setValueAt("-",    fila, 6);       // espera
                modeloTabla.setValueAt("En consulta", fila, 7); // estado
            } else {
                modeloTabla.addRow(new Object[]{dni, nombre, especialidad, medico, sala, prioridad, "-", "En consulta"});
                filasPorDni.put(dni, modeloTabla.getRowCount() - 1);
            }

        } else if (atendido) {
            tiemposPorDni.remove(dni);
            Integer fila = filasPorDni.get(dni);
            if (fila != null) {
                modeloTabla.setValueAt("-",        fila, 6); // espera
                modeloTabla.setValueAt("Atendido", fila, 7); // estado
            }
            totalPacientes++;
            etiquetaTotal.setText(String.valueOf(totalPacientes));
            switch (prioridad) {
                case "ALTA":  totalAlta++;   etiquetaAlta.setText(String.valueOf(totalAlta));    break;
                case "MEDIA": totalMedia++;  etiquetaMedia.setText(String.valueOf(totalMedia));   break;
                default:      totalNormal++; etiquetaNormal.setText(String.valueOf(totalNormal)); break;
            }
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }
        System.out.println("Monitor: agente terminado.");
    }
}