package codehint.exprgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
import org.eclipse.jdt.internal.debug.core.model.JDIType;
import org.eclipse.swt.widgets.Display;

import codehint.dialogs.InitialSynthesisDialog;
import codehint.expreval.EvaluatedExpression;
import codehint.expreval.EvaluationManager;
import codehint.expreval.FullyEvaluatedExpression;
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
import codehint.exprgen.typeconstraint.FieldConstraint;
import codehint.exprgen.typeconstraint.MethodConstraint;
import codehint.exprgen.typeconstraint.SupertypeBound;
import codehint.exprgen.typeconstraint.TypeConstraint;
import codehint.exprgen.typeconstraint.UnknownConstraint;
import codehint.property.Property;
import codehint.property.ValueProperty;
import codehint.utils.EclipseUtils;
import codehint.utils.Utils;

import com.sun.jdi.Field;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.Type;


/**
 * Class for generating expressions that can evaluation to a given value.
 */
public final class ExpressionGenerator {
	
	private final static InfixExpression.Operator[] INT_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS, InfixExpression.Operator.LESS, InfixExpression.Operator.LESS_EQUALS, InfixExpression.Operator.GREATER, InfixExpression.Operator.GREATER_EQUALS };
	private final static InfixExpression.Operator[] BOOLEAN_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.CONDITIONAL_AND, InfixExpression.Operator.CONDITIONAL_OR  };
	private final static InfixExpression.Operator[] REF_COMPARE_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.EQUALS, InfixExpression.Operator.NOT_EQUALS };

	private final static Set<String> classBlacklist = new HashSet<String>();
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
		methodBlacklist.put("java.io.File", new HashSet<String>(Arrays.asList("createNewFile ()Z", "delete ()Z", "mkdir ()Z", "mkdirs ()Z", "renameTo (Ljava/io/File;)Z", "setLastModified (J)Z", "setReadOnly ()Z", "setExecutable (ZZ)Z", "setExecutable (Z)Z", "setReadable (ZZ)Z", "setReadable (Z)Z", "setWritable (ZZ)Z", "setWritable (Z)Z")));
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
	
	private final IJavaDebugTarget target;
	private final IJavaStackFrame stack;
	private final IJavaThread thread;
	private final SubtypeChecker subtypeChecker;
	private final TypeCache typeCache;
	private final ValueCache valueCache;
	private final EvaluationManager evalManager;
    private final StaticEvaluator staticEvaluator;
	private final IJavaReferenceType thisType;
	private final IJavaType intType;
	private final IJavaType booleanType;
	private final IJavaValue zero;
	private final TypedExpression one;
	private final TypedExpression two;
	// Cache the generated expressions
	//private final Map<Pair<TypeConstraint, Integer>, Pair<ArrayList<FullyEvaluatedExpression>, ArrayList<TypedExpression>>> cachedExprs;

	private TypeConstraint typeConstraint;
	private String varName;
	private Map<Value, ArrayList<EvaluatedExpression>> equivalences;
	private Map<Method, Integer> prunedDepths;  // Maps a method to the first consecutive depth at which we pruned calls to it.
	private Set<Method> newlyUnpruneds;  // A set of the methods that were pruned at the previous depth that are not pruned at the current depth.
	private IImportDeclaration[] imports;
	private Set<String> importsSet;
	private Set<String> staticAccesses;
	
	public ExpressionGenerator(IJavaDebugTarget target, IJavaStackFrame stack, SubtypeChecker subtypeChecker, TypeCache typeCache, ValueCache valueCache, EvaluationManager evalManager, StaticEvaluator staticEvaluator) {
		this.target = target;
		this.stack = stack;
		this.thread = (IJavaThread)stack.getThread();
		this.subtypeChecker = subtypeChecker;
		this.typeCache = typeCache;
		this.valueCache = valueCache;
		this.evalManager = evalManager;
		this.staticEvaluator = staticEvaluator;
		try {
			this.thisType = stack.getReferenceType();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		this.intType = EclipseUtils.getFullyQualifiedType("int", stack, target, typeCache);
		this.booleanType = EclipseUtils.getFullyQualifiedType("boolean", stack, target, typeCache);
		this.zero = target.newValue(0);
		this.one = ExpressionMaker.makeNumber("1", target.newValue(1), intType, valueCache, thread);
		this.two = ExpressionMaker.makeNumber("2", target.newValue(2), intType, valueCache, thread);
		//this.cachedExprs = new HashMap<Pair<TypeConstraint, Integer>, Pair<ArrayList<FullyEvaluatedExpression>, ArrayList<TypedExpression>>>();
	}
	
	/**
	 * Generates all the expressions (up to a certain depth) whose value
	 * in the current stack frame is that of the demonstration.
	 * @param property The property entered by the user.
	 * @param typeConstraint The constraint on the type of the expressions
	 * being generated.
	 * @param varName The name of the variable being assigned.
	 * @param synthesisDialog The synthesis dialog to pass the valid expressions,
	 * or null if we should not pass anything.
	 * @param monitor Progress monitor.
	 * @param maxExprDepth The maximum depth of expressions to search.
	 * @return A list containing strings of all the expressions (up
	 * to the given depth) whose result in the current stack frame satisfies
	 * the given pdspec.
	 */
	public ArrayList<FullyEvaluatedExpression> generateExpression(Property property, TypeConstraint typeConstraint, String varName, InitialSynthesisDialog synthesisDialog, IProgressMonitor monitor, int maxExprDepth) {
		monitor.beginTask("Expression generation and evaluation", IProgressMonitor.UNKNOWN);
		
		try {
			this.typeConstraint = typeConstraint;
			this.varName = varName;
			this.equivalences = new HashMap<Value, ArrayList<EvaluatedExpression>>();
			this.prunedDepths = new HashMap<Method, Integer>();
			this.newlyUnpruneds = new HashSet<Method>();
			this.imports = ((ICompilationUnit)EclipseUtils.getProject(stack).findElement(new Path(stack.getSourcePath()))).getImports();
			this.importsSet = new HashSet<String>(imports.length);
			for (IImportDeclaration imp : imports)
				this.importsSet.add(imp.getElementName());
			this.staticAccesses = new HashSet<String>();
			
			ArrayList<FullyEvaluatedExpression> results = genAllExprs(maxExprDepth, property, synthesisDialog, monitor);

			/*for (Map.Entry<Value, ArrayList<EvaluatedExpression>> entry : equivalences.entrySet())
				System.out.println(entry.getKey() + " -> " + entry.getValue().toString());
			int totalNumEquivExprs = 0;
			for (ArrayList<EvaluatedExpression> es: equivalences.values())
				totalNumEquivExprs += es.size();
			System.out.println("Found " + equivalences.size() + " equivalences classes that contain " + totalNumEquivExprs + " expressions.");*/
			
	    	monitor.done();
	    	return results;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Recursively generates all expressions whose value in the
	 * current stack frame is that of the demonstration.
	 * @param maxDepth The maximum depth to search (inclusive).
	 * @param property The property entered by the user.
	 * @param synthesisDialog The synthesis dialog to pass the valid expressions,
	 * or null if we should not pass anything.
	 * @param monitor Progress monitor.
	 * @return all expressions up to the given depth whose result in the
	 * current stack frame satisfies the current pdspec.
	 * @throws DebugException 
	 */
	private ArrayList<FullyEvaluatedExpression> genAllExprs(int maxDepth, Property property, final InitialSynthesisDialog synthesisDialog, IProgressMonitor monitor) throws DebugException {
		long startTime = System.currentTimeMillis();
		
		ArrayList<TypedExpression> curLevel = null;
		ArrayList<FullyEvaluatedExpression> nextLevel = new ArrayList<FullyEvaluatedExpression>(0);
		for (int depth = 0; depth <= maxDepth; depth++) {
			filterDuplicates(nextLevel);
			/*System.out.println("Depth " + depth + " has " + nextLevel.size() + " inputs:");
			for (FullyEvaluatedExpression e: nextLevel)
				System.out.println(Utils.truncate(e.toString(), 100));*/
			curLevel = genOneLevel(nextLevel, depth, maxDepth, property, monitor);
			evalManager.cacheMethodResults(nextLevel);
			if (depth < maxDepth)
				nextLevel = evaluateExpressions(curLevel, null, null, monitor, depth);
		}
		ArrayList<FullyEvaluatedExpression> results = evaluateExpressions(curLevel, property, synthesisDialog, monitor, maxDepth);
		
		//printEquivalenceInfo();
		//System.out.println("Took " + (System.currentTimeMillis() - startTime) + " milliseconds pre-expansion.");
		
		// Expand equivalences.
    	final ArrayList<FullyEvaluatedExpression> extraResults = expandEquivalences(results, monitor);
    	if (synthesisDialog != null) {
			Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
					synthesisDialog.addExpressions(extraResults);
				}
			});
    	}
		results.addAll(extraResults);
		
		EclipseUtils.log("Generated " + curLevel.size() + " expressions at depth " + maxDepth + " and found " + results.size() + " valid expressions and took " + (System.currentTimeMillis() - startTime) + " milliseconds.");
		
    	return results;
	}
	
	/**
	 * Evaluates the given expressions and returns those that
	 * do not crash and satisfy the given pdspec if it is non-null.
	 * @param exprs The expressions to evaluate.
	 * @param property The property entered by the user.
	 * @param synthesisDialog The synthesis dialog to pass the valid
	 * expressions, or null if we should not pass anything.
	 * @param monitor Progress monitor.
	 * @param depth The current depth.
	 * @return The expressions that do not crash and satisfy the
	 * given pdspec if it is non-null.
	 * @throws DebugException
	 */
	private ArrayList<FullyEvaluatedExpression> evaluateExpressions(List<TypedExpression> exprs, Property property, InitialSynthesisDialog synthesisDialog, IProgressMonitor monitor, @SuppressWarnings("unused") int depth) throws DebugException {
		ArrayList<EvaluatedExpression> evaluatedExprs = new ArrayList<EvaluatedExpression>();
		ArrayList<TypedExpression> unevaluatedExprs = new ArrayList<TypedExpression>();
		
    	for (TypedExpression e : exprs)
    		if (e.getValue() != null && e instanceof EvaluatedExpression && !"V".equals(e.getValue().getSignature()))
    			evaluatedExprs.add((EvaluatedExpression)e);
    		else
    			unevaluatedExprs.add(e);
    	
    	//System.out.println("Generated " + exprs.size() + " potential expressions at depth " + depth + ", of which " + evaluatedExprs.size() + " already have values and " + unevaluatedExprs.size() + " still need to be evaluated.");
    	//System.out.println("Generated " + (Utils.getNumValues(equivalences) + unevaluatedExprs.size() + evalManager.getNumCrashes() + staticEvaluator.getNumCrashes()) + " total expressions at depth " + depth + ", of which " + unevaluatedExprs.size() + " still need to be evaluated and " + (evalManager.getNumCrashes() + staticEvaluator.getNumCrashes()) + " crashed.");

		/*for (EvaluatedExpression e: evaluatedExprs)
			System.out.println(Utils.truncate(e.toString(), 100));*/
    	
		ArrayList<FullyEvaluatedExpression> results = evalManager.evaluateExpressions(evaluatedExprs, property, getVarType(), synthesisDialog, monitor);
    	if (unevaluatedExprs.size() > 0) {

	    	/*for (TypedExpression call: unevaluatedExprs) {
	    		System.out.println(call.getExpression());
	    		Method method = ExpressionMaker.getMethod(call.getExpression());
	    		String name = method.name();
	    		List<?> args = call.getExpression() instanceof MethodInvocation ? ((MethodInvocation)call.getExpression()).arguments() : ((ClassInstanceCreation)call.getExpression()).arguments();
				String argString = "";
				for (Object e: args) {
					if (!argString.isEmpty())
						argString += ", ";
					IJavaValue value = ExpressionMaker.getExpressionValue((Expression)e);
					argString += value != null ? value.toString().replace("\n", "\\n") : "??";
				}
				if (call.getExpression() instanceof ClassInstanceCreation)
					System.out.println("new " + ((ClassInstanceCreation)call.getExpression()).getType() + "_" + method.signature() + "(" + argString + ")");
				else if (method.isStatic())
					System.out.println(((MethodInvocation)call.getExpression()).getExpression() + "." + name + "_" + method.signature() + "(" + argString + ")");
				else {
					Expression receiver = ((MethodInvocation)call.getExpression()).getExpression();
					if (receiver == null)
						receiver = ExpressionMaker.makeThis(stack.getThis(), thisType, valueCache, thread).getExpression();
					System.out.println(ExpressionMaker.getExpressionValue(receiver) + "." + name + "_" + method.signature() + "(" + argString + ")");
				}
	    	}*/
    		
    		// We evaluate these separately because we need to set their value and add them to our equivalent expressions map; we have already done this for those whose value we already knew.
    		ArrayList<FullyEvaluatedExpression> result = evalManager.evaluateExpressions(unevaluatedExprs, property, getVarType(), synthesisDialog, monitor);
    		for (FullyEvaluatedExpression e: result) {
    			ExpressionMaker.setExpressionValue(e.getExpression(), e.getValue());
    			addEquivalentExpression(e);
    		}
    		results.addAll(result);
    	}
    	return results;
	}
	
	/**
	 * Removes expressions with duplicate values from the given list.
	 * Note that this modifies the input list.
	 * @param exprs The list of expressions.
	 */
	private static void filterDuplicates(ArrayList<FullyEvaluatedExpression> exprs) {
		Set<Value> values = new HashSet<Value>();
		Iterator<FullyEvaluatedExpression> it = exprs.iterator();
		while (it.hasNext()) {
			FullyEvaluatedExpression expr = it.next();
			if (values.contains(expr.getWrapperValue())) {
				//assert equivalences.get(expr.getWrapperValue()).contains(expr);
				it.remove();
			} else
				values.add(expr.getWrapperValue());
		}
	}
	
	/**
	 * Gets a supertype of the expressions being generated if possible.
	 * @return A supertype of the expressions being generated, or null
	 * if we cannot uniquely determine one from the type constraint.
	 */
	private IJavaType getVarType() {
		if (typeConstraint instanceof DesiredType)
			return ((DesiredType)typeConstraint).getDesiredType();
		else if (typeConstraint instanceof SupertypeBound)
			return ((SupertypeBound)typeConstraint).getSupertypeBound();
		else
			return null;
	}

	/**
	 * Generates one level of expressions at the given depth.
	 * @param nextLevel The expressions of the previous depth.
	 * @param depth The current depth we are generating.
	 * @param maxDepth The maximum depth we are generating.
	 * @param property The property entered by the user.
	 * @param monitor The progress monitor.  The caller should
	 * not allocate a new progress monitor; this method will.
	 * @return The expressions of the given depth.
	 */
	private ArrayList<TypedExpression> genOneLevel(List<FullyEvaluatedExpression> nextLevel, int depth, int maxDepth, Property property, IProgressMonitor monitor) {
		try {
			ArrayList<TypedExpression> curLevel = new ArrayList<TypedExpression>();
			IJavaType[] constraintTypes = typeConstraint.getTypes(stack, target, typeCache);
    		
    		// Get constants (but only at the top-level).
			// We add these directly to curLevel and not equivalences because we don't want to substitute them anywhere else.
			if (depth == maxDepth && property instanceof ValueProperty) {
				IJavaValue demonstration = ((ValueProperty)property).getValue();
	    		if (ExpressionMaker.isInt(demonstration.getJavaType()) && !"0".equals(demonstration.toString()))
	    			curLevel.add(ExpressionMaker.makeNumber(demonstration.toString(), target.newValue(Integer.parseInt(demonstration.toString())), intType, valueCache, thread));
	    		if (ExpressionMaker.isBoolean(demonstration.getJavaType()))
	    			curLevel.add(ExpressionMaker.makeBoolean(Boolean.parseBoolean(demonstration.toString()), target.newValue(Boolean.parseBoolean(demonstration.toString())), booleanType, valueCache, thread));
			}
    		
    		// Copy over the stuff from the next level.
    		for (TypedExpression e : nextLevel)
    			if (depth < maxDepth || isHelpfulType(e.getType(), depth, maxDepth))  // Note that this relies on the fact that something helpful for depth>=2 will be helpful for depth>=1.  If this changes, we'll need to call it again.
    				curLevel.add(e);
    		
    		// Add calls to the desired type's constructors (but only at the top-level).
    		if (depth == maxDepth)
    			for (IJavaType type: constraintTypes)
    				if (type instanceof IJavaClassType)
    					addMethodCalls(new TypedExpression(null, type), nextLevel, curLevel, depth, maxDepth);
    		
    		if (depth == 0) {
        		// Add zero and null.
    			boolean hasInt = false;
    			boolean hasObject = false;
    			for (IJavaType type: constraintTypes) {
    				if (ExpressionMaker.isInt(type))
    					hasInt = true;
    				else if (EclipseUtils.isObject(type))
    					hasObject = true;
    			}
    			if (depth < maxDepth || hasInt)
    				addUniqueExpressionToList(curLevel, ExpressionMaker.makeNumber("0", zero, intType, valueCache, thread), depth);
    			if (depth < maxDepth || (hasObject && !(typeConstraint instanceof MethodConstraint) && !(typeConstraint instanceof FieldConstraint)))  // If we have a method or field constraint, we can't have null.
    				addUniqueExpressionToList(curLevel, ExpressionMaker.makeNull(target, valueCache, thread), depth);
	    		addLocals(depth, maxDepth, curLevel);
				// Add "this" if we're not in a static context.
				if (isHelpfulType(thisType, depth, maxDepth) && !stack.isStatic())
					addUniqueExpressionToList(curLevel, ExpressionMaker.makeThis(stack.getThis(), thisType, valueCache, thread), depth);
    		} else {
    			Set<IJavaReferenceType> objectInterfaceTypes = new HashSet<IJavaReferenceType>();
    			loadTypesFromMethods(nextLevel, imports);
    			monitor = SubMonitor.convert(monitor, "Expression generation", nextLevel.size() * nextLevel.size() + nextLevel.size() + imports.length);
    			addLocals(depth, maxDepth, curLevel);
    			// Get binary ops.
    			// We use string comparisons to avoid duplicates, e.g., x+y and y+x.
    			for (TypedExpression l : nextLevel) {
    				if (monitor.isCanceled())
    					throw new OperationCanceledException();
    				for (TypedExpression r : nextLevel) {
    					// Help ensure that we generate infix operations for equivalent things (e.g., y+z if x=y=z; without this we would not generate x+x).
    					if (l == r && l.getWrapperValue() != null) {
    						ArrayList<EvaluatedExpression> lEquivs = equivalences.get(l.getWrapperValue());
    						if (lEquivs != null) {
    							for (TypedExpression equiv: lEquivs) {
    								if (equiv.getExpression() != r.getExpression() && getDepth(equiv) < depth) {  // Ensure we don't replace r with something from the current depth search.
    									r = equiv;
    									break;
    								}
    							}
    						}
    					}
    					// Arithmetic operations, e.g., +,*.
    					if (ExpressionMaker.isInt(l.getType()) && ExpressionMaker.isInt(r.getType()) && isHelpfulType(intType, depth, maxDepth)
    							&& l.getExpression().getProperty("isConstant") == null && r.getExpression().getProperty("isConstant") == null) {
    						if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0)
    							addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, InfixExpression.Operator.PLUS, r, intType, valueCache, thread), depth);
    						if (l.getExpression().toString().compareTo(r.getExpression().toString()) <= 0)
    							addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, InfixExpression.Operator.TIMES, r, intType, valueCache, thread), depth);
    						if (l.getExpression().toString().compareTo(r.getExpression().toString()) != 0)
    							addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, InfixExpression.Operator.MINUS, r, intType, valueCache, thread), depth);
    						if (l.getExpression().toString().compareTo(r.getExpression().toString()) != 0
    								&& (r.getValue() == null || !r.getValue().getValueString().equals("0")))  // Don't divide by things we know are 0.
    							addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, InfixExpression.Operator.DIVIDE, r, intType, valueCache, thread), depth);
    					}
    					// Integer comparisons, e.g., ==,<.
    					if (isHelpfulType(booleanType, depth, maxDepth) && ExpressionMaker.isInt(l.getType()) && ExpressionMaker.isInt(r.getType()))
    						if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0
    								&& (!(l.getExpression() instanceof PrefixExpression) || !(r.getExpression() instanceof PrefixExpression)))
    							for (InfixExpression.Operator op : INT_COMPARE_OPS)
    								addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, op, r, booleanType, valueCache, thread), depth);
    					// Boolean connectives, &&,||.
    					if (isHelpfulType(booleanType, depth, maxDepth) && ExpressionMaker.isBoolean(l.getType()) && ExpressionMaker.isBoolean(r.getType()))
    						if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0)
    							for (InfixExpression.Operator op : BOOLEAN_COMPARE_OPS)
    								addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, op, r, booleanType, valueCache, thread), depth);
    					// Array access, a[i].
    					if (l.getType() instanceof IJavaArrayType && ExpressionMaker.isInt(r.getType())) {
    						IJavaType elemType = ExpressionMaker.getArrayElementType(l);
    						if (elemType != null && isHelpfulType(ExpressionMaker.getArrayElementType(l), depth, maxDepth)) {
    							// Get the value if we can and skip things with null arrays or out-of-bounds indices.
    							boolean isNull = false;
    							IJavaValue value = null;
    							if (r.getValue() != null) {
    								int index = Integer.parseInt(r.getValue().getValueString());
    								if (index < 0)
    									isNull = true;
    								else if (l.getValue() != null) {
    									if (l.getValue().isNull())
    										isNull = true;
    									else {
    										IJavaArray array = (IJavaArray)l.getValue();
    										if (array.isNull() || index >= array.getLength())
    											isNull = true;
    										else
    											value = array.getValue(index);
    									}
    								}
    							}
    							if (!isNull)
    								addUniqueExpressionToList(curLevel, ExpressionMaker.makeArrayAccess(l, r, value, valueCache, thread), depth);
    						}
    					}
    					// Object/array comparisons
    					if (l.getType() instanceof IJavaReferenceType && r.getType() instanceof IJavaReferenceType
    							&& isHelpfulType(booleanType, depth, maxDepth)
    							&& (subtypeChecker.isSubtypeOf(l.getType(), r.getType()) || subtypeChecker.isSubtypeOf(r.getType(), l.getType())))
    						if (l.getExpression().toString().compareTo(r.getExpression().toString()) < 0)
    							for (InfixExpression.Operator op : REF_COMPARE_OPS)
    								addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, l, op, r, booleanType, valueCache, thread), depth);
    					monitor.worked(1);
    				}
    			}
    			// Get unary ops
    			for (TypedExpression e : nextLevel) {
    				if (monitor.isCanceled())
    					throw new OperationCanceledException();
    				// Arithmetic with constants.
    				if (ExpressionMaker.isInt(e.getType()) && isHelpfulType(intType, depth, maxDepth)
    						&& e.getExpression().getProperty("isConstant") == null) {
    					addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, e, InfixExpression.Operator.PLUS, one, intType, valueCache, thread), depth);
    					addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, e, InfixExpression.Operator.TIMES, two, intType, valueCache, thread), depth);
    					addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, e, InfixExpression.Operator.MINUS, one, intType, valueCache, thread), depth);
    					addUniqueExpressionToList(curLevel, ExpressionMaker.makeInfix(target, e, InfixExpression.Operator.DIVIDE, two, intType, valueCache, thread), depth);
    				}
    				// Field accesses to non-static fields from non-static scope.
    				if (e.getType() instanceof IJavaClassType
    						&& (e.getValue() == null || !e.getValue().isNull()))  // Skip things we know are null dereferences.
    					addFieldAccesses(e, curLevel, depth, maxDepth);
    				// Boolean negation.
    				if (ExpressionMaker.isBoolean(e.getType()) && isHelpfulType(booleanType, depth, maxDepth)
    						&& !(e.getExpression() instanceof PrefixExpression) && !(e.getExpression() instanceof InfixExpression)
    						&& e.getExpression().getProperty("isConstant") == null)  // Disallow things like !(x < y) and !(!x).
    					addUniqueExpressionToList(curLevel, ExpressionMaker.makePrefix(target, e, PrefixExpression.Operator.NOT, booleanType, valueCache, thread), depth);
    				// Integer negation.
    				if (ExpressionMaker.isInt(e.getType()) && isHelpfulType(intType, depth, maxDepth)
    						&& !(e.getExpression() instanceof PrefixExpression) && !(e.getExpression() instanceof InfixExpression)
    						&& e.getExpression().getProperty("isConstant") == null)  // Disallow things like -(-x) and -(x + y).
    					addUniqueExpressionToList(curLevel, ExpressionMaker.makePrefix(target, e, PrefixExpression.Operator.MINUS, intType, valueCache, thread), depth);
    				// Array length (which uses the field access AST).
    				if (e.getType() instanceof IJavaArrayType && isHelpfulType(intType, depth, maxDepth)
    						&& (e.getValue() == null || !e.getValue().isNull()))  // Skip things we know are null dereferences.
    					addUniqueExpressionToList(curLevel, ExpressionMaker.makeFieldAccess(e, "length", intType, e.getValue() != null ? target.newValue(((IJavaArray)e.getValue()).getLength()) : null, valueCache, thread), depth);
    				// Method calls to non-static methods from non-static scope.
    				if (ExpressionMaker.isObjectOrInterface(e.getType())
    						&& (e.getValue() == null || !e.getValue().isNull()))  // Skip things we know are null dereferences.
    					addMethodCalls(e, nextLevel, curLevel, depth, maxDepth);
    				// Collect the class and interface types we've seen.
    				if (ExpressionMaker.isObjectOrInterface(e.getType()))
    					objectInterfaceTypes.add((IJavaReferenceType)e.getType());
    				monitor.worked(1);
    			}
    			// Extra things
    			{
    				// Field accesses from static scope.
    				if (stack.isStatic() && !stack.getReceivingTypeName().contains("<"))  // TODO: Allow referring to generic classes (and below).
    					addFieldAccesses(ExpressionMaker.makeStaticName(stack.getReceivingTypeName(), thisType, valueCache, thread), curLevel, depth, maxDepth);
    				// Method calls from static scope.
    				if (stack.isStatic() && !stack.getReceivingTypeName().contains("<"))
    					addMethodCalls(ExpressionMaker.makeStaticName(stack.getReceivingTypeName(), thisType, valueCache, thread), nextLevel, curLevel, depth, maxDepth);
    				// Accesses/calls to static fields/methods.
    				for (IJavaReferenceType type : objectInterfaceTypes) {
    					String typeName = type.getName();
    					// If we have imported the type or it is an inner class of the this type, use the unqualified typename for brevity.
    					if (importsSet.contains(typeName) || (typeName.contains("$") && thisType.getName().equals(typeName.substring(0, typeName.lastIndexOf('$')))))
    						typeName = EclipseUtils.getUnqualifiedName(EclipseUtils.sanitizeTypename(typeName));
    					addFieldAccesses(ExpressionMaker.makeStaticName(typeName, type, valueCache, thread), curLevel, depth, maxDepth);
    					addMethodCalls(ExpressionMaker.makeStaticName(typeName, type, valueCache, thread), nextLevel, curLevel, depth, maxDepth);
    				}
    				// Calls to static methods and fields of imported classes.
    				for (IImportDeclaration imp : imports) {
    					String fullName = imp.getElementName();
    					String shortName = EclipseUtils.getUnqualifiedName(fullName);  // Use the unqualified typename for brevity.
    					if (!imp.isOnDemand()) {  // TODO: What should we do with import *s?  It might be too expensive to try all static methods.  This ignores them.
    						IJavaReferenceType importedType = (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(fullName, stack, target, typeCache);
    						if (importedType != null) {
    							if (!objectInterfaceTypes.contains(importedType)) {  // We've already handled these above.
    								addFieldAccesses(ExpressionMaker.makeStaticName(shortName, importedType, valueCache, thread), curLevel, depth, maxDepth);
    								addMethodCalls(ExpressionMaker.makeStaticName(shortName, importedType, valueCache, thread), nextLevel, curLevel, depth, maxDepth);
    							}
    						} else
    							;//System.err.println("I cannot get the class of the import " + fullName);
    					}
    					monitor.worked(1);
    				}
    			}
    			for (Method method: newlyUnpruneds)
    				prunedDepths.remove(method);
    			newlyUnpruneds.clear();
    		}
    		
    		/*System.out.println("Exploring " + result.size() + " possible expressions.");
    		for (TypedExpression e : result)
    			System.out.println(e.toString());*/
    		return curLevel;
		} catch (DebugException e) {
			e.printStackTrace();
        	EclipseUtils.showError("Error", "An error occurred during expression generation.", e);
			throw new RuntimeException("I cannot compute all valid expressions.");
		}
	}

	/**
	 * Adds the local variables of the correct depth to the given list.
	 * The variable that is being assigned will have depth 1, since
	 * the line "x = x" is not unique.
	 * @param depth The current depth.
	 * @param maxDepth The maximum search depth.
	 * @param curLevel The current list of expressions being generated.
	 * @throws DebugException
	 */
	private void addLocals(int depth, int maxDepth, ArrayList<TypedExpression> curLevel) throws DebugException {
		for (IJavaVariable l : stack.getLocalVariables()) {
			IJavaType lType = EclipseUtils.getTypeOfVariableAndLoadIfNeeded(l, stack);
			if (isHelpfulType(lType, depth, maxDepth))
				addUniqueExpressionToList(curLevel, ExpressionMaker.makeVar(l.getName(), (IJavaValue)l.getValue(), lType, false, valueCache, thread), depth);
		}
	}
	
	// TODO: Convert field/method code to use the public API?  I can use IType to get fields/methods (but they only get declared ones, so I'd have to walk the supertype chain), IType to get their signature, Signature.getSignature{Qualifier,SimpleName} to get type names, and then EclipseUtils.getType-like code to get the IType back.
	
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
	private static boolean isLegalField(Field field, IJavaType thisType) {
		return field.isPublic() || field.declaringType().equals(((JDIType)thisType).getUnderlyingType());
	}
	
	/**
	 * Adds field accesses of the given expression.
	 * @param e The receiver expression.
	 * @param ops The list into which we will insert
	 * the newly-generated expressions.
	 * @param depth The current search depth.
	 * @param maxDepth The maximum search depth.
	 * @throws DebugException
	 */
	private void addFieldAccesses(TypedExpression e, List<TypedExpression> ops, int depth, int maxDepth) throws DebugException {
		// We could use the public Eclipse API here, but it isn't as clean and works on objects not types, so wouldn't work with our static accesses, which we give a null value.  Note that as below with methods, we must now be careful converting between jdi types and Eclipse types. 
		IJavaObject obj = e.getValue() != null ? (IJavaObject)e.getValue() : null;
		Type objTypeImpl = ((JDIType)e.getType()).getUnderlyingType();
		//Type thisTypeImpl = ((JDIType)thisType).getUnderlyingType();
		boolean isStatic = ExpressionMaker.isStatic(e.getExpression());
		//String objTypeName = isStatic ? e.getExpression().toString() : objTypeImpl.name();
		for (Field field: getFields(e.getType())) {
			if (!isLegalField(field, thisType) || (isStatic != field.isStatic()) || field.isSynthetic())
				continue;
			if (field.isStatic() && staticAccesses.contains(field.declaringType().name() + " " + field.name()))
				continue;
			IJavaType fieldType = EclipseUtils.getTypeAndLoadIfNeeded(field.typeName(), stack, target, typeCache);
			/*if (fieldType == null)
				System.err.println("I cannot get the class of " + objTypeImpl.name() + "." + field.name() + "(" + field.typeName() + ")");*/
			if (fieldType != null && isHelpfulType(fieldType, depth, maxDepth)) {
				TypedExpression receiver = e;
				if (e.getExpression() instanceof ThisExpression || e.getType().equals(thisType))
					receiver = null;  // Don't use a receiver if it is null or the this type.
				else if (field.isStatic())
						receiver = ExpressionMaker.makeStaticName(getShortestTypename(field.declaringType().name()), (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(field.declaringType().name(), stack, target, typeCache), valueCache, thread);
				IJavaValue fieldValue = null;
				if (field.isStatic())
					fieldValue = (IJavaValue)((IJavaReferenceType)e.getType()).getField(field.name()).getValue();
				else if (obj != null)
					fieldValue = (IJavaValue)obj.getField(field.name(), !field.declaringType().equals(objTypeImpl)).getValue();
				TypedExpression fieldExpr = receiver == null ? ExpressionMaker.makeVar(field.name(), fieldValue, fieldType, true, valueCache, thread) : ExpressionMaker.makeFieldAccess(receiver, field.name(), fieldType, fieldValue, valueCache, thread); 
				addUniqueExpressionToList(ops, fieldExpr, depth);
				if (field.isStatic())
					staticAccesses.add(field.declaringType().name() + " " + field.name());
			}
		}
	}

	/**
	 * Gets all the visible methods of the given type.
	 * @param type The type whose methods we want to get.
	 * @return All of the visible methods of the given type.
	 */
	public static List<Method> getMethods(IJavaType type) {
		try {
			if (type != null && EclipseUtils.isObject(type)) {
				List<?> untypedMethods = ((ReferenceType)((JDIType)type).getUnderlyingType()).visibleMethods();
				ArrayList<Method> methods = new ArrayList<Method>(untypedMethods.size());
				for (Object o: untypedMethods) {
					Method method = (Method)o;
					if (!methodBlacklist.containsKey(type.getName()) || !methodBlacklist.get(type.getName()).contains(method.name() + " " + method.signature()))
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
				&& !"hashCode".equals(method.name()) && !"deepHashCode".equals(method.name()) && !"intern".equals(method.name());  // TODO: This should really be part of the blacklist.
	}

	/**
	 * Adds method calls of the given expression.
	 * @param e The receiver expression.
	 * @param nextLevel The expressions to use as
	 * arguments.
	 * @param ops The list into which we will insert
	 * the newly-generated expressions.
	 * @param depth The current search depth.
	 * @param maxDepth The maximum search depth.
	 * @throws DebugException
	 */
	private void addMethodCalls(TypedExpression e, List<FullyEvaluatedExpression> nextLevel, List<TypedExpression> ops, int depth, int maxDepth) throws DebugException {
		// The public API doesn't tell us the methods of a class, so we need to use the jdi.  Note that we must now be careful converting between jdi types and Eclipse types.
		Type objTypeImpl = ((JDIType)e.getType()).getUnderlyingType();
		if (classBlacklist.contains(objTypeImpl.name()))
			return;
		boolean isConstructor = e.getExpression() == null;
		boolean isStatic = !isConstructor && ExpressionMaker.isStatic(e.getExpression());
		//String objTypeName = isStatic ? e.getExpression().toString() : objTypeImpl.name();
		Method stackMethod = ((JDIStackFrame)stack).getUnderlyingMethod();
		List<Method> legalMethods = getMethods(e.getType());
		OverloadChecker overloadChecker = new OverloadChecker(e.getType(), stack, target, typeCache, subtypeChecker);
		for (Method method : legalMethods) {
			// Filter out java.lang.Object methods and fake methods like "<init>".  Note that if we don't filter out Object's methods we do getClass() and then call reflective methods, which is bad times.
			// TODO: Allow calling protected and package-private things when it's legal.
			if (!isLegalMethod(method, thisType, isConstructor) || (isStatic != method.isStatic()) || method.equals(stackMethod))  // Disable explicit recursion (that is, calling the current method), since it is definitely not yet complete.
				continue;
			if (!isConstructor && method.returnTypeName().equals("void"))
				continue;
            if (method.isStatic() && staticAccesses.contains(method.declaringType().name() + " " + method.name() + " " + method.signature()))
                continue;
			IJavaType returnType = isConstructor ? e.getType() : EclipseUtils.getTypeAndLoadIfNeeded(method.returnTypeName(), stack, target, typeCache);
			/*if (returnType == null)
				System.err.println("I cannot get the class of the return type of " + objTypeImpl.name() + "." + method.name() + "() (" + method.returnTypeName() + ")");*/
			if (returnType != null && (isHelpfulType(returnType, depth, maxDepth) || method.isConstructor())) {  // Constructors have void type... 
				List<?> argumentTypeNames = method.argumentTypeNames();
				// TODO: Improve overloading detection.
				overloadChecker.setMethod(method);
				ArrayList<ArrayList<EvaluatedExpression>> allPossibleActuals = new ArrayList<ArrayList<EvaluatedExpression>>(argumentTypeNames.size());
				Iterator<?> aIt = argumentTypeNames.iterator();
				while (aIt.hasNext()) {
					IJavaType argType = EclipseUtils.getTypeAndLoadIfNeeded((String)aIt.next(), stack, target, typeCache);
					if (argType == null) {
						//System.err.println("I cannot get the class of the arguments to " + objTypeImpl.name() + "." + method.name() + "()");
						break;
					}
					int curArgIndex = allPossibleActuals.size();
					ArrayList<EvaluatedExpression> curPossibleActuals = new ArrayList<EvaluatedExpression>();
					// TODO (low priority): This can get called multiple times if there are multiple args with the same type (or even different methods with args of the same type), but this has a tiny effect compared to the general state space explosion problem.
					for (EvaluatedExpression a : nextLevel)
						if ((new SupertypeBound(argType)).isFulfilledBy(a.getType(), subtypeChecker, typeCache, stack, target)  // TODO: This doesn't work for generic methods.
								&& meetsNonNullPreconditions(method, curArgIndex + 1, a)) {
							if (overloadChecker.needsCast(argType, a.getType(), curArgIndex)) {  // If the method is overloaded, when executing the expression we might get "Ambiguous call" compile errors, so we put in a cast to remove the ambiguity.
								//System.out.println("Adding cast to type " + argType.toString() + " to argument " + a.getExpression().toString() + " at index "+ curArgIndex + " of method " + method.declaringType() + "." + method.name() + " with " + method.argumentTypeNames().size() + " arguments.");
								a = (EvaluatedExpression)ExpressionMaker.makeCast(a, argType, a.getValue(), valueCache, thread);
							}
							curPossibleActuals.add(a);
						}
					allPossibleActuals.add(curPossibleActuals);
				}
				if (allPossibleActuals.size() == argumentTypeNames.size()) {
					TypedExpression receiver = e;
					if (method.isStatic())
							receiver = ExpressionMaker.makeStaticName(getShortestTypename(method.declaringType().name()), (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(method.declaringType().name(), stack, target, typeCache), valueCache, thread);
					pruneManyArgCalls(method, allPossibleActuals, depth, depth - 1, receiver.getType() + "." + method.name());
					makeAllCalls(method, method.name(), receiver, returnType, ops, allPossibleActuals, new ArrayList<EvaluatedExpression>(allPossibleActuals.size()), depth);
                    if (method.isStatic())
                        staticAccesses.add(method.declaringType().name() + " " + method.name() + " " + method.signature());
				}
			}
		}
	}
	
	/**
	 * Prunes the given list of possible actuals to ensure that
	 * there are not far too many actual calls.  We do this by
	 * incrementally removing arguments of the greatest depth.
	 * This is of course an incomplete heuristic.
	 * @param method The method being called.
	 * @param possibleActuals A list of all the possible
	 * actuals for each argument.
	 * @param curDepth The current depth of expressions being
	 * generated.
	 * @param curMaxArgDepth The current maximum depth of the args.
	 * @param methodToString A toString representation of the
	 * method being called.
	 */
	private void pruneManyArgCalls(Method method, ArrayList<ArrayList<EvaluatedExpression>> allPossibleActuals, int curDepth, int curMaxArgDepth, String methodToString) {
		boolean pruned = pruneManyArgCalls(allPossibleActuals, curDepth, curMaxArgDepth, methodToString);
		if (pruned) {
			if (!prunedDepths.containsKey(method))
				prunedDepths.put(method, curDepth);
		} else if (prunedDepths.containsKey(method))
			newlyUnpruneds.add(method);
	}
	
	/**
	 * Prunes the given list of possible actuals to ensure that
	 * there are not far too many actual calls.  We do this by
	 * incrementally removing arguments of the greatest depth.
	 * This is of course an incomplete heuristic.
	 * @param possibleActuals A list of all the possible
	 * actuals for each argument.
	 * @param curDepth The current depth of expressions being
	 * generated.
	 * @param curMaxArgDepth The current maximum depth of the args.
	 * @param methodToString A toString representation of the
	 * method being called.
	 */
	private boolean pruneManyArgCalls(ArrayList<? extends ArrayList<? extends TypedExpression>> allPossibleActuals, int curDepth, int curMaxArgDepth, String methodToString) {
		long numCombinations = getNumCalls(allPossibleActuals);
		if (numCombinations > 50 * Math.pow(10, Math.max(0, curDepth - 1))) {  // 50 at depth 1, 500 at depth 2, 5000 at depth 3, etc.
			for (ArrayList<? extends TypedExpression> possibleActuals: allPossibleActuals)
				for (Iterator<? extends TypedExpression> it = possibleActuals.iterator(); it.hasNext(); )
					if (getDepth(it.next()) == curMaxArgDepth)
						it.remove();
			//System.out.println("Pruned call to " + methodToString + " from " + numCombinations + " to " + getNumCalls(allPossibleActuals));
			pruneManyArgCalls(allPossibleActuals, curDepth, curMaxArgDepth - 1, methodToString);
			return true;
		}
		return false;
	}
	
	/**
	 * Gets the number of calls that will be created from
	 * the given list of possible actuals.
	 * @param possibleActuals A list of all the possible
	 * actuals for each argument.
	 * @return The number of calls with the given possible actuals.
	 */
	private static long getNumCalls(ArrayList<? extends ArrayList<? extends TypedExpression>> allPossibleActuals) {
		long total = 1L;
		for (ArrayList<? extends TypedExpression> possibleActuals: allPossibleActuals)
			total *= possibleActuals.size();
		return total;
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
	private static boolean meetsNonNullPreconditions(Method method, int argIndex, EvaluatedExpression arg) {
		String methodId = method.declaringType().name() + " " + method.name() + " " + method.signature();
		if (methodPreconditions.containsKey(methodId))
			for (Predicate precondition: methodPreconditions.get(methodId))
				if (precondition instanceof NonNull)
					if (((NonNull)precondition).getArgIndex() == argIndex && arg.getValue().isNull())
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
	private static boolean meetsPreconditions(Method method, TypedExpression receiver, ArrayList<EvaluatedExpression> actuals) {
		String methodId = method.declaringType().name() + " " + method.name() + " " + method.signature();
		if (methodPreconditions.containsKey(methodId))
			for (Predicate precondition: methodPreconditions.get(methodId))
				if (!precondition.satisfies(receiver, actuals))
					return false;
		return true;
	}
	
	/**
	 * Loads the types from imports and method returns
	 * and args in batches.  This is an optimization,
	 * as doing all of the loads in a batch is faster
	 * than doing them one-by-one.
	 * @param nextLevel The next level of expressions
	 * that we will use to build the current level.
	 * @param imports The imports.
	 * @throws DebugException
	 */
	private void loadTypesFromMethods(List<FullyEvaluatedExpression> nextLevel, IImportDeclaration[] imports) throws DebugException {
		Set<String> importNames = new HashSet<String>();
		for (IImportDeclaration imp : imports)
			if (!imp.isOnDemand())
				importNames.add(imp.getElementName());
		tryToLoad(importNames);
		Set<String> typeNames = new HashSet<String>();
		for (FullyEvaluatedExpression e: nextLevel)
			if (ExpressionMaker.isObjectOrInterface(e.getType()) && (e.getValue() == null || !e.getValue().isNull())) {
				checkMethods(e.getType(), typeNames);
				checkFields(e.getType(), typeNames);
			}
		for (IImportDeclaration imp : imports)
			if (!imp.isOnDemand()) {
				IJavaType impType = EclipseUtils.getTypeAndLoadIfNeeded(imp.getElementName(), stack, target, typeCache);
				checkMethods(impType, typeNames);
				checkFields(impType, typeNames);
			}
		tryToLoad(typeNames);
	}

	/**
	 * Gets the types used as returns or arguments in
	 * methods of the given receiver type.
	 * Note that this is an overapproximation, as we
	 * do not require that the return type is helpful.
	 * @param receiverType The type of the receiver of
	 * the desired methods.
	 * @param typeNames The set into which to store
	 * the type names.
	 */
	private void checkMethods(IJavaType receiverType, Set<String> typeNames) {
		for (Method method : getMethods(receiverType)) {
			if (isLegalMethod(method, thisType, false) && !method.returnTypeName().equals("void")) {
				addTypeName(method.returnTypeName(), typeNames);
				if (method.isStatic())
					addTypeName(method.declaringType().name(), typeNames);
				for (Object argType: method.argumentTypeNames())
					addTypeName((String)argType, typeNames);
			}
		}
	}

	/**
	 * Gets the types used for fields of the given
	 * receiver type.
	 * Note that this is an overapproximation, as we
	 * do not require that the type is helpful.
	 * @param receiverType The type of the receiver of
	 * the desired fields.
	 * @param typeNames The set into which to store
	 * the type names.
	 */
	private void checkFields(IJavaType receiverType, Set<String> typeNames) {
		for (Field field: getFields(receiverType)) {
			if (isLegalField(field, thisType)) {
				addTypeName(field.typeName(), typeNames);
				if (field.isStatic())
					addTypeName(field.declaringType().name(), typeNames);
			}
		}
	}
	
	/**
	 * Adds the given type name to the given set.
	 * It additionally adds the component type
	 * if the given type is an array type.
	 * @param typeName The name of the type to add.
	 * @param typeNames The set of type names.
	 */
	private static void addTypeName(String typeName, Set<String> typeNames) {
		typeNames.add(typeName);
		if (typeName.endsWith("[]"))  // If an array's component type is not loaded, we can crash during evaluation of expressions involving it.
			addTypeName(typeName.substring(0, typeName.length() - 2), typeNames);
	}

	/**
	 * Tries to load the given type names in a batch.
	 * @param typeNames The type names
	 * @throws DebugException
	 */
	private void tryToLoad(Set<String> typeNames) throws DebugException {
		// Filter out types we've already loaded.
		Set<String> unloadedTypeNames = new HashSet<String>();
		for (String typeName: typeNames)
			if (typeCache.get(typeName) == null && !typeCache.isIllegal(typeName) && !typeCache.isCheckedLegal(typeName))
				unloadedTypeNames.add(typeName);
		if (!unloadedTypeNames.isEmpty() && EclipseUtils.tryToLoadTypes(unloadedTypeNames, stack))
			for (String typeName: unloadedTypeNames)  // Mark all the type names as legal so we will not have to check if they are illegal one-by-one, which is slow.
				typeCache.markCheckedLegal(typeName);
	}
	
	/**
	 * If we have imported a type, use its unqualified name.
	 * @param typeName The name of the type.
	 * @return The shortest legal name for the given type.
	 * @throws DebugException
	 */
	private String getShortestTypename(String typeName) throws DebugException {
		if (importsSet.contains(typeName))
			return EclipseUtils.getUnqualifiedName(EclipseUtils.sanitizeTypename(typeName));
		else
			return typeName;
	}
	
	/**
	 * Adds the given expression to the given list
	 * if it has the right depth, including checking
	 * uniqueness wrt UniqueASTChecker.
	 * We need to check the depth since genAllExprs
	 * returns is cumulative, so when the max depth is 2,
	 * at depth 0 nextLevel will be a superset of the
	 * nextLevel at depth 1 and so we will generate the same
	 * expressions again.
	 * @param list List to which to add unique expressions.
	 * @param e Expression to add if it is unique.
	 * @param depth The current search depth.
	 * @throws DebugException 
	 */
	private void addUniqueExpressionToList(List<TypedExpression> list, TypedExpression e, int depth) throws DebugException {
		// We only add constructors at max depth, but they might actually be lower depth.
		if (e != null && isCorrectDepth(e, depth)) {
			if (e.getValue() != null && "V".equals(e.getValue().getSignature()))
				return;
			Value value = e.getWrapperValue();
			if (value != null && equivalences.containsKey(value))
				addEquivalentExpression(equivalences.get(value), (EvaluatedExpression)e);
			else {
				if (e.getValue() != null)
					addEquivalentExpressionOnlyIfNewValue((EvaluatedExpression)e, value);
				list.add(e);
			}
		}
	}

	/**
	 * Checks whether the given expression has the given depth,
	 * including checking uniqueness wrt UniqueASTChecker.
	 * @param e Expression to add if it is unique.
	 * @param depth The current search depth.
	 * @return Whether the given expression has the given depth
	 * wrt UniqueASTChecker.
	 */
	private boolean isCorrectDepth(TypedExpression e, int depth) {
		return getDepth(e) == depth || (ExpressionMaker.getMethod(e.getExpression()) != null && ExpressionMaker.getMethod(e.getExpression()).isConstructor());
	}
	
	/**
	 * Determines whether an expression of the given type can be
	 * useful to us.
	 * We currently assume that anything can be useful until the
	 * outermost depth, since there are probably methods that
	 * can take in anything, but that only the desired type
	 * is helpful otherwise.
	 * TODO (lowish priority): We could be smarter here, e.g.,
	 * by pruning ints if the target is not an int and there
	 * are no methods with int arguments and no arrays.
	 * @param curType The type to test.
	 * @param depth The current depth.
	 * @param maxDepth The maximum search depth.
	 * @return Whether an expression of the given type can be useful to us.
	 * @throws DebugException 
	 */
	private boolean isHelpfulType(IJavaType curType, int depth, int maxDepth) throws DebugException {
		if (curType != null && "V".equals(curType.getSignature()))  // Void things never return anything useful.
			return false;
		// TODO: The commented parts in {DesiredType,SupertypeBound}.fulfillsConstraint disallow downcasting things, e.g., (Foo)x.getObject(), which could be legal, but is unlikely to be.
		if (depth < maxDepth)
			return true;
		return typeConstraint.isFulfilledBy(curType, subtypeChecker, typeCache, stack, target);
	}
	
	/**
	 * Creates all possible calls using the given actuals.
	 * @param method The method being called.
	 * @param name The method name.
	 * @param receiver The receiving object.
	 * @param returnType The return type of the function.
	 * @param ops The list to add the unique calls created. 
	 * @param possibleActuals A list of all the possible actuals for each argument.
	 * @param curActuals The current list of actuals, which is built
	 * up through recursion.
	 * @param depth The current search depth.
	 * @throws DebugException 
	 */
	private void makeAllCalls(Method method, String name, TypedExpression receiver, IJavaType returnType, List<TypedExpression> ops, ArrayList<ArrayList<EvaluatedExpression>> possibleActuals, ArrayList<EvaluatedExpression> curActuals, int depth) throws DebugException {
		if (curActuals.size() == possibleActuals.size()) {
			if (meetsPreconditions(method, receiver, curActuals))
				  // We might evaluate the call when we create it (e.g., StringEvaluator), so first ensure it has the proper depth to avoid re-evaluating some calls.
				if (method.isConstructor() || getDepthOfCall(receiver.getExpression(), curActuals, method.name()) == depth
						|| (newlyUnpruneds.contains(method) && getDepthOfCall(receiver.getExpression(), curActuals, method.name()) >= prunedDepths.get(method)))
					addUniqueExpressionToList(ops, ExpressionMaker.makeCall(name, receiver, curActuals, returnType, thisType, method, target, valueCache, thread, staticEvaluator), depth);
		} else {
			int argNum = curActuals.size();
			for (EvaluatedExpression e : possibleActuals.get(argNum)) {
				curActuals.add(e);
				makeAllCalls(method, name, receiver, returnType, ops, possibleActuals, curActuals, depth);
				curActuals.remove(argNum);
			}
		}
	}
	
	/**
	 * Creates all possible calls/creations using the given actuals.
	 * @param method The method being called.
	 * @param name The method name.
	 * @param receiver The receiving object for method calls or a type
	 * literal representing the type being created for creations.
	 * @param result The list to add the unique calls created. 
	 * @param possibleActuals A list of all the possible actuals for each argument.
	 * @param curActuals The current list of actuals, which is built
	 * up through recursion.
	 */
	private void makeAllCalls(Method method, String name, Expression receiver, List<Expression> result, ArrayList<ArrayList<TypedExpression>> possibleActuals, ArrayList<TypedExpression> curActuals, int targetDepth) {
		if (curActuals.size() == possibleActuals.size()) {
			if (getDepthOfCall(receiver, curActuals, method.name()) <= targetDepth) {
				if ("<init>".equals(name))
					result.add(ExpressionMaker.makeClassInstanceCreation(((TypeLiteral)receiver).getType(), curActuals, method));
				else
					result.add(ExpressionMaker.makeCall(name, receiver, curActuals, method));
			}
		} else {
			int depth = curActuals.size();
			for (TypedExpression e : possibleActuals.get(depth)) {
				curActuals.add(e);
				makeAllCalls(method, name, receiver, result, possibleActuals, curActuals, targetDepth);
				curActuals.remove(depth);
			}
		}
	}
	
	private static void addEquivalentExpression(ArrayList<EvaluatedExpression> equivs, EvaluatedExpression e) {
		equivs.add(e);
	}
	
	private void addEquivalentExpression(EvaluatedExpression e) {
		Utils.addToMap(equivalences, e.getWrapperValue(), e);
	}
	
	private void addEquivalentExpressionOnlyIfNewValue(EvaluatedExpression e, Value value) {
		if (!equivalences.containsKey(value))
			equivalences.put(value, Utils.makeList(e));
	}
	
	/**
	 * Finds other expressions equivalent to the given ones.
	 * @param exprs Expressions for which we want to find
	 * equivalent expressions.
	 * @param monitor The progress monitor.  The caller should
	 * not allocate a new progress monitor; this method will.
	 * @return Expressions that are equivalent to the given
	 * ones.  Note that the result is disjoint from the input.
	 * @throws DebugException
	 */
	private ArrayList<FullyEvaluatedExpression> expandEquivalences(ArrayList<FullyEvaluatedExpression> exprs, IProgressMonitor monitor) throws DebugException {
		Set<Value> values = new HashSet<Value>();
		Set<EvaluatedExpression> newlyExpanded = new HashSet<EvaluatedExpression>();
		Map<Value, String> toStrings = new HashMap<Value, String>();
		int totalWork = 0;  // This is just an estimate of the total worked used for the progress monitor; it may miss some elements.
		for (FullyEvaluatedExpression result: exprs) {
			Value value = result.getWrapperValue();
			addEquivalentExpressionOnlyIfNewValue(result, value);  // Demonstrated primitive values are new, since those are used but not put into the equivalences map.
			boolean added = values.add(value);
			if (added) {
				toStrings.put(value, result.getResultString());
				totalWork += equivalences.get(value).size();
			}
		}
		monitor = SubMonitor.convert(monitor, "Equivalence expansions", totalWork);
		ArrayList<FullyEvaluatedExpression> results = new ArrayList<FullyEvaluatedExpression>();
		for (Value value: values) {
			for (TypedExpression expr : new ArrayList<TypedExpression>(equivalences.get(value))) {  // Make a copy since we'll probably add expressions to this.
				if (monitor.isCanceled())
					throw new OperationCanceledException();
				expandEquivalencesRec(expr.getExpression(), newlyExpanded);
				monitor.worked(1);
			}
			String valueString = toStrings.get(value);
			for (EvaluatedExpression expr: getEquivalentExpressions(value, null, typeConstraint))
				if (typeConstraint.isFulfilledBy(expr.getType(), subtypeChecker, typeCache, stack, target))
					results.add(new FullyEvaluatedExpression(expr.getExpression(), expr.getType(), expr.getWrapperValue(), valueString));
		}
		results.removeAll(exprs);
		return results;
	}
	
	/**
	 * Finds expressions that are equivalent to the given one.
	 * @param expr The given expression.
	 * @param newlyExpanded The set of expressions we have already
	 * expanded to stop us from doing extra work or recursing infinitely.
	 * @throws DebugException
	 */
	private void expandEquivalencesRec(Expression expr, Set<EvaluatedExpression> newlyExpanded) throws DebugException {
		if (expr == null)
			return;
		IJavaValue javaValue = ExpressionMaker.getExpressionValue(expr);
		if (javaValue == null)
			return;
		Value value = Value.makeValue(javaValue, valueCache, thread);
		IJavaType type = javaValue.getJavaType();
		EvaluatedExpression valued = new EvaluatedExpression(expr, type, value);
		if (newlyExpanded.contains(valued))
			return;
		newlyExpanded.add(valued);
		int curDepth = getDepth(expr);
		addEquivalentExpressionOnlyIfNewValue(valued, value);
		ArrayList<EvaluatedExpression> curEquivalences = equivalences.get(value);
		if (expr instanceof NumberLiteral || expr instanceof BooleanLiteral || expr instanceof Name || expr instanceof ParenthesizedExpression || expr instanceof ThisExpression || expr instanceof NullLiteral) {
			// Do nothing as there's nothing to expand.
		} else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			expandEquivalencesRec(infix.getLeftOperand(), newlyExpanded);
			expandEquivalencesRec(infix.getRightOperand(), newlyExpanded);
			for (TypedExpression l : getEquivalentExpressions(infix.getLeftOperand(), UnknownConstraint.getUnknownConstraint()))
				for (TypedExpression r : getEquivalentExpressions(infix.getRightOperand(), UnknownConstraint.getUnknownConstraint()))
					if (getDepth(l.getExpression()) < curDepth && getDepth(r.getExpression()) < curDepth && isUsefulInfix(l, infix.getOperator(), r))
						addIfNew(curEquivalences, new EvaluatedExpression(ExpressionMaker.makeInfix(l.getExpression(), infix.getOperator(), r.getExpression()), type, value), valued);
		} else if (expr instanceof ArrayAccess) {
			ArrayAccess array = (ArrayAccess)expr;
			expandEquivalencesRec(array.getArray(), newlyExpanded);
			expandEquivalencesRec(array.getIndex(), newlyExpanded);
			for (TypedExpression a : getEquivalentExpressions(array.getArray(), UnknownConstraint.getUnknownConstraint()))
				for (TypedExpression i : getEquivalentExpressions(array.getIndex(), UnknownConstraint.getUnknownConstraint()))
					if (getDepth(a.getExpression()) < curDepth && getDepth(i.getExpression()) < curDepth)
						addIfNew(curEquivalences, new EvaluatedExpression(ExpressionMaker.makeArrayAccess(a.getExpression(), i.getExpression()), type, value), valued);
		} else if (expr instanceof FieldAccess) {
			FieldAccess field = (FieldAccess)expr;
			expandEquivalencesRec(field.getExpression(), newlyExpanded);
			for (Expression e : getEquivalentExpressionsOrGiven(field.getExpression(), new FieldConstraint(field.getName().getIdentifier(), UnknownConstraint.getUnknownConstraint())))
				if (getDepth(e) < curDepth)
					addIfNew(curEquivalences, new EvaluatedExpression(ExpressionMaker.makeFieldAccess(e, field.getName().getIdentifier()), type, value), valued);
		} else if (expr instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression)expr;
			expandEquivalencesRec(prefix.getOperand(), newlyExpanded);
			for (TypedExpression e : getEquivalentExpressions(prefix.getOperand(), UnknownConstraint.getUnknownConstraint()))
				if (getDepth(e.getExpression()) < curDepth)
					addIfNew(curEquivalences, new EvaluatedExpression(ExpressionMaker.makePrefix(e.getExpression(), prefix.getOperator()), type, value), valued);
		} else if (expr instanceof MethodInvocation) {
			MethodInvocation call = (MethodInvocation)expr;
			expandCall(call.getExpression(), ExpressionMaker.getMethod(call), call.arguments(), newlyExpanded, value, type, valued, curDepth, curEquivalences);
		} else if (expr instanceof ClassInstanceCreation) {
			ClassInstanceCreation call = (ClassInstanceCreation)expr;
			expandCall(ExpressionMaker.makeTypeLiteral(call.getType()), ExpressionMaker.getMethod(call), call.arguments(), newlyExpanded, value, type, valued, curDepth, curEquivalences);
		} else if (expr instanceof CastExpression) {
			CastExpression cast = (CastExpression)expr;
			ExpressionMaker.setExpressionValue(cast.getExpression(), javaValue);
			expandEquivalencesRec(cast.getExpression(), newlyExpanded);
			IJavaType castType = EclipseUtils.getType(cast.getType().toString(), stack, target, typeCache);
			for (TypedExpression e : getEquivalentExpressions(cast.getExpression(), UnknownConstraint.getUnknownConstraint()))
				if (getDepth(e.getExpression()) < curDepth)
					addIfNew(curEquivalences, new EvaluatedExpression(ExpressionMaker.makeCast(e.getExpression(), castType), type, value), valued);
		} else
			throw new RuntimeException("Unexpected Expression " + expr.toString());
	}
	
	/**
	 * Adds the new expression to the given list if it is not
	 * equal to the current expression.
	 * @param curEquivalences The list.
	 * @param newExpr The new expression.
	 * @param curExpr The old expression.
	 */
	private static void addIfNew(ArrayList<EvaluatedExpression> curEquivalences, EvaluatedExpression newExpr, EvaluatedExpression curExpr) {
		if (!newExpr.equals(curExpr))
			addEquivalentExpression(curEquivalences, newExpr);
	}

	/**
	 * Returns expressions that are equivalent to the given expression.
	 * If the expression is null or we do not know its value (and thus
	 * is not in our equivalences map), we simply return it.
	 * @param expr The expression for which we want to find equivalent
	 * expressions.
	 * @param constraint The constraint that should hold for the expressions.
	 * @return Expressions that are equivalent to the given expression,
	 * or itself if it is not in our equivalences map.
	 * @throws DebugException
	 */
	private Expression[] getEquivalentExpressionsOrGiven(Expression expr, TypeConstraint constraint) throws DebugException {
		if (expr == null || ExpressionMaker.getExpressionValue(expr) == null)
			return new Expression[] { expr };
		else {
			List<EvaluatedExpression> allPossibleExpressions = getEquivalentExpressions(expr, constraint);
			Expression[] result = new Expression[allPossibleExpressions.size()];
			for (int i = 0; i < result.length; i++)
				result[i] = allPossibleExpressions.get(i).getExpression();
			return result;
		}
	}

	/**
	 * Returns expressions that are equivalent to the given expression
	 * by looking them up in the equivalences map.
	 * @param expr The expression for which we want to find equivalent
	 * expressions.
	 * @param constraint The constraint that should hold for the expressions.
	 * @return Expressions that are equivalent to the given expression.
	 * @throws DebugException
	 */
	private ArrayList<EvaluatedExpression> getEquivalentExpressions(Expression expr, TypeConstraint constraint) throws DebugException {
		IJavaValue value = ExpressionMaker.getExpressionValue(expr);
		return getEquivalentExpressions(Value.makeValue(value, valueCache, thread), expr, constraint);
	}

	/**
	 * Returns expressions that have the given value
	 * by looking them up in the equivalences map.
	 * It also adds the current expression if it is a non-zero constant
	 * and hence not in the equivalences map.
	 * @param curValue The value for which we want to find expressions
	 * with that value.
	 * @param curExpr The current expression.
	 * @param constraint The constraint that should hold for the expressions.
	 * @return Expressions that have the given expression.
	 * @throws DebugException
	 */
	private ArrayList<EvaluatedExpression> getEquivalentExpressions(Value curValue, Expression curExpr, TypeConstraint constraint) throws DebugException {
		ArrayList<EvaluatedExpression> results = new ArrayList<EvaluatedExpression>();
		for (EvaluatedExpression expr: equivalences.get(curValue))
			// We might get things that are equivalent but with difference static types (e.g., Object and String when we want a String), so we ensure we satisfy the type constraint.
			// However, we have to special case static accesses/calls (e.g., Foo.bar), as the expression part has type Class not the desired type (Foo).
			if (constraint.isFulfilledBy(expr.getType(), subtypeChecker, typeCache, stack, target) || (ExpressionMaker.isStatic(expr.getExpression()) && (constraint instanceof FieldConstraint || constraint instanceof MethodConstraint)))
				results.add(expr);
		// 0 is already in the equivalences map, but no other int constants are.
		if ((curExpr instanceof NumberLiteral && !"0".equals(((NumberLiteral)curExpr).getToken())) || curExpr instanceof StringLiteral || curExpr instanceof BooleanLiteral)
			results.add(new EvaluatedExpression(curExpr, curValue.getValue().getJavaType(), curValue));
		return results;
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
	private boolean isUsefulInfix(TypedExpression l, InfixExpression.Operator op, TypedExpression r) throws DebugException {
		if (op == InfixExpression.Operator.PLUS)
			return l.getExpression().getProperty("isConstant") == null && ((r.getExpression().getProperty("isConstant") != null && r.getValue().equals(one.getValue())) || l.getExpression().toString().compareTo(r.getExpression().toString()) < 0);
		else if (op == InfixExpression.Operator.TIMES)
			return l.getExpression().getProperty("isConstant") == null && ((r.getExpression().getProperty("isConstant") != null && r.getValue().equals(two.getValue())) || l.getExpression().toString().compareTo(r.getExpression().toString()) <= 0);
		else if (op == InfixExpression.Operator.MINUS)
			return l.getExpression().getProperty("isConstant") == null && ((r.getExpression().getProperty("isConstant") != null && r.getValue().equals(one.getValue())) || l.getExpression().toString().compareTo(r.getExpression().toString()) != 0);
		else if (op == InfixExpression.Operator.DIVIDE)
			return l.getExpression().getProperty("isConstant") == null && ((r.getExpression().getProperty("isConstant") != null && r.getValue().equals(two.getValue())) || l.getExpression().toString().compareTo(r.getExpression().toString()) != 0);
		else if (ExpressionMaker.isInt(l.getType()) && ExpressionMaker.isInt(r.getType()))
			return l.getExpression().toString().compareTo(r.getExpression().toString()) < 0 && (!(l.getExpression() instanceof PrefixExpression) || !(r.getExpression() instanceof PrefixExpression));
		else
			return l.getExpression().toString().compareTo(r.getExpression().toString()) < 0;
	}

	/**
	 * Finds calls that are equivalent to the given one and
	 * adds them to curEquivalences.
	 * @param expression The expression part of the call.
	 * @param method The method being called.
	 * @param arguments The arguments.
	 * @param newlyExpanded The set of expressions we have already
	 * expanded to stop us from doing extra work or recursing infinitely.
	 * @param value The result of the call.
	 * @param type The type of the result of the call.
	 * @param valued The call itself.
	 * @param curDepth The depth of the call.
	 * @param curEquivalences The set of expressions equivalent
	 * to this call.  The new expressions will be added to this set.
	 * @throws DebugException
	 */
	private void expandCall(Expression expression, Method method, List<?> arguments, Set<EvaluatedExpression> newlyExpanded, Value value, IJavaType type, EvaluatedExpression valued, int curDepth, ArrayList<EvaluatedExpression> curEquivalences) throws DebugException {
		String name = method.name();
		expandEquivalencesRec(expression, newlyExpanded);
		OverloadChecker overloadChecker = new OverloadChecker(EclipseUtils.getTypeAndLoadIfNeeded(method.declaringType().name(), stack, target, typeCache), stack, target, typeCache, subtypeChecker);
		overloadChecker.setMethod(method);
		ArrayList<ArrayList<TypedExpression>> newArguments = new ArrayList<ArrayList<TypedExpression>>(arguments.size());
		ArrayList<TypeConstraint> argConstraints = new ArrayList<TypeConstraint>(arguments.size());
		for (int i = 0; i < arguments.size(); i++) {
			Expression curArg = (Expression)arguments.get(i);
			expandEquivalencesRec(curArg, newlyExpanded);
			ArrayList<TypedExpression> allCurArgPossibilities = new ArrayList<TypedExpression>();
			IJavaType argType = EclipseUtils.getTypeAndLoadIfNeeded((String)method.argumentTypeNames().get(i), stack, target, typeCache);
			TypeConstraint argConstraint = new SupertypeBound(argType);
			for (TypedExpression arg : getEquivalentExpressions(curArg, argConstraint))
				if (getDepth(arg.getExpression()) < curDepth) {
					if (overloadChecker.needsCast(argType, arg.getType(), newArguments.size()))  // If the method is overloaded, when executing the expression we might get "Ambiguous call" compile errors, so we put in a cast to remove the ambiguity.
						arg = ExpressionMaker.makeCast(arg, argType, arg.getValue(), valueCache, thread);
					allCurArgPossibilities.add(arg);
				}
			newArguments.add(allCurArgPossibilities);
			argConstraints.add(argConstraint);
		}
		pruneManyArgCalls(newArguments, curDepth, curDepth - 1, method.name());
		List<Expression> newCalls = new ArrayList<Expression>();
		for (Expression e : getEquivalentExpressionsOrGiven(expression, new MethodConstraint(name, UnknownConstraint.getUnknownConstraint(), argConstraints)))
			if (e instanceof TypeLiteral || getDepth(e) < curDepth)
				makeAllCalls(method, name, e, newCalls, newArguments, new ArrayList<TypedExpression>(newArguments.size()), curDepth);
		for (Expression newCall : newCalls)
			addIfNew(curEquivalences, new EvaluatedExpression(newCall, type, value), valued);
	}
    
    /**
     * Class that checks whether an expression is unique.
     * An expression is unique iff it has no repeated variables
     * or integer constants.
     * TODO: Note that this disallows good expressions such as (x*2)-(y/2),
     * but also bad ones like (x+1)+1.
     */
    private static class UniqueASTChecker extends ASTVisitor {
    	
    	private final Set<String> seen;
    	private boolean isUnique;
    	
    	public UniqueASTChecker(String lhsVarName) {
    		seen = new HashSet<String>();
    		if (lhsVarName != null)
    			seen.add(lhsVarName);
    		isUnique = true;
    	}
    	
    	@Override
    	public boolean visit(SimpleName node) {
    		visit(node.getIdentifier());
    		return true;
    	}
    	
    	@Override
    	public boolean visit(NumberLiteral node) {
    		visit(node.getToken());
    		return true;
    	}
    	
    	// Casts have type names that are SimpleNames inside SimpleTypes, but we don't want to count those towards non-uniqueness, so we don't visit them. 
    	@Override
    	public boolean visit(SimpleType node) {
    		return false;
    	}
    	
    	private void visit(String s) {
    		if (seen.contains(s))
    			isUnique = false;
    		else
    			seen.add(s);
    	}
        
        /**
         * Checks whether a given expression is unique wrt UniqueASTChecker.
         * @param e The expression to check.
         * @param varName The name of the variable being assigned.
         * @return Whether or not the expression is unique wrt UniqueASTChecker.
         */
        public static boolean isUnique(Expression e, String varName) {
        	return isUnique(e, new UniqueASTChecker(varName));
        }
        
        /**
         * Checks whether a given expression is unique wrt UniqueASTChecker.
         * @param e The expression to check.
    	 * @param checker The checker to use.
         * @return Whether or not the expression is unique wrt UniqueASTChecker.
         */
        public static boolean isUnique(Expression e, UniqueASTChecker checker) {
        	if (!checker.isUnique)
        		return false;
        	e.accept(checker);
        	return checker.isUnique;
        }
    	
    }
    
    /**
     * Class that checks whether a method contains a call to
     * one of a few specified methods.
     */
    private static class NamedMethodChecker extends ASTVisitor {
    	
    	private boolean hasNamedMethod;
    	
    	public NamedMethodChecker() {
    		hasNamedMethod = false;
    	}

		@Override
		public boolean visit(MethodInvocation node) {
			String name = node.getName().getIdentifier();
			if (isNamedMethod(name, node.getExpression()))
				hasNamedMethod = true;
			return !hasNamedMethod;
		}
		
		public static boolean isNamedMethod(String name, Expression receiver) {
			// We do want to include Integer.valueOf and friends.
			return name.equals("toString") || (name.equals("valueOf") && "java.lang.String".equals(receiver.toString()))
					|| (name.equals("format") && "java.lang.String".equals(receiver.toString()))
					|| name.equals("deepToString") || name.equals("compareTo") || name.equals("compareToIgnoreCase") || name.equals("compare");
		}
		
		/**
		 * Checks whether the given expression contains a call to
		 * one of a few specified methods.
		 * @param e The expression to check.
		 * @return Whether the given expression contains a call
		 * to one of a few specified methods.
		 */
		public static boolean hasNamedMethod(Expression e) {
			return hasNamedMethod(e, new NamedMethodChecker());
		}
		
		/**
		 * Checks whether the given expression contains a call to
		 * one of a few specified methods.
		 * @param e The expression to check.
		 * @param checker The checker to use.
		 * @return Whether the given expression contains a call
		 * to one of a few specified methods.
		 */
		public static boolean hasNamedMethod(Expression e, NamedMethodChecker checker) {
			if (checker.hasNamedMethod)
				return true;
	    	e.accept(checker);
	    	return checker.hasNamedMethod;
		}
		
    }
    
    /**
     * Gets the depth of the given expression including
     * heuristics including whether the expression is unique.
     * @param expr The expression whose depth we want.
     * @return The depth of the given expression.
     */
    private int getDepth(TypedExpression expr) {
		return getDepth(expr.getExpression());
    }

    /**
     * Gets the depth of the given expression including
     * heuristics including whether the expression is unique.
     * @param expr The expression whose depth we want.
     * @return The depth of the given expression.
     */
    private int getDepth(Expression expr) {
    	if (expr == null)
    		return 0;
    	Object depthProp = expr.getProperty("realDepth");
    	if (depthProp != null)
    		return ((Integer)depthProp).intValue();
		int depth = getDepthImpl(expr) + (UniqueASTChecker.isUnique(expr, varName) ? 0 : 1) + (NamedMethodChecker.hasNamedMethod(expr) ? 1 : 0);
		expr.setProperty("realDepth", depth);
		return depth;
    }
    
    /**
     * Gets the depth of a call with the given receiver,
     * arguments, and method name.  We special case this
     * because constructing a new expression can be
     * expensive.
     * @param receiver The receiver of the call.
     * @param args The arguments to the call.
     * @param methodName The name of the method being called.
     * @return The depth of a call with the given receiver,
     * arguments, and method name.
     */
    private int getDepthOfCall(Expression receiver, ArrayList<? extends TypedExpression> args, String methodName) {
    	int depth = getDepthImpl(receiver);
    	for (TypedExpression arg: args)
    		depth = Math.max(depth, getDepthImpl(arg.getExpression()));
    	UniqueASTChecker uniqueChecker = new UniqueASTChecker(varName);
    	NamedMethodChecker namedMethodChecker = new NamedMethodChecker();
    	if (receiver != null) {
    		UniqueASTChecker.isUnique(receiver, uniqueChecker);
			NamedMethodChecker.hasNamedMethod(receiver, namedMethodChecker);
    	}
    	for (TypedExpression arg: args) {
    		UniqueASTChecker.isUnique(arg.getExpression(), uniqueChecker);
			NamedMethodChecker.hasNamedMethod(arg.getExpression(), namedMethodChecker);
    	}
    	return depth + 1 + (uniqueChecker.isUnique ? 0 : 1) + (namedMethodChecker.hasNamedMethod || NamedMethodChecker.isNamedMethod(methodName, receiver) ? 1 : 0);
    }

    /**
     * Gets the depth of the given expression.
     * This should not be called by clients; please use
     * getDepth instead.
     * @param expr The expression whose depth we want.
     * @return The depth of the given expression.
     */
    private static int getDepthImpl(Expression expr) {
    	if (expr == null)
    		return 0;
    	Object depthProp = expr.getProperty("depth");
    	if (depthProp != null)
    		return ((Integer)depthProp).intValue();
    	int depth;
    	if (expr instanceof NumberLiteral || expr instanceof BooleanLiteral || expr instanceof Name || expr instanceof ThisExpression || expr instanceof NullLiteral || expr instanceof TypeLiteral)
			depth = 0;
    	else if (expr instanceof ParenthesizedExpression)
    		depth = getDepthImpl(((ParenthesizedExpression)expr).getExpression());
		else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			return Math.max(getDepthImpl(infix.getLeftOperand()), getDepthImpl(infix.getRightOperand())) + 1;
		} else if (expr instanceof ArrayAccess) {
			ArrayAccess array = (ArrayAccess)expr;
			depth = Math.max(getDepthImpl(array.getArray()), getDepthImpl(array.getIndex())) + 1;
		} else if (expr instanceof FieldAccess) {
			depth = getDepthImpl(((FieldAccess)expr).getExpression()) + 1;
		} else if (expr instanceof PrefixExpression) {
			depth = getDepthImpl(((PrefixExpression)expr).getOperand()) + 1;
		} else if (expr instanceof MethodInvocation) {
			MethodInvocation call = (MethodInvocation)expr;
			int maxChildDepth = call.getExpression() == null ? 0 : getDepthImpl(call.getExpression());
			for (int i = 0; i < call.arguments().size(); i++) {
				Expression curArg = (Expression)call.arguments().get(i);
				int curArgDepth = getDepthImpl(curArg);
				if (curArgDepth > maxChildDepth)
					maxChildDepth = curArgDepth;
			}
			depth = maxChildDepth + 1;
		} else if (expr instanceof ClassInstanceCreation) {
			ClassInstanceCreation call = (ClassInstanceCreation)expr;
			int maxChildDepth = call.getExpression() == null ? 0 : getDepthImpl(call.getExpression());
			for (int i = 0; i < call.arguments().size(); i++) {
				Expression curArg = (Expression)call.arguments().get(i);
				int curArgDepth = getDepthImpl(curArg);
				if (curArgDepth > maxChildDepth)
					maxChildDepth = curArgDepth;
			}
			depth = maxChildDepth + 1;
		} else if (expr instanceof CastExpression) {
			depth = getDepthImpl(((CastExpression)expr).getExpression());
		} else
			throw new RuntimeException("Unexpected Expression " + expr.toString());
    	expr.setProperty("depth", depth);
    	return depth;
    }
    
	/*private void printEquivalenceInfo() {
    	for (Map.Entry<Value, ArrayList<EvaluatedExpression>> equivClass: equivalences.entrySet()) {
    		System.out.println(equivClass.getKey().toString().replace("\n", "\\n") + " -> " + equivClass.getValue().size() + " (" + equivClass.getValue().toString().replace("\n", "\\n") + ")");
    	}
    	System.out.println("Buckets: ");
    	Map<Integer, ArrayList<Value>> buckets = new HashMap<Integer, ArrayList<Value>>();
    	for (Map.Entry<Value, ArrayList<EvaluatedExpression>> equivClass: equivalences.entrySet())
    		Utils.addToMap(buckets, equivClass.getValue().size(), equivClass.getKey());
    	for (Integer bucket: new java.util.TreeSet<Integer>(buckets.keySet()))
    		System.out.println(bucket + " -> " + buckets.get(bucket).size() + " (" + buckets.get(bucket).toString().replace("\n", "\\n") + ")");
    }*/

}
