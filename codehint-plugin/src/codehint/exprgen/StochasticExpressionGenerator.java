package codehint.exprgen;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaReferenceType;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.core.IJavaVariable;

import com.sun.jdi.Field;
import com.sun.jdi.Method;
import com.sun.jdi.TypeComponent;

import codehint.ast.Expression;
import codehint.ast.InfixExpression;
import codehint.ast.NullLiteral;
import codehint.ast.PlaceholderExpression;
import codehint.ast.PrefixExpression;
import codehint.dialogs.SynthesisDialog;
import codehint.effects.Effect;
import codehint.effects.SideEffectHandler;
import codehint.expreval.EvaluationManager;
import codehint.expreval.StaticEvaluator;
import codehint.exprgen.typeconstraint.FieldConstraint;
import codehint.exprgen.typeconstraint.MethodConstraint;
import codehint.exprgen.typeconstraint.TypeConstraint;
import codehint.exprgen.weightedlist.CombinationWeightedList;
import codehint.exprgen.weightedlist.ExpressionWeightedList;
import codehint.exprgen.weightedlist.MethodFieldWeightedList;
import codehint.exprgen.weightedlist.WeightedList;
import codehint.property.Property;
import codehint.utils.EclipseUtils;

// TODO: Get better probabilities for array length, integer negation, different binary ops.
// TODO: Do equivalences.
public class StochasticExpressionGenerator extends ExpressionGenerator {

	private static final int MAX_ITERS = 200;
	private static final int MAX_CANDIDATES = 100;
	
	private final static InfixExpression.Operator[] NUMBER_BINARY_OPS = new InfixExpression.Operator[] { InfixExpression.Operator.PLUS, InfixExpression.Operator.MINUS, InfixExpression.Operator.TIMES, InfixExpression.Operator.DIVIDE };
	
	private final Random random;
	
	private ExpressionWeightedList primitives;
	private ExpressionWeightedList objects;
	private ExpressionWeightedList nulls;
	private ExpressionWeightedList names;
	private CandidateWeightedLists candidates;
	private CandidateLists candidatesList;
	
	public StochasticExpressionGenerator(IJavaDebugTarget target, IJavaStackFrame stack, SideEffectHandler sideEffectHandler, ExpressionMaker expressionMaker, ExpressionEvaluator expressionEvaluator, SubtypeChecker subtypeChecker, TypeCache typeCache, EvaluationManager evalManager, StaticEvaluator staticEvaluator, Weights weights) {
		super(target, stack, sideEffectHandler, expressionMaker, expressionEvaluator, subtypeChecker, typeCache, evalManager, staticEvaluator, weights);
		this.random = new Random();
	}

	@Override
	public ArrayList<Expression> generateExpression(Property property, TypeConstraint typeConstraint, String varName, boolean searchConstructors, boolean searchOperators, SynthesisDialog synthesisDialog, IProgressMonitor monitor, int maxExprDepth) {
		this.names = new ExpressionWeightedList(expressionEvaluator, weights);
		this.primitives = new ExpressionWeightedList(expressionEvaluator, weights);
		this.objects = new ExpressionWeightedList(expressionEvaluator, weights);
		this.nulls = new ExpressionWeightedList(expressionEvaluator, weights);
		this.candidates = new CandidateWeightedLists();
		this.candidatesList = new CandidateLists();
		return genExprs(property, typeConstraint, searchConstructors, searchOperators, synthesisDialog, monitor);
		
	}
	
	private ArrayList<Expression> genExprs(Property property, TypeConstraint typeConstraint, boolean searchConstructors, boolean searchOperators, SynthesisDialog synthesisDialog, IProgressMonitor monitor) {
		try {
			initSearch();
			ReceiverWeightedLists receivers = new ReceiverWeightedLists();
			Set<Expression> evaledExprs = new HashSet<Expression>();
			ArrayList<Expression> results = new ArrayList<Expression>();
			addSeeds(typeConstraint);
			results.addAll(evalManager.evaluateExpressions(candidatesList, property, getVarType(typeConstraint), synthesisDialog, monitor, ""));
			for (int i = 0; i < MAX_ITERS && candidatesList.size() < MAX_CANDIDATES; i++) {
				if (monitor.isCanceled())
					throw new OperationCanceledException();
				//System.out.println("Iter " + i + " candidates: " + candidates);
				Expression curExpr = searchOperators ? candidates.getWeighted() : receivers.getWeighted();
				//System.out.println("Expr: " + curExpr);
				Expression newExpr = extendExpression(curExpr, evaledExprs, searchConstructors, searchOperators);
				//System.out.println(" New expr: " + newExpr);
				/*if (newExpr == null)
					System.out.println(curExpr);*/
				if (newExpr != null) {
					if (!evaledExprs.contains(newExpr)) {
						evaledExprs.add(newExpr);
						candidates.addWeighted(newExpr);
						if (checkSpec(newExpr, typeConstraint, property, synthesisDialog, monitor))
							results.add(newExpr);
					}/* else
						System.out.println(" " + newExpr + " already seen");*/
				}
			}
			//System.out.println("Candidates: " + candidates);
			return results;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}		
	}

