package codehint.exprgen;

import java.util.HashMap;
import java.util.Map;

public final class ExprStats {
	
	public static final double CALL_PROB = .39618;
	public static final double INFIX_PROB = .13922;
	public static final double FIELD_PROB = .09765;
	public static final double NULL_PROB = .0617;
	public static final double NEW_PROB = .05082;
	public static final double THIS_PROB = .02938;
	public static final double PREFIX_PROB = .01936;
	public static final double ARR_ACCESS_PROB = .01534;
	public static final double ARR_LEN_PROB = .00809;
	
	public static final double PRIM_OP_PROB = INFIX_PROB + PREFIX_PROB;
	public static final double OBJECT_OP_PROB = CALL_PROB + FIELD_PROB + NEW_PROB;
	public static final double ARR_OP_PROB = ARR_ACCESS_PROB + ARR_LEN_PROB;
	
	public static final double STATIC_OP_PROB = 0.24295;
	public static final double INSTANCE_OP_PROB = 0.75705;
	
	public static final double INFIX_PLUS_PROB = 0.19478;
	public static final double INFIX_MINUS_PROB = 0.04554;
	public static final double INFIX_TIMES_PROB = 0.0102;
	public static final double INFIX_DIV_PROB = 0.00613;
	private static final double INFIX_EQ_PROB = 0.22798;
	private static final double INFIX_NEQ_PROB = 0.20312;
	private static final double INFIX_AND_PROB = 0.10597;
	private static final double INFIX_LT_PROB = 0.05801;
	private static final double INFIX_OR_PROB = 0.05708;
	private static final double INFIX_GT_PROB = 0.03669;
	private static final double INFIX_GE_PROB = 0.01569;
	private static final double INFIX_BITAND_PROB = 0.01332;
	private static final double INFIX_LE_PROB = 0.00886;
	private static final double INFIX_BITOR_PROB = 0.00869;
	private static final double INFIX_LS_PROB = 0.00373;
	private static final double INFIX_RS_PROB = 0.00151;
	private static final double INFIX_MOD_PROB = 0.00124;
	private static final double INFIX_BITXOR_PROB = 0.0007;
	private static final double INFIX_RSU_PROB = 0.00067;

	private static final double PREFIX_NOT_PROB = 0.72834;
	private static final double PREFIX_MINUS_PROB = 0.22017;
	private static final double PREFIX_INC_PROB = 0.03017;
	private static final double PREFIX_COMP_PROB = 0.01068;
	private static final double PREFIX_DEC_PROB = 0.00956;
	private static final double PREFIX_PLUS_PROB = 0.00106;
	
	private static final Map<String, Double> infixProbs = initInfixProbs();
	private static final Map<String, Double> prefixProbs = initPrefixProbs();
	
	public static double getInfixOperatorProbability(String op) {
		return infixProbs.get(op);
	}
	
	public static double getPrefixOperatorProbability(String op) {
		return prefixProbs.get(op);
	}

	private static Map<String, Double> initInfixProbs() {
		Map<String, Double> infixProbs = new HashMap<String, Double>();
		infixProbs.put("+", INFIX_PLUS_PROB);
		infixProbs.put("-", INFIX_MINUS_PROB);
		infixProbs.put("*", INFIX_TIMES_PROB);
		infixProbs.put("/", INFIX_DIV_PROB);
		infixProbs.put("==", INFIX_EQ_PROB);
		infixProbs.put("!=", INFIX_NEQ_PROB);
		infixProbs.put("&&", INFIX_AND_PROB);
		infixProbs.put("<", INFIX_LT_PROB);
		infixProbs.put("||", INFIX_OR_PROB);
		infixProbs.put(">", INFIX_GT_PROB);
		infixProbs.put(">=", INFIX_GE_PROB);
		infixProbs.put("&", INFIX_BITAND_PROB);
		infixProbs.put("<=", INFIX_LE_PROB);
		infixProbs.put("|", INFIX_BITOR_PROB);
		infixProbs.put("<<", INFIX_LS_PROB);
		infixProbs.put(">>", INFIX_RS_PROB);
		infixProbs.put("%", INFIX_MOD_PROB);
		infixProbs.put("^", INFIX_BITXOR_PROB);
		infixProbs.put(">>>", INFIX_RSU_PROB);
		return infixProbs;
	}

	private static Map<String, Double> initPrefixProbs() {
		Map<String, Double> prefixProbs = new HashMap<String, Double>();
		prefixProbs.put("!", PREFIX_NOT_PROB);
		prefixProbs.put("-", PREFIX_MINUS_PROB);
		prefixProbs.put("++", PREFIX_INC_PROB);
		prefixProbs.put("~", PREFIX_COMP_PROB);
		prefixProbs.put("--", PREFIX_DEC_PROB);
		prefixProbs.put("+", PREFIX_PLUS_PROB);
		return prefixProbs;
	}

}
