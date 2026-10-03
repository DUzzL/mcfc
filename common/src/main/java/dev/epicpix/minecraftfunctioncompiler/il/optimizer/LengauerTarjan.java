package dev.epicpix.minecraftfunctioncompiler.il.optimizer;

import java.util.Arrays;

/**
 * Dominator tree over a CFG in CSR form (node 0 is the entry), computed with the
 * Lengauer-Tarjan algorithm. After {@link #compute}, "does a dominate b" is answered in O(1)
 * from pre/post numbers of a DFS over the dominator tree.
 *
 * <p>Instances are reusable (the arrays grow on demand) but not thread-safe.
 */
public class LengauerTarjan {
    private int[] dfnum = new int[0];
    private int[] vertex = new int[0];
    private int[] parent = new int[0];
    private int[] semi = new int[0];
    private int[] ancestor = new int[0];
    private int[] idom = new int[0];
    private int[] samedom = new int[0];
    private int[] best = new int[0];
    private int[] bucketHead = new int[0];
    private int[] bucketNext = new int[0];
    private int[] scratch = new int[0];
    private int[] scratch2 = new int[0];
    private int[] domPre = new int[0];
    private int[] domPost = new int[0];

    private int N;

    public LengauerTarjan() {}

    private void ensureCapacity(int n) {
        if(dfnum.length >= n) return;
        int cap = Math.max(n, dfnum.length * 2);
        dfnum = new int[cap];
        vertex = new int[cap];
        parent = new int[cap];
        semi = new int[cap];
        ancestor = new int[cap];
        idom = new int[cap];
        samedom = new int[cap];
        best = new int[cap];
        bucketHead = new int[cap];
        bucketNext = new int[cap];
        scratch = new int[cap];
        scratch2 = new int[cap];
        domPre = new int[cap];
        domPost = new int[cap];
    }

    /**
     * Computes the dominator tree of the graph with {@code n} nodes, whose successors of node {@code v} are
     * {@code succ[succOff[v] .. succOff[v+1])} and predecessors {@code pred[predOff[v] .. predOff[v+1])}.
     */
    public void compute(int n, int[] succOff, int[] succ, int[] predOff, int[] pred) {
        ensureCapacity(n);
        Arrays.fill(dfnum, 0, n, -1);
        Arrays.fill(parent, 0, n, -1);
        Arrays.fill(semi, 0, n, -1);
        Arrays.fill(ancestor, 0, n, -1);
        Arrays.fill(idom, 0, n, -1);
        Arrays.fill(samedom, 0, n, -1);
        Arrays.fill(best, 0, n, -1);
        Arrays.fill(bucketHead, 0, n, -1);
        N = 0;
        if(n == 0) return;

        dfs(succOff, succ);

        for(int i = N - 1; i > 0; i--) {
            int node = vertex[i];
            int p = parent[node];
            int s = p;
            for(int e = predOff[node], end = predOff[node + 1]; e < end; e++) {
                int v = pred[e];
                if(dfnum[v] < 0) continue; // unreachable predecessor
                int sPrime = dfnum[v] <= dfnum[node] ? v : semi[eval(v)];
                if(dfnum[sPrime] < dfnum[s]) s = sPrime;
            }
            semi[node] = s;
            bucketNext[node] = bucketHead[s];
            bucketHead[s] = node;
            ancestor[node] = p;
            best[node] = node;

            for(int v = bucketHead[p]; v >= 0; v = bucketNext[v]) {
                int y = eval(v);
                if(semi[y] == semi[v]) idom[v] = p;
                else samedom[v] = y;
            }
            bucketHead[p] = -1;
        }
        for(int i = 1; i < N; i++) {
            int node = vertex[i];
            if(samedom[node] >= 0) idom[node] = idom[samedom[node]];
        }
        idom[0] = -1;
        numberDominatorTree(n);
    }

    /** Iterative depth-first numbering from node 0. */
    private void dfs(int[] succOff, int[] succ) {
        int[] nodeStack = scratch;
        int[] edgeStack = scratch2;
        int sp = 0;
        dfnum[0] = N;
        vertex[N++] = 0;
        best[0] = 0;
        nodeStack[sp] = 0;
        edgeStack[sp++] = succOff[0];
        while(sp > 0) {
            int node = nodeStack[sp - 1];
            int e = edgeStack[sp - 1];
            if(e >= succOff[node + 1]) {
                sp--;
                continue;
            }
            edgeStack[sp - 1] = e + 1;
            int next = succ[e];
            if(dfnum[next] >= 0) continue;
            dfnum[next] = N;
            vertex[N++] = next;
            parent[next] = node;
            best[next] = next;
            nodeStack[sp] = next;
            edgeStack[sp++] = succOff[next];
        }
    }

    /** Ancestor with the lowest semidominator, with iterative path compression. */
    private int eval(int v) {
        int[] path = scratch;
        int sp = 0;
        int x = v;
        while(ancestor[x] >= 0 && ancestor[ancestor[x]] >= 0) {
            path[sp++] = x;
            x = ancestor[x];
        }
        while(sp > 0) {
            int y = path[--sp];
            int a = ancestor[y];
            if(dfnum[semi[best[a]]] < dfnum[semi[best[y]]]) best[y] = best[a];
            ancestor[y] = ancestor[a];
        }
        return best[v];
    }

    /** Pre/post numbering of the dominator tree, so dominance queries are O(1). */
    private void numberDominatorTree(int n) {
        // Children lists via counting sort on idom.
        int[] childOff = new int[n + 1];
        for(int i = 1; i < N; i++) childOff[idom[vertex[i]] + 1]++;
        for(int i = 0; i < n; i++) childOff[i + 1] += childOff[i];
        int[] children = new int[Math.max(N - 1, 0)];
        int[] fill = Arrays.copyOf(childOff, n);
        for(int i = 1; i < N; i++) {
            int node = vertex[i];
            children[fill[idom[node]]++] = node;
        }
        int[] nodeStack = scratch;
        int[] edgeStack = scratch2;
        int sp = 0;
        int counter = 0;
        domPre[0] = counter++;
        nodeStack[sp] = 0;
        edgeStack[sp++] = childOff[0];
        while(sp > 0) {
            int node = nodeStack[sp - 1];
            int e = edgeStack[sp - 1];
            if(e >= childOff[node + 1]) {
                domPost[node] = counter++;
                sp--;
                continue;
            }
            edgeStack[sp - 1] = e + 1;
            int child = children[e];
            domPre[child] = counter++;
            nodeStack[sp] = child;
            edgeStack[sp++] = childOff[child];
        }
    }

    public boolean isReachable(int node) {
        return node >= 0 && node < dfnum.length && dfnum[node] >= 0;
    }

    /** True iff {@code a} dominates {@code b} (reflexive). False when either node is unreachable. */
    public boolean dominates(int a, int b) {
        if(!isReachable(a) || !isReachable(b)) return false;
        return domPre[a] <= domPre[b] && domPost[b] <= domPost[a];
    }
}
