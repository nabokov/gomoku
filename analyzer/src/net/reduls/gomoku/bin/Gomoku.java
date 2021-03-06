package net.reduls.gomoku.bin;

import java.io.IOException;
import net.reduls.gomoku.Tagger;
import net.reduls.gomoku.Morpheme;
import net.reduls.gomoku.util.ReadLine;

public final class Gomoku {
    public static void main(String[] args) throws IOException {
	boolean doWakati = false;
	boolean doCount = false;
	
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-wakati")) {  doWakati = true; }
            else if (args[i].equals("-count")) { doCount = true; }
            else if (args[i].equals("-verbose")) { Tagger.verbose = true; }
            else if (args[i].equals("-fine")) {
        	Tagger.doFinerSplit = true;
        	if (args.length > i+1 && !args[i+1].startsWith("-"))
        	    Tagger.finerSplitDepth = Integer.parseInt(args[++i]);
        	if (args.length > i+1 && !args[i+1].startsWith("-"))
        	    Tagger.finerSplitThreshold = Double.parseDouble(args[++i]);
            	if (args.length > i+1 && !args[i+1].startsWith("-"))
        	    Tagger.finerSplitLengthAdjust = Double.parseDouble(args[++i]);
            }
            else if (args[i].equals("-nbest") && args.length > i+1 && !args[i+1].startsWith("-")) {
        	Tagger.nBest = Integer.parseInt(args[++i]);
            }
            else {
        	System.err.println("Usage: java net.reduls.igo.bin.Gomoku [-wakati] [-count] [-fine [DEPTH] [THRESHOLD]] [-nbest N]");
        	System.exit(1);
            }
	}


	final ReadLine rl = new ReadLine(System.in);
        if(doCount) {
            int count = 0;
	    for(String s=rl.read(); s != null; s=rl.read())
                count += Tagger.wakati(s).size();
            System.out.println("morpheme count: "+count);
	} else if(doWakati) {
	    for(String s=rl.read(); s != null; s=rl.read()) {
	    for(String w : Tagger.wakati(s))
		    System.out.print(w+" ");
		System.out.println("");
	    }
	} else {
	    for(String s=rl.read(); s != null; s=rl.read()) {
		for(Morpheme m : Tagger.parse(s))
		    System.out.println(m.surface+"\t"+m.feature);
		System.out.println("EOS");
	    }
        }
    }   
}