	/**
	 * Adds locals, constants, and imports to seed the search.
	 * @param typeConstraint The type constraint.
	 * @throws DebugException
	 * @throws JavaModelException
	 */
	private void addSeeds(TypeConstraint typeConstraint) throws DebugException, JavaModelException {
		for (IJavaVariable l : stack.getLocalVariables())
			candidates.addWeighted(expressionMaker.makeVar(l.getName(), (IJavaValue)l.getValue(), EclipseUtils.getTypeOfVariableAndLoadIfNeeded(l, stack), thread));
		if (!stack.isStatic())
			objects.addWeighted(expressionMaker.makeThis(stack.getThis(), thisType, thread));
		if (!(typeConstraint instanceof MethodConstraint) && !(typeConstraint instanceof FieldConstraint))  // If we have a method or field constraint, we can't have null.)
			nulls.addWeighted(expressionMaker.makeNull(thread));
		if (stack.isStatic() || stack.isConstructor())
			names.addWeighted(expressionMaker.makeStaticName(stack.getReceivingTypeName(), thisType, thread));
		for (IImportDeclaration imp : imports) {
			// TODO: Handle static imports.
			// TODO: Decomp with deterministic version?
			if (!imp.isOnDemand()) {
				String fullName = imp.getElementName();
				String shortName = EclipseUtils.getUnqualifiedName(fullName);  // Use the unqualified typename for brevity.
				if (!Flags.isStatic(imp.getFlags())) {
					IJavaReferenceType importedType = (IJavaReferenceType)EclipseUtils.getTypeAndLoadIfNeeded(fullName, stack, target, typeCache);
					if (importedType != null) {
						if (hasStaticFieldOrMethod(importedType))
							names.addWeighted(expressionMaker.makeStaticName(shortName, importedType, thread));
					}
				}
			}
		}
	}
	
	/**
	 * Checks whether the given type has a static field or method.
	 * @param type The type to check.
	 * @return Whether the given type has a static field or method.
	 */
	private boolean hasStaticFieldOrMethod(IJavaType type) {
		for (Method method: getMethods(type, sideEffectHandler))
			if (method.isStatic())
				return true;
		for (Field field: getFields(type))
			if (field.isStatic())
				return true;
		return false;
	}
	
	/**
	 * Checks whether the given expression satisfies the given specification.
	 * @param expr The expression.
	 * @param typeConstraint The type constraint.
	 * @param property The specification.
	 * @param synthesisDialog The synthesis dialog.
	 * @param monitor The progress monitor.
	 * @return Whether the given expression satisfies the given specification.
	 * @throws DebugException 
	 */
	private boolean checkSpec(Expression expr, TypeConstraint typeConstraint, Property property, SynthesisDialog synthesisDialog, IProgressMonitor monitor) throws DebugException {
		if (expr == null || !typeConstraint.isFulfilledBy(expr.getStaticType(), subtypeChecker, typeCache, stack, target))
			return false;
		List<Expression> exprList = Collections.singletonList(expr);
    	if (property != null && !EvaluationManager.canEvaluateStatically(property))
			evalManager.cacheMethodResults(exprList);
		return !evalManager.evaluateExpressions(exprList, property, getVarType(typeConstraint), synthesisDialog, monitor, "").isEmpty();
	}
	
	/**
	 * Extends the given expression.
	 * @param expr The expression to extend.
	 * @param evaledExprs The set of expressions we have already evaluated.
	 * @param searchConstructors Whether we should search constructors.
	 * @param searchOperators Whether we should search operators.
	 * @return A new expression that extends the given expression,
	 * or null if the expression we tried crashes.
	 * @throws DebugException
	 * @throws JavaModelException
	 */
	private Expression extendExpression(Expression expr, Set<Expression> evaledExprs, boolean searchConstructors, boolean searchOperators) throws DebugException, JavaModelException {
		IJavaValue value = expressionEvaluator.getValue(expr, Collections.<Effect>emptySet());
		if (value.isNull()) {
			//System.out.println(" Extending null");
			return null;
		}
		IJavaType type = expr.getStaticType();
		if (EclipseUtils.isObjectOrInterface(type))
			return extendObject(expr, evaledExprs, searchConstructors);
		else if (searchOperators && EclipseUtils.isInt(type))
			return extendNumber(expr);
		else if (EclipseUtils.isArray(type))
			return extendArray(expr);
		else {
			//System.out.println(" Cannot extend expression's type.");
			return null;
		}
	}

