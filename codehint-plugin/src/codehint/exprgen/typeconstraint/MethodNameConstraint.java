package codehint.exprgen.typeconstraint;

import java.util.ArrayList;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Method;

import codehint.EclipseUtils;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.SubtypeChecker;

public class MethodNameConstraint extends NameConstraint {

	private final TypeConstraint expressionConstraint;
	private final TypeConstraint methodConstraint;
	private final ArrayList<TypeConstraint> argConstraints;
	
	public MethodNameConstraint(TypeConstraint expressionConstraint, TypeConstraint methodConstraint, ArrayList<TypeConstraint> argConstraints) {
		this.expressionConstraint = expressionConstraint;
		this.methodConstraint = methodConstraint;
		this.argConstraints = argConstraints;
	}
	
	public ArrayList<Method> getMethods(IJavaStackFrame stack, IJavaDebugTarget target, SubtypeChecker subtypeChecker) {
		try {
    		ArrayList<Method> methods = new ArrayList<Method>();
    		for (IJavaType receiverType: expressionConstraint.getTypes(target))
	    		for (Method method: ExpressionGenerator.getMethods(receiverType))
					if (ExpressionGenerator.isLegalMethod(method, stack.getReferenceType(), false) && methodFulfills(subtypeChecker, target, method))
						methods.add(method);
    		return methods;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean isFulfilledBy(IJavaType type, SubtypeChecker subtypeChecker, IJavaDebugTarget target) {
		if (!expressionConstraint.isFulfilledBy(type, subtypeChecker, target))
			return false;
		for (Method method: ExpressionGenerator.getMethods(type))
			if (methodFulfills(subtypeChecker, target, method))
				return true;
		return false;
	}

	private boolean methodFulfills(SubtypeChecker subtypeChecker, IJavaDebugTarget target, Method method) {
		return (legalNames == null || legalNames.contains(method.name())) && 
				fulfillsArgConstraints(method, argConstraints, subtypeChecker, target)
				&& methodConstraint.isFulfilledBy(EclipseUtils.getFullyQualifiedTypeIfExists(method.returnTypeName(), target), subtypeChecker, target);
	}

	public static boolean fulfillsArgConstraints(Method method, ArrayList<TypeConstraint> argConstraints, SubtypeChecker subtypeChecker, IJavaDebugTarget target) {
		if (method.argumentTypeNames().size() != argConstraints.size())
			return false;
		int i = 0;
		for (; i < argConstraints.size(); i++) {
			TypeConstraint argConstraint = argConstraints.get(i);
			if (argConstraint != null && !argConstraint.isFulfilledBy(EclipseUtils.getFullyQualifiedType((String)method.argumentTypeNames().get(i), target), subtypeChecker, target))
				return false;
		}
		return i == argConstraints.size();
	}

	@Override
	public String toString() {
		return "Method with receiver " + expressionConstraint.toString() + " and result " + methodConstraint.toString() + " and args " + argConstraints.toString();
	}

}
