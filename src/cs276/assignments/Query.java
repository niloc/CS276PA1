package cs276.assignments;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.List;
import java.util.Iterator;

public class Query {

	// Term id -> position in index file
	private static Map<Integer, Long> posDict = new TreeMap<Integer, Long>();
	// Term id -> document frequency
	private static Map<Integer, Integer> freqDict = new TreeMap<Integer, Integer>();
	// Doc id -> doc name dictionary
	private static Map<Integer, String> docDict = new TreeMap<Integer, String>();
	// Term -> term id dictionary
	private static Map<String, Integer> termDict = new TreeMap<String, Integer>();
	// Index
	private static BaseIndex index = null;

	
	/* 
	 * Write a posting list with a given termID from the file 
	 * You should seek to the file position of this specific
	 * posting list and read it back.
	 * */
	private static PostingList readPosting(FileChannel fc, int termId)
			throws IOException {
		fc.position(posDict.get(termId));
		return index.readPosting(fc);
	}

	public static void main(String[] args) throws IOException {
		/* Parse command line */
		if (args.length != 2) {
			System.err.println("Usage: java Query [Basic|VB|Gamma] index_dir");
			return;
		}

		/* Get index */
		String className = "cs276.assignments." + args[0] + "Index";
		try {
			Class<?> indexClass = Class.forName(className);
			index = (BaseIndex) indexClass.newInstance();
		} catch (Exception e) {
			System.err
					.println("Index method must be \"Basic\", \"VB\", or \"Gamma\"");
			throw new RuntimeException(e);
		}

		/* Get index directory */
		String input = args[1];
		File inputdir = new File(input);
		if (!inputdir.exists() || !inputdir.isDirectory()) {
			System.err.println("Invalid index directory: " + input);
			return;
		}

		/* Index file */
		RandomAccessFile indexFile = new RandomAccessFile(new File(input,
				"corpus.index"), "r");
		FileChannel indexfc = indexFile.getChannel();

		String line = null;
		/* Term dictionary */
		BufferedReader termReader = new BufferedReader(new FileReader(new File(
				input, "term.dict")));
		while ((line = termReader.readLine()) != null) {
			String[] tokens = line.split("\t");
			termDict.put(tokens[0], Integer.parseInt(tokens[1]));
		}
		termReader.close();

		/* Doc dictionary */
		BufferedReader docReader = new BufferedReader(new FileReader(new File(
				input, "doc.dict")));
		while ((line = docReader.readLine()) != null) {
			String[] tokens = line.split("\t");
			docDict.put(Integer.parseInt(tokens[1]), tokens[0]);
		}
		docReader.close();

		/* Posting dictionary */
		BufferedReader postReader = new BufferedReader(new FileReader(new File(
				input, "posting.dict")));
		while ((line = postReader.readLine()) != null) {
			String[] tokens = line.split("\t");
			posDict.put(Integer.parseInt(tokens[0]), Long.parseLong(tokens[1]));
			freqDict.put(Integer.parseInt(tokens[0]),
					Integer.parseInt(tokens[2]));
		}
		postReader.close();

		/* Processing queries */
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

		/* For each query */
		while ((line = br.readLine()) != null) {
			String[] tokens = line.trim().split(" ");
			List<Integer> resultList = processQuery(tokens, indexfc);
			//Print Results
			if (resultList == null || resultList.isEmpty()){
				System.out.println("no results found");
			} else {
				//Sort and print results
				TreeSet<String> results = new TreeSet<String>();
				for (Integer docId : resultList){
					results.add(docDict.get(docId));
				}
				for (String page : results){
					System.out.println(page);
				}	
			}
		}
		br.close();
		indexFile.close();
	}
	/**
	 * Process the given query array on the given index
	 * @param query
	 * @param indexfc
	 * @return
	 */
	private static List<Integer> processQuery(String[] query, FileChannel indexfc) throws IOException{
		TreeSet<PostingList> plists = new TreeSet<PostingList>(new Comparator<PostingList>(){
			public int compare(PostingList p1, PostingList p2) {
				return freqDict.get(p1.getTermId()) - freqDict.get(p2.getTermId());
			}
		});
		//Read in all the postings lists
		for (String token : query){
			if (termDict.containsKey(token)){
				plists.add(readPosting(indexfc,termDict.get(token)));
			} else {
				return null;
			}
		}
		if (plists.isEmpty()) return null;
		//Intersect the resulting postings lists in increasing freq order
		List<Integer> resultList = plists.first().getList();
		plists.remove(plists.first());//Pop off the first posting list
		for (PostingList pl : plists){
			resultList = intersectLists(resultList, pl.getList());
		}
		return resultList;
	}
	
	/**
	 * Takes two document lists and returns their intersection
	 * @param list1
	 * @param list2
	 */
	private static List<Integer> intersectLists(List<Integer> list1, List<Integer> list2){
		List<Integer> resultList = new ArrayList<Integer>();
		Iterator<Integer> iter1 = list1.iterator();
		Iterator<Integer> iter2 = list2.iterator();
		Integer doc1 = popNextOrNull(iter1);
		Integer doc2 = popNextOrNull(iter2);
		while (doc1 != null && doc2 != null){
			if (doc1.equals(doc2)){
				resultList.add(doc1);
				doc1 = popNextOrNull(iter1);
				doc2 = popNextOrNull(iter2);
			} else if (doc1 < doc2) {
				doc1 = popNextOrNull(iter1);
			} else {
				doc2 = popNextOrNull(iter2);
			}
		}
		return resultList;
	}
	static <X> X popNextOrNull(Iterator<X> p) {
		return p.hasNext() ? p.next() : null;
	}
}
