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
import codehint.ast.BooleanLiteral;
import codehint.ast.CastExpression;
import codehint.ast.ClassInstanceCreation;
import codehint.ast.Expression;
import codehint.ast.FieldAccess;
import codehint.ast.InfixExpression;
import codehint.ast.MethodInvocation;
import codehint.ast.NullLiteral;
import codehint.ast.NumberLiteral;
import codehint.ast.ParenthesizedExpression;
import codehint.ast.PlaceholderExpression;
import codehint.ast.PostfixExpression;
import codehint.ast.PrefixExpression;
import codehint.ast.QualifiedName;
import codehint.ast.SimpleName;
import codehint.ast.ThisExpression;
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
import codehint.expreval.TimeoutChecker;
import codehint.utils.EclipseUtils;
import codehint.utils.Utils;

import com.sun.jdi.ClassNotLoadedException;
import com.sun.jdi.Field;
import com.sun.jdi.Method;

public class ExpressionEvaluator {

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
	
	public ExpressionEvaluator(IJavaStackFrame stack, ValueCache valueCache, TypeCache typeCache, TimeoutChecker timeoutChecker, NativeHandler nativeHandler, SideEffectHandler sideEffectHandler, Metadata metadata) {
		this(stack, valueCache, typeCache, timeoutChecker, nativeHandler, sideEffectHandler, metadata.subsetMethods, metadata.subsetFields);
	}
	
	private ExpressionEvaluator(IJavaStackFrame stack, ValueCache valueCache, TypeCache typeCache, TimeoutChecker timeoutChecker, NativeHandler nativeHandler, SideEffectHandler sideEffectHandler, Map<Integer, Method> methods, Map<Integer, Field> fields) {
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

	IJavaValue computeInfixOp(IJavaValue left, InfixExpression.Operator op, IJavaValue right, IJavaType type) throws NumberFormatException, DebugException {
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

	IJavaValue computePrefixOp(IJavaValue e, PrefixExpression.Operator op) throws DebugException {
		if (e == null )
			return null;
		if (op == PrefixExpression.Operator.MINUS)
			return valueCache.getIntJavaValue(-Integer.parseInt(e.getValueString()));
		if (op == PrefixExpression.Operator.NOT)
			return valueCache.getBooleanJavaValue(!Boolean.parseBoolean(e.getValueString()));
		throw new RuntimeException("Unknown prefix operation: " + op.toString());
	}

	static IJavaValue computePostfixOp(IJavaValue e, PostfixExpression.Operator op) {
		if (e == null )
			return null;
		throw new RuntimeException("Unknown postfix operation: " + op.toString());
	}
	
	static Result computeConditionalOp(Result c, Result t, Result e) throws DebugException {
		if (c == null || t == null || e == null)
			return null;
		boolean cond = Boolean.parseBoolean(c.getValue().getValue().getValueString());
		if (cond)
			return t;
		else
			return e;
	}
	
	static IJavaValue computeArrayAccess(IJavaValue l, IJavaValue r) throws NumberFormatException, DebugException {
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
	
	IJavaValue computeFieldAccess(IJavaValue receiverValue, IJavaType receiverType, Field field) throws DebugException {
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
	Result computeCall(Method method, Expression receiver, ArrayList<Expression> args, IJavaThread thread, boolean isOutermost, ExpressionGenerator expressionGenerator) {
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
	Result computeResultForBinaryOp(Expression left, Expression right, IJavaThread thread) {
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

	void setValue(Expression e, IJavaValue v, Set<Effect> effects, IJavaThread thread) {
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

	void setStatic(Expression e, IJavaReferenceType type) {
		statics.put(e.getID(), type);
	}

	public IJavaReferenceType getStaticType(Expression e) {
		return statics.get(e.getID());
	}

	public boolean isStatic(Expression e) {
		return getStaticType(e) != null;
	}

	void setMethod(Expression e, Method method) {
		methods.put(e.getID(), method);
	}

	public Method getMethod(Expression e) {
		return methods.get(e.getID());
	}

	public Method getMethod(int id) {
		return methods.get(id);
	}

	void setField(Expression e, Field field) {
		fields.put(e.getID(), field);
	}

	public Field getField(Expression e) {
		return fields.get(e.getID());
	}

	public Field getField(int id) {
		return fields.get(id);
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
		
		public void addMetadataFor(List<Expression> exprs, final ExpressionEvaluator expressionEvaluator) {
			ASTVisitor visitor = new ASTVisitor() {
	    		@Override
	    		public void postVisit(ASTNode node) {
	    			if (node instanceof Expression) {
	    				int id = ((Expression)node).getID();
    					Method method = expressionEvaluator.getMethod(id);
    					if (method != null)
    						subsetMethods.put(id, method);
    					Field field = expressionEvaluator.getField(id);
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
