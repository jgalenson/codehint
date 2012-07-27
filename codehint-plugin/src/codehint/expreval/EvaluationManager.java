package codehint.expreval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.debug.core.IJavaArray;
import org.eclipse.jdt.debug.core.IJavaArrayType;
import org.eclipse.jdt.debug.core.IJavaClassType;
import org.eclipse.jdt.debug.core.IJavaDebugTarget;
import org.eclipse.jdt.debug.core.IJavaFieldVariable;
import org.eclipse.jdt.debug.core.IJavaPrimitiveValue;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaType;
import org.eclipse.jdt.debug.core.IJavaValue;
import org.eclipse.jdt.debug.eval.IAstEvaluationEngine;
import org.eclipse.jdt.debug.eval.ICompiledExpression;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.debug.eval.IEvaluationResult;
import org.eclipse.swt.widgets.Display;

import codehint.dialogs.InitialSynthesisDialog;
import codehint.exprgen.SubtypeChecker;
import codehint.exprgen.TypeCache;
import codehint.exprgen.TypedExpression;
import codehint.property.PrimitiveValueProperty;
import codehint.property.StateProperty;
import codehint.property.Property;
import codehint.property.TypeProperty;
import codehint.property.ValueProperty;
import codehint.utils.EclipseUtils;

/**
 * Class for evaluating expressions.
 */
public final class EvaluationManager {
	
	private final static int BATCH_SIZE = 100;
	//private final static int MIN_NUM_BATCHES = 4;

	private final static String IMPL_NAME = "codehint.CodeHintImpl";
	private final static String IMPL_QUALIFIER = IMPL_NAME + ".";
	
    private final static ASTParser parser = ASTParser.newParser(AST.JLS4);
	
	public static class EvaluationError extends RuntimeException {
		
		private static final long serialVersionUID = 1L;

		public EvaluationError(String msg) {
			super(msg);
		}
		
	}
	
	private final IJavaStackFrame stack;
	private final IAstEvaluationEngine engine;
	private final IJavaDebugTarget target;
	private final SubtypeChecker subtypeChecker;
	private final TypeCache typeCache;
	private final IJavaClassType implType;
	private final IJavaFieldVariable validField;
	private final IJavaFieldVariable toStringsField;
	private final IJavaFieldVariable valueCountField;
	private final IJavaFieldVariable fullCountField;
	// As an optimization, we cache expressions that crash and do not evaluate them again.
	private final Set<String> crashingExpressions;
	
	private InitialSynthesisDialog synthesisDialog;
	private IProgressMonitor monitor;
	private String validVal;
	private String preVarsString;
	private String propertyPreconditions;
	
