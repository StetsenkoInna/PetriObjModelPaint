package ua.stetsenkoinna.graphpresentation.training;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JTextArea;
import ua.stetsenkoinna.graphnet.*;
import ua.stetsenkoinna.graphpresentation.*;
import ua.stetsenkoinna.petriobj.*;
import ua.stetsenkoinna.pnml.PnmlModelParser;

/** Observed execution only. A successful run is not an assertion of task correctness. */
public final class SimulationEvidence {
    private SimulationEvidence() { }
    public static final class Log extends JTextArea {
        private volatile boolean truncated;
        @Override public void append(String value) {
            int remaining = 65_536 - getDocument().getLength();
            if (value.length() > remaining) truncated = true;
            if (remaining > 0) super.append(value.substring(0, Math.min(remaining, value.length())));
        }
        @Override public void setText(String value) { truncated = false; super.setText(value); }
        public boolean truncated() { return truncated; }
    }
    public static Map<String, Object> automatic(byte[] pnml, SimulationPlan plan) throws Exception {
        List<Map<String, Object>> runs = new ArrayList<>();
        for (double horizon : plan.horizons()) {
            var run = execute(pnml, horizon, plan.samplingInterval()); runs.add(run);
            if ("failed".equals(run.get("status"))) break;
        }
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("policy", "attempt-or-reason"); evidence.put("source_model_sha256", TrainingBundleWorkspace.hash(pnml));
        evidence.put("skip_reason", plan.skipReason()); evidence.put("automatic_runs", runs); evidence.put("interactive_runs", plan.interactiveRuns());
        return evidence;
    }
    private static Map<String, Object> execute(byte[] pnml, double horizon, double interval) throws Exception {
        long started = System.nanoTime(); String timestamp = Instant.now().toString();
        String sourceHash = TrainingBundleWorkspace.hash(pnml);
        RunPetriObjModel model = null; Capture capture = null; Log protocol = new Log();
        try {
            var parser = new PnmlModelParser();
            var canvas = GraphCanvasModel.fromObjModel(parser.parseXml(new String(pnml, StandardCharsets.UTF_8)));
            if (!parser.getWarnings().isEmpty() || !canvas.getLoadWarnings().isEmpty()) throw new IllegalArgumentException("Saved model requires import repairs; it was not changed for simulation.");
            canvas.getNet().createPetriNet("Review automatic simulation");
            if (canvas.getNet().hasParameters()) throw new IllegalArgumentException("Unspecified model parameters.");
            for (var transition : canvas.getNet().getGraphPetriTransitionList()) {
                if (canvas.getNet().getGraphArcInList().stream().noneMatch(arc -> arc.getEndElement() == transition && !arc.getArcIn().getIsInf()))
                    throw new IllegalArgumentException("Transition «" + transition.getName() + "» has no consuming input; automatic execution would be unbounded.");
            }
            model = CanvasSimulationModels.run(canvas, "Review automatic simulation", 0, horizon, protocol);
            capture = new Capture(model, sourceHash, "automatic", horizon, interval);
            Capture observed = capture; model.setStateListener(observed::observe);
            RunPetriObjModel executing = model; AtomicInteger steps = new AtomicInteger();
            model.setProgressListener(value -> { if (steps.incrementAndGet() > 10_000) executing.halt(); });
            FutureTask<Void> task = new FutureTask<>(() -> { executing.go(horizon); return null; });
            Thread worker = new Thread(task, "review-auto-simulation"); worker.setDaemon(true); worker.start();
            try { task.get(4, TimeUnit.SECONDS); }
            catch (TimeoutException timeout) {
                executing.halt(); task.cancel(true);
                return observed.finish("limit_reached", "Execution exceeded four seconds; incomplete data retained.", protocol.getText(), protocol.truncated(), false);
            }
            return observed.finish(model.isHalted() ? "limit_reached" : "completed", model.isHalted() ? "Event limit reached (10000)." : "", protocol.getText(), protocol.truncated(), true);
        } catch (Exception failure) {
            if (model != null) model.halt();
            Throwable cause = failure instanceof ExecutionException && failure.getCause() != null ? failure.getCause() : failure;
            if (capture != null) return capture.finish("failed", String.valueOf(cause.getMessage()), protocol.getText(), protocol.truncated(), false);
            var record = new LinkedHashMap<String, Object>();
            record.put("source", "automatic"); record.put("source_model_sha256", sourceHash); record.put("created_at", timestamp);
            record.put("status", "failed"); record.put("error", String.valueOf(cause.getMessage())); record.put("start_time", 0); record.put("horizon", horizon);
            record.put("elapsed_ms", (System.nanoTime()-started)/1_000_000); record.put("protocol", protocol.getText()); record.put("protocol_truncated", protocol.truncated());
            record.put("initial_state", List.of()); record.put("final_state", List.of()); record.put("trajectory", Map.of("columns", List.of(), "samples", List.of(), "requested_interval", interval, "effective_interval", interval));
            return record;
        }
    }

    public static Map<String, Object> failed(String hash, String source, String error) {
        var record = new LinkedHashMap<String, Object>(); record.put("source", source); record.put("source_model_sha256", hash);
        record.put("created_at", Instant.now().toString()); record.put("status", "failed"); record.put("error", error);
        record.put("horizon", 0); record.put("start_time", 0); record.put("elapsed_ms", 0);
        record.put("initial_state", List.of()); record.put("final_state", List.of());
        record.put("trajectory", Map.of("columns", List.of(), "samples", List.of(), "requested_interval", 1, "effective_interval", 1));
        record.put("protocol", ""); record.put("protocol_truncated", false); return record;
    }

