package ua.stetsenkoinna.server.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.stetsenkoinna.petriobj.FiringPhase;
import ua.stetsenkoinna.petriobj.PetriNet;
import ua.stetsenkoinna.petriobj.PetriP;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.petriobj.PetriT;
import ua.stetsenkoinna.petriobj.SimulationStatisticCollector;
import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.server.dto.SimulationResultDto;
import ua.stetsenkoinna.server.service.SimulationSession;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SimulationStatisticCollector that emits snapshots to a BlockingQueue for SSE streaming.
 *
 * Supports two snapshot modes:
 * - Time-based (snapshotInterval == null): one snapshot every timeStep simulation units.
 * - Step-based (snapshotInterval != null): one snapshot every snapshotInterval transition firings.
 *
 * The queue consumer (SseSimulationService writer thread) reads frames and sends them
 * as SSE events. An empty Optional signals end-of-stream.
 *
 * <p>Alongside the snapshot state, the sink accumulates the atomic firing phases reported
 * between two snapshots and ships them with the frame that closes the interval, which is what
 * lets a client animate a firing instead of jumping from one marking to the next. The
 * accumulator is only reset once a frame has actually been enqueued, so a snapshot that the
 * throttle skips — or that a full queue rejects — carries its phases into the next one rather
 * than breaking an animation chain in half.
 */
public class SseSimulationSink implements SimulationStatisticCollector {

    private static final Logger log = LoggerFactory.getLogger(SseSimulationSink.class);

    /**
     * How many phases a single frame may carry. Reached only by a model firing far more often
     * than it is sampled; past it the oldest phases of the interval are dropped, never the
     * newest — see {@link #onFiringPhase}.
     */
    private static final int MAX_PHASES_PER_FRAME = 1000;

    /**
     * Ceiling on the marking/buffer entries the accumulator holds between two frames, a
     * quarter of the in-flight budget. A phase costs the size of the model, so a count alone
     * bounds nothing: at the request validator's ceiling of 5000 places and 5000 transitions,
     * {@link #MAX_PHASES_PER_FRAME} phases would be ten million entries on the simulation
     * thread, per session.
     */
    private static final int MAX_ACCUMULATED_PHASE_ENTRIES = 500_000;

    /**
     * Ceiling on the marking/buffer entries of all phases waiting in the queue at once,
     * roughly 100 MB. Expressed in entries rather than phases for the same reason, and kept
     * above the accumulator's own budget so a frame of the largest permitted model still fits
     * when the queue is empty.
     */
    private static final int MAX_IN_FLIGHT_PHASE_ENTRIES = 2_000_000;

    private final BlockingQueue<Optional<SimulationFrame>> queue;
    protected final SimulationSession session;
    private final double timeStep;
    private final Integer snapshotInterval;
    protected final double simulationTime;

    private final Map<String, Integer> currentMarkings = new LinkedHashMap<>();
    private final Map<String, Integer> currentBuffers = new LinkedHashMap<>();

    private final ArrayDeque<FiringStepDto> phases = new ArrayDeque<>();
    private final LinkedHashSet<String> firedTransitions = new LinkedHashSet<>();
    private final AtomicInteger inFlightPhases;

    private List<PetriP> allPlaces;
    private List<PetriT> allTransitions;
    private int maxAccumulatedPhases = MAX_PHASES_PER_FRAME;
    private int maxInFlightPhases = Integer.MAX_VALUE;
    private long droppedPhases = 0;

    private double nextSnapshotAt;
    protected int stepCount = 0;

    /**
     * @param inFlightPhases phases already handed to the writer thread and not yet sent; the
     *        one piece of state shared between the two threads of a session
     */
    public SseSimulationSink(BlockingQueue<Optional<SimulationFrame>> queue,
                              SimulationSession session,
                              double timeStep,
                              Integer snapshotInterval,
                              double simulationTime,
                              AtomicInteger inFlightPhases) {
        this.queue = queue;
        this.session = session;
        this.timeStep = timeStep;
        this.snapshotInterval = snapshotInterval;
        this.simulationTime = simulationTime;
        this.inFlightPhases = inFlightPhases;
        this.nextSnapshotAt = timeStep;
    }

