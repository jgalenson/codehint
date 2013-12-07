package codehint.exprgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import codehint.ast.ASTNode;
import codehint.ast.ASTVisitor;
import codehint.ast.ArrayAccess;
import codehint.ast.BooleanLiteral;
import codehint.ast.CastExpression;
import codehint.ast.ClassInstanceCreation;
import codehint.ast.Expression;
import codehint.ast.FieldAccess;
import codehint.ast.InfixExpression;
import codehint.ast.IntLiteral;
import codehint.ast.MethodInvocation;
import codehint.ast.Name;
import codehint.ast.NullLiteral;
import codehint.ast.NumberLiteral;
import codehint.ast.ParentASTVisitor;
import codehint.ast.ParenthesizedExpression;
import codehint.ast.PlaceholderExpression;
import codehint.ast.PrefixExpression;
import codehint.ast.SimpleName;
import codehint.ast.SimpleType;
import codehint.ast.StringLiteral;
import codehint.ast.ThisExpression;
import codehint.ast.TypeLiteral;

import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaInterfaceType;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.internal.debug.core.model.JDIStackFrame;
import org.eclipse.jdt.internal.debug.core.model.JDIType;

import codehint.DataCollector;
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
import codehint.exprgen.typeconstraint.FieldConstraint;
import codehint.exprgen.typeconstraint.MethodConstraint;
import codehint.exprgen.typeconstraint.SupertypeBound;
import codehint.exprgen.typeconstraint.TypeConstraint;
import codehint.exprgen.typeconstraint.UnknownConstraint;
import codehint.property.Property;
import codehint.property.ValueProperty;
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
	
	private final IJavaDebugTarget target;
	private final IJavaStackFrame stack;
	private final IJavaThread thread;
    private final SideEffectHandler sideEffectHandler;
	private final ExpressionMaker expressionMaker;
	private final SubtypeChecker subtypeChecker;
	private final TypeCache typeCache;
	private final ValueCache valueCache;
	private final EvaluationManager evalManager;
    private final StaticEvaluator staticEvaluator;
    private final Weights weights;
	private final IJavaReferenceType thisType;
	private final IJavaProject project;
	private final IJavaType intType;
	private final IJavaType booleanType;
	private final IJavaType objectType;
	private final Expression zero;
	private final IJavaValue oneValue;
	private final Expression one;
	private final Map<Integer, Integer> realDepths;
	private final Map<Integer, Boolean> hasBadMethodsFields;
	private final Map<Integer, Boolean> hasBadConstants;
	// Cache the generated expressions
	//private final Map<Pair<TypeConstraint, Integer>, Pair<ArrayList<Expression>, ArrayList<Expression>>> cachedExprs;

	private TypeConstraint typeConstraint;
	private String varName;
	private IProgressMonitor curMonitor;
	private Map<Set<Effect>, Map<Result, ArrayList<Expression>>> equivalences;
	private Map<Method, Integer> prunedDepths;  // Maps a method to the first consecutive depth at which we pruned calls to it.
	private Set<Method> newlyUnpruneds;  // A set of the methods that were pruned at the previous depth that are not pruned at the current depth.
	private IImportDeclaration[] imports;
	private Set<String> importsSet;
	private Set<String> staticAccesses;
	private int numFailedDowncasts;
	private Map<String, Integer> helpfulTypes;
	private Map<String, Integer> uniqueValuesSeenForType;
	private Set<String> downcastTypes;
	
	public ExpressionGenerator(IJavaDebugTarget target, IJavaStackFrame stack, SideEffectHandler sideEffectHandler, ExpressionMaker expressionMaker, SubtypeChecker subtypeChecker, TypeCache typeCache, ValueCache valueCache, EvaluationManager evalManager, StaticEvaluator staticEvaluator, Weights weights) {
		this.target = target;
		this.stack = stack;
		this.thread = (IJavaThread)stack.getThread();
		this.sideEffectHandler = sideEffectHandler;
		this.expressionMaker = expressionMaker;
		this.subtypeChecker = subtypeChecker;
		this.typeCache = typeCache;
		this.valueCache = valueCache;
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
		this.objectType = EclipseUtils.getFullyQualifiedType("java.lang.Object", stack, target, typeCache);
		this.zero = expressionMaker.makeInt(0, target.newValue(0), intType, thread);
		this.oneValue = target.newValue(1);
		this.one = expressionMaker.makeInt(1, oneValue, intType, thread);
		this.realDepths = new HashMap<Integer, Integer>();
		this.hasBadMethodsFields = new HashMap<Integer, Boolean>();
		this.hasBadConstants = new HashMap<Integer, Boolean>();
		//this.cachedExprs = new HashMap<Pair<TypeConstraint, Integer>, Pair<ArrayList<Expression>, ArrayList<Expression>>>();
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
	public ArrayList<Expression> generateExpression(Property property, TypeConstraint typeConstraint, String varName, boolean searchConstructors, boolean searchOperators, SynthesisDialog synthesisDialog, IProgressMonitor monitor, int maxExprDepth) {
		monitor.beginTask("Expression generation and evaluation", IProgressMonitor.UNKNOWN);
		
		try {
			this.typeConstraint = typeConstraint;
			this.varName = varName;
			this.equivalences = new HashMap<Set<Effect>, Map<Result, ArrayList<Expression>>>();
			this.prunedDepths = new HashMap<Method, Integer>();
			this.newlyUnpruneds = new HashSet<Method>();
			this.imports = ((ICompilationUnit)project.findElement(new Path(stack.getSourcePath()))).getImports();
			this.importsSet = new HashSet<String>(imports.length);
			for (IImportDeclaration imp : imports)
				this.importsSet.add(imp.getElementName());
			this.numFailedDowncasts = 0;
			this.helpfulTypes = null;
			//this.helpfulTypes = getHelpfulTypesMap(maxExprDepth, monitor);
			this.uniqueValuesSeenForType = new HashMap<String, Integer>();
			this.downcastTypes = new HashSet<String>();
			
			ArrayList<Expression> results = genAllExprs(maxExprDepth, property, searchConstructors, searchOperators, synthesisDialog, monitor);

			/*for (Map.Entry<Value, ArrayList<Expression>> entry : equivalences.entrySet())
				System.out.println(entry.getKey() + " -> " + entry.getValue().toString());
			int totalNumEquivExprs = 0;
			for (ArrayList<Expression> es: equivalences.values())
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
	 * @param searchConstructors Whether or not to search constructors.
	 * @param searchOperators Whether or not to search operator expressions.
	 * @param synthesisDialog The synthesis dialog to pass the valid expressions,
	 * or null if we should not pass anything.
	 * @param monitor Progress monitor.
	 * @return all expressions up to the given depth whose result in the
	 * current stack frame satisfies the current pdspec.
	 * @throws DebugException 
	 */
	private ArrayList<Expression> genAllExprs(int maxDepth, Property property, boolean searchConstructors, boolean searchOperators, final SynthesisDialog synthesisDialog, IProgressMonitor monitor) throws DebugException {
		long startTime = System.currentTimeMillis();
		int initNumCrashes = getNumCrashes();
		
		ArrayList<Expression> curLevel = null;
		ArrayList<Expression> nextLevel = new ArrayList<Expression>(0);
		for (int depth = 0; depth <= maxDepth; depth++) {
			filterDuplicates(nextLevel);
			/*System.out.println("Depth " + depth + " has " + nextLevel.size() + " inputs:");
			for (Expression e: nextLevel)
				System.out.println(Utils.truncate(e.toString(), 100));
			printEquivalenceInfo();*/
			curLevel = genOneLevel(nextLevel, depth, maxDepth, property, searchConstructors, searchOperators, monitor);
			if (depth < maxDepth) {
				evalManager.cacheMethodResults(nextLevel);
				nextLevel = evaluateExpressions(curLevel, null, null, monitor, depth, maxDepth);
			}
		}
		ArrayList<Expression> results = evaluateExpressions(curLevel, property, synthesisDialog, monitor, maxDepth, maxDepth);
		int numEvaled = getNumExprsSearched() - initNumCrashes;
		
		//printEquivalenceInfo();
		//System.out.println("Took " + (System.currentTimeMillis() - startTime) + " milliseconds pre-expansion.");
		
		// Expand equivalences.
		final ArrayList<Expression> extraResults = expandEquivalences(results, maxDepth, monitor);
    	if (synthesisDialog != null)
    		synthesisDialog.addExpressions(extraResults);
		results.addAll(extraResults);

		//getMaxLineInfo();
		
		long time = System.currentTimeMillis() - startTime; 
		int numSearched = getNumExprsSearched() - initNumCrashes;
		EclipseUtils.log("Generated " + numSearched + " expressions (of which " + numEvaled + " were evaluated) at depth " + maxDepth + " and found " + results.size() + " valid expressions and took " + time + " milliseconds.");
		DataCollector.log("gen", "spec=" + (property == null ? "" : property.toString()), "depth=" + maxDepth, "evaled=" + numEvaled, "gen=" + numSearched, "valid=" + results.size(), "time=" + time);
		
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
	 * @param maxDepth The maximum depth to search (inclusive).
	 * @return The expressions that do not crash and satisfy the
	 * given pdspec if it is non-null.
	 * @throws DebugException
	 */
	private ArrayList<Expression> evaluateExpressions(List<Expression> exprs, Property property, SynthesisDialog synthesisDialog, IProgressMonitor monitor, int depth, int maxDepth) throws DebugException {
		String taskNameSuffix = " " + (depth + 1) + "/" + (maxDepth + 1);
		ArrayList<Expression> evaluatedExprs = new ArrayList<Expression>();
		ArrayList<Expression> unevaluatedExprs = new ArrayList<Expression>();
		
    	for (Expression e : exprs) {
    		IJavaValue value = expressionMaker.getValue(e, Collections.<Effect>emptySet());
    		if (value != null && !"V".equals(value.getSignature()))
    			evaluatedExprs.add(e);
    		else
    			unevaluatedExprs.add(e);
    	}
    	
    	//System.out.println("Generated " + (unevaluatedExprs.size() + getNumExprsSearched()) + " total expressions at depth " + depth + ", of which " + unevaluatedExprs.size() + " still need to be evaluated and " + (evalManager.getNumCrashes() + staticEvaluator.getNumCrashes() + expressionMaker.getNumCrashes()) + " crashed.");

		/*for (Expression e: evaluatedExprs)
			System.out.println(Utils.truncate(e.toString(), 100));*/
    	
    	if (property != null && unevaluatedExprs.isEmpty() && !EvaluationManager.canEvaluateStatically(property))
			evalManager.cacheMethodResults(evaluatedExprs);
		ArrayList<Expression> results = evalManager.evaluateExpressions(evaluatedExprs, property, getVarType(), synthesisDialog, monitor, taskNameSuffix);
    	if (unevaluatedExprs.size() > 0) {

    		/*int printCount = 0;
	    	for (Expression call: unevaluatedExprs) {
	    		System.out.println(call);
	    		if (++printCount % 1000 == 0)
	    			printCount = printCount + 1 - 1;
	    		Method method = expressionMaker.getMethod(call);
	    		String name = method.name();
	    		List<?> args = call instanceof MethodInvocation ? ((MethodInvocation)call).arguments() : ((ClassInstanceCreation)call).arguments();
				String argString = "";
				for (Object e: args) {
					if (!argString.isEmpty())
						argString += ", ";
					IJavaValue value = expressionMaker.getExpressionValue((Expression)e);
					argString += value != null ? value.toString().replace("\n", "\\n") : "??";
				}
				if (call instanceof ClassInstanceCreation)
					System.out.println("new " + ((ClassInstanceCreation)call).getType() + "_" + method.signature() + "(" + argString + ")");
				else if (method.isStatic())
					System.out.println(((MethodInvocation)call).getExpression() + "." + name + "_" + method.signature() + "(" + argString + ")");
				else {
					Expression receiver = ((MethodInvocation)call).getExpression();
					if (receiver == null)
						receiver = expressionMaker.makeThis(stack.getThis(), thisType, valueCache, thread);
					System.out.println(expressionMaker.getExpressionValue(receiver) + "." + name + "_" + method.signature() + "(" + argString + ")");
				}
	    	}*/
    		
    		// We evaluate these separately because we need to set their value and add them to our equivalent expressions map; we have already done this for those whose value we already knew.
    		ArrayList<Expression> result = evalManager.evaluateExpressions(unevaluatedExprs, property, getVarType(), synthesisDialog, monitor, taskNameSuffix);
    		for (Expression e: result)
    			addEquivalentExpression(e, Collections.<Effect>emptySet());
    		results.addAll(result);
    	}
    	return results;
	}
	
	/**
	 * Returns the number of expressions that have currently
	 * been searched.  Note that this does not contain things
	 * that did not crash but have not been stored in an
	 * equivalence class.
	 * @return The number of expressions that have currently
	 * been searched.
	 */
	private int getNumExprsSearched() {
		Map<Result, ArrayList<Expression>> equivs = equivalences.get(Collections.emptySet());
		return (equivs == null ? 0 : Utils.getNumValues(equivs)) + getNumCrashes() + numFailedDowncasts;
	}
	
	/**
	 * Returns the number of expressions that have crashed.
	 * @return The number of expressions that have crashed.
	 */
	private int getNumCrashes() {
		return evalManager.getNumCrashes() + staticEvaluator.getNumCrashes() + expressionMaker.getNumCrashes();
	}
	
	/**
	 * Removes expressions with duplicate values from the given list.
	 * Note that this modifies the input list.
	 * @param exprs The list of expressions.
	 */
	private void filterDuplicates(ArrayList<Expression> exprs) {
		Set<Result> results = new HashSet<Result>();
		Iterator<Expression> it = exprs.iterator();
		while (it.hasNext()) {
			Expression expr = it.next();
			Result result = expressionMaker.getResult(expr, Collections.<Effect>emptySet());
			if (results.contains(result)) {
				//assert equivalences.get(expr.getResult()).contains(expr);
				it.remove();
			} else
				results.add(result);
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
	 * @param searchConstructors Whether or not to search constructors.
	 * @param searchOperators Whether or not to search operator expressions.
	 * @param monitor The progress monitor.  The caller should
	 * not allocate a new progress monitor; this method will.
	 * @return The expressions of the given depth.
	 */
	private ArrayList<Expression> genOneLevel(List<Expression> nextLevel, int depth, int maxDepth, Property property, boolean searchConstructors, boolean searchOperators, IProgressMonitor monitor) {
		try {
			ArrayList<Expression> curLevel = new ArrayList<Expression>();
			IJavaType[] constraintTypes = typeConstraint.getTypes(stack, target, typeCache);
			String taskName = "Expression generation " + (depth + 1) + "/" + (maxDepth + 1);
    		
    		// Get constants (but only at the top-level).
			// We add these directly to curLevel and not equivalences because we don't want to substitute them anywhere else.
			if (depth == maxDepth && property instanceof ValueProperty) {
				IJavaValue demonstration = ((ValueProperty)property).getValue();
	    		if (ExpressionMaker.isInt(demonstration.getJavaType()) && !"0".equals(demonstration.toString()))
	    			curLevel.add(expressionMaker.makeNumber(demonstration.toString(), target.newValue(Integer.parseInt(demonstration.toString())), intType, thread));
	    		if (ExpressionMaker.isBoolean(demonstration.getJavaType()))
	    			curLevel.add(expressionMaker.makeBoolean(Boolean.parseBoolean(demonstration.toString()), target.newValue(Boolean.parseBoolean(demonstration.toString())), booleanType, thread));
			}
    		
    		// Copy over the stuff from the next level.
    		for (Expression e : nextLevel) {
    			if (depth < maxDepth || isHelpfulType(e.getStaticType(), depth, maxDepth))  // Note that this relies on the fact that something helpful for depth>=2 will be helpful for depth>=1.  If this changes, we'll need to call it again.
    				curLevel.add(e);
    			else if (depth == maxDepth && isHelpfulWithDowncast(expressionMaker.getValue(e, Collections.<Effect>emptySet())))  // If we're at the maximum depth and an expression is helpful with a downcast, downcast it.
    				curLevel.add(downcast(e));
    		}
    		
    		if (depth == 0) {
        		// Add zero and null.
    			boolean hasObject = false;
    			//boolean hasInt = false;
    			for (IJavaType type: constraintTypes) {
    				if (EclipseUtils.isObject(type)) {
    					hasObject = true;
    					break;
    				}// else if (ExpressionMaker.isInt(type))
                    //    hasInt = true;
    			}
    			//if (depth < maxDepth || hasInt)
                //    addUniqueExpressionToList(curLevel, zero, depth);
    			if (depth < maxDepth || (hasObject && !(typeConstraint instanceof MethodConstraint) && !(typeConstraint instanceof FieldConstraint)))  // If we have a method or field constraint, we can't have null.
    				addUniqueExpressionToList(curLevel, expressionMaker.makeNull(thread), depth, maxDepth);
	    		addLocals(depth, maxDepth, curLevel);
				// Add "this" if we're not in a static context.
				if (isHelpfulType(thisType, depth, maxDepth) && !stack.isStatic())
					addUniqueExpressionToList(curLevel, expressionMaker.makeThis(stack.getThis(), thisType, thread), depth, maxDepth);
    		} else {
				if (!sideEffectHandler.isHandlingSideEffects())  // TODO: Without this, the below line makes us crash on S1 at depth 2 with effect handling on.  Why?  But it's still helpful when effects are off (speeds up S1 at depth 1 noticeably), so I'm keeping it.
					loadTypesFromMethods(nextLevel, imports);
	    		// Add calls to the desired type's (and those of its subtypes in the same package (for efficiency)) constructors (but only at the top-level).
	    		if (searchConstructors && depth == maxDepth) {
	    			for (IJavaType type: constraintTypes) {
	    				List<IJavaType> subtypes = EclipseUtils.getPublicSubtypesInSamePackage(type, project, stack, target, typeCache, monitor, taskName);
	        			curMonitor = SubMonitor.convert(monitor, taskName + ": calling constructors", subtypes.size());
	    				for (IJavaType subtype: subtypes) {
	    					addMethodCalls(new PlaceholderExpression(subtype), nextLevel, curLevel, depth, maxDepth, null);
	    					curMonitor.worked(1);
	    				}
	    			}
	    		}
    			Set<IJavaReferenceType> objectInterfaceTypes = new HashSet<IJavaReferenceType>();
				this.staticAccesses = new HashSet<String>();  // Reset the list of static accesses to ensure that while we don't generate duplicate ones in a given depth, we can generate them again in different depths.
    			curMonitor = SubMonitor.convert(monitor, taskName, nextLevel.size() * 2 + imports.length);
    			addLocals(depth, maxDepth, curLevel);
    			// Get binary ops.
    			// We use string comparisons to avoid duplicates, e.g., x+y and y+x.
    			for (Expression l : nextLevel) {
    				Result lResult = expressionMaker.getResult(l, Collections.<Effect>emptySet());
    				Value lWrapperValue = lResult.getValue();
    				if (isConstantField(l, expressionMaker))
    					l = getRepresentative(l, equivalences.get(Collections.<Effect>emptySet()).get(lResult), depth);
    				for (Expression r: getUniqueExpressions(l, lResult.getEffects(), intType, depth, nextLevel)) {
    					Value rWrapperValue = expressionMaker.getResult(r, Collections.<Effect>emptySet()).getValue();
    					IJavaValue rValue = rWrapperValue.getValue();
        				if (curMonitor.isCanceled())
        					throw new OperationCanceledException();
    					if (lWrapperValue.equals(rWrapperValue) && l.toString().compareTo(r.toString()) > 0) {
    						Expression tmp = l;  // Swap l and r if they have the same value (if not, we will consider both orderings) so that l's toString lexographically precedes r's so that we always consider e.g., l*r. 
    						l = r;
    						r = tmp;
    					}
    					// Arithmetic operations, e.g., +,*.
    					if (searchOperators && ExpressionMaker.isInt(l.getStaticType()) && ExpressionMaker.isInt(r.getStaticType()) && isHelpfulType(intType, depth, maxDepth)
    							&& !isConstant(l) && !isConstant(r)) {
    						if (mightNotCommute(l, r) || l.toString().compareTo(r.toString()) < 0)
    							addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(l, InfixExpression.Operator.PLUS, r, intType, thread), depth, maxDepth);
    						if (mightNotCommute(l, r) || l.toString().compareTo(r.toString()) <= 0)
    							addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(l, InfixExpression.Operator.TIMES, r, intType, thread), depth, maxDepth);
    						if ((mightNotCommute(l, r) || l.toString().compareTo(r.toString()) != 0)
    								&& !(r instanceof PrefixExpression && ((PrefixExpression)r).getOperator() == PrefixExpression.Operator.MINUS))
    							addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(l, InfixExpression.Operator.MINUS, r, intType, thread), depth, maxDepth);
    						if ((mightNotCommute(l, r) || l.toString().compareTo(r.toString()) != 0)
    								&& (rValue == null || sideEffectHandler.isHandlingSideEffects() || !rValue.getValueString().equals("0")))  // Don't divide by things we know are 0.
    							addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(l, InfixExpression.Operator.DIVIDE, r, intType, thread), depth, maxDepth);
    					}
    					// Integer comparisons, e.g., ==,<.
    					if (searchOperators && isHelpfulType(booleanType, depth, maxDepth) && ExpressionMaker.isInt(l.getStaticType()) && ExpressionMaker.isInt(r.getStaticType()))
    						if ((mightNotCommute(l, r) || l.toString().compareTo(r.toString()) < 0)
    								&& (!(l instanceof PrefixExpression) || !(r instanceof PrefixExpression)))
    							for (InfixExpression.Operator op : INT_COMPARE_OPS)
    								addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(l, op, r, booleanType, thread), depth, maxDepth);
    					// Array access, a[i].
    					if (l.getStaticType() instanceof IJavaArrayType && ExpressionMaker.isInt(r.getStaticType())) {
    						IJavaType elemType = ExpressionMaker.getArrayElementType(l);
    						if (elemType != null && (isHelpfulType(elemType, depth, maxDepth) || mightBeHelpfulWithDowncast(elemType))) {
    							// Get the value if we can and skip things with null arrays or out-of-bounds indices.
    							Expression accessExpr = expressionMaker.makeArrayAccess(l, r, thread);
    							if (accessExpr != null)
    								addUniqueExpressionToList(curLevel, accessExpr, depth, maxDepth);
    						}
    					}
    				}
					// Boolean connectives, &&,||.
    				if (searchOperators && isHelpfulType(booleanType, depth, maxDepth) && ExpressionMaker.isBoolean(l.getStaticType()))
    					for (Expression r: getUniqueExpressions(l, lResult.getEffects(), booleanType, depth, nextLevel)) {
        					if (lWrapperValue.equals(expressionMaker.getResult(r, Collections.<Effect>emptySet()).getValue()) && l.toString().compareTo(r.toString()) > 0) {
        						Expression tmp = l;
        						l = r;
        						r = tmp;
        					}
    						if (ExpressionMaker.isBoolean(r.getStaticType()))
    							if (mightNotCommute(l, r) || l.toString().compareTo(r.toString()) < 0)
    								for (InfixExpression.Operator op : BOOLEAN_COMPARE_OPS)
    									addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(l, op, r, booleanType, thread), depth, maxDepth);
    					}
					// Object/array comparisons
    				if (searchOperators && isHelpfulType(booleanType, depth, maxDepth) && l.getStaticType() instanceof IJavaReferenceType)
    					for (Expression r: getUniqueExpressions(l, lResult.getEffects(), objectType, depth, nextLevel)) {
        					if (lWrapperValue.equals(expressionMaker.getResult(r, Collections.<Effect>emptySet()).getValue()) && l.toString().compareTo(r.toString()) > 0) {
        						Expression tmp = l;
        						l = r;
        						r = tmp;
        					}
    						if (r.getStaticType() instanceof IJavaReferenceType
    								&& (subtypeChecker.isSubtypeOf(l.getStaticType(), r.getStaticType()) || subtypeChecker.isSubtypeOf(r.getStaticType(), l.getStaticType())))
    							if (mightNotCommute(l, r) || l.toString().compareTo(r.toString()) < 0)
    								for (InfixExpression.Operator op : REF_COMPARE_OPS)
    									addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(l, op, r, booleanType, thread), depth, maxDepth);
    					}
    				curMonitor.worked(1);
    			}
    			// Get unary ops
    			for (Expression e : nextLevel) {
    				if (curMonitor.isCanceled())
    					throw new OperationCanceledException();
    				IJavaValue eValue = expressionMaker.getValue(e, Collections.<Effect>emptySet());
    				// Arithmetic with constants.
    				if (searchOperators && ExpressionMaker.isInt(e.getStaticType()) && isHelpfulType(intType, depth, maxDepth)
    						&& !isConstant(e)) {
    					addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(e, InfixExpression.Operator.PLUS, one, intType, thread), depth, maxDepth);
    					//addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(e, InfixExpression.Operator.TIMES, two, intType, valueCache, thread, target), depth, maxDepth);
    					addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(e, InfixExpression.Operator.MINUS, one, intType, thread), depth, maxDepth);
    					//addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(e, InfixExpression.Operator.DIVIDE, two, intType, valueCache, thread, target), depth, maxDepth);
    				}
    				// Comparisons with constants.
    				if (searchOperators && ExpressionMaker.isInt(e.getStaticType()) && isHelpfulType(booleanType, depth, maxDepth)
    						&& !isConstant(e)) {
    					addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(e, InfixExpression.Operator.LESS, zero, booleanType, thread), depth, maxDepth);
    					addUniqueExpressionToList(curLevel, expressionMaker.makeInfix(e, InfixExpression.Operator.GREATER, zero, booleanType, thread), depth, maxDepth);
    				}
    				// Field accesses to non-static fields from non-static scope.
    				if (e.getStaticType() instanceof IJavaClassType
    						&& (eValue == null || !eValue.isNull()))  // Skip things we know are null dereferences.
    					addFieldAccesses(e, curLevel, depth, maxDepth, null);
    				// Boolean negation.
    				if (searchOperators && ExpressionMaker.isBoolean(e.getStaticType()) && isHelpfulType(booleanType, depth, maxDepth)
    						&& !(e instanceof PrefixExpression) && !(e instanceof InfixExpression)
    						&& !isConstant(e))  // Disallow things like !(x < y) and !(!x).
    					addUniqueExpressionToList(curLevel, expressionMaker.makePrefix(e, PrefixExpression.Operator.NOT, thread), depth, maxDepth);
    				// Integer negation.
    				if (searchOperators && ExpressionMaker.isInt(e.getStaticType()) && isHelpfulType(intType, depth, maxDepth)
    						&& !(e instanceof PrefixExpression) && !(e instanceof InfixExpression)  // Disallow things like -(-x) and -(x + y).
    						&& !isConstant(e) && !isConstantField(e, expressionMaker))  // Disallow things like -KeyEvent.VK_ENTER.
    					addUniqueExpressionToList(curLevel, expressionMaker.makePrefix(e, PrefixExpression.Operator.MINUS, thread), depth, maxDepth);
    				// Array length (which uses the field access AST).
    				if (e.getStaticType() instanceof IJavaArrayType && isHelpfulType(intType, depth, maxDepth)
    						&& (eValue == null || !eValue.isNull()))  // Skip things we know are null dereferences.
    					addUniqueExpressionToList(curLevel, expressionMaker.makeFieldAccess(e, "length", intType, null, thread), depth, maxDepth);
    				// Method calls to non-static methods from non-static scope.
    				if (ExpressionMaker.isObjectOrInterface(e.getStaticType())
    						&& (eValue == null || !eValue.isNull()))  // Skip things we know are null dereferences.
    					addMethodCalls(e, nextLevel, curLevel, depth, maxDepth, null);
    				// Collect the class and interface types we've seen.
    				if (ExpressionMaker.isObjectOrInterface(e.getStaticType()))
    					objectInterfaceTypes.add((IJavaReferenceType)e.getStaticType());
    				curMonitor.worked(1);
    			}
    			// Extra things
    			{
    				// Field accesses from static scope.
    				if ((stack.isStatic() || stack.isConstructor()) && !stack.getReceivingTypeName().contains("<"))  // TODO: Allow referring to generic classes (and below).
    					addFieldAccesses(expressionMaker.makeStaticName(stack.getReceivingTypeName(), thisType, thread), curLevel, depth, maxDepth, null);
    				// Method calls from static scope.
    				if ((stack.isStatic() || stack.isConstructor()) && !stack.getReceivingTypeName().contains("<"))
    					addMethodCalls(expressionMaker.makeStaticName(stack.getReceivingTypeName(), thisType, thread), nextLevel, curLevel, depth, maxDepth, null);
    				// Accesses/calls to static fields/methods.
    				for (IJavaReferenceType type : objectInterfaceTypes) {
    					String typeName = type.getName();
    					// If we have imported the type or it is an inner class of the this type, use the unqualified typename for brevity.
    					if (typeName.contains("$") && thisType.getName().equals(typeName.substring(0, typeName.lastIndexOf('$'))))
    						typeName = EclipseUtils.getUnqualifiedName(EclipseUtils.sanitizeTypename(typeName));
    					else
    						typeName = getShortestTypename(typeName);
    					addFieldAccesses(expressionMaker.makeStaticName(typeName, type, thread), curLevel, depth, maxDepth, null);
    					addMethodCalls(expressionMaker.makeStaticName(typeName, type, thread), nextLevel, curLevel, depth, maxDepth, null);
    				}
    				// Calls to static methods and fields of imported classes.
    				for (IImportDeclaration imp : imports) {
    					String fullName = imp.getElementName();
    					String shortName = EclipseUtils.getUnqualifiedName(fullName);  // Use the unqualified typename for brevity.
    					if (!imp.isOnDemand()) {  // TODO: What should we do with import *s?  It might be too expensive to try all static methods.  This ignores them.
    						if (Flags.isStatic(imp.getFlags())) {
    							String typeName = fullName.substring(0, fullName.lastIndexOf('.'));
								IJavaReferenceType importedType = (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(typeName, stack, target, typeCache);
    							if (importedType != null) {
    								// TODO: I'm currently using the full name of the type, while because of the import I could simply use the name and forego the type completely.
    								addFieldAccesses(expressionMaker.makeStaticName(typeName, importedType, thread), curLevel, depth, maxDepth, shortName);
    								addMethodCalls(expressionMaker.makeStaticName(typeName, importedType, thread), nextLevel, curLevel, depth, maxDepth, shortName);
    							} else
	    							;//System.err.println("I cannot get the class of the import " + fullName);
    						} else {
	    						IJavaReferenceType importedType = (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(fullName, stack, target, typeCache);
	    						if (importedType != null) {
	    							if (!objectInterfaceTypes.contains(importedType)) {  // We've already handled these above.
	    								addFieldAccesses(expressionMaker.makeStaticName(shortName, importedType, thread), curLevel, depth, maxDepth, null);
	    								addMethodCalls(expressionMaker.makeStaticName(shortName, importedType, thread), nextLevel, curLevel, depth, maxDepth, null);
	    							}
	    						} else
	    							;//System.err.println("I cannot get the class of the import " + fullName);
    						}
    					}
    					curMonitor.worked(1);
    				}
    			}
    			for (Method method: newlyUnpruneds)
    				prunedDepths.remove(method);
    			newlyUnpruneds.clear();
    		}
    		
    		/*System.out.println("Exploring " + result.size() + " possible expressions.");
    		for (Expression e : result)
    			System.out.println(e.toString());*/
    		return curLevel;
		} catch (DebugException e) {
			e.printStackTrace();
        	EclipseUtils.showError("Error", "An error occurred during expression generation.", e);
			throw new RuntimeException("I cannot compute all valid expressions.");
		} catch (JavaModelException e) {
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
	private void addLocals(int depth, int maxDepth, ArrayList<Expression> curLevel) throws DebugException {
		for (IJavaVariable l : stack.getLocalVariables()) {
			IJavaType lType = EclipseUtils.getTypeOfVariableAndLoadIfNeeded(l, stack);
			if (isHelpfulType(lType, depth, maxDepth) || mightBeHelpfulWithDowncast(lType))
				addUniqueExpressionToList(curLevel, expressionMaker.makeVar(l.getName(), (IJavaValue)l.getValue(), lType, thread), depth, maxDepth);
		}
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
	 * Adds field accesses of the given expression.
	 * @param e The receiver expression.
	 * @param ops The list into which we will insert
	 * the newly-generated expressions.
	 * @param depth The current search depth.
	 * @param maxDepth The maximum search depth.
	 * @param targetName The name of the field access
	 * to add, or null if we should add any access.
	 * @throws DebugException
	 * @throws JavaModelException 
	 */
	private void addFieldAccesses(Expression e, List<Expression> ops, int depth, int maxDepth, String targetName) throws DebugException, JavaModelException {
		// We could use the public Eclipse API here, but it isn't as clean and works on objects not types, so wouldn't work with our static accesses, which we give a null value.  Note that as below with methods, we must now be careful converting between jdi types and Eclipse types. 
		//IJavaObject obj = e.getValue() != null ? (IJavaObject)e.getValue() : null;
		//Type objTypeImpl = ((JDIType)e.getType()).getUnderlyingType();
		//Type thisTypeImpl = ((JDIType)thisType).getUnderlyingType();
		boolean isStatic = expressionMaker.isStatic(e);
		//String objTypeName = isStatic ? e.toString() : objTypeImpl.name();
		for (Field field: getFields(e.getStaticType())) {
			if (!isLegalField(field, thisType) || (isStatic != field.isStatic()) || field.isSynthetic())
				continue;
			if (field.isStatic() && staticAccesses.contains(field.declaringType().name() + " " + field.name()))
				continue;
			if (targetName != null && !targetName.equals(field.name()))
				continue;
            IField ifield = EclipseUtils.getIField(field, project);
            try {
            	if (ifield != null && Flags.isDeprecated(ifield.getFlags()))
            		continue;
            } catch (JavaModelException ex) { }  // We need this for Android for some reason.
			IJavaType fieldType = EclipseUtils.getTypeAndLoadIfNeeded(field.typeName(), stack, target, typeCache);
			/*if (fieldType == null)
				System.err.println("I cannot get the class of " + objTypeImpl.name() + "." + field.name() + "(" + field.typeName() + ")");*/
			if (fieldType != null && (isHelpfulType(fieldType, depth, maxDepth) || mightBeHelpfulWithDowncast(fieldType))) {
				Expression receiver = e;
				if (e instanceof ThisExpression || e.getStaticType().equals(thisType))
					receiver = null;  // Don't use a receiver if it is null or the this type.
				else if (field.isStatic())
					receiver = expressionMaker.makeStaticName(EclipseUtils.sanitizeTypename(getShortestTypename(field.declaringType().name())), (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(field.declaringType().name(), stack, target, typeCache), thread);
				Expression fieldExpr = receiver == null ? expressionMaker.makeFieldVar(field.name(), fieldType, e, field, thread) : expressionMaker.makeFieldAccess(receiver, field.name(), fieldType, field, thread); 
				addUniqueExpressionToList(ops, fieldExpr, depth, maxDepth);
				if (field.isStatic())
					staticAccesses.add(field.declaringType().name() + " " + field.name());
			}
		}
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
	 * Adds method calls of the given expression.
	 * @param e The receiver expression.
	 * @param nextLevel The expressions to use as
	 * arguments.
	 * @param ops The list into which we will insert
	 * the newly-generated expressions.
	 * @param depth The current search depth.
	 * @param maxDepth The maximum search depth.
	 * @param targetName The name of the method to
	 * call, or null if we can call any method.
	 * @throws DebugException
	 * @throws JavaModelException 
	 */
	private void addMethodCalls(Expression e, List<Expression> nextLevel, List<Expression> ops, int depth, int maxDepth, String targetName) throws DebugException, JavaModelException {
		// The public API doesn't tell us the methods of a class, so we need to use the jdi.  Note that we must now be careful converting between jdi types and Eclipse types.
		//Type objTypeImpl = ((JDIType)e.getType()).getUnderlyingType();
		if (classBlacklist.contains(e.getStaticType().getName()))
			return;
		boolean isConstructor = e instanceof PlaceholderExpression;
		boolean isStatic = !isConstructor && expressionMaker.isStatic(e);
		//String objTypeName = isStatic ? e.toString() : objTypeImpl.name();
		Method stackMethod = ((JDIStackFrame)stack).getUnderlyingMethod();
		IJavaValue receiverValue = expressionMaker.getValue(e, Collections.<Effect>emptySet());
		IJavaType curType = !isStatic && receiverValue != null && !receiverValue.isNull() ? receiverValue.getJavaType() : e.getStaticType();
		if (!(curType instanceof IJavaClassType))  // This can happen when something of static type Object is actually an array.
			return;
		while (curType.getName().indexOf('$') != -1 && curType instanceof IJavaClassType) {  // Avoid downcasting to anonymous types.
			IType itype = project.findType(curType.getName());
			if (itype == null || !itype.isAnonymous())
				break;
			curType = ((IJavaClassType)curType).getSuperclass();
		}
		while (curType != null && !curType.equals(e.getStaticType()) && (downcastTypes.contains(curType.getName()) || uniqueValuesSeenForType.containsKey(curType.getName()) || curType.getName().startsWith("sun."))) {
			curType = ((IJavaClassType)curType).getSuperclass();  // As a heuristic optimization, avoid downcasting to types we've seen before (or private sun. types).
		}
		if (curType == null)
			curType = e.getStaticType();
		boolean isSubtype = !curType.equals(e.getStaticType());
		if (isSubtype) {
			//System.out.println("Downcasting " + e + " to " + curType.getName());
			for (IJavaType supertype: subtypeChecker.getSupertypes(curType))  // Heuristically ensure we do not downcast to any of this type's supertypes.
				downcastTypes.add(supertype.getName());
		}
		List<Method> legalMethods = getMethods(curType, sideEffectHandler);
		Set<String> calledMethods = new HashSet<String>();
		OverloadChecker overloadChecker = new OverloadChecker(curType, stack, target, typeCache, subtypeChecker);
		for (Method method : legalMethods) {
			// Filter out java.lang.Object methods and fake methods like "<init>".  Note that if we don't filter out Object's methods we do getClass() and then call reflective methods, which is bad times.
			// TODO: Allow calling protected and package-private things when it's legal.
			if (!isLegalMethod(method, thisType, isConstructor) || (isStatic != method.isStatic()) || method.equals(stackMethod))  // Disable explicit recursion (that is, calling the current method), since it is definitely not yet complete.
				continue;
			if (!isConstructor && method.returnTypeName().equals("void"))
				continue;
            if (method.isStatic() && staticAccesses.contains(method.declaringType().name() + " " + method.name() + " " + method.signature()))
                continue;
			if (targetName != null && !targetName.equals(method.name()))
				continue;
			if (e.getStaticType().getName().equals("java.lang.String") && (method.name().equals("toString") || method.name().equals("subSequence")))
				continue;  // Don't call toString on Strings.  Also don't call subSequence because it's the same as substring.
            if (!(e.getStaticType() instanceof IJavaInterfaceType)) {  // Skip interface methods called on non-interface objects, as the object method will also be in the list.  Without this, we duplicate calls to interface methods when the static type is a non-interface.
				IJavaType declaringType = EclipseUtils.getTypeAndLoadIfNeeded(method.declaringType().name(), stack, target, typeCache);
				if (declaringType instanceof IJavaInterfaceType)
					continue;
            }
            IMethod imethod = EclipseUtils.getIMethod(method, project);
            if (imethod != null && Flags.isDeprecated(imethod.getFlags()))
            	continue;
            if (calledMethods.contains(method.name() + "~" + method.signature()))
            	continue;  // visibleMethods can return duplicates, so we filter them out.
			IJavaType returnType = isConstructor ? e.getStaticType() : EclipseUtils.getTypeAndLoadIfNeeded(method.returnTypeName(), stack, target, typeCache);
			/*if (returnType == null)
				System.err.println("I cannot get the class of the return type of " + objTypeImpl.name() + "." + method.name() + "() (" + method.returnTypeName() + ")");*/
			if (returnType != null && (isHelpfulType(returnType, depth, maxDepth) || method.isConstructor() || mightBeHelpfulWithDowncast(returnType))) {  // Constructors have void type... 
				List<?> argumentTypeNames = method.argumentTypeNames();
				// TODO: Improve overloading detection.
				overloadChecker.setMethod(method);
				IJavaType[] argTypes = new IJavaType[argumentTypeNames.size()];
				ArrayList<ArrayList<Expression>> allPossibleActuals = new ArrayList<ArrayList<Expression>>(argumentTypeNames.size());
				Iterator<?> aIt = argumentTypeNames.iterator();
				while (aIt.hasNext()) {
					IJavaType argType = EclipseUtils.getTypeAndLoadIfNeeded((String)aIt.next(), stack, target, typeCache);
					if (argType == null) {
						//System.err.println("I cannot get the class of the arguments to " + objTypeImpl.name() + "." + method.name() + "()");
						break;
					}
					ArrayList<Expression> curPossibleActuals = getArgs(nextLevel, e, method, overloadChecker, argType, allPossibleActuals.size(), depth);
					argTypes[allPossibleActuals.size()] = argType;
					allPossibleActuals.add(curPossibleActuals);
				}
				if (allPossibleActuals.size() == argumentTypeNames.size()) {
					Expression receiver = e;
					if (method.isStatic())
						receiver = expressionMaker.makeStaticName(EclipseUtils.sanitizeTypename(getShortestTypename(method.declaringType().name())), (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(method.declaringType().name(), stack, target, typeCache), thread);
					else if (isSubtype && ((ReferenceType)((JDIType)e.getStaticType()).getUnderlyingType()).methodsByName(method.name(), method.signature()).isEmpty()) {
						IJavaType downcastType = getDowncastTypeName(method);
						if (downcastType != null)
							receiver = expressionMaker.makeParenthesized(downcast(receiver, downcastType));
					}
					String name = method.name();
					if (method.isConstructor())
						name = getShortestTypename(receiver.getStaticType().getName());
					int maxArgDepth = pruneManyArgCalls(method, allPossibleActuals, depth, depth - 1);
					Set<Effect> receiverEffects = isConstructor ? Collections.<Effect>emptySet() : expressionMaker.getResult(e, Collections.<Effect>emptySet()).getEffects();
					makeAllCalls(method, name, receiver, returnType, ops, receiverEffects, argTypes, allPossibleActuals, new ArrayList<Expression>(allPossibleActuals.size()), depth, maxDepth, maxArgDepth, overloadChecker);
                    if (method.isStatic())
                        staticAccesses.add(method.declaringType().name() + " " + method.name() + " " + method.signature());
                    calledMethods.add(method.name() + "~" + method.signature());
				}
			}
		}
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
	private ArrayList<Expression> getArgs(List<Expression> possibleArgs, Expression receiver, Method method, OverloadChecker overloadChecker, IJavaType argType, int curArgIndex, int maxArgDepth) throws DebugException {
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
				if (overloadChecker.needsCast(argType, a.getStaticType(), curArgIndex)) {  // If the method is overloaded, when executing the expression we might get "Ambiguous call" compile errors, so we put in a cast to remove the ambiguity.
					//System.out.println("Adding cast to type " + argType.toString() + " to argument " + a.toString() + " at index "+ curArgIndex + " of method " + method.declaringType() + "." + method.name() + " with " + method.argumentTypeNames().size() + " arguments.");
					a = (Expression)expressionMaker.makeCast(a, argType);
				}
				// Cast the null literal when passed as a vararg to the component type.
				// TODO: Bug: Eclipse's sendMessage interface takes in a value and so ignores this cast and calls the wrong one.  I thus do not generate such expressions.
				if (a instanceof NullLiteral && method.isVarArgs() && curArgIndex == numArgs - 1)
					continue;//a = (Expression)expressionMaker.makeCast(a, ((IJavaArrayType)argType).getComponentType(), a.getValue(), valueCache, thread);
				if (getDepth(a) > maxArgDepth)
					continue;
				curPossibleActuals.add(a);
			}
		return curPossibleActuals;
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
	 * @return The maximum depth of arguments to this method.
	 */
	private int pruneManyArgCalls(Method method, ArrayList<ArrayList<Expression>> allPossibleActuals, int curDepth, int curMaxArgDepth) {
		int maxArgDepth = pruneManyArgCalls(allPossibleActuals, curDepth, curMaxArgDepth, method);
		if (maxArgDepth < curMaxArgDepth) {
			if (!prunedDepths.containsKey(method))
				prunedDepths.put(method, curDepth);
		} else if (prunedDepths.containsKey(method))
			newlyUnpruneds.add(method);
		return maxArgDepth;
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
	 * @param method The method being called.
	 * @return The maximum depth of arguments to this method.
	 */
	private int pruneManyArgCalls(ArrayList<ArrayList<Expression>> allPossibleActuals, int curDepth, int curMaxArgDepth, Method method) {
		long numCombinations = Utils.getNumCalls(allPossibleActuals);
		if (numCombinations > getPruneThreshold(curDepth, method)) {
			for (ArrayList<Expression> possibleActuals: allPossibleActuals)
				for (Iterator<Expression> it = possibleActuals.iterator(); it.hasNext(); )
					if (getDepth(it.next()) >= curMaxArgDepth)  // We need >= not > since we use this during equivalence expansion.
						it.remove();
			//System.out.println("Pruned call to " + (method.declaringType().name() + "." + method.name()) + " from " + numCombinations + " to " + Utils.getNumCalls(allPossibleActuals));
			//if (numCombinations <= 50 * Math.pow(10, Math.max(0, curDepth - 1)))
			//	System.out.println("Helpfully reduced prune threshold for " + method.declaringType() + "." + Weights.getMethodKey(method));
			if (curMaxArgDepth > 0)  // Without this, we can recurse forever.
				return pruneManyArgCalls(allPossibleActuals, curDepth, curMaxArgDepth - 1, method);
		}
		return curMaxArgDepth;
	}
	
	/**
	 * Gets the threshold for the number of calls at
	 * which we start pruning.
	 * @param curDepth The current search depth.
	 * @param method The method being called.
	 * @return The threshold for the number of calls
	 * at which we start pruning.
	 */
	private int getPruneThreshold(int curDepth, Method method) {
		int depthFactor = curDepth - 1;
		if (weights.isUncommon(method.declaringType().name(), Weights.getMethodKey(method)))
			depthFactor--;
		return (int)(50 * Math.pow(10, Math.max(0, depthFactor)));  // 50 at depth 1, 500 at depth 2, 5000 at depth 3, etc.
	}
	
	/**
	 * Gets the type to which we want to downcast calls to
	 * the given method.  We heuristically try to choose one
	 * of the types highest up in the hierarchy.
	 * @param method The method we are calling.
	 * @return The type to which we should downcast calls to
	 * the given method, or null if we should not downcast.
	 */
	private IJavaType getDowncastTypeName(Method method) {
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
	 * Finds the types that might be helpful for the given search.
	 * This eliminates types that we definitely do not need to search
	 * and for each types tells the maximum depth at which it is
	 * useful.
	 * For example, if A can get B which can get C and we want a C,
	 * then C is useful at all depths, B is useful at <=maxDepth-1, and
	 * A at <=maxDepth-2.  Specifically, A is not useful at maxDepth-1,
	 * since it takes two steps to get a C from it.
	 * As this example suggests, we do a backwards search from the
	 * types for which we are searching.  To help with this, we first
	 * build a mapping that tells us which types are used to generate
	 * the given types.
	 * TODO: getTypeGen does not work for downcasting.  E.g., if target is VirtualMachine, it finds almost nothing that points to it because the actual expressions require downcasting to get more methods.
	 * TODO: If I enable downcasting during the search, enable the bits here that work with that.  Test this if we do, since it might not be very helpful (e.g., if we see an Object, which are common thanks to generics, we have to assume everything is available).
	 * TODO: This, specifically getTypeGen, is a bit slow.
	 * TODO: I could make this more precise, by e.g., consider paired types and available depths (e.g., maybe we need A and B to get C, so if we have A but not B, A is not helpful either).
	 * @param maxDepth The maximum search depth.
	 * @param monitor The process monitor.
	 * @return A mapping of useful types to the maximum depth at which
	 * they are useful.  Types not in this mapping are not useful.  Returns
	 * null if all types should be considered useful.
	 * @throws DebugException
	 * @throws JavaModelException
	 */
	private Map<String, Integer> getHelpfulTypesMap(int maxDepth, IProgressMonitor monitor) throws DebugException, JavaModelException {
		if (typeConstraint instanceof MethodConstraint || typeConstraint instanceof FieldConstraint || typeConstraint instanceof UnknownConstraint)
			return null;
		curMonitor = SubMonitor.convert(monitor, "Finding useful types", IProgressMonitor.UNKNOWN);
		Map<String, Set<String>> typeGen = getTypeGen(maxDepth, monitor);
		Map<String, Set<String>> subtypes = new HashMap<String, Set<String>>();
		Map<String, Set<String>> supertypes = new HashMap<String, Set<String>>();
		getSubSuperTypes(typeGen, subtypes, supertypes);
		Map<String, Integer> helpfulTypes = new HashMap<String, Integer>();
		Set<String> processedTypes = new HashSet<String>();
		Set<String> curTypes = new HashSet<String>();
		//Set<String> superTypes = new HashSet<String>();
		// Add the target types.
		for (IJavaType constraintType: typeConstraint.getTypes(stack, target, typeCache))
			curTypes.add(constraintType.getName());
		// Do the backwards search.
		for (int depth = maxDepth; depth >= 0; depth--) {
			Set<String> nextCurTypes = new HashSet<String>();
			//Set<String> nextSuperTypes = new HashSet<String>();
			for (String typeName: curTypes) {
				for (String subTypeName: subtypes.get(typeName))
					process(subTypeName, depth, processedTypes, typeGen, helpfulTypes, nextCurTypes);
				//for (String superTypeName: supertypes.get(typeName))
				//	process(superTypeName, depth, processedTypes, typeGen, helpfulTypes, nextSuperTypes);
			}
			//for (String typeName: superTypes)
			//	process(typeName, depth, processedTypes, typeGen, helpfulTypes, nextSuperTypes);
			monitor.worked(curTypes.size());
			curTypes = nextCurTypes;
			//superTypes = nextSuperTypes;
		}
		curMonitor.done();
		return helpfulTypes;
	}
	
	/**
	 * Gets subtypes and supertypes of all types in the
	 * typeGen map.
	 * @param typeGen A map that contains types.
	 * @param subtypes The map in which we store all subtypes.
	 * @param supertypes The map in which we store all supertypes.
	 * @throws DebugException
	 */
	private void getSubSuperTypes(Map<String, Set<String>> typeGen, Map<String, Set<String>> subtypes, Map<String, Set<String>> supertypes) throws DebugException {
		Set<String> allTypes = getAllTypes(typeGen);
		tryToLoad(allTypes);
		for (String typeName: allTypes) {
			IJavaType type = EclipseUtils.getTypeAndLoadIfNeeded(typeName, stack, target, typeCache);
			if (type != null) {  // The type could be illegal.
				for (IJavaType parentType: subtypeChecker.getSupertypes(type)) {
					Utils.addToSetMap(subtypes, parentType.getName(), typeName);
					Utils.addToSetMap(supertypes, typeName, parentType.getName());
				}
			}
		}
	}
	
	/**
	 * Gets all the types referenced in the given map.
	 * @param typeGen A map that contains types.
	 * @return All the types referenced in the given map.
	 */
	private static Set<String> getAllTypes(Map<String, Set<String>> typeGen) {
		Set<String> allTypes = new HashSet<String>();
		for (Map.Entry<String, Set<String>> entry: typeGen.entrySet()) {
			allTypes.add(entry.getKey());
			for (String typeName: entry.getValue())
				allTypes.add(typeName);
		}
		return allTypes;
	}
	
	/**
	 * Processes the given type by marking it as helpful and
	 * preparing to search the types that can generate it.
	 * @param typeName The current type.
	 * @param depth The current depth.
	 * @param processedTypes A list of all types we have processed,
	 * which allows us to avoid unnecessary work.
	 * @param typeGen A map that maps a type to all the types that
	 * can be used to directly get it.
	 * @param helpfulTypes The map we are building that maps useful
	 * types to the maximum depth at which they are useful.
	 * @param nextTypes The types we want to search next.
	 */
	private static void process(String typeName, int depth, Set<String> processedTypes, Map<String, Set<String>> typeGen, Map<String, Integer> helpfulTypes, Set<String> nextTypes) {
		if (processedTypes.contains(typeName))
			return;
		if (!helpfulTypes.containsKey(typeName))
			helpfulTypes.put(typeName, depth);
		Set<String> sources = typeGen.get(typeName);
		if (sources != null)
			nextTypes.addAll(sources);
		processedTypes.add(typeName);  // As an optimization, avoid checking a type multiple times.  Because we're doing a BFS, the first time will have the maximum depth.

	}
	
	/**
	 * Builds a mapping of the types that can be used to build
	 * the given type.
	 * @param maxDepth The maximum depth to search.
	 * @param monitor The progress monitor.z
	 * @return A map that maps a type to all the types that
	 * can be used to directly get it.
	 * @throws DebugException
	 * @throws JavaModelException
	 */
	private Map<String, Set<String>> getTypeGen(int maxDepth, IProgressMonitor monitor) throws DebugException, JavaModelException {
		Map<String, Set<String>> typeGen = new HashMap<String, Set<String>>();
		Set<String> processedTypes = new HashSet<String>();
		Set<String> curTypes = new HashSet<String>();
		// Initialize depth 0.
		for (IJavaVariable l : stack.getLocalVariables())
			curTypes.add(EclipseUtils.getTypeOfVariableAndLoadIfNeeded(l, stack).getName());
		if (!stack.isStatic())
			curTypes.add(stack.getDeclaringTypeName());
		// Do the BFS search.
		for (int depth = 1; depth <= maxDepth; depth++) {
			tryToLoad(curTypes);
			for (String typeName: curTypes) {
				if (processedTypes.contains(typeName) || classBlacklist.contains(typeName))
					continue;
				IJavaType type = EclipseUtils.getTypeAndLoadIfNeeded(typeName, stack, target, typeCache);
				addTypesFromCalls(typeGen, typeName, type, false, null);
				addTypesFromFields(typeGen, typeName, type, false, null);
				if (type instanceof IJavaArrayType) {  // Add {int,array_type} -> component type (for access) and array_type -> int (for length).
					Utils.addToSetMap(typeGen, ((IJavaArrayType)type).getComponentType().getName(), typeName);
					Utils.addToSetMap(typeGen, ((IJavaArrayType)type).getComponentType().getName(), "int");
					Utils.addToSetMap(typeGen, "int", typeName);
				}
				processedTypes.add(typeName);  // As an optimization, ensure we don't waste time re-processing types.
				monitor.worked(1);
			}
			curTypes = new HashSet<String>(typeGen.keySet());
		}
		// Add static accesses/calls to imported classes.
		tryToLoad(importsSet);
		for (IImportDeclaration imp : imports) {
			String fullName = imp.getElementName();
			if (!imp.isOnDemand()) {
				if (Flags.isStatic(imp.getFlags())) {
					String typeName = fullName.substring(0, fullName.lastIndexOf('.'));
					if (processedTypes.contains(typeName))
						continue;
					IJavaReferenceType importedType = (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(typeName, stack, target, typeCache);
					String shortName = EclipseUtils.getUnqualifiedName(fullName);
					addTypesFromCalls(typeGen, typeName, importedType, true, shortName);
					addTypesFromFields(typeGen, typeName, importedType, true, shortName);
				} else {
					if (processedTypes.contains(fullName) || classBlacklist.contains(fullName))
						continue;
					IJavaReferenceType importedType = (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(fullName, stack, target, typeCache);
					addTypesFromCalls(typeGen, fullName, importedType, true, null);
					addTypesFromFields(typeGen, fullName, importedType, true, null);
				}
			}
		}
		return typeGen;
	}

	/**
	 * Adds types from method calls to the map of which types
	 * are used to build other types.
	 * @param typeGen The mapping of types to types used to build them.
	 * @param typeName The name of the current type.
	 * @param type The current type.
	 * @param isStatic Whether we are only looking for static types.
	 * @param targetName If this is non-null, only consider methods
	 * with the given name.
	 */
	private void addTypesFromCalls(Map<String, Set<String>> typeGen, String typeName, IJavaType type, boolean isStatic, String targetName) {
		for (Method method: getMethods(type, sideEffectHandler)) {
			if (!isLegalMethod(method, thisType, false) || method.returnTypeName().equals("void") || (targetName != null && !targetName.equals(method.name())))
				continue;
			for (String argTypeName: method.argumentTypeNames())
				Utils.addToSetMap(typeGen, method.returnTypeName(), argTypeName);
			if (!isStatic || method.isStatic())  // Only call static methods if it's an import.
				Utils.addToSetMap(typeGen, method.returnTypeName(), typeName);
		}
	}

	/**
	 * Adds types from field accesses to the map of which types
	 * are used to build other types.
	 * @param typeGen The mapping of types to types used to build them.
	 * @param typeName The name of the current type.
	 * @param type The current type.
	 * @param isStatic Whether we are only looking for static types.
	 * @param targetName If this is non-null, only consider fields
	 * with the given name.
	 */
	private void addTypesFromFields(Map<String, Set<String>> typeGen, String typeName, IJavaType type, boolean isStatic, String targetName) {
		for (Field field: getFields(type)) {
			if (!isLegalField(field, thisType) || (targetName != null && !targetName.equals(field.name())))
				continue;
			if (!isStatic || field.isStatic())  // Only access static fields if it's an import.
				Utils.addToSetMap(typeGen, field.typeName(), typeName);
		}
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
	private boolean meetsNonNullPreconditions(Method method, int argIndex, Expression arg) {
		String methodId = method.declaringType().name() + " " + method.name() + " " + method.signature();
		if (methodPreconditions.containsKey(methodId))
			for (Predicate precondition: methodPreconditions.get(methodId))
				if (precondition instanceof NonNull)
					if (((NonNull)precondition).getArgIndex() == argIndex && expressionMaker.getValue(arg, Collections.<Effect>emptySet()).isNull())
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
	private boolean meetsPreconditions(Method method, Expression receiver, ArrayList<Expression> actuals) {
		String methodId = method.declaringType().name() + " " + method.name() + " " + method.signature();
		if (methodPreconditions.containsKey(methodId))
			for (Predicate precondition: methodPreconditions.get(methodId))
				if (!precondition.satisfies(receiver, actuals, expressionMaker))
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
	private void loadTypesFromMethods(List<Expression> nextLevel, IImportDeclaration[] imports) throws DebugException {
		Set<String> importNames = new HashSet<String>();
		for (IImportDeclaration imp : imports)
			if (!imp.isOnDemand())
				importNames.add(imp.getElementName());
		tryToLoad(importNames);
		Set<String> typeNames = new HashSet<String>();
		for (Expression e: nextLevel) {
			IJavaValue value = expressionMaker.getValue(e, Collections.<Effect>emptySet());
			if (ExpressionMaker.isObjectOrInterface(e.getStaticType()) && (value == null || !value.isNull())) {
				checkMethods(e.getStaticType(), typeNames, false);
				checkFields(e.getStaticType(), typeNames, false);
			}
		}
		for (IImportDeclaration imp : imports)
			if (!imp.isOnDemand()) {
				IJavaType impType = EclipseUtils.getTypeAndLoadIfNeeded(imp.getElementName(), stack, target, typeCache);
				checkMethods(impType, typeNames, true);
				checkFields(impType, typeNames, true);
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
	 * @param isImport If the receiver type comes from an import.
	 */
	private void checkMethods(IJavaType receiverType, Set<String> typeNames, boolean isImport) {
		for (Method method : getMethods(receiverType, sideEffectHandler)) {
			if (isLegalMethod(method, thisType, false) && !method.returnTypeName().equals("void") && (!isImport || method.isStatic())) {
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
	 * @param isImport If the receiver type comes from an import.
	 */
	private void checkFields(IJavaType receiverType, Set<String> typeNames, boolean isImport) {
		for (Field field: getFields(receiverType)) {
			if (isLegalField(field, thisType) && (!isImport || field.isStatic())) {
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
			if (typeCache.get(typeName) == null && !typeCache.isIllegal(typeName) && !typeCache.isCheckedLegal(typeName) && !isPrimitive(typeName))
				unloadedTypeNames.add(typeName);
		if (!unloadedTypeNames.isEmpty() && EclipseUtils.tryToLoadTypes(unloadedTypeNames, stack))
			for (String typeName: unloadedTypeNames)  // Mark all the type names as legal so we will not have to check if they are illegal one-by-one, which is slow.
				typeCache.markCheckedLegal(typeName);
	}
	
	/**
	 * Checks whether the given type name is that of a primitive.
	 * @param typeName The name of a type.
	 * @return Whether the given string is the name of a primitive type.
	 */
	private static boolean isPrimitive(String typeName) {
		return "int".equals(typeName) || "boolean".equals(typeName) || "long".equals(typeName) || "byte".equals(typeName) || "char".equals(typeName) || "short".equals(typeName) || "float".equals(typeName) || "double".equals(typeName);
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
		else if (typeName.startsWith("java.lang.") && typeName.lastIndexOf('.') == 9)  // We don't want to shorten e.g., java.lang.reflect.Field to reflect.Field.
			return typeName.substring("java.lang.".length());
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
	 * @param maxDepth The maximum search depth.
	 * @throws DebugException 
	 */
	private void addUniqueExpressionToList(List<Expression> list, Expression e, int depth, int maxDepth) throws DebugException {
		// We only add constructors at max depth, but they might actually be lower depth.
		if (e != null && isCorrectDepth(e, depth)) {
			Result result = expressionMaker.getResult(e, Collections.<Effect>emptySet());
			IJavaValue value = result.getValue().getValue();
			if (value != null && "V".equals(value.getSignature()))
				return;
			if (depth == maxDepth && !typeConstraint.isFulfilledBy(e.getStaticType(), subtypeChecker, typeCache, stack, target)) {
				// The current type does not fulfill the constraint and we're at the maximum depth, so only add it if we can downcast it.
				if (isHelpfulWithDowncast(value))
					e = downcast(e);
				else {
					numFailedDowncasts++;
					return;
				}
			}
			Set<Effect> curEffects = Collections.emptySet();
			Map<Result, ArrayList<Expression>> curEquivalences = equivalences.get(curEffects);
			if (result != null && curEquivalences != null && curEquivalences.containsKey(result))
				addEquivalentExpression(curEquivalences.get(result), e);
			else {
				if (value != null) {
					addEquivalentExpressionOnlyIfNewValue(e, result, curEffects);
					if (e.getStaticType() != null) {
						String typeName = e.getStaticType().getName();
						Integer numValuesSeenOfType = uniqueValuesSeenForType.get(typeName);
						uniqueValuesSeenForType.put(typeName, numValuesSeenOfType == null ? 1 : numValuesSeenOfType + 1);
					}
				}
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
	private boolean isCorrectDepth(Expression e, int depth) {
		return getDepth(e) == depth || (expressionMaker.getMethod(e) != null && expressionMaker.getMethod(e).isConstructor());
	}
	
	/**
	 * Determines whether an expression of the given type can be
	 * useful to us.
	 * @param curType The type to test.
	 * @param depth The current depth.
	 * @param maxDepth The maximum search depth.
	 * @return Whether an expression of the given type can be useful to us.
	 * @throws DebugException 
	 */
	private boolean isHelpfulType(IJavaType curType, int depth, int maxDepth) throws DebugException {
		if (curType != null && "V".equals(curType.getSignature()))  // Void things never return anything useful.
			return false;
		if (curType == null || helpfulTypes == null)
			return depth < maxDepth || typeConstraint.isFulfilledBy(curType, subtypeChecker, typeCache, stack, target);
		Integer maxHelpfulDepth = helpfulTypes.get(curType.getName());
		return maxHelpfulDepth != null && depth <= maxHelpfulDepth;
	}
	
	/**
	 * Determines whether an expression of the given type might be
	 * useful after a downcast (i.e., whether a value of the given
	 * type might be of some subtype that is useful).
	 * @param curType The type to test.
	 * @return Whether an expression of the given type might be
	 * useful after a downcast
	 */
	private boolean mightBeHelpfulWithDowncast(IJavaType curType) {
		for (IJavaType constraintType: typeConstraint.getTypes(stack, target, typeCache))
			if (subtypeChecker.isSubtypeOf(constraintType, curType))
				return true;
		return false;
	}
	
	/**
	 * Checks whether the given value is useful to us after a
	 * downcast (i.e., whether it is non-null and its type
	 * fulfills the constraint).  There is no point in downcasting
	 * null, so we exclude it.
	 * @param value The value to test.
	 * @return Whether the given value is useful to us after a
	 * downcast.
	 * @throws DebugException
	 */
	private boolean isHelpfulWithDowncast(IJavaValue value) throws DebugException {
		return value.getJavaType() != null && typeConstraint.isFulfilledBy(value.getJavaType(), subtypeChecker, typeCache, stack, target);
	}

	/**
	 * Downcasts the given expression to one that fulfills the
	 * constraint.
	 * @param e The expression to downcast.
	 * @return The given expression downcast to one that fulfills
	 * the constraint.
	 * @throws DebugException
	 */
	private Expression downcast(Expression e) throws DebugException {
		IJavaType downcastType = getDowncastType(e.getStaticType());
		if (downcastType == null)  // we can't downcast the static type, so use the dynamic type, which was what we used when we checked if a downcast would be useful.
			downcastType = expressionMaker.getValue(e, Collections.<Effect>emptySet()).getJavaType();
		return downcast(e, downcastType);
	}

	/**
	 * Downcasts the given expression to the given type.
	 * @param e The expression to downcast.
	 * @param typename The name of the type to which we
	 * should downcast the given expression.
	 * @return The given expression downcast to one the given type.
	 * @throws DebugException
	 */
	private Expression downcast(Expression e, IJavaType type) throws DebugException {
		assert type != null : e.toString();
		Expression casted = expressionMaker.makeCast(e, type, getShortestTypename(type.getName()));
		expressionMaker.copyResults(e, casted);
		expressionMaker.setResultString(casted, expressionMaker.getResultString(((Expression)e)));
		return casted;
	}
	
	/**
	 * Gets a type to which we should downcast the given type
	 * so that it satisfies the constraint.  Such a type must
	 * exist.
	 * @param curType The type we want to downcast.
	 * @return The type to which we should downtype the given
	 * type so that it satisfies the constraint.
	 */
	private IJavaType getDowncastType(IJavaType curType) {
		IJavaType[] constraintTypes = typeConstraint.getTypes(stack, target, typeCache);
		if (constraintTypes.length == 1)  // Short-circuit for efficiency: if there is only one constraint type, it must be valid.
			return constraintTypes[0];
		for (IJavaType constraintType: constraintTypes)
			if (subtypeChecker.isSubtypeOf(constraintType, curType))
				return constraintType;
		return null;
	}
	
	/**
	 * Creates all possible calls using the given actuals.
	 * @param method The method being called.
	 * @param name The method name.
	 * @param receiver The receiving object.
	 * @param returnType The return type of the function.
	 * @param ops The list to add the unique calls created.
	 * @param curEffects The current effects.
	 * @param actualTypes The types of the actuals.
	 * @param defaultPossibleActuals A list of all the possible
	 * actuals for each argument assuming there are no effects.
	 * We use this for efficiency to avoid re-computing it in the
	 * common case of no effects.
	 * @param curActuals The current list of actuals, which is built
	 * up through recursion.
	 * @param depth The current search depth.
	 * @param maxDepth The maximum search depth.
	 * @param maxArgDepth The maximum depth of an argument.
	 * @param overloadChecker The overload checker.
	 * @throws DebugException 
	 */
	private void makeAllCalls(Method method, String name, Expression receiver, IJavaType returnType, List<Expression> ops, Set<Effect> curEffects, IJavaType[] actualTypes, ArrayList<ArrayList<Expression>> defaultPossibleActuals, ArrayList<Expression> curActuals, int depth, int maxDepth, int maxArgDepth, OverloadChecker overloadChecker) throws DebugException {
		if (curMonitor.isCanceled())
			throw new OperationCanceledException();
		if (curActuals.size() == actualTypes.length) {
			if (meetsPreconditions(method, receiver, curActuals))
				  // We might evaluate the call when we create it (e.g., StringEvaluator), so first ensure it has the proper depth to avoid re-evaluating some calls.
				if (method.isConstructor() || getDepthOfCall(receiver, curActuals, method) == depth
						|| (newlyUnpruneds.contains(method) && getDepthOfCall(receiver, curActuals, method) >= prunedDepths.get(method)))
					addUniqueExpressionToList(ops, expressionMaker.makeCall(name, receiver, curActuals, returnType, thisType, method, thread, staticEvaluator), depth, maxDepth);
		} else {
			int argNum = curActuals.size();
			ArrayList<Expression> uniqueExpressions = getUniqueExpressions(null, curEffects, actualTypes[argNum], depth, defaultPossibleActuals.get(argNum));
			ArrayList<Expression> nextArgs = curEffects.isEmpty() ? uniqueExpressions : getArgs(uniqueExpressions, receiver, method, overloadChecker, actualTypes[argNum], argNum, maxArgDepth);
			for (Expression e : nextArgs) {
				curActuals.add(e);
				makeAllCalls(method, name, receiver, returnType, ops, expressionMaker.getResult(e, curEffects).getEffects(), actualTypes, defaultPossibleActuals, curActuals, depth, maxDepth, maxArgDepth, overloadChecker);
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
	 * @param results The list to add the unique calls created. 
	 * @param possibleActuals A list of all the possible actuals for each argument.
	 * @param curActuals The current list of actuals, which is built
	 * up through recursion.
	 * @param returnType The return type of the method.
	 * @param effects The current side effects.
	 * @param The result of all these calls.
	 * @param maxDepth The maximum expression depth to find.
	 */
	private void makeAllCalls(Method method, String name, Expression receiver, List<Expression> results, ArrayList<ArrayList<Expression>> possibleActuals, ArrayList<Expression> curActuals, IJavaType returnType, Set<Effect> effects, Result result, int maxDepth) {
		if (curMonitor.isCanceled())
			throw new OperationCanceledException();
		if (curActuals.size() == possibleActuals.size()) {
			if (getDepthOfCall(receiver, curActuals, method) <= maxDepth) {  // Optimization: Do an early check to ensure we don't generate expressions that are too large.  We would filter it out later, but this would cause us to create lots of new ASTS, which can be very slow.
				if ("<init>".equals(name))
					results.add(expressionMaker.makeClassInstanceCreation(((TypeLiteral)receiver).getType(), curActuals, method, effects, result));
				else
					results.add(expressionMaker.makeCall(name, receiver, curActuals, method, returnType, effects, result));
			}
		} else {
			int depth = curActuals.size();
			for (Expression e : possibleActuals.get(depth)) {
				curActuals.add(e);
				makeAllCalls(method, name, receiver, results, possibleActuals, curActuals, returnType, effects, result, maxDepth);
				curActuals.remove(depth);
			}
		}
	}
	
	/**
	 * Gets unique (non-equivalent) expressions with the given effects.
	 * Prefers non-constant fields.
	 * @param e The expression to which none of the returned
	 * expressions should be reference-equivalent.
	 * @param curEffects The current effects.
	 * @param type The desired type of the expressions.
	 * @param curDepth The current depth.
	 * @param defaults The default expressions to use if
	 * there are no side effects.
	 * @return A list of representatives of the equivalence classes
	 * under the given effects.
	 * @throws DebugException 
	 */
	private ArrayList<Expression> getUniqueExpressions(Expression e, Set<Effect> curEffects, IJavaType type, int curDepth, List<Expression> defaults) throws DebugException {
		if (curEffects.isEmpty()) {  // Fast-path the common case of no side effects by using the defaults provided.
			ArrayList<Expression> result = new ArrayList<Expression>(defaults.size());
			for (Expression cur: defaults) {
				if (subtypeChecker.isSubtypeOf(cur.getStaticType(), type)) {
					if (cur != e && !isConstantField(cur, expressionMaker))
						result.add(cur);
					else
						result.add(getRepresentative(e, equivalences.get(curEffects).get(expressionMaker.getResult(cur, Collections.<Effect>emptySet())), curDepth));
				}
			}
			return result;
		} else {
			// Compute the equivalence class for the given effects if necessary.
			Map<Result, ArrayList<Expression>> effectEquivalences = equivalences.get(curEffects);
			if (effectEquivalences == null) {
				effectEquivalences = new HashMap<Result, ArrayList<Expression>>();
				equivalences.put(curEffects, effectEquivalences);
			}
			for (Map.Entry<Result, ArrayList<Expression>> entry: new ArrayList<Map.Entry<Result, ArrayList<Expression>>>(equivalences.get(Collections.<Effect>emptySet()).entrySet()))
				if (subtypeChecker.isSubtypeOf(entry.getKey().getValue().getValue().getJavaType(), type))
					for (Expression cur: new ArrayList<Expression>(entry.getValue()))
						expressionMaker.evaluateExpressionWithEffects(cur, curEffects, thread, this);
			// Get the representatives of the equivalence classes.
			ArrayList<Expression> result = new ArrayList<Expression>(effectEquivalences.size());
			for (ArrayList<Expression> equivs: effectEquivalences.values()) {
				Expression cur = equivs.get(0);
				if (subtypeChecker.isSubtypeOf(cur.getStaticType(), type)) {
					if (cur != e && !isConstantField(cur, expressionMaker))
						result.add(cur);
					else
						result.add(getRepresentative(e, equivs, curDepth));
				}
			}
			return result;
		}
	}
	
	/**
	 * Gets a representative of the given equivalence class that is not
	 * equivalent to the given expression or a constant field.
	 * (e.g., y+z if x=y=z; without this we would not generate x+x).
	 * @param e The expression to which the returned expression should
	 * not be equivalent.
	 * @param equivs The equivalence class.
	 * @param curDepth The current depth being searched.
	 * @return A representative of the given equivalence class that is
	 * not equivalent to the given expression.
	 */
	private Expression getRepresentative(Expression e, ArrayList<Expression> equivs, int curDepth) {
		for (int i = 1; i < equivs.size(); i++) {
			Expression equiv = equivs.get(i);
			if (equiv != e && getDepth(equiv) < curDepth && !isConstantField(equiv, expressionMaker))  // Ensure we don't replace it with something from the current depth search.
				return equiv;
		}
		return equivs.get(0);
	}
	
	private static void addEquivalentExpression(ArrayList<Expression> equivs, Expression e) {
		equivs.add(e);
	}
	
	void addEquivalentExpression(Expression e, Set<Effect> curEffects) {
		Map<Result, ArrayList<Expression>> curEquivalences = equivalences.get(curEffects);
		if (curEquivalences == null) {
			curEquivalences = new HashMap<Result, ArrayList<Expression>>();
			equivalences.put(curEffects, curEquivalences);
		}
		Utils.addToListMap(curEquivalences, expressionMaker.getResult(e, curEffects), e);
	}
	
	private void addEquivalentExpressionOnlyIfNewValue(Expression e, Result result, Set<Effect> curEffects) {
		Map<Result, ArrayList<Expression>> curEquivalences = equivalences.get(curEffects);
		if (curEquivalences == null)
			Utils.addToMapMap(equivalences, curEffects, result, Utils.makeList(e));
		else if (!curEquivalences.containsKey(result))
			curEquivalences.put(result, Utils.makeList(e));
	}
	
	/**
	 * Finds other expressions equivalent to the given ones.
	 * @param exprs Expressions for which we want to find
	 * equivalent expressions.
	 * @param maxDepth The maximum depth of expressions.
	 * @param monitor The progress monitor.  The caller should
	 * not allocate a new progress monitor; this method will.
	 * @return Expressions that are equivalent to the given
	 * ones.  Note that the result is disjoint from the input.
	 * @throws DebugException
	 */
	private ArrayList<Expression> expandEquivalences(ArrayList<Expression> exprs, int maxDepth, IProgressMonitor monitor) throws DebugException {
		Set<Result> seenResults = new HashSet<Result>();
		Set<Expression> newlyExpanded = new HashSet<Expression>();
		Map<Result, String> toStrings = new HashMap<Result, String>();
		int totalWork = 0;  // This is just an estimate of the total worked used for the progress monitor; it may miss some elements.
		for (Expression expr: exprs) {
			Result result = expressionMaker.getResult(expr, Collections.<Effect>emptySet());
			addEquivalentExpressionOnlyIfNewValue(expr, result, Collections.<Effect>emptySet());  // Demonstrated primitive values are new, since those are used but not put into the equivalences map.
			boolean added = seenResults.add(result);
			if (added) {
				toStrings.put(result, expressionMaker.getResultString(expr));
				totalWork += equivalences.get(Collections.emptySet()).get(result).size();
			}
		}
		curMonitor = SubMonitor.convert(monitor, "Equivalence expansions", totalWork);
		Set<Expression> exprsSet = new HashSet<Expression>(exprs);
		ArrayList<Expression> results = new ArrayList<Expression>();
		for (Result result: seenResults) {
			for (Expression expr : new ArrayList<Expression>(equivalences.get(Collections.emptySet()).get(result))) {  // Make a copy since we'll probably add expressions to this.
				if (curMonitor.isCanceled())
					throw new OperationCanceledException();
				expandEquivalencesRec(expr, newlyExpanded, Collections.<Effect>emptySet(), maxDepth);
				curMonitor.worked(1);
			}
			String valueString = toStrings.get(result);
			for (Expression expr: getEquivalentExpressions(result, null, typeConstraint, Collections.<Effect>emptySet(), maxDepth))
				if (typeConstraint.isFulfilledBy(expr.getStaticType(), subtypeChecker, typeCache, stack, target) && getDepth(expr) <= maxDepth) {
					expressionMaker.setResultString(expr, valueString);
					if (!exprsSet.contains(expr))
						results.add(expr);
				}
		}
		return results;
	}
	
	/**
	 * Finds expressions that are equivalent to the given one.
	 * @param expr The given expression.
	 * @param newlyExpanded The set of expressions we have already
	 * expanded to stop us from doing extra work or recursing infinitely.
	 * @param curEffects The current effects.
	 * @param maxDepth The maximum depth of expressions.
	 * @throws DebugException
	 */
	private void expandEquivalencesRec(Expression expr, Set<Expression> newlyExpanded, Set<Effect> curEffects, int maxDepth) throws DebugException {
		if (expr == null)
			return;
		if (newlyExpanded.contains(expr))
			return;
		Result result = expressionMaker.getResult(expr, curEffects);
		if (result == null)
			return;
		IJavaType type = expressionMaker.isStatic(expr) ? expressionMaker.getStaticType(expr) : result.getValue().getValue().getJavaType();
		if (expressionMaker.getMethod(expr) != null) {  // If this is a call, use the declared type of the method, not the dynamic type of the value.
			Method method = expressionMaker.getMethod(expr);
			type = EclipseUtils.getFullyQualifiedType(method.isConstructor() ? method.declaringType().name() : expressionMaker.getMethod(expr).returnTypeName(), stack, target, typeCache);
		}
		newlyExpanded.add(expr);
		addEquivalentExpressionOnlyIfNewValue(expr, result, curEffects);
		ArrayList<Expression> curEquivalences = equivalences.get(curEffects).get(result);
		if (expr instanceof NumberLiteral || expr instanceof BooleanLiteral || expr instanceof Name || expr instanceof ThisExpression || expr instanceof NullLiteral) {
			// Do nothing as there's nothing to expand.
		} else if (expr instanceof ParenthesizedExpression) {
			expandEquivalencesRec(((ParenthesizedExpression)expr).getExpression(), newlyExpanded, curEffects, maxDepth);
		} else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			expandEquivalencesRec(infix.getLeftOperand(), newlyExpanded, curEffects, maxDepth);
			Set<Effect> intermediateEffects = expressionMaker.getResult(infix.getLeftOperand(), curEffects).getEffects();
			expandEquivalencesRec(infix.getRightOperand(), newlyExpanded, intermediateEffects, maxDepth);
			for (Expression l : getEquivalentExpressions(infix.getLeftOperand(), UnknownConstraint.getUnknownConstraint(), curEffects, maxDepth - 1))
				for (Expression r : getEquivalentExpressions(infix.getRightOperand(), UnknownConstraint.getUnknownConstraint(), intermediateEffects, maxDepth - 1)) {
					if (curMonitor.isCanceled())
						throw new OperationCanceledException();
					if (isUsefulInfix(l, infix.getOperator(), r)) {
						InfixExpression newInfix = expressionMaker.makeInfix(l, infix.getOperator(), r, type);
						expressionMaker.copyResults(expr, newInfix);
						addIfNew(curEquivalences, newInfix, expr, maxDepth);
					}
				}
		} else if (expr instanceof ArrayAccess) {
			ArrayAccess array = (ArrayAccess)expr;
			expandEquivalencesRec(array.getArray(), newlyExpanded, curEffects, maxDepth);
			Set<Effect> intermediateEffects = expressionMaker.getResult(array.getArray(), curEffects).getEffects();
			expandEquivalencesRec(array.getIndex(), newlyExpanded, intermediateEffects, maxDepth);
			for (Expression a : getEquivalentExpressions(array.getArray(), UnknownConstraint.getUnknownConstraint(), curEffects, maxDepth - 1))
				for (Expression i : getEquivalentExpressions(array.getIndex(), UnknownConstraint.getUnknownConstraint(), intermediateEffects, maxDepth - 1)) {
    				if (curMonitor.isCanceled())
    					throw new OperationCanceledException();
					ArrayAccess newArrayAccess = expressionMaker.makeArrayAccess(type, a, i);
					expressionMaker.copyResults(expr, newArrayAccess);
					addIfNew(curEquivalences, newArrayAccess, expr, maxDepth);
				}
		} else if (expr instanceof FieldAccess) {
			FieldAccess fieldAccess = (FieldAccess)expr;
			Field field = expressionMaker.getField(fieldAccess);
			expandEquivalencesRec(fieldAccess.getExpression(), newlyExpanded, curEffects, maxDepth);
			for (Expression e : getEquivalentExpressionsOrGiven(fieldAccess.getExpression(), new FieldConstraint(fieldAccess.getName().getIdentifier(), UnknownConstraint.getUnknownConstraint()), curEffects, maxDepth - 1)) {
				FieldAccess newFieldAccess = expressionMaker.makeFieldAccess(e, fieldAccess.getName().getIdentifier(), type, field);
				expressionMaker.copyResults(expr, newFieldAccess);
				addIfNew(curEquivalences, newFieldAccess, expr, maxDepth);
			}
		} else if (expr instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression)expr;
			expandEquivalencesRec(prefix.getOperand(), newlyExpanded, curEffects, maxDepth);
			for (Expression e : getEquivalentExpressions(prefix.getOperand(), UnknownConstraint.getUnknownConstraint(), curEffects, maxDepth - 1)) {
				PrefixExpression newPrefix = expressionMaker.makePrefix(e, prefix.getOperator());
				expressionMaker.copyResults(expr, newPrefix);
				addIfNew(curEquivalences, newPrefix, expr, maxDepth);
			}
		} else if (expr instanceof MethodInvocation) {
			MethodInvocation call = (MethodInvocation)expr;
			expandCall(call, call.getExpression(), expressionMaker.getMethod(call), call.arguments(), newlyExpanded, result, type, expr, maxDepth, curEquivalences, curEffects);
		} else if (expr instanceof ClassInstanceCreation) {
			ClassInstanceCreation call = (ClassInstanceCreation)expr;
			expandCall(call, expressionMaker.makeTypeLiteral(call.getType()), expressionMaker.getMethod(call), call.arguments(), newlyExpanded, result, type, expr, maxDepth, curEquivalences, curEffects);
		} else if (expr instanceof CastExpression) {
			CastExpression cast = (CastExpression)expr;
			expressionMaker.setResult(cast.getExpression(), result, curEffects);
			expandEquivalencesRec(cast.getExpression(), newlyExpanded, curEffects, maxDepth);
			// We don't want to make equivalences of the cast itself since our equivalences are based on the value, so anything equivalent to this will also be equivalent to the inner expression.  So if we made new casts here, we would be introducing duplicate expressions.
		} else
			throw new RuntimeException("Unexpected Expression " + expr.toString());
	}
	
	/**
	 * Adds the new expression to the given list if it is not
	 * equal to the current expression and if it is no larger than
	 * the given max depth.
	 * @param curEquivalences The list.
	 * @param newExpr The new expression.
	 * @param curExpr The old expression.
	 * @param The maximum depth.
	 */
	private void addIfNew(ArrayList<Expression> curEquivalences, Expression newExpr, Expression curExpr, int maxDepth) {
		if (!newExpr.equals(curExpr) && getDepth(newExpr) <= maxDepth)
			addEquivalentExpression(curEquivalences, newExpr);
	}

	/**
	 * Returns expressions that are equivalent to the given expression
	 * and at most the given max depth.
	 * If the expression is null or we do not know its value (and thus
	 * is not in our equivalences map), we simply return it.
	 * @param expr The expression for which we want to find equivalent
	 * expressions.
	 * @param constraint The constraint that should hold for the expressions.
	 * @param maxDepth The maximum depth of expressions to find.
	 * @return Expressions that are equivalent to the given expression,
	 * or itself if it is not in our equivalences map.
	 * @throws DebugException
	 */
	private ArrayList<Expression> getEquivalentExpressionsOrGiven(Expression expr, TypeConstraint constraint, Set<Effect> curEffects, int maxDepth) throws DebugException {
		if (expr == null || expressionMaker.getResult(expr, curEffects) == null) {
			ArrayList<Expression> result = new ArrayList<Expression>(1);
			if (getDepth(expr) <= maxDepth)
				result.add(expr);
			return result;
		} else {
			return getEquivalentExpressions(expr, constraint, curEffects, maxDepth);
		}
	}

	/**
	 * Returns expressions that are equivalent to the given expression and
	 * at most the given max depth by looking them up in the equivalences map.
	 * @param expr The expression for which we want to find equivalent
	 * expressions.
	 * @param constraint The constraint that should hold for the expressions.
	 * @param maxDepth The maximum depth of expressions to find.
	 * @return Expressions that are equivalent to the given expression.
	 * @throws DebugException
	 */
	private ArrayList<Expression> getEquivalentExpressions(Expression expr, TypeConstraint constraint, Set<Effect> curEffects, int maxDepth) throws DebugException {
		Result result = expressionMaker.getResult(expr, curEffects);
		return getEquivalentExpressions(result, expr, constraint, curEffects, maxDepth);
	}

	/**
	 * Returns expressions that have the given value and are at most the
	 * given max depth by looking them up in the equivalences map.
	 * It also adds the current expression if it is a non-zero constant
	 * and hence not in the equivalences map.
	 * @param result The result for which we want to find expressions
	 * with that result.
	 * @param curExpr The current expression.
	 * @param constraint The constraint that should hold for the expressions.
	 * @param maxDepth The maximum depth of expressions to find.
	 * @return Expressions that have the given expression.
	 * @throws DebugException
	 */
	private ArrayList<Expression> getEquivalentExpressions(Result result, Expression curExpr, TypeConstraint constraint, Set<Effect> curEffects, int maxDepth) throws DebugException {
		ArrayList<Expression> results = new ArrayList<Expression>();
		ArrayList<Expression> equivs = equivalences.get(curEffects).get(result);
		if (equivs != null || (!(curExpr instanceof NumberLiteral) && !expressionMaker.isStatic(curExpr))) {
			Set<String> fulfillingType = new HashSet<String>();
			IJavaType castType = null;
			if (curExpr instanceof ParenthesizedExpression)
				curExpr = ((ParenthesizedExpression)curExpr).getExpression();
			if (curExpr instanceof CastExpression)
				castType = ((CastExpression)curExpr).getStaticType();
			for (Expression expr: equivs) {
				// We might get things that are equivalent but with difference static types (e.g., Object and String when we want a String), so we ensure we satisfy the type constraint.
				// Otherwise, we downcast to the dynamic type (or, if the given expression is a cast, its type, as otherwise we might generate a different cast of it) if curExpr is non-null (if it is null, we have already downcast to this in the initial generation).
				// However, we have to special case static accesses/calls (e.g., Foo.bar), as the expression part has type Class not the desired type (Foo).
				if (getDepth(expr) > maxDepth)
					continue;
				if (isValidType(expr.getStaticType(), constraint, fulfillingType)
						|| expressionMaker.isStatic(expr) && (constraint instanceof FieldConstraint || constraint instanceof MethodConstraint))
					results.add(expr);
				else if (curExpr != null && !result.getValue().getValue().isNull() && isValidType(result.getValue().getValue().getJavaType(), constraint, fulfillingType))  // I think this will only fail if the value is null, so I could optimize it by confirming that and removing the extra work here.
					results.add((Expression)expressionMaker.makeParenthesized(downcast(expr, castType == null ? result.getValue().getValue().getJavaType() : castType)));
			}
		}
		// 0 is already in the equivalences map, but no other int constants are.
		if ((curExpr instanceof IntLiteral && ((IntLiteral)curExpr).getNumber() != 0) || curExpr instanceof StringLiteral || curExpr instanceof BooleanLiteral)
			results.add(curExpr);
		if (equivs == null && expressionMaker.isStatic(curExpr) && getDepth(curExpr) <= maxDepth)
			results.add(curExpr);
		filterBadCasts(results);
		return results;
	}
	
	/**
	 * If the given list contains some expressions that do not contain
	 * bad casts, removes all the expressions that do contain bad casts.
	 * Bad casts are casts added by getEquivalentExpressions when we
	 * already have a non-cast that is equivalent.
	 * This heuristic allows us to avoid using downcast expressions when
	 * we have equivalent non-downcast expressions.
	 * @param args The list of expressions from which to remove bad casts.
	 */
	private static void filterBadCasts(ArrayList<Expression> exprs) {
		boolean hasNonCast = false;
		boolean hasCast = false;
		for (Expression e: exprs) {
			if (hasCast && hasNonCast)  // Short-circuit the loop
				break;
			if (e == null)
				continue;
			boolean isCast = e instanceof ParenthesizedExpression && ((ParenthesizedExpression)e).getExpression() instanceof CastExpression;
			if (isCast)
				hasCast = true;
			else
				hasNonCast = true;
		}
		if (hasCast && hasNonCast) {  // Remove all expressions with casts,
			for (Iterator<Expression> it = exprs.iterator(); it.hasNext();) {
				Expression e = it.next();
				if (e != null && e instanceof ParenthesizedExpression && ((ParenthesizedExpression)e).getExpression() instanceof CastExpression)
					it.remove();
			}
		}
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
	private boolean isValidType(IJavaType type, TypeConstraint constraint, Set<String> fulfillingType) throws DebugException {
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
	private boolean isUsefulInfix(Expression l, InfixExpression.Operator op, Expression r) throws DebugException {
		IJavaValue rValue = expressionMaker.getValue(r, Collections.<Effect>emptySet());
		if (op == InfixExpression.Operator.PLUS)
			return !isConstant(l) && (r == one || (mightNotCommute(l, r) || l.toString().compareTo(r.toString()) < 0));
		else if (op == InfixExpression.Operator.TIMES)
			return !isConstant(l) && (/*r == two || */(mightNotCommute(l, r) || l.toString().compareTo(r.toString()) <= 0));
		else if (op == InfixExpression.Operator.MINUS)
			return !isConstant(l) && (r == one || (mightNotCommute(l, r) || l.toString().compareTo(r.toString()) != 0)) && !(r instanceof PrefixExpression && ((PrefixExpression)r).getOperator() == PrefixExpression.Operator.MINUS);
		else if (op == InfixExpression.Operator.DIVIDE)
			return !isConstant(l) && (/*r == two || */(mightNotCommute(l, r) || l.toString().compareTo(r.toString()) != 0));
		else if (ExpressionMaker.isInt(l.getStaticType()) && ExpressionMaker.isInt(r.getStaticType()))
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
	private boolean mightNotCommute(Expression l, Expression r) {
		return sideEffectHandler.isHandlingSideEffects() && (l instanceof MethodInvocation || r instanceof MethodInvocation);
	}

	/**
	 * Finds calls that are equivalent to the given one and
	 * adds them to curEquivalences.
	 * @param call The entire call expression.
	 * @param expression The expression part of the call.
	 * @param method The method being called.
	 * @param arguments The arguments.
	 * @param newlyExpanded The set of expressions we have already
	 * expanded to stop us from doing extra work or recursing infinitely.
	 * @param result The result of the call.
	 * @param type The type of the result of the call.
	 * @param valued The call itself.
	 * @param maxDepth The maximum depth of expressions.
	 * @param curEquivalences The set of expressions equivalent
	 * to this call.  The new expressions will be added to this set.
	 * @param curEffects The current effects.
	 * @throws DebugException
	 */
	private void expandCall(Expression call, Expression expression, Method method, Expression[] arguments, Set<Expression> newlyExpanded, Result result, IJavaType type, Expression valued, int maxDepth, ArrayList<Expression> curEquivalences, Set<Effect> curEffects) throws DebugException {
		String name = method.name();
		expandEquivalencesRec(expression, newlyExpanded, curEffects, maxDepth);
		IJavaType receiverType = EclipseUtils.getTypeAndLoadIfNeeded(method.declaringType().name(), stack, target, typeCache);
		if (receiverType == null)  // The above line will fail for anonymous classes.
			receiverType = (expression == null ? stack.getThis() : expressionMaker.getValue(expression, curEffects)).getJavaType(); 
		OverloadChecker overloadChecker = new OverloadChecker(receiverType, stack, target, typeCache, subtypeChecker);
		overloadChecker.setMethod(method);
		ArrayList<ArrayList<Expression>> newArguments = new ArrayList<ArrayList<Expression>>(arguments.length);
		ArrayList<TypeConstraint> argConstraints = new ArrayList<TypeConstraint>(arguments.length);
		Set<Effect> curArgEffects = expression == null || method.isConstructor() ? Collections.<Effect>emptySet() : expressionMaker.getResult(expression, curEffects).getEffects();
		for (int i = 0; i < arguments.length; i++) {
			Expression curArg = (Expression)arguments[i];
			expandEquivalencesRec(curArg, newlyExpanded, curArgEffects, maxDepth);
			ArrayList<Expression> allCurArgPossibilities = new ArrayList<Expression>();
			IJavaType argType = EclipseUtils.getTypeAndLoadIfNeeded((String)method.argumentTypeNames().get(i), stack, target, typeCache);
			TypeConstraint argConstraint = new SupertypeBound(argType);
			for (Expression arg : getEquivalentExpressions(curArg, argConstraint, curArgEffects, maxDepth - 1)) {
				if (overloadChecker.needsCast(argType, arg.getStaticType(), newArguments.size()))  // If the method is overloaded, when executing the expression we might get "Ambiguous call" compile errors, so we put in a cast to remove the ambiguity.
					arg = expressionMaker.makeCast(arg, argType);
				if (arg instanceof NullLiteral && method.isVarArgs() && newArguments.size() == arguments.length - 1)
					continue;//arg = expressionMaker.makeCast(arg, ((IJavaArrayType)argType).getComponentType(), arg.getValue(), valueCache, thread);
				allCurArgPossibilities.add(arg);
			}
			newArguments.add(allCurArgPossibilities);
			argConstraints.add(argConstraint);
			curArgEffects = expressionMaker.getResult(curArg, curArgEffects).getEffects();
		}
		int curDepth = getDepth(call);
		pruneManyArgCalls(newArguments, curDepth, curDepth - 1, method);
		List<Expression> newCalls = new ArrayList<Expression>();
		for (Expression e : getEquivalentExpressionsOrGiven(expression, new MethodConstraint(name, UnknownConstraint.getUnknownConstraint(), argConstraints, sideEffectHandler.isHandlingSideEffects()), curEffects, maxDepth - 1))
			makeAllCalls(method, name, e, newCalls, newArguments, new ArrayList<Expression>(newArguments.size()), type, curEffects, result, maxDepth);
		for (Expression newCall : newCalls) {
			expressionMaker.copyResults(call, newCall);
			addIfNew(curEquivalences, newCall, valued, maxDepth);
		}
	}
	
	/**
	 * Checks whether the given expression is a constant.
	 * @param e An expression.
	 * @return Whether the given expression is a constant.
	 */
	private static boolean isConstant(Expression e) {
		if (e instanceof NullLiteral || e instanceof NumberLiteral || e instanceof BooleanLiteral)
			return true;
		else if (e instanceof ParenthesizedExpression)
			return isConstant(((ParenthesizedExpression)e).getExpression());
		else
			return false;
	}
	
	/**
	 * Checks whether the given expression is a constant field.
	 * @param e The expression to check.
	 * @param expressionMaker The expression maker.
	 * @return Whether the given expression is a constant field.
	 */
	private static boolean isConstantField(Expression e, ExpressionMaker expressionMaker) {
		return BadConstantChecker.isConstantField(BadConstantChecker.getField(e, expressionMaker));
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
    	public boolean visit(IntLiteral node) {
    		visit(String.valueOf(node.getID()));
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
     * Class that checks whether an expression contains a call/access to
     * one of a few specified methods or methods/fields that we did
     * not see in real-world code.
     */
    private static class BadMethodFieldChecker extends ASTVisitor {
    	
    	private final ExpressionMaker expressionMaker;
    	private final Weights weights;
    	private boolean hasBad;
    	
    	public BadMethodFieldChecker(ExpressionMaker expressionMaker, Weights weights) {
    		this.expressionMaker = expressionMaker;
    		this.weights = weights;
    		hasBad = false;
    	}

		@Override
		public boolean visit(MethodInvocation node) {
			if (isBadMethod(expressionMaker.getMethod(node), node, weights))
				hasBad = true;
			return !hasBad;
		}

		@Override
		public boolean visit(FieldAccess node) {
			Field field = expressionMaker.getField(node);
			if (field != null && weights.isRare(field.declaringType().name(), field.name()))
				hasBad = true;
			return !hasBad;
		}
		
		public static boolean isBadMethod(Method method, Expression receiver, Weights weights) {
			return isNamedMethod(method.name(), receiver) || weights.isRare(method.declaringType().name(), Weights.getMethodKey(method));
		}
		
		private static boolean isNamedMethod(String name, Expression receiver) {
			// We do not want to include Integer.valueOf and friends.
			return name.equals("toString") || (name.equals("valueOf") && "String".equals(receiver.toString()))
					|| (name.equals("format") && "String".equals(receiver.toString()))
					|| name.equals("deepToString") || name.equals("compareTo") || name.equals("compareToIgnoreCase") || name.equals("compare");
		}
		
		/**
		 * Checks whether the given expression contains a call to
		 * one of a few specified methods or methods/fields that we did
		 * not see in real-world code.
		 * @param e The expression to check.
		 * @param expressionMaker The expression maker.
		 * @param weights Weight data about methods and fields.
		 * @param hasBadMethods Result cache to avoid recomputation
		 * when possible.
		 * @return Whether the given expression contains a call
		 * to one of a few specified methods.
		 */
		public static boolean hasBad(Expression e, ExpressionMaker expressionMaker, Weights weights, Map<Integer, Boolean> hasBadMethods) {
			return hasBad(e, new BadMethodFieldChecker(expressionMaker, weights), hasBadMethods);
		}
		
		/**
		 * Checks whether the given expression contains a call to
		 * one of a few specified methods or methods/fields that we did
		 * not see in real-world code.
		 * @param e The expression to check.
		 * @param checker The checker to use.
		 * @param hasBadMethods Result cache to avoid recomputation
		 * when possible.
		 * @return Whether the given expression contains a call
		 * to one of a few specified methods.
		 */
		public static boolean hasBad(Expression e, BadMethodFieldChecker checker, Map<Integer, Boolean> hasBadMethods) {
			if (checker.hasBad)
				return true;
	    	int id = e.getID();
	    	Object hasNamedMethodOpt = hasBadMethods.get(id);
	    	if (hasNamedMethodOpt != null) {
	    		checker.hasBad = ((Boolean)hasNamedMethodOpt).booleanValue();
	    		return checker.hasBad;
	    	}
	    	e.accept(checker);
	    	hasBadMethods.put(id, checker.hasBad);
	    	return checker.hasBad;
		}
		
    }
    
    /**
     * Class that checks whether an expression contains a call
     * that is mis-using a constant field or an infix expression
     * using a constant field that is not inside a call.
     */
    private static class BadConstantChecker extends ParentASTVisitor {
    	
    	private final ExpressionMaker expressionMaker;
    	private final Weights weights;
    	private boolean hasBad;
    	
    	public BadConstantChecker(ExpressionMaker expressionMaker, Weights weights) {
    		this.expressionMaker = expressionMaker;
    		this.weights = weights;
    		hasBad = false;
    	}

		@Override
		public boolean visit(MethodInvocation node) {
			Method method = expressionMaker.getMethod(node);
			if (weights.seenMethod(method)) {
				Expression[] args = node.arguments();
				for (int i = 0; i < args.length; i++) {
					Expression arg = args[i];
					if (isBadConstant(method, i, arg))
						return false;
				}
			}
			return !hasBad;
		}

		/**
		 * Checks whether using the given argument as the ith
		 * parameter to the given method is using a bad constant.
		 * @param method The method to call.
		 * @param i The index of the argument.
		 * @param arg The argument being used.
		 * @return Whether the given call would use a bad constant.
		 */
		public boolean isBadConstant(Method method, int i, Expression arg) {
			Field field = getField(arg, expressionMaker);
			if (isConstantField(field)) {
				if (weights.isBadConstant(method, i, field)) {
					hasBad = true;
					return true;
				}
			}
			return false;
		}
		
		@Override
		public boolean visit(InfixExpression node) {
			if ((ExpressionGenerator.isConstantField(node.getLeftOperand(), expressionMaker) || ExpressionGenerator.isConstantField(node.getRightOperand(), expressionMaker)) && !hasCallParent(node)) {
				hasBad = true;
				return false;
			}
			return !hasBad;
		}
		
		/**
		 * Checks whether the given expression is a constant field.
		 * @param e The expression to check.
		 * @param expressionMaker The expression maker.
		 * @return Whether the given expression is a constant field.
		 */
		public static Field getField(Expression e, ExpressionMaker expressionMaker) {
			if (e instanceof FieldAccess)
				return expressionMaker.getField(e);
			else
				return null;
		}
		
		/**
		 * Checks whether the given field is a constant.
		 * @param field The field to check.
		 * @return Whether the given field is a constant.
		 */
		public static boolean isConstantField(Field field) {
			return field != null && field.isPublic() && field.isStatic() && field.isFinal();
		}
		
		/**
		 * Checks whether the given expression is contained
		 * within a call or a constructor.
		 * @param e The expression.
		 * @return Whether the given expression is contained
		 * within a call or a constructor.
		 */
		private boolean hasCallParent(Expression e) {
			for (ASTNode cur: parents)
				if (cur instanceof MethodInvocation || cur instanceof ClassInstanceCreation)
					return true;
			return false;
		}
		
		/**
		 * Checks whether the given expression contains a call that
		 * misuses a constant field.
		 * @param e The expression to check.
		 * @param expressionMaker The expression maker.
		 * @param weights Weight data about methods and fields.
		 * @param hasBadConstants Result cache to avoid recomputation
		 * when possible.
		 * @return Whether the given expression contains a call
		 * that misuses a constant field.
		 */
		public static boolean hasBad(Expression e, ExpressionMaker expressionMaker, Weights weights, Map<Integer, Boolean> hasBadConstants) {
			return hasBad(e, new BadConstantChecker(expressionMaker, weights), hasBadConstants);
		}
		
		/**
		 * Checks whether the given expression contains a call that
		 * misuses a constant field.
		 * @param e The expression to check.
		 * @param checker The checker to use.
		 * @param hasBadConstants Result cache to avoid recomputation
		 * when possible.
		 * @return Whether the given expression contains a call
		 * that misuses a constant field.
		 */
		public static boolean hasBad(Expression e, BadConstantChecker checker, Map<Integer, Boolean> hasBadConstants) {
			if (checker.hasBad)
				return true;
	    	int id = e.getID();
	    	Object hasBadConstantOpt = hasBadConstants.get(id);
	    	if (hasBadConstantOpt != null) {
	    		checker.hasBad = ((Boolean)hasBadConstantOpt).booleanValue();
	    		return checker.hasBad;
	    	}
	    	e.accept(checker);
	    	hasBadConstants.put(id, checker.hasBad);
	    	return checker.hasBad;
		}
		
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
    	int id = expr.getID();
    	Object depthOpt = realDepths.get(id);
    	if (depthOpt != null)
    		return ((Integer)depthOpt).intValue();
    	IJavaType exprType = null;
    	try {
			Result result = expressionMaker.getResult(expr, Collections.<Effect>emptySet());
			if (result != null)  // result can be null if we are expanding equivalences.
				exprType = result.getValue().getValue().getJavaType();
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
    	Integer numBooleansSeen = uniqueValuesSeenForType.get("boolean");
		int depth = getDepthImpl(expr) + (UniqueASTChecker.isUnique(expr, varName) ? 0 : 1) + (BadMethodFieldChecker.hasBad(expr, expressionMaker, weights, hasBadMethodsFields) ? 1 : 0) + (BadConstantChecker.hasBad(expr, expressionMaker, weights, hasBadConstants) ? 1 : 0) + (numBooleansSeen != null && numBooleansSeen == 2 && booleanType.equals(exprType) ? 1 : 0);
		realDepths.put(id, depth);
		return depth;
    }
    
    /**
     * Gets the depth of a call with the given receiver,
     * arguments, and method.  We special case this
     * because constructing a new expression can be
     * expensive.
     * @param receiver The receiver of the call.
     * @param args The arguments to the call.
     * @param method The method being called.
     * @return The depth of a call with the given receiver,
     * arguments, and method name.
     */
    private int getDepthOfCall(Expression receiver, ArrayList<Expression> args, Method method) {
    	int depth = getDepthImpl(receiver);
    	for (Expression arg: args)
    		depth = Math.max(depth, getDepthImpl(arg));
    	UniqueASTChecker uniqueChecker = new UniqueASTChecker(varName);
    	BadMethodFieldChecker badMethodFieldChecker = new BadMethodFieldChecker(expressionMaker, weights);
    	BadConstantChecker badConstantChecker = new BadConstantChecker(expressionMaker, weights);
    	if (receiver != null) {
    		UniqueASTChecker.isUnique(receiver, uniqueChecker);
			BadMethodFieldChecker.hasBad(receiver, badMethodFieldChecker, hasBadMethodsFields);
			BadConstantChecker.hasBad(receiver, badConstantChecker, hasBadConstants);
    	}
    	for (int i = 0; i < args.size(); i++) {
    		Expression arg = args.get(i);
    		UniqueASTChecker.isUnique(arg, uniqueChecker);
			BadMethodFieldChecker.hasBad(arg, badMethodFieldChecker, hasBadMethodsFields);
			BadConstantChecker.hasBad(arg, badConstantChecker, hasBadConstants);
			badConstantChecker.isBadConstant(method, i, arg);
    	}
    	Integer numBooleansSeen = uniqueValuesSeenForType.get("boolean");
    	return depth + 1 + (uniqueChecker.isUnique ? 0 : 1) + (badMethodFieldChecker.hasBad || BadMethodFieldChecker.isBadMethod(method, receiver, weights) ? 1 : 0) + (badConstantChecker.hasBad ? 1 : 0) + (numBooleansSeen != null && numBooleansSeen == 2 && "boolean".equals(method.returnTypeName()) ? 1 : 0);
    }

    /**
     * Gets the depth of the given expression.
     * This should not be called by clients; please use
     * getDepth instead.
     * @param expr The expression whose depth we want.
     * @return The depth of the given expression.
     */
    private int getDepthImpl(Expression expr) {
    	if (expr == null)
    		return 0;
    	Object depthOpt = expressionMaker.getDepthOpt(expr);
    	if (depthOpt != null)
    		return ((Integer)depthOpt).intValue();
    	int depth;
    	if (expr instanceof NumberLiteral || expr instanceof BooleanLiteral || expr instanceof Name || expr instanceof ThisExpression || expr instanceof NullLiteral || expr instanceof TypeLiteral)
			depth = 0;
    	else if (expr instanceof ParenthesizedExpression)
    		depth = getDepthImpl(((ParenthesizedExpression)expr).getExpression());
		else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			depth = Math.max(getDepthImpl(infix.getLeftOperand()), getDepthImpl(infix.getRightOperand())) + 1;
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
			for (Expression curArg: call.arguments()) {
				int curArgDepth = getDepthImpl(curArg);
				if (curArgDepth > maxChildDepth)
					maxChildDepth = curArgDepth;
			}
			depth = maxChildDepth + 1;
		} else if (expr instanceof ClassInstanceCreation) {
			ClassInstanceCreation call = (ClassInstanceCreation)expr;
			int maxChildDepth = call.getExpression() == null ? 0 : getDepthImpl(call.getExpression());
			for (Expression curArg: call.arguments()) {
				int curArgDepth = getDepthImpl(curArg);
				if (curArgDepth > maxChildDepth)
					maxChildDepth = curArgDepth;
			}
			depth = maxChildDepth + 1;
		} else if (expr instanceof CastExpression) {
			depth = getDepthImpl(((CastExpression)expr).getExpression());
		} else
			throw new RuntimeException("Unexpected expression " + expr.toString());
    	expressionMaker.setDepth(expr, depth);
    	return depth;
    }
    
    // Informative utility methods.
    
	@SuppressWarnings("unused")
	private void printEquivalenceInfo() {
		if (equivalences.isEmpty())
			return;
		System.out.println("Exprs:");
    	for (ArrayList<Expression> equivClass: equivalences.get(Collections.<Effect>emptySet()).values()) {
    		for (Expression expr: equivClass)
    			System.out.println(expr);
    	}
		System.out.println("Types:");
    	Map<String, ArrayList<Result>> typeBuckets = new HashMap<String, ArrayList<Result>>();
    	try {
    		for (Map.Entry<Result, ArrayList<Expression>> equivClass: equivalences.get(Collections.<Effect>emptySet()).entrySet())
    			Utils.addToListMap(typeBuckets, EclipseUtils.getTypeName(equivClass.getKey().getValue().getValue().getJavaType()), equivClass.getKey());
    	} catch (DebugException e) {
    		throw new RuntimeException(e);
    	}
    	List<Map.Entry<String, ArrayList<Result>>> typeBucketEntries = new ArrayList<Map.Entry<String,ArrayList<Result>>>(typeBuckets.entrySet());
    	Collections.sort(typeBucketEntries, new Comparator<Map.Entry<String, ArrayList<Result>>>() {
			@Override
			public int compare(Entry<String, ArrayList<Result>> o1, Entry<String, ArrayList<Result>> o2) {
				return o2.getValue().size() - o1.getValue().size();
			}
		});
    	for (Map.Entry<String, ArrayList<Result>> bucket: typeBucketEntries)
    		System.out.println(bucket.getKey() + " -> " + bucket.getValue().size() + " (" + Utils.truncate(Utils.getPrintableString(bucket.getValue().toString()), 200) + ")");
		System.out.println("Equivalences:");
    	for (Map.Entry<Result, ArrayList<Expression>> equivClass: equivalences.get(Collections.<Effect>emptySet()).entrySet()) {
    		System.out.println(equivClass.getKey().toString().replace("\n", "\\n") + " -> " + equivClass.getValue().size() + " (" + Utils.truncate(Utils.getPrintableString(equivClass.getValue().toString()), 200) + ")");
    	}
    	System.out.println("Buckets: ");
    	Map<Integer, ArrayList<Result>> buckets = new HashMap<Integer, ArrayList<Result>>();
    	for (Map.Entry<Result, ArrayList<Expression>> equivClass: equivalences.get(Collections.<Effect>emptySet()).entrySet())
    		Utils.addToListMap(buckets, equivClass.getValue().size(), equivClass.getKey());
    	for (Integer bucket: new java.util.TreeSet<Integer>(buckets.keySet()))
    		System.out.println(bucket + " -> " + buckets.get(bucket).size() + " (" + Utils.truncate(Utils.getPrintableString(buckets.get(bucket).toString()), 200) + ")");
    }

	@SuppressWarnings("unused")
	private void getMaxLineInfo() {
    	Map<Integer, ArrayList<Expression>> buckets = new HashMap<Integer, ArrayList<Expression>>();
		for (ArrayList<Expression> exprs: equivalences.get(Collections.<Effect>emptySet()).values())
			for (Expression e: exprs)
				Utils.addToListMap(buckets, getMaxLines(e, null), e);
    	for (Integer bucket: new java.util.TreeSet<Integer>(buckets.keySet()))
    		System.out.println(bucket + " -> " + buckets.get(bucket).size() + " (e.g., " + buckets.get(bucket).get(0) + ")");
	}
	
	private int getMaxLines(Expression expr, Expression parent) {
		if (expr == null)
			return 0;
    	if (expr instanceof NumberLiteral || expr instanceof BooleanLiteral || expr instanceof Name || expr instanceof ThisExpression || expr instanceof NullLiteral || expr instanceof TypeLiteral)
			return parent == null ? 1 : 0;
    	else if (expr instanceof ParenthesizedExpression)
    		return getMaxLines(((ParenthesizedExpression)expr).getExpression(), expr);
		else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			return getMaxLines(infix.getLeftOperand(), expr) + getMaxLines(infix.getRightOperand(), expr) + 1;
		} else if (expr instanceof ArrayAccess) {
			ArrayAccess array = (ArrayAccess)expr;
			return getMaxLines(array.getArray(), expr) + getMaxLines(array.getIndex(), expr) + 1;
		} else if (expr instanceof FieldAccess) {
			return getMaxLines(((FieldAccess)expr).getExpression(), expr) + 1;
		} else if (expr instanceof PrefixExpression) {
			return getMaxLines(((PrefixExpression)expr).getOperand(), expr) + 1;
		} else if (expr instanceof MethodInvocation) {
			MethodInvocation call = (MethodInvocation)expr;
			int curNumLines = getMaxLines(call.getExpression(), expr);
			for (Expression arg: call.arguments())
				curNumLines += getMaxLines(arg, expr);
			return curNumLines + 1;
		} else if (expr instanceof ClassInstanceCreation) {
			ClassInstanceCreation call = (ClassInstanceCreation)expr;
			int curNumLines = getMaxLines(call.getExpression(), expr);
			for (Expression arg: call.arguments())
				curNumLines += getMaxLines(arg, expr);
			return curNumLines + 1;
		} else if (expr instanceof CastExpression) {
			return getMaxLines(((CastExpression)expr).getExpression(), expr);
		} else
			throw new RuntimeException("Unexpected expression " + expr.toString());
	}

}
