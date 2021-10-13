package cl.uchile.dcc.caching.bgps;

import java.util.ArrayList;
import java.util.Map;

import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.Syntax;
import org.apache.jena.sparql.algebra.Algebra;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.op.Op1;
import org.apache.jena.sparql.algebra.op.Op2;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpN;
import org.apache.jena.sparql.algebra.op.OpPath;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.TriplePath;
import org.apache.jena.sparql.syntax.ElementGroup;

import cl.uchile.dcc.main.SingleQuery;

public class ExtractBgps {
	
	private static Map<String, String> varMap;
	
	public static Map<String, String> getVarMap() {
		return varMap;
	}
	
	public static ArrayList<OpBGP> getBgps(Op op){
		ArrayList<OpBGP> bgps = new ArrayList<OpBGP>();
		getBgps(op,bgps);
		return bgps;
	}
	
	public static ArrayList<OpBGP> getSplitBgps(Op op){
		ArrayList<OpBGP> splitBgps = new ArrayList<OpBGP>();
		getSplitBgps(op,splitBgps);
		return splitBgps;
	}
	
	public static void getBgps(Op op, ArrayList<OpBGP> bgps){
		if(op instanceof OpBGP) {
			bgps.add((OpBGP)op);
		}else if (op instanceof OpPath) {
			TriplePath path = ((OpPath)op).getTriplePath();
			BasicPattern bp = new BasicPattern();
			Triple nt = Triple.create(path.getSubject(), NodeFactory.createURI(path.getPath().toString()), path.getObject());
			bp.add(nt);
			bgps.add(new OpBGP(bp));
		} else if(op instanceof Op1) {
			getBgps(((Op1)op).getSubOp(),bgps);
		} else if(op instanceof Op2) {
			getBgps(((Op2)op).getLeft(),bgps);
			getBgps(((Op2)op).getRight(),bgps);
		} else if(op instanceof OpN) {
			OpN opn = (OpN) op;
			for(Op sop:opn.getElements()) {
				getBgps(sop,bgps);
			}
		}
	}
	
	/**
	 * WILL COUNT PROPERTY PATHS AS A NORMAL TRIPLE
	 * @param op
	 * @param splitbgps
	 */
	public static void getSplitBgps(Op op, ArrayList<OpBGP> splitbgps) {
		if (op instanceof OpBGP) {
			ArrayList<OpBGP> l = new ArrayList<OpBGP>();
			splitbgps.add((OpBGP)op);
		} else if (op instanceof OpPath) {
			ArrayList<OpBGP> l = new ArrayList<OpBGP>();
			TriplePath path = ((OpPath)op).getTriplePath();
			BasicPattern bp = new BasicPattern();
			Triple nt = Triple.create(path.getSubject(), NodeFactory.createURI(path.getPath().toString()), path.getObject());
			bp.add(nt);
			splitbgps.add(new OpBGP(bp));
		} else if (op instanceof Op1) {
			getSplitBgps(((Op1)op).getSubOp(), splitbgps);
		} else if (op instanceof Op2) {
			getSplitBgps(((Op2)op).getLeft(), splitbgps);
			getSplitBgps(((Op2)op).getRight(), splitbgps);
		} else if(op instanceof OpN) {
			OpN opn = (OpN) op;
			for(Op sop : opn.getElements()) {
				getSplitBgps(sop, splitbgps);
			}
		}
	}
	
	public static void extractBGP(Query q) {
		Op op = Algebra.compile(q);
		ArrayList<OpBGP> opbgps = getBgps(op);
		System.out.println(opbgps.toString());
	}
	
	public static void extractSplitBGPs(Query q) {
		Op op = Algebra.compile(q);
		ArrayList<OpBGP> splitbgps = getSplitBgps(op);
		System.out.println(splitbgps.toString());
	}
	
	public static OpBGP unifyBGPs(ArrayList<OpBGP> input) {
		BasicPattern bp = new BasicPattern();
		
		for (OpBGP bgp : input) {
			bp.add(bgp.getPattern().get(0));
		}
		
		OpBGP output = new OpBGP(bp);
		return output;
	}
	
	public static OpBGP canonBGP(OpBGP input) throws Exception {
		Query q = QueryFactory.make();
		q.setQuerySelectType();
		q.setQueryResultStar(true);
		ElementGroup elg = new ElementGroup();
		for (int i = 0; i < input.getPattern().size(); i++) {
			elg.addTriplePattern(input.getPattern().get(i));
		}
		q.setQueryPattern(elg);
		SingleQuery sq = new SingleQuery(q.toString(), true, true, false, true);
		q = QueryFactory.create(sq.getQuery(), Syntax.syntaxARQ);
		Op op = Algebra.compile(q);
		ArrayList<OpBGP> bgps = getBgps(op);
		varMap = sq.getVarMap();
		return bgps.get(0);
	}
	
	/**
	 * Only separates bgps into chunks of size 1
	 * @param input
	 * @return
	 * @throws Exception
	 */
	public static ArrayList<OpBGP> separateBGPs(ArrayList<OpBGP> input) {
		ArrayList<OpBGP> output = new ArrayList<OpBGP>();
		
		for (int i = 0; i < input.size(); i++) {
			OpBGP bgp = input.get(i);
			for (int j = 0; j < bgp.getPattern().size(); j++) {
				Triple t = input.get(i).getPattern().get(j);
				Query q = QueryFactory.make();
				q.setQuerySelectType();
				q.setQueryResultStar(true);
				ElementGroup elg = new ElementGroup();
				elg.addTriplePattern(t);
				q.setQueryPattern(elg);
				output.addAll(getBgps(Algebra.compile(q)));
			}
		}
		
		return output;
	}
	
	/*
	 * Gets bgps and returns bgps of size one that have been canonicalised
	 */
	public static ArrayList<OpBGP> separateCanonBGPs(ArrayList<OpBGP> input) throws Exception {
		ArrayList<OpBGP> output = new ArrayList<OpBGP>();
		
		for (int i = 0; i < input.size(); i++) {
			OpBGP bgp = input.get(i);
			for (int j = 0; j < bgp.getPattern().size(); j++) {
				Triple t = input.get(i).getPattern().get(j);
				Query q = QueryFactory.make();
				q.setQuerySelectType();
				q.setQueryResultStar(true);
				ElementGroup elg = new ElementGroup();
				elg.addTriplePattern(t);
				q.setQueryPattern(elg);
				SingleQuery sq = new SingleQuery(q.toString(), true, true, false, true);
				q = QueryFactory.create(sq.getQuery(), Syntax.syntaxARQ);
				output.addAll(getBgps(Algebra.compile(q)));
			}
		}
		
		return output;
	}
	
	public static void main(String[] args) {
		
	}
}
