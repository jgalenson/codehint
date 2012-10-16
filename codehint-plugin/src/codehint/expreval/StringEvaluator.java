package codehint.expreval;

import java.util.ArrayList;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.exprgen.TypedExpression;

import com.sun.jdi.Method;

/**
 * Evaluates String method calls.
 */
public class StringEvaluator {
	
	/**
	 * Evaluates the given method call, which must be in
	 * the String class, if possible.
	 * @param receiver The receiver, which may be the
	 * static name String.
	 * @param args The arguments.
	 * @param method The method being called.
	 * @param target The debug target.
	 * @return The result of calling the given method with
	 * the given arguments, or null if we could not evaluate
	 * it, or void if the evaluation crashed.
	 */
	public static IJavaValue evaluateCall(TypedExpression receiver, ArrayList<? extends TypedExpression> args, Method method, IJavaDebugTarget target) {
		IJavaValue result = null;
		try {
			IJavaValue[] argVals = new IJavaValue[args.size()];
			for (int i = 0; i < args.size(); i++) {
				TypedExpression arg = args.get(i);
				if (arg.getValue() == null)
					return null;
				argVals[i] = arg.getValue();
			}
			if (receiver.getExpression() == null)
				result = evaluateConstructorCall(argVals, method, target);
			else
				result = evaluateCall(stringOfValue(receiver.getValue()), receiver.getValue(), argVals, method, target);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		} catch (Exception ex) {
			result = target.voidValue();
		}
		//System.out.println("Evaluating " + receiver.getExpression().toString().replaceAll("[\n]", "\\\\n") + "." + method.name() + args.toString().replaceAll("[\n]", "\\\\n") + " and got " + (result == null ? "null" : result.toString().replaceAll("[\n]", "\\\\n")));
		return result;
	}

	private static IJavaValue evaluateConstructorCall(IJavaValue[] args, Method method, IJavaDebugTarget target) throws DebugException {
		String sig = method.signature();
		if ("()V".equals(sig))
			return valueOfString("", target);
		if ("(Ljava/lang/String;)V".equals(sig))
			return valueOfString(stringOfValue(args[0]), target);
		return null;
	}

	/* More:
		public java.lang.String(char[]);
		  Signature: ([C)V
		public java.lang.String(char[], int, int);
		  Signature: ([CII)V
		public java.lang.String(int[], int, int);
		  Signature: ([III)V
		public java.lang.String(byte[], int, int, int);
		  Signature: ([BIII)V
		public java.lang.String(byte[], int);
		  Signature: ([BI)V
		public java.lang.String(byte[], int, int, java.lang.String)   throws java.io.UnsupportedEncodingException;
		  Signature: ([BIILjava/lang/String;)V
		public java.lang.String(byte[], int, int, java.nio.charset.Charset);
		  Signature: ([BIILjava/nio/charset/Charset;)V
		public java.lang.String(byte[], java.lang.String)   throws java.io.UnsupportedEncodingException;
		  Signature: ([BLjava/lang/String;)V
		public java.lang.String(byte[], java.nio.charset.Charset);
		  Signature: ([BLjava/nio/charset/Charset;)V
		public java.lang.String(byte[], int, int);
		  Signature: ([BII)V
		public java.lang.String(byte[]);
		  Signature: ([B)V
		public java.lang.String(java.lang.StringBuffer);
		  Signature: (Ljava/lang/StringBuffer;)V
		public java.lang.String(java.lang.StringBuilder);
		  Signature: (Ljava/lang/StringBuilder;)V
	 */
	
