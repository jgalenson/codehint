package codehint.exprgen.weightedlist;

import java.util.List;
import java.util.Random;

/**
 * A weighted list built out of other weighted lists
 * that uses the weights of each component list.
 * @param <T> The type of the elements in the list.
 */
public abstract class CombinationWeightedList<T> implements IWeightedList<T> {
	
	protected static final Random random = new Random();
	
	// I use a List rather than an array here because Java doesn't let you create generic arrays because of covariance, but you can create generic lists.
	protected final List<IWeightedList<T>> weightedLists;

	/**
	 * Constructs a new combination weighted list.
	 * @param weightedLists The component weighted lists.
	 */
	public CombinationWeightedList(List<IWeightedList<T>> weightedLists) {
		this.weightedLists = weightedLists;
	}

	@Override
	public T getWeighted() {
		if (size() == 0)
			return null;
		double rand = random.nextDouble() * getTotalWeight();
		for (IWeightedList<T> weightedList: weightedLists)
			if (rand < weightedList.getTotalWeight())
				return weightedList.getWeighted();
			else
				rand -= weightedList.getTotalWeight();
		throw new RuntimeException("Cannot get weighted.");
	}

	@Override
	public int size() {
		int size = 0;
		for (IWeightedList<T> weightedList: weightedLists)
			size += weightedList.size();
		return size;
	}

	@Override
	public double getTotalWeight() {
		double totalWeight = 0;
		for (IWeightedList<T> weightedList: weightedLists)
			totalWeight += weightedList.getTotalWeight();
		return totalWeight;
	}
	
	@Override
	public String toString() {
		int iMax = weightedLists.size() - 1;
		if (iMax == -1)
			return "[]";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; ; i++) {
			sb.append(weightedLists.get(i).toString());
			if (i == iMax)
				return sb.toString();
			sb.append(";  ");
		}
	}

}
