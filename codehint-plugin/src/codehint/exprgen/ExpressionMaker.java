package codehint.exprgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.debug.core.DebugException;

import codehint.ast.ASTNode;
import codehint.ast.ASTVisitor;
import codehint.ast.ArrayAccess;
import codehint.ast.ArrayType;
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
import codehint.ast.PostfixExpression;
import codehint.ast.PrefixExpression;
import codehint.ast.PrimitiveType;
import codehint.ast.QualifiedName;
import codehint.ast.SimpleName;
import codehint.ast.SimpleType;
import codehint.ast.StringLiteral;
import codehint.ast.SuperFieldAccess;
import codehint.ast.SuperMethodInvocation;
import codehint.ast.ThisExpression;
import codehint.ast.Type;
import codehint.ast.TypeLiteral;

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
import com.sun.jdi.Field;
import com.sun.jdi.Method;

public class ExpressionMaker {

	private final IJavaStackFrame stack;
	private final IJavaDebugTarget target;
	private int id;
	private final Map<Set<Effect>, Map<Integer, Result>> results;
	private final Map<Integer, String> resultStrings;
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
	
	public ExpressionMaker(IJavaStackFrame stack, ValueCache valueCache, TypeCache typeCache, TimeoutChecker timeoutChecker, NativeHandler nativeHandler, SideEffectHandler sideEffectHandler, Metadata metadata) {
		this(stack, valueCache, typeCache, timeoutChecker, nativeHandler, sideEffectHandler, metadata.subsetMethods, metadata.subsetFields, metadata.maxId);
	}
	
