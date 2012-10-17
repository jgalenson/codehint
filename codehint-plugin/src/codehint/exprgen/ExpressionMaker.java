package codehint.exprgen;

import java.util.ArrayList;

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
import codehint.expreval.StringEvaluator;
import codehint.utils.EclipseUtils;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Method;

public class ExpressionMaker {

	private final static AST ast = AST.newAST(AST.JLS4);

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

	private static IJavaValue computeInfixOp(IJavaDebugTarget target, IJavaValue left, InfixExpression.Operator op, IJavaValue right, IJavaType type) throws NumberFormatException, DebugException {
		if (isInt(type))
			return computeIntInfixOp(target, left, op, right);
		else if (isBoolean(type))
			return computeBooleanInfixOp(target, left, op, right);
		else if (type instanceof IJavaReferenceType)
			return computeRefInfixOp(target, left, op, right);
		else
			throw new RuntimeException("Unexpected type: " + type);
	}

	private static IJavaValue computeIntInfixOp(IJavaDebugTarget target, IJavaValue left, InfixExpression.Operator op, IJavaValue right) throws NumberFormatException, DebugException {
		if (left == null || right == null)
			return null;
		int l = Integer.parseInt(left.getValueString());
		int r = Integer.parseInt(right.getValueString());
		if (op == InfixExpression.Operator.PLUS)
			return target.newValue(l + r);
		if (op == InfixExpression.Operator.MINUS)
			return target.newValue(l - r);
		if (op == InfixExpression.Operator.TIMES)
			return target.newValue(l * r);
		if (op == InfixExpression.Operator.DIVIDE) {
			assert r != 0;
			return target.newValue(l / r);
		}
		if (op == InfixExpression.Operator.EQUALS)
			return target.newValue(l == r);
		if (op == InfixExpression.Operator.NOT_EQUALS)
			return target.newValue(l != r);
		if (op == InfixExpression.Operator.LESS)
			return target.newValue(l < r);
		if (op == InfixExpression.Operator.LESS_EQUALS)
			return target.newValue(l <= r);
		if (op == InfixExpression.Operator.GREATER)
			return target.newValue(l > r);
		if (op == InfixExpression.Operator.GREATER_EQUALS)
			return target.newValue(l >= r);
		throw new RuntimeException("Unknown infix operation: " + op.toString());
	}

	private static IJavaValue computeBooleanInfixOp(IJavaDebugTarget target, IJavaValue left, InfixExpression.Operator op, IJavaValue right) throws DebugException {
		if (left == null || right == null)
			return null;
		boolean l = Boolean.parseBoolean(left.getValueString());
		boolean r = Boolean.parseBoolean(right.getValueString());
		if (op == InfixExpression.Operator.EQUALS)
			return target.newValue(l == r);
		if (op == InfixExpression.Operator.NOT_EQUALS)
			return target.newValue(l != r);
		if (op == InfixExpression.Operator.CONDITIONAL_AND)
			return target.newValue(l && r);
		if (op == InfixExpression.Operator.CONDITIONAL_OR)
			return target.newValue(l || r);
		throw new RuntimeException("Unknown infix operation: " + op.toString());
	}

	private static IJavaValue computeRefInfixOp(IJavaDebugTarget target, IJavaValue left, InfixExpression.Operator op, IJavaValue right) throws DebugException {
		if (left == null || right == null)
			return null;
		IJavaObject l = (IJavaObject)left;
		IJavaObject r = (IJavaObject)right;
		if (op == InfixExpression.Operator.EQUALS)
			return target.newValue(l.getUniqueId() == r.getUniqueId());
		if (op == InfixExpression.Operator.NOT_EQUALS)
			return target.newValue(l.getUniqueId() != r.getUniqueId());
		IJavaType lType = l.getJavaType();
		IJavaType rType = r.getJavaType();
		if (op == InfixExpression.Operator.PLUS && ((lType != null && "java.lang.String".equals(lType.getName())) || (rType != null && "java.lang.String".equals(rType.getName()))))
			return target.newValue(l.getValueString() + r.getValueString());
		throw new RuntimeException("Unknown infix operation: " + op.toString() + " for types " + lType.toString() + " and " + rType.toString());
	}

