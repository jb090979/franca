package org.franca.core.ui.addons.contractviewer.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef4.zest.core.widgets.Graph;
import org.eclipse.gef4.zest.core.widgets.GraphConnection;
import org.eclipse.gef4.zest.core.widgets.GraphNode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.franca.core.contracts.ContractDotGenerator;
import org.franca.core.franca.FInterface;
import org.franca.core.franca.FModel;
import org.franca.core.franca.FState;
import org.franca.core.franca.FTransition;
import org.franca.core.ui.addons.contractviewer.graph.CustomGraphNode;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * The intermediate graph model acts as a bridge between the franca model and the graph model used by Zest. 
 * It can be used to create the {@link GraphNode}s and {@link GraphConnection}s for the Zesz based viewer. 
 * Additionally the main purpose of the class is to provide a proper equals implementation between the 
 * {@link IntermediateFrancaGraphModel} instances to be able to compute when should the viewer really be updated.
 * 
 * @author Tamas Szabo (itemis AG)
 *
 */
public class IntermediateFrancaGraphModel {

	private List<String> states;
	private Map<String, GraphNode> nodeMap;
	private Set<GraphConnection> connections;
	private Multimap<String, IntermediateFrancaGraphConnection> connectionMap;
	private ContractDotGenerator generator;
	private FState initialState;
	private CustomGraphNode initialNode;
	
	public static IntermediateFrancaGraphModel createFrom(FModel model) {
		return new IntermediateFrancaGraphModel(model);
	}
	
	private CustomGraphNode createInitNode(Graph graph) {
		CustomGraphNode node = new CustomGraphNode(graph, SWT.NONE, null);
		//make the bound a rectangle
		int r = Math.min(node.getFigure().getBounds().width, node.getFigure().getBounds().height);
		node.getFigure().setBounds(new Rectangle(0, 0, r, r));
		return node;
	}

	private IntermediateFrancaGraphModel(FModel model) {
		states = new ArrayList<String>();
		connectionMap = ArrayListMultimap.create();
		generator = new ContractDotGenerator();
		buildFromModel(model);
	}
	
	private void buildFromModel(FModel model) {
		for (FInterface _interface : model.getInterfaces()) {
			if (_interface.getContract() != null && _interface.getContract().getStateGraph() != null) {
				initialState = _interface.getContract().getStateGraph().getInitial();
				//first collect all states
				for (FState state : _interface.getContract().getStateGraph().getStates()) {
					states.add(state.getName());
				}
				for (FState state : _interface.getContract().getStateGraph().getStates()) {
					for (FTransition transition : state.getTransitions()) {
						//this check will remove all invalid connections from the intermediate model
						if (transition.getTo() != null && states.contains(transition.getTo().getName())) {
							IntermediateFrancaGraphConnection connection = 
									new IntermediateFrancaGraphConnection(state.getName(), transition.getTo().getName(), generator.genLabel(transition));
							connectionMap.put(state.getName(), connection);
						}
					}
				}
			}
		}
	}
	
	public Collection<GraphNode> getGraphNodes(Graph graph) {
		if (nodeMap == null) {
			nodeMap = new HashMap<String, GraphNode>();
			for (String name : states) {
				CustomGraphNode customGraphNode = new CustomGraphNode(graph, SWT.NONE, name);
				customGraphNode.setData(name);
				nodeMap.put(name, customGraphNode);
			}
			
			if (states.size() > 0) {
				initialNode = createInitNode(graph);
				nodeMap.put(initialNode.toString(), initialNode);
			}
		}
		return Collections.unmodifiableCollection(nodeMap.values());
	}
	
	public Collection<GraphConnection> getGraphConnections(Graph graph) {
		getGraphNodes(graph);
		if (connections == null) {
			connections = new HashSet<GraphConnection>();
			for (String name : states) {
				for (IntermediateFrancaGraphConnection conn : connectionMap.get(name)) {
					GraphConnection graphConnection = new GraphConnection(graph, SWT.NONE, nodeMap.get(conn.source), nodeMap.get(conn.target));
					graphConnection.setData(conn.label);
					graphConnection.setText("");
					graphConnection.setLineColor(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
					
					String opposite = getOppositeLabel(conn.source, conn.target);
					String labelText = conn.label + ((opposite == null) ? "" : "\n(opposite: " + opposite + ")");
					graphConnection.setTooltip(new Label(labelText));
					connections.add(graphConnection);
				}
			}
			
			if (states.size() > 0) {
				GraphConnection graphConnection = new GraphConnection(graph, SWT.NONE, initialNode, nodeMap.get(initialState.getName()));
				graphConnection.setData("init");
				graphConnection.setText("init");
				graphConnection.setLineColor(Display.getDefault().getSystemColor(SWT.COLOR_BLACK));
				graphConnection.setTooltip(new Label("init"));
				connections.add(graphConnection);
			}
		}
		return Collections.unmodifiableCollection(connections);
	}
	
	private String getOppositeLabel(String source, String target) {
		for (IntermediateFrancaGraphConnection conn : connectionMap.get(target)) {
			if (conn.target.equals(source)) {
				return conn.label;
			}
		}
		return null;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		else if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		else {
			IntermediateFrancaGraphModel model = (IntermediateFrancaGraphModel) obj;
			
			if (model.states.size() != this.states.size()) {
				return false;
			}
			for (String name : this.states) {
				if (!model.states.contains(name)) {
					return false;
				}
				if (model.connectionMap.get(name).size() != this.connectionMap.get(name).size()) {
					return false;
				}
				for (IntermediateFrancaGraphConnection conn : model.connectionMap.get(name)) {
					if (!this.connectionMap.get(name).contains(conn)) {
						return false;
					}
				}
			}
			
			return true;
		}
	}
	
	@Override
    public int hashCode() {
        int hash = 1;
        for (String name : states) {
        	hash = hash * 17 + name.hashCode();
        }
        for (IntermediateFrancaGraphConnection conn : connectionMap.values()) {
        	hash = hash * 31 + conn.hashCode();        	
        }
        return hash;
    }
}
