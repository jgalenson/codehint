package codehint.ast;

public class CastExpression extends Expression {
	
	private final Type type;
	private final Expression expression;
	
	public CastExpression(Type type, Expression expression) {
		super(type.getStaticType());
		this.type = type;
		this.expression = expression;
	}

	public Type getType() {
		return type;
	}

	public Expression getExpression() {
		return expression;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren) {
			acceptChild(visitor, type);
			acceptChild(visitor, expression);
		}
	}
	
	@Override
	public String toString() {
		return "(" + type.toString() + ")" + expression.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((expression == null) ? 0 : expression.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		CastExpression other = (CastExpression) obj;
		if (expression == null) {
			if (other.expression != null)
				return false;
		} else if (!expression.equals(other.expression))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

}
