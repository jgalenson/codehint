package codehint.exprgen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaInterfaceType;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.expreval.EvaluatedExpression;
import codehint.expreval.StaticEvaluator;
import codehint.utils.EclipseUtils;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Method;

public class ExpressionMaker {

	private final static AST ast = AST.newAST(AST.JLS4);
	
	private int id;
	private final Map<Integer, IJavaValue> values;
	private final Map<Integer, Method> methods;
	private final Set<Integer> statics;
	private final Map<Integer, Integer> depths;
	private final ValueCache valueCache;
	
	public ExpressionMaker(ValueCache valueCache) {
		id = 0;
		values = new HashMap<Integer, IJavaValue>();
		methods = new HashMap<Integer, Method>();
		statics = new HashSet<Integer>();
		depths = new HashMap<Integer, Integer>();
		this.valueCache = valueCache;
	}

	public static boolean isInt(IJavaType type) throws DebugException {
		return type != null && "I".equals(type.getSignature());
	}

	public static boolean isBoolean(IJavaType type) throws DebugException {
		return type != null && "Z".equals(type.getSignature());
	}

	public static boolean isLong(IJavaType type) throws DebugException {
		return type != null && "J".equals(type.getSignature());
	}

	public static boolean isByte(IJavaType type) throws DebugException {
		return type != null && "B".equals(type.getSignature());
	}

	public static boolean isChar(IJavaType type) throws DebugException {
		return type != null && "C".equals(type.getSignature());
	}

	public static boolean isShort(IJavaType type) throws DebugException {
		return type != null && "S".equals(type.getSignature());
	}

	public static boolean isFloat(IJavaType type) throws DebugException {
		return type != null && "F".equals(type.getSignature());
	}

	public static boolean isDouble(IJavaType type) throws DebugException {
		return type != null && "D".equals(type.getSignature());
	}

	public static boolean isObjectOrInterface(IJavaType type) {
		return type instanceof IJavaClassType || type instanceof IJavaInterfaceType;
	}

	// Evaluation helpers that compute IJavaValues and IJavaTypes.

	private IJavaValue computeInfixOp(IJavaValue left, InfixExpression.Operator op, IJavaValue right, IJavaType type) throws NumberFormatException, DebugException {
		if (isInt(type))
			return computeIntInfixOp(left, op, right);
		else if (isBoolean(type))
			return computeBooleanInfixOp(left, op, right);
		else if (type instanceof IJavaReferenceType)
			return computeRefInfixOp(left, op, right);
		else
			throw new RuntimeException("Unexpected type: " + type);
	}

	private IJavaValue computeIntInfixOp(IJavaValue left, InfixExpression.Operator op, IJavaValue right) throws NumberFormatException, DebugException {
		if (left == null || right == null)
			return null;
		int l = Integer.parseInt(left.getValueString());
		int r = Integer.parseInt(right.getValueString());
		if (op == InfixExpression.Operator.PLUS)
			return valueCache.getIntJavaValue(l + r);
		if (op == InfixExpression.Operator.MINUS)
			return valueCache.getIntJavaValue(l - r);
		if (op == InfixExpression.Operator.TIMES)
			return valueCache.getIntJavaValue(l * r);
		if (op == InfixExpression.Operator.DIVIDE) {
			assert r != 0;
			return valueCache.getIntJavaValue(l / r);
		}
		if (op == InfixExpression.Operator.EQUALS)
			return valueCache.getBooleanJavaValue(l == r);
		if (op == InfixExpression.Operator.NOT_EQUALS)
			return valueCache.getBooleanJavaValue(l != r);
		if (op == InfixExpression.Operator.LESS)
			return valueCache.getBooleanJavaValue(l < r);
		if (op == InfixExpression.Operator.LESS_EQUALS)
			return valueCache.getBooleanJavaValue(l <= r);
		if (op == InfixExpression.Operator.GREATER)
			return valueCache.getBooleanJavaValue(l > r);
		if (op == InfixExpression.Operator.GREATER_EQUALS)
			return valueCache.getBooleanJavaValue(l >= r);
		throw new RuntimeException("Unknown infix operation: " + op.toString());
	}

