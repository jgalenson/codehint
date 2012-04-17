
import java.util.*;

class PbyDC {
    static <T> T choose(T firstChoice, T... otherChoices) {
		//To get in here, one of two things have happened:
		// - we have a concrete value for this set of variables (and all choices must 
		//   evaluate to that)
		// - The user has cancelled out of a demonstration and attempted to step over.  This
		//	 is an error.
    	if (otherChoices == null)  // If the user only passes two args and the second is null, this happens....
    		assert firstChoice == null;
    	else
    		for (T choice : otherChoices)
                assert (firstChoice == null && choice == null) || (firstChoice != null && firstChoice.equals(choice));
        return firstChoice;
    }
	static <T> T choice(T choice) {
		return choice;
	}
}

class test {
	public static int parse_arguments(String s[]) {
		String key = "--arg";
		int i = -1;
		
		i = PbyDC.choose(0, Arrays.binarySearch(s,key), i + 1, i / 2);
		return i;
	}
	
	public static void string_example() {
		//TODO: Why can't we get the hasFlag example working with booleans?
		parse_arguments( new String[] { "--arg", "5"} );
		parse_arguments( new String[] { "--arg", "5", "--foo"} );
		parse_arguments( new String[] { "--foo", "--arg", "5" } );
		parse_arguments( new String[] { "--bar", "--arg", "5", "--foo"} );
		
	}
	
	public static void int_example() {
		int i = 5;
		int j = 7;
		int x = 0;
		//how do we make x be 2?
		System.out.println(x);
	}
	public static class RingBuf {
		int arr[];
		int qs;
		int qe;
		
		public RingBuf() {
			arr = new int[50];
			qs = 0;
			qe = 0;
		}
		
		//works
		boolean empty() {
			boolean e = true;
			return e;
		}
		
		//works
		void clear() {
			
		}
		
		void push(int a) {
			//TODO: Access of array index needs fixing
			//TODO: Include fields of enclosing objects in var set
		}
		int pop() {
			int rval = 0;
			return rval;
		}
	}
	
	public static void ringbuffer_example() {
		RingBuf b = new RingBuf();
		b.push(1);
		b.clear();
		b.push(3);
		b.push(4);
		assert b.pop() == 3;
		assert b.pop() == 4;
		assert b.empty();
	}
	

	public static void main(String[] s) {
		//string_example();
		//int_example();
		
		ringbuffer_example();
	}

	/* Examples to implement:
	 * Argument recognition (array contains)
	 * Ring buffer indexing
	 * Indexing into a sparse matrix?
	 * Substring based on prefix/postfix
	 */
}