	private static IJavaValue computePrefixOp(IJavaDebugTarget target, IJavaValue e, PrefixExpression.Operator op) throws DebugException {
		if (e == null )
			return null;
		if (op == PrefixExpression.Operator.MINUS)
			return target.newValue(-Integer.parseInt(e.getValueString()));
		if (op == PrefixExpression.Operator.NOT)
			return target.newValue(!Boolean.parseBoolean(e.getValueString()));
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
	public static TypedExpression makeNumber(String val, IJavaValue value, IJavaType type, IJavaThread thread) {
		Expression e = ast.newNumberLiteral(val);
		e.setProperty("isConstant", true);
		setExpressionValue(e, value);
		return new EvaluatedExpression(e, type, new Value(value, thread));
	}

	// Pass in cached boolean type for efficiency.
	public static TypedExpression makeBoolean(boolean val, IJavaValue value, IJavaType type, IJavaThread thread) {
		Expression e = ast.newBooleanLiteral(val);
		e.setProperty("isConstant", true);
		setExpressionValue(e, value);
		return new EvaluatedExpression(e, type, new Value(value, thread));
	}

	public static TypedExpression makeNull(IJavaDebugTarget target, IJavaThread thread) {
		Expression e = ast.newNullLiteral();
		e.setProperty("isConstant", true);
		IJavaValue value = target.nullValue();
		setExpressionValue(e, value);
		return new EvaluatedExpression(e, null, new Value(value, thread));
	}

	public static TypedExpression makeVar(String name, IJavaValue value, IJavaType type, boolean isFieldAccess, IJavaThread thread) {
		Expression e = ast.newSimpleName(name);
		setExpressionValue(e, value);
		if (isFieldAccess)
			e.setProperty("depth", 1);
		return new EvaluatedExpression(e, type, new Value(value, thread));
	}

	private static Expression newStaticName(String name, IJavaValue value) {
		Expression e = ast.newName(name);
		setStatic(e);
		setExpressionValue(e, value);
		return e;
	}

	public static TypedExpression makeStaticName(String name, IJavaReferenceType type, IJavaThread thread) {
		try {
			IJavaValue value = type.getClassObject();
			return new EvaluatedExpression(newStaticName(name, value), type, new Value(value, thread));
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	public static TypedExpression makeThis(IJavaValue value, IJavaType type, IJavaThread thread) {
		ThisExpression e = ast.newThisExpression();
		setExpressionValue(e, value);
		return new EvaluatedExpression(e, type, new Value(value, thread));
	}

	public static TypedExpression makeInfix(IJavaDebugTarget target, TypedExpression left, InfixExpression.Operator op, TypedExpression right, IJavaType type, IJavaThread thread) throws NumberFormatException, DebugException {
		InfixExpression e = makeInfix(left.getExpression(), op, right.getExpression());
		IJavaValue value = computeInfixOp(target, left.getValue(), op, right.getValue(), left.getType() != null ? left.getType() : right.getType());
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, value, thread);
	}

	public static InfixExpression makeInfix(Expression l, InfixExpression.Operator op, Expression r) {
		InfixExpression e = ast.newInfixExpression();
		e.setLeftOperand(parenIfNeeded(ASTCopyer.copy(l)));
		e.setOperator(op);
		e.setRightOperand(parenIfNeeded(ASTCopyer.copy(r)));
		return e;
	}

	public static TypedExpression makeArrayAccess(TypedExpression array, TypedExpression index, IJavaValue value, IJavaThread thread) {
		ArrayAccess e = makeArrayAccess(array.getExpression(), index.getExpression());
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, getArrayElementType(array), value, thread);
	}

	public static ArrayAccess makeArrayAccess(Expression array, Expression index) {
		ArrayAccess e = ast.newArrayAccess();
		e.setArray(ASTCopyer.copy(array));
		e.setIndex(ASTCopyer.copy(index));
		return e;
	}

	public static TypedExpression makeFieldAccess(TypedExpression obj, String name, IJavaType fieldType, IJavaValue value, IJavaThread thread) {
		FieldAccess e = makeFieldAccess(obj.getExpression(), name);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, fieldType, value, thread);
	}

	public static FieldAccess makeFieldAccess(Expression obj, String name) {
		FieldAccess e = ast.newFieldAccess();
		e.setExpression(ASTCopyer.copy(obj));
		e.setName(ast.newSimpleName(name));
		return e;
	}

