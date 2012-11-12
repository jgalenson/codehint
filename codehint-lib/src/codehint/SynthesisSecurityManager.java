package codehint;

import java.security.Permission;

class SynthesisSecurityManager extends SecurityManager {
	
	private static class SynthesisSecurityException extends SecurityException {
	    
		private static final long serialVersionUID = 7795599073766113024L;

		@Override
		public Throwable fillInStackTrace() {
	    	return this;
	    }
		
	}
	
	private boolean disabled;
	
	public SynthesisSecurityManager() {
		disabled = false;
	}
	
	protected void disable(SecurityManager sm) {
		disabled = true;
		System.setSecurityManager(sm);
	}
	
    @Override
	public void checkPermission(Permission perm) {
    	if (!disabled && "setSecurityManager".equals(perm.getName()))
	    	throw new SynthesisSecurityException();
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
