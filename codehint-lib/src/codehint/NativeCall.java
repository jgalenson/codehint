package codehint;

public class NativeCall extends ThreadDeath {

	private static final long serialVersionUID = -3874898928659700917L;

	@Override
	public Throwable fillInStackTrace() {
    	return this;
    }

}