	public static TypedExpression makePrefix(IJavaDebugTarget target, TypedExpression operand, PrefixExpression.Operator op, IJavaType type, IJavaThread thread) {
		try {
			PrefixExpression e = makePrefix(operand.getExpression(), op);
			IJavaValue value = computePrefixOp(target, operand.getValue(), op);
			setExpressionValue(e, value);
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, value, thread);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public static PrefixExpression makePrefix(Expression operand, PrefixExpression.Operator op) {
		PrefixExpression e = ast.newPrefixExpression();
		e.setOperand(parenIfNeeded(ASTCopyer.copy(operand)));
		e.setOperator(op);
		return e;
	}

	public static TypedExpression makePostfix(IJavaDebugTarget target, TypedExpression operand, PostfixExpression.Operator op, IJavaType type, IJavaThread thread) {
		PostfixExpression e = makePostfix(operand.getExpression(), op);
		IJavaValue value = computePostfixOp(target, operand.getValue(), op);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, value, thread);
	}

	private static PostfixExpression makePostfix(Expression operand, PostfixExpression.Operator op) {
		PostfixExpression e = ast.newPostfixExpression();
		e.setOperand(parenIfNeeded(ASTCopyer.copy(operand)));
		e.setOperator(op);
		return e;
	}

	@SuppressWarnings("unused")
	public static TypedExpression makeCall(String name, TypedExpression receiver, ArrayList<? extends TypedExpression> args, IJavaType returnType, IJavaType thisType, Method method, IJavaDebugTarget target, IJavaThread thread, StringEvaluator stringEvaluator) {
		//IJavaValue value = computeCall(method, receiver.getValue(), args, thread, target, ((JDIType)receiver.getType()));
		IJavaValue value = null;
		if ("java.lang.String".equals(method.declaringType().name()))
			value = stringEvaluator.evaluateCall(receiver, args, method, target);
		TypedExpression result = null;
		if (receiver.getExpression() == null) {
			assert "<init>".equals(name);
			try {
				result = makeClassInstanceCreation(receiver.getType(), args, value, thread);
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
		} else {
			if (receiver.getExpression() instanceof ThisExpression || receiver.getType().equals(thisType))
				receiver = null;  // Don't use a receiver if it is null or the this type.
			result = makeCall(name, receiver == null ? null : ASTCopyer.copy(receiver.getExpression()), args, returnType, value, thread);
		}
		setMethod(result.getExpression(), method);
		return result;
	}
	/*private static TypedExpression makeCall(String name, String classname, ArrayList<TypedExpression> args, IJavaType returnType) {
    	return makeCall(name, newStaticName(classname), args, returnType, null);
    }*/
	@SuppressWarnings("unchecked")
	private static TypedExpression makeCall(String name, Expression receiver, ArrayList<? extends TypedExpression> args, IJavaType returnType, IJavaValue value, IJavaThread thread) {
		MethodInvocation e = ast.newMethodInvocation();
		e.setName(ast.newSimpleName(name));
		e.setExpression(receiver);
		for (TypedExpression ex: args)
			e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, returnType, value, thread);
	}
	@SuppressWarnings("unchecked")
	public static Expression makeCall(String name, Expression receiver, ArrayList<Expression> args, Method method) {
    	MethodInvocation e = ast.newMethodInvocation();
    	e.setName(ast.newSimpleName(name));
    	e.setExpression(ASTCopyer.copy(receiver));
    	for (Expression ex: args)
    		e.arguments().add(ASTCopyer.copy(ex));
		setMethod(e, method);
    	return e;
    }

