package codehint.exprgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import codehint.ast.ArrayAccess;
import codehint.ast.BooleanLiteral;
import codehint.ast.CastExpression;
import codehint.ast.ClassInstanceCreation;
import codehint.ast.Expression;
import codehint.ast.FieldAccess;
import codehint.ast.InfixExpression;
import codehint.ast.MethodInvocation;
import codehint.ast.Name;
import codehint.ast.NullLiteral;
import codehint.ast.NumberLiteral;
import codehint.ast.ParenthesizedExpression;
import codehint.ast.PrefixExpression;
import codehint.ast.ThisExpression;
import codehint.ast.TypeLiteral;

import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaInterfaceType;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
import org.eclipse.jdt.internal.debug.core.model.JDIType;

import codehint.dialogs.SynthesisDialog;
import codehint.effects.Effect;
import codehint.effects.SideEffectHandler;
import codehint.expreval.EvaluationManager;
import codehint.expreval.StaticEvaluator;
import codehint.exprgen.precondition.Arg;
import codehint.exprgen.precondition.Const;
import codehint.exprgen.precondition.GE;
import codehint.exprgen.precondition.In;
import codehint.exprgen.precondition.Len;
import codehint.exprgen.precondition.Minus;
import codehint.exprgen.precondition.NonNull;
import codehint.exprgen.precondition.Plus;
import codehint.exprgen.precondition.Predicate;
import codehint.exprgen.typeconstraint.DesiredType;
import codehint.exprgen.typeconstraint.SupertypeBound;
import codehint.exprgen.typeconstraint.TypeConstraint;
import codehint.property.Property;
import codehint.utils.EclipseUtils;
import codehint.utils.Utils;

import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;


/**
 * Class for generating expressions that can evaluation to a given value.
 */
public abstract class ExpressionGenerator {
	