    public static final class Capture {
        private final PetriObjModel model;
        private final List<PetriSim> objects;
        private final String hash, source, createdAt = Instant.now().toString();
        private final long started = System.nanoTime();
        private final double start, horizon, requested, step;
        private final List<Map<String, Object>> columns = new ArrayList<>();
        private final List<List<Number>> samples = new ArrayList<>();
        private final List<Map<String, Object>> initial;
        private List<Number> previous;
        private long next;
        private double lastObserved;
        public Capture(PetriObjModel model, String hash, String source, double horizon, double interval) {
            this.model = model; this.hash = hash; this.source = source; this.horizon = horizon; this.requested = interval; this.start = model.getCurrentTime(); this.lastObserved = start;
            this.objects = List.copyOf(model.getListObj());
            for (int index = 0; index < objects.size(); index++) {
                var sim = objects.get(index);
                for (var place : sim.getNet().getListP()) columns.add(Map.of("object_index", index, "object_name", sim.getName(), "id", place.getId(), "number", place.getNumber(), "name", place.getName(), "kind", "place", "metric", "tokens"));
                for (var transition : sim.getNet().getListT()) columns.add(Map.of("object_index", index, "object_name", sim.getName(), "id", transition.getId(), "number", transition.getNumber(), "name", transition.getName(), "kind", "transition", "metric", "active_channels"));
            }
            // Bound rows AND scalar cells, while declaring the actual interval rather than dropping samples silently.
            long rows = Math.max(2, Math.min(20_000, 1_000_000 / Math.max(1, columns.size()+1)));
            this.step = Math.max(interval, Math.ceil(((horizon - start) / Math.max(1, rows-1)) / interval) * interval);
            this.initial = state(objects); this.previous = values();
        }
        public synchronized void observe(double time) {
            if (!Double.isFinite(time) || time < start || time > horizon) return;
            while ((start + next * step) < time - 1e-9) { append((start + next * step), previous); next++; }
            previous = values(); lastObserved = time;
            if (Math.abs((start + next * step) - time) < 1e-9) { append(time, previous); next++; }
            else if (!samples.isEmpty() && Math.abs(samples.getLast().getFirst().doubleValue()-time) < 1e-9) {
                samples.removeLast(); append(time, previous);
            }
        }
        private List<Number> values() {
            List<Number> values = new ArrayList<>();
            for (var sim : objects) {
                for (var place : sim.getNet().getListP()) values.add(place.getMark());
                for (var transition : sim.getNet().getListT()) values.add(transition.getBuffer());
            }
            return values;
        }
        private void append(double time, List<Number> values) { var row = new ArrayList<Number>(); row.add(time); row.addAll(values); samples.add(List.copyOf(row)); }
        public synchronized Map<String, Object> finish(String status, String error, String protocol, boolean truncated, boolean stableFinalState) {
            double until = status.equals("completed") ? horizon : lastObserved;
            while ((start + next * step) <= until + 1e-9) { append((start + next * step), previous); next++; }
            if (samples.isEmpty() || samples.getLast().getFirst().doubleValue() < until) append(until, previous);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("source", source); record.put("source_model_sha256", hash); record.put("created_at", createdAt);
            record.put("status", status); record.put("error", error); record.put("start_time", start); record.put("horizon", horizon); record.put("observed_until", until);
            record.put("elapsed_ms", (System.nanoTime()-started)/1_000_000); record.put("protocol", protocol); record.put("protocol_truncated", truncated);
            record.put("random_seed", null); record.put("randomness", "Engine randomness was not seeded; exact replay is not guaranteed.");
            record.put("initial_state", initial); record.put("final_state", stableFinalState ? state(objects) : List.of());
            record.put("trajectory", Map.of("columns", columns, "samples", List.copyOf(samples), "requested_interval", requested, "effective_interval", step,
                    "time_unit", "model-time", "semantics", "Right-continuous tokens and active channels after discrete events; held constant between events.", "complete", status.equals("completed")));
            return record;
        }
    }
    private static List<Map<String, Object>> state(List<PetriSim> objects) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < objects.size(); index++) {
            var sim = objects.get(index); List<Map<String, Object>> places = new ArrayList<>(), transitions = new ArrayList<>();
            for (var p : sim.getNet().getListP()) places.add(Map.of("id", p.getId(), "number", p.getNumber(), "name", p.getName(), "tokens", p.getMark(), "mean", finite(p.getMean()), "min", p.getObservedMin(), "max", p.getObservedMax()));
            for (var t : sim.getNet().getListT()) transitions.add(Map.of("id", t.getId(), "number", t.getNumber(), "name", t.getName(), "active_channels", t.getBuffer(), "mean", finite(t.getMean()), "min", finite(t.getObservedMin()), "max", finite(t.getObservedMax())));
            values.add(Map.of("object_index", index, "name", sim.getName(), "places", places, "transitions", transitions));
        }
        return values;
    }
    private static Object finite(double value) { return Double.isFinite(value) ? value : String.valueOf(value); }
}
