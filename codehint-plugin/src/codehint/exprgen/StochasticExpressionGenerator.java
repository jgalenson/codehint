package codehint.exprgen;

import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
import org.eclipse.swt.widgets.Display;

import com.sun.jdi.Field;
import com.sun.jdi.Method;
import com.sun.jdi.TypeComponent;

import codehint.ast.ArrayAccess;
import codehint.ast.ClassInstanceCreation;
import codehint.ast.Expression;
import codehint.ast.FieldAccess;
import codehint.ast.InfixExpression;
import codehint.ast.InfixExpression.Operator;
import codehint.ast.MethodInvocation;
import codehint.ast.NullLiteral;
import codehint.ast.NumberLiteral;
import codehint.ast.PlaceholderExpression;
import codehint.ast.PrefixExpression;
import codehint.ast.SimpleName;
import codehint.ast.ThisExpression;
import codehint.ast.TypeLiteral;
import codehint.dialogs.SynthesisDialog;
import codehint.effects.Effect;
import codehint.effects.SideEffectHandler;
import codehint.expreval.EvaluationManager;
import codehint.expreval.StaticEvaluator;
import codehint.exprgen.typeconstraint.FieldConstraint;
import codehint.exprgen.typeconstraint.MethodConstraint;
import codehint.exprgen.typeconstraint.SupertypeBound;
import codehint.exprgen.typeconstraint.TypeConstraint;
import codehint.exprgen.typeconstraint.UnknownConstraint;
import codehint.exprgen.weightedlist.ExpressionWeightedList;
import codehint.exprgen.weightedlist.MethodFieldWeightedList;
import codehint.exprgen.weightedlist.WeightedCombinationWeightedList;
import codehint.exprgen.weightedlist.IWeightedList;
import codehint.exprgen.weightedlist.WeightedList;
import codehint.property.Property;
import codehint.utils.EclipseUtils;
import codehint.utils.Utils;

// TODO: Get better probabilities for array length, integer negation, different binary ops.
// TODO: Get bigram probabilities of some sort.  They're especially helpful for equivalence expansion.
public class StochasticExpressionGenerator extends ExpressionGenerator {

	private static final int MAX_ITERS = 400;
	private static final int MAX_CANDIDATES = 500;
	private static final int MAX_VALUES = 100;
	
	private final Random random;
	private final WeightedList<InfixExpression.Operator> infixOperators;	
	
	private ExpressionWeightedList primitives;
	private ExpressionWeightedList objects;
	private ExpressionWeightedList arrs;
	private ExpressionWeightedList nulls;
	private ExpressionWeightedList names;
	private ExpressionCombinationWeightedList candidates;
	private CandidateLists candidatesList;

	private Map<String, Integer> uniqueValuesSeenForType;
	
	public StochasticExpressionGenerator(IJavaDebugTarget target, IJavaStackFrame stack, SideEffectHandler sideEffectHandler, ExpressionMaker expressionMaker, ExpressionEvaluator expressionEvaluator, SubtypeChecker subtypeChecker, TypeCache typeCache, EvaluationManager evalManager, StaticEvaluator staticEvaluator, Weights weights) {
		super(target, stack, sideEffectHandler, expressionMaker, expressionEvaluator, subtypeChecker, typeCache, evalManager, staticEvaluator, weights);
		this.random = new Random();
		this.infixOperators = new WeightedList<InfixExpression.Operator>(new InfixExpression.Operator[] { InfixExpression.Operator.PLUS, InfixExpression.Operator.MINUS, InfixExpression.Operator.TIMES, InfixExpression.Operator.DIVIDE }, new double[] { ExprStats.INFIX_PLUS_PROB, ExprStats.INFIX_MINUS_PROB, ExprStats.INFIX_TIMES_PROB, ExprStats.INFIX_DIV_PROB });
	}

	@Override
	public ArrayList<Expression> generateStatement(Property property, TypeConstraint typeConstraint, String varName, boolean searchConstructors, boolean searchOperators, boolean searchStatements, SynthesisDialog synthesisDialog, IProgressMonitor monitor, int maxExprDepth) {
		this.names = new StaticNameWeightedList(expressionEvaluator, weights);
		this.primitives = new ExpressionWeightedList(expressionEvaluator, weights);
		this.objects = new ExpressionWeightedList(expressionEvaluator, weights);
		this.arrs = new ExpressionWeightedList(expressionEvaluator, weights);
		this.nulls = new ExpressionWeightedList(expressionEvaluator, weights);
		this.candidates = makeCandidateWeightedList(searchOperators);
		this.candidatesList = new CandidateLists();
		try {
			if (searchStatements)  // FIXME: Implement searching statements stochastically.
				EclipseUtils.showWarning("Cannot search statements", "The stochastic search cannot yet search statements.", null);
			initSearch();
			this.equivalences.put(Collections.<Effect>emptySet(), new HashMap<Result, ArrayList<Expression>>());
			this.uniqueValuesSeenForType = new HashMap<String, Integer>();
			return genExprs(property, typeConstraint, searchConstructors, searchOperators, synthesisDialog, monitor);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		} catch (JavaModelException e) {
			throw new RuntimeException(e);
		}
		
	}
	
