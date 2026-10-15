package br.edu.redes.simulador.ui;

import br.edu.redes.simulador.core.ChannelConfig;
import br.edu.redes.simulador.core.PacketTrace;
import br.edu.redes.simulador.core.TransitionLog;
import br.edu.redes.simulador.fsm.FsmDefinition;
import br.edu.redes.simulador.scenario.ScenarioResult;
import br.edu.redes.simulador.scenario.ScenarioRunner;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** Main JavaFX screen for channel controls, FSM graph, sequence and filtered transition log. */
public final class MainView extends BorderPane {
    private static final String VIEW_TCP = "TCP";
    private static final String VIEW_UDP = "UDP";
    private static final String VIEW_COMPARE = "Comparação lado a lado";
    private final ScenarioRunner runner = new ScenarioRunner();
    private final ComboBox<ScenarioChoice> scenarioChoice = new ComboBox<>();
    private final ComboBox<String> protocolView = new ComboBox<>();
    private final ComboBox<String> logFilter = new ComboBox<>();
    private final Slider lossSlider = slider(0, 60, 0);
    private final Slider minDelaySlider = slider(0, 20, 1);
    private final Slider maxDelaySlider = slider(1, 50, 1);
    private final Slider corruptionSlider = slider(0, 60, 0);
    private final Slider duplicationSlider = slider(0, 60, 0);
    private final Slider reorderingSlider = slider(0, 60, 0);
    private final Slider timeWaitSlider = slider(10, 300, 80);
    private final Slider speedSlider = slider(0.5, 4, 1.5);
    private final TextField seedField = new TextField("2025");
    private final Button dropButton = new Button("⌖  Descartar próximo pacote");
    private final Label resultLabel = new Label("Pronto para simular");
    private final Label resultDetail = new Label("Escolha um cenário e pressione Iniciar.");
    private final Label progressLabel = new Label("0 eventos");
    private final VBox comparisonPanel = new VBox(6);
    private final FsmDiagramPane tcpDiagram = new FsmDiagramPane();
    private final FsmDiagramPane udpDiagram = new FsmDiagramPane();
    private final SequenceDiagramPane sequenceDiagram = new SequenceDiagramPane();
    private final ScrollPane sequenceScroll = new ScrollPane();
    private final ObservableList<TransitionLog> logItems = FXCollections.observableArrayList();
    private final FilteredList<TransitionLog> filteredLogs = new FilteredList<>(logItems, item -> true);
    private final TableView<TransitionLog> logTable = new TableView<>(filteredLogs);
    private final Timeline playback = new Timeline(new KeyFrame(Duration.millis(550), e -> showNextEvent()));
    private final List<DisplayEvent> displayEvents = new ArrayList<>();
    private int cursor;
    private boolean manualDropArmed;
    private ScenarioResult currentResult;
    private HBox diagramRow;

    public MainView(Stage stage) {
        setPadding(new Insets(12));
        getStyleClass().add("app-root");
        loadDefinitions();
        setTop(buildHeader(stage));
        setCenter(buildWorkspace());
        setBottom(buildStatusBar());
        protocolView.getSelectionModel().select(VIEW_TCP);
        scenarioChoice.getSelectionModel().select(0);
        scenarioChoice.setOnAction(e -> {
            ScenarioChoice choice = scenarioChoice.getValue();
            if (choice != null) {
                protocolView.getSelectionModel().select(choice.id == 8 || choice.id == 9 ? VIEW_UDP
                        : choice.id == 10 ? VIEW_COMPARE : VIEW_TCP);
                resetPlayback();
            }
        });
        protocolView.setOnAction(e -> rebuildVisibleState());
        logFilter.setOnAction(e -> applyLogFilter());
        speedSlider.valueProperty().addListener((o, oldValue, newValue) -> playback.setRate(newValue.doubleValue()));
        minDelaySlider.valueProperty().addListener((o, oldValue, newValue) -> {
            if (maxDelaySlider.getValue() < newValue.doubleValue()) maxDelaySlider.setValue(newValue.doubleValue());
            updateSliderLabels();
        });
        maxDelaySlider.valueProperty().addListener((o, oldValue, newValue) -> {
            if (newValue.doubleValue() < minDelaySlider.getValue()) minDelaySlider.setValue(newValue.doubleValue());
            updateSliderLabels();
        });
        updateSliderLabels();
    }

