package codehint.ast;

public class ParenthesizedExpression extends Expression {
	
	private final Expression expression;

	public ParenthesizedExpression(Expression expression) {
		super(expression.getStaticType());
		this.expression = expression;
	}

	public Expression getExpression() {
		return expression;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren)
			acceptChild(visitor, expression);
	}
	
	@Override
	public String toString() {
		return "(" + expression.toString() + ")";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((expression == null) ? 0 : expression.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ParenthesizedExpression other = (ParenthesizedExpression) obj;
		if (expression == null) {
			if (other.expression != null)
				return false;
		} else if (!expression.equals(other.expression))
			return false;
		return true;
	}

}
