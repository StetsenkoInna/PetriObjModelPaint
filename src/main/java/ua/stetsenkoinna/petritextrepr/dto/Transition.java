package ua.stetsenkoinna.petritextrepr.dto;

import ua.stetsenkoinna.petritextrepr.dto.ArcImpl.ArcIn;
import ua.stetsenkoinna.petritextrepr.dto.ArcImpl.ArcOut;
import ua.stetsenkoinna.petritextrepr.dto.mathstats.DistributionLaws;

import java.util.ArrayList;
import java.util.List;

public class Transition {

    public Transition(final int id){
        this.id = id;
    }

    private final int id;
    private double timeDelay;
    private int priority = 0; // Значення за замовчуванням
    private double probability = 1.0; // Значення за замовчуванням
    private String name;
    private DistributionLaws distributionLaw;
    private double paramDeviation = 0.0; // Для нормального розподілу
    private List<ArcIn> inputArcs = new ArrayList<>();
    private List<ArcOut> outputArcs = new ArrayList<>();

    public int getId() {
        return id;
    }

    public double getTimeDelay() {
        return timeDelay;
    }

    public int getPriority() {
        return priority;
    }

    public double getProbability() {
        return probability;
    }

    public String getName() {
        return name;
    }

    public List<ArcIn> getInputArcs() {
        return inputArcs;
    }

    public List<ArcOut> getOutputArcs() {
        return outputArcs;
    }

    public void setTimeDelay(double timeDelay) {
        this.timeDelay = timeDelay;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public void setProbability(double probability) {
        this.probability = probability;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addOutputArc(ArcOut arc){
        this.outputArcs.add(arc);
    }

    public void addInputArc(ArcIn in){
        this.inputArcs.add(in);
    }

    public void setDistributionLaw(DistributionLaws distributionLaw) {
        this.distributionLaw = distributionLaw;
    }

    public DistributionLaws getDistributionLaw() {
        return distributionLaw;
    }

    public double getParamDeviation() {
        return paramDeviation;
    }

    public void setParamDeviation(double paramDeviation) {
        this.paramDeviation = paramDeviation;
    }
}