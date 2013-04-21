package net.reduls.gomoku.dic;

public final class ViterbiNode {
    public int cost;
    public ViterbiNode prev = null;
    public ViterbiNode next = null;
    
    public final int start;
    public final short length;
    public final short posId;
    public final boolean isSpace;
    
    public ViterbiNode(int start, short length, short wordCost, short posId, boolean isSpace) {
        this.cost = wordCost;
        
        this.start = start;
        this.length = length;
        this.posId = posId;
        this.isSpace = isSpace;
    }

    public static ViterbiNode makeBOSEOS() {
        return new ViterbiNode(0, (short)0, (short)0, (short)0, false);
    }
    
    public String toString() {
      String str = 
          "[" + this.getClass().getSimpleName()
          + " start:" + start
          + " lengh:" + length
          + " posId:" + posId
          + (prev != null ? " link cost:" + Matrix.linkCost(prev.posId, posId) : "")
          + " acc cost:" + cost
          + "]";
      return str;
    }

}