package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;

public class SupertypeBound extends TypeConstraint {
	
	private final IJavaType supertypeBound;
	
	public SupertypeBound(IJavaType supertypeBound) {
		this.supertypeBound = supertypeBound;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, IJavaStackFrame stack, IJavaDebugTarget target) {
		return subtypeChecker.isSubtypeOf(type, supertypeBound);// || subtypeChecker.isSubtypeOf(supertypeBound, type);
	}

	@Override
	public IJavaType[] getTypes(IJavaDebugTarget target) {
		return new IJavaType[] { supertypeBound };
	}

	@Override
	public String toString() {
		return "<=" + supertypeBound.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((supertypeBound == null) ? 0 : supertypeBound.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SupertypeBound other = (SupertypeBound) obj;
		if (supertypeBound == null) {
			if (other.supertypeBound != null)
				return false;
		} else if (!supertypeBound.equals(other.supertypeBound))
			return false;
		return true;
	}

}
