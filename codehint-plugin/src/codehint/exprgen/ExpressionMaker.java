package codehint.exprgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassObject;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaInterfaceType;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.effects.Effect;
import codehint.effects.SideEffectHandler;
import codehint.expreval.EvaluatedExpression;
import codehint.expreval.NativeHandler;
import codehint.expreval.StaticEvaluator;
import codehint.expreval.TimeoutChecker;
import codehint.utils.EclipseUtils;
import codehint.utils.Utils;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.Method;

public class ExpressionMaker {

	private final static AST ast = AST.newAST(AST.JLS4);
	
	private final IJavaStackFrame stack;
	private int id;
	private final Map<Set<Effect>, Map<Integer, Result>> results;
	private final Map<Integer, Method> methods;
	private final Map<Integer, Field> fields;
	private final Map<Integer, IJavaReferenceType> statics;
	private final Map<Integer, Integer> depths;
	private final ValueCache valueCache;
	private final TypeCache typeCache;
	private int numCrashes;
	private final TimeoutChecker timeoutChecker;
	private final NativeHandler nativeHandler;
	private final SideEffectHandler sideEffectHandler;
	
	public ExpressionMaker(IJavaStackFrame stack, ValueCache valueCache, TypeCache typeCache, TimeoutChecker timeoutChecker, NativeHandler nativeHandler, SideEffectHandler sideEffectHandler) {
		this.stack = stack;
		id = 0;
		results = new HashMap<Set<Effect>, Map<Integer, Result>>();
		methods = new HashMap<Integer, Method>();
		fields = new HashMap<Integer, Field>();
		statics = new HashMap<Integer, IJavaReferenceType>();
		depths = new HashMap<Integer, Integer>();
		this.valueCache = valueCache;
		this.typeCache = typeCache;
		numCrashes = 0;
		this.timeoutChecker = timeoutChecker;
		this.nativeHandler = nativeHandler;
		this.sideEffectHandler = sideEffectHandler;
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
	
	private static Result computeConditionalOp(Result c, Result t, Result e) throws DebugException {
		if (c == null || t == null || e == null)
			return null;
		boolean cond = Boolean.parseBoolean(c.getValue().getValue().getValueString());
		if (cond)
			return t;
		else
			return e;
	}
	
	private static IJavaValue computeArrayAccess(IJavaValue l, IJavaValue r) throws NumberFormatException, DebugException {
		if (l.isNull())
			return null;
		int index = Integer.parseInt(r.getValueString());
		if (index < 0)
			return null;
		IJavaArray array = (IJavaArray)l;
		if (index >= array.getLength())
			return null;
		return array.getValue(index);
	}
	
	private static IJavaValue computeFieldAccess(IJavaValue receiverValue, IJavaType receiverType, Field field, IJavaDebugTarget target) throws DebugException {
		if (field == null)
			return target.newValue(((IJavaArray)receiverValue).getLength());
		else if (field.isStatic())
			return (IJavaValue)((IJavaReferenceType)receiverType).getField(field.name()).getValue();
		else if (receiverValue != null)
			return (IJavaValue)((IJavaObject)receiverValue).getField(field.name(), !field.declaringType().name().equals(receiverType.getName())).getValue();
		else
			return null;
	}

	// I don't need to call getExpressionResult on receiver and args (in getArgValues) because reEvaluate expression creates them with the correct results.
	private Result computeCall(Method method, TypedExpression receiver, ArrayList<? extends TypedExpression> args, IJavaThread thread, IJavaDebugTarget target, boolean isOutermost) {
		for (TypedExpression arg: args)
			if (arg.getValue() == null)
				return null;
		IJavaValue receiverValue = receiver.getValue();
		IJavaValue value = null;
		Set<Effect> effects = null;
		try {
			//System.out.println("Calling " + (receiver.getValue() != null ? receiver.getExpression() : receiver.getType()).toString().replace("\n", "\\n") + "." + method.name() + " with args " + args.toString());
			timeoutChecker.startEvaluating(null);
			nativeHandler.blockNativeCalls();
			sideEffectHandler.startHandlingSideEffects();
			IJavaValue[] argValues = getArgValues(method, receiver, args, thread, target);
			sideEffectHandler.checkArguments(argValues);
			if (receiverValue == null && "<init>".equals(method.name()))
				value = ((IJavaClassType)receiver.getType()).newInstance(method.signature(), argValues, thread);
			else if (receiverValue instanceof IJavaClassObject && method.isStatic())
				value = ((IJavaClassType)((IJavaClassObject)receiverValue).getInstanceType()).sendMessage(method.name(), method.signature(), argValues, thread);
			else
				value = ((IJavaObject)receiverValue).sendMessage(method.name(), method.signature(), argValues, thread, null);
			//System.out.println("Got " + value);
		} catch (DebugException e) {
			//System.out.println("Crashed on " + receiver.toString().replace("\n", "\\n") + "." + method.name() + " with args " + args.toString().replace("\n", "\\n") + " got " + EclipseUtils.getExceptionMessage(e));
			numCrashes++;
			value = target.voidValue();
		} finally {
			effects = isOutermost ? sideEffectHandler.stopHandlingSideEffects() : sideEffectHandler.getSideEffects();
			nativeHandler.allowNativeCalls();
			timeoutChecker.stopEvaluating();
		}
		return new Result(value, effects, valueCache, thread);
	}
	
	private IJavaValue[] getArgValues(Method method, TypedExpression receiver, ArrayList<? extends TypedExpression> args, IJavaThread thread, IJavaDebugTarget target) throws DebugException {
		IJavaValue[] argValues = new IJavaValue[args.size()];
		Set<Effect> effects = method.isConstructor() ? Collections.<Effect>emptySet() : receiver.getResult().getEffects();
		boolean seenEffects = !effects.isEmpty();
		if (seenEffects) {
			//System.out.println("Replaying/re-evaluating starting at receiver: " + receiver + ", " + args.toString());
			sideEffectHandler.redoAndRecordEffects(effects);
		}
		for (int i = 0; i < argValues.length; i++) {
			TypedExpression arg = args.get(i);
			if (!seenEffects) {
				argValues[i] = arg.getValue();
				effects = arg.getResult().getEffects();
				if (!effects.isEmpty()) {
					seenEffects = true;
					//System.out.println("Replaying/re-evaluating starting at index " + i + ": " + receiver + ", " + args.toString());
					sideEffectHandler.redoAndRecordEffects(effects);
				}
			} else {
				Result argResult = reEvaluateExpression(arg.getExpression(), effects, thread, target);
				argValues[i] = argResult.getValue().getValue();
				effects = argResult.getEffects();
				sideEffectHandler.redoAndRecordEffects(effects);
			}
		}
		return argValues;
	}
	
	// For efficiency, special-case the no-effects common case.
	private Result computeResultForBinaryOp(TypedExpression left, TypedExpression right, IJavaThread thread, IJavaDebugTarget target) {
		if (left.getResult() == null || left.getResult().getEffects().isEmpty() || right.getResult() == null)
			return right.getResult();
		return evaluateExpressionWithEffects(right, left.getResult().getEffects(), thread, target);
	}
	
	public Result evaluateExpressionWithEffects(TypedExpression expr, Set<Effect> effects, IJavaThread thread, IJavaDebugTarget target) {
		Result result = getExpressionResult(expr.getExpression(), effects);
		if (result != null)
			return result;
		try {
			//System.out.println("Re-evaluating " + expr.getExpression() + " with effects " + effects);
			sideEffectHandler.startHandlingSideEffects();
			sideEffectHandler.redoAndRecordEffects(effects);
			result = reEvaluateExpression(expr.getExpression(), effects, thread, target);
		} catch (DebugException e) {
			//System.out.println("Crashed on " + expr.getExpression() + " got " + EclipseUtils.getExceptionMessage(e));
			numCrashes++;
		} finally {
			sideEffectHandler.stopHandlingSideEffects();
			//System.out.println("Re-evaluated " + expr.getExpression().toString() + " with effects " + effects);
		}
		return result;
	}
	
	private Result reEvaluateExpression(Expression e, Set<Effect> effects, IJavaThread thread, IJavaDebugTarget target) throws DebugException {
		if (e == null)
			return null;
		Result result = getExpressionResult(e, effects);
		if (result != null)
			return result;
		if (e instanceof NumberLiteral || e instanceof BooleanLiteral || e instanceof NullLiteral || e instanceof ThisExpression || e instanceof QualifiedName)
			result = new Result(getExpressionValue(e, Collections.<Effect>emptySet()), effects, valueCache, thread);
		else if (e instanceof SimpleName) {
			IJavaReferenceType staticType = getStaticType(e);
			if (staticType == null)
				result = new Result((IJavaValue)stack.findVariable(((SimpleName)e).getIdentifier()).getValue(), effects, valueCache, thread);
			else
				result = new Result(staticType.getClassObject(), effects, valueCache, thread);
		} else if (e instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)e;
			Result leftResult = reEvaluateExpression(infix.getLeftOperand(), effects, thread, target);
			IJavaValue leftValue = leftResult.getValue().getValue();
			Result rightResult = reEvaluateExpression(infix.getRightOperand(), effects, thread, target);
			IJavaValue rightValue = rightResult.getValue().getValue();
			if (!"V".equals(leftValue.getSignature()) && !"V".equals(rightValue.getSignature())) {
				IJavaValue value = computeInfixOp(leftValue, infix.getOperator(), rightValue, leftValue.isNull() ? rightValue.getJavaType() : leftValue.getJavaType());
				result = new Result(value, rightResult.getEffects(), valueCache, thread);
			}
		} else if (e instanceof ArrayAccess) {
			ArrayAccess access = (ArrayAccess)e;
			Result arrayResult = reEvaluateExpression(access.getArray(), effects, thread, target);
			Result indexResult = reEvaluateExpression(access.getIndex(), effects, thread, target);
			if (!"V".equals(arrayResult.getValue().getValue().getSignature()) && !"V".equals(indexResult.getValue().getValue().getSignature())) {
				IJavaValue value = computeArrayAccess(arrayResult.getValue().getValue(), indexResult.getValue().getValue());
				result = new Result(value, indexResult.getEffects(), valueCache, thread);
			}
		} else if (e instanceof FieldAccess) {
			FieldAccess access = (FieldAccess)e;
			Result receiverResult = reEvaluateExpression(access.getExpression(), effects, thread, target);
			if (receiverResult != null && receiverResult.getValue().getValue() instanceof IJavaObject) {
				IJavaType receiverType = isStatic(access.getExpression()) ? getStaticType(access.getExpression()) : receiverResult.getValue().getValue().getJavaType();
				Field field = getField(e);
				IJavaValue value = field != null && field.isFinal() ? getExpressionValue(e, Collections.<Effect>emptySet()) : computeFieldAccess(receiverResult.getValue().getValue(), receiverType, field, target);
				result = new Result(value, receiverResult.getEffects(), valueCache, thread);
			}
		} else if (e instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression)e;
			Result operandResult = reEvaluateExpression(prefix.getOperand(), effects, thread, target);
			if (!"V".equals(operandResult.getValue().getValue().getSignature()))
				result = new Result(computePrefixOp(operandResult.getValue().getValue(), prefix.getOperator()), operandResult.getEffects(), valueCache, thread);
		} else if (e instanceof MethodInvocation || e instanceof ClassInstanceCreation) {
			Method method = getMethod(e);
			TypedExpression receiver = null;
			List<?> argExprs = null;
			Set<Effect> curArgEffects = null;
			if (e instanceof MethodInvocation) {
				MethodInvocation call = (MethodInvocation)e;
				Expression receiverExpr = call.getExpression() == null ? ast.newThisExpression() : call.getExpression();
				Result receiverResult = call.getExpression() == null ? new Result(stack.getThis() == null ? stack.getReferenceType().getClassObject() : stack.getThis(), effects, valueCache, thread) : reEvaluateExpression(receiverExpr, effects, thread, target);
				IJavaType receiverType = call.getExpression() == null ? stack.getReferenceType() : receiverResult.getValue().getValue().getJavaType();
				receiver = new EvaluatedExpression(receiverExpr, receiverType, receiverResult);
				argExprs = call.arguments();
				curArgEffects = receiverResult.getEffects();
			} else {
				ClassInstanceCreation call = (ClassInstanceCreation)e;
				receiver = new TypedExpression(null, EclipseUtils.getType(call.getType().toString(), stack, target, typeCache));
				argExprs = call.arguments();
				curArgEffects = Collections.<Effect>emptySet();
			}
			// TODO: I think the work below (and some of the receiver stuff above) duplicates getArgValues (e.g., re-computing results).
			ArrayList<EvaluatedExpression> args = new ArrayList<EvaluatedExpression>(argExprs.size());
			boolean hasCrash = receiver.getValue() != null && "V".equals(receiver.getValue().getSignature());
			for (int i = 0; i < argExprs.size(); i++) {
				Expression arg = (Expression)argExprs.get(i);
				Result argResult = reEvaluateExpression(arg, curArgEffects, thread, target);
				args.add(new EvaluatedExpression(arg, null, argResult));
				hasCrash = hasCrash || "V".equals(argResult.getValue().getValue().getSignature());
				curArgEffects = argResult.getEffects();
			}
			if (!hasCrash)
				result = computeCall(method, receiver, args, thread, target, false);
		} else if (e instanceof CastExpression) {
			result = reEvaluateExpression(((CastExpression)e).getExpression(), effects, thread, target);
		} else
			throw new RuntimeException("Unexpected expression type:" + e.getClass().getName());
		setExpressionResult(e, result, effects);
		return result;
	}
	
	public String getToStringWithEffects(TypedExpression expr, IJavaValue value) throws DebugException {
		if (sideEffectHandler == null || !sideEffectHandler.isEnabled())  // This should only be null during refinement, in which case we just get the toString without worrying about side effects, as we do when we're not handling side effects.
			return EclipseUtils.javaStringOfValue(value, stack);
		Set<Effect> effects = expr.getResult().getEffects();
		try {
			//System.out.println("Getting toString of " + expr.getExpression() + " with effects " + effects);
			SideEffectHandler.redoEffects(effects);
			timeoutChecker.startEvaluating(null);
			return EclipseUtils.javaStringOfValue(value, stack);
		} catch (DebugException e) {
			//System.out.println("Crashed on getting toString of " + expr.getExpression() + " got " + EclipseUtils.getExceptionMessage(e));
			return value.toString();
		} finally {
			timeoutChecker.stopEvaluating();
			SideEffectHandler.undoEffects(effects);
			//System.out.println("Got toString of " + expr.getExpression().toString());
		}
	}

	// Helper methods to create AST nodes, as they don't seem to have useful constructors.

	/*
	 * My nodes can have the following properties:
	 * isStatic: Exists (and is true) iff the expression is a static name.
	 * value: The value of the expression, or null if it is unknown..  All nodes should have this.
	 * isConstant: Exists (and is true) iff the expression is a constant (e.g., 0, true, null).
	 */

	private EvaluatedExpression initSimple(Expression e, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		//e.setProperty("isConstant", true);
		setID(e);
		setExpressionValue(e, value, Collections.<Effect>emptySet(), valueCache, thread);
		return new EvaluatedExpression(e, type, new Result(value, valueCache, thread));
	}

	// Pass in cached int type for efficiency.
	public EvaluatedExpression makeNumber(String val, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		Expression e = ast.newNumberLiteral(val);
		return initSimple(e, value, type, valueCache, thread);
	}
	// Pass in cached boolean type for efficiency.
	public EvaluatedExpression makeBoolean(boolean val, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		Expression e = ast.newBooleanLiteral(val);
		return initSimple(e, value, type, valueCache, thread);
	}

	public EvaluatedExpression makeChar(char val, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		CharacterLiteral e = ast.newCharacterLiteral();
		e.setCharValue(val);
		return initSimple(e, value, type, valueCache, thread);
	}

	public EvaluatedExpression makeString(String val, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		StringLiteral e = ast.newStringLiteral();
		e.setLiteralValue(val);
		return initSimple(e, value, type, valueCache, thread);
	}

	public EvaluatedExpression makeNull(IJavaDebugTarget target, ValueCache valueCache, IJavaThread thread) {
		Expression e = ast.newNullLiteral();
		return initSimple(e, target.nullValue(), null, valueCache, thread);
	}

	public TypedExpression makeVar(String name, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		Expression e = ast.newSimpleName(name);
		return initSimple(e, value, type, valueCache, thread);
	}

	public TypedExpression makeFieldVar(String name, IJavaType type, TypedExpression thisExpr, Field field, ValueCache valueCache, IJavaThread thread) {
		try{
			TypedExpression e = makeVar(name, computeFieldAccess(thisExpr.getValue(), thisExpr.getType(), field, null), type, valueCache, thread);
			setDepth(e.getExpression(), 1);
			return e;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	private Expression newStaticName(String name, IJavaValue value, IJavaReferenceType type, IJavaThread thread) {
		Expression e = makeName(name);
		setStatic(e, type);
		setExpressionValue(e, value, Collections.<Effect>emptySet(), valueCache, thread);
		return e;
	}

	public TypedExpression makeStaticName(String name, IJavaReferenceType type, ValueCache valueCache, IJavaThread thread) {
		try {
			IJavaValue value = type.getClassObject();
			return new EvaluatedExpression(newStaticName(name, value, type, thread), type, new Result(value, valueCache, thread));
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	public TypedExpression makeThis(IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		ThisExpression e = ast.newThisExpression();
		return initSimple(e, value, type, valueCache, thread);
	}

	public TypedExpression makeInfix(TypedExpression left, InfixExpression.Operator op, TypedExpression right, IJavaType type, ValueCache valueCache, IJavaThread thread, IJavaDebugTarget target) throws NumberFormatException, DebugException {
		InfixExpression e = makeInfix(left.getExpression(), op, right.getExpression());
		Result operandResults = computeResultForBinaryOp(left, right, thread, target);
		if (operandResults == null)
			return null;
		IJavaValue value = computeInfixOp(left.getValue(), op, operandResults.getValue().getValue(), left.getType() != null ? left.getType() : right.getType());
		Result result = new Result(value, operandResults.getEffects(), valueCache, thread);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, result);
	}

	public InfixExpression makeInfix(Expression l, InfixExpression.Operator op, Expression r) {
		InfixExpression e = ast.newInfixExpression();
		e.setLeftOperand(parenIfNeeded(ASTCopyer.copy(l)));
		e.setOperator(op);
		e.setRightOperand(parenIfNeeded(ASTCopyer.copy(r)));
		setID(e);
		return e;
	}

	public TypedExpression makeArrayAccess(TypedExpression array, TypedExpression index, ValueCache valueCache, IJavaThread thread, IJavaDebugTarget target) throws NumberFormatException, DebugException {
		Result operandResults = computeResultForBinaryOp(array, index, thread, target);
		if (operandResults == null)
			return null;
		IJavaValue value = null;
		if (array.getValue() != null && operandResults.getValue().getValue() != null) {
			value = computeArrayAccess(array.getValue(), operandResults.getValue().getValue());
			if (value == null)
				return null;
		}
		ArrayAccess e = makeArrayAccess(array.getExpression(), index.getExpression());
		Result result = new Result(value, operandResults.getEffects(), valueCache, thread);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, getArrayElementType(array), result);
	}

	public ArrayAccess makeArrayAccess(Expression array, Expression index) {
		ArrayAccess e = ast.newArrayAccess();
		e.setArray(ASTCopyer.copy(array));
		e.setIndex(ASTCopyer.copy(index));
		setID(e);
		return e;
	}

	public TypedExpression makeFieldAccess(TypedExpression obj, String name, IJavaType fieldType, Field field, ValueCache valueCache, IJavaThread thread, IJavaDebugTarget target) {
		try {
			FieldAccess e = makeFieldAccess(obj.getExpression(), name);
			IJavaValue value = computeFieldAccess(obj.getValue(), obj.getType(), field, target);
			Result result = new Result(value, obj.getResult().getEffects(), valueCache, thread);
			setExpressionResult(e, result, Collections.<Effect>emptySet());
			setField(e, field);
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, fieldType, result);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
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
			Result result = new Result(value, operand.getResult().getEffects(), valueCache, thread);
			setExpressionResult(e, result, Collections.<Effect>emptySet());
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, result);
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
		Result result = new Result(value, operand.getResult().getEffects(), valueCache, thread);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, result);
	}

	private PostfixExpression makePostfix(Expression operand, PostfixExpression.Operator op) {
		PostfixExpression e = ast.newPostfixExpression();
		e.setOperand(parenIfNeeded(ASTCopyer.copy(operand)));
		e.setOperator(op);
		setID(e);
		return e;
	}

	public TypedExpression makeCall(String name, TypedExpression receiver, ArrayList<? extends TypedExpression> args, IJavaType returnType, IJavaType thisType, Method method, IJavaDebugTarget target, ValueCache valueCache, IJavaThread thread, StaticEvaluator staticEvaluator) {
		IJavaValue value = staticEvaluator.evaluateCall(receiver, args, method, target);
		Result result = null;
		if (value == null)
			result = computeCall(method, receiver, args, thread, target, true);
		else
			result = new Result(value, valueCache, thread);
		TypedExpression e = null;
		if (receiver.getExpression() == null) {
			assert "<init>".equals(name);
			try {
				e = makeClassInstanceCreation(receiver.getType(), args, result);
			} catch (DebugException ex) {
				throw new RuntimeException(ex);
			}
		} else {
			if (receiver.getExpression() instanceof ThisExpression || receiver.getType().equals(thisType))
				receiver = null;  // Don't use a receiver if it is null or the this type.
			e = makeCall(name, receiver == null ? null : ASTCopyer.copy(receiver.getExpression()), args, returnType, result);
		}
		setMethod(e.getExpression(), method);
		return e;
	}
	/*private TypedExpression makeCall(String name, String classname, ArrayList<TypedExpression> args, IJavaType returnType) {
    	return makeCall(name, newStaticName(classname), args, returnType, null);
    }*/
	@SuppressWarnings("unchecked")
	private TypedExpression makeCall(String name, Expression receiver, ArrayList<? extends TypedExpression> args, IJavaType returnType, Result result) {
		MethodInvocation e = ast.newMethodInvocation();
		e.setName(makeSimpleName(name));
		e.setExpression(receiver);
		for (TypedExpression ex: args)
			e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setID(e);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, returnType, result);
	}
	@SuppressWarnings("unchecked")
	public Expression makeCall(String name, Expression receiver, ArrayList<TypedExpression> args, Method method, Set<Effect> effects, Result result) {
    	MethodInvocation e = ast.newMethodInvocation();
    	e.setName(makeSimpleName(name));
    	e.setExpression(ASTCopyer.copy(receiver));
    	for (TypedExpression ex: args)
    		e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setID(e);
		setMethod(e, method);
		setExpressionResult(e, result, effects);
    	return e;
    }

	public TypedExpression makeCast(TypedExpression obj, IJavaType targetType, IJavaValue value, ValueCache valueCache, IJavaThread thread) {
		try {
			CastExpression e = makeCast(obj.getExpression(), targetType.getName());
			Result result = new Result(value, obj.getResult().getEffects(), valueCache, thread);
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, targetType, result);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public CastExpression makeCast(Expression obj, String targetTypeName) {
		CastExpression e = ast.newCastExpression();
		e.setExpression(ASTCopyer.copy(obj));
		e.setType(makeType(EclipseUtils.sanitizeTypename(targetTypeName)));
		setID(e);
		copyExpressionResults(obj, e);
		return e;
	}

	public TypedExpression makeInstanceOf(TypedExpression obj, Type targetDomType, IJavaType targetType, IJavaValue value, ValueCache valueCache, IJavaThread thread) {
		InstanceofExpression e = makeInstanceOf(obj.getExpression(), targetDomType);
		Result result = new Result(value, obj.getResult().getEffects(), valueCache, thread);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, targetType, result);
	}

	private InstanceofExpression makeInstanceOf(Expression expr, Type targetDomType) {
		InstanceofExpression e = ast.newInstanceofExpression();
		e.setLeftOperand(ASTCopyer.copy(expr));
		e.setRightOperand(targetDomType);
		setID(e);
		return e;
	}

	public TypedExpression makeConditional(TypedExpression cond, TypedExpression t, TypedExpression e, IJavaType type) {
		try {
			ConditionalExpression ex = makeConditional(cond.getExpression(), t.getExpression(), e.getExpression());
			Result result = computeConditionalOp(cond.getResult(), t.getResult(), e.getResult());
			setExpressionResult(ex, result, Collections.<Effect>emptySet());
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(ex, type, result);
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
	private TypedExpression makeClassInstanceCreation(IJavaType type, ArrayList<? extends TypedExpression> args, Result result) throws DebugException {
		ClassInstanceCreation e = ast.newClassInstanceCreation();
		e.setType(ast.newSimpleType(makeName(EclipseUtils.sanitizeTypename(type.getName()))));
		for (TypedExpression ex: args)
			e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setID(e);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, result);
	}

	@SuppressWarnings("unchecked")
	public ClassInstanceCreation makeClassInstanceCreation(Type type, ArrayList<TypedExpression> args, Method method, Set<Effect> effects, Result result) {
    	ClassInstanceCreation e = ast.newClassInstanceCreation();
    	e.setType(ASTCopyer.copy(type));
    	for (TypedExpression ex: args)
    		e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setID(e);
		setMethod(e, method);
		setExpressionResult(e, result, effects);
    	return e;
	}

	public TypedExpression makeParenthesized(TypedExpression obj) {
		ParenthesizedExpression e = makeParenthesized(obj.getExpression());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, obj.getType(), getExpressionResult(e, Collections.<Effect>emptySet()));
	}

	private ParenthesizedExpression makeParenthesized(Expression e) {
		ParenthesizedExpression p = ast.newParenthesizedExpression();
		p.setExpression(e);
		setID(p);
		copyExpressionResults(e, p);
		return p;
	}

	public TypedExpression makeSuperFieldAccess(Name qualifier, TypedExpression receiverExpr, String name, IJavaType fieldType, Field field, ValueCache valueCache, IJavaThread thread) {
		try {
			SuperFieldAccess e = makeSuperFieldAccess(qualifier, name);
			IJavaValue value = computeFieldAccess(receiverExpr.getValue(), receiverExpr.getType(), field, null);
			Result result = new Result(value, valueCache, thread);
			setExpressionResult(e, result, Collections.<Effect>emptySet());
			setField(e, field);
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, fieldType, result);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	private SuperFieldAccess makeSuperFieldAccess(Name qualifier, String name) {
		SuperFieldAccess e = ast.newSuperFieldAccess();
		e.setQualifier((Name)ASTCopyer.copy(qualifier));
		e.setName(makeSimpleName(name));
		setID(e);
		return e;
	}

	@SuppressWarnings("unchecked")
	public TypedExpression makeSuperCall(String name, Name qualifier, ArrayList<TypedExpression> args, IJavaType returnType, Result result, Method method) {
		SuperMethodInvocation e = ast.newSuperMethodInvocation();
		e.setName(makeSimpleName(name));
		e.setQualifier(qualifier);
		for (TypedExpression ex: args)
			e.arguments().add(ASTCopyer.copy(ex.getExpression()));
		setID(e);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		setMethod(e, method);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, returnType, result);
	}
	
	public TypeLiteral makeTypeLiteral(Type type) {
		TypeLiteral e = ast.newTypeLiteral();
		e.setType(ASTCopyer.copy(type));
		setID(e);
		return e;
	}

	private Type makeType(String typeName) {
		if (typeName.endsWith("[]"))
			return ast.newArrayType(makeType(typeName.substring(0, typeName.length() - 2)));
		else if (typeName.equals("int"))
			return ast.newPrimitiveType(PrimitiveType.INT);
		else if (typeName.equals("boolean"))
			return ast.newPrimitiveType(PrimitiveType.BOOLEAN);
		else if (typeName.equals("long"))
			return ast.newPrimitiveType(PrimitiveType.LONG);
		else if (typeName.equals("byte"))
			return ast.newPrimitiveType(PrimitiveType.BYTE);
		else if (typeName.equals("char"))
			return ast.newPrimitiveType(PrimitiveType.CHAR);
		else if (typeName.equals("short"))
			return ast.newPrimitiveType(PrimitiveType.SHORT);
		else if (typeName.equals("float"))
			return ast.newPrimitiveType(PrimitiveType.FLOAT);
		else if (typeName.equals("double"))
			return ast.newPrimitiveType(PrimitiveType.DOUBLE);
		else
			return ast.newSimpleType(makeName(EclipseUtils.sanitizeTypename(typeName)));
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

	public void setID(Expression e) {
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

	private void setExpressionValue(Expression e, IJavaValue v, Set<Effect> effects, ValueCache valueCache, IJavaThread thread) {
		Utils.addToMapMap(results, effects, getID(e), new Result(v, valueCache, thread));
	}

	public IJavaValue getExpressionValue(Expression e, Set<Effect> effects) {
		return getExpressionResult(e, effects).getValue().getValue();
	}

	public void setExpressionResult(Expression e, Result r, Set<Effect> effects) {
		Utils.addToMapMap(results, effects, getID(e), r);
	}

	private void copyExpressionResults(Expression oldExpr, Expression newExpr) {
		int oldID = getID(oldExpr);
		int newID = getID(newExpr);
		for (Map<Integer, Result> effectResults: results.values()) {
			Result result = effectResults.get(oldID);
			if (result != null)
				effectResults.put(newID, result);
		}
	}

	public Result getExpressionResult(Expression e, Set<Effect> effects) {
		return getExpressionResult(getID(e), effects);
	}

	public Result getExpressionResult(int id, Set<Effect> effects) {
		Map<Integer, Result> effectResults = results.get(effects);
		if (effectResults == null)
			return null;
		return effectResults.get(id);
	}

	public void setDepth(Expression e, int d) {
		depths.put(getID(e), d);
	}

	public Object getDepthOpt(Expression e) {
		return depths.get(getID(e));
	}

	private void setStatic(Expression e, IJavaReferenceType type) {
		statics.put(getID(e), type);
	}

	public IJavaReferenceType getStaticType(Expression e) {
		return statics.get(getID(e));
	}

	public boolean isStatic(Expression e) {
		return getStaticType(e) != null;
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

	private void setField(Expression e, Field field) {
		fields.put(getID(e), field);
	}

	public Field getField(Expression e) {
		return fields.get(getID(e));
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
	
	public int getNumCrashes() {
		return numCrashes;
	}

}
