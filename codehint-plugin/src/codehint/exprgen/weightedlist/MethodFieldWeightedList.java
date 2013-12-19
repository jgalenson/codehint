package codehint.exprgen.weightedlist;

import com.sun.jdi.Field;
import com.sun.jdi.Method;
import com.sun.jdi.TypeComponent;

import codehint.exprgen.ProbabilityComputer;
import codehint.exprgen.Weights;

public class MethodFieldWeightedList extends WeightedList<TypeComponent> {
	
	private final Weights weights;
	
	public MethodFieldWeightedList(Weights weights) {
		this.weights = weights;
	}

	@Override
	public double getWeight(TypeComponent comp) {
		if (comp instanceof Method)
			return ProbabilityComputer.getMethodProbability((Method)comp, weights);
		else if (comp instanceof Field)
			return ProbabilityComputer.getFieldProbability((Field)comp, weights);
		else
			throw new IllegalArgumentException(String.valueOf(comp));
	}

}
