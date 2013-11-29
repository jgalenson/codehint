package codehint.exprgen;

import java.util.Comparator;

import codehint.ast.ASTVisitor;
import codehint.ast.ArrayAccess;
import codehint.ast.ClassInstanceCreation;
import codehint.ast.Expression;
import codehint.ast.FieldAccess;
import codehint.ast.InfixExpression;
import codehint.ast.MethodInvocation;
import codehint.ast.PrefixExpression;
import codehint.ast.SimpleName;

import com.sun.jdi.Field;
import com.sun.jdi.Method;

/**
 * Sorts expressions based on how often their methods and fields
 * appear in our database.
 */
public class ExpressionSorter implements Comparator<Expression> {
	
	private final ExpressionMaker expressionMaker;
	private final Weights weights;
	
	public ExpressionSorter(ExpressionMaker expressionMaker, Weights weights) {
		this.expressionMaker = expressionMaker;
		this.weights = weights;
	}

	@Override
	public int compare(Expression e1, Expression e2) {
		double p1 = weigh(e1);
		double p2 = weigh(e2);
		//System.out.println(e1 + " " + p1);
		//System.out.println(e2 + " " + p2);
		if (p1 < p2)
			return 1;
		else if (p1 > p2)
			return -1;
		else
			return e1.toString().length() - e2.toString().length();
	}
	
	private double weigh(Expression expr) {
		CallVisitor visitor = new CallVisitor();
		expr.accept(visitor);
		return visitor.getProbability();
	}
	
	private class CallVisitor extends ASTVisitor {
		
		private double prob;
		
		public CallVisitor() {
			prob = 1;
		}

		@Override
		public boolean visit(MethodInvocation node) {
			return visitMethod(node);
		}

		@Override
		public boolean visit(ClassInstanceCreation node) {
			return visitMethod(node);
		}
		
		private boolean visitMethod(Expression node) {
			Method method = expressionMaker.getMethod(node);
			if (method == null)  // During refinement, we might not have an id.
				prob *= weights.getAverageWeight();
			else
				prob *= weights.getWeight(method.declaringType().name(), Weights.getMethodKey(method));
			return true;
		}
		
		@Override
		public boolean visit(FieldAccess node) {
			Field field = expressionMaker.getField(node);
			if (field == null)  // Array length
				prob *= weights.getAverageWeight();
			else
				visitField(field);
			return true;
		}
		
		@Override
		public boolean visit(SimpleName node) {
			Field field = expressionMaker.getField(node);
			if (field != null && field.isPublic())  // Don't count unqualified non-public field accesses.
				visitField(field);
			return true;
		}
		
		private void visitField(Field field) {
			if (field != null) {
				prob *= weights.getWeight(field.declaringType().name(), field.name());
				// We bias fields by how often they occur compared to methods: P(field) / P(method).
				prob *= weights.getFieldWeight();  // TODO: Use the actually-computed value once I update the weights file to add it.
			}
		}
		
		@Override
		public boolean visit(InfixExpression node) {
			prob *= weights.getAverageWeight();
			return true;
		}
		
		@Override
		public boolean visit(PrefixExpression node) {
			prob *= weights.getAverageWeight();
			return true;
		}
		
		@Override
		public boolean visit(ArrayAccess node) {
			prob *= weights.getAverageWeight();
			return true;
		}
		
		public double getProbability() {
			return prob;
		}
		
	}

}