    private Node buildHeader(Stage stage) {
        Label mark = new Label("R"); mark.getStyleClass().add("brand-mark");
        VBox titles = new VBox(2);
        Label title = new Label("Máquinas de estado em ação"); title.getStyleClass().add("page-title");
        Label subtitle = new Label("REDES CONVERGENTES  ·  UDP / TCP  ·  JAVA + JAVAFX"); subtitle.getStyleClass().add("eyebrow");
        titles.getChildren().addAll(subtitle, title);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label("SEED 2025 · RELÓGIO VIRTUAL"); badge.getStyleClass().add("header-badge");
        HBox header = new HBox(14, mark, titles, spacer, badge); header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("topbar"); header.setPadding(new Insets(9, 8, 15, 8));
        stage.setTitle("Simulador FSM UDP e TCP — Redes Convergentes");
        return header;
    }

    private Node buildWorkspace() {
        VBox controls = buildControls();
        VBox diagrams = buildDiagrams();
        VBox log = buildLogPanel();
        HBox workspace = new HBox(12, controls, diagrams, log);
        workspace.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(diagrams, Priority.ALWAYS);
        controls.setPrefWidth(260); controls.setMinWidth(245); controls.setMaxWidth(280);
        diagrams.setMinWidth(430);
        log.setPrefWidth(430); log.setMinWidth(390); log.setMaxWidth(470);
        return workspace;
    }

    private VBox buildControls() {
        VBox panel = new VBox(11); panel.getStyleClass().add("panel"); panel.setPadding(new Insets(15));
        Label heading = sectionHeading("CONFIGURAÇÃO");
        scenarioChoice.getItems().setAll(
                new ScenarioChoice(1, "01 · Handshake sem falhas"),
                new ScenarioChoice(2, "02 · Perda do SYN"),
                new ScenarioChoice(3, "03 · Perda do SYN-ACK"),
                new ScenarioChoice(4, "04 · Encerramento normal"),
                new ScenarioChoice(5, "05 · Fechamento simultâneo"),
                new ScenarioChoice(6, "06 · Fast retransmit"),
                new ScenarioChoice(7, "07 · Porta fechada / RST"),
                new ScenarioChoice(8, "08 · UDP: perda 30%"),
                new ScenarioChoice(9, "09 · UDP: checksum"),
                new ScenarioChoice(10, "10 · UDP × TCP"));
        scenarioChoice.setMaxWidth(Double.MAX_VALUE); scenarioChoice.getStyleClass().add("field");
        protocolView.getItems().setAll(VIEW_TCP, VIEW_UDP, VIEW_COMPARE);
        protocolView.setMaxWidth(Double.MAX_VALUE); protocolView.getStyleClass().add("field");
        Label scenarioLabel = fieldLabel("Cenário");
        Label protocolLabel = fieldLabel("Visualização");
        panel.getChildren().addAll(heading, scenarioLabel, scenarioChoice, protocolLabel, protocolView,
                new Separator(), sectionHeading("PARÂMETROS DO CANAL"),
                sliderField("Perda", lossSlider, "%"),
                sliderField("Atraso mínimo", minDelaySlider, " ms"),
                sliderField("Atraso máximo", maxDelaySlider, " ms"),
                sliderField("Corrupção", corruptionSlider, "%"),
                sliderField("Duplicação", duplicationSlider, "%"),
                sliderField("Reordenação", reorderingSlider, "%"),
                sliderField("2×MSL · TIME_WAIT", timeWaitSlider, " ms"));
        HBox seedRow = new HBox(8, fieldLabel("Seed"), seedField); seedRow.setAlignment(Pos.CENTER_LEFT);
        seedField.setPrefColumnCount(8); seedField.getStyleClass().add("field");
        panel.getChildren().addAll(seedRow, dropButton);
        dropButton.getStyleClass().add("drop-button"); dropButton.setMaxWidth(Double.MAX_VALUE);
        dropButton.setOnAction(e -> {
            manualDropArmed = !manualDropArmed;
            dropButton.setText(manualDropArmed ? "✓  Próximo pacote será perdido" : "⌖  Descartar próximo pacote");
            dropButton.getStyleClass().removeAll("drop-armed", "drop-idle");
            dropButton.getStyleClass().add(manualDropArmed ? "drop-armed" : "drop-idle");
        });
        panel.getChildren().addAll(new Separator(), sectionHeading("REPRODUÇÃO"), buildPlaybackControls(),
                sliderField("Velocidade", speedSlider, "×"), new Separator(),
                resultLabel, resultDetail);
        resultLabel.getStyleClass().add("result-label"); resultDetail.getStyleClass().add("result-detail");
        ScrollPane scroller = new ScrollPane(panel); scroller.setFitToWidth(true); scroller.getStyleClass().add("control-scroll");
        VBox wrapper = new VBox(scroller); VBox.setVgrow(scroller, Priority.ALWAYS); wrapper.setPrefWidth(265);
        return wrapper;
    }

