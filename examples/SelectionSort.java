import java.util.Arrays;
import codehint.CodeHint;


public class SelectionSort {
	
	private static int[] selectionSort(int[] a) {
		for (int i = 0; i < a.length - 1; i++) {
			int min = i;
			for (int j = i + 1; j < a.length; j++) {
				if (a[j] < a[min]) {
					System.out.println("Here");
				}
			}
			swap(a, i, min);
		}
		return a;
	}
	
	private static int[] selectionSort2(int[] a) {
		for (int i = 0; i < a.length - 1; i++) {
			int min = -1;
			swap(a, i, min);
		}
		return a;
	}
	
	public static void main(String[] args) {
		//int[] a = selectionSort(new int[] { 137, 5, 42, 13 });
		int[] a = selectionSort2(new int[] { 137, 5, 42, 13 });
		assert Arrays.equals(a, new int[] { 5, 13, 42, 137}) : Arrays.toString(a);
	}
	
	private static void swap(int[] a, int i, int j) {
		int tmp = a[i];
		a[i] = a[j];
		a[j] = tmp;
	}
	
	private static int argmin(int[] a, int i) {
		int argMin = i;
		for (i = i + 1; i < a.length; i++)
			if (a[i] < a[argMin])
				argMin = i;
		return argMin;
	}

}
