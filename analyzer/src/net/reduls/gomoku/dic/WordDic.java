package net.reduls.gomoku.dic;

import java.util.List;

public final class WordDic {
    public static interface Callback {
        public void call(ViterbiNode vn);
        public boolean isEmpty();
    }

    public static void search(String text, int start, Callback fn) {
        //System.out.println("  [search WordDic]");
        SurfaceId.eachCommonPrefix(text, start, fn);
    }

    public static void eachViterbiNode(Callback fn, int surfaceId, 
                                       int start, int length, boolean isSpace) {
        //System.out.println("  eachVitterbiNode: start="+start+" length="+length);
        int[] mis = Morpheme.getMorphemes(surfaceId);
        for(int i=0; i < mis.length; i++)
            fn.call(new ViterbiNode(start, (short)length,
                                    Morpheme.cost(mis[i]),
                                    Morpheme.posId(mis[i]),
                                    isSpace));        
    }
}