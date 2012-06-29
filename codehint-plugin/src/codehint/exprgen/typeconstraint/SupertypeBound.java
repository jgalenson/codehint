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

}
