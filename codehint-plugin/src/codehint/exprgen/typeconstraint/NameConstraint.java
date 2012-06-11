package codehint.exprgen.typeconstraint;

import java.util.Set;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaType;

public abstract class NameConstraint extends TypeConstraint {
	
	protected Set<String> legalNames;
	
	protected NameConstraint() {
		this.legalNames = null;
	}

	public void setLegalNames(Set<String> legalNames) {
		this.legalNames = legalNames;
	}

	@Override
	public IJavaType[] getTypes(IJavaDebugTarget target) {
		throw new RuntimeException("Cannot get the types of a name constraint.");
	}

}
