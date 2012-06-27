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
    
    public static <T> T pre(T v) {
    	return v;
    }
    
    public static <T> T post(T v) {
    	return v;
    }
    
    @SuppressWarnings("unused")
	public static <T> void pdspec(T var, boolean b) {
    	assert b;
    }
    
    public static <T> void value(T var, T val) {
    	assert var == val;
    }
    
    @SuppressWarnings("unused")
	public static <T> void type(Object var) {
    	assert false;
    }

}