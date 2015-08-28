/*
 * Copyright (c) 2012-2014 Jeffrey Guenther
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software  and associated documentation files (the
 * "Software"), to deal in the Software without restriction,  including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute,  sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT  NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.shirolang.base;

import org.shirolang.dag.DAGraph;
import org.shirolang.dag.DependencyRelation;
import org.shirolang.dag.GraphNode;
import org.shirolang.dag.TopologicalSort;
import org.shirolang.exceptions.OptionNotFoundException;
import org.shirolang.exceptions.PathNotFoundException;
import org.shirolang.interpreter.Consoleable;
import org.shirolang.interpreter.SFuncAction;
import org.shirolang.values.Path;
import org.shirolang.values.PathSegment;
import org.shirolang.values.SIdent;

import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * Defines a graph instance in Shiro.
 *
 * A graph functions as the root of the scope tree for
 * a Shiro program.
 */
public class SGraph implements Scope, Consoleable{
    private DAGraph<SFunc> graph;
    private SFuncAction graphNodeAction = new SFuncAction();

    private String name; // name of the graph
    private Map<String, SNode> nodes; // nodes stored in the graph
    private Map<String, SFunc> ports;
    private Set<SFunc> anonymousPorts;

    public SGraph(){
        this("");
    }

    public SGraph(String name){
        graph = new DAGraph<>();

        this.name = name;
        this.nodes = new HashMap<>();
        this.ports = new HashMap<>();
        this.anonymousPorts = new HashSet<>();
    }

    /**
     * Add the function
     * @param func function to add the graph
     */
    public void addFunction(SFunc func){
        if(func.getSymbolType().isNode()){
            addNode((SNode) func);
        }else{
            addPort(func);
        }
    }

    /**
     * Adds the node to the graph.
     * The node is stored by it's name.
     * Updates the node's scope to the graph
     * @param node node to be added
     */
    public void addNode(SNode node){
        node.setScope(this);
        nodes.put(node.getName(), node);
    }

    /**
     * Gets the node stored in the graph by name
     * @param name
     * @return
     */
    public SNode getNode(String name){
        return nodes.get(name);
    }

    /**
     * Gets the node instances in the graph
     * @return the add of node instances in the graph
     */
    public Set<SNode> getNodes(){
        return new HashSet<>(nodes.values());
    }

    public Set<SNode> getNodesWithOptions(){
        return nodes.values().stream().filter(SNode::hasOptions).collect(toSet());
    }

    /**
     * Adds a port to the graph
     * @param port port to be added to the graph
     */
    public void addPort(SFunc port){
        ports.put(port.getName(), port);
    }


    public void addAnonymousPort(SFunc port){
        anonymousPorts.add(port);
    }

    /**
     * Gets all of the ports in the graph
     * @return the add of named ports in the graph
     */
    public Set<SFunc> getPorts(){
        HashSet<SFunc> allPorts = new HashSet<>();

        // Get all of the top level ports
        allPorts.addAll(ports.values());
        allPorts.addAll(anonymousPorts);
        for(SNode n: getNodes()){
            allPorts.addAll(n.getPorts());
        }

        // Get all the dependencies
        HashSet<SFunc> others = new HashSet<>();
        for(SFunc f: allPorts){
            others.addAll(collectDependencies(f));
        }

        allPorts.addAll(others);
        return allPorts;
    }

    /**
     * Walk the graph to collect the ports depended on
     * @param port port to collect dependences
     * @return set containing all of the functions depended on
     */
    private Set<SFunc> collectDependencies(SFunc port){
        Set<SFunc> deps = new HashSet<>();

        for(SFunc arg : port.getDependencies()){
            deps.add(arg);

            // if the arg is not a named port
            if(arg.getName().isEmpty()){
                deps.addAll(collectDependencies(arg));
            }
        }

        return deps;
    }

    /**
     * Gets the port with the given name
     * @param name name of the port to be found
     * @return the port with the given name
     */
    public SFunc getPort(String name){
        return ports.get(name);
    }

    /**
     * TODO expand this method to handle nested subjuncts specifications
     * Evaluates a graph with the passed configuration
     * @param subjunctTable subjunct table to determine the active options
     * @throws OptionNotFoundException
     */
    public void evaluate(Map<String, String> subjunctTable) throws OptionNotFoundException {
        for(String optionName: subjunctTable.keySet()){
            SNode node = nodes.get(optionName);
            node.setActiveOption(subjunctTable.get(optionName));
        }

        evaluate();
    }

