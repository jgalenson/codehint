package codehint.exprgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.ast.ArrayAccess;
import codehint.ast.ArrayType;
import codehint.ast.Assignment;
import codehint.ast.Block;
import codehint.ast.BooleanLiteral;
import codehint.ast.CastExpression;
import codehint.ast.CharacterLiteral;
import codehint.ast.ClassInstanceCreation;
import codehint.ast.ConditionalExpression;
import codehint.ast.DoubleLiteral;
import codehint.ast.Expression;
import codehint.ast.FieldAccess;
import codehint.ast.FloatLiteral;
import codehint.ast.InfixExpression;
import codehint.ast.InstanceofExpression;
import codehint.ast.IntLiteral;
import codehint.ast.LongLiteral;
import codehint.ast.MethodInvocation;
import codehint.ast.Name;
import codehint.ast.NullLiteral;
import codehint.ast.NumberLiteral;
import codehint.ast.ParenthesizedExpression;
import codehint.ast.PlaceholderExpression;
import codehint.ast.PostfixExpression;
import codehint.ast.PrefixExpression;
import codehint.ast.PrimitiveType;
import codehint.ast.QualifiedName;
import codehint.ast.SimpleName;
import codehint.ast.SimpleType;
import codehint.ast.Statement;
import codehint.ast.StringLiteral;
import codehint.ast.SuperFieldAccess;
import codehint.ast.SuperMethodInvocation;
import codehint.ast.ThisExpression;
import codehint.ast.Type;
import codehint.ast.TypeLiteral;
import codehint.effects.Effect;
import codehint.expreval.StaticEvaluator;
import codehint.utils.EclipseUtils;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Field;
import com.sun.jdi.Method;

public class ExpressionMaker {

	private final IJavaDebugTarget target;
	private final ExpressionEvaluator expressionEvaluator;
	private final ValueCache valueCache;
	
	public ExpressionMaker(IJavaStackFrame stack, ExpressionEvaluator expressionEvaluator, ValueCache valueCache) {
		this.target = (IJavaDebugTarget)stack.getDebugTarget();
		this.expressionEvaluator = expressionEvaluator;
		this.valueCache = valueCache;
	}
	
	/*
	 * My nodes can have the following properties:
	 * isStatic: Exists (and is true) iff the expression is a static name.
	 * value: The value of the expression, or null if it is unknown..  All nodes should have this.
	 * isConstant: Exists (and is true) iff the expression is a constant (e.g., 0, true, null).
	 */

	private <T extends Expression> T initSimple(T e, IJavaValue value, IJavaThread thread) {
		//e.setProperty("isConstant", true);
		Result result = new Result(value, valueCache, thread);
		expressionEvaluator.setResult(e, result, Collections.<Effect>emptySet());
		return e;
	}

