package codehint;

import java.security.Permission;

public class SynthesisSecurityManager extends SecurityManager {
	
    @Override
	public void checkPermission(Permission perm) {
    	// Do nothing and hence allow anything not explicitly disallowed.
    }

    @Override
	public void checkWrite(String file) {
    	throw new SecurityException();
    }

    @Override
	public void checkDelete(String file) {
    	throw new SecurityException();
    }

}