    private Node buildPlaybackControls() {
        Button start = new Button("▶  Iniciar"); start.getStyleClass().add("primary-button");
        Button pause = new Button("Ⅱ"); pause.getStyleClass().add("plain-button"); pause.setTooltip(new javafx.scene.control.Tooltip("Pausar ou continuar"));
        Button step = new Button("Passo"); step.getStyleClass().add("plain-button");
        Button reset = new Button("↻"); reset.getStyleClass().add("plain-button"); reset.setTooltip(new javafx.scene.control.Tooltip("Reiniciar visualização"));
        start.setOnAction(e -> runScenario());
        pause.setOnAction(e -> { if (playback.getStatus() == javafx.animation.Animation.Status.RUNNING) playback.pause(); else playback.play(); });
        step.setOnAction(e -> { playback.pause(); showNextEvent(); });
        reset.setOnAction(e -> resetPlayback());
        HBox row = new HBox(6, start, pause, step, reset); row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(start, Priority.ALWAYS);
        start.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private VBox buildDiagrams() {
        VBox panel = new VBox(10); panel.getStyleClass().add("diagram-column");
        diagramRow = new HBox(10, tcpDiagram);
        HBox.setHgrow(tcpDiagram, Priority.ALWAYS); HBox.setHgrow(udpDiagram, Priority.ALWAYS);
        ScrollPane graphScroll = new ScrollPane(diagramRow); graphScroll.setFitToWidth(true); graphScroll.setFitToHeight(true);
        graphScroll.setMinHeight(365); graphScroll.setPrefHeight(450); graphScroll.getStyleClass().add("diagram-scroll");
        VBox sequenceCard = new VBox(sequenceDiagram); sequenceCard.getStyleClass().add("panel");
        sequenceScroll.setContent(sequenceCard); sequenceScroll.setFitToWidth(true); sequenceScroll.setMinHeight(240); sequenceScroll.setPrefHeight(305);
        comparisonPanel.getStyleClass().add("panel"); comparisonPanel.setPadding(new Insets(10));
        comparisonPanel.setVisible(false); comparisonPanel.setManaged(false);
        VBox.setVgrow(graphScroll, Priority.ALWAYS); VBox.setVgrow(sequenceScroll, Priority.ALWAYS);
        panel.getChildren().addAll(graphScroll, comparisonPanel, sequenceScroll);
        return panel;
    }

    private VBox buildLogPanel() {
        VBox panel = new VBox(9); panel.getStyleClass().add("panel"); panel.setPadding(new Insets(14));
        HBox titleRow = new HBox(8, sectionHeading("LOG DE TRANSIÇÕES"));
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        progressLabel.getStyleClass().add("eyebrow"); titleRow.getChildren().addAll(spacer, progressLabel);
        logFilter.getItems().setAll("Todos", "TCP", "UDP"); logFilter.getSelectionModel().selectFirst();
        logFilter.setMaxWidth(Double.MAX_VALUE); logFilter.getStyleClass().add("field");
        configureTable();
        VBox.setVgrow(logTable, Priority.ALWAYS);
        panel.getChildren().addAll(titleRow, logFilter, logTable);
        return panel;
    }

    private void configureTable() {
        logTable.setPlaceholder(new Label("As transições aparecerão aqui."));
        logTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        logTable.getColumns().setAll(
                column("ms", 44, r -> r.timeMs() + ""),
                column("Host", 44, TransitionLog::host),
                column("Estado anterior", 76, TransitionLog::previousState),
                column("Evento", 80, TransitionLog::event),
                column("Ação", 90, TransitionLog::action),
                column("Novo estado", 76, TransitionLog::nextState));
        logTable.setRowFactory(table -> new javafx.scene.control.TableRow<>() {
            @Override protected void updateItem(TransitionLog item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("rejected-row", "transition-row");
                if (!empty && item != null) getStyleClass().add(item.accepted() ? "transition-row" : "rejected-row");
            }
        });
    }

    private TableColumn<TransitionLog, String> column(String title, double width, Function<TransitionLog, String> getter) {
        TableColumn<TransitionLog, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(getter.apply(cell.getValue())));
        return column;
    }