    /**
     * Evaluates the graph
     * TODO add details about which options the method will use
     */
    public void evaluate(){
        graph.removeAllDependencies();
        for(SFunc f: getPorts()){
            if(f.isIdent()){
                SIdent fAsId = (SIdent) f;
                if(!fAsId.isSelector()){
                    if(fAsId.getDependencies().isEmpty()){
                        addDependency(f, null);
                    }else{
                        for (SFunc arg : f.getDependencies()) {
                            addDependency(f, arg);
                        }
                    }
                }
            }else {
                for (SFunc arg : f.getDependencies()) {
                    addDependency(f, arg);
                }

                if (f.getSymbolType().isLiteral()) {
                    addDependency(f, null);
                }

                if (f.getDependencies().isEmpty() && f.getSymbolType().isPort()) {
                    addDependency(f, null);
                }
            }
        }

        TopologicalSort<SFunc> sorter = new TopologicalSort<>(graph);
        List<GraphNode<SFunc>> topologicalOrdering = sorter.getTopologicalOrdering();

//        System.out.println("--------new list -----------");
//        for (GraphNode<SFunc> gn : topologicalOrdering) {
//            SFunc func = gn.getValue();
////            System.out.println(func.toString());
//        }
//        System.out.println("--------end list -----------");

        // loop through all ports to update them.
        for (GraphNode<SFunc> gn : topologicalOrdering) {
            gn.doAction();
        }
    }

    @Override
    public SFunc resolvePath(Path path) throws PathNotFoundException {
        SFunc funcReferenced = null;

        if(path.isAtEnd()){
            // check the nodes
            funcReferenced = nodes.get(path.getSegmentAtHead().getKey().get());

            // check the ports
            if(funcReferenced == null){
                funcReferenced = ports.get(path.getSegmentAtHead().getKey().get());
            }
        }else{ // recursively examine the node
            PathSegment head = path.getSegmentAtHead();

            if(head.isSimple()) {
                if (nodes.containsKey(head.getKey().get())) {
                    SNode nodeReferenced = nodes.get(path.getSegmentAtHead().getKey().get());
                    path.advanceHead();
                    funcReferenced = nodeReferenced.resolvePath(path);
                }
            }
        }

        if(funcReferenced == null){
            throw new PathNotFoundException(path + " was not found.");
        }
        
        return funcReferenced;
    }

    @Override
    public SFunc resolvePath(String path) throws PathNotFoundException {
        return resolvePath(Path.create(path));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getFullName() {
        return name;
    }

    @Override
    public boolean isRoot() {
        return true;
    }

    /**
     * Add a dependency between two SFuncs
     *
     * @param dependency dependency relation to be added to the graph
     */
    private void addDependency(DependencyRelation<SFunc> dependency) {
        addDependency(dependency.getDependent(), dependency.getDependedOn());
    }

    /**
     * *
     * Add a dependency between two SFuncs A - depends on -> B
     *
     * @param a the depended on SFunc reference
     * @param b the dependent SFunc reference
     */
    private void addDependency(SFunc a, SFunc b) {
        if (b == null) {
            GraphNode<SFunc> aNode = graph.getNodeForValue(a, graphNodeAction);
            graph.addDependency(aNode, null);
        } else {
            graph.addDependency(graph.getNodeForValue(a, graphNodeAction),
                    graph.getNodeForValue(b, graphNodeAction));
        }
    }

    /**
     * Outputs the graph in it's console from
     * @return string containing console output for the graph
     */
    public String toConsole(){
        StringBuilder sb = new StringBuilder();

        sb.append("#<Graph::").append(name).append("\n[");

        for(GraphNode<SFunc> n: graph.getNodes()){
            sb.append(formatDependency(n));
        }
        sb.append("]>");
        return sb.toString();
    }


    /**
     * Generates a dependency string
     * @param node node to print
     * @return string containing each of the node's dependencies on a new line.
     */
    private String formatDependency(GraphNode<SFunc> node){
        StringBuilder sb = new StringBuilder();

        SFunc dependent = node.getValue();

        if(!dependent.getDependencies().isEmpty()) {

            for (GraphNode<SFunc> dependedOn : node.getNodesDependedOn()) {
                SFunc func = dependedOn.getValue();

                sb.append(dependent.getFullName())
                    .append(dependent.toConsole())
                    .append(" => ")
                    .append(func.getFullName())
                    .append(func.toConsole())
                .append("\n");
            }
        }else{
            sb.append(dependent.getFullName())
              .append(dependent.toConsole())
              .append("\n");
        }

        return sb.toString();
    }
}