	private IJavaValue computeBooleanInfixOp(IJavaValue left, InfixExpression.Operator op, IJavaValue right) throws DebugException {
		if (left == null || right == null)
			return null;
		boolean l = Boolean.parseBoolean(left.getValueString());
		boolean r = Boolean.parseBoolean(right.getValueString());
		if (op == InfixExpression.Operator.EQUALS)
			return valueCache.getBooleanJavaValue(l == r);
		if (op == InfixExpression.Operator.NOT_EQUALS)
			return valueCache.getBooleanJavaValue(l != r);
		if (op == InfixExpression.Operator.CONDITIONAL_AND)
			return valueCache.getBooleanJavaValue(l && r);
		if (op == InfixExpression.Operator.CONDITIONAL_OR)
			return valueCache.getBooleanJavaValue(l || r);
		throw new RuntimeException("Unknown infix operation: " + op.toString());
	}

	private IJavaValue computeRefInfixOp(IJavaValue left, InfixExpression.Operator op, IJavaValue right) throws DebugException {
		if (left == null || right == null)
			return null;
		IJavaObject l = (IJavaObject)left;
		IJavaObject r = (IJavaObject)right;
		if (op == InfixExpression.Operator.EQUALS)
			return valueCache.getBooleanJavaValue(l.getUniqueId() == r.getUniqueId());
		if (op == InfixExpression.Operator.NOT_EQUALS)
			return valueCache.getBooleanJavaValue(l.getUniqueId() != r.getUniqueId());
		IJavaType lType = l.getJavaType();
		IJavaType rType = r.getJavaType();
		if (op == InfixExpression.Operator.PLUS && ((lType != null && "java.lang.String".equals(lType.getName())) || (rType != null && "java.lang.String".equals(rType.getName()))))
			return valueCache.getStringJavaValue(l.getValueString() + r.getValueString());
		throw new RuntimeException("Unknown infix operation: " + op.toString() + " for types " + lType.toString() + " and " + rType.toString());
	}

	private IJavaValue computePrefixOp(IJavaValue e, PrefixExpression.Operator op) throws DebugException {
		if (e == null )
			return null;
		if (op == PrefixExpression.Operator.MINUS)
			return valueCache.getIntJavaValue(-Integer.parseInt(e.getValueString()));
		if (op == PrefixExpression.Operator.NOT)
			return valueCache.getBooleanJavaValue(!Boolean.parseBoolean(e.getValueString()));
		throw new RuntimeException("Unknown prefix operation: " + op.toString());
	}

	private static IJavaValue computePostfixOp(@SuppressWarnings("unused") IJavaDebugTarget target, IJavaValue e, PostfixExpression.Operator op) {
		if (e == null )
			return null;
		throw new RuntimeException("Unknown postfix operation: " + op.toString());
	}
	
	private static IJavaValue computeConditionalOp(IJavaValue c, IJavaValue t, IJavaValue e) throws DebugException {
		if (c == null || t == null || e == null)
			return null;
		boolean cond = Boolean.parseBoolean(c.getValueString());
		if (cond)
			return t;
		else
			return e;
	}

	/*
	 * If we call this code, things that crash trigger breakpoints for some reason.
	 * Some things work when I modify the ChoiceBreakpointListener to resume the
	 * thread rather than handle things normally, but some things break....
	 */
	/*private static IJavaValue computeCall(final Method method, final IJavaValue receiver, ArrayList<TypedExpression> args, final IJavaThread thread, IJavaDebugTarget target, final JDIType receiverType) {
		final IJavaValue[] argValues = new IJavaValue[args.size()];
		for (int i = 0; i < args.size(); i++) {
			if (args.get(i).getValue() == null)
				return null;
			argValues[i] = args.get(i).getValue();
		}
		try {
			//System.out.println("Calling " + (receiver != null ? receiver : receiverType) + "." + method.name() + " with args " + Arrays.toString(argValues));
			IJavaValue value = null;
			if (receiver == null && "<init>".equals(method.name()))
				value = ((IJavaClassType)receiverType).newInstance(method.signature(), argValues, thread);
			else if (receiver == null)
				value = ((IJavaClassType)receiverType).sendMessage(method.name(), method.signature(), argValues, thread);
			else
				value = ((IJavaObject)receiver).sendMessage(method.name(), method.signature(), argValues, thread, !method.declaringType().equals(receiverType.getUnderlyingType()));
			//System.out.println("Got " + value);
			return value;
		} catch (DebugException e) {
			//System.out.println("Crashed.");
			return target.voidValue();
		}
	}*/