    private Node buildStatusBar() {
        HBox bar = new HBox(10, new Label("●"), new Label("Canal em memória"), new Separator(),
                new Label("FSM dirigida por dados"), new Separator(), new Label("Junit: cenários executados sem GUI"));
        bar.setAlignment(Pos.CENTER_LEFT); bar.getStyleClass().add("statusbar"); bar.setPadding(new Insets(10, 6, 2, 6));
        return bar;
    }

    private void loadDefinitions() {
        tcpDiagram.setDefinition("TCP", FsmDefinition.loadResource("/fsm/tcp_connection.fsm").transitions());
        udpDiagram.setDefinition("UDP", FsmDefinition.loadResource("/fsm/udp.fsm").transitions());
    }

    private void runScenario() {
        playback.stop();
        try {
            ScenarioChoice selected = scenarioChoice.getValue();
            if (selected == null) return;
            long seed = Long.parseLong(seedField.getText().trim());
            int min = (int) Math.round(minDelaySlider.getValue());
            int max = Math.max(min, (int) Math.round(maxDelaySlider.getValue()));
            ChannelConfig config = new ChannelConfig((int) Math.round(lossSlider.getValue()), min, max,
                    (int) Math.round(corruptionSlider.getValue()), (int) Math.round(duplicationSlider.getValue()),
                    (int) Math.round(reorderingSlider.getValue()), (int) Math.round(timeWaitSlider.getValue()), seed, manualDropArmed);
            manualDropArmed = false; dropButton.setText("⌖  Descartar próximo pacote");
            dropButton.getStyleClass().remove("drop-armed"); dropButton.getStyleClass().add("drop-idle");
            currentResult = runner.run(selected.id, config);
            displayEvents.clear();
            currentResult.trace().transitions().forEach(log -> displayEvents.add(new DisplayEvent(log.timeMs(), 0, log, null)));
            currentResult.trace().packets().forEach(packet -> displayEvents.add(new DisplayEvent(packet.timeMs(), 1, null, packet)));
            displayEvents.sort(Comparator.comparingLong(DisplayEvent::timeMs).thenComparingInt(DisplayEvent::order));
            cursor = 0; logItems.clear(); sequenceDiagram.clearPackets();
            tcpDiagram.resetState(); udpDiagram.resetState();
            resultLabel.setText("CENÁRIO " + String.format("%02d", currentResult.id()) + " · EM EXECUÇÃO");
            resultLabel.getStyleClass().removeAll("result-approved", "result-failed");
            resultDetail.setText(currentResult.summary());
            updateProgress();
            playback.setRate(speedSlider.getValue());
            playback.playFromStart();
        } catch (NumberFormatException ex) {
            resultLabel.setText("Seed inválida"); resultDetail.setText("Informe um número inteiro para reproduzir a simulação.");
        } catch (RuntimeException ex) {
            resultLabel.setText("Falha na simulação"); resultDetail.setText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
        }
    }

