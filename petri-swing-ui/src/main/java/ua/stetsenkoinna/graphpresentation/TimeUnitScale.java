package ua.stetsenkoinna.graphpresentation;

/**
 * What one unit of the model's own clock stands for in the world being modelled.
 *
 * <p>The simulator has no opinion on this. A delay of {@code 12} is twelve of something, and
 * every number the header shows - the horizon, the clock a run starts from, every delay written
 * on a transition - is in those same units, whatever they are. That is the right way for a
 * simulator to work and the wrong way for a person to read a screen: "one hundred per second"
 * says nothing about whether the run about to play is a minute of a factory's day or a week of
 * it.
 *
 * <p>So this is a reading, not a setting: choosing it changes no number the simulator ever sees,
 * and changes every label put in front of the user. A model whose transitions are in minutes
 * says so once here, and the playback speeds go from naming abstract ratios to naming what an
 * hour of that factory costs to watch. The same choice, spelled the same way, exists in the web
 * editor, where it does the same job for the same reason.
 */
public enum TimeUnitScale {

    SECONDS("1 unit = 1 s", 1),
    MINUTES("1 unit = 1 min", 60),
    HOURS("1 unit = 1 h", 3600),

    /**
     * The model's units mean nothing in particular - which is the honest answer for a great many
     * nets, and the reason this is not simply assumed to be seconds. Speeds are then named as
     * bare ratios, because there is nothing to convert them into.
     */
    ABSTRACT("Abstract", 0);

    private final String label;
    private final double secondsPerUnit;

    TimeUnitScale(String label, double secondsPerUnit) {
        this.label = label;
        this.secondsPerUnit = secondsPerUnit;
    }

    /**
     * @return how many seconds one unit stands for, or zero for {@link #ABSTRACT}, where the
     *         question does not apply
     */
    public double secondsPerUnit() {
        return secondsPerUnit;
    }

    /**
     * @return whether units convert to real time at all
     */
    public boolean isConcrete() {
        return secondsPerUnit > 0;
    }

    /**
     * Names a playback ratio the way this scale makes it readable: the same ratio of model units
     * to real seconds, said in whichever real unit it comes out round in.
     *
     * <p>Sixty units a second is "1 min/s" when a unit is a second, and "1 h/s" when a unit is a
     * minute - the same ratio, and the same amount of watching, described in terms of the thing
     * being modelled rather than in terms of the model's arithmetic.
     *
     * @param unitsPerSecond model units played back per real second
     * @return the label for that ratio under this scale
     */
    public String formatRate(double unitsPerSecond) {
        if (!isConcrete()) {
            return trim(unitsPerSecond) + "×";
        }
        double secondsPerSecond = unitsPerSecond * secondsPerUnit;
        if (secondsPerSecond < 60) {
            return trim(secondsPerSecond) + " s/s";
        }
        if (secondsPerSecond < 3600) {
            return trim(secondsPerSecond / 60) + " min/s";
        }
        if (secondsPerSecond < 86_400) {
            return trim(secondsPerSecond / 3600) + " h/s";
        }
        return trim(secondsPerSecond / 86_400) + " d/s";
    }

    /**
     * @param unitsPerSecond model units played back per real second
     * @return the same ratio spelled out in full, for a tooltip
     */
    public String describeRate(double unitsPerSecond) {
        return isConcrete()
                ? formatRate(unitsPerSecond).replace(" s/s", " simulated seconds per second")
                        .replace(" min/s", " simulated minutes per second")
                        .replace(" h/s", " simulated hours per second")
                        .replace(" d/s", " simulated days per second")
                : trim(unitsPerSecond) + " model units per second";
    }

    /** Drops a trailing {@code .0}, so a round ratio reads as a whole number. */
    private static String trim(double value) {
        return value == Math.rint(value) && !Double.isInfinite(value)
                ? Long.toString((long) value)
                : Double.toString(Math.round(value * 10) / 10.0);
    }

    @Override
    public String toString() {
        return label;
    }
}
