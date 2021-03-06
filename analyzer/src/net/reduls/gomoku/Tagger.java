package net.reduls.gomoku;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import net.reduls.gomoku.dic.ViterbiNode;
import net.reduls.gomoku.dic.WordDic;
import net.reduls.gomoku.dic.Unknown;
import net.reduls.gomoku.dic.Matrix;
import net.reduls.gomoku.dic.PartsOfSpeech;

public final class Tagger {
    static class ViterbiNodeList extends ArrayList<ViterbiNode> {}

    public static boolean doFinerSplit = false;
    public static double finerSplitThreshold = 1.5;
    public static double finerSplitLengthAdjust = 0.0; // penalize short morphs 
    public static int finerSplitDepth = 2;

    public static int nBest = 1;
    public static boolean verbose = false;

    private static final ViterbiNodeList BOS_NODES = new ViterbiNodeList();
    static {
	BOS_NODES.add(ViterbiNode.makeBOSEOS());
    }

    public static List<Morpheme> parse(String text) {
	return parse(text, new ArrayList<Morpheme>(text.length()/2));
    }

    public static List<Morpheme> parse(String text, List<Morpheme> result) {
	ViterbiNodeList[] nodesAry = makeLattice(text);
	
	HashSet<ViterbiNode> usedPaths = (nBest > 1 ? new HashSet<ViterbiNode>() : null);
	for (int n = 0; n < nBest; n++) {
	    if (verbose) System.out.println("nBest:"+n);
	    
	    ViterbiNode head = extractPath(nodesAry, usedPaths);
	    if (head == null) break;
	    
	    //for(ViterbiNode vn=head; vn!=null; vn=vn.next) { // include EOS/BOS
	    for(ViterbiNode vn=head.next; vn.next!=null; vn=vn.next) { // skip EOS/BOS
		final String surface = text.substring(vn.start, vn.start+vn.length);
		final String feature = PartsOfSpeech.get(vn.posId);
		result.add(new Morpheme(surface, feature, vn.start));

		if (verbose) System.out.println(vn.toString());
	    }
	    cleanupNextLinks(head);
	}
	return result;
    }

    public static List<String> wakati(String text) {
	return wakati(text, new ArrayList<String>(text.length()/1));
    }

    public static List<String> wakati(String text, List<String> result) {
	ViterbiNodeList[] nodesAry = makeLattice(text);
	ViterbiNode head = extractPath(nodesAry, null);
	
	//for(ViterbiNode vn=head; vn!=null; vn=vn.next) // include EOS/BOS
	for(ViterbiNode vn=head.next; vn.next!=null; vn=vn.next) // skip EOS/BOS
	    result.add(text.substring(vn.start, vn.start+vn.length));
	
	cleanupNextLinks(head);
	return result;
    }

    /**
     * create lattice
     * @param text
     * @return
     */
    public static ViterbiNodeList[] makeLattice(String text) {
	final int len = text.length();
	final ViterbiNodeList[] nodesAry = new ViterbiNodeList[len+1];
	nodesAry[0] = BOS_NODES;

	MakeLattice fn = new MakeLattice(nodesAry);
	for(int i=0; i < len; i++) {
	    if(nodesAry[i] != null) {
		fn.set(i);
		WordDic.search(text, i, fn);
		Unknown.search(text, i, fn);
	    }
	}
	return nodesAry;
    }

    /**
     * extract optimal path from the created lattice
     * @param nodesAry : lattice
     * @param usedPaths
     * @return
     */
    public static ViterbiNode extractPath(ViterbiNodeList[] nodesAry, HashSet<ViterbiNode> usedPaths) {
	int len = nodesAry.length - 1;

	ViterbiNode tail = ViterbiNode.makeBOSEOS();
	setMincostNode(tail, nodesAry[len], usedPaths);
	if (tail.prev == null) return null; // paths exhausted

	ViterbiNode head = setReversePath(tail);
	
	if (doFinerSplit) {
	    ViterbiNode cur = head.next;
	    while (cur.next != null) {
		injectFinerSplit(cur.prev, cur, nodesAry, finerSplitDepth);
		cur = cur.next;
	    }
	}

	return head; 
    }

    /**
     * update ViterbiNode's temporary variables in accordance to current path of interest
     * 
     * @param tail : tail of the path
     * @return  : head node of the path. traversable via node.next
     */
    private static ViterbiNode setReversePath(ViterbiNode tail) {
	ViterbiNode cur = tail;
	while(cur.prev != null) {
	    cur.prev.next = cur;
	    cur = cur.prev;
	}
	return cur;
    }

    /**
     * reset *.next to null 
     * @param head
     */
    private static void cleanupNextLinks(ViterbiNode head) {
	ViterbiNode cur = head;
	while (cur != null) {
	    ViterbiNode tmp = cur.next;
	    cur.next = null;
	    cur = tmp;
	}
    }
    
