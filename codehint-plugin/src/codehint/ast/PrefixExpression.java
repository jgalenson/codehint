package codehint.ast;

public class PrefixExpression extends Expression {
	
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
		public static final Operator PLUS = new Operator("+");
		public static final Operator MINUS = new Operator("-");
		public static final Operator COMPLEMENT = new Operator("~");
		public static final Operator NOT = new Operator("!");
		
	}

	private final Operator operator;
	private final Expression operand;
	
	public PrefixExpression(Operator operator, Expression operand) {
		super(operand.getStaticType());
		this.operator = operator;
		this.operand = operand;
	}

	public Operator getOperator() {
		return operator;
	}

	public Expression getOperand() {
		return operand;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren)
			acceptChild(visitor, operand);
	}
	
	@Override
	public String toString() {
		return operator.toString() + operand.toString();
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
		PrefixExpression other = (PrefixExpression) obj;
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
