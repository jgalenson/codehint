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
import codehint.ast.PlaceholderExpression;
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
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;

import codehint.effects.Effect;
import codehint.effects.SideEffectHandler;
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
		this(stack, valueCache, typeCache, timeoutChecker, nativeHandler, sideEffectHandler, metadata.subsetMethods, metadata.subsetFields);
	}
	
	private ExpressionMaker(IJavaStackFrame stack, ValueCache valueCache, TypeCache typeCache, TimeoutChecker timeoutChecker, NativeHandler nativeHandler, SideEffectHandler sideEffectHandler, Map<Integer, Method> methods, Map<Integer, Field> fields) {
		this.stack = stack;
		this.target = (IJavaDebugTarget)stack.getDebugTarget();
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

	// Evaluation helpers that compute IJavaValues and IJavaTypes.

	private IJavaValue computeInfixOp(IJavaValue left, InfixExpression.Operator op, IJavaValue right, IJavaType type) throws NumberFormatException, DebugException {
		if (EclipseUtils.isInt(type))
			return computeIntInfixOp(left, op, right);
		else if (EclipseUtils.isBoolean(type))
			return computeBooleanInfixOp(left, op, right);
		else if (EclipseUtils.isLong(type))
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
			if (r == 0)
				return null;
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

	private static IJavaValue computePostfixOp(IJavaValue e, PostfixExpression.Operator op) {
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
	
	private IJavaValue computeFieldAccess(IJavaValue receiverValue, IJavaType receiverType, Field field) throws DebugException {
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
	private Result computeCall(Method method, Expression receiver, ArrayList<Expression> args, IJavaThread thread, boolean isOutermost, ExpressionGenerator expressionGenerator) {
		IJavaValue receiverValue = getValue(receiver, Collections.<Effect>emptySet());
		IJavaValue value = null;
		Set<Effect> effects = null;
		//long startTime = System.currentTimeMillis();
		try {
			//System.out.println("Calling " + (receiverValue != null ? receiver : receiver.getStaticType()).toString().replace("\n", "\\n") + "." + method.name() + " with args " + args.toString());
			timeoutChecker.startEvaluating(null);
			nativeHandler.blockNativeCalls();
			sideEffectHandler.startHandlingSideEffects();
			IJavaValue[] argValues = getArgValues(method, receiver, args, thread, expressionGenerator);
			sideEffectHandler.checkArguments(argValues);
			if (receiverValue == null && "<init>".equals(method.name()))
				value = ((IJavaClassType)receiver.getStaticType()).newInstance(method.signature(), argValues, thread);
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
			//System.out.println("Calling " + (receiverValue != null ? receiver : receiver.getStaticType()).toString().replace("\n", "\\n") + "." + method.name() + " with args " + args.toString() + " got " + value + " with effects " + effects + " and took " + (System.currentTimeMillis() - startTime) + "ms.");
		}
		return new Result(value, effects, valueCache, thread);
	}
	
	private IJavaValue[] getArgValues(Method method, Expression receiver, ArrayList<Expression> args, IJavaThread thread, ExpressionGenerator expressionGenerator) throws DebugException {
		IJavaValue[] argValues = new IJavaValue[args.size()];
		Set<Effect> effects = method.isConstructor() ? Collections.<Effect>emptySet() : getResult(receiver, Collections.<Effect>emptySet()).getEffects();
		boolean seenEffects = !effects.isEmpty();
		if (seenEffects) {
			//System.out.println("Replaying/re-evaluating starting at receiver: " + receiver + ", " + args.toString());
			sideEffectHandler.redoAndRecordEffects(effects);
		}
		for (int i = 0; i < argValues.length; i++) {
			Expression arg = args.get(i);
			if (!seenEffects) {
				Result argResult = getResult(arg, Collections.<Effect>emptySet());
				argValues[i] = argResult.getValue().getValue();
				effects = argResult.getEffects();
				if (!effects.isEmpty()) {
					seenEffects = true;
					//System.out.println("Replaying/re-evaluating starting at index " + i + ": " + receiver + ", " + args.toString());
					sideEffectHandler.redoAndRecordEffects(effects);
				}
			} else {
				Result argResult = reEvaluateExpression(arg, effects, thread, expressionGenerator);
				argValues[i] = argResult.getValue().getValue();
				effects = argResult.getEffects();
				sideEffectHandler.redoAndRecordEffects(effects);
			}
		}
		return argValues;
	}
	
	// For efficiency, special-case the no-effects common case.
	private Result computeResultForBinaryOp(Expression left, Expression right, IJavaThread thread) {
		Result leftResult = getResult(left, Collections.<Effect>emptySet());
		Result rightResult = getResult(right, Collections.<Effect>emptySet());
		if (leftResult == null || leftResult.getEffects().isEmpty() || rightResult == null)
			return rightResult;
		return evaluateExpressionWithEffects(right, leftResult.getEffects(), thread, null);
	}
	
	public Result evaluateExpressionWithEffects(Expression expr, Set<Effect> effects, IJavaThread thread, ExpressionGenerator expressionGenerator) {
		Result result = getResult(expr, effects);
		if (result != null)
			return result;
		try {
			//System.out.println("Re-evaluating " + expr + " with effects " + effects);
			sideEffectHandler.startHandlingSideEffects();
			sideEffectHandler.redoAndRecordEffects(effects);
			result = reEvaluateExpression(expr, effects, thread, expressionGenerator);
		} catch (DebugException e) {
			//System.out.println("Crashed on " + expr + " got " + EclipseUtils.getExceptionMessage(e));
			numCrashes++;
		} finally {
			sideEffectHandler.stopHandlingSideEffects();
			//System.out.println("Re-evaluated " + expr.toString() + " with effects " + effects);
		}
		return result;
	}
	
	// If expressionGenerator is not null, we register the newly-computed result with its equivalence classes.
	private Result reEvaluateExpression(Expression e, Set<Effect> effects, IJavaThread thread, ExpressionGenerator expressionGenerator) throws DebugException {
		if (e == null)
			return null;
		Result result = getResult(e, effects);
		if (result != null) {
			//System.out.println("Expression " + e.toString() + " with effects " + effects.toString() + " is " + result);
			return result;
		}
		//System.out.println("Reevaluating " + e.toString() + " with effects " + effects.toString());
		if (e instanceof NumberLiteral || e instanceof BooleanLiteral || e instanceof NullLiteral || e instanceof ThisExpression || e instanceof QualifiedName)
			result = new Result(getValue(e, Collections.<Effect>emptySet()), effects, valueCache, thread);
		else if (e instanceof SimpleName) {
			IJavaReferenceType staticType = getStaticType(e);
			if (staticType == null) {
				try {
					SideEffectHandler.redoEffects(effects);
					result = new Result((IJavaValue)stack.findVariable(((SimpleName)e).getIdentifier()).getValue(), effects, valueCache, thread);
				} finally {
					SideEffectHandler.undoEffects(effects);
				}
			} else
				result = new Result(staticType.getClassObject(), effects, valueCache, thread);
		} else if (e instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)e;
			Result leftResult = reEvaluateExpression(infix.getLeftOperand(), effects, thread, expressionGenerator);
			IJavaValue leftValue = leftResult.getValue().getValue();
			Result rightResult = reEvaluateExpression(infix.getRightOperand(), leftResult.getEffects(), thread, expressionGenerator);
			IJavaValue rightValue = rightResult.getValue().getValue();
			if (!"V".equals(leftValue.getSignature()) && !"V".equals(rightValue.getSignature())) {
				IJavaValue value = computeInfixOp(leftValue, infix.getOperator(), rightValue, leftValue.isNull() ? rightValue.getJavaType() : leftValue.getJavaType());
				result = new Result(value, rightResult.getEffects(), valueCache, thread);
			}
		} else if (e instanceof ArrayAccess) {
			ArrayAccess access = (ArrayAccess)e;
			Result arrayResult = reEvaluateExpression(access.getArray(), effects, thread, expressionGenerator);
			Result indexResult = reEvaluateExpression(access.getIndex(), arrayResult.getEffects(), thread, expressionGenerator);
			if (!"V".equals(arrayResult.getValue().getValue().getSignature()) && !"V".equals(indexResult.getValue().getValue().getSignature())) {
				try {
					SideEffectHandler.redoEffects(arrayResult.getEffects());
					IJavaValue value = computeArrayAccess(arrayResult.getValue().getValue(), indexResult.getValue().getValue());
					result = new Result(value, indexResult.getEffects(), valueCache, thread);
				} finally {
					SideEffectHandler.undoEffects(arrayResult.getEffects());
				}
			}
		} else if (e instanceof FieldAccess) {
			FieldAccess access = (FieldAccess)e;
			Result receiverResult = reEvaluateExpression(access.getExpression(), effects, thread, expressionGenerator);
			if (receiverResult != null && receiverResult.getValue().getValue() instanceof IJavaObject) {
				IJavaType receiverType = isStatic(access.getExpression()) ? getStaticType(access.getExpression()) : receiverResult.getValue().getValue().getJavaType();
				Field field = getField(e);
				IJavaValue value = null;
				if (field != null && field.isFinal())
					value = getValue(e, Collections.<Effect>emptySet());
				else
					try {
						SideEffectHandler.redoEffects(receiverResult.getEffects());
						value = computeFieldAccess(receiverResult.getValue().getValue(), receiverType, field);
					} finally {
						SideEffectHandler.undoEffects(receiverResult.getEffects());
					}
				result = new Result(value, receiverResult.getEffects(), valueCache, thread);
			}
		} else if (e instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression)e;
			Result operandResult = reEvaluateExpression(prefix.getOperand(), effects, thread, expressionGenerator);
			if (!"V".equals(operandResult.getValue().getValue().getSignature()))
				result = new Result(computePrefixOp(operandResult.getValue().getValue(), prefix.getOperator()), operandResult.getEffects(), valueCache, thread);
		} else if (e instanceof MethodInvocation || e instanceof ClassInstanceCreation) {
			Method method = getMethod(e);
			Expression receiver = null;
			Expression[] argExprs = null;
			Set<Effect> curArgEffects = null;
			if (e instanceof MethodInvocation) {
				MethodInvocation call = (MethodInvocation)e;
				receiver = call.getExpression() == null ? new ThisExpression(stack.getReferenceType()) : call.getExpression();
				Result receiverResult = call.getExpression() == null ? new Result(stack.getThis() == null ? stack.getReferenceType().getClassObject() : stack.getThis(), effects, valueCache, thread) : reEvaluateExpression(receiver, effects, thread, expressionGenerator);
				setResult(receiver, receiverResult, Collections.<Effect>emptySet());
				argExprs = call.arguments();
				curArgEffects = receiverResult.getEffects();
			} else {
				ClassInstanceCreation call = (ClassInstanceCreation)e;
				receiver = new PlaceholderExpression(EclipseUtils.getType(call.getType().toString(), stack, target, typeCache));
				argExprs = call.arguments();
				curArgEffects = Collections.<Effect>emptySet();
			}
			// TODO: I think the work below (and some of the receiver stuff above) duplicates getArgValues (e.g., re-computing results).
			ArrayList<Expression> args = new ArrayList<Expression>(argExprs.length);
			IJavaValue receiverValue = getValue(receiver, Collections.<Effect>emptySet());
			boolean hasCrash = receiverValue != null && "V".equals(receiverValue.getSignature());
			for (Expression arg: argExprs) {
				Result argResult = reEvaluateExpression(arg, curArgEffects, thread, expressionGenerator);
				setResult(arg, argResult, Collections.<Effect>emptySet());
				args.add(arg);
				hasCrash = hasCrash || "V".equals(argResult.getValue().getValue().getSignature());
				curArgEffects = argResult.getEffects();
			}
			if (!hasCrash)
				result = computeCall(method, receiver, args, thread, false, expressionGenerator);
		} else if (e instanceof CastExpression) {
			result = reEvaluateExpression(((CastExpression)e).getExpression(), effects, thread, expressionGenerator);
		} else if (e instanceof ParenthesizedExpression) {
			result = reEvaluateExpression(((ParenthesizedExpression)e).getExpression(), effects, thread, expressionGenerator);
		} else
			throw new RuntimeException("Unexpected expression type:" + e.getClass().getName());
		setResult(e, result, effects);
		//System.out.println("Reevaluating " + e.toString() + " with effects " + effects.toString() + " got " + result);
		if (expressionGenerator != null)
			expressionGenerator.addEquivalentExpression(e, effects);
		return result;
	}
	
	public String getToStringWithEffects(Expression expr, IJavaValue value) throws DebugException {
		if (sideEffectHandler == null || !sideEffectHandler.isEnabled())  // This should only be null during refinement, in which case we just get the toString without worrying about side effects, as we do when we're not handling side effects.
			return EclipseUtils.javaStringOfValue(value, stack, true);
		Result result = getResult(expr, Collections.<Effect>emptySet());
		Set<Effect> effects = result == null ? Collections.<Effect>emptySet() : result.getEffects();
		try {
			//System.out.println("Getting toString of " + expr + " with effects " + effects);
			SideEffectHandler.redoEffects(effects);
			timeoutChecker.startEvaluating(null);
			return EclipseUtils.javaStringOfValue(value, stack, true);
		} catch (DebugException e) {
			//System.out.println("Crashed on getting toString of " + expr + " got " + EclipseUtils.getExceptionMessage(e));
			return value.toString();
		} finally {
			timeoutChecker.stopEvaluating();
			SideEffectHandler.undoEffects(effects);
			//System.out.println("Got toString of " + expr.toString());
		}
	}

	// Helper methods to create AST nodes, as they don't seem to have useful constructors.

	/*
	 * My nodes can have the following properties:
	 * isStatic: Exists (and is true) iff the expression is a static name.
	 * value: The value of the expression, or null if it is unknown..  All nodes should have this.
	 * isConstant: Exists (and is true) iff the expression is a constant (e.g., 0, true, null).
	 */

	private Expression initSimple(Expression e, IJavaValue value, IJavaThread thread) {
		//e.setProperty("isConstant", true);
		Result result = new Result(value, valueCache, thread);
		setResult(e, result, Collections.<Effect>emptySet());
		return e;
	}

	// Pass in cached int type for efficiency.
	public Expression makeNumber(String val, IJavaValue value, IJavaType type, IJavaThread thread) {
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
	public Expression makeInt(int num, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new IntLiteral(type, num), value, thread);
	}
	// Pass in cached int type for efficiency.
	public Expression makeLong(long num, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new LongLiteral(type, num), value, thread);
	}
	// Pass in cached int type for efficiency.
	public Expression makeFloat(float num, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new FloatLiteral(type, num), value, thread);
	}
	// Pass in cached int type for efficiency.
	public Expression makeDouble(double num, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new DoubleLiteral(type, num), value, thread);
	}
	// Pass in cached boolean type for efficiency.
	public Expression makeBoolean(boolean val, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new BooleanLiteral(type, val), value, thread);
	}

	public Expression makeChar(char val, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new CharacterLiteral(type, val), value, thread);
	}

	public Expression makeString(String val, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new StringLiteral(type, val), value, thread);
	}

	public Expression makeNull(IJavaThread thread) {
		return initSimple(NullLiteral.getNullLiteral(), target.nullValue(), thread);
	}

	public Expression makeVar(String name, IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new SimpleName(type, name), value, thread);
	}

	public Expression makeFieldVar(String name, IJavaType type, Expression thisExpr, Field field, IJavaThread thread) {
		try{
			Expression e = makeVar(name, computeFieldAccess(getValue(thisExpr, Collections.<Effect>emptySet()), thisExpr.getStaticType(), field), type, thread);
			setDepth(e, 1);
			setField(e, field);
			return e;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	private Expression newStaticName(String name, IJavaValue value, IJavaReferenceType type, IJavaThread thread) {
		Expression e = makeName(type, name);
		setStatic(e, type);
		setValue(e, value, Collections.<Effect>emptySet(), thread);
		return e;
	}

	public Expression makeStaticName(String name, IJavaReferenceType type, IJavaThread thread) {
		try {
			IJavaValue value = type.getClassObject();
			Result result = new Result(value, valueCache, thread);
			Expression expr = newStaticName(name, value, type, thread);
			setResult(expr, result, Collections.<Effect>emptySet());
			return expr;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}

	public Expression makeThis(IJavaValue value, IJavaType type, IJavaThread thread) {
		return initSimple(new ThisExpression(type), value, thread);
	}

	public Expression makeInfix(Expression left, InfixExpression.Operator op, Expression right, IJavaType type, IJavaThread thread) throws NumberFormatException, DebugException {
		InfixExpression e = makeInfix(left, op, right, type);
		Result operandResults = computeResultForBinaryOp(left, right, thread);
		if (operandResults == null)
			return null;
		IJavaValue value = computeInfixOp(getValue(left, Collections.<Effect>emptySet()), op, operandResults.getValue().getValue(), left.getStaticType() != null ? left.getStaticType() : right.getStaticType());
		if (value == null)
			return null;
		Result result = new Result(value, operandResults.getEffects(), valueCache, thread);
		setResult(e, result, Collections.<Effect>emptySet());
		return e;
	}

	public InfixExpression makeInfix(Expression l, InfixExpression.Operator op, Expression r, IJavaType type) {
		return new InfixExpression(type, parenIfNeeded(l), op, parenIfNeeded(r));
	}

	public Expression makeArrayAccess(Expression array, Expression index, IJavaThread thread) throws NumberFormatException, DebugException {
		Result operandResults = computeResultForBinaryOp(array, index, thread);
		if (operandResults == null)
			return null;
		IJavaValue value = null;
		IJavaValue arrayValue = getValue(array, Collections.<Effect>emptySet());
		if (arrayValue != null && operandResults.getValue().getValue() != null) {
			value = computeArrayAccess(arrayValue, operandResults.getValue().getValue());
			if (value == null)
				return null;
		}
		IJavaType type = getArrayElementType(array);
		ArrayAccess e = makeArrayAccess(type, array, index);
		Result result = new Result(value, operandResults.getEffects(), valueCache, thread);
		setResult(e, result, Collections.<Effect>emptySet());
		return e;
	}

	public ArrayAccess makeArrayAccess(IJavaType type, Expression array, Expression index) {
		return new ArrayAccess(type, array, index);
	}

	public Expression makeFieldAccess(Expression obj, String name, IJavaType fieldType, Field field, IJavaThread thread) {
		try {
			FieldAccess e = makeFieldAccess(obj, name, fieldType, field);
			Result objResult = getResult(obj, Collections.<Effect>emptySet());
			IJavaValue value = computeFieldAccess(objResult.getValue().getValue(), obj.getStaticType(), field);
			Result result = new Result(value, objResult.getEffects(), valueCache, thread);
			setResult(e, result, Collections.<Effect>emptySet());
			return e;
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public FieldAccess makeFieldAccess(Expression obj, String name, IJavaType type, Field field) {
		FieldAccess e = new FieldAccess(type, obj, makeSimpleName(null, name));
		setField(e, field);
		return e;
	}

	public Expression makePrefix(Expression operand, PrefixExpression.Operator op, IJavaThread thread) {
		try {
			PrefixExpression e = makePrefix(operand, op);
			Result operandResult = getResult(operand, Collections.<Effect>emptySet());
			IJavaValue value = computePrefixOp(operandResult.getValue().getValue(), op);
			Result result = new Result(value, operandResult.getEffects(), valueCache, thread);
			setResult(e, result, Collections.<Effect>emptySet());
			return e;
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public PrefixExpression makePrefix(Expression operand, PrefixExpression.Operator op) {
		return new PrefixExpression(op, operand);
	}

	public Expression makePostfix(Expression operand, PostfixExpression.Operator op, IJavaThread thread) {
		PostfixExpression e = new PostfixExpression(operand, op);
		Result operandResult = getResult(operand, Collections.<Effect>emptySet());
		IJavaValue value = computePostfixOp(operandResult.getValue().getValue(), op);
		Result result = new Result(value, operandResult.getEffects(), valueCache, thread);
		setResult(e, result, Collections.<Effect>emptySet());
		return e;
	}

	public Expression makeCall(String name, Expression receiver, ArrayList<Expression> args, IJavaType returnType, IJavaType thisType, Method method, IJavaThread thread, StaticEvaluator staticEvaluator) {
		IJavaValue value = staticEvaluator.evaluateCall(receiver, args, method, target);
		Result result = null;
		if (value == null)
			result = computeCall(method, receiver, args, thread, true, null);
		else
			result = new Result(value, valueCache, thread);
		Expression e = null;
		if (receiver instanceof PlaceholderExpression) {
			e = makeClassInstanceCreation(new SimpleType(receiver.getStaticType(), makeName(receiver.getStaticType(), EclipseUtils.sanitizeTypename(name))), args, method, Collections.<Effect>emptySet(), result);
		} else {
			if (receiver instanceof ThisExpression || receiver.getStaticType().equals(thisType))
				receiver = null;  // Don't use a receiver if it is null or the this type.
			e = makeCall(name, receiver == null ? null : receiver, args, method, returnType, Collections.<Effect>emptySet(), result);
		}
		return e;
	}
	/*private Expression makeCall(String name, String classname, ArrayList<Expression> args, IJavaType returnType) {
    	return makeCall(name, newStaticName(classname), args, returnType, null);
    }*/
	public Expression makeCall(String name, Expression receiver, ArrayList<Expression> args, Method method, IJavaType returnType, Set<Effect> effects, Result result) {
		Expression[] newArgs = new Expression[args.size()];
		args.toArray(newArgs);
		MethodInvocation e = new MethodInvocation(returnType, receiver, makeSimpleName(null, name), newArgs);
		setMethod(e, method);
		setResult(e, result, effects);
    	return e;
    }

	public Expression makeCast(Expression obj, IJavaType targetType) {
		try {
			return makeCast(obj, targetType, targetType.getName());
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	// I need to pass the target type name because it could be a shortened (not fully-qualified) version.
	public CastExpression makeCast(Expression obj, IJavaType targetType, String targetTypeName) throws DebugException {
		CastExpression e = new CastExpression(makeType(targetType, targetTypeName), obj);
		copyResults(obj, e);
		return e;
	}

	public Expression makeInstanceOf(Expression obj, Type targetDomType, IJavaType booleanType, IJavaValue value, IJavaThread thread) throws DebugException {
		assert booleanType.getName().equals("boolean");
		InstanceofExpression e = new InstanceofExpression(booleanType, obj, targetDomType);
		Result result = new Result(value, getResult(obj, Collections.<Effect>emptySet()).getEffects(), valueCache, thread);
		setResult(e, result, Collections.<Effect>emptySet());
		return e;
	}

	public Expression makeConditional(Expression cond, Expression t, Expression e, IJavaType type) {
		try {
			ConditionalExpression ex = new ConditionalExpression(type, cond, t, e);
			Result result = computeConditionalOp(getResult(cond, Collections.<Effect>emptySet()), getResult(t, Collections.<Effect>emptySet()), getResult(e, Collections.<Effect>emptySet()));
			setResult(ex, result, Collections.<Effect>emptySet());
			return ex;
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public ClassInstanceCreation makeClassInstanceCreation(Type type, ArrayList<Expression> args, Method method, Set<Effect> effects, Result result) {
		Expression[] newArgs = new Expression[args.size()];
		args.toArray(newArgs);
		ClassInstanceCreation e = new ClassInstanceCreation(type, newArgs);
		setMethod(e, method);
		setResult(e, result, effects);
    	return e;
	}

	public ParenthesizedExpression makeParenthesized(Expression e) {
		ParenthesizedExpression p = new ParenthesizedExpression(e);
		copyResults(e, p);
		return p;
	}

	public Expression makeSuperFieldAccess(Name qualifier, Expression receiverExpr, String name, IJavaType fieldType, Field field, IJavaThread thread) {
		try {
			SuperFieldAccess e = new SuperFieldAccess(fieldType, qualifier, makeSimpleName(null, name));
			IJavaValue value = computeFieldAccess(getValue(receiverExpr, Collections.<Effect>emptySet()), receiverExpr.getStaticType(), field);
			if (value != null)
				setResult(e, new Result(value, valueCache, thread), Collections.<Effect>emptySet());
			setField(e, field);
			return e;
		} catch (DebugException ex) {
			throw new RuntimeException(ex);
		}
	}

	public Expression makeSuperCall(String name, Name qualifier, ArrayList<Expression> args, IJavaType returnType, Result result, Method method) {
		Expression[] newArgs = new Expression[args.size()];
		for (int i = 0; i < args.size(); i++)
			newArgs[i] = args.get(i);
		SuperMethodInvocation e = new SuperMethodInvocation(returnType, qualifier, makeSimpleName(null, name), newArgs);
		setResult(e, result, Collections.<Effect>emptySet());
		setMethod(e, method);
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

	private void setValue(Expression e, IJavaValue v, Set<Effect> effects, IJavaThread thread) {
		Utils.addToMapMap(results, effects, e.getID(), new Result(v, valueCache, thread));
	}

	public IJavaValue getValue(Expression e, Set<Effect> effects) {
		Result result = getResult(e, effects);
		if (result == null)
			return null;
		return result.getValue().getValue();
	}

	public void setResult(Expression e, Result r, Set<Effect> effects) {
		Utils.addToMapMap(results, effects, e.getID(), r);
	}

	public void copyResults(Expression oldExpr, Expression newExpr) {
		int oldID = oldExpr.getID();
		int newID = newExpr.getID();
		for (Map<Integer, Result> effectResults: results.values()) {
			Result result = effectResults.get(oldID);
			if (result != null)
				effectResults.put(newID, result);
		}
	}

	public Result getResult(Expression e, Set<Effect> effects) {
		return getResult(e.getID(), effects);
	}

	private Result getResult(int id, Set<Effect> effects) {
		Map<Integer, Result> effectResults = results.get(effects);
		if (effectResults == null)
			return null;
		return effectResults.get(id);
	}
	
	public void setResultString(Expression e, String resultString) {
		resultStrings.put(e.getID(), resultString);
	}
	
	public String getResultString(Expression e) {
		return resultStrings.get(e.getID());
	}

	public void setDepth(Expression e, int d) {
		depths.put(e.getID(), d);
	}

	public Object getDepthOpt(Expression e) {
		return depths.get(e.getID());
	}

	private void setStatic(Expression e, IJavaReferenceType type) {
		statics.put(e.getID(), type);
	}

	public IJavaReferenceType getStaticType(Expression e) {
		return statics.get(e.getID());
	}

	public boolean isStatic(Expression e) {
		return getStaticType(e) != null;
	}

	private void setMethod(Expression e, Method method) {
		methods.put(e.getID(), method);
	}

	public Method getMethod(Expression e) {
		return methods.get(e.getID());
	}

	public Method getMethod(int id) {
		return methods.get(id);
	}

	private void setField(Expression e, Field field) {
		fields.put(e.getID(), field);
	}

	public Field getField(Expression e) {
		return fields.get(e.getID());
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
	
	public int getNumCrashes() {
		return numCrashes;
	}
	
	public static class Metadata {
		
		private final Map<Integer, Method> subsetMethods;
		private final Map<Integer, Field> subsetFields;
		
		private Metadata(Map<Integer, Method> subsetMethods, Map<Integer, Field> subsetFields) {
			this.subsetMethods = subsetMethods;
			this.subsetFields = subsetFields;
		}
		
		public static Metadata emptyMetadata() {
			return new Metadata(new HashMap<Integer, Method>(), new HashMap<Integer, Field>());
		}
		
		public void addMetadataFor(List<Expression> exprs, final ExpressionMaker expressionMaker) {
			ASTVisitor visitor = new ASTVisitor() {
	    		@Override
	    		public void postVisit(ASTNode node) {
	    			if (node instanceof Expression) {
	    				int id = ((Expression)node).getID();
    					Method method = expressionMaker.getMethod(id);
    					if (method != null)
    						subsetMethods.put(id, method);
    					Field field = expressionMaker.getField(id);
    					if (field != null)
    						subsetFields.put(id, field);
	    			}
	    		}
			};
			for (Expression e: exprs)
				e.accept(visitor);
		}
		
	}

}