	// Helper methods to create AST nodes, as they don't seem to have useful constructors.

	/*
	 * My nodes can have the following properties:
	 * isStatic: Exists (and is true) iff the expression is a static name.
	 * value: The value of the expression, or null if it is unknown..  All nodes should have this.
	 * isConstant: Exists (and is true) iff the expression is a constant (e.g., 0, true, null).
	 */

	// Pass in cached int type for efficiency.
	public TypedExpression makeNumber(String val, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		Expression e = ast.newNumberLiteral(val);
		//e.setProperty("isConstant", true);
		setID(e);
		setExpressionValue(e, value);
		return new EvaluatedExpression(e, type, Value.makeValue(value, valueCache, thread));
	}

	// Pass in cached boolean type for efficiency.
	public TypedExpression makeBoolean(boolean val, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		Expression e = ast.newBooleanLiteral(val);
		//e.setProperty("isConstant", true);
		setID(e);
		setExpressionValue(e, value);
		return new EvaluatedExpression(e, type, Value.makeValue(value, valueCache, thread));
	}

	public TypedExpression makeNull(IJavaDebugTarget target, ValueCache valueCache, IJavaThread thread) {
		Expression e = ast.newNullLiteral();
		//e.setProperty("isConstant", true);
		IJavaValue value = target.nullValue();
		setID(e);
		setExpressionValue(e, value);
		return new EvaluatedExpression(e, null, Value.makeValue(value, valueCache, thread));
	}

	public TypedExpression makeVar(String name, IJavaValue value, IJavaType type, boolean isFieldAccess, ValueCache valueCache, IJavaThread thread) {
		Expression e = ast.newSimpleName(name);
		setID(e);
		setExpressionValue(e, value);
		if (isFieldAccess)
			setDepth(e, 1);
		return new EvaluatedExpression(e, type, Value.makeValue(value, valueCache, thread));
	}

	private Expression newStaticName(String name, IJavaValue value) {
		Expression e = makeName(name);
		setStatic(e);
		setExpressionValue(e, value);
		return e;
	}

	public TypedExpression makeStaticName(String name, IJavaReferenceType type, ValueCache valueCache, IJavaThread thread) {
		try {
			IJavaValue value = type.getClassObject();
			return new EvaluatedExpression(newStaticName(name, value), type, Value.makeValue(value, valueCache, thread));
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	public TypedExpression makeThis(IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		ThisExpression e = ast.newThisExpression();
		setID(e);
		setExpressionValue(e, value);
		return new EvaluatedExpression(e, type, Value.makeValue(value, valueCache, thread));
	}

	public TypedExpression makeInfix(TypedExpression left, InfixExpression.Operator op, TypedExpression right, IJavaType type, ValueCache valueCache, IJavaThread thread) throws NumberFormatException, DebugException {
		InfixExpression e = makeInfix(left.getExpression(), op, right.getExpression());
		IJavaValue value = computeInfixOp(left.getValue(), op, right.getValue(), left.getType() != null ? left.getType() : right.getType());
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, value, valueCache, thread);
	}

	public InfixExpression makeInfix(Expression l, InfixExpression.Operator op, Expression r) {
		InfixExpression e = ast.newInfixExpression();
		e.setLeftOperand(parenIfNeeded(ASTCopyer.copy(l)));
		e.setOperator(op);
		e.setRightOperand(parenIfNeeded(ASTCopyer.copy(r)));
		setID(e);
		return e;
	}

	public TypedExpression makeArrayAccess(TypedExpression array, TypedExpression index, IJavaValue value, ValueCache valueCache, IJavaThread thread) {
		ArrayAccess e = makeArrayAccess(array.getExpression(), index.getExpression());
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, getArrayElementType(array), value, valueCache, thread);
	}

	public ArrayAccess makeArrayAccess(Expression array, Expression index) {
		ArrayAccess e = ast.newArrayAccess();
		e.setArray(ASTCopyer.copy(array));
		e.setIndex(ASTCopyer.copy(index));
		setID(e);
		return e;
	}

