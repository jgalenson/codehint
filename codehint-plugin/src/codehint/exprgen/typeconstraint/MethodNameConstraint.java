package codehint.exprgen.typeconstraint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Method;

import codehint.utils.EclipseUtils;
import codehint.exprgen.ExpressionGenerator;
import codehint.exprgen.SubtypeChecker;
import codehint.utils.Utils;

public class MethodNameConstraint extends NameConstraint {

	private final TypeConstraint expressionConstraint;
	private final TypeConstraint methodConstraint;
	private final ArrayList<TypeConstraint> argConstraints;
	
	public MethodNameConstraint(TypeConstraint expressionConstraint, TypeConstraint methodConstraint, ArrayList<TypeConstraint> argConstraints) {
		this.expressionConstraint = expressionConstraint;
		this.methodConstraint = methodConstraint;
		this.argConstraints = argConstraints;
	}
	
	public Map<String, ArrayList<Method>> getMethods(IJavaStackFrame stack, IJavaDebugTarget target, SubtypeChecker subtypeChecker) {
		try {
			IJavaType[] receiverTypes = expressionConstraint.getTypes(target);
			Map<String, ArrayList<Method>> methodsByType = new HashMap<String, ArrayList<Method>>(receiverTypes.length);
    		for (IJavaType receiverType: receiverTypes) {
    			String typeName = receiverType.getName();
	    		for (Method method: ExpressionGenerator.getMethods(receiverType))
					if (ExpressionGenerator.isLegalMethod(method, stack.getReferenceType(), false) && methodFulfills(subtypeChecker, target, method))
						Utils.addToMap(methodsByType, typeName, method);
    		}
    		return methodsByType;
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
				&& (!"void".equals(method.returnTypeName()) && methodConstraint.isFulfilledBy(EclipseUtils.getFullyQualifiedTypeIfExists(method.returnTypeName(), target), subtypeChecker, target));
	}

	public static boolean fulfillsArgConstraints(Method method, ArrayList<TypeConstraint> argConstraints, SubtypeChecker subtypeChecker, IJavaDebugTarget target) {
		if (method.argumentTypeNames().size() != argConstraints.size())
			return false;
		int i = 0;
		for (; i < argConstraints.size(); i++) {
			TypeConstraint argConstraint = argConstraints.get(i);
			if (argConstraint != null && !argConstraint.isFulfilledBy(EclipseUtils.getTypeAndLoadIfNeeded((String)method.argumentTypeNames().get(i), target), subtypeChecker, target))
				return false;
		}
		return i == argConstraints.size();
	}

	@Override
	public String toString() {
		return "Method with receiver " + expressionConstraint.toString() + " and result " + methodConstraint.toString() + " and args " + argConstraints.toString();
	}

}