	private static IJavaValue evaluateCall(String receiver, IJavaValue receiverValue, IJavaValue[] args, Method method, IJavaDebugTarget target) throws DebugException {
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
		if ("offsetByCodePoints".equals(name))
			return valueOfInt(receiver.offsetByCodePoints(intOfValue(args[0]), intOfValue(args[1])), target);
		// getBytes
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
		if ("matches".equals(name))
			return valueOfBoolean(receiver.matches(stringOfValue(args[0])), target);
		// contains
		if ("contains".equals(name)) {
			if (isNullOrString(args[0]))
				return valueOfBoolean(receiver.contains(stringOfValue(args[0])), target);
			else
				return null;
		}
		if ("replaceFirst".equals(name))
			return valueOfString(receiver.replaceFirst(stringOfValue(args[0]), stringOfValue(args[1])), target);
		if ("replaceAll".equals(name))
			return valueOfString(receiver.replaceAll(stringOfValue(args[0]), stringOfValue(args[1])), target);
		if ("replace".equals(name) && "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;".equals(sig)) {
			if (isNullOrString(args[0]) && isNullOrString(args[1]))
				return valueOfString(receiver.replace(stringOfValue(args[0]), stringOfValue(args[1])), target);
			else
				return null;
		}
		// replace, split, toLowerCase
		if ("toLowerCase".equals(name) && "()Ljava/lang/String;".equals(sig))
			return valueOfString(receiver.toLowerCase(), target);
		// toUpperCase
		if ("toUpperCase".equals(name) && "()Ljava/lang/String;".equals(sig))
			return valueOfString(receiver.toUpperCase(), target);
		if ("trim".equals(name))
			return valueOfString(receiver.trim(), target);
		if ("toString".equals(name))
			return receiverValue;
		// toCharArray, format, valueOf, copyValueOf
		if ("valueOf".equals(name) && "(Ljava/lang/Object;)Ljava/lang/String;".equals(sig)) {
			if (args[0].isNull())
				return valueOfString("null", target);
			else if (isNonNullString(args[0]))
				return args[0];
			else
				return null;
		}
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
		public byte[] getBytes(java.lang.String)   throws java.io.UnsupportedEncodingException;
		  Signature: (Ljava/lang/String;)[B
		public byte[] getBytes(java.nio.charset.Charset);
		  Signature: (Ljava/nio/charset/Charset;)[B
		public byte[] getBytes();
		  Signature: ()[B
		  
		public boolean contentEquals(java.lang.StringBuffer);
		  Signature: (Ljava/lang/StringBuffer;)Z
		public boolean contentEquals(java.lang.CharSequence);
		  Signature: (Ljava/lang/CharSequence;)Z
		  
		public boolean contains(java.lang.CharSequence);
		  Signature: (Ljava/lang/CharSequence;)Z
		  
		public java.lang.String replace(java.lang.CharSequence, java.lang.CharSequence);
		  Signature: (Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
		public java.lang.String[] split(java.lang.String, int);
		  Signature: (Ljava/lang/String;I)[Ljava/lang/String;
		public java.lang.String[] split(java.lang.String);
		  Signature: (Ljava/lang/String;)[Ljava/lang/String;
		public java.lang.String toLowerCase(java.util.Locale);
		  Signature: (Ljava/util/Locale;)Ljava/lang/String;
		  
		public java.lang.String toUpperCase(java.util.Locale);
		  Signature: (Ljava/util/Locale;)Ljava/lang/String;
		  
		public char[] toCharArray();
		  Signature: ()[C
		public static java.lang.String format(java.lang.String, java.lang.Object[]);
		  Signature: (Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;
		public static java.lang.String format(java.util.Locale, java.lang.String, java.lang.Object[]);
		  Signature: (Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;
		public static java.lang.String valueOf(java.lang.Object);
		  Signature: (Ljava/lang/Object;)Ljava/lang/String;
		public static java.lang.String valueOf(char[]);
		  Signature: ([C)Ljava/lang/String;
		public static java.lang.String valueOf(char[], int, int);
		  Signature: ([CII)Ljava/lang/String;
		public static java.lang.String copyValueOf(char[], int, int);
		  Signature: ([CII)Ljava/lang/String;
		public static java.lang.String copyValueOf(char[]);
		  Signature: ([C)Ljava/lang/String;
		  
		public native java.lang.String intern();
		  Signature: ()Ljava/lang/String;
		public int compareTo(java.lang.Object);
		  Signature: (Ljava/lang/Object;)I
	 */
	
	// Check type
	
	private static boolean isNonNullString(IJavaValue value) throws DebugException {
		return "Ljava/lang/String;".equals(value.getSignature());
	}
	
	private static boolean isNullOrString(IJavaValue value) throws DebugException {
		return value.isNull() || "Ljava/lang/String;".equals(value.getSignature());
	}
	
	// Convert IJavaValue to actual values
	
	private static String stringOfValue(IJavaValue value) throws DebugException {
		assert isNullOrString(value);
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
	
	// Convert actual values to IJavaValues
	
	private static IJavaValue valueOfString(String s, IJavaDebugTarget target) {
		return target.newValue(s);
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

}
