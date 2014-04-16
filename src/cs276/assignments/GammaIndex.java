package cs276.assignments;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;
import java.util.BitSet;

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
		output[0] = postingsList.get(0);
		return output;
	}
	
	/**
	 * Decodes a gap list to a postings list in place.
	 * @param gapList
	 */
	private void gapDecode(List<Integer> gapList) {
		for (int i = 1; i < gapList.size(); i++) {
			gapList.set(i, gapList.get(i) + gapList.get(i-1));
		}
		if (gapList.size() < 40) {
		}

	}
	
	/**
	 * Encodes (with Gamma encoding) an array of int gaps.
	 * @param gapList
	 * @param VBGapBuf - the output buffer of VB-encoded integers. Must be
	 * 	large enough to hold the VB-encoded gapList.
	 */
	private void GammaEncode(int[] gapList, ByteBuffer gammaGapBuf) {
		for (int n : gapList) {
			if (n == 1) {
				gammaGapBuf.put((byte) 0);
				continue;
			}
			BitSet bits = new BitSet();
			int length = 0;
			int tmp = n;
			while (tmp != 0) {
				tmp = (tmp >>> 1);
				length++;
			}
			length--;
			System.out.println("n: " + n + "len: " + length);

			bits.set(0, length);
			bits.set(length, false);
			for (int i = length - 1; i >= 0; i--) {
				int mask = (1 << i);
				bits.set(2 * length - i, (n & mask) != 0);
			}
			
			byte[] bytes = bits.toByteArray();
			for (byte b : bytes) {
				gammaGapBuf.put(b);
			}
		}
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
		int n = 0, decodedCount = 0, numBytes = 0;
		while (decodedCount < count && gapBuf.hasRemaining()) {
			// TODO
		}

	}

	@Override
	public PostingList readPosting(FileChannel fc) throws IOException {
		//Read in termID and list length
		ByteBuffer buf = ByteBuffer.allocate(INT_BYTES*2);
		if (fc.read(buf) == -1) return null;
		buf.rewind();
		// Decode termID, listLength
		PostingList p = new PostingList(buf.getInt());
		int listLength = buf.getInt();
		//Read in list
		long currentPos = fc.position();
		buf = ByteBuffer.allocate(VB_INT_BYTES * listLength);
		if (fc.read(buf) == -1) return null;
		buf.rewind();
		int numBytes = GammaDecode(buf, p.getList(), listLength);
		fc.position(currentPos + numBytes);
		gapDecode(p.getList());
		return p;
	}

	@Override
	public void writePosting(FileChannel fc, PostingList p) throws IOException {
		//Postings list encoded with: termID, listLength, encoded gaps...
		ByteBuffer buf = ByteBuffer.allocate(VB_INT_BYTES * (p.getList().size() + 2));
		// Encode termID, listLength
		buf.putInt(p.getTermId());
		buf.putInt(p.getList().size());
		int[] gaps = gapEncode(p.getList());
		GammaEncode(gaps, buf);
		buf.flip();
		fc.write(buf);
	}
}
