package codehint.exprgen.typeconstraint;

import java.util.ArrayList;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Method;

import codehint.utils.EclipseUtils;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.SubtypeChecker;

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
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, IJavaStackFrame stack, IJavaDebugTarget target) {
		for (Method method: ExpressionGenerator.getMethods(type)) {
			if ((methodName == null || method.name().equals(methodName)) && method.argumentTypeNames().size() == argConstraints.size()
					&& methodConstraint.isFulfilledBy(EclipseUtils.getFullyQualifiedTypeIfExists(method.returnTypeName(), target), subtypeChecker, stack, target)) {  // We use the ifExists version because it might be void.
				int i = 0;
				for (; i < argConstraints.size(); i++) {
					TypeConstraint argConstraint = argConstraints.get(i);
					if (argConstraint != null && !argConstraint.isFulfilledBy(EclipseUtils.getFullyQualifiedType((String)method.argumentTypeNames().get(i), target), subtypeChecker, stack, target))
						break;
				}
				if (i == argConstraints.size())
					return true;
			}
		}
		return false;
	}

	@Override
	public IJavaType[] getTypes(IJavaDebugTarget target) {
		return new IJavaType[] { EclipseUtils.getFullyQualifiedType("java.lang.Object", target) };
	}

	@Override
	public String toString() {
		return "Object with method named " + methodName + " of type " + methodConstraint.toString()+ " and args " + argConstraints.toString();
	}

}