	protected final static InfixExpression.Operator[] INT_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS, InfixExpression.Operator.LESS, InfixExpression.Operator.LESS_EQUALS, InfixExpression.Operator.GREATER, InfixExpression.Operator.GREATER_EQUALS };
	protected final static InfixExpression.Operator[] BOOLEAN_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.CONDITIONAL_AND, InfixExpression.Operator.CONDITIONAL_OR  };
	protected final static InfixExpression.Operator[] REF_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS };

	protected final static Set<String> classBlacklist = new HashSet<String>();
	private final static Map<String, Set<String>> methodBlacklist = new HashMap<String, Set<String>>();
	private final static Map<String, Predicate[]> methodPreconditions = new HashMap<String, Predicate[]>();

	public static void init() {
		initBlacklist();
		initMethodPreconditions();
	}

	public static void clear() {
		classBlacklist.clear();
		methodBlacklist.clear();
		methodPreconditions.clear();
	}

	/**
	 * Initializes the blacklist.
	 */
	private static void initBlacklist() {
		classBlacklist.add("codehint.CodeHint");
		methodBlacklist.put("java.io.File", new HashSet<String>(Arrays.asList("createNewFile ()Z", "delete ()Z", "mkdir ()Z", "mkdirs ()Z", "renameTo (Ljava/io/File;)Z", "setLastModified (J)Z", "setReadOnly ()Z", "setExecutable (ZZ)Z", "setExecutable (Z)Z", "setReadable (ZZ)Z", "setReadable (Z)Z", "setWritable (ZZ)Z", "setWritable (Z)Z", "canWrite ()Z", "canExecute ()Z", "createTempFile (Ljava/lang/String;Ljava/lang/String;Ljava/io/File;)Ljava/io/File;")));
		methodBlacklist.put("java.util.Arrays", new HashSet<String>(Arrays.asList("deepHashCode ([Ljava/lang/Object;)I")));
		methodBlacklist.put("java.util.List", new HashSet<String>(Arrays.asList("add (Ljava/lang/Object;)Z", "addAll (Ljava/util/Collection;)Z", "addAll (ILjava/util/Collection;)Z", "remove (Ljava/lang/Object;)Z", "remove (I)Ljava/lang/Object;", "removeAll (Ljava/util/Collection;)Z", "retainAll (Ljava/util/Collection;)Z", "set (ILjava/lang/Object;)Ljava/lang/Object;", "toArray ([Ljava/lang/Object;)[Ljava/lang/Object;")));
		methodBlacklist.put("java.util.ArrayList", new HashSet<String>(Arrays.asList("add (Ljava/lang/Object;)Z", "addAll (Ljava/util/Collection;)Z", "addAll (ILjava/util/Collection;)Z", "remove (Ljava/lang/Object;)Z", "remove (I)Ljava/lang/Object;", "removeAll (Ljava/util/Collection;)Z", "removeRange (II)V", "retainAll (Ljava/util/Collection;)Z", "set (ILjava/lang/Object;)Ljava/lang/Object;", "toArray ([Ljava/lang/Object;)[Ljava/lang/Object;")));
		methodBlacklist.put("java.util.Map", new HashSet<String>(Arrays.asList("put (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", "remove (Ljava/lang/Object;)Ljava/lang/Object;")));
		methodBlacklist.put("java.util.HashMap", new HashSet<String>(Arrays.asList("put (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", "remove (Ljava/lang/Object;)Ljava/lang/Object;")));
		methodBlacklist.put("java.util.TreeMap", new HashSet<String>(Arrays.asList("put (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", "remove (Ljava/lang/Object;)Ljava/lang/Object;")));
		methodBlacklist.put("java.util.Set", new HashSet<String>(Arrays.asList("add (Ljava/lang/Object;)Z", "addAll (Ljava/util/Collection;)Z", "remove (Ljava/lang/Object;)Z", "removeAll (Ljava/util/Collection;)Z", "retainAll (Ljava/util/Collection;)Z", "toArray ([Ljava/lang/Object;)[Ljava/lang/Object;")));
		methodBlacklist.put("java.util.HashSet", new HashSet<String>(Arrays.asList("add (Ljava/lang/Object;)Z", "addAll (Ljava/util/Collection;)Z", "remove (Ljava/lang/Object;)Z", "removeAll (Ljava/util/Collection;)Z", "retainAll (Ljava/util/Collection;)Z", "toArray ([Ljava/lang/Object;)[Ljava/lang/Object;")));
		methodBlacklist.put("java.util.TreeSet", new HashSet<String>(Arrays.asList("add (Ljava/lang/Object;)Z", "addAll (Ljava/util/Collection;)Z", "remove (Ljava/lang/Object;)Z", "removeAll (Ljava/util/Collection;)Z", "retainAll (Ljava/util/Collection;)Z", "toArray ([Ljava/lang/Object;)[Ljava/lang/Object;")));
		methodBlacklist.put("java.util.Collection", new HashSet<String>(Arrays.asList("add (Ljava/lang/Object;)Z", "addAll (Ljava/util/Collection;)Z", "remove (Ljava/lang/Object;)Z", "removeAll (Ljava/util/Collection;)Z", "retainAll (Ljava/util/Collection;)Z", "toArray ([Ljava/lang/Object;)[Ljava/lang/Object;")));
		methodBlacklist.put("org.eclipse.swt.widgets.Shell", new HashSet<String>(Arrays.asList("internal_new (Lorg/eclipse/swt/widgets/Display;J)Lorg/eclipse/swt/widgets/Shell;")));  // Calling this method actually crashes the JVM on my machine.
	}
	
	private static void initMethodPreconditions() {
		methodPreconditions.put("java.lang.String <init> (Ljava/lang/String;)V", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String <init> ([C)V", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String <init> ([CII)V", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Arg(3), new Const(0)), new GE(new Len(1), new Plus(new Arg(2), new Arg(3))) });
		methodPreconditions.put("java.lang.String <init> ([III)V", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Arg(3), new Const(0)), new GE(new Len(1), new Plus(new Arg(2), new Arg(3))) });
		methodPreconditions.put("java.lang.String <init> ([BIII)V", new Predicate[] { new NonNull(1), new GE(new Arg(3), new Const(0)), new GE(new Arg(4), new Const(0)), new GE(new Len(1), new Plus(new Arg(3), new Arg(4))) });
		methodPreconditions.put("java.lang.String <init> ([BI)V", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String <init> ([BIILjava/lang/String;)V", new Predicate[] { new NonNull(1), new NonNull(4), new GE(new Arg(2), new Const(0)), new GE(new Arg(3), new Const(0)), new GE(new Len(1), new Plus(new Arg(2), new Arg(3))) });
		methodPreconditions.put("java.lang.String <init> ([BIILjava/nio/charset/Charset;)V", new Predicate[] { new NonNull(1), new NonNull(4), new GE(new Arg(2), new Const(0)), new GE(new Arg(3), new Const(0)), new GE(new Len(1), new Plus(new Arg(2), new Arg(3))) });
		methodPreconditions.put("java.lang.String <init> ([BLjava/lang/String;)V", new Predicate[] { new NonNull(1), new NonNull(2) });
		methodPreconditions.put("java.lang.String <init> ([BLjava/nio/charset/Charset;)V", new Predicate[] { new NonNull(1), new NonNull(2) });
		methodPreconditions.put("java.lang.String <init> ([BII)V", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Arg(3), new Const(0)), new GE(new Len(1), new Plus(new Arg(2), new Arg(3))) });
		methodPreconditions.put("java.lang.String <init> ([B)V", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String <init> (Ljava/lang/StringBuffer;)V", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String <init> (Ljava/lang/StringBuilder;)V", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String charAt (I)C", new Predicate[] { new In(new Arg(1), 0) });
		methodPreconditions.put("java.lang.String codePointAt (I)I", new Predicate[] { new In(new Arg(1), 0) });
		methodPreconditions.put("java.lang.String codePointBefore (I)I", new Predicate[] { new In(new Minus(new Arg(1), new Const(1)), 0) });
		methodPreconditions.put("java.lang.String codePointCount (II)I", new Predicate[] { new GE(new Arg(1), new Const(0)), new GE(new Len(0), new Arg(2)), new GE(new Arg(2), new Arg(1)) });
		methodPreconditions.put("java.lang.String offsetByCodePoints (II)I", new Predicate[] { new In(new Arg(1), 0) });  // TODO: Missing a constraint
		methodPreconditions.put("java.lang.String getBytes (Ljava/lang/String;)[B", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String getBytes (Ljava/nio/charset/Charset;)[B", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String contentEquals (Ljava/lang/StringBuffer;)Z", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String contentEquals (Ljava/lang/CharSequence;)Z", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String compareTo (Ljava/lang/String;)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String compareToIgnoreCase (Ljava/lang/String;)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String regionMatches (ILjava/lang/String;II)Z", new Predicate[] { new NonNull(2) });
		methodPreconditions.put("java.lang.String regionMatches (ZILjava/lang/String;II)Z", new Predicate[] { new NonNull(3) });
		methodPreconditions.put("java.lang.String startsWith (Ljava/lang/String;I)Z", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String startsWith (Ljava/lang/String;)Z", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String endsWith (Ljava/lang/String;)Z", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String indexOf (I)I", new Predicate[] { });
		methodPreconditions.put("java.lang.String indexOf (II)I", new Predicate[] { });
		methodPreconditions.put("java.lang.String lastIndexOf (I)I", new Predicate[] { });
		methodPreconditions.put("java.lang.String lastIndexOf (II)I", new Predicate[] { });
		methodPreconditions.put("java.lang.String indexOf (Ljava/lang/String;)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String indexOf (Ljava/lang/String;I)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String indexOf ([CII[CIII)I", new Predicate[] { new NonNull(1), new NonNull(4) });
		methodPreconditions.put("java.lang.String lastIndexOf (Ljava/lang/String;)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String lastIndexOf (Ljava/lang/String;I)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String lastIndexOf ([CII[CIII)I", new Predicate[] { new NonNull(1), new NonNull(4) });
		methodPreconditions.put("java.lang.String substring (I)Ljava/lang/String;", new Predicate[] { new In(new Arg(1), 0) });
		methodPreconditions.put("java.lang.String substring (II)Ljava/lang/String;", new Predicate[] { new GE(new Arg(1), new Const(0)), new GE(new Len(0), new Arg(2)), new GE(new Arg(2), new Arg(1)) });
		methodPreconditions.put("java.lang.String subSequence (II)Ljava/lang/CharSequence;", new Predicate[] { new GE(new Arg(1), new Const(0)), new GE(new Len(0), new Arg(2)), new GE(new Arg(2), new Arg(1)) });
		methodPreconditions.put("java.lang.String concat (Ljava/lang/String;)Ljava/lang/String;", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String matches (Ljava/lang/String;)Z", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String contains (Ljava/lang/CharSequence;)Z", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String replaceFirst (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", new Predicate[] { new NonNull(1), new NonNull(2) });
		methodPreconditions.put("java.lang.String replaceAll (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", new Predicate[] { new NonNull(1), new NonNull(2) });
		methodPreconditions.put("java.lang.String replace (Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;", new Predicate[] { new NonNull(1), new NonNull(2) });
		methodPreconditions.put("java.lang.String split (Ljava/lang/String;I)[Ljava/lang/String;", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String split (Ljava/lang/String;)[Ljava/lang/String;", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String toLowerCase (Ljava/util/Locale;)Ljava/lang/String;", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String toUpperCase (Ljava/util/Locale;)Ljava/lang/String;", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String format (Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", new Predicate[] { new NonNull(1), new NonNull(2) });
		methodPreconditions.put("java.lang.String format (Ljava/util/Locale;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", new Predicate[] { new NonNull(2), new NonNull(3) });
		methodPreconditions.put("java.lang.String format (Ljava/lang/Object;)Ljava/lang/String;", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String valueOf ([C)Ljava/lang/String;", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String valueOf ([CII)Ljava/lang/String;", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Arg(3), new Const(0)), new GE(new Len(1), new Plus(new Arg(2), new Arg(3))) });
		methodPreconditions.put("java.lang.String copyValueOf ([CII)Ljava/lang/String;", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Arg(3), new Const(0)), new GE(new Len(1), new Plus(new Arg(2), new Arg(3))) });
		methodPreconditions.put("java.lang.String copyValueOf ([C)Ljava/lang/String;", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.lang.String compareTo (Ljava/lang/Object;)I", new Predicate[] { new NonNull(1) });

		methodPreconditions.put("java.util.Arrays binarySearch ([JJ)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.util.Arrays binarySearch ([JIIJ)I", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Len(1), new Arg(3)), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays binarySearch ([II)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.util.Arrays binarySearch ([IIII)I", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Len(1), new Arg(3)), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays binarySearch ([SS)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.util.Arrays binarySearch ([SIIS)I", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Len(1), new Arg(3)), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays binarySearch ([CC)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.util.Arrays binarySearch ([CIIC)I", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Len(1), new Arg(3)), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays binarySearch ([BB)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.util.Arrays binarySearch ([BIIB)I", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Len(1), new Arg(3)), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays binarySearch ([DD)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.util.Arrays binarySearch ([DIID)I", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Len(1), new Arg(3)), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays binarySearch ([FF)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.util.Arrays binarySearch ([FIIF)I", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Len(1), new Arg(3)), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays binarySearch ([Ljava/lang/Object;Ljava/lang/Object;)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.util.Arrays binarySearch ([Ljava/lang/Object;IILjava/lang/Object;)I", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Len(1), new Arg(3)), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays binarySearch ([Ljava/lang/Object;Ljava/lang/Object;Ljava/util/Comparator;)I", new Predicate[] { new NonNull(1) });
		methodPreconditions.put("java.util.Arrays binarySearch ([Ljava/lang/Object;IILjava/lang/Object;Ljava/util/Comparator;)I", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)), new GE(new Len(1), new Arg(3)), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays copyOf ([Ljava/lang/Object;I)[Ljava/lang/Object;", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)) });
		methodPreconditions.put("java.util.Arrays copyOf ([Ljava/lang/Object;ILjava/lang/Class;)[Ljava/lang/Object;", new Predicate[] { new NonNull(1), new NonNull(3), new GE(new Arg(2), new Const(0)) });
		methodPreconditions.put("java.util.Arrays copyOf ([BI)[B", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)) });
		methodPreconditions.put("java.util.Arrays copyOf ([SI)[S", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)) });
		methodPreconditions.put("java.util.Arrays copyOf ([II)[I", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)) });
		methodPreconditions.put("java.util.Arrays copyOf ([JI)[J", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)) });
		methodPreconditions.put("java.util.Arrays copyOf ([CI)[C", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)) });
		methodPreconditions.put("java.util.Arrays copyOf ([FI)[F", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)) });
		methodPreconditions.put("java.util.Arrays copyOf ([DI)[D", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)) });
		methodPreconditions.put("java.util.Arrays copyOf ([ZI)[Z", new Predicate[] { new NonNull(1), new GE(new Arg(2), new Const(0)) });
		methodPreconditions.put("java.util.Arrays copyOfRange ([Ljava/lang/Object;II)[Ljava/lang/Object;", new Predicate[] { new NonNull(1), new In(new Arg(2), 1), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays copyOfRange ([Ljava/lang/Object;IILjava/lang/Class;)[Ljava/lang/Object;", new Predicate[] { new NonNull(1), new NonNull(4), new In(new Arg(2), 1), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays copyOfRange ([BII)[B", new Predicate[] { new NonNull(1), new In(new Arg(2), 1), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays copyOfRange ([SII)[S", new Predicate[] { new NonNull(1), new In(new Arg(2), 1), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays copyOfRange ([III)[I", new Predicate[] { new NonNull(1), new In(new Arg(2), 1), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays copyOfRange ([JII)[J", new Predicate[] { new NonNull(1), new In(new Arg(2), 1), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays copyOfRange ([CII)[C", new Predicate[] { new NonNull(1), new In(new Arg(2), 1), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays copyOfRange ([FII)[F", new Predicate[] { new NonNull(1), new In(new Arg(2), 1), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays copyOfRange ([DII)[D", new Predicate[] { new NonNull(1), new In(new Arg(2), 1), new GE(new Arg(3), new Arg(2)) });
		methodPreconditions.put("java.util.Arrays copyOfRange ([ZII)[Z", new Predicate[] { new NonNull(1), new In(new Arg(2), 1), new GE(new Arg(3), new Arg(2)) });
	}
	
	protected final IJavaDebugTarget target;
	protected final IJavaStackFrame stack;
	protected final IJavaThread thread;
    protected final SideEffectHandler sideEffectHandler;
	protected final ExpressionMaker expressionMaker;
	protected final ExpressionEvaluator expressionEvaluator;
	protected final SubtypeChecker subtypeChecker;
	protected final TypeCache typeCache;
	protected final EvaluationManager evalManager;
    protected final StaticEvaluator staticEvaluator;
    protected final Weights weights;
	protected final IJavaReferenceType thisType;
	protected final IJavaProject project;
	protected final IJavaType intType;
	protected final IJavaType booleanType;
	protected final IJavaValue oneValue;
	protected final Expression one;
	
	protected Map<Set<Effect>, Map<Result, ArrayList<Expression>>> equivalences;
	protected IImportDeclaration[] imports;
	protected Set<String> importsSet;
	
	public ExpressionGenerator(IJavaDebugTarget target, IJavaStackFrame stack, SideEffectHandler sideEffectHandler, ExpressionMaker expressionMaker, ExpressionEvaluator expressionEvaluator, SubtypeChecker subtypeChecker, TypeCache typeCache, EvaluationManager evalManager, StaticEvaluator staticEvaluator, Weights weights) {
		this.target = target;
		this.stack = stack;
		this.thread = (IJavaThread)stack.getThread();
		this.sideEffectHandler = sideEffectHandler;
		this.expressionMaker = expressionMaker;
		this.expressionEvaluator = expressionEvaluator;
		this.subtypeChecker = subtypeChecker;
		this.typeCache = typeCache;
		this.evalManager = evalManager;
		this.staticEvaluator = staticEvaluator;
		this.weights = weights;
		try {
			this.thisType = stack.getReferenceType();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		this.project = EclipseUtils.getProject(stack);
		this.intType = EclipseUtils.getFullyQualifiedType("int", stack, target, typeCache);
		this.booleanType = EclipseUtils.getFullyQualifiedType("boolean", stack, target, typeCache);
		this.oneValue = target.newValue(1);
		this.one = expressionMaker.makeInt(1, oneValue, intType, thread);
	}
	
	/**
	 * Generates all the expressions (up to a certain depth) whose value
	 * in the current stack frame is that of the demonstration.
	 * @param property The property entered by the user.
	 * @param typeConstraint The constraint on the type of the expressions
	 * being generated.
	 * @param varName The name of the variable being assigned.
	 * @param searchConstructors Whether or not to search constructors.
	 * @param searchOperators Whether or not to search operator expressions.
	 * @param synthesisDialog The synthesis dialog to pass the valid expressions,
	 * or null if we should not pass anything.
	 * @param monitor Progress monitor.
	 * @param maxExprDepth The maximum depth of expressions to search.
	 * @return A list containing strings of all the expressions (up
	 * to the given depth) whose result in the current stack frame satisfies
	 * the given pdspec.
	 */
	public abstract ArrayList<Expression> generateExpression(Property property, TypeConstraint typeConstraint, String varName, boolean searchConstructors, boolean searchOperators, SynthesisDialog synthesisDialog, IProgressMonitor monitor, int maxExprDepth);
	
	protected void initSearch() throws JavaModelException, DebugException {
		this.equivalences = new HashMap<Set<Effect>, Map<Result, ArrayList<Expression>>>();
		this.imports = ((ICompilationUnit)project.findElement(new Path(stack.getSourcePath()))).getImports();
		this.importsSet = new HashSet<String>(imports.length);
		for (IImportDeclaration imp : imports)
			this.importsSet.add(imp.getElementName());
	}
	
	/**
	 * Returns the number of expressions that have crashed.
	 * @return The number of expressions that have crashed.
	 */
	protected int getNumCrashes() {
		return evalManager.getNumCrashes() + staticEvaluator.getNumCrashes() + expressionEvaluator.getNumCrashes();
	}
	
	/**
	 * Gets a supertype of the expressions being generated if possible.
	 * @return A supertype of the expressions being generated, or null
	 * if we cannot uniquely determine one from the type constraint.
	 */
	protected IJavaType getVarType(TypeConstraint typeConstraint) {
		if (typeConstraint instanceof DesiredType)
			return ((DesiredType)typeConstraint).getDesiredType();
		else if (typeConstraint instanceof SupertypeBound)
			return ((SupertypeBound)typeConstraint).getSupertypeBound();
		else
			return null;
	}
	
	// TODO: Convert field/method code to use the public API?  I can use IType to get fields/methods (but they only get declared ones, so I'd have to walk the supertype chain), IType to get their signature, Signature.getSignature{Qualifier,SimpleName} to get type names, and then EclipseUtils.getType-like code to get the IType back.
	// TODO: Downcast expressions to get extra fields and array accesses.
	
	/**
	 * Gets all the visible fields of the given type.
	 * @param type The type whose fields we want to get.
	 * @return All of the visible fields of the given type.
	 */
	public static List<Field> getFields(IJavaType type) {
		if (type != null && EclipseUtils.isObject(type)) {
			List<?> untypedFields = ((ReferenceType)((JDIType)type).getUnderlyingType()).visibleFields();
			ArrayList<Field> fields = new ArrayList<Field>(untypedFields.size());
			for (Object o: untypedFields)
				fields.add((Field)o);
			return fields;
		} else
			return new ArrayList<Field>(0);
	}

	/**
	 * Checks whether the given method can legally be accessed.
	 * @param field The field to check.
	 * @param thisType The type of the this object.
	 * @return Whether the given field is legal to access.
	 */
	public static boolean isLegalField(Field field, IJavaType thisType) {
		return field.isPublic() || field.declaringType().equals(((JDIType)thisType).getUnderlyingType());
	}
	
	/**
	 * Gets all the visible methods of the given type.
	 * @param type The type whose methods we want to get.
	 * @param sideEffectHandler The side effect handler.
	 * @return All of the visible methods of the given type.
	 */
	public static List<Method> getMethods(IJavaType type, SideEffectHandler sideEffectHandler) {
		return getMethods(type, sideEffectHandler.isHandlingSideEffects());
	}
	
	/**
	 * Gets all the visible methods of the given type.
	 * @param type The type whose methods we want to get.
	 * @param ignoreMethodBlacklist Whether we should ignore
	 * blacklisted methods.  This is usually true when we are
	 * handling side effects.
	 * @return All of the visible methods of the given type.
	 */
	public static List<Method> getMethods(IJavaType type, boolean ignoreMethodBlacklist) {
		try {
			if (type != null && EclipseUtils.isObject(type)) {
				List<?> untypedMethods = ((ReferenceType)((JDIType)type).getUnderlyingType()).visibleMethods();
				ArrayList<Method> methods = new ArrayList<Method>(untypedMethods.size());
				for (Object o: untypedMethods) {
					Method method = (Method)o;
					if (ignoreMethodBlacklist || (!methodBlacklist.containsKey(type.getName()) || !methodBlacklist.get(type.getName()).contains(method.name() + " " + method.signature())))
						methods.add(method);
				}
				return methods;
			} else
				return new ArrayList<Method>(0);
		} catch (DebugException e) {
			throw new RuntimeException();
		}
	}
	
	/**
	 * Checks whether the given method can legally be called.
	 * @param method The method to check.
	 * @param thisType The type of the this object.
	 * @param isConstructor Whether we are expecting a constructor.
	 * @return Whether the given method is legal to call.
	 */
	public static boolean isLegalMethod(Method method, IJavaType thisType, boolean isConstructor) {
		return ((method.isPublic() || method.declaringType().equals(((JDIType)thisType).getUnderlyingType())) && (!method.isConstructor() || !method.isPackagePrivate()))  // Constructors are not marked as public.
				&& isConstructor == method.isConstructor() && !method.isSynthetic() && !method.isStaticInitializer() && !method.declaringType().name().equals("java.lang.Object")
				&& !"hashCode".equals(method.name()) && !"deepHashCode".equals(method.name()) && !"intern".equals(method.name()) && !"clone".equals(method.name());  // TODO: This should really be part of the blacklist.
	}
	
	/**
	 * Checks whether the given field is a useful field to
	 * search or if we should ignore it.
	 * @param field The field to check.
	 * @return Whether the given field is useful to search.
	 * @throws JavaModelException
	 */
	protected boolean isUsefulField(Field field) throws JavaModelException {
		if (!isLegalField(field, thisType) || field.isSynthetic())
			return false;
        IField ifield = EclipseUtils.getIField(field, project);
        try {
        	if (ifield != null && Flags.isDeprecated(ifield.getFlags()))
        		return false;
        } catch (JavaModelException ex) { }  // We need this for Android for some reason.
        return true;
	}

	/**
	 * Makes the field access expression.  This is slightly
	 * more complicated than it sounds because we make the
	 * this explicit when possible, in which case we represent
	 * the field access with a simple name expression.
	 * @param expr The receiver.
	 * @param field The field.
	 * @param fieldType The type of the result.
	 * @return The given field access expression.
	 * @throws DebugException
	 */
	protected Expression makeFieldAccess(Expression expr, Field field, IJavaType fieldType) throws DebugException {
		Expression receiver = expr;
		if (expr instanceof ThisExpression || expr.getStaticType().equals(thisType))
			receiver = null;  // Don't use a receiver if it is null or the this type.
		else if (field.isStatic())
			receiver = expressionMaker.makeStaticName(EclipseUtils.sanitizeTypename(getShortestTypename(field.declaringType().name())), (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(field.declaringType().name(), stack, target, typeCache), thread);
		Expression fieldExpr = receiver == null ? expressionMaker.makeFieldVar(field.name(), fieldType, expr, field, thread) : expressionMaker.makeFieldAccess(receiver, field.name(), fieldType, field, thread);
		return fieldExpr;
	}

	/**
	 * Gets the lowest-possible legal type to which we might
	 * want to downcast the given expression.
	 * @param e The expression.
	 * @param isStatic Whether the receiver is static.
	 * @return The lowest legal type to which we might want to
	 * downcast the given expression.
	 * @throws DebugException
	 * @throws JavaModelException
	 */
	protected IJavaType getActualTypeForDowncast(Expression e, boolean isStatic) throws DebugException, JavaModelException {
		IJavaValue receiverValue = expressionEvaluator.getValue(e, Collections.<Effect>emptySet());
		IJavaType curType = !isStatic && receiverValue != null && !receiverValue.isNull() ? receiverValue.getJavaType() : e.getStaticType();
		if (!(curType instanceof IJavaClassType))  // This can happen when something of static type Object is actually an array.
			return null;
		while (curType.getName().indexOf('$') != -1 && curType instanceof IJavaClassType) {  // Avoid downcasting to anonymous types.
			IType itype = project.findType(curType.getName());
			if (itype == null || !itype.isAnonymous())
				break;
			curType = ((IJavaClassType)curType).getSuperclass();
		}
		while (curType != null && !curType.equals(e.getStaticType()) && curType.getName().startsWith("sun."))
			curType = ((IJavaClassType)curType).getSuperclass();  // As a heuristic optimization, avoid downcasting to private sun. types.
		if (curType == null)
			curType = e.getStaticType();
		return curType;
	}
	
	/**
	 * Checks whether the given method is useful to search or
	 * if we should ignore it.
	 * @param method The method to search.
	 * @param receiver The receiver.
	 * @param isConstructor Whether or not we are searching for
	 * constructors.
	 * @return Whether we should search the given method.
	 * @throws DebugException
	 * @throws JavaModelException
	 */
	protected boolean isUsefulMethod(Method method, Expression receiver, boolean isConstructor) throws DebugException, JavaModelException {
		// Filter out java.lang.Object methods and fake methods like "<init>".  Note that if we don't filter out Object's methods we do getClass() and then call reflective methods, which is bad times.
		// TODO: Allow calling protected and package-private things when it's legal.
		if (!isLegalMethod(method, thisType, isConstructor) || method.equals(((JDIStackFrame)stack).getUnderlyingMethod()))  // Disable explicit recursion (that is, calling the current method), since it is definitely not yet complete.
			return false;
		if (!isConstructor && method.returnTypeName().equals("void"))
			return false;
		if (receiver.getStaticType().getName().equals("java.lang.String") && (method.name().equals("toString") || method.name().equals("subSequence")))
			return false;  // Don't call toString on Strings.  Also don't call subSequence because it's the same as substring.
        if (!(receiver.getStaticType() instanceof IJavaInterfaceType)) {  // Skip interface methods called on non-interface objects, as the object method will also be in the list.  Without this, we duplicate calls to interface methods when the static type is a non-interface.
			IJavaType declaringType = EclipseUtils.getTypeAndLoadIfNeeded(method.declaringType().name(), stack, target, typeCache);
			if (declaringType instanceof IJavaInterfaceType)
				return false;
        }
        IMethod imethod = EclipseUtils.getIMethod(method, project);
        if (imethod != null && Flags.isDeprecated(imethod.getFlags()))
        	return false;
        return true;
	}

	/**
	 * Gets the return type of the given method.
	 * This is slightly more complicated than it sounds
	 * because the API returns void for constructors when
	 * we want the type they create.
	 * @param receiver The receiver.
	 * @param method The method.
	 * @param isConstructor Whether this is a constructor.
	 * @return The return type of the given method.
	 */
	protected IJavaType getReturnType(Expression receiver, Method method, boolean isConstructor) {
		return isConstructor ? receiver.getStaticType() : EclipseUtils.getTypeAndLoadIfNeeded(method.returnTypeName(), stack, target, typeCache);
	}

	/**
	 * Gets the receiver to use for the given method call.
	 * This is more complicated than it sounds because we
	 * want to use a name if the method is static and we have
	 * to parenthesize it if it is a downcast.
	 * @param e The initial receiver.
	 * @param method The method being called.
	 * @param isSubtype Whether we have downcast the receiver.
	 * @return The receiver to use for the given method call.
	 * @throws DebugException
	 */
	protected Expression getCallReceiver(Expression e, Method method, boolean isSubtype) throws DebugException {
		Expression receiver = e;
		if (method.isStatic())
			receiver = expressionMaker.makeStaticName(EclipseUtils.sanitizeTypename(getShortestTypename(method.declaringType().name())), (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(method.declaringType().name(), stack, target, typeCache), thread);
		else if (isSubtype && ((ReferenceType)((JDIType)e.getStaticType()).getUnderlyingType()).methodsByName(method.name(), method.signature()).isEmpty()) {
			IJavaType downcastType = getDowncastTypeName(method);
			if (downcastType != null)
				receiver = expressionMaker.makeParenthesized(downcast(receiver, downcastType));
		}
		return receiver;
	}

	/**
	 * Gets the name to use for the given method call.
	 * This is slightly more complicated than it sounds because
	 * constructors have a fake name.
	 * @param receiver The receiver.
	 * @param method The method.
	 * @return The name to use for the given call.
	 * @throws DebugException
	 */
	protected String getCallName(Expression receiver, Method method) throws DebugException {
		String name = method.name();
		if (method.isConstructor())
			name = getShortestTypename(receiver.getStaticType().getName());
		return name;
	}

	/**
	 * Finds the arguments to use for the given argument of the
	 * given call by filtering and casting the given possibilities.
	 * @param possibleArgs The possible arguments.
	 * @param receiver The receiver of the call.
	 * @param method The method being called.
	 * @param overloadChecker The overload checker.
	 * @param argType The type of the argument.
	 * @param curArgIndex The index of the current argument.
	 * @param maxArgDepth The maximum depth of the arguments.
	 * @return The expressions to use for the given argument.
	 * @throws DebugException 
	 */
	protected ArrayList<Expression> getArgs(List<Expression> possibleArgs, Expression receiver, Method method, OverloadChecker overloadChecker, IJavaType argType, int curArgIndex, int maxArgDepth) throws DebugException {
		SupertypeBound argBound = new SupertypeBound(argType);
		int numArgs = method.argumentTypeNames().size();
		ArrayList<Expression> curPossibleActuals = new ArrayList<Expression>();
		// TODO (low priority): This can get called multiple times if there are multiple args with the same type (or even different methods with args of the same type), but this has a tiny effect compared to the general state space explosion problem.
		// TODO: Allow downcasting args (i.e., a's static type does not meet argBound but its dynamic type does).  This should be easy to do with isHelpfulWithDowncast() and downcast(). 
		for (Expression a : possibleArgs)
			if (argBound.isFulfilledBy(a.getStaticType(), subtypeChecker, typeCache, stack, target)  // TODO: This doesn't work for generic methods.
					&& meetsNonNullPreconditions(method, curArgIndex + 1, a)
					// Avoid calling equals with things the same element, things of incomparable type, or null.
					&& !("equals".equals(method.name()) && "(Ljava/lang/Object;)Z".equals(method.signature()) && (a == receiver || isConstant(a) || (!subtypeChecker.isSubtypeOf(a.getStaticType(), receiver.getStaticType()) && !subtypeChecker.isSubtypeOf(receiver.getStaticType(), a.getStaticType()))))) {
				if (method.name().equals("valueOf") && method.declaringType().name().equals("java.lang.String") && a.getStaticType() != null && a.getStaticType().getName().equals("java.lang.String"))
					continue;  // Don't call valueOf on a String.
				// Cast the null literal when passed as a vararg to the component type.
				// TODO: Bug: Eclipse's sendMessage interface takes in a value and so ignores this cast and calls the wrong one.  I thus do not generate such expressions.
				if (a instanceof NullLiteral && method.isVarArgs() && curArgIndex == numArgs - 1)
					continue;//a = (Expression)expressionMaker.makeCast(a, ((IJavaArrayType)argType).getComponentType(), a.getValue(), valueCache, thread);
				a = getArgExpression(a, overloadChecker, argType, curArgIndex, maxArgDepth);
				if (a != null)
					curPossibleActuals.add(a);
			}
		return curPossibleActuals;
	}

	@SuppressWarnings("unused")
	protected Expression getArgExpression(Expression a, OverloadChecker overloadChecker, IJavaType argType, int curArgIndex, int maxArgDepth) {
		return a;
	}
	
	/**
	 * Casts the given argument if it is needed to be a
	 * legal method call.
	 * @param a The argument.
	 * @param overloadChecker The overload checker, which must
	 * have been set to the method being called.
	 * @param argType The expected argument type.
	 * @param curArgIndex The index of the argument.
	 * @return The given argument, with a cast if necessary.
	 */
	protected Expression castArgIfNecessary(Expression a, OverloadChecker overloadChecker, IJavaType argType, int curArgIndex) {
		if (overloadChecker.needsCast(argType, a.getStaticType(), curArgIndex)) {  // If the method is overloaded, when executing the expression we might get "Ambiguous call" compile errors, so we put in a cast to remove the ambiguity.
			//System.out.println("Adding cast to type " + argType.toString() + " to argument " + a.toString() + " at index "+ curArgIndex + " of method " + method.declaringType() + "." + method.name() + " with " + method.argumentTypeNames().size() + " arguments.");
			a = expressionMaker.makeCast(a, argType);
		}
		return a;
	}
	
	/**
	 * Gets the type to which we want to downcast calls to
	 * the given method.  We heuristically try to choose one
	 * of the types highest up in the hierarchy.
	 * @param method The method we are calling.
	 * @return The type to which we should downcast calls to
	 * the given method, or null if we should not downcast.
	 */
	protected IJavaType getDowncastTypeName(Method method) {
		List<ReferenceType> candidates = new ArrayList<ReferenceType>();
		candidates.add(method.declaringType());
		// Do a BFS to find types highest in the hierarchy.
		while (true) {
			List<ReferenceType> newCandidates = new ArrayList<ReferenceType>();
			for (ReferenceType type: candidates) {
				if (type instanceof ClassType) {  // Add interface types first so we try to prefer them.
					ClassType classType = (ClassType)type;
					for (InterfaceType interfacetype: classType.interfaces())
						if (hasSimilarMethod(interfacetype, method))
							newCandidates.add(interfacetype);
					ClassType supertype = classType.superclass();
					if (supertype != null && hasSimilarMethod(supertype, method))
						newCandidates.add(supertype);
				} else {
					for (InterfaceType interfacetype: ((InterfaceType)type).superinterfaces())
						if (hasSimilarMethod(interfacetype, method))
							newCandidates.add(interfacetype);
				}
			}
			if (newCandidates.isEmpty())
				break;
			candidates = newCandidates;
		}
		for (ReferenceType rtype: candidates) {
			IJavaType type = EclipseUtils.getTypeAndLoadIfNeededAndExists(rtype.name(), stack, target, typeCache);
			if (type != null)  // Ensure we can load the type.
				return type;
		}
		return null;
	}
	
	/**
	 * Checks whether the given type has a public version
	 * of the given method.
	 * @param type The type to check.
	 * @param method The method we want to call.
	 * @return Whether the given type has a public version
	 * of the given method.
	 */
	private static boolean hasSimilarMethod(ReferenceType type, Method method) {
		for (Method cur: type.methodsByName(method.name(), method.signature()))
			if (cur.isPublic())  // Some supertypes might have non-public version of the method, so ignore them.
				return true;
		return false;
	}
	
	/**
	 * Determines whether an expression of the given type might be
	 * useful after a downcast (i.e., whether a value of the given
	 * type might be of some subtype that is useful).
	 * @param curType The type to test.
	 * @param typeConstraint The type constraint.
	 * @return Whether an expression of the given type might be
	 * useful after a downcast
	 */
	protected boolean mightBeHelpfulWithDowncast(IJavaType curType, TypeConstraint typeConstraint) {
		for (IJavaType constraintType: typeConstraint.getTypes(stack, target, typeCache))
			if (subtypeChecker.isSubtypeOf(constraintType, curType))
				return true;
		return false;
	}

	/**
	 * Downcasts the given expression to the given type.
	 * @param e The expression to downcast.
	 * @param typename The name of the type to which we
	 * should downcast the given expression.
	 * @return The given expression downcast to one the given type.
	 * @throws DebugException
	 */
	protected Expression downcast(Expression e, IJavaType type) throws DebugException {
		assert type != null : e.toString();
		Expression casted = expressionMaker.makeCast(e, type, getShortestTypename(type.getName()));
		expressionEvaluator.copyResults(e, casted);
		expressionEvaluator.setResultString(casted, expressionEvaluator.getResultString(((Expression)e)));
		return casted;
	}
	
	/**
	 * If we have imported a type, use its unqualified name.
	 * @param typeName The name of the type.
	 * @return The shortest legal name for the given type.
	 * @throws DebugException
	 */
	protected String getShortestTypename(String typeName) throws DebugException {
		if (importsSet.contains(typeName))
			return EclipseUtils.getUnqualifiedName(EclipseUtils.sanitizeTypename(typeName));
		else if (typeName.startsWith("java.lang.") && typeName.lastIndexOf('.') == 9)  // We don't want to shorten e.g., java.lang.reflect.Field to reflect.Field.
			return typeName.substring("java.lang.".length());
		else
			return typeName;
	}
	
	/**
	 * Checks whether the given expression is a constant.
	 * @param e An expression.
	 * @return Whether the given expression is a constant.
	 */
	protected static boolean isConstant(Expression e) {
		if (e instanceof NullLiteral || e instanceof NumberLiteral || e instanceof BooleanLiteral)
			return true;
		else if (e instanceof ParenthesizedExpression)
			return isConstant(((ParenthesizedExpression)e).getExpression());
		else
			return false;
	}
	
	/**
	 * Ensures that the call to the given method with the given
	 * argument at the given index meets any known non-null
	 * preconditions.
	 * Note that this is simply a short-circuit check; the full
	 * check is done in meetsPreconditions.
	 * @param method The method being called.
	 * @param argIndex The index of the current argument, where
	 * 1 is the first argument, not 0.
	 * @param arg The given argument.
	 * @return Ensures that the given arg is non-null if we know
	 * that it must be.
	 */
	protected boolean meetsNonNullPreconditions(Method method, int argIndex, Expression arg) {
		String methodId = method.declaringType().name() + " " + method.name() + " " + method.signature();
		if (methodPreconditions.containsKey(methodId))
			for (Predicate precondition: methodPreconditions.get(methodId))
				if (precondition instanceof NonNull)
					if (((NonNull)precondition).getArgIndex() == argIndex && expressionEvaluator.getValue(arg, Collections.<Effect>emptySet()).isNull())
						return false;
		return true;
	}
	
	/**
	 * Ensures that the given call meets its known preconditions.
	 * @param method The method being called.
	 * @param receiver The receiver.
	 * @param actuals The actuals.
	 * @return Whether or not the given call meets its known preconditions.
	 */
	protected boolean meetsPreconditions(Method method, Expression receiver, ArrayList<Expression> actuals) {
		String methodId = method.declaringType().name() + " " + method.name() + " " + method.signature();
		if (methodPreconditions.containsKey(methodId))
			for (Predicate precondition: methodPreconditions.get(methodId))
				if (!precondition.satisfies(receiver, actuals, expressionEvaluator))
					return false;
		return true;
	}

	/**
	 * Creates a call (method or constructor) during equivalence expansion.
	 * @param method The method being called.
	 * @param name The method name.
	 * @param returnType The return type of the method.
	 * @param receiver The receiving object for method calls or a type
	 * literal representing the type being created for creations.
	 * @param curActuals The current list of actuals, which is built
	 * up through recursion.
	 * @param effects The current side effects.
	 * @param result The result of all these calls.
	 * @return A call with the given information.
	 */
	protected Expression makeEquivalenceCall(Method method, String name, IJavaType returnType, Expression receiver, ArrayList<Expression> curActuals, Set<Effect> effects, Result result) {
		if ("<init>".equals(name))
			return expressionMaker.makeClassInstanceCreation(((TypeLiteral)receiver).getType(), curActuals, method, effects, result);
		else
			return expressionMaker.makeCall(name, receiver, curActuals, method, returnType, effects, result);
	}
	
	void addEquivalentExpression(Expression e, Set<Effect> curEffects) {
		Map<Result, ArrayList<Expression>> curEquivalences = equivalences.get(curEffects);
		if (curEquivalences == null) {
			curEquivalences = new HashMap<Result, ArrayList<Expression>>();
			equivalences.put(curEffects, curEquivalences);
		}
		Utils.addToListMap(curEquivalences, expressionEvaluator.getResult(e, curEffects), e);
	}

	/**
	 * Gets the type of the given expression to use for expanding equivalences.
	 * @param expr The expression.
	 * @param result The result of the expression.
	 * @return The type of the given expression to use for expanding equivalences.
	 * @throws DebugException
	 */
	protected IJavaType getEquivalenceType(Expression expr, Result result) throws DebugException {
		IJavaType type = expressionEvaluator.isStatic(expr) ? expressionEvaluator.getStaticType(expr) : result.getValue().getValue().getJavaType();
		if (expressionEvaluator.getMethod(expr) != null) {  // If this is a call, use the declared type of the method, not the dynamic type of the value.
			Method method = expressionEvaluator.getMethod(expr);
			type = EclipseUtils.getFullyQualifiedType(method.isConstructor() ? method.declaringType().name() : expressionEvaluator.getMethod(expr).returnTypeName(), stack, target, typeCache);
		}
		return type;
	}
	
	/**
	 * Checks whether the given type satisfies the given constraint
	 * using the specified cache as a shortcut.
	 * @param type The type.
	 * @param constraint The constraint.
	 * @param fulfillingType A cache of types that fulfill the constraint.
	 * @return Whether the given type satisfies the given constraint.
	 * @throws DebugException
	 */
	protected boolean isValidType(IJavaType type, TypeConstraint constraint, Set<String> fulfillingType) throws DebugException {
		String typeName = type == null ? null : type.getName();
		if (fulfillingType.contains(typeName)) {
			return true;  // Cache the fulfilling types, since there can be a ton of equivalent expressions at higher depths and computing isFulfilledBy can take a lot of time.
		} else if (constraint.isFulfilledBy(type, subtypeChecker, typeCache, stack, target)) {
			fulfillingType.add(typeName);
			return true;
		} else
			return false;
	}
	
	/**
	 * Ensures that the given infix operation is useful with respect
	 * to our heuristics that remove uninteresting expressions like
	 * x+0 or x+x.
	 * @param l The left side.
	 * @param op The operation.
	 * @param r The right side.
	 * @return Whether or not the given infix expression is useful.
	 * @throws DebugException
	 */
	protected boolean isUsefulInfix(Expression l, InfixExpression.Operator op, Expression r) throws DebugException {
		//IJavaValue rValue = expressionEvaluator.getValue(r, Collections.<Effect>emptySet());
		if (op == InfixExpression.Operator.PLUS)
			return !isConstant(l) && (r == one || (mightNotCommute(l, r) || l.toString().compareTo(r.toString()) < 0));
		else if (op == InfixExpression.Operator.TIMES)
			return !isConstant(l) && (/*r == two || */(mightNotCommute(l, r) || l.toString().compareTo(r.toString()) <= 0));
		else if (op == InfixExpression.Operator.MINUS)
			return !isConstant(l) && (r == one || (mightNotCommute(l, r) || l.toString().compareTo(r.toString()) != 0)) && !(r instanceof PrefixExpression && ((PrefixExpression)r).getOperator() == PrefixExpression.Operator.MINUS);
		else if (op == InfixExpression.Operator.DIVIDE)
			return !isConstant(l) && (/*r == two || */(mightNotCommute(l, r) || l.toString().compareTo(r.toString()) != 0));
		else if (EclipseUtils.isInt(l.getStaticType()) && EclipseUtils.isInt(r.getStaticType()))
			return l.toString().compareTo(r.toString()) < 0 && (!(l instanceof PrefixExpression) || !(r instanceof PrefixExpression));
		else
			return l.toString().compareTo(r.toString()) < 0;
	}
	
	/**
	 * Checks whether or not two expressions might not commute.
	 * This will only be the case if we are handling side effects
	 * and at least one expression might have side effects.
	 * TODO: This should really check if the expressions contain call,
	 * e.g., foo().x + bar().y should return true.
	 * @param l An operand.
	 * @param r An operand.
	 * @return
	 */
	protected boolean mightNotCommute(Expression l, Expression r) {
		return sideEffectHandler.isHandlingSideEffects() && (l instanceof MethodInvocation || r instanceof MethodInvocation);
	}

	/**
	 * Gets the type of the receiver.
	 * @param expression The expression representing the receiver.
	 * @param method The method being called.
	 * @param curEffects The current effects.
	 * @return The type of the receiver.
	 * @throws DebugException
	 */
	protected IJavaType getReceiverType(Expression expression, Method method, Set<Effect> curEffects) throws DebugException {
		IJavaType receiverType = EclipseUtils.getTypeAndLoadIfNeeded(method.declaringType().name(), stack, target, typeCache);
		if (receiverType == null)  // The above line will fail for anonymous classes.
			receiverType = (expression == null ? stack.getThis() : expressionEvaluator.getValue(expression, curEffects)).getJavaType();
		return receiverType;
	}

	/**
	 * Finds the arguments to use for the given argument of the
	 * given call by casting the given possibilities.  This should
	 * be used during equivalence expansion.
	 * @param possibleArgs The possible arguments.
	 * @param argIndex The index of the current arguments.
	 * @param argType The type of the argument.
	 * @param method The method being called.
	 * @param overloadChecker The overload checker.
	 * @return The expressions to use for the given argument.
	 * @throws DebugException 
	 */
	protected ArrayList<Expression> getExpansionArgs(List<Expression> possibleArgs, int argIndex, IJavaType argType, Method method, OverloadChecker overloadChecker) {
		ArrayList<Expression> allCurArgPossibilities = new ArrayList<Expression>();
		for (Expression arg: possibleArgs) {
			if (overloadChecker.needsCast(argType, arg.getStaticType(), argIndex))  // If the method is overloaded, when executing the expression we might get "Ambiguous call" compile errors, so we put in a cast to remove the ambiguity.
				arg = expressionMaker.makeCast(arg, argType);
			if (arg instanceof NullLiteral && method.isVarArgs() && argIndex == method.argumentTypeNames().size() - 1)
				continue;//arg = expressionMaker.makeCast(arg, ((IJavaArrayType)argType).getComponentType(), arg.getValue(), valueCache, thread);
			allCurArgPossibilities.add(arg);
		}
		return allCurArgPossibilities;
	}

    /**
     * Gets the depth of the given expression.
     * This should not be called by clients; please use
     * getDepth instead.
     * @param expr The expression whose depth we want.
     * @return The depth of the given expression.
     */
    protected int getDepth(Expression expr) {
    	if (expr == null)
    		return 0;
    	Object depthOpt = expressionEvaluator.getDepthOpt(expr);
    	if (depthOpt != null)
    		return ((Integer)depthOpt).intValue();
    	int depth;
    	if (expr instanceof NumberLiteral || expr instanceof BooleanLiteral || expr instanceof Name || expr instanceof ThisExpression || expr instanceof NullLiteral || expr instanceof TypeLiteral)
			depth = 0;
    	else if (expr instanceof ParenthesizedExpression)
    		depth = getDepth(((ParenthesizedExpression)expr).getExpression());
		else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			depth = Math.max(getDepth(infix.getLeftOperand()), getDepth(infix.getRightOperand())) + 1;
		} else if (expr instanceof ArrayAccess) {
			ArrayAccess array = (ArrayAccess)expr;
			depth = Math.max(getDepth(array.getArray()), getDepth(array.getIndex())) + 1;
		} else if (expr instanceof FieldAccess) {
			depth = getDepth(((FieldAccess)expr).getExpression()) + 1;
		} else if (expr instanceof PrefixExpression) {
			depth = getDepth(((PrefixExpression)expr).getOperand()) + 1;
		} else if (expr instanceof MethodInvocation) {
			MethodInvocation call = (MethodInvocation)expr;
			int maxChildDepth = call.getExpression() == null ? 0 : getDepth(call.getExpression());
			for (Expression curArg: call.arguments()) {
				int curArgDepth = getDepth(curArg);
				if (curArgDepth > maxChildDepth)
					maxChildDepth = curArgDepth;
			}
			depth = maxChildDepth + 1;
		} else if (expr instanceof ClassInstanceCreation) {
			ClassInstanceCreation call = (ClassInstanceCreation)expr;
			int maxChildDepth = call.getExpression() == null ? 0 : getDepth(call.getExpression());
			for (Expression curArg: call.arguments()) {
				int curArgDepth = getDepth(curArg);
				if (curArgDepth > maxChildDepth)
					maxChildDepth = curArgDepth;
			}
			depth = maxChildDepth + 1;
		} else if (expr instanceof CastExpression) {
			depth = getDepth(((CastExpression)expr).getExpression());
		} else
			throw new RuntimeException("Unexpected expression " + expr.toString());
    	expressionEvaluator.setDepth(expr, depth);
    	return depth;
    }

}