	private ExpressionMaker(IJavaStackFrame stack, ValueCache valueCache, TypeCache typeCache, TimeoutChecker timeoutChecker, NativeHandler nativeHandler, SideEffectHandler sideEffectHandler, Map<Integer, Method> methods, Map<Integer, Field> fields, int id) {
		this.stack = stack;
		this.target = (IJavaDebugTarget)stack.getDebugTarget();
		this.id = id + 1;
		results = new HashMap<Set<Effect>, Map<Integer, Result>>();
		this.resultStrings = new HashMap<Integer, String>();
		this.methods = new HashMap<Integer, Method>(methods);  // Make copies so that changes we make here don't affect the metadata's maps.
		this.fields = new HashMap<Integer, Field>(fields);
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
		else if (isLong(type))
			return computeLongInfixOp(left, op, right);
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
		if (op == InfixExpression.Operator.REMAINDER) {
			assert r != 0;
			return valueCache.getIntJavaValue(l % r);
		}
		if (op == InfixExpression.Operator.XOR)
			return valueCache.getIntJavaValue(l ^ r);
		if (op == InfixExpression.Operator.OR)
			return valueCache.getIntJavaValue(l | r);
		if (op == InfixExpression.Operator.AND)
			return valueCache.getIntJavaValue(l & r);
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

	private IJavaValue computeLongInfixOp(IJavaValue left, InfixExpression.Operator op, IJavaValue right) throws NumberFormatException, DebugException {
		if (left == null || right == null)
			return null;
		long l = Long.parseLong(left.getValueString());
		long r = Long.parseLong(right.getValueString());
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
		if (op == InfixExpression.Operator.REMAINDER) {
			assert r != 0;
			return target.newValue(l % r);
		}
		if (op == InfixExpression.Operator.XOR)
			return target.newValue(l ^ r);
		if (op == InfixExpression.Operator.OR)
			return target.newValue(l | r);
		if (op == InfixExpression.Operator.AND)
			return target.newValue(l & r);
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
		throw new RuntimeException("Unknown infix operation: " + op.toString() + " for " + left.toString() + " of type " + lType.toString() + " and " + right.toString() + " of type " + rType.toString());
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
		//long startTime = System.currentTimeMillis();
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
			//System.out.println("Calling " + (receiver.getValue() != null ? receiver.getExpression() : receiver.getType()).toString().replace("\n", "\\n") + "." + method.name() + " with args " + args.toString() + " got " + value + " with effects " + effects + " and took " + (System.currentTimeMillis() - startTime) + "ms.");
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
			Expression[] argExprs = null;
			Set<Effect> curArgEffects = null;
			if (e instanceof MethodInvocation) {
				MethodInvocation call = (MethodInvocation)e;
				Expression receiverExpr = call.getExpression() == null ? new ThisExpression(stack.getReferenceType()) : call.getExpression();
				Result receiverResult = call.getExpression() == null ? new Result(stack.getThis() == null ? stack.getReferenceType().getClassObject() : stack.getThis(), effects, valueCache, thread) : reEvaluateExpression(receiverExpr, effects, thread, target);
				IJavaType receiverType = call.getExpression() == null ? stack.getReferenceType() : receiverResult.getValue().getValue().getJavaType();
				receiverExpr.setStaticType(receiverType);
				setExpressionResult(receiverExpr, receiverResult, Collections.<Effect>emptySet());
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
			ArrayList<EvaluatedExpression> args = new ArrayList<EvaluatedExpression>(argExprs.length);
			boolean hasCrash = receiver.getValue() != null && "V".equals(receiver.getValue().getSignature());
			for (Expression arg: argExprs) {
				Result argResult = reEvaluateExpression(arg, curArgEffects, thread, target);
				setExpressionResult(arg, argResult, Collections.<Effect>emptySet());
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
			return EclipseUtils.javaStringOfValue(value, stack, true);
		Set<Effect> effects = expr.getResult() == null ? Collections.<Effect>emptySet() : expr.getResult().getEffects();
		try {
			//System.out.println("Getting toString of " + expr.getExpression() + " with effects " + effects);
			SideEffectHandler.redoEffects(effects);
			timeoutChecker.startEvaluating(null);
			return EclipseUtils.javaStringOfValue(value, stack, true);
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
		Result result = new Result(value, valueCache, thread);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return new EvaluatedExpression(e, type, result);
	}

	// Pass in cached int type for efficiency.
	public EvaluatedExpression makeNumber(String val, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		int lastChar = val.charAt(val.length() - 1);
		// Rules taken from: http://docs.oracle.com/javase/tutorial/java/nutsandbolts/datatypes.html.
		if (lastChar == 'l' || lastChar == 'L')
			return makeLong(Long.parseLong(val), value, type, valueCache, thread);
		else if (lastChar == 'f' || lastChar == 'F')
			return makeFloat(Float.parseFloat(val), value, type, valueCache, thread);
		else if (lastChar == 'd' || lastChar == 'D')
			return makeDouble(Double.parseDouble(val), value, type, valueCache, thread);
		else
			return makeInt(Integer.parseInt(val), value, type, valueCache, thread);
	}
	// Pass in cached int type for efficiency.
	public EvaluatedExpression makeInt(int num, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		Expression e = new IntLiteral(type, num);
		return initSimple(e, value, type, valueCache, thread);
	}
	// Pass in cached int type for efficiency.
	public EvaluatedExpression makeLong(long num, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		Expression e = new LongLiteral(type, num);
		return initSimple(e, value, type, valueCache, thread);
	}
	// Pass in cached int type for efficiency.
	public EvaluatedExpression makeFloat(float num, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		Expression e = new FloatLiteral(type, num);
		return initSimple(e, value, type, valueCache, thread);
	}
	// Pass in cached int type for efficiency.
	public EvaluatedExpression makeDouble(double num, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		Expression e = new DoubleLiteral(type, num);
		return initSimple(e, value, type, valueCache, thread);
	}
	// Pass in cached boolean type for efficiency.
	public EvaluatedExpression makeBoolean(boolean val, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		Expression e = new BooleanLiteral(type, val);
		return initSimple(e, value, type, valueCache, thread);
	}

	public EvaluatedExpression makeChar(char val, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		CharacterLiteral e = new CharacterLiteral(type, val);
		return initSimple(e, value, type, valueCache, thread);
	}

	public EvaluatedExpression makeString(String val, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		StringLiteral e = new StringLiteral(type, val);
		return initSimple(e, value, type, valueCache, thread);
	}

	public EvaluatedExpression makeNull(IJavaDebugTarget target, ValueCache valueCache, IJavaThread thread) {
		Expression e = NullLiteral.getNullLiteral();
		return initSimple(e, target.nullValue(), null, valueCache, thread);
	}

	public TypedExpression makeVar(String name, IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		Expression e = new SimpleName(type, name);
		return initSimple(e, value, type, valueCache, thread);
	}

	public TypedExpression makeFieldVar(String name, IJavaType type, TypedExpression thisExpr, Field field, ValueCache valueCache, IJavaThread thread) {
		try{
			TypedExpression e = makeVar(name, computeFieldAccess(thisExpr.getValue(), thisExpr.getType(), field, null), type, valueCache, thread);
			setDepth(e.getExpression(), 1);
			setField(e.getExpression(), field);
			return e;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	private Expression newStaticName(String name, IJavaValue value, IJavaReferenceType type, IJavaThread thread) {
		Expression e = makeName(type, name);
		setStatic(e, type);
		setExpressionValue(e, value, Collections.<Effect>emptySet(), valueCache, thread);
		return e;
	}

	public TypedExpression makeStaticName(String name, IJavaReferenceType type, ValueCache valueCache, IJavaThread thread) {
		try {
			IJavaValue value = type.getClassObject();
			Result result = new Result(value, valueCache, thread);
			Expression expr = newStaticName(name, value, type, thread);
			setExpressionResult(expr, result, Collections.<Effect>emptySet());
			return new EvaluatedExpression(expr, type, result);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	public TypedExpression makeThis(IJavaValue value, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		ThisExpression e = new ThisExpression(type);
		return initSimple(e, value, type, valueCache, thread);
	}

	public TypedExpression makeInfix(TypedExpression left, InfixExpression.Operator op, TypedExpression right, IJavaType type, ValueCache valueCache, IJavaThread thread, IJavaDebugTarget target) throws NumberFormatException, DebugException {
		InfixExpression e = makeInfix(left.getExpression(), op, right.getExpression(), type);
		Result operandResults = computeResultForBinaryOp(left, right, thread, target);
		if (operandResults == null)
			return null;
		IJavaValue value = computeInfixOp(left.getValue(), op, operandResults.getValue().getValue(), left.getType() != null ? left.getType() : right.getType());
		Result result = new Result(value, operandResults.getEffects(), valueCache, thread);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, result);
	}

	public InfixExpression makeInfix(Expression l, InfixExpression.Operator op, Expression r, IJavaType type) {
		InfixExpression e = new InfixExpression(type, parenIfNeeded(l), op, parenIfNeeded(r));
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
		IJavaType type = getArrayElementType(array);
		ArrayAccess e = makeArrayAccess(type, array.getExpression(), index.getExpression());
		Result result = new Result(value, operandResults.getEffects(), valueCache, thread);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, result);
	}

	public ArrayAccess makeArrayAccess(IJavaType type, Expression array, Expression index) {
		ArrayAccess e = new ArrayAccess(type, array, index);
		return e;
	}

	public TypedExpression makeFieldAccess(TypedExpression obj, String name, IJavaType fieldType, Field field, ValueCache valueCache, IJavaThread thread, IJavaDebugTarget target) {
		try {
			FieldAccess e = makeFieldAccess(obj.getExpression(), name, fieldType, field);
			IJavaValue value = computeFieldAccess(obj.getValue(), obj.getType(), field, target);
			Result result = new Result(value, obj.getResult().getEffects(), valueCache, thread);
			setExpressionResult(e, result, Collections.<Effect>emptySet());
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, fieldType, result);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public FieldAccess makeFieldAccess(Expression obj, String name, IJavaType type, Field field) {
		FieldAccess e = new FieldAccess(type, obj, makeSimpleName(null, name));
		setField(e, field);
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
		PrefixExpression e = new PrefixExpression(op, operand);
		return e;
	}

	public TypedExpression makePostfix(IJavaDebugTarget target, TypedExpression operand, PostfixExpression.Operator op, IJavaType type, ValueCache valueCache, IJavaThread thread) {
		PostfixExpression e = makePostfix(operand.getExpression(), op);
		IJavaValue value = computePostfixOp(target, operand.getValue(), op);
		Result result = new Result(value, operand.getResult().getEffects(), valueCache, thread);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, result);
	}

	private static PostfixExpression makePostfix(Expression operand, PostfixExpression.Operator op) {
		PostfixExpression e = new PostfixExpression(operand, op);
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
			e = makeClassInstanceCreation(receiver.getType(), name, args, result);
		} else {
			if (receiver.getExpression() instanceof ThisExpression || receiver.getType().equals(thisType))
				receiver = null;  // Don't use a receiver if it is null or the this type.
			e = makeCall(name, receiver == null ? null : receiver.getExpression(), args, returnType, result);
		}
		setMethod(e.getExpression(), method);
		return e;
	}
	/*private TypedExpression makeCall(String name, String classname, ArrayList<TypedExpression> args, IJavaType returnType) {
    	return makeCall(name, newStaticName(classname), args, returnType, null);
    }*/
	private TypedExpression makeCall(String name, Expression receiver, ArrayList<? extends TypedExpression> args, IJavaType returnType, Result result) {
		Expression[] newArgs = new Expression[args.size()];
		for (int i = 0; i < args.size(); i++)
			newArgs[i] = args.get(i).getExpression();
		MethodInvocation e = new MethodInvocation(returnType, receiver, makeSimpleName(null, name), newArgs);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, returnType, result);
	}
	public Expression makeCall(String name, Expression receiver, ArrayList<TypedExpression> args, Method method, IJavaType returnType, Set<Effect> effects, Result result) {
		Expression[] newArgs = new Expression[args.size()];
		for (int i = 0; i < args.size(); i++)
			newArgs[i] = args.get(i).getExpression();
		MethodInvocation e = new MethodInvocation(returnType, receiver, makeSimpleName(null, name), newArgs);
		setMethod(e, method);
		setExpressionResult(e, result, effects);
    	return e;
    }

	public TypedExpression makeCast(TypedExpression obj, IJavaType targetType, IJavaValue value, ValueCache valueCache, IJavaThread thread) {
		try {
			CastExpression e = makeCast(obj.getExpression(), targetType, targetType.getName());
			Result result = new Result(value, obj.getResult().getEffects(), valueCache, thread);
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, targetType, result);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	// I need to pass the target type name because it could be a shortened (not fully-qualified) version.
	public CastExpression makeCast(Expression obj, IJavaType targetType, String targetTypeName) throws DebugException {
		CastExpression e = new CastExpression(makeType(targetType, targetTypeName), obj);
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
		InstanceofExpression e = new InstanceofExpression(EclipseUtils.getFullyQualifiedType("boolean", stack, target, typeCache), expr, targetDomType);
		return e;
	}

	public TypedExpression makeConditional(TypedExpression cond, TypedExpression t, TypedExpression e, IJavaType type) {
		try {
			ConditionalExpression ex = makeConditional(type, cond.getExpression(), t.getExpression(), e.getExpression());
			Result result = computeConditionalOp(cond.getResult(), t.getResult(), e.getResult());
			setExpressionResult(ex, result, Collections.<Effect>emptySet());
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(ex, type, result);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	private static ConditionalExpression makeConditional(IJavaType type, Expression cond, Expression t, Expression e) {
		ConditionalExpression ex = new ConditionalExpression(type, cond, t, e);
		return ex;
	}

	private TypedExpression makeClassInstanceCreation(IJavaType type, String name, ArrayList<? extends TypedExpression> args, Result result) {
		Expression[] newArgs = new Expression[args.size()];
		for (int i = 0; i < args.size(); i++)
			newArgs[i] = args.get(i).getExpression();
		ClassInstanceCreation e = new ClassInstanceCreation(new SimpleType(type, makeName(type, EclipseUtils.sanitizeTypename(name))), newArgs);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, type, result);
	}

	public ClassInstanceCreation makeClassInstanceCreation(Type type, ArrayList<TypedExpression> args, Method method, Set<Effect> effects, Result result) {
		Expression[] newArgs = new Expression[args.size()];
		for (int i = 0; i < args.size(); i++)
			newArgs[i] = args.get(i).getExpression();
		ClassInstanceCreation e = new ClassInstanceCreation(type, newArgs);
		setMethod(e, method);
		setExpressionResult(e, result, effects);
    	return e;
	}

	public TypedExpression makeParenthesized(TypedExpression obj) {
		ParenthesizedExpression e = makeParenthesized(obj.getExpression());
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, obj.getType(), getExpressionResult(e, Collections.<Effect>emptySet()));
	}

	private ParenthesizedExpression makeParenthesized(Expression e) {
		ParenthesizedExpression p = new ParenthesizedExpression(e);
		copyExpressionResults(e, p);
		return p;
	}

	public TypedExpression makeSuperFieldAccess(Name qualifier, TypedExpression receiverExpr, String name, IJavaType fieldType, Field field, ValueCache valueCache, IJavaThread thread) {
		try {
			SuperFieldAccess e = makeSuperFieldAccess(qualifier, name, fieldType);
			IJavaValue value = computeFieldAccess(receiverExpr.getValue(), receiverExpr.getType(), field, null);
			Result result = new Result(value, valueCache, thread);
			setExpressionResult(e, result, Collections.<Effect>emptySet());
			setField(e, field);
			return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, fieldType, result);
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	private static SuperFieldAccess makeSuperFieldAccess(Name qualifier, String name, IJavaType type) {
		SuperFieldAccess e = new SuperFieldAccess(type, qualifier, makeSimpleName(null, name));
		return e;
	}

	public TypedExpression makeSuperCall(String name, Name qualifier, ArrayList<TypedExpression> args, IJavaType returnType, Result result, Method method) {
		Expression[] newArgs = new Expression[args.size()];
		for (int i = 0; i < args.size(); i++)
			newArgs[i] = args.get(i).getExpression();
		SuperMethodInvocation e = new SuperMethodInvocation(returnType, qualifier, makeSimpleName(null, name), newArgs);
		setExpressionResult(e, result, Collections.<Effect>emptySet());
		setMethod(e, method);
		return EvaluatedExpression.makeTypedOrEvaluatedExpression(e, returnType, result);
	}
	
	public TypeLiteral makeTypeLiteral(Type type) {
		TypeLiteral e = new TypeLiteral(type);
		return e;
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
		SimpleName e = new SimpleName(type, name);
		return e;
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

	public static int getID(Expression e) {
		return e.getID();
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

	public void copyExpressionResults(Expression oldExpr, Expression newExpr) {
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
	
	public void setResultString(Expression e, String resultString) {
		resultStrings.put(getID(e), resultString);
	}
	
	public String getResultString(Expression e) {
		return resultStrings.get(getID(e));
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

	public Field getField(int id) {
		return fields.get(id);
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
	
	public int getNumCrashes() {
		return numCrashes;
	}
	
	public static class Metadata {
		
		private final Map<Integer, Method> subsetMethods;
		private final Map<Integer, Field> subsetFields;
		private int maxId;
		
		public Metadata(Map<Integer, Method> subsetMethods, Map<Integer, Field> subsetFields) {
			this.subsetMethods = subsetMethods;
			this.subsetFields = subsetFields;
			this.maxId = -1;
		}
		
		public static Metadata emptyMetadata() {
			return new Metadata(new HashMap<Integer, Method>(), new HashMap<Integer, Field>());
		}
		
		public void addMetadataFor(List<? extends TypedExpression> exprs, final ExpressionMaker expressionMaker) {
			ASTVisitor visitor = new ASTVisitor() {
	    		@Override
	    		public void postVisit(ASTNode node) {
	    			if (node instanceof Expression) {
	    				int id = ExpressionMaker.getID((Expression)node);
    					Method method = expressionMaker.getMethod(id);
    					if (method != null)
    						subsetMethods.put(id, method);
    					Field field = expressionMaker.getField(id);
    					if (field != null)
    						subsetFields.put(id, field);
	    			}
	    		}
			};
			for (TypedExpression e: exprs)
				e.getExpression().accept(visitor);
			maxId = expressionMaker.id - 1;
		}
		
	}

}
