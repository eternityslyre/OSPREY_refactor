package experiments;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import edu.duke.cs.osprey.sparse.ResidueInteractionGraph;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.BorderLayout;

public class SparseGraphVisualizer extends JPanel {
	
	
    /**
     * Keeps track of the last point to draw the next line from.
     */
    private Point lastPoint;

    /**
     * Constructs a panel, registering listeners for the mouse.
     */
    public SparseGraphVisualizer(ResidueInteractionGraph interactionGraph) {
		System.setProperty("gs.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
		Graph graph = new SingleGraph("InteractionGraph");
		graph.setAttribute("layout.force", 0);
		
		for(int residueIndex : interactionGraph.getVertices())
		{
			graph.addNode(residueIndex+"");
			graph.getNode(""+residueIndex).addAttribute("ui.label", ""+residueIndex);
		}
		
		for(int residue1 : interactionGraph.getVertices())
		{		
			for(int residue2 : interactionGraph.getVertices())
			{
				if(residue1 > residue2)
					if(interactionGraph.connected(residue1, residue2))
					{
						String edgeName = "("+residue1+"-"+residue2+")";
						graph.addEdge(edgeName, ""+residue1, ""+residue2);
						double width = interactionGraph.getEnergyBound(residue1, residue2)*3;
						System.out.println("Edge "+edgeName+" width: "+width);
						graph.getEdge(edgeName).setAttribute("ui.style", "stroke-width: "+width+";");
	
						graph.getEdge(edgeName).setAttribute("layout.weight", ""+(1/width*width*width*width));
					}
					else 
					{
						/*
						String edgeName = "("+residue1+"-"+residue2+")";
						graph.addEdge(edgeName, residue1, residue2);
						graph.getEdge(edgeName).setAttribute("ui.hide", "true");
	
						graph.getEdge(edgeName).setAttribute("layout.weight", ""+1000000000);
						*/
					}
			}
		}

		 String styleSheet=
				 "node {"+
						   " fill-color: grey;"+
						   " size: 30px;"+
						   " stroke-mode: plain;"+
						   " stroke-color: black;"+
						   " stroke-width: 1px;"+
						   "}"+
				   "edge {"+
				   " fill-color: black;"+
				   " size: 1px;"+
				   " stroke-mode: plain;"+
				   " stroke-color: black;"+
				   " stroke-width: 1px;"+
				   "}";				   
	        graph.addAttribute("ui.stylesheet", styleSheet);


		graph.display();
    }

}
