package codehint.ast;

public class PostfixExpression extends Expression {
	
	public static class Operator {
		
		private final String token;
		
		private Operator(String token) {
			this.token = token;
		}
		
		@Override
		public String toString() {
			return token;
		}

		public static final Operator INCREMENT = new Operator("++");
		public static final Operator DECREMENT = new Operator("--");
		
	}
	
	private final Expression operand;
	private final Operator operator;
	
	public PostfixExpression(Expression operand, Operator operator) {
		this.operand = operand;
		this.operator = operator;
	}

	public Expression getOperand() {
		return operand;
	}

	public Operator getOperator() {
		return operator;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren)
			acceptChild(visitor, operand);
	}
	
	@Override
	public String toString() {
		return operand.toString() + operator.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((operand == null) ? 0 : operand.hashCode());
		result = prime * result + ((operator == null) ? 0 : operator.hashCode());
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
		PostfixExpression other = (PostfixExpression) obj;
		if (operand == null) {
			if (other.operand != null)
				return false;
		} else if (!operand.equals(other.operand))
			return false;
		if (operator == null) {
			if (other.operator != null)
				return false;
		} else if (!operator.equals(other.operator))
			return false;
		return true;
	}

}
