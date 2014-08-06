import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import codehint.CodeHint;

@SuppressWarnings("unused")
public class Demo {

	/*
	 * A JTree displays data in a hierarchical form, where child nodes can be hidden or expanded as necessary.
	 * When run, this code will make a small JTree that will pop up on the screen.
	 */
	private static void configureTree(final JTree jtree) {
		
		Window window = null;
		// TO WRITE: Get the window in which the JTree is contained.
		configureWindow(window);
		
		
		Dimension size = null;
		// TO WRITE: Get the size of the window.
		useSize(size);
		
		/*
		 * This adds a mouse handler that triggers whenever the user clicks on the tree.
		 */
		jtree.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				JTree tree = jtree;
				int mouseX = e.getX();
				int mouseY = e.getY();
				
				int clickedRow = 0;
				// TO WRITE: Set clickedRow to the index of the clicked element.
				System.out.println(clickedRow);
				
				
				Object x = null;
				// TO WRITE: Set x to somehow represent the clicked element.
				System.out.println(x);
			}
		});
	}

	public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
        		JFrame frame = new JFrame("Swing test");
        		frame.setPreferredSize(new Dimension(100, 200));
        		frame.setJMenuBar(new JMenuBar());
        		frame.setLocation(0, 20);
        		JTree tree = createExampleTree();
        		frame.add(tree);
        		configureTree(tree);
        		showFrame(frame);
            }
        });
	}
	
	private static JTree createExampleTree() {
		DefaultMutableTreeNode top = new DefaultMutableTreeNode("home");
		top.add(new DefaultMutableTreeNode("Alice"));
		top.add(new DefaultMutableTreeNode("Bob"));
		top.add(new DefaultMutableTreeNode("Eve"));
		JTree tree = new JTree(top);
		return tree;
	}
	
	private static void showFrame(JFrame frame) {
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
	}
	
	private static void configureWindow(Window window) {
		if (window != null)
			window.setAlwaysOnTop(true);
	}
	
	// I'm not doing anything real at the moment....
	private static void useSize(Dimension windowSize) {
		if (windowSize != null)
			System.out.println(windowSize);
	}

}
