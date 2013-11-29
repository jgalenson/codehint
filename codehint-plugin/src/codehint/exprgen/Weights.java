package codehint.exprgen;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import com.sun.jdi.Field;
import com.sun.jdi.Method;

import codehint.utils.EclipseUtils;

/**
 * Stores our database of methods and fields seen in real code.
 * The main component is the weights map, which maps a fully-
 * qualified typename to a map from method keys (name + signature)
 * or field keys (name) to the (positive) number of times that
 * method/field was seen.  We also store the average weight of all
 * things in the database and the total number of calls/accesses
 * in the database.
 */
@SuppressWarnings("unchecked")
public class Weights {

	private final Map<String, Map<String, Integer>> weights;
	private final Map<String, Map<String, Integer>> methodsForConstants;
	private final double averageWeight;
	private final long total;
	private final double fieldWeight;

	public Weights() {
		try {
			ObjectInputStream is = new ObjectInputStream(new GZIPInputStream(EclipseUtils.getFileFromBundle("data" + System.getProperty("file.separator") + "weights.gz")));
			weights = (Map<String, Map<String, Integer>>)is.readObject();
			methodsForConstants = (Map<String, Map<String, Integer>>)is.readObject();
			averageWeight = is.readDouble();
			total = is.readLong();
			fieldWeight = is.readDouble();
			is.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	public double getAverageWeight() {
		return averageWeight;
	}
	
	private long getTotal() {
		return total;
	}
	
	public double getFieldWeight() {
		return fieldWeight;
	}
	
	// Note: Type keys do not use $ for nested types but method/field keys do.
	
	public double getWeight(String typeName, String key) {
		Map<String, Integer> calls = weights.get(typeName.replace("$", "."));
		if (calls == null)
			return averageWeight;
		/*int numCallsOn = 0;
		for (Integer n: calls.values())
			numCallsOn += n;
		assert numCallsOn > 0;*/
		Integer numCallsTo = calls.get(key);
		if (numCallsTo == null)
			return 0.1d / total;
		//return numCallsTo / (double)numCallsOn;
		return numCallsTo / (double)getTotal();
	}
	
	public boolean isRare(String typeName, String key) {
		Map<String, Integer> calls = weights.get(typeName.replace("$", "."));
		if (calls == null)
			return false;
		Integer numCallsTo = calls.get(key);
		return numCallsTo == null;
	}

	public static String getMethodKey(Method method) {
		return (method.isConstructor() ? "" : method.name()) + method.signature();
	}
	
	public boolean isUncommon(String typeName, String key) {
		Map<String, Integer> calls = weights.get(typeName.replace("$", "."));
		if (calls == null)
			return false;
		Integer numCallsTo = calls.get(key);
		if (numCallsTo == null)
			return true;
		int numCallsOn = 0;
		for (Integer n: calls.values())
			numCallsOn += n;
		return numCallsTo < ((numCallsOn / (double)calls.size()) / 5);
	}
	
	public boolean seenMethod(Method method) {
		Map<String, Integer> calls = weights.get(method.declaringType().name().replace("$", "."));
		if (calls == null)
			return false;
		return calls.get(getMethodKey(method)) != null;
	}

	public boolean isBadConstant(Method method, int i, Field field) {
		Map<String, Integer> locations = methodsForConstants.get(field.declaringType().name().replace("$", ".") + "." + field.name());
		if (locations == null)
			return false;
		Integer numUsesWith = locations.get(method.declaringType().name() + "~" + getMethodKey(method) + "~" + i);
		return numUsesWith == null;
	}
	
	/*private double getMethodWeightComplete(Method method, IJavaClassType type) throws DebugException {
		int numCallsTo = 0;
		int numCallsOn = 0;
		for (IJavaClassType superType = type; superType != null; superType = superType.getSuperclass()) {
			if (!hasMethod(superType, method))
				break;
			String typeSig = superType.getSignature();
			String methodKey = getMethodKey(method);
			numCallsTo += getNumCallsTo(typeSig, methodKey);
			numCallsOn += getNumCallsOn(typeSig);
		}
		for (IJavaReferenceType intType: type.getInterfaces()) {
			if (hasMethod(intType, method)) {
				String typeSig = intType.getSignature();
				String methodKey = getMethodKey(method);
				numCallsTo += getNumCallsTo(typeSig, methodKey);
				numCallsOn += getNumCallsOn(typeSig);
			}
		}
		if (numCallsOn == 0)
			return averageWeight;
		if (numCallsTo == 0)
			return EPSILON;
		return numCallsTo / (double)numCallsOn;
	}
	
	private static boolean hasMethod(IJavaReferenceType javaType, Method target) {
		ReferenceType type = (ReferenceType)((JDIType)javaType).getUnderlyingType();
		for (Method cur: type.allMethods())
			if (cur.name().equals(target.name()) && cur.signature().equals(target.signature()))
				return true;
		return false;
	}

	private IJavaReferenceType getReceiverType(MethodInvocation node, Set<Effect> curEffects) throws DebugException {
		IJavaReferenceType receiverType;
		if (node.getExpression() == null)
			receiverType = stack.getReferenceType();
		else {
			receiverType = expressionMaker.getStaticType(node.getExpression());
			if (receiverType == null)
				receiverType = (IJavaReferenceType)expressionMaker.getExpressionValue(node.getExpression(), curEffects).getJavaType();
		}
		return receiverType;
	}*/

}