	/**
	 * Extends the given object by accessing a field or calling a method.
	 * @param receiver The receiver.  Note that this can be a static name,
	 * which we can extend by making a static access.
	 * @param evaledExprs The set of expressions we have already evaluated.
	 * We use this to avoid re-evaluating a call we have already evaluated.
	 * @param searchConstructors Whether we should search constructors.
	 * @return A new expression that extends the given object.
	 * @throws DebugException
	 * @throws JavaModelException
	 */
	private Expression extendObject(Expression receiver, Set<Expression> evaledExprs, boolean searchConstructors) throws DebugException, JavaModelException {
		if (classBlacklist.contains(receiver.getStaticType().getName()))
			return null;
		boolean isStatic = expressionEvaluator.isStatic(receiver);
		IJavaType receiverType = getActualTypeForDowncast(receiver, isStatic);
		// Get possible fields and methods.
		MethodFieldWeightedList comps = new MethodFieldWeightedList(weights);
		for (Method method: getMethods(receiverType, sideEffectHandler))
			if (isUsefulMethod(method, receiver, searchConstructors && method.isConstructor()) && (!isStatic || method.isStatic()))
				comps.addWeighted(method);
		for (Field field: getFields(receiver.getStaticType()))  // TODO: Allow downcasting for field accesses here and in deterministic.
			if (isUsefulField(field) && (!isStatic || field.isStatic()))
				comps.addWeighted(field);
		// Access component.
		TypeComponent component = comps.getWeighted();
		if (component instanceof Field) {
			Field field = (Field)component;
			IJavaType fieldType = EclipseUtils.getTypeAndLoadIfNeeded(field.typeName(), stack, target, typeCache);
			return makeFieldAccess(receiver, field, fieldType);
		} else if (component instanceof Method) {
			Method method = (Method)component;
			List<String> argumentTypeNames = method.argumentTypeNames();
			OverloadChecker overloadChecker = new OverloadChecker(receiverType, stack, target, typeCache, subtypeChecker);
			overloadChecker.setMethod(method);
			ArrayList<Expression> args = new ArrayList<Expression>(argumentTypeNames.size());
			for (String argTypeName: argumentTypeNames) {
				IJavaType argType = EclipseUtils.getTypeAndLoadIfNeeded(argTypeName, stack, target, typeCache);
				if (argType == null) {
					//System.err.println("I cannot get the class of the arguments to " + objTypeImpl.name() + "." + method.name() + "()");
					break;
				}
				ExpressionWeightedList argChoices = new ExpressionWeightedList(expressionEvaluator, weights);
				argChoices.addAllWeighted(getArgs(candidatesList, receiver, method, overloadChecker, argType, args.size(), -1));
				Expression arg = argChoices.getWeighted();
				if (arg == null) {
					//System.out.println(" No legal args.");
					return null;
				}
				args.add(castArgIfNecessary(arg, overloadChecker, argType, args.size()));
			}
			boolean isConstructor = method.isConstructor();
			boolean isSubtype = !receiverType.equals(receiver.getStaticType());
			IJavaType returnType = getReturnType(receiver, method, isConstructor);
			Expression actualReceiver = isConstructor ? new PlaceholderExpression(EclipseUtils.getType(method.declaringType().name(), stack, target, typeCache)) : getCallReceiver(receiver, method, isSubtype);
			String name = getCallName(actualReceiver, method);
			Expression newExpr = expressionMaker.makeCall(name, actualReceiver, args, returnType, thisType, method, null);
			if (evaledExprs.contains(newExpr)) {
				//System.out.println(" " + newExpr + " already seen");
				return null;
			}
			Result result = expressionEvaluator.computeCall(method, actualReceiver, args, staticEvaluator);
			if (result == null || "V".equals(result.getValue().getValue().getSignature())) {
				//System.out.println(" " + newExpr + " crashed");
				evaledExprs.add(newExpr);
				return null;
			}
			expressionEvaluator.setResult(newExpr, result, Collections.<Effect>emptySet());
			return newExpr;
		} else {
			assert component == null;
			//System.out.println(" No fields or methods.");
			return null;
		}
	}
	
