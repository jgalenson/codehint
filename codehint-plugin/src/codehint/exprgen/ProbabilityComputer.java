package codehint.exprgen;

import codehint.ast.ASTVisitor;
import codehint.ast.ArrayAccess;
import codehint.ast.ClassInstanceCreation;
import codehint.ast.Expression;
import codehint.ast.FieldAccess;
import codehint.ast.InfixExpression;
import codehint.ast.MethodInvocation;
import codehint.ast.NullLiteral;
import codehint.ast.PrefixExpression;
import codehint.ast.SimpleName;
import codehint.ast.Statement;
import codehint.ast.ThisExpression;

import com.sun.jdi.Field;
import com.sun.jdi.Method;

public class ProbabilityComputer extends ASTVisitor {
	
	private final ExpressionEvaluator expressionEvaluator;
	private final Weights weights;
	private double prob;
	private double probSum;
	private int count;
	
	private ProbabilityComputer(ExpressionEvaluator expressionEvaluator, Weights weights) {
		this.expressionEvaluator = expressionEvaluator;
		this.weights = weights;
		this.prob = 1d;
		this.probSum = 0d;
		this.count = 0;
	}
	
	public static double getProbability(Statement stmt, ExpressionEvaluator expressionEvaluator, Weights weights) {
		return visit(stmt, expressionEvaluator, weights).prob;
	}
	
	public static double getNormalizedProbability(Expression expr, ExpressionEvaluator expressionEvaluator, Weights weights) {
		ProbabilityComputer visitor = visit(expr, expressionEvaluator, weights);
		if (visitor.probSum == 0) {  // Avoid zero and infinite probabilities.
			visitor.count = 1;
			visitor.probSum = weights.getAverageWeight();
		}
		double ret = (visitor.probSum / visitor.count) / Math.pow(2, visitor.count);
		return ret;
	}
	
	private static ProbabilityComputer visit(Statement stmt, ExpressionEvaluator expressionEvaluator, Weights weights) {
		ProbabilityComputer visitor = new ProbabilityComputer(expressionEvaluator, weights);
		stmt.accept(visitor);
		return visitor;
	}

	@Override
	public boolean visit(MethodInvocation node) {
		return visitMethod(node, ExprStats.CALL_PROB);
	}

	@Override
	public boolean visit(ClassInstanceCreation node) {
		return visitMethod(node, ExprStats.NEW_PROB);
	}
	
	private boolean visitMethod(Expression node, double typeProb) {
		double methodProb = getMethodProbability(expressionEvaluator.getMethod(node), weights) * typeProb;
		prob *= methodProb;
		probSum += methodProb;
		count++;
		return true;
	}

	public static double getMethodProbability(Method method, Weights weights) {
		if (method == null)  // During refinement, we might not have an id.
			return weights.getAverageWeight();
		else
			return weights.getWeight(method.declaringType().name(), Weights.getMethodKey(method));
	}
	
	@Override
	public boolean visit(FieldAccess node) {
		Field field = expressionEvaluator.getField(node);
		return visitField(field, field == null ? ExprStats.ARR_LEN_PROB : ExprStats.FIELD_PROB);  // field is null for array length
	}
	
	@Override
	public boolean visit(SimpleName node) {
		Field field = expressionEvaluator.getField(node);
		if (field != null && field.isPublic())  // Don't count unqualified non-public field accesses.
			return visitField(field, ExprStats.FIELD_PROB);
		else
			count++;
		return true;
	}
	
	private boolean visitField(Field field, double typeProb) {
		double fieldProb = getFieldProbability(field, weights) * typeProb;
		prob *= fieldProb;
		probSum += fieldProb;
		count++;
		return true;
	}
	
	public static double getFieldProbability(Field field, Weights weights) {
		if (field == null)
			return weights.getAverageWeight();
		else
			return weights.getWeight(field.declaringType().name(), field.name()) * weights.getFieldWeight();  // We bias fields by how often they occur compared to methods: P(field) / P(method).
	}
	
	@Override
	public boolean visit(InfixExpression node) {
		return visitAverage(ExprStats.INFIX_PROB * ExprStats.getInfixOperatorProbability(node.getOperator().toString()));
	}
	
	@Override
	public boolean visit(PrefixExpression node) {
		return visitAverage(ExprStats.PREFIX_PROB * ExprStats.getPrefixOperatorProbability(node.getOperator().toString()));
	}
	
	@Override
	public boolean visit(ArrayAccess node) {
		return visitAverage(ExprStats.ARR_ACCESS_PROB);
	}
	
	private boolean visitAverage(double typeProb) {
		double weight = weights.getAverageWeight() * typeProb;
		prob *= weight;
		probSum += weight;
		count++;
		return true;
	}
	
	@Override
	public boolean visit(NullLiteral node) {
		return visitAverage(ExprStats.NULL_PROB);
	}
	
	@Override
	public boolean visit(ThisExpression node) {
		return visitAverage(ExprStats.THIS_PROB);
	}
	
}