	/**
	 * Makes the candidate weighted list.
	 * @param searchOperators Whether to search operators.
	 * @return The weighted list from which to choose
	 * the initial candidate to extend.
	 */
	private ExpressionCombinationWeightedList makeCandidateWeightedList(boolean searchOperators) {
		ArrayList<IWeightedList<Expression>> objWeightedLists = new ArrayList<IWeightedList<Expression>>(3);
		objWeightedLists.add(objects);
		objWeightedLists.add(nulls);
		objWeightedLists.add(names);
		ExpressionCombinationWeightedList objs = new ExpressionCombinationWeightedList(objWeightedLists, new double[] { ExprStats.INSTANCE_OP_PROB, 0, ExprStats.STATIC_OP_PROB });
		ArrayList<IWeightedList<Expression>> allWeightedLists = new ArrayList<IWeightedList<Expression>>(3);
		allWeightedLists.add(primitives);
		allWeightedLists.add(arrs);
		allWeightedLists.add(objs);
		return new ExpressionCombinationWeightedList(allWeightedLists, new double[] { searchOperators ? ExprStats.PRIM_OP_PROB : 0, ExprStats.ARR_OP_PROB, ExprStats.OBJECT_OP_PROB });
	}
	
	private ArrayList<Expression> genExprs(Property property, TypeConstraint typeConstraint, boolean searchConstructors, boolean searchOperators, final SynthesisDialog synthesisDialog, IProgressMonitor monitor) throws DebugException, JavaModelException {
		Set<Expression> evaledExprs = new HashSet<Expression>();
		Map<Result, Boolean> specCache = new HashMap<Result, Boolean>();
		ExpressionParents parents = new ExpressionParents();
		ArrayList<Expression> results = new ArrayList<Expression>();
		Map<Result, ArrayList<Expression>> curEquivMap = equivalences.get(Collections.<Effect>emptySet());
		addSeeds(typeConstraint);
		results.addAll(evalManager.evaluateStatements(candidatesList, property, getVarType(typeConstraint), synthesisDialog, monitor, ""));
		for (Expression expr: candidatesList) {
			addEquivalentExpression(expr, Collections.<Effect>emptySet());
			Utils.incrementMap(uniqueValuesSeenForType, expr instanceof NullLiteral ? "null" : expr.getStaticType().getName());
		}
		int i;
		for (i = 0; i < MAX_ITERS && curEquivMap.size() < MAX_VALUES && candidatesList.size() < MAX_CANDIDATES; i++) {
			if (monitor.isCanceled())
				throw new OperationCanceledException();
			//System.out.println("Iter " + i + " candidates: " + candidates);
			Expression curExpr = candidates.getWeighted();
			if (curExpr == null)
				continue;
			//System.out.println("Expr: " + curExpr);
			Expression newExpr = extendExpression(curExpr, evaledExprs, typeConstraint, searchConstructors, searchOperators);
			//System.out.println(" New expr: " + newExpr);
			/*if (newExpr == null)
					System.out.println(curExpr);*/
			if (newExpr != null) {
				if (!evaledExprs.contains(newExpr)) {
					final ArrayList<Expression> newResults = expandEquivalences(newExpr, property, typeConstraint, evaledExprs, specCache, parents, monitor);
					results.addAll(newResults);
					Display.getDefault().asyncExec(new Runnable(){
						@Override
						public void run() {
							synthesisDialog.addStatements(newResults);
						}
			    	});
				} /* else
						System.out.println(" " + newExpr + " already seen");*/
			}
		}
		//System.out.println(candidatesList.size() + " candidates (" + curEquivMap.size() + " unique values) in " + i + " iters: " + candidates);
		//System.out.println("Unique values: " + curEquivMap.keySet());
		return results;
	}

