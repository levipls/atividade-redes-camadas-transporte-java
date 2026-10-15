package br.edu.redes.simulador.ui;

import br.edu.redes.simulador.core.PacketTrace;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.ArrayList;
import java.util.List;

/** Scrollable JavaFX sequence view; losses are cut/red and retries are orange. */
public final class SequenceDiagramPane extends Region {
    private final Canvas canvas = new Canvas();
    private final List<PacketTrace> packets = new ArrayList<>();

    public SequenceDiagramPane() {
        getChildren().add(canvas);
        setMinHeight(300); setPrefHeight(350);
        widthProperty().addListener((o, a, b) -> redraw());
        heightProperty().addListener((o, a, b) -> redraw());
    }

    public void setPackets(List<PacketTrace> current) { packets.clear(); packets.addAll(current); redraw(); }
    public void addPacket(PacketTrace packet) { packets.add(packet); redraw(); }
    public void clearPackets() { packets.clear(); setMinHeight(300); redraw(); }

    @Override protected void layoutChildren() {
        canvas.setWidth(getWidth()); canvas.setHeight(Math.max(getHeight(), 300)); redraw();
    }

    private void redraw() {
        double width = Math.max(1, getWidth());
        double height = Math.max(getHeight(), 300);
        canvas.setWidth(width); canvas.setHeight(height);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.WHITE); g.fillRoundRect(0, 0, width, height, 14, 14);
        g.setFill(Color.web("#193632")); g.setFont(Font.font("Arial", FontWeight.BOLD, 15));
        g.fillText("Diagrama de sequência", 16, 24);
        double ax = 105, bx = Math.max(250, width - 110);
        g.setFill(Color.web("#147F70")); g.fillText("Host A", ax - 22, 52);
        g.setFill(Color.web("#147F70")); g.fillText("Host B", bx - 22, 52);
        g.setStroke(Color.web("#CAD9D2")); g.setLineDashes(4, 4);
        g.strokeLine(ax, 62, ax, height - 14); g.strokeLine(bx, 62, bx, height - 14); g.setLineDashes();
        int visible = Math.min(packets.size(), 40);
        int start = packets.size() - visible;
        double rowStep = visible == 0 ? 20 : Math.min(24, Math.max(8, (height - 84) / visible));
        double fontSize = Math.max(7, Math.min(10, rowStep - 2));
        for (int i = 0; i < visible; i++) {
            PacketTrace p = packets.get(start + i);
            double y = 68 + i * rowStep;
            boolean aToB = p.from().equals("A");
            double x1 = aToB ? ax : bx, x2 = aToB ? bx : ax;
            Color color = p.lost() ? Color.web("#C64C47")
                    : p.retransmission() ? Color.web("#D58B24")
                    : p.corrupted() ? Color.web("#BA5D92") : Color.web("#438678");
            g.setStroke(color); g.setFill(color); g.setLineWidth(p.retransmission() ? 2.2 : 1.3);
            if (p.lost()) {
                g.setLineDashes(7, 6); g.strokeLine(x1, y, x1 + (x2 - x1) * .43, y); g.setLineDashes();
                double markX = x1 + (x2 - x1) * .45; g.strokeLine(markX - 5, y - 5, markX + 5, y + 5); g.strokeLine(markX - 5, y + 5, markX + 5, y - 5);
            } else {
                g.strokeLine(x1, y, x2, y);
                double angle = aToB ? 0 : Math.PI;
                g.strokeLine(x2, y, x2 - 7 * Math.cos(angle - .5), y - 7 * Math.sin(angle - .5));
                g.strokeLine(x2, y, x2 - 7 * Math.cos(angle + .5), y - 7 * Math.sin(angle + .5));
            }
            g.setFont(Font.font("Arial", fontSize));
            String label = p.protocol() + "  " + p.label() + (p.duplicate() ? " · cópia" : "")
                    + (p.corrupted() ? " · corrompido" : "");
            double labelX = Math.min(x1, x2) + 8;
            g.fillText(label, labelX, y - 4);
            g.setFill(Color.web("#71827B")); g.fillText(p.timeMs() + " ms", 14, y + 4);
        }
    }
}