	public TypedExpression makeFieldAccess(TypedExpression obj, String name, IJavaType fieldType, IJavaValue value, ValueCache valueCache, IJavaThread thread) {
		FieldAccess e = makeFieldAccess(obj.getExpression(), name);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, fieldType, value, valueCache, thread);
	}

	public FieldAccess makeFieldAccess(Expression obj, String name) {
		FieldAccess e = ast.newFieldAccess();
		e.setExpression(ASTCopyer.copy(obj));
		e.setName(makeSimpleName(name));
		setID(e);
		return e;
	}

	public TypedExpression makePrefix(TypedExpression operand, PrefixExpression.Operator op, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		try {
			PrefixExpression e = makePrefix(operand.getExpression(), op);
			IJavaValue value = computePrefixOp(operand.getValue(), op);
			setExpressionValue(e, value);
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, value, valueCache, thread);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public PrefixExpression makePrefix(Expression operand, PrefixExpression.Operator op) {
		PrefixExpression e = ast.newPrefixExpression();
		e.setOperand(parenIfNeeded(ASTCopyer.copy(operand)));
		e.setOperator(op);
		setID(e);
		return e;
	}

	public TypedExpression makePostfix(IJavaDebugTarget target, TypedExpression operand, PostfixExpression.Operator op, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		PostfixExpression e = makePostfix(operand.getExpression(), op);
		IJavaValue value = computePostfixOp(target, operand.getValue(), op);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, value, valueCache, thread);
	}

	private PostfixExpression makePostfix(Expression operand, PostfixExpression.Operator op) {
		PostfixExpression e = ast.newPostfixExpression();
		e.setOperand(parenIfNeeded(ASTCopyer.copy(operand)));
		e.setOperator(op);
		setID(e);
		return e;
	}

	public TypedExpression makeCall(String name, TypedExpression receiver, ArrayList<? extends TypedExpression> args, IJavaType returnType, IJavaType thisType, Method method, IJavaDebugTarget target, ValueCache valueCache, IJavaThread thread, StaticEvaluator staticEvaluator) {
		//IJavaValue value = computeCall(method, receiver.getValue(), args, thread, target, ((JDIType)receiver.getType()));
		IJavaValue value = staticEvaluator.evaluateCall(receiver, args, method, target);
		TypedExpression result = null;
		if (receiver.getExpression() == null) {
			assert "<init>".equals(name);
			try {
				result = makeClassInstanceCreation(receiver.getType(), args, value, valueCache, thread);
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		} else {
			if (receiver.getExpression() instanceof ThisExpression || receiver.getType().equals(thisType))
				receiver = null;  // Don't use a receiver if it is null or the this type.
			result = makeCall(name, receiver == null ? null : ASTCopyer.copy(receiver.getExpression()), args, returnType, value, valueCache, thread);
		}
		setMethod(result.getExpression(), method);
		return result;
	}
	/*private TypedExpression makeCall(String name, String classname, ArrayList<TypedExpression> args, IJavaType returnType) {
    	return makeCall(name, newStaticName(classname), args, returnType, null);
    }*/
	@SuppressWarnings("unchecked")
	private TypedExpression makeCall(String name, Expression receiver, ArrayList<? extends TypedExpression> args, IJavaType returnType, IJavaValue value, ValueCache valueCache, IJavaThread thread) {
		MethodInvocation e = ast.newMethodInvocation();
		e.setName(makeSimpleName(name));
		e.setExpression(receiver);
		for (TypedExpression ex: args)
			e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setID(e);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, returnType, value, valueCache, thread);
	}
	@SuppressWarnings("unchecked")
	public Expression makeCall(String name, Expression receiver, ArrayList<TypedExpression> args, Method method) {
    	MethodInvocation e = ast.newMethodInvocation();
    	e.setName(makeSimpleName(name));
    	e.setExpression(ASTCopyer.copy(receiver));
    	for (TypedExpression ex: args)
    		e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setID(e);
		setMethod(e, method);
    	return e;
    }

	public TypedExpression makeCast(TypedExpression obj, IJavaType targetType, IJavaValue value, ValueCache valueCache, IJavaThread thread) {
		CastExpression e = makeCast(obj.getExpression(), targetType);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, targetType, value, valueCache, thread);
	}

	public CastExpression makeCast(Expression obj, IJavaType targetType) {
		CastExpression e = ast.newCastExpression();
		e.setExpression(ASTCopyer.copy(obj));
		e.setType(makeType(targetType));
		setID(e);
		return e;
	}

	public TypedExpression makeInstanceOf(TypedExpression obj, Type targetDomType, IJavaType targetType, IJavaValue value, ValueCache valueCache, IJavaThread thread) {
		InstanceofExpression e = makeInstanceOf(obj.getExpression(), targetDomType);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, targetType, value, valueCache, thread);
	}

	private InstanceofExpression makeInstanceOf(Expression expr, Type targetDomType) {
		InstanceofExpression e = ast.newInstanceofExpression();
		e.setLeftOperand(ASTCopyer.copy(expr));
		e.setRightOperand(targetDomType);
		setID(e);
		return e;
	}

	public TypedExpression makeConditional(TypedExpression cond, TypedExpression t, TypedExpression e, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		try {
			ConditionalExpression ex = makeConditional(cond.getExpression(), t.getExpression(), e.getExpression());
			IJavaValue value = computeConditionalOp(cond.getValue(), t.getValue(), e.getValue());
			setExpressionValue(ex, value);
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(ex, type, value, valueCache, thread);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	private ConditionalExpression makeConditional(Expression cond, Expression t, Expression e) {
		ConditionalExpression ex = ast.newConditionalExpression();
		ex.setExpression(ASTCopyer.copy(cond));
		ex.setThenExpression(ASTCopyer.copy(t));
		ex.setElseExpression(ASTCopyer.copy(e));
		setID(ex);
		return ex;
	}

	@SuppressWarnings("unchecked")
	private TypedExpression makeClassInstanceCreation(IJavaType type, ArrayList<? extends TypedExpression> args, IJavaValue value, ValueCache valueCache, IJavaThread thread) throws DebugException {
		ClassInstanceCreation e = ast.newClassInstanceCreation();
		e.setType(ast.newSimpleType(makeName(EclipseUtils.sanitizeTypename(type.getName()))));
		for (TypedExpression ex: args)
			e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setID(e);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, value, valueCache, thread);
	}

	@SuppressWarnings("unchecked")
	public ClassInstanceCreation makeClassInstanceCreation(Type type,ArrayList<TypedExpression> args, Method method) {
    	ClassInstanceCreation e = ast.newClassInstanceCreation();
    	e.setType(ASTCopyer.copy(type));
    	for (TypedExpression ex: args)
    		e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setID(e);
		setMethod(e, method);
    	return e;
	}

	public TypedExpression makeParenthesized(TypedExpression obj, ValueCache valueCache, IJavaThread thread) {
		ParenthesizedExpression e = makeParenthesized(obj.getExpression());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, obj.getType(), getExpressionValue(e), valueCache, thread);
	}

	private ParenthesizedExpression makeParenthesized(Expression e) {
		ParenthesizedExpression p = ast.newParenthesizedExpression();
		p.setExpression(e);
		setID(p);
		setExpressionValue(p, getExpressionValue(e));
		return p;
	}

	public TypedExpression makeSuperFieldAccess(Name qualifier, String name, IJavaType fieldType, IJavaValue value, ValueCache valueCache, IJavaThread thread) {
		SuperFieldAccess e = makeSuperFieldAccess(qualifier, name);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, fieldType, value, valueCache, thread);
	}

	private SuperFieldAccess makeSuperFieldAccess(Name qualifier, String name) {
		SuperFieldAccess e = ast.newSuperFieldAccess();
		e.setQualifier((Name)ASTCopyer.copy(qualifier));
		e.setName(makeSimpleName(name));
		setID(e);
		return e;
	}

	@SuppressWarnings("unchecked")
	public TypedExpression makeSuperCall(String name, Name qualifier, ArrayList<TypedExpression> args, IJavaType returnType, IJavaValue value, Method method, ValueCache valueCache, IJavaThread thread) {
		SuperMethodInvocation e = ast.newSuperMethodInvocation();
		e.setName(makeSimpleName(name));
		e.setQualifier(qualifier);
		for (TypedExpression ex: args)
			e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setID(e);
		setExpressionValue(e, value);
		setMethod(e, method);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, returnType, value, valueCache, thread);
	}
	
	public TypeLiteral makeTypeLiteral(Type type) {
		TypeLiteral e = ast.newTypeLiteral();
		e.setType(ASTCopyer.copy(type));
		setID(e);
		return e;
	}

	private Type makeType(IJavaType type) {
		try {
			if (type instanceof IJavaArrayType)
				return ast.newArrayType(makeType(((IJavaArrayType)type).getComponentType()));
			else if (type instanceof IJavaReferenceType)
				return ast.newSimpleType(makeName(type.getName()));
			else if (isInt(type))
				return ast.newPrimitiveType(PrimitiveType.INT);
			else if (isBoolean(type))
				return ast.newPrimitiveType(PrimitiveType.BOOLEAN);
			else if (isLong(type))
				return ast.newPrimitiveType(PrimitiveType.LONG);
			else if (isByte(type))
				return ast.newPrimitiveType(PrimitiveType.BYTE);
			else if (isChar(type))
				return ast.newPrimitiveType(PrimitiveType.CHAR);
			else if (isShort(type))
				return ast.newPrimitiveType(PrimitiveType.SHORT);
			else if (isFloat(type))
				return ast.newPrimitiveType(PrimitiveType.FLOAT);
			else if (isDouble(type))
				return ast.newPrimitiveType(PrimitiveType.DOUBLE);
			else
				throw new RuntimeException("Unexpected type " + type);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	private SimpleName makeSimpleName(String name) {
		SimpleName e = ast.newSimpleName(name);
		setID(e);
		return e;
	}
	
	private Name makeName(String name) {
		Name e = ast.newName(name);
		for (Name cur = e; true; ) {
			setID(cur);
			if (cur instanceof QualifiedName) {
				setID(((QualifiedName)cur).getName());
				cur = ((QualifiedName)cur).getQualifier();
			} else
				break;
		}
		return e;
	}

	private void setID(Expression e) {
		e.setProperty("id", id++);
	}

	public static int getID(Expression e) {
		return (Integer)e.getProperty("id");
	}
	
	public void setIDIfNeeded(Expression e) {
		if (e.getProperty("id") == null)
			setID(e);
	}

	public static Object getIDOpt(Expression e) {
		return e.getProperty("id");
	}

	public void setExpressionValue(Expression e, IJavaValue v) {
		values.put(getID(e), v);
	}

	public IJavaValue getExpressionValue(Expression e) {
		return values.get(getID(e));
	}

	public IJavaValue getExpressionValue(int id) {
		return values.get(id);
	}

	public void setDepth(Expression e, int d) {
		depths.put(getID(e), d);
	}

	public Object getDepthOpt(Expression e) {
		return depths.get(getID(e));
	}

	private void setStatic(Expression e) {
		statics.add(getID(e));
	}

	public boolean isStatic(Expression e) {
		return statics.contains(getID(e));
	}

	private void setMethod(Expression e, Method method) {
		methods.put(getID(e), method);
	}

	public Method getMethod(Expression e) {
		return methods.get(getID(e));
	}

	public Method getMethod(int id) {
		return methods.get(id);
	}

	/**
	 * Parenthesize an expression if it is an infix or prefix expression.
	 * @param e The expression.
	 * @return The given expression parenthesized if it is infix
	 * or prefix, and the expression itself otherwise.
	 */
	private Expression parenIfNeeded(Expression e) {
		if (e instanceof InfixExpression || e instanceof PrefixExpression) {
			return makeParenthesized(e);
		} else
			return e;
	}

	/**
	 * Gets the type of an array.
	 * @param array The array whose type we want to get.
	 * @return The type of the given array.
	 */
	public static IJavaType getArrayElementType(TypedExpression array) {
		try {
			return ((IJavaArrayType)array.getType()).getComponentType();
		} catch (DebugException e) {
			if (e.getCause() instanceof ClassNotLoadedException) {
				//System.err.println("I cannot get the class of the array " + array.getExpression());
				return null;
			} else
				throw new RuntimeException(e);
		}
	}

	/**
	 * Make a copy of the given node so that it uses
	 * the AST object of this class.  We need to do
	 * this because we cannot combine nodes that come
	 * from different AST objects.
	 * @param node The AST node to copy.
	 * @return A copy of the given AST node that is
	 * set to use the AST object of this class.
	 */
	public static ASTNode resetAST(ASTNode node) {
		return ASTNode.copySubtree(ast, node);
	}

}
