package codehint;

public class Timeout extends RuntimeException {
	
	private static final long serialVersionUID = -7690975768551905025L;

	@Override
	public Throwable fillInStackTrace() {
    	return this;
    }

}
