package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

public class SupertypeBound extends SingleTypeConstraint {
	
	public SupertypeBound(IJavaType supertypeBound) {
		super(supertypeBound);
	}
	
	public IJavaType getSupertypeBound() {
		return typeConstraint;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		return subtypeChecker.isSubtypeOf(type, typeConstraint);// || subtypeChecker.isSubtypeOf(supertypeBound, type);
	}

	@Override
	public IJavaType[] getTypes(IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		return new IJavaType[] { typeConstraint };
	}

	@Override
	public String toString() {
		return "<=" + typeConstraint.toString();
	}

}