	public static TypedExpression makeCast(TypedExpression obj, IJavaType targetType, IJavaValue value, IJavaThread thread) {
		CastExpression e = makeCast(obj.getExpression(), targetType);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, targetType, value, thread);
	}

	public static CastExpression makeCast(Expression obj, IJavaType targetType) {
		CastExpression e = ast.newCastExpression();
		e.setExpression(ASTCopyer.copy(obj));
		e.setType(makeType(targetType));
		return e;
	}

	public static TypedExpression makeInstanceOf(TypedExpression obj, Type targetDomType, IJavaType targetType, IJavaValue value, IJavaThread thread) {
		InstanceofExpression e = makeInstanceOf(obj.getExpression(), targetDomType);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, targetType, value, thread);
	}

	private static InstanceofExpression makeInstanceOf(Expression expr, Type targetDomType) {
		InstanceofExpression e = ast.newInstanceofExpression();
		e.setLeftOperand(ASTCopyer.copy(expr));
		e.setRightOperand(targetDomType);
		return e;
	}

	public static TypedExpression makeConditional(TypedExpression cond, TypedExpression t, TypedExpression e, IJavaType type, IJavaThread thread) {
		try {
			ConditionalExpression ex = makeConditional(cond.getExpression(), t.getExpression(), e.getExpression());
			IJavaValue value = computeConditionalOp(cond.getValue(), t.getValue(), e.getValue());
			setExpressionValue(ex, value);
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(ex, type, value, thread);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	private static ConditionalExpression makeConditional(Expression cond, Expression t, Expression e) {
		ConditionalExpression ex = ast.newConditionalExpression();
		ex.setExpression(ASTCopyer.copy(cond));
		ex.setThenExpression(ASTCopyer.copy(t));
		ex.setElseExpression(ASTCopyer.copy(e));
		return ex;
	}

	@SuppressWarnings("unchecked")
	private static TypedExpression makeClassInstanceCreation(IJavaType type, ArrayList<? extends TypedExpression> args, IJavaValue value, IJavaThread thread) throws DebugException {
		ClassInstanceCreation e = ast.newClassInstanceCreation();
		e.setType(ast.newSimpleType(ast.newName(EclipseUtils.sanitizeTypename(type.getName()))));
		for (TypedExpression ex: args)
			e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, value, thread);
	}

	@SuppressWarnings("unchecked")
	public static ClassInstanceCreation makeClassInstanceCreation(Type type,ArrayList<Expression> args, Method method) {
    	ClassInstanceCreation e = ast.newClassInstanceCreation();
    	e.setType(ASTCopyer.copy(type));
    	for (Expression ex: args)
    		e.arguments().add(ASTCopyer.copy(ex));
		setMethod(e, method);
    	return e;
	}

	public static TypedExpression makeParenthesized(TypedExpression obj, IJavaThread thread) {
		ParenthesizedExpression e = makeParenthesized(obj.getExpression());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, obj.getType(), getExpressionValue(e), thread);
	}

	private static ParenthesizedExpression makeParenthesized(Expression e) {
		ParenthesizedExpression p = ast.newParenthesizedExpression();
		p.setExpression(e);
		setExpressionValue(p, getExpressionValue(e));
		return p;
	}

	public static TypedExpression makeSuperFieldAccess(Name qualifier, String name, IJavaType fieldType, IJavaValue value, IJavaThread thread) {
		SuperFieldAccess e = makeSuperFieldAccess(qualifier, name);
		setExpressionValue(e, value);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, fieldType, value, thread);
	}

	private static SuperFieldAccess makeSuperFieldAccess(Name qualifier, String name) {
		SuperFieldAccess e = ast.newSuperFieldAccess();
		e.setQualifier((Name)ASTCopyer.copy(qualifier));
		e.setName(ast.newSimpleName(name));
		return e;
	}

	@SuppressWarnings("unchecked")
	public static TypedExpression makeSuperCall(String name, Name qualifier, ArrayList<TypedExpression> args, IJavaType returnType, IJavaValue value, Method method, IJavaThread thread) {
		SuperMethodInvocation e = ast.newSuperMethodInvocation();
		e.setName(ast.newSimpleName(name));
		e.setQualifier(qualifier);
		for (TypedExpression ex: args)
			e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setExpressionValue(e, value);
		setMethod(e, method);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, returnType, value, thread);
	}
	
	public static TypeLiteral makeTypeLiteral(Type type) {
		TypeLiteral e = ast.newTypeLiteral();
		e.setType(ASTCopyer.copy(type));
		return e;
	}

	private static Type makeType(IJavaType type) {
		try {
			if (type instanceof IJavaArrayType)
				return ast.newArrayType(makeType(((IJavaArrayType)type).getComponentType()));
			else if (type instanceof IJavaReferenceType)
				return ast.newSimpleType(ast.newName(type.getName()));
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

	public static void setExpressionValue(Expression e, IJavaValue v) {
		e.setProperty("value", v);
	}

	public static IJavaValue getExpressionValue(Expression e) {
		return (IJavaValue)e.getProperty("value");
	}

	private static void setStatic(Expression e) {
		e.setProperty("isStatic", true);
	}

	public static boolean isStatic(Expression e) {
		return e.getProperty("isStatic") != null;
	}

	private static void setMethod(Expression e, Method method) {
		e.setProperty("method", method);
	}

	public static Method getMethod(Expression e) {
		return (Method)e.getProperty("method");
	}

	/**
	 * Parenthesize an expression if it is an infix or prefix expression.
	 * @param e The expression.
	 * @return The given expression parenthesized if it is infix
	 * or prefix, and the expression itself otherwise.
	 */
	private static Expression parenIfNeeded(Expression e) {
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