    /** Prepares a fully rendered scenario for deterministic screenshots and demonstrations. */
    public void renderScenarioImmediately(int id, ChannelConfig config) {
        playback.stop();
        scenarioChoice.getSelectionModel().select(id - 1);
        protocolView.getSelectionModel().select(id == 8 || id == 9 ? VIEW_UDP : id == 10 ? VIEW_COMPARE : VIEW_TCP);
        currentResult = runner.run(id, config);
        displayEvents.clear();
        currentResult.trace().transitions().forEach(log -> displayEvents.add(new DisplayEvent(log.timeMs(), 0, log, null)));
        currentResult.trace().packets().forEach(packet -> displayEvents.add(new DisplayEvent(packet.timeMs(), 1, null, packet)));
        displayEvents.sort(Comparator.comparingLong(DisplayEvent::timeMs).thenComparingInt(DisplayEvent::order));
        cursor = 0; logItems.clear(); sequenceDiagram.clearPackets(); tcpDiagram.resetState(); udpDiagram.resetState();
        while (cursor < displayEvents.size()) {
            DisplayEvent event = displayEvents.get(cursor++);
            if (event.log != null) {
                TransitionLog log = event.log; logItems.add(log);
                (log.protocol().equals("UDP") ? udpDiagram : tcpDiagram)
                        .setState(log.host(), log.previousState(), log.nextState(), log.event() + " / " + log.action());
            } else if (event.packet != null && shouldShowPacket(event.packet)) sequenceDiagram.addPacket(event.packet);
        }
        logTable.scrollTo(Math.max(0, logItems.size() - 1));
        sequenceScroll.setVvalue(1.0);
        resultLabel.setText(currentResult.passed() ? "✓  APROVADO" : "✕  REPROVADO");
        resultLabel.getStyleClass().removeAll("result-approved", "result-failed");
        resultLabel.getStyleClass().add(currentResult.passed() ? "result-approved" : "result-failed");
        resultDetail.setText(currentResult.summary() + "  " + metricSummary(currentResult));
        updateProgress(); applyLogFilter(); updateGraphVisibility(); updateComparisonPanel();
    }

    private void showNextEvent() {
        if (cursor >= displayEvents.size()) {
            playback.stop(); finishScenario(); return;
        }
        DisplayEvent event = displayEvents.get(cursor++);
        if (event.log != null) {
            TransitionLog log = event.log;
            logItems.add(log);
            FsmDiagramPane graph = log.protocol().equals("UDP") ? udpDiagram : tcpDiagram;
            graph.setState(log.host(), log.previousState(), log.nextState(), log.event() + " / " + log.action());
        } else if (event.packet != null && shouldShowPacket(event.packet)) {
            sequenceDiagram.addPacket(event.packet);
            Platform.runLater(() -> sequenceScroll.setVvalue(1.0));
        }
        if (event.log != null) logTable.scrollTo(Math.max(0, logItems.size() - 1));
        updateProgress();
        if (cursor >= displayEvents.size()) { playback.stop(); finishScenario(); }
    }

    private boolean shouldShowPacket(PacketTrace packet) {
        String view = protocolView.getValue();
        return view.equals(VIEW_COMPARE) || packet.protocol().equals(view);
    }

    private void finishScenario() {
        if (currentResult == null) return;
        resultLabel.setText(currentResult.passed() ? "✓  APROVADO" : "✕  REPROVADO");
        resultLabel.getStyleClass().removeAll("result-approved", "result-failed");
        resultLabel.getStyleClass().add(currentResult.passed() ? "result-approved" : "result-failed");
        resultDetail.setText(currentResult.summary() + "  " + metricSummary(currentResult));
        updateProgress(); updateComparisonPanel();
    }

    private String metricSummary(ScenarioResult result) {
        if (result.id() == 6) return "Economia estimada: " + result.metrics().get("savedMs") + " ms.";
        if (result.id() == 8 || result.id() == 9) return "Entregues: " + result.metrics().get("delivered") + "/" + result.metrics().get("sent") + ".";
        if (result.id() == 10) return "Consulte as métricas de UDP e TCP no painel de comparação.";
        return "";
    }

    private void resetPlayback() {
        playback.stop(); cursor = 0; currentResult = null; displayEvents.clear(); logItems.clear(); sequenceDiagram.clearPackets();
        tcpDiagram.resetState(); udpDiagram.resetState();
        resultLabel.setText("Pronto para simular"); resultDetail.setText("Escolha um cenário e pressione Iniciar.");
        progressLabel.setText("0 eventos");
        updateGraphVisibility(); updateComparisonPanel();
    }

    private void rebuildVisibleState() {
        if (currentResult != null) {
            List<TransitionLog> priorLogs = List.copyOf(logItems);
            List<PacketTrace> priorPackets = new ArrayList<>();
            for (int i = 0; i < cursor; i++) {
                DisplayEvent e = displayEvents.get(i);
                if (e.packet != null && shouldShowPacket(e.packet)) priorPackets.add(e.packet);
            }
            sequenceDiagram.setPackets(priorPackets);
            applyLogFilter();
            tcpDiagram.resetState(); udpDiagram.resetState();
            priorLogs.forEach(log -> (log.protocol().equals("UDP") ? udpDiagram : tcpDiagram)
                    .setState(log.host(), log.previousState(), log.nextState(), log.event() + " / " + log.action()));
        }
        updateGraphVisibility(); updateComparisonPanel();
    }

