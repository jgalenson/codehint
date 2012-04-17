package codehint;

public class CodeHint {

    public static <T> T choose(T firstChoice, T... otherChoices) {
    	if (otherChoices == null)  // If the user only passes two args and the second is null, this happens....
    		assert firstChoice == null;
    	else
    		for (T choice : otherChoices)
    			assert firstChoice == null ? choice == null : firstChoice.equals(choice);
        return firstChoice;
    }
    
    public static <T> T chosen(T v) {
    	return v;
    }

}