	// Pass in cached int type for efficiency.
	public NumberLiteral makeNumber(String val, IJavaValue value, IJavaType type, IJavaThread thread) {
		int lastChar = val.charAt(val.length() - 1);
		// Rules taken from: http://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html.
		if (lastChar == 'l' || lastChar == 'L')
			return makeLong(Long.parseLong(val), value, type, thread);
		else if (lastChar == 'f' || lastChar == 'F')
			return makeFloat(Float.parseFloat(val), value, type, thread);
		else if (lastChar == 'd' || lastChar == 'D')
			return makeDouble(Double.parseDouble(val), value, type, thread);
		else
			return makeInt(Integer.parseInt(val), value, type, thread);
	}
	// Pass in cached int type for efficiency.
	public IntLiteral makeInt(int num, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new IntLiteral(type, num), value, thread);
	}
	// Pass in cached int type for efficiency.
	public LongLiteral makeLong(long num, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new LongLiteral(type, num), value, thread);
	}
	// Pass in cached int type for efficiency.
	public FloatLiteral makeFloat(float num, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new FloatLiteral(type, num), value, thread);
	}
	// Pass in cached int type for efficiency.
	public DoubleLiteral makeDouble(double num, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new DoubleLiteral(type, num), value, thread);
	}
	// Pass in cached boolean type for efficiency.
	public BooleanLiteral makeBoolean(boolean val, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new BooleanLiteral(type, val), value, thread);
	}

	public CharacterLiteral makeChar(char val, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new CharacterLiteral(type, val), value, thread);
	}

	public StringLiteral makeString(String val, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new StringLiteral(type, val), value, thread);
	}

	public NullLiteral makeNull(IJavaThread thread) {
		return initSimple(NullLiteral.getNullLiteral(), target.nullValue(), thread);
	}

	public SimpleName makeVar(String name, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new SimpleName(type, name), value, thread);
	}

	public SimpleName makeFieldVar(String name, IJavaType type, Expression thisExpr, Field field, IJavaThread thread) {
		try{
			SimpleName e = makeVar(name, expressionEvaluator.computeFieldAccess(expressionEvaluator.getValue(thisExpr, Collections.<Effect>emptySet()), thisExpr.getStaticType(), field), type, thread);
			expressionEvaluator.setDepth(e, 1);
			expressionEvaluator.setField(e, field);
			return e;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	private Name newStaticName(String name, IJavaValue value, IJavaReferenceType type, IJavaThread thread) {
		Name e = makeName(type, name);
		expressionEvaluator.setStatic(e, type);
		expressionEvaluator.setValue(e, value, Collections.<Effect>emptySet(), thread);
		return e;
	}

	public Name makeStaticName(String name, IJavaReferenceType type, IJavaThread thread) {
		try {
			IJavaValue value = type.getClassObject();
			Result result = new Result(value, valueCache, thread);
			Name expr = newStaticName(name, value, type, thread);
			expressionEvaluator.setResult(expr, result, Collections.<Effect>emptySet());
			return expr;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	public ThisExpression makeThis(IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new ThisExpression(type), value, thread);
	}

	public InfixExpression makeInfix(Expression left, InfixExpression.Operator op, Expression right, IJavaType type, IJavaThread thread) throws NumberFormatException, DebugException {
		InfixExpression e = makeInfix(left, op, right, type);
		Result operandResults = expressionEvaluator.computeResultForBinaryOp(left, right);
		if (operandResults == null)
			return null;
		Value value = expressionEvaluator.computeInfixOp(expressionEvaluator.getValue(left, Collections.<Effect>emptySet()), op, operandResults.getValue().getValue(), left.getStaticType() != null ? left.getStaticType() : right.getStaticType());
		if (value == null)
			return null;
		Result result = new Result(value, operandResults.getEffects());
		expressionEvaluator.setResult(e, result, Collections.<Effect>emptySet());
		return e;
	}

	public InfixExpression makeInfix(Expression l, InfixExpression.Operator op, Expression r, IJavaType type) {
		return new InfixExpression(type, parenIfNeeded(l), op, parenIfNeeded(r));
	}

	public ArrayAccess makeArrayAccess(Expression array, Expression index, IJavaThread thread) throws NumberFormatException, DebugException {
		Result operandResults = expressionEvaluator.computeResultForBinaryOp(array, index);
		if (operandResults == null)
			return null;
		IJavaValue value = null;
		IJavaValue arrayValue = expressionEvaluator.getValue(array, Collections.<Effect>emptySet());
		if (arrayValue != null && operandResults.getValue().getValue() != null) {
			value = ExpressionEvaluator.computeArrayAccess(arrayValue, operandResults.getValue().getValue());
			if (value == null)
				return null;
		}
		IJavaType type = getArrayElementType(array);
		ArrayAccess e = makeArrayAccess(type, array, index);
		Result result = new Result(value, operandResults.getEffects(), valueCache, thread);
		expressionEvaluator.setResult(e, result, Collections.<Effect>emptySet());
		return e;
	}

	public ArrayAccess makeArrayAccess(IJavaType type, Expression array, Expression index) {
		return new ArrayAccess(type, array, index);
	}

	public FieldAccess makeFieldAccess(Expression obj, String name, IJavaType fieldType, Field field, IJavaThread thread) {
		try {
			FieldAccess e = makeFieldAccess(obj, name, fieldType, field);
			Result objResult = expressionEvaluator.getResult(obj, Collections.<Effect>emptySet());
			IJavaValue value = expressionEvaluator.computeFieldAccess(objResult.getValue().getValue(), obj.getStaticType(), field);
			Result result = new Result(value, objResult.getEffects(), valueCache, thread);
			expressionEvaluator.setResult(e, result, Collections.<Effect>emptySet());
			return e;
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public FieldAccess makeFieldAccess(Expression obj, String name, IJavaType type, Field field) {
		FieldAccess e = new FieldAccess(type, obj, makeSimpleName(null, name));
		expressionEvaluator.setField(e, field);
		return e;
	}

	public PrefixExpression makePrefix(Expression operand, PrefixExpression.Operator op, IJavaThread thread) {
		try {
			PrefixExpression e = makePrefix(operand, op);
			Result operandResult = expressionEvaluator.getResult(operand, Collections.<Effect>emptySet());
			Value value = expressionEvaluator.computePrefixOp(operandResult.getValue().getValue(), op);
			Result result = new Result(value, operandResult.getEffects());
			expressionEvaluator.setResult(e, result, Collections.<Effect>emptySet());
			return e;
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public PrefixExpression makePrefix(Expression operand, PrefixExpression.Operator op) {
		return new PrefixExpression(op, operand);
	}

	public PostfixExpression makePostfix(Expression operand, PostfixExpression.Operator op, IJavaThread thread) {
		PostfixExpression e = new PostfixExpression(operand, op);
		Result operandResult = expressionEvaluator.getResult(operand, Collections.<Effect>emptySet());
		IJavaValue value = ExpressionEvaluator.computePostfixOp(operandResult.getValue().getValue(), op);
		Result result = new Result(value, operandResult.getEffects(), valueCache, thread);
		expressionEvaluator.setResult(e, result, Collections.<Effect>emptySet());
		return e;
	}

	public Expression makeCall(String name, Expression receiver, ArrayList<Expression> args, IJavaType returnType, IJavaType thisType, Method method, IJavaThread thread, StaticEvaluator staticEvaluator) {
		Result result = expressionEvaluator.computeCall(method, receiver, args, staticEvaluator);
		return makeCall(name, receiver, args, returnType, thisType, method, result);
	}
	
	public Expression makeCall(String name, Expression receiver, ArrayList<Expression> args, IJavaType returnType, IJavaType thisType, Method method, Result result) {
		if (receiver instanceof PlaceholderExpression) {
			return makeClassInstanceCreation(new SimpleType(receiver.getStaticType(), makeName(receiver.getStaticType(), EclipseUtils.sanitizeTypename(name))), args, method, Collections.<Effect>emptySet(), result);
		} else {
			if (receiver instanceof ThisExpression || receiver.getStaticType().equals(thisType))
				receiver = null;  // Don't use a receiver if it is null or the this type.
			return makeCall(name, receiver == null ? null : receiver, args, method, returnType, Collections.<Effect>emptySet(), result);
		}
	}
	/*private Expression makeCall(String name, String classname, ArrayList<Expression> args, IJavaType returnType) {
    	return makeCall(name, newStaticName(classname), args, returnType, null);
    }*/
	public MethodInvocation makeCall(String name, Expression receiver, ArrayList<Expression> args, Method method, IJavaType returnType, Set<Effect> effects, Result result) {
		Expression[] newArgs = new Expression[args.size()];
		args.toArray(newArgs);
		MethodInvocation e = new MethodInvocation(returnType, receiver, makeSimpleName(null, name), newArgs);
		expressionEvaluator.setMethod(e, method);
		expressionEvaluator.setResult(e, result, effects);
    	return e;
    }

	public CastExpression makeCast(Expression obj, IJavaType targetType) {
		try {
			return makeCast(obj, targetType, targetType.getName());
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	// I need to pass the target type name because it could be a shortened (not fully-qualified) version.
	public CastExpression makeCast(Expression obj, IJavaType targetType, String targetTypeName) throws DebugException {
		CastExpression e = new CastExpression(makeType(targetType, targetTypeName), obj);
		expressionEvaluator.copyResults(obj, e);
		return e;
	}

	public InstanceofExpression makeInstanceOf(Expression obj, Type targetDomType, IJavaType booleanType, Value value) throws DebugException {
		assert booleanType.getName().equals("boolean");
		InstanceofExpression e = new InstanceofExpression(booleanType, obj, targetDomType);
		Result result = new Result(value, expressionEvaluator.getEffects(obj, Collections.<Effect>emptySet()));
		expressionEvaluator.setResult(e, result, Collections.<Effect>emptySet());
		return e;
	}

	public ConditionalExpression makeConditional(Expression cond, Expression t, Expression e, IJavaType type) {
		try {
			ConditionalExpression ex = new ConditionalExpression(type, cond, t, e);
			Result result = ExpressionEvaluator.computeConditionalOp(expressionEvaluator.getResult(cond, Collections.<Effect>emptySet()), expressionEvaluator.getResult(t, Collections.<Effect>emptySet()), expressionEvaluator.getResult(e, Collections.<Effect>emptySet()));
			expressionEvaluator.setResult(ex, result, Collections.<Effect>emptySet());
			return ex;
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public ClassInstanceCreation makeClassInstanceCreation(Type type, ArrayList<Expression> args, Method method, Set<Effect> effects, Result result) {
		Expression[] newArgs = new Expression[args.size()];
		args.toArray(newArgs);
		ClassInstanceCreation e = new ClassInstanceCreation(type, newArgs);
		expressionEvaluator.setMethod(e, method);
		expressionEvaluator.setResult(e, result, effects);
    	return e;
	}

	public ParenthesizedExpression makeParenthesized(Expression e) {
		ParenthesizedExpression p = new ParenthesizedExpression(e);
		expressionEvaluator.copyResults(e, p);
		return p;
	}

	public SuperFieldAccess makeSuperFieldAccess(Name qualifier, Expression receiverExpr, String name, IJavaType fieldType, Field field, IJavaThread thread) {
		try {
			SuperFieldAccess e = new SuperFieldAccess(fieldType, qualifier, makeSimpleName(null, name));
			IJavaValue value = expressionEvaluator.computeFieldAccess(expressionEvaluator.getValue(receiverExpr, Collections.<Effect>emptySet()), receiverExpr.getStaticType(), field);
			if (value != null)
				expressionEvaluator.setResult(e, new Result(value, valueCache, thread), Collections.<Effect>emptySet());
			expressionEvaluator.setField(e, field);
			return e;
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public SuperMethodInvocation makeSuperCall(String name, Name qualifier, ArrayList<Expression> args, IJavaType returnType, Result result, Method method) {
		Expression[] newArgs = new Expression[args.size()];
		for (int i = 0; i < args.size(); i++)
			newArgs[i] = args.get(i);
		SuperMethodInvocation e = new SuperMethodInvocation(returnType, qualifier, makeSimpleName(null, name), newArgs);
		expressionEvaluator.setResult(e, result, Collections.<Effect>emptySet());
		expressionEvaluator.setMethod(e, method);
		return e;
	}
	
	public TypeLiteral makeTypeLiteral(Type type) {
		return new TypeLiteral(type);
	}

	private Type makeType(IJavaType type, String typeName) throws DebugException {
		if (type instanceof IJavaArrayType)
			return new ArrayType(type, makeType(((IJavaArrayType)type).getComponentType(), typeName.substring(0, typeName.length() - 2)));
		else if (typeName.equals("int"))
			return new PrimitiveType(type, PrimitiveType.INT);
		else if (typeName.equals("boolean"))
			return new PrimitiveType(type, PrimitiveType.BOOLEAN);
		else if (typeName.equals("long"))
			return new PrimitiveType(type, PrimitiveType.LONG);
		else if (typeName.equals("byte"))
			return new PrimitiveType(type, PrimitiveType.BYTE);
		else if (typeName.equals("char"))
			return new PrimitiveType(type, PrimitiveType.CHAR);
		else if (typeName.equals("short"))
			return new PrimitiveType(type, PrimitiveType.SHORT);
		else if (typeName.equals("float"))
			return new PrimitiveType(type, PrimitiveType.FLOAT);
		else if (typeName.equals("double"))
			return new PrimitiveType(type, PrimitiveType.DOUBLE);
		else
			return new SimpleType(type, makeName(type, EclipseUtils.sanitizeTypename(typeName)));
	}
	
	private static SimpleName makeSimpleName(IJavaType type, String name) {
		return new SimpleName(type, name);
	}
	
	private static Name makeName(IJavaType type, String name) {
		int index = 0;
		Name result = null;
		while (index < name.length()) {
			IJavaType curType = null;
			int nextIndex = name.indexOf('.', index);
			if (nextIndex == -1) {
				nextIndex = name.length();
				curType = type;
			}
			SimpleName simpleName = makeSimpleName(curType, name.substring(index, nextIndex));
			if (result == null)
				result = simpleName;
			else
				result = new QualifiedName(curType, result, simpleName);
			index = nextIndex + 1;
		}
		return result;
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
	public static IJavaType getArrayElementType(Expression array) {
		try {
			return ((IJavaArrayType)array.getStaticType()).getComponentType();
		} catch (DebugException e) {
			if (e.getCause() instanceof ClassNotLoadedException) {
				//System.err.println("I cannot get the class of the array " + array);
				return null;
			} else
				throw new RuntimeException(e);
		}
	}
	
	public Assignment makeAssignment(Expression lhs, Expression rhs) {
		try {
			Assignment assign = new Assignment(lhs, Assignment.Operator.ASSIGN, rhs);
			Result lhsResult = expressionEvaluator.evaluateExpressionWithEffects(lhs, Collections.<Effect>emptySet(), null);
			Result rhsResult = expressionEvaluator.evaluateExpressionWithEffects(rhs, lhsResult.getEffects(), null);
			Result result = expressionEvaluator.computeAssignment(lhs, rhs, Collections.<Effect>emptySet(), lhsResult, rhsResult);
			assert result != null;
			expressionEvaluator.setResult(assign, result, Collections.<Effect>emptySet());
			return assign;
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	public Block makeBlock(ArrayList<Expression> body) {
		Block block = new Block(body.toArray(new Statement[body.size()]));
		Result result = expressionEvaluator.computeBlock(body, Collections.<Effect>emptySet());
		if (result == null)  // A block could unexpectedly crash due to intermediate effects.
			return null;
		expressionEvaluator.setResult(block, result, Collections.<Effect>emptySet());
		return block;
	}

}
