package ua.stetsenkoinna.graphpresentation;

/**
 * What one tick of the model's clock stands for in the world being modelled.
 *
 * <p>The simulator has no opinion on this. A delay of {@code 12} is twelve of something, and
 * every number in the parameters row is in those same units, whatever they are. That is the right
 * way for a simulator to work and the wrong way for a person to read a screen: a horizon of 1000
 * says nothing about whether the run about to play covers a quarter of an hour of a factory's day
 * or six weeks of it.
 *
 * <p>So this is a reading, not a setting. Choosing it changes no number the simulator ever sees;
 * it says what the numbers already there amount to.
 */
public enum TimeUnitScale {

    SECONDS("s", 1),
    MINUTES("min", 60),
    HOURS("h", 3600),

    /**
     * The model's units mean nothing in particular, which is the honest answer for a great many
     * nets and the reason seconds are not simply assumed.
     */
    ABSTRACT("abstract", 0);

    private final String chipLabel;
    private final double secondsPerUnit;

    TimeUnitScale(String chipLabel, double secondsPerUnit) {
        this.chipLabel = chipLabel;
        this.secondsPerUnit = secondsPerUnit;
    }

    /**
     * @return the short form the chip carries. The only name any of these is shown under: the
     *         row it sits in already says what is being counted, the reading beside it says what
     *         choosing one does, and the question mark at the end of the row says why - so there
     *         is nothing left for a longer name, or a tooltip carrying it, to add.
     */
    public String chipLabel() {
        return chipLabel;
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
     * Says what a stretch of the model's clock amounts to, in the largest real unit it fills.
     *
     * @param units a length of model time
     * @return something like {@code 16 min 40 s}, or the empty string when the units stand for
     *         nothing and there is nothing to convert
     */
    public String formatDuration(double units) {
        if (!isConcrete() || units <= 0) {
            return "";
        }
        long seconds = Math.round(units * secondsPerUnit);
        if (seconds < 60) {
            return seconds + " s";
        }
        if (seconds < 3600) {
            long minutes = seconds / 60;
            long rest = seconds % 60;
            return rest > 0 ? minutes + " min " + rest + " s" : minutes + " min";
        }
        if (seconds < 86_400) {
            long hours = seconds / 3600;
            long rest = (seconds % 3600) / 60;
            return rest > 0 ? hours + " h " + rest + " min" : hours + " h";
        }
        long days = seconds / 86_400;
        long rest = (seconds % 86_400) / 3600;
        return rest > 0 ? days + " d " + rest + " h" : days + " d";
    }
}
