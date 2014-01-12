package codehint.exprgen.weightedlist;

import java.util.ArrayList;

/**
 * A weighted list built out of other weighted lists
 * where each component weighted list is weighed by
 * the associated weight.
 * @param <T> The type of the elements in the list.
 */
public abstract class WeightedCombinationWeightedList<T> extends CombinationWeightedList<T> {
	
	private final double[] weights;

	/**
	 * Constructs a new combination weighted list.
	 * @param weightedLists The component weighted lists.
	 * @param weights The weights of the component lists.
	 */
	public WeightedCombinationWeightedList(ArrayList<IWeightedList<T>> weightedLists, double[] weights) {
		super(weightedLists);
		assert weightedLists.size() == weights.length;
		this.weights = weights;
	}

	@Override
	public T getWeighted() {
		if (size() == 0)
			return null;
		double rand = random.nextDouble() * getTotalWeight();
		for (int i = 0; i < weightedLists.size(); i++)
			if (rand < weights[i])
				return weightedLists.get(i).getWeighted();
			else
				rand -= weights[i];
		throw new RuntimeException("Cannot get weighted.");
	}

	@Override
	public double getTotalWeight() {
		double totalWeight = 0;
		for (double weight: weights)
			totalWeight += weight;
		return totalWeight;
	}
	
	@Override
	public String toString() {
		int iMax = weightedLists.size() - 1;
		if (iMax == -1)
			return "[]";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; ; i++) {
			sb.append("[").append(weightedLists.get(i).toString()).append("](").append(weights[i]).append(")");
			if (i == iMax)
				return sb.toString();
			sb.append(";  ");
		}
	}

}
