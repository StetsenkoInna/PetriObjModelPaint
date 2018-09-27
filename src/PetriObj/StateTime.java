package PetriObj;

/**
 *
 * @author Anatoliy
 */
public class StateTime {
    private double currentTime;
    private double simulationTime;

    public StateTime() {
        currentTime = 0;
        simulationTime = Double.MAX_VALUE - 1;
    }

    public StateTime(double currentTime, double modelingTime) {
        this.currentTime = currentTime;
        this.simulationTime = modelingTime;
    }

    public double getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(double currentTime) {
        this.currentTime = currentTime;
    }

    public double getSimulationTime() {
        return simulationTime;
    }

    public void setSimulationTime(double modelingTime) {
        this.simulationTime = modelingTime;
    }
}
