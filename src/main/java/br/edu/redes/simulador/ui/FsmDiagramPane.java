package br.edu.redes.simulador.ui;

import br.edu.redes.simulador.fsm.FsmTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** JavaFX state graph with active endpoints and the last executed transition highlighted. */
public final class FsmDiagramPane extends Region {
    private static final Color INK = Color.web("#193632");
    private static final Color MUTED = Color.web("#71827B");
    private static final Color GREEN = Color.web("#147F70");
    private static final Color LIGHT = Color.web("#EFF7F3");
    private static final Color LINE = Color.web("#C9D9D1");
    private final Canvas canvas = new Canvas();
    private String protocol = "TCP";
    private List<String> states = List.of();
    private List<FsmTransition> transitions = List.of();
    private String stateA = "CLOSED";
    private String stateB = "CLOSED";
    private String lastFrom = "";
    private String lastTo = "";
    private String lastLabel = "Aguardando execução";

    public FsmDiagramPane() {
        getChildren().add(canvas);
        setMinSize(330, 310);
        setPrefSize(820, 420);
        widthProperty().addListener((o, a, b) -> redraw());
        heightProperty().addListener((o, a, b) -> redraw());
    }

    public void setDefinition(String protocol, List<FsmTransition> transitions) {
        this.protocol = protocol;
        setMinWidth(protocol.equals("UDP") ? 220 : 330);
        this.transitions = List.copyOf(transitions);
        LinkedHashSet<String> values = new LinkedHashSet<>();
        transitions.forEach(t -> { values.add(t.state()); values.add(t.nextState()); });
        this.states = new ArrayList<>(values);
        resetState();
    }

    public void setState(String host, String previous, String next, String label) {
        if (host.equals("A")) stateA = next;
        if (host.equals("B")) stateB = next;
        lastFrom = previous;
        lastTo = next;
        lastLabel = host + " · " + label;
        redraw();
    }

    public void resetState() {
        stateA = protocol.equals("UDP") ? "UDP_READY" : "CLOSED";
        stateB = stateA;
        lastFrom = "";
        lastTo = "";
        lastLabel = "Aguardando execução";
        redraw();
    }

    @Override protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        redraw();
    }

    private void redraw() {
        double width = Math.max(1, getWidth());
        double height = Math.max(1, getHeight());
        canvas.setWidth(width);
        canvas.setHeight(height);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE); g.fillRoundRect(0, 0, width, height, 18, 18);
        g.setFill(INK); g.setFont(Font.font("Arial", FontWeight.BOLD, 17));
        g.fillText(protocol.equals("UDP") ? "FSM UDP · serviço sem conexão" : "FSM TCP · conexão e transferência", 20, 30);
        g.setFill(MUTED); g.setFont(Font.font("Arial", 11)); g.fillText(lastLabel, 20, 50);
        Map<String, Point> points = positions(width, height);
        double radius = Math.max(22, Math.min(34, width / (states.size() > 1 ? 23 : 12)));
        if (protocol.equals("UDP")) radius = Math.max(32, Math.min(46, width / 12));

        g.setLineWidth(1.1);
        for (FsmTransition t : transitions) {
            if (t.state().equals(t.nextState())) continue;
            Point a = points.get(t.state()), b = points.get(t.nextState());
            if (a == null || b == null) continue;
            boolean active = t.state().equals(lastFrom) && t.nextState().equals(lastTo);
            g.setStroke(active ? GREEN : LINE); g.setLineWidth(active ? 2.5 : 1.0);
            double dx = b.x - a.x, dy = b.y - a.y;
            double length = Math.max(1, Math.hypot(dx, dy));
            double sx = a.x + dx / length * (radius + 2), sy = a.y + dy / length * (radius + 2);
            double ex = b.x - dx / length * (radius + 3), ey = b.y - dy / length * (radius + 3);
            g.strokeLine(sx, sy, ex, ey);
            double angle = Math.atan2(ey - sy, ex - sx);
            double ah = 7;
            g.strokeLine(ex, ey, ex - ah * Math.cos(angle - .48), ey - ah * Math.sin(angle - .48));
            g.strokeLine(ex, ey, ex - ah * Math.cos(angle + .48), ey - ah * Math.sin(angle + .48));
        }

        for (String state : states) {
            Point p = points.get(state); if (p == null) continue;
            boolean activeA = state.equals(stateA), activeB = state.equals(stateB);
            g.setFill(activeA || activeB ? Color.web("#D9F1E7") : LIGHT);
            g.setStroke(activeA || activeB ? GREEN : LINE); g.setLineWidth(activeA || activeB ? 2.2 : 1.0);
            g.fillRoundRect(p.x - radius, p.y - 20, radius * 2, 40, 12, 12);
            g.strokeRoundRect(p.x - radius, p.y - 20, radius * 2, 40, 12, 12);
            g.setFill(INK); g.setFont(Font.font("Arial", FontWeight.BOLD, Math.max(8, Math.min(11, radius / 2.6))));
            double labelWidth = state.length() * Math.max(5, radius / 5.5);
            g.fillText(state, p.x - labelWidth / 2, p.y + 3);
            String marker = activeA && activeB ? "A · B" : activeA ? "A" : activeB ? "B" : "";
            if (!marker.isEmpty()) {
                g.setFill(GREEN); g.setFont(Font.font("Arial", FontWeight.BOLD, 9));
                g.fillText(marker, p.x - (marker.length() * 2.5), p.y + 34);
            }
        }
        g.setFill(MUTED); g.setFont(Font.font("Arial", 10));
        g.fillText("A: " + stateA + "     B: " + stateB, 20, height - 16);
    }

    private Map<String, Point> positions(double width, double height) {
        java.util.LinkedHashMap<String, Point> map = new java.util.LinkedHashMap<>();
        if (protocol.equals("UDP")) {
            map.put("UDP_READY", new Point(width / 2, height / 2 + 5));
            return map;
        }
        double left = 56, right = width - 56;
        double gap = (right - left) / 4;
        double[] ys = {height * .25, height * .56, height * .82};
        List<String> order = List.of("CLOSED", "LISTEN", "SYN_SENT", "SYN_RCVD", "ESTABLISHED",
                "FIN_WAIT_1", "FIN_WAIT_2", "CLOSING", "TIME_WAIT", "CLOSE_WAIT", "LAST_ACK");
        for (int i = 0; i < order.size(); i++) {
            int row = i < 5 ? 0 : i < 10 ? 1 : 2;
            int col = i < 5 ? i : i < 10 ? i - 5 : 2;
            if (states.contains(order.get(i))) map.put(order.get(i), new Point(left + col * gap, ys[row]));
        }
        return map;
    }

    private record Point(double x, double y) { }
}
