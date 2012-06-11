package codehint.exprgen.typeconstraint;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;

public class DesiredType extends TypeConstraint {
	
	private final IJavaType desiredType;
	
	public DesiredType(IJavaType desiredType) {
		this.desiredType = desiredType;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, IJavaDebugTarget target) {
		return (type == null && desiredType instanceof IJavaReferenceType) || desiredType.equals(type); // subtypeChecker.isSubtypeOf(desiredType, curType);
	}

	@Override
	public IJavaType[] getTypes(IJavaDebugTarget target) {
		return new IJavaType[] { desiredType };
	}

	@Override
	public String toString() {
		return "=" + desiredType.toString();
	}

}
