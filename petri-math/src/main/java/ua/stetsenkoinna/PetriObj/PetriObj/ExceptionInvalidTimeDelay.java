package ua.stetsenkoinna.PetriObj;

/**
 * This exception is generated when user tries to construct Petri net with transition 
 * that has no input positions or output positions, 
 * and also if Petri net has no any position or any transition.
 *  @author Inna V. Stetsenko
 */
public class ExceptionInvalidTimeDelay extends Exception {

    public ExceptionInvalidTimeDelay(String string) {
        super(string);
    }
    
}
