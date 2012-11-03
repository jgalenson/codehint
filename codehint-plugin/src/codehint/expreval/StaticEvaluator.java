package codehint.expreval;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassObject;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.exprgen.TypeCache;
import codehint.exprgen.TypedExpression;
import codehint.utils.EclipseUtils;

import com.sun.jdi.Method;

/**
 * Evaluates String and Arrays method calls.
 */
public class StaticEvaluator {
	
	private final IJavaStackFrame stack;
	private final TypeCache typeCache;
	private final ArrayList<IJavaObject> collectionDisableds;
	private int numCrashes;
	private final Set<String> unsupportedEncodings;
	private final Set<String> illegalPatterns;
	private final Map<String, IJavaValue> stringCache;
	
	public StaticEvaluator(IJavaStackFrame stack, TypeCache typeCache) {
		this.stack = stack;
		this.typeCache = typeCache;
		collectionDisableds = new ArrayList<IJavaObject>();
		numCrashes = 0;
		unsupportedEncodings = new HashSet<String>();
		illegalPatterns = new HashSet<String>();
		stringCache = new HashMap<String, IJavaValue>();
	}
	
	/**
	 * Evaluates the given method call  if possible.
	 * @param receiver The receiver, which may be
	 * a static name.
	 * @param args The arguments.
	 * @param method The method being called.
	 * @param target The debug target.
	 * @return The result of calling the given method with
	 * the given arguments, or null if we could not evaluate
	 * it, or void if the evaluation crashed.
	 */
	public IJavaValue evaluateCall(TypedExpression receiver, ArrayList<? extends TypedExpression> args, Method method, IJavaDebugTarget target) {
		String declaringType = method.declaringType().name();
		if (!"java.lang.String".equals(declaringType) && !"java.util.Arrays".equals(declaringType))
			return null;
		IJavaValue result = null;
		try {
			IJavaValue[] argVals = new IJavaValue[args.size()];
			for (int i = 0; i < args.size(); i++) {
				TypedExpression arg = args.get(i);
				if (arg.getValue() == null)
					return null;
				argVals[i] = arg.getValue();
			}
			if ("java.lang.String".equals(declaringType)) {
				if (receiver.getExpression() == null)
					result = evaluateStringConstructorCall(argVals, method, target);
				else
					result = evaluateStringCall(receiver.getValue() instanceof IJavaClassObject ? null : stringOfValue(receiver.getValue()), receiver.getValue(), argVals, method, target);
			} else if ("java.util.Arrays".equals(declaringType))
				result = evaluateArraysCall(argVals, method, target);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		} catch (Exception ex) {
			//System.out.println("Unexpected crash on " + receiver.getExpression().toString().replace("\n", "\\n") + "." + method.name() + args.toString().replace("\n", "\\n") + " with exception " + ex.getClass().toString());
			result = handleCrash(target);
		}
		//System.out.println("Evaluating " + receiver.getExpression().toString().replaceAll("[\n]", "\\\\n") + "." + method.name() + args.toString().replaceAll("[\n]", "\\\\n") + " and got " + (result == null ? "null" : result.toString().replaceAll("[\n]", "\\\\n")));
		return result;
	}
	
