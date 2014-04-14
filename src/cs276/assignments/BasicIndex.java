package cs276.assignments;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class BasicIndex implements BaseIndex {
	private static final int INT_BYTES = Integer.SIZE / Byte.SIZE;

	@Override
	public PostingList readPosting(FileChannel fc) throws IOException {
		//Read in termID and list length
		ByteBuffer buf = ByteBuffer.allocate(INT_BYTES*2);
		if (fc.read(buf) == -1) return null;
		buf.rewind();
		PostingList p = new PostingList(buf.getInt());
		int listLength = buf.getInt();
		//Read in list
		buf = ByteBuffer.allocate(INT_BYTES*listLength);
		if (fc.read(buf) == -1) return null;
		buf.rewind();
		for (int i=0; i < listLength; i++){
			p.getList().add(buf.getInt());
		}
		return p;
	}

	@Override
	public void writePosting(FileChannel fc, PostingList p) throws IOException {
		//Postings list encoded with: termID,listLength,docIDs...
		ByteBuffer buf = ByteBuffer.allocate(INT_BYTES*(p.getList().size()+2));
		buf.putInt(p.getTermId());
		buf.putInt(p.getList().size());
		for (int docID : p.getList()){
			buf.putInt(docID);
		}
		buf.flip();
		fc.write(buf);
	}
}
