package cs276.assignments;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.BitSet;
import java.util.Vector;

public class GammaIndex implements BaseIndex {
	private static final int INT_BYTES = Integer.SIZE / Byte.SIZE;
	private static final int VB_INT_BYTES = Integer.SIZE / (Byte.SIZE - 1) + 1;
	/**
	 * Encodes a postings list as a gap list.
	 * @param postingsList 
	 * @return - the encoded gap list
	 */
	private int[] gapEncode(List<Integer> postingsList) {
		int[] output = new int[postingsList.size()];
		for (int i = postingsList.size() - 1; i > 0; i--) {
			 output[i] = postingsList.get(i) - postingsList.get(i-1);
		}
		output[0] = postingsList.get(0)+1;//Offset first entry by 1, since 0 can't be encoded		
		return output;
	}
	
	/**
	 * Decodes a gap list to a postings list in place.
	 * @param gapList
	 */
	private void gapDecode(List<Integer> gapList) {
		gapList.set(0,gapList.get(0)-1);//Undo offset
		for (int i = 1; i < gapList.size(); i++) {
			gapList.set(i, gapList.get(i) + gapList.get(i-1));
		}
	}
	
	/**
	 * Encodes (with Gamma encoding) an array of int gaps.
	 * @param gapList
	 * @param VBGapBuf - the output buffer of VB-encoded integers. Must be
	 * 	large enough to hold the VB-encoded gapList.
	 */
	private int GammaEncode(List<Integer> gapList, ByteBuffer gammaGapBuf) {
		BitSet bits = new BitSet();
		int idx = 0;
		for (int n : gapList) {
			if (n == 1) {
				idx++;
				continue;
			}
			//Get #bits in Gamma Encode
			int numBits = (int) Math.floor(Math.log(n)/Math.log(2));
			//Set the length bits
			bits.set(idx,idx+numBits);
			idx+=numBits+1;
			//Encode n
			for (int i=0; i < numBits; i++){
				if (((1 << i) & n) != 0){
					bits.set(idx+numBits-i-1);
				}
			}
			idx +=numBits;
		}
		//Extend bitset to byte boundary by setting last bit to be 1
		bits.set(idx+(Byte.SIZE-idx%Byte.SIZE));
		byte[] bytes = bits.toByteArray();
		//Turn off last bit
		bytes[bytes.length-1] = (byte) (~((int) 0x1) & (int) bytes[bytes.length-1]);
		//Add to byte buffer
		for (byte b : bytes){
			gammaGapBuf.put(b);
		}
		return (int) Math.ceil((double) idx / Byte.SIZE);
	}

	public static byte[] toByteArray(BitSet bits) {
	    byte[] bytes = new byte[bits.length()/8+1];
	    for (int i=0; i<bits.length(); i++) {
	        if (bits.get(i)) {
	        	bytes[i/8] |= 1<<(7-i%8);
	        }
	    }
	    return bytes;
	}
	
	/**
	 * Decodes a Gamma-encoded byte sequence into a list.
	 * @param gaplist
	 * @param output - the list in which the decoded sequence is stored.
	 * @param count - the number of gamma-encoded integers to decode
	 * @return - the number of bytes decoded
	 */
	private int GammaDecode(ByteBuffer gapBuf, List<Integer> output, int count) {
		int decodedCount = 0, idx = 0;
		BitSet bits = BitSet.valueOf(gapBuf);
		//While more numbers available
		while (decodedCount < count){
			if (!bits.get(idx)){//Found a 0
				output.add(1);
				idx++;
				decodedCount++;
			} else {
				int length = 0;
				while (bits.get(idx)){
					length++;
					idx++;
				}
				//Pull out the number
				int gap = 0;
				for (int i=0; i<length; i++){
					if (bits.get(idx+length-i)){
						gap |= 0x1 << i;
					}
				}
				gap |= 0x1 << length;//Add on first bit
				decodedCount++;
				output.add(gap);
				idx+=length+1;
			}
		}
		return (int) Math.ceil((double) idx /Byte.SIZE);
	}

	@Override
	public PostingList readPosting(FileChannel fc) throws IOException {
		long currentPos = fc.position();
		//Read in termID and list length
		ByteBuffer buf = ByteBuffer.allocate(INT_BYTES*2);
		if (fc.read(buf) == -1) return null;
		buf.rewind();
		List<Integer> plist = new ArrayList<Integer>();
		GammaDecode(buf,plist,2);
		PostingList p = new PostingList(plist.get(0)-1);
		int listLength = plist.get(1);
		//Read in list
		fc.position(currentPos);
		buf = ByteBuffer.allocate(VB_INT_BYTES * listLength+2);
		if (fc.read(buf) == -1) return null;
		buf.rewind();
		plist.clear();
		int numBytes = GammaDecode(buf, plist, listLength + 2);
		for (int i = 2; i < plist.size(); i++){
			p.getList().add(plist.get(i));
		}
		fc.position(currentPos + numBytes);
		gapDecode(p.getList());
		return p;
	}

	@Override
	public void writePosting(FileChannel fc, PostingList p) throws IOException {
		//Postings list encoded with: termID, listLength, encoded gaps...
		ByteBuffer buf = ByteBuffer.allocate(VB_INT_BYTES * (p.getList().size() + 2));
		//Build the postings list
		int[] gaps = gapEncode(p.getList());
		Vector<Integer> list = new Vector<Integer>();
		// Encode termID, listLength
		list.add(p.getTermId()+1);
		list.add(p.getList().size());
		//Encode gaps
		for (int g: gaps){
			list.add(g);
		}
		int bytes = GammaEncode(list, buf);
		buf.limit(bytes);
		buf.flip();
		fc.write(buf);
	}
	
	//Test Gamma Encoding
	public boolean test(){
		boolean passed = true;
		Integer[] list = {1,23,145,1,56,45612,45,555,546545,1,1,1,154,1564,1,1,4564564,1111,14,5,4,5,6};
		//int[] list = {1,2,3,4,9,1,24,1,3};
		ByteBuffer bf = ByteBuffer.allocate(list.length * INT_BYTES);
		int bytes = GammaEncode(new ArrayList<Integer>(Arrays.asList(list)), bf);
		System.out.println(bytes);
		bf.limit(bytes);
		System.out.println("Encoded");
		for (byte b : bf.array()){
			String s1 = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
			System.out.println(s1 + " ");
		}		
		List<Integer> results = new ArrayList<Integer>();
		bf.rewind();
		GammaDecode(bf,results,list.length);
		for (int i = 0; i < list.length; i++) {
			if (list[i] != results.get(i)){
				System.out.println("Error encoding: " + list[i]);
				passed = false;
			}
		}
		if (passed){
			System.out.println("Passed!");
		}
		return passed;
	}
}
