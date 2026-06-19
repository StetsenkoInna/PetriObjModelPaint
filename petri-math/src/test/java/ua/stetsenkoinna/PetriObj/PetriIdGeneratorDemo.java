package ua.stetsenkoinna.PetriObj;

/**
 * Demo showing ID generator usage
 */
public class PetriIdGeneratorDemo {

    public static void main(String[] args) {
        System.out.println("=== Petri Net ID Generator Demo ===\n");

        // Reset to start fresh
        PetriIdGenerator.reset();

        // Create places with different names
        System.out.println("Places:");
        PetriP buffer = new PetriP("Buffer", 10);
        System.out.println("  Buffer: " + buffer.getId());

        PetriP queue = new PetriP("Request Queue", 5);
        System.out.println("  Request Queue: " + queue.getId());

        PetriP output = new PetriP("Output@Port", 0);
        System.out.println("  Output@Port: " + output.getId());

        // Create transitions
        System.out.println("\nTransitions:");
        PetriT process = new PetriT("Process Request", 2.5);
        System.out.println("  Process Request: " + process.getId());

        PetriT send = new PetriT("Send Response", 1.0);
        System.out.println("  Send Response: " + send.getId());

        // Create arcs
        System.out.println("\nArcs:");
        ArcIn arc1 = new ArcIn(buffer, process, 2);
        System.out.println("  Buffer -> Process: " + arc1.getId());

        ArcOut arc2 = new ArcOut(process, output, 1);
        System.out.println("  Process -> Output: " + arc2.getId());

        // Show uniqueness
        System.out.println("\nUniqueness test (same name, different IDs):");
        PetriP place1 = new PetriP("Test", 0);
        PetriP place2 = new PetriP("Test", 0);
        System.out.println("  Test #1: " + place1.getId());
        System.out.println("  Test #2: " + place2.getId());

        // Loading from PNML (simulated)
        System.out.println("\nLoading existing ID from PNML:");
        PetriP imported = new PetriP("p-buffer-abc12345", "Buffer", 10);
        System.out.println("  Imported place: " + imported.getId());
        System.out.println("  Is registered: " + PetriIdGenerator.isIdUsed("p-buffer-abc12345"));

        // Summary
        System.out.println("\nTotal IDs generated: " + PetriIdGenerator.getUsedIdCount());
    }
}
