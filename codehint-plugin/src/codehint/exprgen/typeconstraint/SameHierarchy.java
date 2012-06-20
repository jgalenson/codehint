package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;

public class SameHierarchy extends TypeConstraint {
	
	private final IJavaType targetType;
	
	public SameHierarchy(IJavaType targetType) {
		this.targetType = targetType;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, IJavaDebugTarget target) {
		return subtypeChecker.isSubtypeOf(type, targetType) || subtypeChecker.isSubtypeOf(targetType, type);
	}

	@Override
	public IJavaType[] getTypes(IJavaDebugTarget target) {
		return new IJavaType[] { targetType };
	}

	@Override
	public String toString() {
		return "comparable to " + targetType.toString();
	}

}
