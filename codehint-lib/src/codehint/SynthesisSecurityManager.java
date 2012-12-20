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

	private final SecurityManager oldSecurityManager;
	private boolean disabled;
	
	public SynthesisSecurityManager() {
		this.oldSecurityManager = System.getSecurityManager();
		disabled = false;
	}
	
	protected void disable() {
		disabled = true;
		System.setSecurityManager(oldSecurityManager);
	}
	
    @Override
	public void checkPermission(Permission perm) {
    	if ("setSecurityManager".equals(perm.getName()) && !disabled)
	    	throw new SynthesisSecurityException();
    	if (oldSecurityManager != null)
    		oldSecurityManager.checkPermission(perm);
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

    @Override
    public void checkPrintJobAccess() {
    	throw new SynthesisSecurityException();
    }

    /*
    // Users can do dangerous things with Unsafe, but they need to get it through reflection, so block that.
    @Override
    public void checkMemberAccess(Class<?> clazz, int which) {
    	if (clazz.getName().equals("sun.misc.Unsafe"))
    		throw new SynthesisSecurityException();
    }*/

}
