package codehint.ast;

public class InfixExpression extends Expression {
	
	public static class Operator {
		
		private final String token;
		
		private Operator(String token) {
			this.token = token;
		}
		
		@Override
		public String toString() {
			return token;
		}
		
		public static final Operator TIMES = new Operator("*");
		public static final Operator DIVIDE = new Operator("/");
		public static final Operator REMAINDER = new Operator("%");
		public static final Operator PLUS = new Operator("+");
		public static final Operator MINUS = new Operator("-");
		public static final Operator LEFT_SHIFT = new Operator("<<");
		public static final Operator RIGHT_SHIFT_SIGNED = new Operator(">>");
		public static final Operator RIGHT_SHIFT_UNSIGNED = new Operator(">>>");
		public static final Operator LESS = new Operator("<");
		public static final Operator GREATER = new Operator(">");
		public static final Operator LESS_EQUALS = new Operator("<=");
		public static final Operator GREATER_EQUALS = new Operator(">=");
		public static final Operator EQUALS = new Operator("==");
		public static final Operator NOT_EQUALS = new Operator("!=");
		public static final Operator XOR = new Operator("^");
		public static final Operator OR = new Operator("|");
		public static final Operator AND = new Operator("&");
		public static final Operator CONDITIONAL_OR = new Operator("||");
		public static final Operator CONDITIONAL_AND = new Operator("&&");
		
	}
	
	private final Expression lhs;
	private final Operator operator; 
	private final Expression rhs;
	
	public InfixExpression(Expression lhs, Operator operator, Expression rhs) {
		this.lhs = lhs;
		this.operator = operator;
		this.rhs = rhs;
	}

	public Expression getLeftOperand() {
		return lhs;
	}

	public Operator getOperator() {
		return operator;
	}

	public Expression getRightOperand() {
		return rhs;
	}
	
	@Override
	protected void accept0(ASTVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren) {
			acceptChild(visitor, lhs);
			acceptChild(visitor, rhs);
		}
	}
	
	@Override
	public String toString() {
		return lhs.toString() + " " + operator.toString() + " " + rhs.toString(); 
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((lhs == null) ? 0 : lhs.hashCode());
		result = prime * result + ((operator == null) ? 0 : operator.hashCode());
		result = prime * result + ((rhs == null) ? 0 : rhs.hashCode());
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
		InfixExpression other = (InfixExpression) obj;
		if (lhs == null) {
			if (other.lhs != null)
				return false;
		} else if (!lhs.equals(other.lhs))
			return false;
		if (operator == null) {
			if (other.operator != null)
				return false;
		} else if (!operator.equals(other.operator))
			return false;
		if (rhs == null) {
			if (other.rhs != null)
				return false;
		} else if (!rhs.equals(other.rhs))
			return false;
		return true;
	}

}
