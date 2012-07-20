package codehint.exprgen.typeconstraint;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.exprgen.SubtypeChecker;

public class DesiredType extends TypeConstraint {
	
	private final IJavaType desiredType;
	
	public DesiredType(IJavaType desiredType) {
		this.desiredType = desiredType;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, IJavaStackFrame stack, IJavaDebugTarget target) {
		if (type == null)
			return desiredType instanceof IJavaReferenceType;
		if (desiredType == null)
			return type instanceof IJavaReferenceType;
		return desiredType.equals(type); // subtypeChecker.isSubtypeOf(desiredType, curType);
	}

	@Override
	public IJavaType[] getTypes(IJavaDebugTarget target) {
		return new IJavaType[] { desiredType };
	}

	@Override
	public String toString() {
		try {
			return "=" + desiredType.getName();
		} catch (DebugException e) {
			e.printStackTrace();
			return "=" + desiredType.toString();
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((desiredType == null) ? 0 : desiredType.hashCode());
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
		DesiredType other = (DesiredType) obj;
		if (desiredType == null) {
			if (other.desiredType != null)
				return false;
		} else if (!desiredType.equals(other.desiredType))
			return false;
		return true;
	}

}