	public EvaluationManager(IJavaStackFrame stack, SubtypeChecker subtypeChecker, TypeCache typeCache) {
		this.stack = stack;
		this.engine = EclipseUtils.getASTEvaluationEngine(stack);
		this.target = (IJavaDebugTarget)stack.getDebugTarget();
		this.subtypeChecker = subtypeChecker;
		this.typeCache = typeCache;
		this.implType = (IJavaClassType)EclipseUtils.getTypeAndLoadIfNeeded(IMPL_NAME, stack, target, typeCache);
		try {
			this.validField = implType.getField("valid");
			this.toStringsField = implType.getField("toStrings");
			this.valueCountField = implType.getField("valueCount");
			this.fullCountField = implType.getField("fullCount");
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
		this.crashingExpressions = new HashSet<String>();
	}

	/**
	 * Evaluates the given expressions.
	 * If the desired property is non-null, it returns those that satisfy it;
	 * otherwise, it returns all those whose execution does not crash.
	 * @param exprs The expressions to evaluate
	 * @param property The desired property, or null if there is none.
	 * @param synthesisDialog The synthesis dialog to pass the valid expressions,
	 * or null if we should not pass anything.
	 * @param monitor a progress monitor, or null if progress reporting and cancellation are not desired.
     * @return the results of the evaluations of the given expressions
     * that satisfy the given property, if it is non-null.
	 */
	public ArrayList<EvaluatedExpression> evaluateExpressions(ArrayList<TypedExpression> exprs, Property property, InitialSynthesisDialog synthesisDialog, IProgressMonitor monitor) {
		try {
			this.synthesisDialog = synthesisDialog;
			this.monitor = monitor;
			//batchSize = exprs.size() >= 2 * BATCH_SIZE ? BATCH_SIZE : exprs.size() >= MIN_NUM_BATCHES ? exprs.size() / MIN_NUM_BATCHES : 1;
			validVal = property == null ? "true" : property.getReplacedString("_$curValue", stack);
			preVarsString = getPreVarsString(stack, property);
			PropertyPreconditionFinder pf = new PropertyPreconditionFinder();
    		EclipseUtils.parseExpr(parser, validVal).accept(pf);
    		propertyPreconditions = property instanceof ValueProperty ? "" : pf.getPreconditions();  // TODO: This will presumably fail if the user does their own null check.
			Map<String, ArrayList<TypedExpression>> expressionsByType = getExpressionByType(exprs);
			ArrayList<EvaluatedExpression> evaluatedExprs = new ArrayList<EvaluatedExpression>(exprs.size());
			for (Map.Entry<String, ArrayList<TypedExpression>> expressionsOfType: expressionsByType.entrySet()) {
				String type = EclipseUtils.sanitizeTypename(expressionsOfType.getKey());
				String valuesArrayName = getValuesArrayName(type);
				IJavaFieldVariable valuesField = implType.getField(valuesArrayName);
				evaluatedExprs.addAll(evaluateExpressions(expressionsOfType.getValue(), type, valuesField, 0));
			}
			return evaluatedExprs;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Splits the given expressions by their type, either object or primitives.
	 * We do this so that we can evaluate as many expressions together as possible
	 * by storing them into a shared array.  If we used an array of objects for
	 * everything, we would box primitives, which modifies them.  So we put all objects
	 * in an array of objects and primitives in arrays of their own.
	 */
	private static Map<String, ArrayList<TypedExpression>> getExpressionByType(ArrayList<TypedExpression> exprs) throws DebugException {
		Map<String, ArrayList<TypedExpression>> expressionsByType = new HashMap<String, ArrayList<TypedExpression>>();
		for (TypedExpression expr: exprs) {
			IJavaType type = expr.getType();
			String typeName = type == null ? null : EclipseUtils.isPrimitive(type) ? type.getName() : "Object";
			if (!expressionsByType.containsKey(typeName))
				expressionsByType.put(typeName, new ArrayList<TypedExpression>());
			expressionsByType.get(typeName).add(expr);
		}
		// Nulls have a null type, so put them with the objects.
		if (expressionsByType.containsKey(null)) {
			ArrayList<TypedExpression> nulls = expressionsByType.remove(null);
			if (!expressionsByType.containsKey("Object"))
				expressionsByType.put("Object", new ArrayList<TypedExpression>());
			expressionsByType.get("Object").addAll(nulls);
		}
		return expressionsByType;
	}

	/**
	 * Gets a string whose evaluation will cache the results of
	 * pre-state variables used in the given pdspec.
	 * @param stack The current stack frame.
	 * @param property The current pdspec.
	 * @return A string whose evaluation will cache the results
	 * of pre-state variables used in the given pdspec.
	 * @throws DebugException
	 */
	private static String getPreVarsString(IJavaStackFrame stack, Property property) throws DebugException {
		if (property instanceof StateProperty) {
			StringBuilder expressionsStr = new StringBuilder();
			for (String preVar: ((StateProperty)property).getPreVariables(stack))
				expressionsStr.append(stack.findVariable(preVar).getJavaType().getName()).append(" ").append(StateProperty.getRenamedVar(preVar)).append(" = ").append(preVar).append(";\n");
			return expressionsStr.toString();
		} else
			return "";
	}
	
	/**
	 * Gets the names of the array of values in CodeHintImpl
	 * to use for the given type.
	 * @param type The name of the type (either Object or a primitive).
	 * @return The name of the array to use.
	 */
	private static String getValuesArrayName(String type) {
		if ("Object".equals(type))
			return "objects";
		else
			return type + 's';
	}

	/**
	 * Filters the given evaluated expressions and keeps only
	 * those that satisfy the given property.
	 * @param evaledExprs The expressions to filter
	 * @param property The desired property.
	 * @param synthesisDialog The synthesis dialog to pass the valid expressions,
	 * or null if we should not pass anything.
	 * @param monitor a progress monitor, or null if progress reporting and cancellation are not desired.
     * @return the evaluated expressions that satisfy
     * the given property.
	 */
	public ArrayList<EvaluatedExpression> filterExpressions(ArrayList<EvaluatedExpression> evaledExprs, Property property, InitialSynthesisDialog synthesisDialog, IProgressMonitor monitor) {
		this.synthesisDialog = synthesisDialog;  // We need to set the dialog so it gets used in reportResults.
		if (property instanceof PrimitiveValueProperty) {  // Optimization: manually check primitive value equality.
			// TODO: If I changed value pdspecs to check reference instead of deep equality for objects, I could do this for them as well. 
			IJavaValue demonstratedValue = ((PrimitiveValueProperty)property).getValue();
			String valueStr = demonstratedValue.toString();
			ArrayList<EvaluatedExpression> results = new ArrayList<EvaluatedExpression>();
			for (EvaluatedExpression e: evaledExprs)
				if (e.getResult().toString().equals(valueStr))
					results.add(e);
			reportResults(results);
			monitor.worked(evaledExprs.size());
			return results;
		} else if (property instanceof TypeProperty) {  // Optimization: manually do type checks.
			IJavaType targetType = EclipseUtils.getTypeAndLoadIfNeeded(((TypeProperty)property).getTypeName(), stack, target, typeCache);
			ArrayList<EvaluatedExpression> results = new ArrayList<EvaluatedExpression>();
			try {
				for (EvaluatedExpression e: evaledExprs) {
					IJavaValue value = e.getResult();
					if (!value.isNull() && subtypeChecker.isSubtypeOf(value.getJavaType(), targetType))  // null is not instanceof Object
						results.add(e);
				}
			} catch (DebugException e) {
				throw new RuntimeException(e);
			}
			reportResults(results);
			monitor.worked(evaledExprs.size());
			return results;
		} else {  // Evaluate the pdspecs directly.
			ArrayList<TypedExpression> exprs = new ArrayList<TypedExpression>(evaledExprs.size());
			for (EvaluatedExpression expr : evaledExprs)
				exprs.add(new TypedExpression(expr.getExpression(), expr.getType(), expr.getResult()));
			return evaluateExpressions(exprs, property, synthesisDialog, monitor);
		}
	}
	
	/**
	 * Evaluates the given expressions.
	 * @param exprs The expressions to evaluate
	 * @param type The static type of the desired expression.
	 * @param valuesField The field of the proper type to store the
	 * results of the given expressions.
	 * @param startIndex The index at which to start evaluation.
     * @return the results of the evaluations of the given expressions
     * that do not crash and satisfy the current pdspec.
	 */
	private ArrayList<EvaluatedExpression> evaluateExpressions(ArrayList<TypedExpression> exprs, String type, IJavaFieldVariable valuesField, int startIndex) {
		if (monitor.isCanceled())
			throw new OperationCanceledException();
		boolean hasPropertyPrecondition = propertyPreconditions.length() > 0;
		boolean arePrimitives = !"Object".equals(type);
		StringBuilder expressionsStr = new StringBuilder();
		
		try {
			String valuesArrayName = valuesField.getName();
			
			expressionsStr.append(preVarsString);
			ArrayList<TypedExpression> exprsToEvaluate = new ArrayList<TypedExpression>();
			int i;
	    	for (i = startIndex; i < exprs.size() && exprsToEvaluate.size() < BATCH_SIZE; i++) {
	    		TypedExpression curTypedExpr = exprs.get(i);
	    		Expression curExpr = curTypedExpr.getExpression();
	    		String curExprStr = curExpr.toString();
	    		if (crashingExpressions.contains(curExprStr))
	    			continue;
	    		IJavaValue curValue = curTypedExpr.getValue();
	    		NormalPreconditionFinder pf = new NormalPreconditionFinder();
	    		curExpr.accept(pf);
	    		String preconditions = pf.getPreconditions();
	    		StringBuilder curString = new StringBuilder();
	    		// TODO: If the user has variables with the same names as the ones I introduce, this will crash....
	    		String curRHSStr = curExprStr;
	    		if (arePrimitives && curValue != null)
	    			curRHSStr = curValue.toString();
	    		curString.append("{\n ").append(type).append(" _$curValue = ").append(curRHSStr).append(";\n ");
	    		curString.append(IMPL_QUALIFIER).append("valueCount++;\n ");
	    		if (hasPropertyPrecondition)
	    			curString.append("if (" + propertyPreconditions + ") {\n ");
	    		curString.append("boolean _$curValid = ").append(validVal).append(";\n ");
	    		curString.append(IMPL_QUALIFIER).append("valid[").append(exprsToEvaluate.size()).append("] = _$curValid;\n ");
	    		curString.append(IMPL_QUALIFIER).append(valuesArrayName).append("[").append(exprsToEvaluate.size()).append("] = _$curValue;\n");
	    		if (!arePrimitives)
	    			curString.append(" if (_$curValid)\n  ").append(IMPL_QUALIFIER).append("toStrings[").append(exprsToEvaluate.size()).append("] = ").append(getToStringGetter(curTypedExpr)).append(";\n");
	    		if (hasPropertyPrecondition)
	    			curString.append(" }\n");
	    		curString.append(" ").append(IMPL_QUALIFIER).append("fullCount++;\n");
	    		curString.append("}\n");
	    		if (preconditions.length() > 0 && curValue == null)  // if the value is non-null, I know the execution won't crash.
	    			expressionsStr.append("if (" + preconditions + ") ");
				expressionsStr.append(curString.toString());
				exprsToEvaluate.add(exprs.get(i));
	    	}
	    	String newTypeString = "[" + exprsToEvaluate.size() + "]";
            if (type.contains("[]")) {  // If this is an array type, we must specify our new size as the first array dimension, not the last one.
                int index = type.indexOf("[]");
                newTypeString = type.substring(0, index) + newTypeString + type.substring(index); 
            } else
                newTypeString = type + newTypeString;
            StringBuilder prefix = new StringBuilder();
            prefix.append("{\n").append(IMPL_QUALIFIER).append(valuesArrayName).append(" = new ").append(newTypeString).append(";\n").append(IMPL_QUALIFIER).append("valid = new boolean[").append(exprsToEvaluate.size()).append("];\n");
            if (!arePrimitives)
            	prefix.append(IMPL_QUALIFIER).append("toStrings = new String[").append(exprsToEvaluate.size()).append("];\n");
            else
            	prefix.append(IMPL_QUALIFIER).append("toStrings = null;\n");
        	prefix.append(IMPL_QUALIFIER).append("valueCount = 0;\n");
        	prefix.append(IMPL_QUALIFIER).append("fullCount = 0;\n");
			expressionsStr.insert(0, prefix.toString());
	    	expressionsStr.append("}");
	    	
	    	String finalStr = expressionsStr.toString();
	    	ICompiledExpression compiled = engine.getCompiledExpression(finalStr, stack);
	    	if (compiled.hasErrors())  // The user entered a property that does not compile, so notify them.
	    		throw new EvaluationError("Evaluation error: " + "The following errors were encountered during evaluation.\nDid you enter a valid property?\n\n" + EclipseUtils.getCompileErrors(compiled));
	    	IEvaluationResult result = Evaluator.evaluateExpression(compiled, engine, stack);
    		
	    	boolean hasError = result.getException() != null;
			int fullCount = ((IJavaPrimitiveValue)fullCountField.getValue()).getIntValue();
	    	final ArrayList<EvaluatedExpression> results = fullCount == 0 ? new ArrayList<EvaluatedExpression>() : getResultsFromArray(exprsToEvaluate, valuesField, fullCount);
	    	/*System.out.println("Evaluated " + count + " expressions.");
	    	if (hasError)
	    		System.out.println("Crashed on " + exprs.get(startIndex + count));*/
	    	reportResults(results);
	    	int work = i - startIndex;
	    	if (hasError) {
	    		TypedExpression crashingExpr = exprsToEvaluate.get(fullCount);
				int valueCount = ((IJavaPrimitiveValue)valueCountField.getValue()).getIntValue();
				if (valueCount == fullCount)
					crashingExpressions.add(crashingExpr.getExpression().toString());
	    		work = exprs.indexOf(crashingExpr) - startIndex + 1;
	    	}
	    	monitor.worked(work);
	    	int nextStartIndex = startIndex + work;
	    	if (nextStartIndex < exprs.size())
	    		results.addAll(evaluateExpressions(exprs, type, valuesField, nextStartIndex));
	    	return results;
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Gets a string whose evaluation will get a String
	 * representation of the result of the given expression.
	 * @param expr The expression of whose result we want
	 * a string representation.
	 * @return A String representation of the value of the
	 * given expression.
	 */
	private static String getToStringGetter(TypedExpression expr) {
		try {
			String nullCheck = "_$curValue == null ? \"null\" : ";
			if (expr.getType() instanceof IJavaArrayType)
				return nullCheck + "java.util.Arrays.toString((" + EclipseUtils.sanitizeTypename(expr.getType().getName()) + ")_$curValue)";
			else
				return nullCheck + "_$curValue.toString()";
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Gets the evaluated expressions of those expressions
	 * whose execution did not crash and that produce a
	 * valid result.  See evaluateExpressions for the
	 * string on whose evaluation this is called.
	 * @param exprs The expressions that were evaluated.
	 * @param valuesField The field of the proper type to store the
	 * results of the given expressions.
	 * @param count The number of expressions that were successfully
	 * evaluated.
	 * @return the evaluated expressions of those expressions
	 * whose execution did not crash and that satisfy the current
	 * pdspec.
	 * @throws DebugException
	 */
	private ArrayList<EvaluatedExpression> getResultsFromArray(ArrayList<TypedExpression> exprs, IJavaFieldVariable valuesField, int count) throws DebugException {
		IJavaValue[] values = ((IJavaArray)valuesField.getValue()).getValues();
		IJavaValue[] valids = ((IJavaArray)validField.getValue()).getValues();
		IJavaValue[] toStrings = ((IJavaValue)toStringsField.getValue()).isNull() ? null :((IJavaArray)toStringsField.getValue()).getValues();
		ArrayList<EvaluatedExpression> results = new ArrayList<EvaluatedExpression>();
		for (int i = 0; i < count; i++) {
			if ("true".equals(valids[i].getValueString())) {
				TypedExpression typedExpr = exprs.get(i);
				String resultString = toStrings == null ? EclipseUtils.javaStringOfValue(values[i], stack) : toStrings[i].getValueString();
				if (!values[i].isNull() && "java.lang.String".equals(values[i].getJavaType().getName()))
					resultString = "\"" + resultString + "\"";
				results.add(new EvaluatedExpression(typedExpr.getExpression(), values[i], typedExpr.getType(), resultString));
			}
		}
		return results;
	}

	/**
	 * Records the given expressions in the synthesis dialog,
	 * or does nothing if there is no dialog.
	 * @param results The expressions to record.
	 */
	private void reportResults(final ArrayList<EvaluatedExpression> results) {
		if (synthesisDialog != null && !results.isEmpty()) {
			Display.getDefault().asyncExec(new Runnable(){
				@Override
				public void run() {
		    		synthesisDialog.addExpressions(results);
				}
			});
		}
	}
	
	/**
	 * Nulls out the CodeHintImpl fields used during evaluation
	 * to free up memory.
	 */
	public void resetFields() {
		try {
			EclipseUtils.evaluate(IMPL_QUALIFIER + "reset()", stack);
		} catch (DebugException e) {
			throw new RuntimeException(e);
		}
	}
	
	private static class Evaluator {
		
		private static final Semaphore semaphore = new Semaphore(0);
		private static final IEvaluationResult[] results = new IEvaluationResult[] { null };
		private static final EvaluationListener evaluationListener = new EvaluationListener(results, semaphore);
	
		/**
		 * Evaluates the given expression synchronously.
		 * @param compiled The compiled expression to evaluate.
		 * @param engine The evaluation engine.
		 * @param stack The current stack frame.
		 * @return The result of the evaluation.
		 */
	    public static IEvaluationResult evaluateExpression(ICompiledExpression compiled, IAstEvaluationEngine engine, IJavaStackFrame stack) {
			try {
				engine.evaluateExpression(compiled, stack, evaluationListener, DebugEvent.EVALUATION_IMPLICIT, false);
				semaphore.acquire();
				return results[0];
			} catch (InterruptedException ex) {
				ex.printStackTrace();
				throw new RuntimeException("Thread interrupted.");
			} catch (DebugException ex) {
				ex.printStackTrace();
				throw new RuntimeException("Debug Exception.");
			}
	    }
	    
	    /**
	     * Class to wait for evaluations to finish.
	     */
	    private static class EvaluationListener implements IEvaluationListener {
	
	    	private final IEvaluationResult[] results;
	    	private final Semaphore semaphore;
	    	
	    	public EvaluationListener(IEvaluationResult[] results, Semaphore semaphore) {
	    		this.results = results;
	    		this.semaphore = semaphore;
	    	}
	
			@Override
			public void evaluationComplete(IEvaluationResult result) {
				results[0] = result;
				semaphore.release();  // Up the semaphore to mark that the evaluation finished.
			}
	    	
	    }
	    
	}
    
    /**
     * Finds the preconditions under which we can evaluate the
     * given expression without it crashing, or notes that we
     * can never be sure of this.
     */
    private static class PreconditionFinder extends ASTVisitor {
    	
    	private String preconditions;
    	
    	public PreconditionFinder() {
    		preconditions = "";
    	}
    	
    	protected void add(String str) {
    		if (preconditions.length() == 0)
    			preconditions = str;
    		else
    			preconditions = str + " && " + preconditions;
    	}
    	
    	/**
    	 * Finds the precondition under which the evaluation
    	 * of this expression will not throw an exception
    	 * (if canThrowException is false).
    	 * @return the precondition under which the evaluation
    	 * of this expression will not throw an exception
    	 * (if canThrowException is false).
    	 */
    	public String getPreconditions() {
    		return preconditions;
    	}
    	
    }
    
    /**
     * Finds the preconditions under which we can evaluate the
     * given expression without it crashing, or notes that we
     * can never be sure of this.
     */
    private static class NormalPreconditionFinder extends PreconditionFinder {
    	
    	//private boolean canThrowException;
    	
    	public NormalPreconditionFinder() {
    		//canThrowException = false;
    	}
    	
    	// a[i] -> a != null && i >= 0 && i < a.length 
    	@Override
    	public boolean visit(ArrayAccess node) {
    		String arrStr = node.getArray().toString();
    		String indexStr = node.getIndex().toString();
    		add("(" + arrStr + " != null && " + indexStr + " >= 0 && " + indexStr + " < " + arrStr + ".length)");
    		return true;
    	}
    	
    	// p.q -> p != null
    	@Override
    	public boolean visit(FieldAccess node) {
    		/*if (node.getExpression().getProperty("isStatic") == null)  // Ignore static field accesses like Test.foo.  But this won't work since at refinement time we don't have this information.
    			add(node.getExpression() + " != null");*/
    		if ((IJavaValue)node.getProperty("value") != null)
    			return true;  // The node can be evaluated safely because we already computed its value (so it must have one).
    		//canThrowException = true;
    		IJavaValue exprValue = (IJavaValue)node.getExpression().getProperty("value");
    		if (exprValue != null && exprValue.isNull())
    			add("false");  // We know the expression is null and this will crash.
    		return true;
    	}
    	
    	// x/y -> y != 0
    	// x%y -> y != 0
    	@Override
    	public boolean visit(InfixExpression node) {
    		if (node.getOperator() == InfixExpression.Operator.DIVIDE || node.getOperator() == InfixExpression.Operator.REMAINDER)
    			add(node.getRightOperand() + " != 0");
    		return true;
    	}
    	
    	// x.foo() -> x != null
    	@Override
    	public boolean visit(MethodInvocation node) {
    		/*if (node.getExpression() != null && node.getExpression().getProperty("isStatic") == null && !(node.getExpression() instanceof QualifiedName))  // If the expression is a Qualified name, it is a package name and not a variable.  But this won't work since at refinement time we don't have this information.
    			add(node.getExpression() + " != null");*/
    		if ((IJavaValue)node.getProperty("value") != null)
    			return true;  // The node can be evaluated safely because we already computed its value (so it must have one).
    		//canThrowException = true;
    		IJavaValue exprValue = node.getExpression() == null ? null : (IJavaValue)node.getExpression().getProperty("value");
    		if (exprValue != null && exprValue.isNull())
    			add("false");  // We know the expression is null and this will crash.
    		return true;
    	}
    	
    	// Constructor calls can always throw and we can't (currently) statically evaluate them.
    	@Override
		public boolean visit(ClassInstanceCreation node) {
    		//canThrowException = true;
    		return true;
    	}
    	
    	/**
    	 * Checks whether this expression can throw an exception
    	 * even if the precondition is true.
    	 * @return whether this expression can throw an exception
    	 * even if the precondition is true.
    	 */
    	/*public boolean canThrowException() {
    		return canThrowException;
    	}*/
    	
    }

    // TODO: This doesn't work for lambda properties with types, since they always insert a cast.
    /**
     * Gets a simple precondition for a pdspec.
     * It ensures that we do not evaluate the pdspecs
     * for some pdspecs that we know will crash.
     */
    private static class PropertyPreconditionFinder extends PreconditionFinder {
    	
    	@Override
    	public boolean visit(FieldAccess node) {
    		checkForSyntheticVar(node.getExpression());
    		return true;
    	}
    	
    	@Override
    	public boolean visit(MethodInvocation node) {
    		checkForSyntheticVar(node.getExpression());
    		return true;
    	}
    	
    	/**
    	 * If the given node is a synthetic variable,
    	 * we ensure that it is not null before
    	 * evaluating the pdspec. 
    	 * @param node The node to check.
    	 */
    	private void checkForSyntheticVar(Expression node) {
    		if (node instanceof SimpleName) {
    			String name = ((SimpleName)node).getIdentifier();
    			if (name.startsWith("_$"))
    				add(name + " != null");
    		}
    	}
    	
    }

}