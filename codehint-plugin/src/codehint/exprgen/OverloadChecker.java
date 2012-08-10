package codehint.exprgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.debug.core.IJavaType;

import com.sun.jdi.Method;

/**
 * A class that checks whether a given argument to a
 * method call needs a cast to remove ambiguities due
 * to overloaded methods.
 * The current implementation is conservative; it could
 * add unnecessary casts.
 */
public class OverloadChecker {
	
	private final Map<String, Map<Integer, List<Method>>> methodsByName;
	private boolean[] allHaveSameType;
	
	/**
	 * Creates a new OverloadChecker that will check methods
	 * of the given receiver.
	 * @param receiverType The type of the receiver of whose
	 * methods this object will check.
	 */
	public OverloadChecker(IJavaType receiverType) {
		List<Method> visibleMethods = ExpressionGenerator.getMethods(receiverType);
		methodsByName = new HashMap<String, Map<Integer, List<Method>>>();
		for (Method method: visibleMethods) {
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
		// TODO: Improve overloading detection.  One obvious way is to store, for each index, all the types possible at that index (instead of just a boolean indicating if they're all the same).  Then if a given type is only a subtype of one of them, we don't need a cast.
		List<?> argumentTypeNames = method.argumentTypeNames();
		allHaveSameType = new boolean[argumentTypeNames.size()];
		Arrays.fill(allHaveSameType, true);
		for (Method m : methodsByName.get(method.name()).get(argumentTypeNames.size()))
			for (int i = 0; i < argumentTypeNames.size(); i++)
				if (!m.argumentTypeNames().get(i).equals(argumentTypeNames.get(i)))
					allHaveSameType[i] = false;
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
		return !allHaveSameType[index] && !argType.equals(curType);
	}

}
