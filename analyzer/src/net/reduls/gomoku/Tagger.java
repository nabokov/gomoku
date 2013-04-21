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
    public static double finerSplitThreshold = 2.0;
    public static int nBest = 1;

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
	    System.out.println("nBest:"+n);
	    
	    ViterbiNode head = extractPath(nodesAry, usedPaths);
	    if (head == null) break;
	    
	    //for(ViterbiNode vn=head; vn!=null; vn=vn.next) { // include EOS/BOS
	    for(ViterbiNode vn=head.next; vn.next!=null; vn=vn.next) { // skip EOS/BOS
		final String surface = text.substring(vn.start, vn.start+vn.length);
		final String feature = PartsOfSpeech.get(vn.posId);
		result.add(new Morpheme(surface, feature, vn.start));

		System.out.println(vn.toString());
	    }
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
	    //System.out.println("parseImpl loop len="+i);

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
     * @param nodesAry
     * @param usedPaths
     * @return
     */
    public static ViterbiNode extractPath(ViterbiNodeList[] nodesAry, HashSet<ViterbiNode> usedPaths) {
	int len = nodesAry.length - 1;

	ViterbiNode tail = ViterbiNode.makeBOSEOS();
	setMincostNode(tail, nodesAry[len], usedPaths);
	if (tail.prev == null) return null; // paths exausted

	ViterbiNode head = setReversePath(tail);
	
	if (doFinerSplit) {
	    ViterbiNode cur = head.next;
	    while (cur.next != null) {
		injectFinerSplit(cur.prev, cur, nodesAry);
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
     * further split current node into finer morphs, and inject corresponding nodes into current path
     * 
     * @param left : 
     * @param currentNode
     * @param nodesAry
     * @return
     */
    private static ViterbiNode injectFinerSplit(ViterbiNode left, ViterbiNode currentNode, ViterbiNodeList[] nodesAry) {
	final int rightPos = currentNode.start + currentNode.length;
	final int costLowerBound = currentNode.cost;
	final int costUpperBound = (int)(costLowerBound * finerSplitThreshold);

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
		p = p.prev;
	    }
	}
	    
	return currentNode;
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
	int nextMinCost = costUpperBound;
	ViterbiNode nextMinCostTail = null;
	ViterbiNode nextMinCostHead = null;

	System.out.println(" search segment: right pos=["+currentNode.start+","+(currentNode.start+currentNode.length)+"] cost=["+costLowerBound+" < x < "+costUpperBound+"]");

	for (int i = 0; i < prevs.size(); i++) {
	    final ViterbiNode p = prevs.get(i);
	    if (p.cost < nextMinCost
		    && p.cost >= costLowerBound
		    && p != currentNode
		    && p.length < currentNode.length) {

		// check if this segment actually divides the current morph
		ViterbiNode tmp2 = p;
		int segmentLength = p.length;
		while (segmentLength < currentNode.length && tmp2.prev != null) {
		    tmp2 = tmp2.prev;
		    segmentLength += tmp2.length;
		}
		if (segmentLength != currentNode.length) continue;

		nextMinCost = p.cost;
		nextMinCostTail = p;
		nextMinCostHead = tmp2;
	    }
	}

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
	    
	    System.out.println("    setMincostNode:"+vn.toString());
	} else {
	    System.out.println("    setMincostNode: no more available nodes");
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
	    //System.out.println("    MakeLattice.call vitterbinode="+vn.toString());
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
