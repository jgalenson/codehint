import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import codehint.CodeHint;

@SuppressWarnings("unused")
public class SwingTest {
	
	private static void createAndShowGUI(JFrame frame) {
		Dimension dim = null;
		//dim = frame.getPreferredSize();
		System.out.println("Task: Find a Dimension that somehow represents the size of the frame.");
		
		int w = (int)dim.getWidth();
		int h = (int)dim.getHeight() / 2;
		Dimension newDim = null;
		//newDim = new java.awt.Dimension(w,h);
		System.out.println("Task: Make a Dimension with the given width and height.");

		JLabel label = new JLabel("Hello, world!");
		MySwingHelper.resizeLabel(label, newDim);
		frame.add(label);
		
		Window window = null;
		//window = frame;
		System.out.println("Task: Find a Window that contains the label.");
		window.setAlwaysOnTop(true);
    }

	/*
	 * A JTree displays data in a hierarchical form, where child nodes can be hidden or expanded as necessary.
	 * When run, this code will make a small little JTree that will pop up on the screen.
	 */
	private static void createJTree(JFrame frame) {
		final JTree jtree = MySwingHelper.createTree();
		
		/*
		 * This adds a mouse handler that triggers whenever the use clicks on the tree.
		 */
		jtree.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				JTree tree = jtree;
				int mouseX = e.getX();
				int mouseY = e.getY();
				int row = 0;
				//row = tree.getRowForLocation(mouseX,mouseY);
				System.out.println("Task: Figure out which row (0-based, containing all elements in the tree, including the top-level one) the user clicked, or -1 if they didn't click a valid element.");
				
				TreePath path = null;
				System.out.println("Task: Find the path the user clicked, or null if they didn't click a valid element.");
				
				System.out.println(row);
				System.out.println(path);
			}
		});
		
		frame.add(jtree);
	}
	
	public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
        		JFrame frame = MySwingHelper.makeFrame("Swing test");
                createAndShowGUI(frame);
        		createJTree(frame);
        		MySwingHelper.showFrame(frame);
            }
        });
	}

}
