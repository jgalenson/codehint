package codehint.exprgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;

import codehint.utils.EclipseUtils;

import com.sun.jdi.Method;

/**
 * A class that checks whether a given argument to a
 * method call needs a cast to remove ambiguities due
 * to overloaded methods.
 * The current implementation is conservative; it could
 * add unnecessary casts.
 */
public class OverloadChecker {
	
	private final IJavaStackFrame stack;
	private final IJavaDebugTarget target;
	private final TypeCache typeCache;
	private final SubtypeChecker subtypeChecker;
	private final Map<String, Map<Integer, List<Method>>> methodsByName;
	private ArrayList<Set<IJavaType>> argTypes;
	
	/**
	 * Creates a new OverloadChecker that will check methods
	 * of the given receiver.
	 * @param receiverType The type of the receiver of whose
	 * methods this object will check.
	 */
	public OverloadChecker(IJavaType receiverType, IJavaStackFrame stack, IJavaDebugTarget target, TypeCache typeCache, SubtypeChecker subtypeChecker) {
		this.stack = stack;
		this.target = target;
		this.typeCache = typeCache;
		this.subtypeChecker = subtypeChecker;
		methodsByName = new HashMap<String, Map<Integer, List<Method>>>();
		for (Method method: ExpressionGenerator.getMethods(receiverType)) {
			String name = method.name();
			if (!methodsByName.containsKey(name))
				methodsByName.put(name, new HashMap<Integer, List<Method>>());
			Map<Integer, List<Method>> methodsWithName = methodsByName.get(name);
			int numArgs = method.argumentTypeNames().size();
			if (!methodsWithName.containsKey(numArgs))
				methodsWithName.put(numArgs, new ArrayList<Method>());
			methodsWithName.get(numArgs).add(method);
		}
	}
	
	/**
	 * Sets the method that future calls to needsCast will
	 * check.  This can be called multiple times, and will
	 * use the information from the last call.
	 * @param method The method to which future calls to
	 * needsCast will check.
	 */
	public void setMethod(Method method) {
		List<?> argumentTypeNames = method.argumentTypeNames();
		ArrayList<Method> potentialOverloads = getPotentialOverloads(method);
		assert !potentialOverloads.isEmpty();
		if (potentialOverloads.size() == 1)  // The current method is the only possibility, so there's no potential overloading.
			argTypes = null;
		else {
			argTypes = new ArrayList<Set<IJavaType>>(argumentTypeNames.size());
			for (int i = 0; i < argumentTypeNames.size(); i++) {
				Set<IJavaType> curTypes = new HashSet<IJavaType>();
				for (Method m : potentialOverloads)
					curTypes.add(EclipseUtils.getTypeAndLoadIfNeededAndExists((String)m.argumentTypeNames().get(i), stack, target, typeCache));
				argTypes.add(curTypes);
			}
		}
	}

	/**
	 * Gets methods that the compiler could think a call
	 * to the given method might also call.
	 * @param method The method that should be called.
	 * @return The methods that the compiler might think
	 * a call to the given method might call, including
	 * the given method itself.
	 */
	private ArrayList<Method> getPotentialOverloads(Method method) {
		ArrayList<Method> potentialOverloads = new ArrayList<Method>();
		methods: for (Method other : methodsByName.get(method.name()).get(method.argumentTypeNames().size())) {
			for (int i = 0; i < method.argumentTypeNames().size(); i++) {
				IJavaType curType = EclipseUtils.getTypeAndLoadIfNeededAndExists((String)method.argumentTypeNames().get(i), stack, target, typeCache);
				IJavaType otherType = EclipseUtils.getTypeAndLoadIfNeededAndExists((String)other.argumentTypeNames().get(i), stack, target, typeCache);
				if (EclipseUtils.isPrimitive(curType) != EclipseUtils.isPrimitive(otherType))
					continue methods;
			}
			potentialOverloads.add(other);
		}
		return potentialOverloads;
	}
	
	/**
	 * Checks whether the given parameter to the current method
	 * needs to be cast to the expected type to resolve overload
	 * ambiguities.
	 * @param argType The expected type of the parameter.
	 * @param curType The current type of the parameter.
	 * @param index The index of this parameter in the call.
	 * @return Whether this parameter needs a cast to resolve
	 * ambiguities.
	 */
	public boolean needsCast(IJavaType argType, IJavaType curType, int index) {
		if (argTypes == null || argType.equals(curType))
			return false;
		int numSubtypes = 0;
		for (IJavaType potentialType: argTypes.get(index)) {
			if (subtypeChecker.isSubtypeOf(curType, potentialType))
				numSubtypes++;
			if (numSubtypes > 1)
				return true;
		}
		return false;
	}

}
