package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import it.unimi.dsi.fastutil.ints.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullUnmarked;

import java.util.*;

@NullUnmarked
public class LengauerTarjan {
    private Int2ObjectMap<IntSet> successors;
    private Int2ObjectMap<IntSet> predecessors;

    private final ArrayList<IntArrayList> bucket;
    private int[] dfnum;
    private int[] vertex;
    private int[] parent;
    private int[] semi;
    private int[] ancestor;
    private int[] idom;
    private int[] samedom;
    private int[] best;

    private int N = 0;

    public LengauerTarjan() {
        bucket = new ArrayList<>();
    }

    private void resize(int numNodes) {
        int currentLength = dfnum != null ? dfnum.length : 0;
        if(currentLength < numNodes) {
            int rem = numNodes - currentLength;
            for(int i = 0; i < rem; i++) bucket.add(new IntArrayList());
            dfnum = new int[numNodes];
            vertex = new int[numNodes];
            parent = new int[numNodes];
            semi = new int[numNodes];
            ancestor = new int[numNodes];
            idom = new int[numNodes];
            samedom = new int[numNodes];
            best = new int[numNodes];
        }
    }

    public void updateData(@NonNull Int2ObjectMap<IntSet> forward, @NonNull Int2ObjectMap<IntSet> backward) {
        predecessors = backward;
        successors = forward;
        resize(forward.size());
        for(var b : bucket) b.clear();
        Arrays.fill(dfnum, 0);
        Arrays.fill(vertex, -1);
        Arrays.fill(parent, -1);
        Arrays.fill(semi, -1);
        Arrays.fill(ancestor, -1);
        Arrays.fill(idom, -1);
        Arrays.fill(samedom, -1);
        Arrays.fill(best, -1);
        N = 0;
        computeDominatorTree();
    }

    public @NonNull IntList getDominators(int target) {
        var dominators = new IntArrayList();
        if(target >= dfnum.length || target < 0) return IntList.of();
        while(target != -1) {
            dominators.add(target);
            target = idom[target];
        }
        for(int i = 0; i < dominators.size() / 2; i++) {
            var a = dominators.getInt(i);
            dominators.set(i, dominators.getInt(dominators.size() - i - 1));
            dominators.set(dominators.size() - i - 1, a);
        }
        return dominators;
    }

    private void computeDominatorTree() {
        dfs();

        for(int i = N - 1; i > 0; i--) {
            var n = vertex[i];
            var p = parent[n];
            var s = p;

            var pre = predecessors.get(n);
            if(!pre.isEmpty()) {
                for(var it = pre.intIterator(); it.hasNext(); ) {
                    var v = it.nextInt();
                    int sPrime = dfnum[v] <= dfnum[n] ? v : semi[ancestorWithLowestSemi(v)];
                    if(dfnum[sPrime] < dfnum[s]) {
                        s = sPrime;
                    }
                }
            }

            semi[n] = s;
            bucket.get(s).add(n);
            link(p, n);

            for(var it = bucket.get(p).intIterator(); it.hasNext(); ) {
                var v = it.nextInt();
                var y = ancestorWithLowestSemi(v);
                if(semi[y] == semi[v]) {
                    idom[v] = p;
                }else {
                    samedom[v] = y;
                }
                it.remove();
            }
        }

        for(var i = 1; i < N; i++) {
            var n = vertex[i];
            if(samedom[n] >= 0) {
                idom[n] = idom[samedom[n]];
            }
        }
        idom[0] = -1;
    }

    private void dfs() {
        var stack = new IntArrayList();
        stack.push(0);
        stack.push(-1);
        while(!stack.isEmpty()) {
            var p = stack.popInt();
            var n = stack.popInt();
            if(dfnum[n] == 0) {
                dfnum[n] = N;
                vertex[N] = n;
                parent[n] = p;
                best[n] = n;
                N++;

                for(IntIterator it = successors.get(n).intIterator(); it.hasNext(); ) {
                    stack.push(it.nextInt());
                    stack.push(n);
                }
            }
        }
    }

    private int ancestorWithLowestSemi(int v) {
        var a = ancestor[v];
        if(a >= 0 && ancestor[a] >= 0) {
            var b = ancestorWithLowestSemi(a);
            ancestor[v] = ancestor[a];
            if(dfnum[semi[b]] < dfnum[semi[best[v]]]) {
                best[v] = b;
            }
        }
        return best[v];
    }

    private void link(int p, int n) {
        ancestor[n] = p;
        best[n] = n;
    }

}