	/**
	 * Extends the given number with a unary or binary operation.
	 * @param num The number to extend.
	 * @return A new expression that extends the given number.
	 * @throws DebugException
	 */
	private Expression extendNumber(Expression num) throws DebugException {
		ArrayList<Expression> rightExprs = new ArrayList<Expression>();
		for (Expression expr: primitives)
			if (EclipseUtils.isInt(expr.getStaticType()))
				rightExprs.add(expr);
		if (!(num instanceof PrefixExpression) && !(num instanceof InfixExpression))  // Disallow things like -(-x) and -(x + y).
				rightExprs.add(expressionMaker.makeNull(thread));  // As a hack we use null to stand for unary negation.
		if (rightExprs.isEmpty())
			return null;
		ExpressionWeightedList rightChoices = new ExpressionWeightedList(expressionEvaluator, weights);
		rightChoices.addAllWeighted(rightExprs);
		Expression right = rightChoices.getWeighted();
		if (right instanceof NullLiteral)
			return expressionMaker.makePrefix(num, PrefixExpression.Operator.MINUS, thread);
		else {
			Operator op = NUMBER_BINARY_OPS[random.nextInt(NUMBER_BINARY_OPS.length)];
			if (!mightNotCommute(num, right)) {
				int cmp = num.toString().compareTo(right.toString());
				if ((op == InfixExpression.Operator.PLUS || op == InfixExpression.Operator.TIMES) && cmp > 0) {
					Expression tmp = num;
					num = right;
					right = tmp;
				} else if ((op == InfixExpression.Operator.MINUS || op == InfixExpression.Operator.DIVIDE) && cmp == 0)
					return null;
			}
			return expressionMaker.makeInfix(num, op, right, intType, thread);
		}
	}
	
	/**
	 * Extends the given array by indexing into it or
	 * getting its length.
	 * @param arr The array to extend.
	 * @return A new expression that extends the given array.
	 * @throws DebugException
	 */
	private Expression extendArray(Expression arr) throws DebugException {
		Result arrResult = expressionEvaluator.getResult(arr, Collections.<Effect>emptySet());
		int arrLen = ((IJavaArray)arrResult.getValue().getValue()).getLength();
		Set<Effect> arrEffects = arrResult.getEffects();
		ArrayList<Expression> indexExprs = new ArrayList<Expression>();
		for (Expression expr: primitives)
			if (EclipseUtils.isInt(expr.getStaticType())) {
				int index = Integer.parseInt(expressionEvaluator.getValue(expr, arrEffects).getValueString());
				if (index >= 0 && index < arrLen)
					indexExprs.add(expr);
			}
		indexExprs.add(expressionMaker.makeNull(thread));  // As a hack we use null to stand in for array length.
		ExpressionWeightedList indexChoices = new ExpressionWeightedList(expressionEvaluator, weights);
		indexChoices.addAllWeighted(indexExprs);
		Expression index = indexChoices.getWeighted();  // indexExprs is guaranteed to be non-null because of the length.
		if (index instanceof NullLiteral)
			return expressionMaker.makeFieldAccess(arr, "length", intType, null, thread);
		else
			return expressionMaker.makeArrayAccess(arr, index, thread);
	}
	
	/**
	 * Adds the given expression to the correct weighted list.
	 * @param expr The expression to add.
	 */
	private void addCandidate(Expression expr) {
		if (EclipseUtils.isPrimitive(expr.getStaticType()))
			primitives.addWeighted(expr);
		else if (expressionEvaluator.isStatic(expr))
			names.addWeighted(expr);
		else if (expressionEvaluator.getValue(expr, Collections.<Effect>emptySet()).isNull())
			nulls.addWeighted(expr);
		else
			objects.addWeighted(expr);
	}
	
	/**
	 * A weighted list that contains all of our candidates.
	 * We use this instead of one giant weighted list so that
	 * we can control from which list we draw, e.g., avoid
	 * trying to extend null or ints when we're not searching
	 * operators.
	 */
	private class CandidateWeightedLists extends CombinationWeightedList<Expression> {

		public CandidateWeightedLists() {
			super(new ExpressionWeightedList[] { primitives, objects, nulls, names });
		}

		@Override
		public void addWeighted(Expression expr) {
			addCandidate(expr);
		}
		
	}
	
	/**
	 * A weighted list that contains things that can be receivers,
	 * i.e., objects and names.
	 */
	private class ReceiverWeightedLists extends CombinationWeightedList<Expression> {

		public ReceiverWeightedLists() {
			super(new ExpressionWeightedList[] { objects, names });
		}

		@Override
		public void addWeighted(Expression expr) {
			addCandidate(expr);
		}
		
	}
	
	/**
	 * Gives a read-only java.util.List view of all of our
	 * weighted lists, except for names, which we exclude
	 * as they are not valid expressions.
	 */
	private class CandidateLists extends AbstractList<Expression> {

		@Override
		public Expression get(int index) {
			if (index < primitives.size())
				return primitives.get(index);
			else if (index < primitives.size() + objects.size())
				return objects.get(index - primitives.size());
			else
				return nulls.get(index - primitives.size() - objects.size());
		}

		@Override
		public int size() {
			return primitives.size() + objects.size() + nulls.size();
		}
		
		@Override
		public String toString() {
			return primitives.toString() + "; " + objects.toString() + "; " + nulls.toString();
		}
		
	}

}
