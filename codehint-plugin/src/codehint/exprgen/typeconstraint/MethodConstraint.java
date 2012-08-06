package codehint.exprgen.typeconstraint;

import java.util.ArrayList;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Method;

import codehint.utils.EclipseUtils;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;

public class MethodConstraint extends TypeConstraint {
	
	private final String methodName;
	private final TypeConstraint methodConstraint;
	private final ArrayList<TypeConstraint> argConstraints;
	
	public MethodConstraint(String methodName, TypeConstraint methodConstraint, ArrayList<TypeConstraint> argConstraints) {
		this.methodName = methodName;
		this.methodConstraint = methodConstraint;
		this.argConstraints = argConstraints;
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, TypeCache typeCache, IJavaStackFrame stack, IJavaDebugTarget target) {
		for (Method method: ExpressionGenerator.getMethods(type)) {
			if ((methodName == null || method.name().equals(methodName)) && (argConstraints == null || method.argumentTypeNames().size() == argConstraints.size())
					&& methodConstraint.isFulfilledBy(EclipseUtils.getFullyQualifiedTypeIfExists(method.returnTypeName(), stack, target, typeCache), subtypeChecker, typeCache, stack, target)) {  // We use the ifExists version because it might be void.
				int i = 0;
				if (argConstraints == null)
					return true;
				for (; i < argConstraints.size(); i++) {
					TypeConstraint argConstraint = argConstraints.get(i);
					if (argConstraint != null && !argConstraint.isFulfilledBy(EclipseUtils.getFullyQualifiedType((String)method.argumentTypeNames().get(i), stack, target, typeCache), subtypeChecker, typeCache, stack, target))
						break;
				}
				if (i == argConstraints.size())
					return true;
			}
		}
		return false;
	}

	@Override
	public IJavaType[] getTypes(IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache) {
		return new IJavaType[] { EclipseUtils.getFullyQualifiedType("java.lang.Object", stack, target, typeCache) };
	}

	@Override
	public String toString() {
		return "Object with method named " + methodName + " of type " + methodConstraint.toString()+ " and args " + (argConstraints == null ? "null" : argConstraints.toString());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((argConstraints == null) ? 0 : argConstraints.hashCode());
		result = prime * result + ((methodConstraint == null) ? 0 : methodConstraint.hashCode());
		result = prime * result + ((methodName == null) ? 0 : methodName.hashCode());
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
		MethodConstraint other = (MethodConstraint) obj;
		if (argConstraints == null) {
			if (other.argConstraints != null)
				return false;
		} else if (!argConstraints.equals(other.argConstraints))
			return false;
		if (methodConstraint == null) {
			if (other.methodConstraint != null)
				return false;
		} else if (!methodConstraint.equals(other.methodConstraint))
			return false;
		if (methodName == null) {
			if (other.methodName != null)
				return false;
		} else if (!methodName.equals(other.methodName))
			return false;
		return true;
	}

}
