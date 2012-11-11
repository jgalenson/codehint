package codehint;

import java.security.Permission;

public class SynthesisSecurityManager extends SecurityManager {
	
	private static class SynthesisSecurityException extends SecurityException {
	    
		private static final long serialVersionUID = 7795599073766113024L;

		@Override
		public Throwable fillInStackTrace() {
	    	return this;
	    }
		
	}
	
    @Override
	public void checkPermission(Permission perm) {
    	// Do nothing and hence allow anything not explicitly disallowed.
    }

    @Override
	public void checkWrite(String file) {
    	throw new SynthesisSecurityException();
    }

    @Override
	public void checkDelete(String file) {
    	throw new SynthesisSecurityException();
    }

    @Override
    public void checkExec(String cmd) {
    	throw new SynthesisSecurityException();
    }

}