	/**
	 * Allow the strings we created ourselves to be collected.
	 */
	public void allowCollectionOfNewStrings() {
		try {
			for (IJavaObject obj: collectionDisableds)
				obj.enableCollection();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		collectionDisableds.clear();
	}
	
	public int getNumCrashes() {
		return numCrashes;
	}

	@SuppressWarnings("deprecation")
	private IJavaValue evaluateStringConstructorCall(IJavaValue[] args, Method method, IJavaDebugTarget target) throws DebugException {
		String sig = method.signature();
		if ("()V".equals(sig))
			return valueOfString("", target);
		if ("(Ljava/lang/String;)V".equals(sig))
			return valueOfString(stringOfValue(args[0]), target);
		if ("([C)V".equals(sig))
			return valueOfString(new String(charArrOfValue(args[0])), target);
		if ("([CII)V".equals(sig))
			return valueOfString(new String(charArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("([III)V".equals(sig))
			return valueOfString(new String(intArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("([BIII)V".equals(sig))
			return valueOfString(new String(byteArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2]), intOfValue(args[3])), target);
		if ("([BI)V".equals(sig))
			return valueOfString(new String(byteArrOfValue(args[0]), intOfValue(args[1])), target);
		if ("([BIILjava/lang/String;)V".equals(sig)) {
			String charsetName = stringOfValue(args[3]);
			if (!unsupportedEncodings.contains(charsetName)) {
				try {
					return valueOfString(new String(byteArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2]), charsetName), target);
				} catch (UnsupportedEncodingException ex) {
					unsupportedEncodings.add(charsetName);
				}
			}
			return handleCrash(target);
		}
		// String(byte[], int, int, java.nio.charset.Charset)
		if ("([BLjava/lang/String;)V".equals(sig)) {
			String charsetName = stringOfValue(args[1]);
			if (!unsupportedEncodings.contains(charsetName)) {
				try {
					return valueOfString(new String(byteArrOfValue(args[0]), charsetName), target);
				} catch (UnsupportedEncodingException ex) {
					unsupportedEncodings.add(charsetName);
				}
			}
			return handleCrash(target);
		}
		// String(byte[], java.nio.charset.Charset)
		if ("([BII)V".equals(sig))
			return valueOfString(new String(byteArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("([B)V".equals(sig))
			return valueOfString(new String(byteArrOfValue(args[0])), target);
		// String(java.lang.StringBuffer), String(java.lang.StringBuffer)
		return null;
	}

	/* More:
		public java.lang.String(byte[], int, int, java.nio.charset.Charset);
		  Signature: ([BIILjava/nio/charset/Charset;)V
		  
		public java.lang.String(java.lang.StringBuffer);
		  Signature: (Ljava/lang/StringBuffer;)V
		public java.lang.String(java.lang.StringBuilder);
		  Signature: (Ljava/lang/StringBuilder;)V
	 */
	
	private IJavaValue evaluateStringCall(String receiver, IJavaValue receiverValue, IJavaValue[] args, Method method, IJavaDebugTarget target) throws DebugException {
		String name = method.name();
		String sig = method.signature();
		if ("length".equals(name))
			return valueOfInt(receiver.length(), target);
		if ("isEmpty".equals(name))
			return valueOfBoolean(receiver.isEmpty(), target);
		if ("charAt".equals(name))
			return valueOfChar(receiver.charAt(intOfValue(args[0])), target);
		if ("codePointAt".equals(name))
			return valueOfInt(receiver.codePointAt(intOfValue(args[0])), target);
		if ("codePointBefore".equals(name))
			return valueOfInt(receiver.codePointBefore(intOfValue(args[0])), target);
		if ("codePointCount".equals(name))
			return valueOfInt(receiver.codePointCount(intOfValue(args[0]), intOfValue(args[1])), target);
		if ("offsetByCodePoints".equals(name)) {
			int index = intOfValue(args[0]);
			int codePointOffset = intOfValue(args[1]);
			if ((codePointOffset >= 0 && receiver.codePointCount(index, receiver.length()) >= codePointOffset)
					|| (codePointOffset < 0 && receiver.codePointCount(0, index) >= -codePointOffset))
				return valueOfInt(receiver.offsetByCodePoints(index, codePointOffset), target);
			else
				return handleCrash(target);
		}
		if ("getBytes".equals(name) && sig.equals("(Ljava/lang/String;)[B")) {
			String charsetName = stringOfValue(args[0]);
			if (!unsupportedEncodings.contains(charsetName)) {
				try {
					return valueOfByteArr(receiver.getBytes(charsetName), target);
				} catch (UnsupportedEncodingException ex) {
					unsupportedEncodings.add(charsetName);
				}
			}
			return handleCrash(target);
		}
		// getBytes
		if ("getBytes".equals(name) && sig.equals("()[B"))
			return valueOfByteArr(receiver.getBytes(), target);
		if ("equals".equals(name))
			if (isNullOrString(args[0]))
				return valueOfBoolean(receiver.equals(stringOfValue(args[0])), target);
			else
				return valueOfBoolean(false, target);
		// contentEquals
		if ("contentEquals".equals(name) && "(Ljava/lang/CharSequence;)Z".equals(sig)) {
			if (isNullOrString(args[0]))
				return valueOfBoolean(receiver.contentEquals(stringOfValue(args[0])), target);
			else
				return null;
		}
		if ("equalsIgnoreCase".equals(name))
			return valueOfBoolean(receiver.equalsIgnoreCase(stringOfValue(args[0])), target);
		if ("compareTo".equals(name))
			return valueOfInt(receiver.compareTo(stringOfValue(args[0])), target);
		if ("compareToIgnoreCase".equals(name))
			return valueOfInt(receiver.compareToIgnoreCase(stringOfValue(args[0])), target);
		if ("regionMatches".equals(name) && "(ILjava/lang/String;II)Z".equals(sig))
			return valueOfBoolean(receiver.regionMatches(intOfValue(args[0]), stringOfValue(args[1]), intOfValue(args[2]), intOfValue(args[3])), target);
		if ("regionMatches".equals(name) && "(ZILjava/lang/String;II)Z".equals(sig))
			return valueOfBoolean(receiver.regionMatches(booleanOfValue(args[0]), intOfValue(args[1]), stringOfValue(args[2]), intOfValue(args[3]), intOfValue(args[4])), target);
		if ("startsWith".equals(name) && "(Ljava/lang/String;I)Z".equals(sig))
			return valueOfBoolean(receiver.startsWith(stringOfValue(args[0]), intOfValue(args[1])), target);
		if ("startsWith".equals(name) && "(Ljava/lang/String;)Z".equals(sig))
			return valueOfBoolean(receiver.startsWith(stringOfValue(args[0])), target);
		if ("endsWith".equals(name) && "(Ljava/lang/String;)Z".equals(sig))
			return valueOfBoolean(receiver.endsWith(stringOfValue(args[0])), target);
		if ("hashCode".equals(name))
			return valueOfInt(receiver.hashCode(), target);
		if ("indexOf".equals(name) && "(I)I".equals(sig))
			return valueOfInt(receiver.indexOf(intOfValue(args[0])), target);
		if ("indexOf".equals(name) && "(II)I".equals(sig))
			return valueOfInt(receiver.indexOf(intOfValue(args[0]), intOfValue(args[1])), target);
		if ("lastIndexOf".equals(name) && "(I)I".equals(sig))
			return valueOfInt(receiver.lastIndexOf(intOfValue(args[0])), target);
		if ("lastIndexOf".equals(name) && "(II)I".equals(sig))
			return valueOfInt(receiver.lastIndexOf(intOfValue(args[0]), intOfValue(args[1])), target);
		if ("indexOf".equals(name) && "(Ljava/lang/String;)I".equals(sig))
			return valueOfInt(receiver.indexOf(stringOfValue(args[0])), target);
		if ("indexOf".equals(name) && "(Ljava/lang/String;I)I".equals(sig))
			return valueOfInt(receiver.indexOf(stringOfValue(args[0]), intOfValue(args[1])), target);
		if ("lastIndexOf".equals(name) && "(Ljava/lang/String;)I".equals(sig))
			return valueOfInt(receiver.lastIndexOf(stringOfValue(args[0])), target);
		if ("lastIndexOf".equals(name) && "(Ljava/lang/String;I)I".equals(sig))
			return valueOfInt(receiver.lastIndexOf(stringOfValue(args[0]), intOfValue(args[1])), target);
		if ("substring".equals(name) && "(I)Ljava/lang/String;".equals(sig))
			return valueOfString(receiver.substring(intOfValue(args[0])), target);
		if ("substring".equals(name) && "(II)Ljava/lang/String;".equals(sig))
			return valueOfString(receiver.substring(intOfValue(args[0]), intOfValue(args[1])), target);
		if ("subSequence".equals(name))
			return valueOfString((String)receiver.subSequence(intOfValue(args[0]), intOfValue(args[1])), target);
		if ("concat".equals(name))
			return valueOfString(receiver.concat(stringOfValue(args[0])), target);
		if ("replace".equals(name) && "(CC)Ljava/lang/String;".equals(sig))
			return valueOfString(receiver.replace(charOfValue(args[0]), charOfValue(args[1])), target);
		if ("matches".equals(name)) {
			String regex = stringOfValue(args[0]);
			if (!illegalPatterns.contains(regex)) {
				try {
					return valueOfBoolean(receiver.matches(regex), target);
				} catch (PatternSyntaxException ex) {
					illegalPatterns.add(regex);
				}
			}
			return handleCrash(target);
		}
		// contains
		if ("contains".equals(name)) {
			if (isNullOrString(args[0]))
				return valueOfBoolean(receiver.contains(stringOfValue(args[0])), target);
			else
				return null;
		}
		if ("replaceFirst".equals(name)) {
			String regex = stringOfValue(args[0]);
			if (!illegalPatterns.contains(regex)) {
				try {
					return valueOfString(receiver.replaceFirst(regex, stringOfValue(args[1])), target);
				} catch (PatternSyntaxException ex) {
					illegalPatterns.add(regex);
				}
			}
			return handleCrash(target);
		}
		if ("replaceAll".equals(name)) {
			String regex = stringOfValue(args[0]);
			if (!illegalPatterns.contains(regex)) {
				try {
					return valueOfString(receiver.replaceAll(regex, stringOfValue(args[1])), target);
				} catch (PatternSyntaxException ex) {
					illegalPatterns.add(regex);
				}
			}
			return handleCrash(target);
		}
		if ("replace".equals(name) && "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;".equals(sig)) {
			if (isNullOrString(args[0]) && isNullOrString(args[1]))
				return valueOfString(receiver.replace(stringOfValue(args[0]), stringOfValue(args[1])), target);
			else
				return null;
		}
		// replace
		if ("split".equals(name) && "(Ljava/lang/String;I)[Ljava/lang/String;".equals(sig)) {
			String regex = stringOfValue(args[0]);
			if (!illegalPatterns.contains(regex)) {
				try {
					return valueOfStringArr(receiver.split(regex, intOfValue(args[1])), target);
				} catch (PatternSyntaxException ex) {
					illegalPatterns.add(regex);
				}
			}
			return handleCrash(target);
		}
		if ("split".equals(name) && "(Ljava/lang/String;)[Ljava/lang/String;".equals(sig)) {
			String regex = stringOfValue(args[0]);
			if (!illegalPatterns.contains(regex)) {
				try {
					return valueOfStringArr(receiver.split(regex), target);
				} catch (PatternSyntaxException ex) {
					illegalPatterns.add(regex);
				}
			}
			return handleCrash(target);
		}
		// toLowerCase
		if ("toLowerCase".equals(name) && "()Ljava/lang/String;".equals(sig))
			return valueOfString(receiver.toLowerCase(), target);
		// toUpperCase
		if ("toUpperCase".equals(name) && "()Ljava/lang/String;".equals(sig))
			return valueOfString(receiver.toUpperCase(), target);
		if ("trim".equals(name))
			return valueOfString(receiver.trim(), target);
		if ("toString".equals(name))
			return receiverValue;
		if ("toCharArray".equals(name))
			return valueOfCharArr(receiver.toCharArray(), target);
		// format, valueOf, copyValueOf
		if ("valueOf".equals(name) && "(Ljava/lang/Object;)Ljava/lang/String;".equals(sig)) {
			if (args[0].isNull())
				return valueOfString("null", target);
			else if (isNonNullString(args[0]))
				return args[0];
			else
				return null;
		}
		if ("valueOf".equals(name) && "([C)Ljava/lang/String;".equals(sig))
			return valueOfString(String.valueOf(charArrOfValue(args[0])), target);
		if ("valueOf".equals(name) && "([CII)Ljava/lang/String;".equals(sig))
			return valueOfString(String.valueOf(charArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("copyValueOf".equals(name) && "([CII)Ljava/lang/String;".equals(sig))
			return valueOfString(String.copyValueOf(charArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("copyValueOf".equals(name) && "([C)Ljava/lang/String;".equals(sig))
			return valueOfString(String.copyValueOf(charArrOfValue(args[0])), target);
		if ("valueOf".equals(name) && "(Z)Ljava/lang/String;".equals(sig))
			return valueOfString(String.valueOf(booleanOfValue(args[0])), target);
		if ("valueOf".equals(name) && "(C)Ljava/lang/String;".equals(sig))
			return valueOfString(String.valueOf(charOfValue(args[0])), target);
		if ("valueOf".equals(name) && "(I)Ljava/lang/String;".equals(sig))
			return valueOfString(String.valueOf(intOfValue(args[0])), target);
		if ("valueOf".equals(name) && "(J)Ljava/lang/String;".equals(sig))
			return valueOfString(String.valueOf(longOfValue(args[0])), target);
		if ("valueOf".equals(name) && "(F)Ljava/lang/String;".equals(sig))
			return valueOfString(String.valueOf(floatOfValue(args[0])), target);
		if ("valueOf".equals(name) && "(D)Ljava/lang/String;".equals(sig))
			return valueOfString(String.valueOf(doubleOfValue(args[0])), target);
		// intern, compareTo
		return null;
	}

	/* More
		public byte[] getBytes(java.nio.charset.Charset);
		  Signature: (Ljava/nio/charset/Charset;)[B
		  
		public boolean contentEquals(java.lang.StringBuffer);
		  Signature: (Ljava/lang/StringBuffer;)Z
		public boolean contentEquals(java.lang.CharSequence);
		  Signature: (Ljava/lang/CharSequence;)Z
		  
		public boolean contains(java.lang.CharSequence);
		  Signature: (Ljava/lang/CharSequence;)Z
		  
		public java.lang.String replace(java.lang.CharSequence, java.lang.CharSequence);
		  Signature: (Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
		  
		public java.lang.String toLowerCase(java.util.Locale);
		  Signature: (Ljava/util/Locale;)Ljava/lang/String;
		  
		public java.lang.String toUpperCase(java.util.Locale);
		  Signature: (Ljava/util/Locale;)Ljava/lang/String;
		  
		public static java.lang.String format(java.lang.String, java.lang.Object[]);
		  Signature: (Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;
		public static java.lang.String format(java.util.Locale, java.lang.String, java.lang.Object[]);
		  Signature: (Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;
		public static java.lang.String valueOf(java.lang.Object);
		  Signature: (Ljava/lang/Object;)Ljava/lang/String;
		  
		public native java.lang.String intern();
		  Signature: ()Ljava/lang/String;
		public int compareTo(java.lang.Object);
		  Signature: (Ljava/lang/Object;)I
	 */

	private IJavaValue evaluateArraysCall(IJavaValue[] args, Method method, IJavaDebugTarget target) throws DebugException {
		String name = method.name();
		String sig = method.signature();
		if ("binarySearch".equals(name) && "([JJ)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(longArrOfValue(args[0]), longOfValue(args[1])), target);
		if ("binarySearch".equals(name) && "([JIIJ)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(longArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2]), longOfValue(args[3])), target);
		if ("binarySearch".equals(name) && "([II)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(intArrOfValue(args[0]), intOfValue(args[1])), target);
		if ("binarySearch".equals(name) && "([IIII)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(intArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2]), intOfValue(args[3])), target);
		if ("binarySearch".equals(name) && "([SS)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(shortArrOfValue(args[0]), shortOfValue(args[1])), target);
		if ("binarySearch".equals(name) && "([SIIS)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(shortArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2]), shortOfValue(args[3])), target);
		if ("binarySearch".equals(name) && "([CC)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(charArrOfValue(args[0]), charOfValue(args[1])), target);
		if ("binarySearch".equals(name) && "([CIIC)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(charArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2]), charOfValue(args[3])), target);
		if ("binarySearch".equals(name) && "([BB)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(byteArrOfValue(args[0]), byteOfValue(args[1])), target);
		if ("binarySearch".equals(name) && "([BIIB)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(byteArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2]), byteOfValue(args[3])), target);
		if ("binarySearch".equals(name) && "([DD)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(doubleArrOfValue(args[0]), doubleOfValue(args[1])), target);
		if ("binarySearch".equals(name) && "([DIID)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(doubleArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2]), doubleOfValue(args[3])), target);
		if ("binarySearch".equals(name) && "([FF)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(floatArrOfValue(args[0]), floatOfValue(args[1])), target);
		if ("binarySearch".equals(name) && "([FIIF)I".equals(sig))
			return valueOfInt(Arrays.binarySearch(floatArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2]), floatOfValue(args[3])), target);
		// binarySearch
		if ("binarySearch".equals(name) && "([Ljava/lang/Object;Ljava/lang/Object;)I".equals(sig)) {
			if (isNullOrStringArr(args[0]) && isNullOrString(args[1])) {
				if (args[1].isNull())
					return handleCrash(target);
				else
					return valueOfInt(Arrays.binarySearch(stringArrOfValue(args[0]), stringOfValue(args[1])), target);
			} else if (((IJavaArray)args[0]).getLength() > 0 && isNullOrStringArr(args[0]) && !isNullOrString(args[1]))
				return handleCrash(target);
			 else
				return null;
		}
		if ("binarySearch".equals(name) && "([Ljava/lang/Object;IILjava/lang/Object;)I".equals(sig)) {
			if (isNullOrStringArr(args[0]) && isNullOrString(args[1])) {
				if (args[3].isNull())
					return handleCrash(target);
				else
					return valueOfInt(Arrays.binarySearch(stringArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2]), stringOfValue(args[3])), target);
			} else
				return null;
		}
		if ("binarySearch".equals(name) && "([Ljava/lang/Object;Ljava/lang/Object;Ljava/util/Comparator;)I".equals(sig)) {
			if (isNullOrStringArr(args[0]) && isNullOrString(args[1]) && args[2].isNull()) {
				if (args[1].isNull())
					return handleCrash(target);
				else
					return valueOfInt(Arrays.binarySearch(stringArrOfValue(args[0]), stringOfValue(args[1]), null), target);
			} else if (args[2].isNull() && ((IJavaArray)args[0]).getLength() > 0 && isNullOrStringArr(args[0]) && !isNullOrString(args[1]))
				return handleCrash(target);
			else
				return null;
		}
		if ("binarySearch".equals(name) && "([Ljava/lang/Object;IILjava/lang/Object;Ljava/util/Comparator;)I".equals(sig)) {
			if (isNullOrStringArr(args[0]) && isNullOrString(args[3]) && args[4].isNull()) {
				if (args[3].isNull())
					return handleCrash(target);
				else
					return valueOfInt(Arrays.binarySearch(stringArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2]), stringOfValue(args[3]), null), target);
			} else
				return null;
		}
		if ("equals".equals(name) && "([J[J)Z".equals(sig))
			return valueOfBoolean(Arrays.equals(longArrOfValue(args[0]), longArrOfValue(args[1])), target);
		if ("equals".equals(name) && "([I[I)Z".equals(sig))
			return valueOfBoolean(Arrays.equals(intArrOfValue(args[0]), intArrOfValue(args[1])), target);
		if ("equals".equals(name) && "([S[S)Z".equals(sig))
			return valueOfBoolean(Arrays.equals(shortArrOfValue(args[0]), shortArrOfValue(args[1])), target);
		if ("equals".equals(name) && "([C[C)Z".equals(sig))
			return valueOfBoolean(Arrays.equals(charArrOfValue(args[0]), charArrOfValue(args[1])), target);
		if ("equals".equals(name) && "([B[B)Z".equals(sig))
			return valueOfBoolean(Arrays.equals(byteArrOfValue(args[0]), byteArrOfValue(args[1])), target);
		if ("equals".equals(name) && "([Z[Z)Z".equals(sig))
			return valueOfBoolean(Arrays.equals(booleanArrOfValue(args[0]), booleanArrOfValue(args[1])), target);
		if ("equals".equals(name) && "([D[D)Z".equals(sig))
			return valueOfBoolean(Arrays.equals(doubleArrOfValue(args[0]), doubleArrOfValue(args[1])), target);
		if ("equals".equals(name) && "([F[F)Z".equals(sig))
			return valueOfBoolean(Arrays.equals(floatArrOfValue(args[0]), floatArrOfValue(args[1])), target);
		// equals
		if ("equals".equals(name) && "([Ljava/lang/Object;[Ljava/lang/Object;)Z".equals(sig)) {
			if (isNullOrStringArr(args[0]) && isNullOrStringArr(args[1]))
				return valueOfBoolean(Arrays.equals(stringArrOfValue(args[0]), stringArrOfValue(args[1])), target);
			else
				return null;
		}
		// copyOf
		if ("copyOf".equals(name) && "([Ljava/lang/Object;I)[Ljava/lang/Object;".equals(sig)) {
			if (isNullOrStringArr(args[0]))
				return valueOfStringArr(Arrays.copyOf(stringArrOfValue(args[0]), intOfValue(args[1])), target);
			else
				return null;
		}
		if ("copyOf".equals(name) && "([BI)[B".equals(sig))
			return valueOfByteArr(Arrays.copyOf(byteArrOfValue(args[0]), intOfValue(args[1])), target);
		if ("copyOf".equals(name) && "([SI)[S".equals(sig))
			return valueOfShortArr(Arrays.copyOf(shortArrOfValue(args[0]), intOfValue(args[1])), target);
		if ("copyOf".equals(name) && "([II)[I".equals(sig))
			return valueOfIntArr(Arrays.copyOf(intArrOfValue(args[0]), intOfValue(args[1])), target);
		if ("copyOf".equals(name) && "([JI)[J".equals(sig))
			return valueOfLongArr(Arrays.copyOf(longArrOfValue(args[0]), intOfValue(args[1])), target);
		if ("copyOf".equals(name) && "([CI)[C".equals(sig))
			return valueOfCharArr(Arrays.copyOf(charArrOfValue(args[0]), intOfValue(args[1])), target);
		if ("copyOf".equals(name) && "([FI)[F".equals(sig))
			return valueOfFloatArr(Arrays.copyOf(floatArrOfValue(args[0]), intOfValue(args[1])), target);
		if ("copyOf".equals(name) && "([DI)[D".equals(sig))
			return valueOfDoubleArr(Arrays.copyOf(doubleArrOfValue(args[0]), intOfValue(args[1])), target);
		if ("copyOf".equals(name) && "([ZI)[Z".equals(sig))
			return valueOfBooleanArr(Arrays.copyOf(booleanArrOfValue(args[0]), intOfValue(args[1])), target);
		// copyOfRange
		if ("copyOfRange".equals(name) && "([Ljava/lang/Object;II)[Ljava/lang/Object;".equals(sig)) {
			if (isNullOrStringArr(args[0]))
				return valueOfStringArr(Arrays.copyOfRange(stringArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
			else
				return null;
		}
		if ("copyOfRange".equals(name) && "([BII)[B".equals(sig))
			return valueOfByteArr(Arrays.copyOfRange(byteArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("copyOfRange".equals(name) && "([SII)[S".equals(sig))
			return valueOfShortArr(Arrays.copyOfRange(shortArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("copyOfRange".equals(name) && "([III)[I".equals(sig))
			return valueOfIntArr(Arrays.copyOfRange(intArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("copyOfRange".equals(name) && "([JII)[J".equals(sig))
			return valueOfLongArr(Arrays.copyOfRange(longArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("copyOfRange".equals(name) && "([CII)[C".equals(sig))
			return valueOfCharArr(Arrays.copyOfRange(charArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("copyOfRange".equals(name) && "([FII)[F".equals(sig))
			return valueOfFloatArr(Arrays.copyOfRange(floatArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("copyOfRange".equals(name) && "([DII)[D".equals(sig))
			return valueOfDoubleArr(Arrays.copyOfRange(doubleArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		if ("copyOfRange".equals(name) && "([ZII)[Z".equals(sig))
			return valueOfBooleanArr(Arrays.copyOfRange(booleanArrOfValue(args[0]), intOfValue(args[1]), intOfValue(args[2])), target);
		// asList, hashCode, deepEquals
		if ("deepEquals".equals(name) && "([Ljava/lang/Object;[Ljava/lang/Object;)Z".equals(sig)) {
			if (isNullOrStringArr(args[0]) && isNullOrStringArr(args[1]))
				return valueOfBoolean(Arrays.deepEquals(stringArrOfValue(args[0]), stringArrOfValue(args[1])), target);
			else
				return null;
		}
		if ("toString".equals(name) && "([J)Ljava/lang/String;".equals(sig))
			return valueOfString(Arrays.toString(longArrOfValue(args[0])), target);
		if ("toString".equals(name) && "([I)Ljava/lang/String;".equals(sig))
			return valueOfString(Arrays.toString(intArrOfValue(args[0])), target);
		if ("toString".equals(name) && "([S)Ljava/lang/String;".equals(sig))
			return valueOfString(Arrays.toString(shortArrOfValue(args[0])), target);
		if ("toString".equals(name) && "([C)Ljava/lang/String;".equals(sig))
			return valueOfString(Arrays.toString(charArrOfValue(args[0])), target);
		if ("toString".equals(name) && "([B)Ljava/lang/String;".equals(sig))
			return valueOfString(Arrays.toString(byteArrOfValue(args[0])), target);
		if ("toString".equals(name) && "([Z)Ljava/lang/String;".equals(sig))
			return valueOfString(Arrays.toString(booleanArrOfValue(args[0])), target);
		if ("toString".equals(name) && "([F)Ljava/lang/String;".equals(sig))
			return valueOfString(Arrays.toString(floatArrOfValue(args[0])), target);
		if ("toString".equals(name) && "([D)Ljava/lang/String;".equals(sig))
			return valueOfString(Arrays.toString(doubleArrOfValue(args[0])), target);
		// toString, deepToString
		if ("toString".equals(name) && "([Ljava/lang/Object;)Ljava/lang/String;".equals(sig)) {
			if (isNullOrStringArr(args[0]))
				return valueOfString(Arrays.toString(stringArrOfValue(args[0])), target);
			else
				return null;
		}
		if ("deepToString".equals(name) && "([Ljava/lang/Object;)Ljava/lang/String;".equals(sig)) {
			if (isNullOrStringArr(args[0]))
				return valueOfString(Arrays.deepToString(stringArrOfValue(args[0])), target);
			else
				return null;
		}
		return null;
	}
	
	/* More:
		public static int binarySearch(java.lang.Object[], java.lang.Object);
		  Signature: ([Ljava/lang/Object;Ljava/lang/Object;)I
		public static int binarySearch(java.lang.Object[], int, int, java.lang.Object);
		  Signature: ([Ljava/lang/Object;IILjava/lang/Object;)I
		public static int binarySearch(java.lang.Object[], java.lang.Object, java.util.Comparator);
		  Signature: ([Ljava/lang/Object;Ljava/lang/Object;Ljava/util/Comparator;)I
		public static int binarySearch(java.lang.Object[], int, int, java.lang.Object, java.util.Comparator);
		  Signature: ([Ljava/lang/Object;IILjava/lang/Object;Ljava/util/Comparator;)I
		  
		public static boolean equals(java.lang.Object[], java.lang.Object[]);
		  Signature: ([Ljava/lang/Object;[Ljava/lang/Object;)Z
		  
		public static java.lang.Object[] copyOf(java.lang.Object[], int);
		  Signature: ([Ljava/lang/Object;I)[Ljava/lang/Object;
		public static java.lang.Object[] copyOf(java.lang.Object[], int, java.lang.Class);
		  Signature: ([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;
		  
		public static java.lang.Object[] copyOfRange(java.lang.Object[], int, int);
		  Signature: ([Ljava/lang/Object;II)[Ljava/lang/Object;
		public static java.lang.Object[] copyOfRange(java.lang.Object[], int, int, java.lang.Class);
		  Signature: ([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;
		  
		public static java.util.List asList(java.lang.Object[]);
		  Signature: ([Ljava/lang/Object;)Ljava/util/List;
		public static int hashCode(long[]);
		  Signature: ([J)I
		public static int hashCode(int[]);
		  Signature: ([I)I
		public static int hashCode(short[]);
		  Signature: ([S)I
		public static int hashCode(char[]);
		  Signature: ([C)I
		public static int hashCode(byte[]);
		  Signature: ([B)I
		public static int hashCode(boolean[]);
		  Signature: ([Z)I
		public static int hashCode(float[]);
		  Signature: ([F)I
		public static int hashCode(double[]);
		  Signature: ([D)I
		public static int hashCode(java.lang.Object[]);
		  Signature: ([Ljava/lang/Object;)I
		public static int deepHashCode(java.lang.Object[]);
		  Signature: ([Ljava/lang/Object;)I
		public static boolean deepEquals(java.lang.Object[], java.lang.Object[]);
		  Signature: ([Ljava/lang/Object;[Ljava/lang/Object;)Z
		  
		public static java.lang.String toString(java.lang.Object[]);
		  Signature: ([Ljava/lang/Object;)Ljava/lang/String;
		public static java.lang.String deepToString(java.lang.Object[]);
		  Signature: ([Ljava/lang/Object;)Ljava/lang/String;
		}
	 */

	private IJavaValue handleCrash(IJavaDebugTarget target) {
		numCrashes++;
		return target.voidValue();
	}
	
	// Check type
	
	private static boolean isNonNullString(IJavaValue value) throws DebugException {
		return "Ljava/lang/String;".equals(value.getSignature());
	}
	
	private static boolean isNullOrString(IJavaValue value) throws DebugException {
		return value.isNull() || "Ljava/lang/String;".equals(value.getSignature());
	}
	
	private static boolean isNullOrStringArr(IJavaValue value) throws DebugException {
		return value.isNull() || "[Ljava/lang/String;".equals(value.getSignature());
	}
	
	// Convert IJavaValue to actual values
	
	private static String stringOfValue(IJavaValue value) throws DebugException {
		assert isNullOrString(value) : value;
		if (value.isNull())
			return null;
		else
			return value.getValueString();
	}
	
	private static int intOfValue(IJavaValue value) {
		return ((IJavaPrimitiveValue)value).getIntValue();
	}
	
	private static boolean booleanOfValue(IJavaValue value) {
		return ((IJavaPrimitiveValue)value).getBooleanValue();
	}
	
	private static char charOfValue(IJavaValue value) {
		return ((IJavaPrimitiveValue)value).getCharValue();
	}
	
	private static long longOfValue(IJavaValue value) {
		return ((IJavaPrimitiveValue)value).getLongValue();
	}
	
	private static float floatOfValue(IJavaValue value) {
		return ((IJavaPrimitiveValue)value).getFloatValue();
	}
	
	private static double doubleOfValue(IJavaValue value) {
		return ((IJavaPrimitiveValue)value).getDoubleValue();
	}
	
	private static byte byteOfValue(IJavaValue value) {
		return ((IJavaPrimitiveValue)value).getByteValue();
	}
	
	private static short shortOfValue(IJavaValue value) {
		return ((IJavaPrimitiveValue)value).getShortValue();
	}
	
	private static char[] charArrOfValue(IJavaValue value) throws DebugException {
		if (value.isNull())
			return null;
		IJavaArray array = (IJavaArray)value;
		char[] result = new char[array.getLength()];
		IJavaValue[] arrayValues = array.getValues();
		for (int i = 0; i < result.length; i++)
			result[i] = charOfValue(arrayValues[i]);
		return result;
	}
	
	private static byte[] byteArrOfValue(IJavaValue value) throws DebugException {
		if (value.isNull())
			return null;
		IJavaArray array = (IJavaArray)value;
		byte[] result = new byte[array.getLength()];
		IJavaValue[] arrayValues = array.getValues();
		for (int i = 0; i < result.length; i++)
			result[i] = byteOfValue(arrayValues[i]);
		return result;
	}
	
	private static long[] longArrOfValue(IJavaValue value) throws DebugException {
		if (value.isNull())
			return null;
		IJavaArray array = (IJavaArray)value;
		long[] result = new long[array.getLength()];
		IJavaValue[] arrayValues = array.getValues();
		for (int i = 0; i < result.length; i++)
			result[i] = longOfValue(arrayValues[i]);
		return result;
	}
	
	private static int[] intArrOfValue(IJavaValue value) throws DebugException {
		if (value.isNull())
			return null;
		IJavaArray array = (IJavaArray)value;
		int[] result = new int[array.getLength()];
		IJavaValue[] arrayValues = array.getValues();
		for (int i = 0; i < result.length; i++)
			result[i] = intOfValue(arrayValues[i]);
		return result;
	}
	
	private static short[] shortArrOfValue(IJavaValue value) throws DebugException {
		if (value.isNull())
			return null;
		IJavaArray array = (IJavaArray)value;
		short[] result = new short[array.getLength()];
		IJavaValue[] arrayValues = array.getValues();
		for (int i = 0; i < result.length; i++)
			result[i] = shortOfValue(arrayValues[i]);
		return result;
	}
	
	private static double[] doubleArrOfValue(IJavaValue value) throws DebugException {
		if (value.isNull())
			return null;
		IJavaArray array = (IJavaArray)value;
		double[] result = new double[array.getLength()];
		IJavaValue[] arrayValues = array.getValues();
		for (int i = 0; i < result.length; i++)
			result[i] = doubleOfValue(arrayValues[i]);
		return result;
	}
	
	private static float[] floatArrOfValue(IJavaValue value) throws DebugException {
		if (value.isNull())
			return null;
		IJavaArray array = (IJavaArray)value;
		float[] result = new float[array.getLength()];
		IJavaValue[] arrayValues = array.getValues();
		for (int i = 0; i < result.length; i++)
			result[i] = floatOfValue(arrayValues[i]);
		return result;
	}
	
	private static boolean[] booleanArrOfValue(IJavaValue value) throws DebugException {
		if (value.isNull())
			return null;
		IJavaArray array = (IJavaArray)value;
		boolean[] result = new boolean[array.getLength()];
		IJavaValue[] arrayValues = array.getValues();
		for (int i = 0; i < result.length; i++)
			result[i] = booleanOfValue(arrayValues[i]);
		return result;
	}
	
	private static String[] stringArrOfValue(IJavaValue value) throws DebugException {
		if (value.isNull())
			return null;
		IJavaArray array = (IJavaArray)value;
		String[] result = new String[array.getLength()];
		IJavaValue[] arrayValues = array.getValues();
		for (int i = 0; i < result.length; i++)
			result[i] = stringOfValue(arrayValues[i]);
		return result;
	}
	
	// Convert actual values to IJavaValues

	// We must disable collection on these strings since they are not reachable and hence could be collected.  Without this, the strings are collected and the EvaluationManager inserts non-quoted string literals for them and crashes.
	private <T extends IJavaObject> T disableObjectCollection(T o) throws DebugException {
		o.disableCollection();
		collectionDisableds.add(o);
		return o;
	}
	
	private IJavaValue valueOfString(String s, IJavaDebugTarget target) throws DebugException {
		IJavaValue value = stringCache.get(s);
		if (value != null)
			return value;
		value = disableObjectCollection((IJavaObject)target.newValue(s));
		stringCache.put(s, value);
		return value;
	}
	
	private static IJavaValue valueOfInt(int n, IJavaDebugTarget target) {
		return target.newValue(n);
	}
	
	private static IJavaValue valueOfBoolean(boolean b, IJavaDebugTarget target) {
		return target.newValue(b);
	}
	
	private static IJavaValue valueOfChar(char c, IJavaDebugTarget target) {
		return target.newValue(c);
	}
	
	private static IJavaValue valueOfByte(byte b, IJavaDebugTarget target) {
		return target.newValue(b);
	}
	
	private static IJavaValue valueOfLong(long l, IJavaDebugTarget target) {
		return target.newValue(l);
	}
	
	private static IJavaValue valueOfShort(short s, IJavaDebugTarget target) {
		return target.newValue(s);
	}
	
	private static IJavaValue valueOfFloat(float f, IJavaDebugTarget target) {
		return target.newValue(f);
	}
	
	private static IJavaValue valueOfDouble(double d, IJavaDebugTarget target) {
		return target.newValue(d);
	}
	
	private IJavaArray valueOfCharArr(char[] a, IJavaDebugTarget target) throws DebugException {
		IJavaArrayType charArrType = (IJavaArrayType)EclipseUtils.getFullyQualifiedType("char[]", stack, target, typeCache);
		IJavaArray result = disableObjectCollection(charArrType.newInstance(a.length));
		IJavaValue[] arrValues = new IJavaValue[a.length];
		for (int i = 0; i < a.length; i++)
			arrValues[i] = valueOfChar(a[i], target);
		result.setValues(arrValues);
		return result;
	}
	
	private IJavaArray valueOfByteArr(byte[] a, IJavaDebugTarget target) throws DebugException {
		IJavaArrayType byteArrType = (IJavaArrayType)EclipseUtils.getFullyQualifiedType("byte[]", stack, target, typeCache);
		IJavaArray result = disableObjectCollection(byteArrType.newInstance(a.length));
		IJavaValue[] arrValues = new IJavaValue[a.length];
		for (int i = 0; i < a.length; i++)
			arrValues[i] = valueOfByte(a[i], target);
		result.setValues(arrValues);
		return result;
	}
	
	private IJavaArray valueOfStringArr(String[] a, IJavaDebugTarget target) throws DebugException {
		IJavaArrayType stringArrType = (IJavaArrayType)EclipseUtils.getFullyQualifiedType("java.lang.String[]", stack, target, typeCache);
		IJavaArray result = disableObjectCollection(stringArrType.newInstance(a.length));
		IJavaValue[] arrValues = new IJavaValue[a.length];
		for (int i = 0; i < a.length; i++)
			arrValues[i] = a[i] == null ? target.nullValue() : valueOfString(a[i], target);
		result.setValues(arrValues);
		return result;
	}
	
	private IJavaArray valueOfLongArr(long[] a, IJavaDebugTarget target) throws DebugException {
		IJavaArrayType longArrType = (IJavaArrayType)target.getJavaTypes("long[]")[0];
		IJavaArray result = disableObjectCollection(longArrType.newInstance(a.length));
		IJavaValue[] arrValues = new IJavaValue[a.length];
		for (int i = 0; i < a.length; i++)
			arrValues[i] = valueOfLong(a[i], target);
		result.setValues(arrValues);
		return result;
	}
	
	private IJavaArray valueOfShortArr(short[] a, IJavaDebugTarget target) throws DebugException {
		IJavaArrayType shortArrType = (IJavaArrayType)EclipseUtils.getFullyQualifiedType("short[]", stack, target, typeCache);
		IJavaArray result = disableObjectCollection(shortArrType.newInstance(a.length));
		IJavaValue[] arrValues = new IJavaValue[a.length];
		for (int i = 0; i < a.length; i++)
			arrValues[i] = valueOfShort(a[i], target);
		result.setValues(arrValues);
		return result;
	}
	
	private IJavaArray valueOfIntArr(int[] a, IJavaDebugTarget target) throws DebugException {
		IJavaArrayType intArrType = (IJavaArrayType)EclipseUtils.getFullyQualifiedType("int[]", stack, target, typeCache);
		IJavaArray result = disableObjectCollection(intArrType.newInstance(a.length));
		IJavaValue[] arrValues = new IJavaValue[a.length];
		for (int i = 0; i < a.length; i++)
			arrValues[i] = valueOfInt(a[i], target);
		result.setValues(arrValues);
		return result;
	}
	
	private IJavaArray valueOfFloatArr(float[] a, IJavaDebugTarget target) throws DebugException {
		IJavaArrayType floatArrType = (IJavaArrayType)EclipseUtils.getFullyQualifiedType("float[]", stack, target, typeCache);
		IJavaArray result = disableObjectCollection(floatArrType.newInstance(a.length));
		IJavaValue[] arrValues = new IJavaValue[a.length];
		for (int i = 0; i < a.length; i++)
			arrValues[i] = valueOfFloat(a[i], target);
		result.setValues(arrValues);
		return result;
	}
	
	private IJavaArray valueOfDoubleArr(double[] a, IJavaDebugTarget target) throws DebugException {
		IJavaArrayType doubleArrType = (IJavaArrayType)EclipseUtils.getFullyQualifiedType("double[]", stack, target, typeCache);
		IJavaArray result = disableObjectCollection(doubleArrType.newInstance(a.length));
		IJavaValue[] arrValues = new IJavaValue[a.length];
		for (int i = 0; i < a.length; i++)
			arrValues[i] = valueOfDouble(a[i], target);
		result.setValues(arrValues);
		return result;
	}
	
	private IJavaArray valueOfBooleanArr(boolean[] a, IJavaDebugTarget target) throws DebugException {
		IJavaArrayType booleanArrType = (IJavaArrayType)EclipseUtils.getFullyQualifiedType("boolean[]", stack, target, typeCache);
		IJavaArray result = disableObjectCollection(booleanArrType.newInstance(a.length));
		IJavaValue[] arrValues = new IJavaValue[a.length];
		for (int i = 0; i < a.length; i++)
			arrValues[i] = valueOfBoolean(a[i], target);
		result.setValues(arrValues);
		return result;
	}

}
