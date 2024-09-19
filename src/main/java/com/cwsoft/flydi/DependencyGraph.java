package com.cwsoft.flydi;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DependencyGraph<T> {

    private final Map<T, Set<T>> usesMap;
    private final Map<T, Set<T>> usedByMap;
    private final Set<T> allNodes;

    public DependencyGraph() {
        usesMap = new HashMap<>();
        usedByMap = new HashMap<>();
        allNodes = new HashSet<>();
    }

    public void addDependency(T uses, T usedBy) {
        allNodes.add(uses);
        allNodes.add(usedBy);

        Set<T> allUses = usesMap.get(uses);
        if (allUses == null) {
            allUses = new HashSet<>();
            usesMap.put(uses, allUses);
        }
        allUses.add(usedBy);

        Set<T> allUsedBy = usedByMap.get(usedBy);
        if (allUsedBy == null) {
            allUsedBy = new HashSet<>();
            usedByMap.put(usedBy, allUsedBy);
        }
        allUsedBy.add(uses);
    }

    public void add(T node) {
        allNodes.add(node);
    }


    @NoArgsConstructor
    private static class MutableInt {
        int value;

        public MutableInt(int value) {
            this.value = value;
        }

        public void increment() {
            ++value;
        }

        public void decrement() {
            --value;
        }

        public int get() {
            return value;
        }
    }


    // prints a Topological Sort of the complete graph
    public List<T> topologicalSort() throws CircularDependencyException {

        // set up outDegrees to show out degree for each node - out degree is count of dependencies on other nodes
        Map<T, MutableInt> outDegrees = new HashMap<>();

        allNodes.forEach(node -> {
            Set<T> allUses = usesMap.get(node);

            if (allUses == null) {
                outDegrees.put(node, new MutableInt());
            } else {
                outDegrees.put(node, new MutableInt(allUses.size()));
            }
        });


        // Create a queue and enqueue all nodes with out degree 0 (i.e. have no dependencies)
        Queue<T> q = new LinkedList<>();
        outDegrees.entrySet().forEach(outDegree -> {
            if (outDegree.getValue().value == 0) {
                q.add(outDegree.getKey());
            }
        });

        int visitedCount = 0;

        // list will hold the nodes sorted in topological order
        List<T> topOrder = new ArrayList<>();
        while (!q.isEmpty()) {

            // get next node (that it now down to out degree 0) from queue and add to sorted list
            T node = q.poll();
            topOrder.add(node);

            // Iterate through all this nodes "used by" nodes and decrease their out-degree, add node to queue if out-degree is now 0
            Set<T> usingNodes = usedByMap.get(node);

            if (usingNodes != null) {
                usingNodes.forEach(usingNode -> {
                    MutableInt outDegree = outDegrees.get(usingNode);
                    outDegree.decrement();

                    if (outDegree.get() == 0) {
                        q.add(usingNode);
                    }
                });
            }

            visitedCount++;
        }

        // check if there was a cycle
        if (visitedCount != outDegrees.size()) {
            String circularCandidates =
                    outDegrees.entrySet().stream()
                            .filter(entry -> entry.getValue().value > 0)
                            .map(entry -> entry.getKey().toString())
                    .collect( Collectors.joining( "," ) );
            throw new CircularDependencyException ("Cycle detected in dependency evaluation, check for circular dependencies in the following: " + circularCandidates);
        }

        return topOrder;
    }


    public static void main(String args[]) throws CircularDependencyException {
        // Create a graph given in the above diagram
        DependencyGraph<String> g = new DependencyGraph();
        g.addDependency("D", "A");
        g.addDependency("C", "A");
        g.addDependency("C", "B");
        g.addDependency("E", "B");
        g.addDependency("F", "D");
        g.addDependency("E", "F");
        g.addDependency("C", "F");
        g.addDependency("H", "G");
        g.addDependency("I", "H");
        g.add("X");
        g.add("Y");
        g.add("Z");
        System.out.println("Following is a Topological Sort");
        g.topologicalSort().forEach(System.out::println);

    }


    public static class CircularDependencyException extends Exception {
        public CircularDependencyException(String s) {
            super(s);
        }
    }
}