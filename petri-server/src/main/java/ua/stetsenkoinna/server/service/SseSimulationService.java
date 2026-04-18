package ua.stetsenkoinna.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ua.stetsenkoinna.PetriObj.PetriNet;
import ua.stetsenkoinna.PetriObj.PetriObjModel;
import ua.stetsenkoinna.PetriObj.PetriP;
import ua.stetsenkoinna.PetriObj.PetriSim;
import ua.stetsenkoinna.PetriObj.PetriT;
import ua.stetsenkoinna.api.simulation.SimulationStatus;
import ua.stetsenkoinna.pnml.PnmlParser;
import ua.stetsenkoinna.server.adapter.NetBuildLock;
import ua.stetsenkoinna.server.adapter.SimulationFrame;
import ua.stetsenkoinna.server.adapter.SimulationInterruptedException;
import ua.stetsenkoinna.server.adapter.SseSimulationSink;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Runs a Petri net simulation and streams snapshots as Server-Sent Events.
 *
 * Architecture:
 *   sim-thread  — builds net, runs PetriObjModel.go(), writes frames to BlockingQueue
 *   writer-thread — reads from queue, serializes to JSON, sends via SseEmitter
 */
@Service
public class SseSimulationService {

    private static final Logger log = LoggerFactory.getLogger(SseSimulationService.class);
    private static final int QUEUE_CAPACITY = 2000;
    private static final long QUEUE_POLL_TIMEOUT_SEC = 60;

    private final SimulationSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public SseSimulationService(SimulationSessionRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /**
     * @param simulationTime  total simulation time units
     * @param timeStep        time-based mode: emit snapshot every this many sim units
     * @param snapshotInterval step-based mode: emit snapshot every N transition firings;
     *                        when non-null, overrides timeStep
     * @param animationDelayMs real-time pause after each emitted snapshot (0 = full speed)
     */
    public record StreamParams(
            double simulationTime,
            double timeStep,
            Integer snapshotInterval,
            long animationDelayMs
    ) {}

    public SseEmitter stream(String netXml, StreamParams params) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        SimulationSession session = registry.create();
        LinkedBlockingQueue<Optional<SimulationFrame>> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

        SseSimulationSink sink = new SseSimulationSink(
                queue, session,
                params.timeStep(), params.snapshotInterval(), params.simulationTime()
        );

        Thread.ofVirtual().name("sim-sse-" + session.getId()).start(() -> {
            session.setStatus(SimulationStatus.RUNNING);
            try {
                PetriNet net;
                synchronized (NetBuildLock.LOCK) {
                    PetriP.initNext();
                    PetriT.initNext();
                    net = new PnmlParser().parseXml(netXml);
                }
                PetriSim sim = new PetriSim(net);
                ArrayList<PetriSim> objects = new ArrayList<>(List.of(sim));
                PetriObjModel model = new PetriObjModel(session.getId(), objects);
                model.setIsProtokol(false);
                model.setIsStatistics(true);
                model.setStatisticCollector(sink);
                model.go(params.simulationTime());
            } catch (SimulationInterruptedException e) {
                log.info("SSE simulation {} halted by request", session.getId());
                session.setStatus(SimulationStatus.HALTED);
                queue.offer(Optional.empty());
            } catch (Exception e) {
                log.error("SSE simulation {} failed", session.getId(), e);
                session.setStatus(SimulationStatus.HALTED);
                queue.offer(Optional.empty());
            }
        });

        Thread.ofVirtual().name("sse-writer-" + session.getId()).start(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("session")
                        .data("{\"sessionId\":\"" + session.getId() + "\"}"));

                while (true) {
                    Optional<SimulationFrame> frameOpt = queue.poll(QUEUE_POLL_TIMEOUT_SEC, TimeUnit.SECONDS);
                    if (frameOpt == null || frameOpt.isEmpty()) {
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                        break;
                    }
                    emitter.send(SseEmitter.event()
                            .data(objectMapper.writeValueAsString(frameOpt.get())));
                    if (params.animationDelayMs() > 0) {
                        Thread.sleep(params.animationDelayMs());
                    }
                }
            } catch (Exception e) {
                log.warn("SSE writer {} error: {}", session.getId(), e.getMessage());
                emitter.completeWithError(e);
            } finally {
                registry.remove(session.getId());
            }
        });

        emitter.onTimeout(() -> {
            session.requestStop();
            registry.remove(session.getId());
        });
        emitter.onError(ex -> {
            session.requestStop();
            registry.remove(session.getId());
        });

        return emitter;
    }
}