	/**
	 * Handles a newly-generated expression, by adding it to
	 * the list of candidates and storing metadata about it.
	 * @param newExpr The newly-generated expression.
	 * @param evaledExprs The set of evaluated expressions.
	 * @param parents The map of parent expressions.
	 * @throws DebugException 
	 */
	private void handleNewExpression(Expression newExpr, Set<Expression> evaledExprs, ExpressionParents parents) throws DebugException {
		candidates.addWeighted(newExpr);
		assert !evaledExprs.contains(newExpr) : newExpr;
		evaledExprs.add(newExpr);
		parents.addParents(newExpr);
		Utils.incrementMap(uniqueValuesSeenForType, newExpr.getStaticType().getName());
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
						if (hasPublicStaticFieldOrMethod(importedType))
							names.addWeighted(expressionMaker.makeStaticName(shortName, importedType, thread));
					}
				}
			}
		}
	}
	
	/**
	 * Checks whether the given type has a public static field or method.
	 * @param type The type to check.
	 * @return Whether the given type has a public static field or method.
	 */
	private boolean hasPublicStaticFieldOrMethod(IJavaType type) {
		for (Method method: getMethods(type, sideEffectHandler))
			if (method.isStatic() && method.isPublic())
				return true;
		for (Field field: getFields(type))
			if (field.isStatic() && field.isPublic())
				return true;
		return false;
	}
	
	/**
	 * Checks whether the given expression satisfies the given specification.
	 * @param expr The expression.
	 * @param typeConstraint The type constraint.
	 * @param property The specification.
	 * @param specCache Cache of specification check results.
	 * @param monitor The progress monitor.
	 * @return Whether the given expression satisfies the given specification.
	 * @throws DebugException 
	 */
	private boolean checkSpec(Expression expr, TypeConstraint typeConstraint, Property property, Map<Result, Boolean> specCache, IProgressMonitor monitor) throws DebugException {
		Result result = expressionEvaluator.getResult(expr, Collections.<Effect>emptySet());
		if (specCache.containsKey(result))
			return specCache.get(result);
		boolean satisfies;
		if (expr == null || !typeConstraint.isFulfilledBy(expr.getStaticType(), subtypeChecker, typeCache, stack, target))
			satisfies = false;
		else {
			List<Expression> exprList = Collections.singletonList(expr);
	    	if (property != null && !EvaluationManager.canEvaluateStatically(property))
				evalManager.cacheMethodResults(exprList);
			satisfies = !evalManager.evaluateStatements(exprList, property, getVarType(typeConstraint), null, monitor, "").isEmpty();
		}
		specCache.put(result, satisfies);
		return satisfies;
	}
	
	/**
	 * Extends the given expression.
	 * @param expr The expression to extend.
	 * @param evaledExprs The set of expressions we have already evaluated.
	 * @param typeConstraint The type constraint.
	 * @param searchConstructors Whether we should search constructors.
	 * @param searchOperators Whether we should search operators.
	 * @return A new expression that extends the given expression,
	 * or null if the expression we tried crashes.
	 * @throws DebugException
	 * @throws JavaModelException
	 */
	private Expression extendExpression(Expression expr, Set<Expression> evaledExprs, TypeConstraint typeConstraint, boolean searchConstructors, boolean searchOperators) throws DebugException, JavaModelException {
		IJavaValue value = expressionEvaluator.getValue(expr, Collections.<Effect>emptySet());
		if (value.isNull()) {
			//System.out.println(" Extending null");
			return null;
		}
		IJavaType type = expr.getStaticType();
		if (EclipseUtils.isObjectOrInterface(type))
			return extendObject(expr, evaledExprs, typeConstraint, searchConstructors);
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
	 * @param typeConstraint The type constraint.
	 * @param searchConstructors Whether we should search constructors.
	 * @return A new expression that extends the given object.
	 * @throws DebugException
	 * @throws JavaModelException
	 */
	private Expression extendObject(Expression receiver, Set<Expression> evaledExprs, TypeConstraint typeConstraint, boolean searchConstructors) throws DebugException, JavaModelException {
		if (classBlacklist.contains(receiver.getStaticType().getName()))
			return null;
		boolean isStatic = expressionEvaluator.isStatic(receiver);
		IJavaType receiverType = getActualTypeForDowncast(receiver, isStatic);
		// Get possible fields and methods.
		MethodFieldWeightedList comps = new TargetedMethodFieldWeightedList(typeConstraint, receiver, weights);
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
		ExpressionWeightedList rightChoices = new ExpressionWeightedList(expressionEvaluator, weights);
		for (Expression expr: primitives)
			if (EclipseUtils.isInt(expr.getStaticType()))
				rightChoices.addWeighted(expr);
		boolean canDoPrefix = !(num instanceof PrefixExpression) && !(num instanceof InfixExpression);  // Disallow things like -(-x) and -(x + y).
		if (rightChoices.isEmpty() || random.nextDouble() * ExprStats.PRIM_OP_PROB < (canDoPrefix ? ExprStats.PREFIX_PROB : 0)) {
			if (!canDoPrefix)
				return null;
			return expressionMaker.makePrefix(num, PrefixExpression.Operator.MINUS, thread);
		} else {
			Expression right = rightChoices.getWeighted();
			Operator op = infixOperators.getWeighted();
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
		ExpressionWeightedList indexChoices = new ExpressionWeightedList(expressionEvaluator, weights);
		for (Expression expr: primitives)
			if (EclipseUtils.isInt(expr.getStaticType())) {
				int index = Integer.parseInt(expressionEvaluator.getValue(expr, arrEffects).getValueString());
				if (index >= 0 && index < arrLen)
					indexChoices.addWeighted(expr);
			}
		if (indexChoices.isEmpty() || random.nextDouble() * ExprStats.ARR_OP_PROB < ExprStats.ARR_LEN_PROB)
			return expressionMaker.makeFieldAccess(arr, "length", intType, null, thread);
		else
			return expressionMaker.makeArrayAccess(arr, indexChoices.getWeighted(), thread);
	}
	
	/**
	 * A weighted list of methods and fields that biases
	 * the search towards useful types.
	 */
	private class TargetedMethodFieldWeightedList extends MethodFieldWeightedList {
		
		private final TypeConstraint typeConstraint;
		private final Expression receiver;

		public TargetedMethodFieldWeightedList(TypeConstraint typeConstraint, Expression receiver, Weights weights) {
			super(weights);
			this.typeConstraint = typeConstraint;
			this.receiver = receiver;
		}

		@Override
		public double getWeight(TypeComponent comp) {
			double weight = super.getWeight(comp);
			IJavaType type = comp instanceof Field ? EclipseUtils.getTypeAndLoadIfNeeded(((Field)comp).typeName(), stack, target, typeCache) : getReturnType(receiver, (Method)comp, ((Method)comp).isConstructor());
			try {
				weight *= getTypeFactor(type);
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
			return weight;
		}
		
		/**
		 * Gets the factor by which to multiple the given type's
		 * weight.  Biases the search toward types that fulfill
		 * the type constraint and away from those that have
		 * already been seen more.
		 * @param type The type.
		 * @return The multiplicative factor to change the type's
		 * weight in a weighted list.
		 * @throws DebugException
		 */
		private double getTypeFactor(IJavaType type) throws DebugException {
			if (typeConstraint.isFulfilledBy(type, subtypeChecker, typeCache, stack, target))
				return 20;
			else if (mightBeHelpfulWithDowncast(type, typeConstraint))
				return 10;
			else {
				Integer numValuesForType = uniqueValuesSeenForType.get(type.getName());
				if (numValuesForType == null)
					return 1;
				else if (numValuesForType == 2 && booleanType.equals(type))
					return .01d;
				else  // Return a smaller number the more values of this type we have seen.
					return 1 / Math.sqrt(numValuesForType + 1);
			}
		}
		
	}
	
	private class ExpressionCombinationWeightedList extends WeightedCombinationWeightedList<Expression> {

		public ExpressionCombinationWeightedList(ArrayList<IWeightedList<Expression>> weightedLists, double[] weights) {
			super(weightedLists, weights);
		}

		public void addWeighted(Expression expr) {
			if (EclipseUtils.isPrimitive(expr.getStaticType()))
				primitives.addWeighted(expr);
			else if (expressionEvaluator.isStatic(expr))
				names.addWeighted(expr);
			else if (expressionEvaluator.getValue(expr, Collections.<Effect>emptySet()).isNull())
				nulls.addWeighted(expr);
			else if (EclipseUtils.isArray(expr.getStaticType()))
				arrs.addWeighted(expr);
			else
				objects.addWeighted(expr);
		}
		
	}
	
	/**
	 * A weighted list that computes probabilities by
	 * how often static methods or fields are accessed
	 * on the given name.
	 */
	private class StaticNameWeightedList extends ExpressionWeightedList {

		public StaticNameWeightedList(ExpressionEvaluator expressionEvaluator, Weights weights) {
			super(expressionEvaluator, weights);
		}
		
		/*
		 * Note that this involves looping over all of a type's
		 * methods and so is expensive.  If I end up needing to
		 * call this a lot, it would be more efficient to store
		 * the information in the model itself.
		 */
		@Override
		public double getWeight(Expression expr) {
			try {
				IJavaType type = expressionEvaluator.getStaticType(expr);
				if (!weights.seenType(type.getName()))
					return weights.getAverageWeight();
				int methodCount = 0;
				for (Method method: getMethods(type, sideEffectHandler))
					if (method.isStatic() && isUsefulMethod(method, expr, false))
						methodCount += weights.getMethodCount(method);
				if (methodCount == 0)
					return 0.05d / (double)weights.getTotal();
				return methodCount / (double)weights.getTotal();
			} catch (DebugException e) {
				throw new RuntimeException(e);
			} catch (JavaModelException e) {
				throw new RuntimeException(e);
			}
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
			else if (index < primitives.size() + objects.size() + arrs.size())
				return arrs.get(index - primitives.size() - objects.size());
			else
				return nulls.get(index - primitives.size() - objects.size() - arrs.size());
		}

		@Override
		public int size() {
			return primitives.size() + objects.size() + arrs.size() + nulls.size();
		}
		
		@Override
		public String toString() {
			return primitives.toString() + "; " + objects.toString() + "; " + arrs.toString() + ";" + nulls.toString();
		}
		
	}
	
	// Equivalence expansion
	
	/**
	 * Generates new expressions by using the given expression.
	 * We do this in two steps: we first generate expressions
	 * equivalent to the given expression by replacing its
	 * subexpressions and then we expand expressions that
	 * contain an expression equivalent to the given one or
	 * newly-generated expressions.
	 * @param expr The new expression.
	 * @param property The specification.
	 * @param typeConstraint The type constraint.
	 * @param evaledExprs The set of expressions we have evaluated.
	 * @param specCache Cache of specification check results.
	 * @param parents The map of containing expressions.
	 * @param monitor The progress monitor.
	 * @return The newly-generated expressions that satisfy
	 * the specification.
	 * @throws DebugException
	 */
	private ArrayList<Expression> expandEquivalences(Expression expr, Property property, TypeConstraint typeConstraint, Set<Expression> evaledExprs, Map<Result, Boolean> specCache, ExpressionParents parents, IProgressMonitor monitor) throws DebugException {
		final ArrayList<Expression> newResults = new ArrayList<Expression>();
		Result result = expressionEvaluator.getResult(expr, Collections.<Effect>emptySet());
		Map<Result, ArrayList<Expression>> newEquivs = new HashMap<Result, ArrayList<Expression>>();
		Queue<Expression> exprsToExpand = new ArrayDeque<Expression>();
		// Preprocessing: add the new expression to the newly-generated equivalences (so we will handle it later) and prepare to expand expressions that contain something equivalent to it.
		exprsToExpand.add(getEquivalentExpressions(expr, Collections.<Effect>emptySet(), null, newEquivs).get(0));  // Only add the first, since by induction anything that contains a different one contains the first as well.
		Utils.addToListMap(newEquivs, result, expr);
		checkSpec(expr, typeConstraint, property, specCache, monitor);  // Pre-compute whether this expression satisfies this spec.  We don't do anything with this yet, but we will at the end of the this method, and this allows us to ensure we never evaluate anything at that point.
		// Expand subexpressions in this expression.
		expandExpression(expr, null, newEquivs, exprsToExpand, evaledExprs, monitor);
		// Process an expression equivalent to the given one and each newly-generated equivalent expression by expanding things that contain it.
		while (true) {
			if (monitor.isCanceled())
				throw new OperationCanceledException();
			Expression cur = exprsToExpand.poll();
			if (cur == null)
				break;
			if (Utils.getNumValues(newEquivs) > 100)  // Heuristically avoid generating too many equivalent expressions.
				break;
			for (Expression parent: parents.getParents(cur))
				expandExpression(parent, cur, newEquivs, exprsToExpand, evaledExprs, monitor);
		}
		// Register the new expressions.
		Map<Result, ArrayList<Expression>> equivMap = equivalences.get(Collections.<Effect>emptySet());
		for (Map.Entry<Result, ArrayList<Expression>> entry: newEquivs.entrySet()) {
			Utils.addAllToListMap(equivMap, entry.getKey(), entry.getValue());  // Add to equivalence map.
			//System.out.println(entry.getValue());
			if (specCache.get(entry.getKey()).booleanValue()) {  // Show to user if they pass the spec.
				newResults.addAll(entry.getValue());
				String resultString = expressionEvaluator.getResultString(equivMap.get(entry.getKey()).get(0));
				for (Expression newExpr: entry.getValue())  // Set the result string for new equivalent expressions.
					expressionEvaluator.setResultString(newExpr, resultString);
			}
			for (Expression newExpr: entry.getValue())
				handleNewExpression(newExpr, evaledExprs, parents);  // Add it to the list of candidates.
		}
    	return newResults;
	}
	
	/**
	 * Generates expressions that are equivalent to the given expression
	 * by replacing its subexpressions with equivalent expressions.
	 * The control parameter controls exactly how we do this; see
	 * getEquivalentExpressions.
	 * @param expr The expression whose subexpressions we should replace
	 * with equivalent expressions to generate new expressions.
	 * @param control The control expression.  See getEquivalentExpressions.
	 * @param newEquivs The set of newly-generated equivalent expressions.
	 * @param exprsToExpand The expressions that we want to expand.
	 * @param evaledExprs The set of expressions we have evaluated.
	 * @param monitor The progress monitor.
	 * @throws DebugException
	 */
	private void expandExpression(Expression expr, Expression control, Map<Result, ArrayList<Expression>> newEquivs, Queue<Expression> exprsToExpand, Set<Expression> evaledExprs, IProgressMonitor monitor) throws DebugException {
		//System.out.println("Expanding " + expr + " with control " + control);
		Result result = expressionEvaluator.getResult(expr, Collections.<Effect>emptySet());
		IJavaType type = getEquivalenceType(expr, result);
		if (expr instanceof NumberLiteral || expr instanceof SimpleName || expr instanceof NullLiteral || expr instanceof ThisExpression) {
			return;
		} else if (expr instanceof PrefixExpression) {
			PrefixExpression prefix = (PrefixExpression)expr;
			for (Expression e: getEquivalentExpressions(prefix.getOperand(), Collections.<Effect>emptySet(), control, newEquivs)) {
				PrefixExpression newPrefix = expressionMaker.makePrefix(e, prefix.getOperator());
				handleNewEquivalentExpr(newEquivs, exprsToExpand, newPrefix, expr, result, evaledExprs);
			}
		} else if (expr instanceof InfixExpression) {
			InfixExpression infix = (InfixExpression)expr;
			for (Expression l: getEquivalentExpressions(infix.getLeftOperand(), Collections.<Effect>emptySet(), control, newEquivs))
				for (Expression r: getEquivalentExpressions(infix.getRightOperand(), expressionEvaluator.getEffects(l, Collections.<Effect>emptySet()), control, newEquivs)) {
					if (monitor.isCanceled())
						throw new OperationCanceledException();
					if (isUsefulInfix(l, infix.getOperator(), r)) {
						InfixExpression newInfix = expressionMaker.makeInfix(l, infix.getOperator(), r, type);
						handleNewEquivalentExpr(newEquivs, exprsToExpand, newInfix, expr, result, evaledExprs);
					}
				}
		} else if (expr instanceof FieldAccess) {
			FieldAccess fieldAccess = (FieldAccess)expr;
			Field field = expressionEvaluator.getField(fieldAccess);
			for (Expression e: getEquivalentExpressions(fieldAccess.getExpression(), Collections.<Effect>emptySet(), control, newEquivs)) {
				FieldAccess newFieldAccess = expressionMaker.makeFieldAccess(e, fieldAccess.getName().getIdentifier(), type, field);
				handleNewEquivalentExpr(newEquivs, exprsToExpand, newFieldAccess, expr, result, evaledExprs);
			}
		} else if (expr instanceof ArrayAccess) {
			ArrayAccess arrayAccess = (ArrayAccess)expr;
			for (Expression a: getEquivalentExpressions(arrayAccess.getArray(), Collections.<Effect>emptySet(), control, newEquivs))
				for (Expression i: getEquivalentExpressions(arrayAccess.getIndex(), expressionEvaluator.getEffects(a, Collections.<Effect>emptySet()), control, newEquivs)) {
					if (monitor.isCanceled())
						throw new OperationCanceledException();
					ArrayAccess newArrayAccess = expressionMaker.makeArrayAccess(type, a, i);
					handleNewEquivalentExpr(newEquivs, exprsToExpand, newArrayAccess, expr, result, evaledExprs);
				}
		} else if (expr instanceof MethodInvocation) {
			MethodInvocation call = (MethodInvocation)expr;
			expandCall(call, call.getExpression(), expressionEvaluator.getMethod(call), call.arguments(), result, type, control, newEquivs, exprsToExpand, evaledExprs);
		} else if (expr instanceof ClassInstanceCreation) {
			ClassInstanceCreation call = (ClassInstanceCreation)expr;
			expandCall(call, expressionMaker.makeTypeLiteral(call.getType()), expressionEvaluator.getMethod(call), call.arguments(), result, type, control, newEquivs, exprsToExpand, evaledExprs);
		} else
			throw new IllegalArgumentException("Unexpected expression " + expr);
	}

	/**
	 * Gets expressions that are equivalent to the given expression
	 * in the given set of effects.  The control expression controls
	 * how we compute the set of equivalent expressions.
	 * @param expr The expressions whose equivalent expressions we want.
	 * @param effects The current effects.
	 * @param control The control expression.  If this is null, we
	 * return equivalent expressions from the existing equivalence
	 * classes.  If it is the same as the given expression and there
	 * are no effects, we use the newly-generated equivalent
	 * expressions.  Otherwise we return the expression itself.
	 * @param newEquivs The newly-generated equivalent expressions.
	 * @return Expressions that are equivalent to the given expression.
	 */
	private List<Expression> getEquivalentExpressions(Expression expr, Set<Effect> effects, Expression control, Map<Result, ArrayList<Expression>> newEquivs) {
		if (control == null) {
			List<Expression> result = expr == null ? null : equivalences.get(effects).get(expressionEvaluator.getResult(expr, effects));
			if (result == null) {
				assert expr == null || expressionEvaluator.isStatic(expr) || expr instanceof TypeLiteral || newEquivs.isEmpty() : expr.toString();
				return Collections.singletonList(expr);
			} else
				return capDepth(result, getDepth(expr));
		} else if (expr == control && effects.isEmpty())
			return capDepth(newEquivs.get(expressionEvaluator.getResult(expr, effects)), getDepth(expr));
		else
			return Collections.singletonList(expr);
	}
	
	/**
	 * Return a new list containing the elements of the given list
	 * whose depth does not exceed the given depth.
	 * @param exprs The expressions to filter.
	 * @param maxDepth The maximum depth of expressions to return.
	 * @return A new list containing the elements of the given list
	 * whose depth is at most the given depth.
	 */
	private List<Expression> capDepth(List<Expression> exprs, int maxDepth) {
		ArrayList<Expression> result = new ArrayList<Expression>();
		for (Expression expr: exprs)
			if (getDepth(expr) <= maxDepth)
				result.add(expr);
		if (result.isEmpty() && !exprs.isEmpty())  // Always return at least one element when possible.
			result.add(exprs.get(0));
		return result;
	}

	/**
	 * Handle a new expression create during equivalence expansion by
	 * setting its results and adding it to the list of new expressions
	 * if it is not the same as the expression from which it was created.
	 * @param newEquivs The newly-generated equivalent expressions.
	 * @param exprsToExpand The expressions that we want to expand.
	 * @param newExpr The new expression.
	 * @param expr The expression from which the new expression was created.
	 * @param result The result of the two expressions.
	 * @param evaledExprs The set of expressions we have evaluated.
	 */
	private void handleNewEquivalentExpr(Map<Result, ArrayList<Expression>> newEquivs, Queue<Expression> exprsToExpand, Expression newExpr, Expression expr, Result result, Set<Expression> evaledExprs) {
		if (evaledExprs.contains(newExpr))  // TODO: I shouldn't need this.  But I currently miss some things (I think because I only expand once), which, if I later generate them from scratch, would cause me to generate duplicates during expansion.
			return;
		if (!expr.equals(newExpr)) {
			assert !newEquivs.containsKey(result) || !newEquivs.get(result).contains(newExpr) : newExpr + " from " + expr;
			if (ProbabilityComputer.getNormalizedProbability(newExpr, expressionEvaluator, weights) < ProbabilityComputer.getNormalizedProbability(expr, expressionEvaluator, weights) / 10) {
				//System.out.println("Skipping " + newExpr);
				return;  // Don't add low probability equivalent expressions.
			}
			expressionEvaluator.copyResults(expr, newExpr);
			Utils.addToListMap(newEquivs, result, newExpr);
			//System.out.println("Adding " + newExpr + " from " + expr);
			exprsToExpand.add(newExpr);
		}
	}

	/**
	 * Finds calls that are equivalent to the given one and adds
	 * them to curEquivalences.
	 * @param call The entire call expression.
	 * @param receiver The receiver part of the call.
	 * @param method The method being called.
	 * @param arguments The arguments.
	 * @param result The result of the call.
	 * @param type The type of the result of the call.
	 * @param control The control expression.  See getEquivalentExpressions.
	 * @param newEquivs The newly-generated equivalent expressions.
	 * @param exprsToExpand The expressions that we want to expand.
	 * @param evaledExprs The set of expressions we have evaluated.
	 * @throws DebugException
	 */
	private void expandCall(Expression call, Expression receiver, Method method, Expression[] arguments, Result result, IJavaType type, Expression control, Map<Result, ArrayList<Expression>> newEquivs, Queue<Expression> exprsToExpand, Set<Expression> evaledExprs) throws DebugException {
		String name = method.name();
		IJavaType receiverType = getReceiverType(receiver, method, Collections.<Effect>emptySet());
		OverloadChecker overloadChecker = new OverloadChecker(receiverType, stack, target, typeCache, subtypeChecker);
		overloadChecker.setMethod(method);
		ArrayList<ArrayList<Expression>> newArguments = new ArrayList<ArrayList<Expression>>(arguments.length);
		ArrayList<TypeConstraint> argConstraints = new ArrayList<TypeConstraint>(arguments.length);
		Set<Effect> curArgEffects = receiver == null || method.isConstructor() ? Collections.<Effect>emptySet() : expressionEvaluator.getEffects(receiver, Collections.<Effect>emptySet());
		for (int i = 0; i < arguments.length; i++) {
			Expression curArg = arguments[i];
			IJavaType argType = EclipseUtils.getTypeAndLoadIfNeeded((String)method.argumentTypeNames().get(i), stack, target, typeCache);
			newArguments.add(getExpansionArgs(getEquivalentExpressions(curArg, curArgEffects, control, newEquivs), i, argType, method, overloadChecker));
			argConstraints.add(new SupertypeBound(argType));
			curArgEffects = expressionEvaluator.getEffects(curArg, curArgEffects);
		}
		MethodConstraint receiverConstraint = new MethodConstraint(name, UnknownConstraint.getUnknownConstraint(), argConstraints, sideEffectHandler.isHandlingSideEffects());
		Set<String> fulfillingType = new HashSet<String>();
		List<Expression> newCalls = new ArrayList<Expression>();
		for (Expression e: getEquivalentExpressions(receiver, Collections.<Effect>emptySet(), control, newEquivs)) {
			if (e != null && !isValidType(e.getStaticType(), receiverConstraint, fulfillingType) && !(e instanceof TypeLiteral))  // I think this will only fail if the value is null, so I could optimize it by confirming that and removing the extra work here.
				e = expressionMaker.makeParenthesized(downcast(e, receiverType));  // FIXME: Use castType like in Deterministic.getEquivalentExpressoins
			makeAllCalls(method, name, e, newCalls, newArguments, new ArrayList<Expression>(newArguments.size()), type, result);
		}
		for (Expression newCall: newCalls)
			handleNewEquivalentExpr(newEquivs, exprsToExpand, newCall, call, result, evaledExprs);
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
	 * @param result The result of all these calls.
	 */
	private void makeAllCalls(Method method, String name, Expression receiver, List<Expression> results, ArrayList<ArrayList<Expression>> possibleActuals, ArrayList<Expression> curActuals, IJavaType returnType, Result result) {
		if (curActuals.size() == possibleActuals.size())
			results.add(makeEquivalenceCall(method, name, returnType, receiver, curActuals, Collections.<Effect>emptySet(), result));
		else {
			int depth = curActuals.size();
			for (Expression e : possibleActuals.get(depth)) {
				curActuals.add(e);
				makeAllCalls(method, name, receiver, results, possibleActuals, curActuals, returnType, result);
				curActuals.remove(depth);
			}
		}
	}
	
	/**
	 * A class that maps expressions to the expressions
	 * that contain it.
	 */
	private static class ExpressionParents {
	
		private final Map<Expression, ArrayList<Expression>> parents;
		private final ArrayList<Expression> emptyList;

		public ExpressionParents() {
			this.parents = new HashMap<Expression, ArrayList<Expression>>();
			this.emptyList = new ArrayList<Expression>(0);
		}
		
		/**
		 * Computes and stores the parents of the given expression.
		 * @param expr The expressions whose parents we should compute.
		 */
		public void addParents(Expression expr) {
			if (expr instanceof NumberLiteral || expr instanceof SimpleName || expr instanceof NullLiteral || expr instanceof ThisExpression)
				return;
			else if (expr instanceof PrefixExpression)
				Utils.addToListMap(parents, ((PrefixExpression)expr).getOperand(), expr);
			else if (expr instanceof InfixExpression) {
				InfixExpression infix = (InfixExpression)expr;
				Utils.addToListMap(parents, infix.getLeftOperand(), infix);
				if (infix.getLeftOperand() != infix.getRightOperand())
					Utils.addToListMap(parents, infix.getRightOperand(), infix);
			} else if (expr instanceof FieldAccess) {
				Utils.addToListMap(parents, ((FieldAccess)expr).getExpression(), expr);
			} else if (expr instanceof ArrayAccess) {
				ArrayAccess access = (ArrayAccess)expr;
				Utils.addToListMap(parents, access.getArray(), access);
				if (access.getArray() != access.getIndex())
					Utils.addToListMap(parents, access.getIndex(), access);
			} else if (expr instanceof MethodInvocation) {
				MethodInvocation call = (MethodInvocation)expr;
				addParentsForCall(call, call.getExpression(), call.arguments());
			} else if (expr instanceof ClassInstanceCreation) {
				ClassInstanceCreation call = (ClassInstanceCreation)expr;
				addParentsForCall(call, call.getExpression(), call.arguments());
			} else
				throw new IllegalArgumentException("Unexpected expression " + expr);
		}
		
		/**
		 * Computes and adds parents for a call.
		 * @param call The call expression.
		 * @param receiver The receiver.
		 * @param args The arguments.
		 */
		private void addParentsForCall(Expression call, Expression receiver, Expression[] args) {
			if (receiver != null)
				Utils.addToListMap(parents, receiver, call);
			for (Expression arg: new HashSet<Expression>(Arrays.asList(args)))
				if (arg != receiver)
					Utils.addToListMap(parents, arg, call);
		}
		
		/**
		 * Gets the expressions that contain the given expression
		 * as a subexpression.
		 * @param expr The expression whose parents to get.
		 * @return The expressions that contain the given
		 * expression as a subexpression.
		 */
		public ArrayList<Expression> getParents(Expression expr) {
			ArrayList<Expression> result = parents.get(expr);
			if (result == null)  // As an optimization, instead of storing lots of empty lists, we store nothing and reuse the constant empty list.
				return emptyList;
			else {
				assert (new HashSet<Expression>(result)).size() == result.size() : expr + " " + result;
				return result;
			}
		}
		
	}

}