    private void updateGraphVisibility() {
        String view = protocolView.getValue();
        if (view == null) view = VIEW_TCP;
        diagramRow.getChildren().setAll(view.equals(VIEW_UDP) ? List.of(udpDiagram)
                : view.equals(VIEW_COMPARE) ? List.of(tcpDiagram, udpDiagram) : List.of(tcpDiagram));
        if (view.equals(VIEW_COMPARE)) {
            tcpDiagram.setPrefWidth(500); udpDiagram.setPrefWidth(250);
        } else if (view.equals(VIEW_UDP)) udpDiagram.setPrefWidth(820);
        else tcpDiagram.setPrefWidth(820);
    }

    private void applyLogFilter() {
        String filter = logFilter.getValue() == null ? "Todos" : logFilter.getValue();
        filteredLogs.setPredicate(log -> filter.equals("Todos") || log.protocol().equals(filter));
    }

    private void updateProgress() {
        progressLabel.setText(cursor + " / " + displayEvents.size() + " eventos");
    }

    @SuppressWarnings("unchecked")
    private void updateComparisonPanel() {
        boolean show = currentResult != null && currentResult.id() == 10 && VIEW_COMPARE.equals(protocolView.getValue());
        comparisonPanel.setVisible(show); comparisonPanel.setManaged(show);
        if (!show) return;
        comparisonPanel.getChildren().clear();
        Label title = new Label("Cenário 10 · mesmos 10 dados, canal e seed"); title.getStyleClass().add("section-heading");
        GridPane table = new GridPane(); table.setHgap(16); table.setVgap(4);
        Label metric = new Label("Métrica"), udpHeader = new Label("UDP"), tcpHeader = new Label("TCP");
        metric.getStyleClass().add("control-label"); udpHeader.getStyleClass().add("control-value"); tcpHeader.getStyleClass().add("control-value");
        table.addRow(0, metric, udpHeader, tcpHeader);
        var udp = (java.util.Map<String, Object>) currentResult.metrics().get("udp");
        var tcp = (java.util.Map<String, Object>) currentResult.metrics().get("tcp");
        String[][] rows = {
                {"Entregues", udp.get("delivered") + "/10", tcp.get("delivered") + "/10"},
                {"Perdas finais", String.valueOf(udp.get("lost")), String.valueOf(tcp.get("lost"))},
                {"Duplicados", String.valueOf(udp.get("duplicates")), String.valueOf(tcp.get("duplicates"))},
                {"Em ordem", String.valueOf(udp.get("inOrder")), String.valueOf(tcp.get("inOrder"))},
                {"Tempo", udp.get("totalTimeMs") + " ms", tcp.get("totalTimeMs") + " ms"}
        };
        for (int row = 0; row < rows.length; row++)
            table.addRow(row + 1, new Label(rows[row][0]), new Label(rows[row][1]), new Label(rows[row][2]));
        comparisonPanel.getChildren().addAll(title, table);
    }

    private Node sliderField(String name, Slider slider, String suffix) {
        Label label = new Label(name); label.getStyleClass().add("control-label");
        Label value = new Label(); value.getStyleClass().add("control-value");
        slider.setShowTickMarks(false); slider.setShowTickLabels(false); slider.setBlockIncrement(1); slider.setMaxWidth(Double.MAX_VALUE);
        slider.valueProperty().addListener((o, a, b) -> value.setText(Math.round(b.doubleValue()) + suffix));
        value.setText(Math.round(slider.getValue()) + suffix);
        HBox row = new HBox(8, label, new Region(), value); HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
        VBox field = new VBox(4, row, slider); field.getStyleClass().add("slider-field");
        return field;
    }

    private void updateSliderLabels() { }

    private static Slider slider(double min, double max, double value) {
        Slider slider = new Slider(min, max, value); slider.setSnapToTicks(true); slider.setMajorTickUnit(1); return slider;
    }

    private Label sectionHeading(String text) { Label label = new Label(text); label.getStyleClass().add("section-heading"); return label; }
    private Label fieldLabel(String text) { Label label = new Label(text); label.getStyleClass().add("control-label"); return label; }

    private record ScenarioChoice(int id, String label) {
        @Override public String toString() { return label; }
    }

    private record DisplayEvent(long timeMs, int order, TransitionLog log, PacketTrace packet) { }
}