    @Override
    public boolean shouldCollect(double currentTime) {
        while (session.isPauseRequested() && !session.isStopRequested()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SimulationInterruptedException();
            }
        }
        if (session.isStopRequested()) {
            throw new SimulationInterruptedException();
        }
        return true;
    }

    @Override
    public void onTimeStep(double currentTime, PetriNet net, int petriObjId) {
        for (PetriP p : net.getListP()) {
            currentMarkings.put(p.getId(), p.getMark());
        }
        for (PetriT t : net.getListT()) {
            currentBuffers.put(t.getId(), t.getBuffer());
        }
    }

    /**
     * Records one instant of a firing.
     *
     * <p>Deliberately does not go through {@link #shouldCollect}: a pause or stop raised in the
     * middle of a firing would tear a before/after pair apart and put half an animation on the
     * wire. Control requests are honoured at the time-step boundary, where they always were.
     */
    @Override
    public void onFiringPhase(double time, FiringPhase phase, PetriT transition,
                              Iterable<PetriSim> objects) {
        if (allPlaces == null) {
            buildIndex(objects);
        }

        Map<String, Integer> markings = new LinkedHashMap<>();
        for (PetriP p : allPlaces) {
            markings.put(p.getId(), p.getMark());
        }
        Map<String, Integer> buffers = new LinkedHashMap<>();
        for (PetriT t : allTransitions) {
            buffers.put(t.getId(), t.getBuffer());
        }

        // Drop the oldest, never the newest: a client replaying a frame ends on the state of
        // the last phase, which has to stay the state the frame itself reports.
        while (phases.size() >= maxAccumulatedPhases) {
            phases.removeFirst();
            droppedPhases++;
        }
        phases.addLast(new FiringStepDto(transition.getId(), phase.wireName(), markings, buffers, time));
        firedTransitions.add(transition.getId());
    }

    /**
     * Walks the model once for the elements every phase snapshot spans. The object list is
     * shuffled between events and the phases arrive far too often to rebuild this each time.
     */
    private void buildIndex(Iterable<PetriSim> objects) {
        allPlaces = new ArrayList<>();
        allTransitions = new ArrayList<>();
        for (PetriSim sim : objects) {
            allPlaces.addAll(List.of(sim.getNet().getListP()));
            allTransitions.addAll(List.of(sim.getNet().getListT()));
        }
        int entriesPerPhase = Math.max(1, allPlaces.size() + allTransitions.size());
        maxAccumulatedPhases = Math.clamp(
                MAX_ACCUMULATED_PHASE_ENTRIES / entriesPerPhase, 16, MAX_PHASES_PER_FRAME);
        maxInFlightPhases = Math.clamp(MAX_IN_FLIGHT_PHASE_ENTRIES / entriesPerPhase, 64, 100_000);
    }

    @Override
    public void flush(double currentTime) {
        stepCount++;

        boolean shouldEmit;
        if (snapshotInterval != null) {
            shouldEmit = stepCount % snapshotInterval == 0;
        } else {
            shouldEmit = currentTime >= nextSnapshotAt;
            if (shouldEmit) {
                nextSnapshotAt = (Math.floor(currentTime / timeStep) + 1) * timeStep;
            }
        }

        if (shouldEmit && !currentMarkings.isEmpty()) {
            emit(currentTime,
                    Map.copyOf(currentMarkings),
                    Map.copyOf(currentBuffers),
                    Math.min(currentTime / simulationTime, 1.0));
        }

        currentMarkings.clear();
        currentBuffers.clear();
    }

    @Override
    public void onSimulationEnd(double simulationEndTime, Iterable<PetriSim> objects) {
        Map<String, Integer> finalMarkings = new LinkedHashMap<>();
        Map<String, Integer> finalBuffers = new LinkedHashMap<>();
        List<SimulationResultDto.PlaceResultDto> places = new ArrayList<>();
        List<SimulationResultDto.TransitionResultDto> transitions = new ArrayList<>();

        for (PetriSim sim : objects) {
            for (PetriP p : sim.getNet().getListP()) {
                finalMarkings.put(p.getId(), p.getMark());
                places.add(new SimulationResultDto.PlaceResultDto(
                        p.getId(), p.getName(), p.getMark(),
                        p.getMean(), p.getObservedMin(), p.getObservedMax()
                ));
            }
            for (PetriT t : sim.getNet().getListT()) {
                finalBuffers.put(t.getId(), t.getBuffer());
                transitions.add(new SimulationResultDto.TransitionResultDto(
                        t.getId(), t.getName(), t.getBuffer(),
                        t.getMean(), t.getObservedMin(), t.getObservedMax()
                ));
            }
        }

        session.setResult(new SimulationResultDto(simulationTime, simulationEndTime, stepCount, places, transitions));
        // The phases of the last event loop iteration have no further frame to ride on. Their
        // own times are kept as recorded: simulationEndTime is a clamped value that need not
        // be any of them.
        emit(simulationEndTime, finalMarkings, finalBuffers, 1.0);
    }

    @Override
    public void shutdown() {
        if (droppedPhases > 0) {
            log.info("SSE simulation {} dropped {} firing phases (caps: {} accumulated, {} in flight)",
                    session.getId(), droppedPhases, maxAccumulatedPhases, maxInFlightPhases);
        }
        session.setStatus(SimulationStatus.FINISHED);
        queue.offer(Optional.empty());
    }

    /**
     * Attaches the accumulated firing phases to a snapshot and enqueues the resulting frame.
     *
     * <p>When the in-flight budget cannot hold the whole interval the sequence is trimmed to
     * its tail rather than dropped outright: the newest phases are the ones that end on the
     * state this frame reports, so they are what leaves a client in the right place. Dropping
     * whole intervals instead would turn a large model — where a phase costs the size of the
     * net — into one with no animation at all rather than a coarser one.
     *
     * <p>The accumulators are cleared only once the frame is on the queue. A rejected frame
     * leaves them alone so the next one carries the same phases, and the reservation made
     * against the in-flight budget is given back — the writer thread never saw those phases and
     * will never decrement for them.
     */
    private void emit(double currentTime, Map<String, Integer> markings,
                      Map<String, Integer> buffers, double progress) {
        int room = Math.max(0, maxInFlightPhases - inFlightPhases.get());
        int taken = Math.min(phases.size(), room);
        List<FiringStepDto> sequence = phases.stream().skip(phases.size() - (long) taken).toList();
        inFlightPhases.addAndGet(taken);

        boolean sent = enqueue(new SimulationFrame(currentTime, stepCount, markings, buffers,
                progress, List.copyOf(firedTransitions), sequence));

        if (sent) {
            droppedPhases += phases.size() - taken;
            phases.clear();
            firedTransitions.clear();
        } else {
            inFlightPhases.addAndGet(-taken);
        }
    }

    /**
     * The in-flight budget this model works out to, which depends on its size. Visible to the
     * tests, which cannot otherwise say how far behind the writer has to be for trimming to
     * begin.
     *
     * @return the maximum number of phases that may be awaiting the writer at once
     */
    int maxInFlightPhases() {
        return maxInFlightPhases;
    }

    private boolean enqueue(SimulationFrame frame) {
        if (queue.offer(Optional.of(frame))) {
            return true;
        }
        log.warn("SSE queue full — dropping frame at t={}", frame.currentTime());
        return false;
    }
}
