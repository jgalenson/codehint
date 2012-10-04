import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import codehint.CodeHint;

@SuppressWarnings("unused")
public class OSQJTree {

	/*
	 * A JTree displays data in a hierarchical form, where child nodes can be hidden or expanded as necessary.
	 * When run, this code will make a small JTree that will pop up on the screen.
	 */
	private static void configureTree(final JTree jtree) {
		
		/*
		 * This adds a mouse handler that triggers whenever the use clicks on the tree.
		 */
		jtree.addMouseListener(new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				JTree tree = jtree;
				int mouseX = e.getX();
				int mouseY = e.getY();
				
				int clickedRow = -1;
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
        		JFrame frame = MySwingHelper.makeFrame("Swing test");
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

}