    /**
     * further split current node into finer morphs, and inject corresponding nodes into current path
     * 
     * @param left : left(prev) node of the current node
     * @param currentNode : node to further split
     * @param nodesAry : lattice
     * @param depth : number of recursive splits
     * @return : head of the new finer segment
     */
    private static ViterbiNode injectFinerSplit(ViterbiNode left, ViterbiNode currentNode, ViterbiNodeList[] nodesAry, int depth) {
	if (currentNode.length == 1) return currentNode; // for efficiency
	
	final int rightPos = currentNode.start + currentNode.length;
	final int currentNodeCostAlone = currentNode.cost - currentNode.prev.cost;
	final int costLowerBound = currentNode.cost;
	final int costUpperBound = costLowerBound
		+ (int)((currentNodeCostAlone >= 0 ? currentNodeCostAlone : currentNodeCostAlone*-1) * (finerSplitThreshold - 1.0));

	if (verbose) System.out.println(" search segment : depth="+(finerSplitDepth-depth)+" pos=["+currentNode.start+","+(currentNode.start+currentNode.length)+"] cost=["+costLowerBound+" < x < "+costUpperBound+"]");

	ViterbiNode[] nextMincostSegment = findNextMincostSegment(currentNode, nodesAry[rightPos], costLowerBound, costUpperBound);
	ViterbiNode nextMinCostHead = nextMincostSegment[0];
	ViterbiNode nextMinCostTail = nextMincostSegment[1];

	if (nextMinCostTail != null) { //found
	    currentNode.prev.next = nextMinCostHead;
	    
	    ViterbiNode tmp = currentNode;
	    ViterbiNode p = nextMinCostTail;
	    while (p != nextMinCostHead.prev) {
		p.next = tmp;
		
		tmp = p;
		if (depth > 1) tmp = injectFinerSplit(p.prev, p, nodesAry, depth - 1);
		
		p = p.prev;
	    }
	}
	    
	return (nextMinCostHead != null ? nextMinCostHead : currentNode); 
    }

    /**
     * find a minimum cost path that further splits the current morpheme
     * 
     * @param currentNode : node to be splitted
     * @param prevs : cost matrix for the current position 
     * @param costLowerBound : cost should be equal or more than this value
     * @param costUpperBound : cost should not exceed this value
     * @return : [head, tail] nodes of the path, if found 
     */
    private static ViterbiNode[] findNextMincostSegment(ViterbiNode currentNode, ViterbiNodeList prevs, int costLowerBound, int costUpperBound) {
	double nextMinCost = costUpperBound;
	ViterbiNode nextMinCostTail = null;
	ViterbiNode nextMinCostHead = null;

	for (int i = 0; i < prevs.size(); i++) {
	    final ViterbiNode p = prevs.get(i);
	    if (p.cost < nextMinCost
		    && p.cost >= costLowerBound
		    && p != currentNode
		    && p.length < currentNode.length) {

		// check if this segment actually divides the current morph
		// and also adjust the cost taking the length of each morphs in account

		ViterbiNode tmp2 = p;
		int segmentLength = p.length;
		double adjustedCost = p.cost * (1.0 + finerSplitLengthAdjust / p.length); //(1.0 + finerSplitLengthAdjust / (p.length*p.length));
		while (segmentLength < currentNode.length && tmp2.prev != null) {
		    tmp2 = tmp2.prev;
		    segmentLength += tmp2.length;
		    adjustedCost *= (1.0 + finerSplitLengthAdjust / tmp2.length);//(1.0 + finerSplitLengthAdjust / (tmp2.length*tmp2.length));
		}
		if (segmentLength != currentNode.length
			|| tmp2.prev != currentNode.prev // should connect to the same morph as original
			|| adjustedCost > nextMinCost) continue;

		nextMinCost = adjustedCost;
		nextMinCostTail = p;
		nextMinCostHead = tmp2;
	    }
	}

	if (verbose && nextMinCostTail != null) { System.out.println("   found : ->"+nextMinCostTail.toString()); }

	ViterbiNode[] rtn = { nextMinCostHead, nextMinCostTail };
	return rtn;
    }
    
    /**
     * selects and sets the previous node which forms the minimum cost path.
     * will avoid nodes in usedPath set when provided. (used for n-best).
     *  
     * @param vn : node to update its prev attribute
     * @param prevs : candidate previous node set
     * @param usedPaths : (optional) nodes to exclude
     * @return vn
     */
    private static ViterbiNode setMincostNode(ViterbiNode vn, ViterbiNodeList prevs) {
	return setMincostNode(vn, prevs, null);
    }
    private static ViterbiNode setMincostNode(ViterbiNode vn, ViterbiNodeList prevs, HashSet<ViterbiNode> usedPaths) {
	ViterbiNode minCostNode = null;
	int minCost = -1;
	for(int i=0; i < prevs.size(); i++) {
	    final ViterbiNode p = prevs.get(i);
	    final int cost = p.cost + Matrix.linkCost(p.posId, vn.posId);

	    if((minCost == -1 || cost < minCost)
		    && (usedPaths == null || !usedPaths.contains(p))) {
		minCost = cost;
		minCostNode = p;
		vn.prev = p;
	    }
	}
	
	if (minCostNode != null) { // found
	    vn.cost += minCost;
	
	    if (usedPaths != null) usedPaths.add(minCostNode);
	    
	    if (verbose) System.out.println("    setMincostNode:"+vn.toString());
	} else {
	    if (verbose) System.out.println("    setMincostNode: no more available nodes");
	}
	
	return vn;
    }

    private static final class MakeLattice implements WordDic.Callback {
	private final ViterbiNodeList[] nodesAry;
	private int i;
	private ViterbiNodeList prevs;
	private boolean empty=true;

	public MakeLattice(ViterbiNodeList[] nodesAry) {
	    this.nodesAry = nodesAry;
	}

	public void set(int i) {
	    this.i = i;
	    prevs = nodesAry[i];
	    if (!doFinerSplit) nodesAry[i] = null;
	    empty = true;
	}

	public void call(ViterbiNode vn) {
	    empty=false;

	    final int end = i+vn.length;
	    if(nodesAry[end]==null)
		nodesAry[end] = new ViterbiNodeList();

	    if(vn.isSpace)
		nodesAry[end].addAll(prevs);
	    else
		nodesAry[end].add(setMincostNode(vn, prevs));
	}

	public boolean isEmpty() { return empty; }
    }
}
