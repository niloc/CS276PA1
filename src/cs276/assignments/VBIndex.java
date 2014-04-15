package cs276.assignments;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class VBIndex implements BaseIndex {
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
	 * Encodes (with VB encoding) an array of int gaps.
	 * @param gapList
	 * @param VBGapBuf - the output buffer of VB-encoded integers. Must be
	 * 	large enough to hold the VB-encoded gapList.
	 */
	private void VBEncode(int[] gapList, ByteBuffer VBGapBuf) {
		Stack<Byte> byteStack = new Stack<Byte>();
		for (int n : gapList) {
			while (true) {
				byteStack.push(new Byte((byte) (n & 0x7f)));
				if (n < 128) {
					break;
				}
				n = n / 128;
			}
			
			// Write encoded byte sequence to output array
			Byte encoded;
			while (!byteStack.empty()) {
				encoded = byteStack.pop();
				if (byteStack.size() == 0) {
					// Set continuation bit of last byte
					encoded = (byte) (encoded | 0x80);
				} 
				VBGapBuf.put(encoded);
			}
		}
	}
	
	/**
	 * Decodes a VB-encoded byte sequence into a list.
	 * @param VBGapList
	 * @param output - the list in which the decoded sequence is stored.
	 * @param count - the number of VB-encoded integers to decode
	 * @return - the number of bytes decoded
	 */
	private int VBDecode(ByteBuffer VBGapBuf, List<Integer> output, int count) {
		int n = 0, decodedCount = 0, numBytes = 0;
		while (decodedCount < count && VBGapBuf.hasRemaining()) {
			numBytes++;
			byte curByte = VBGapBuf.get();
			if ((curByte & 0x80) == 0) {
				n = 128 * n + curByte;
			} else {
				n = 128 * n + (curByte & 0x7f);
				output.add(new Integer(n));
				decodedCount++;
				n = 0;
			}
		}
		return numBytes;
	}

	@Override
	public PostingList readPosting(FileChannel fc) throws IOException {
		//Read in termID and list length
		long currentPos = fc.position();
		ByteBuffer buf = ByteBuffer.allocate(VB_INT_BYTES*2);
		if (fc.read(buf) == -1) return null;
		buf.rewind();
		// Decode termID, listLength
		List<Integer> metaData = new ArrayList<Integer>();
		int numBytes = VBDecode(buf, metaData, 2);
		PostingList p = new PostingList(metaData.get(0));
		int listLength = metaData.get(1);
		fc.position(currentPos + numBytes);
		//Read in list
		currentPos = fc.position();
		buf = ByteBuffer.allocate(VB_INT_BYTES * listLength);
		if (fc.read(buf) == -1) return null;
		buf.rewind();
		numBytes = VBDecode(buf, p.getList(), listLength);
		fc.position(currentPos + numBytes);
		gapDecode(p.getList());
		return p;
	}

	@Override
	public void writePosting(FileChannel fc, PostingList p) throws IOException {
		//Postings list encoded with: termID, listLength, encoded gaps...
		ByteBuffer buf = ByteBuffer.allocate(VB_INT_BYTES * (p.getList().size() + 2));
		// Encode termID, listLength
		int[] metaData = {p.getTermId(), p.getList().size()};
		VBEncode(metaData, buf);
		int[] gaps = gapEncode(p.getList());
		VBEncode(gaps, buf);
		buf.flip();
		fc.write(buf);
	}
}
