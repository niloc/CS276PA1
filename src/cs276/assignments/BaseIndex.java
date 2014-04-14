package cs276.assignments;

import java.io.IOException;
import java.nio.channels.FileChannel;

public interface BaseIndex {
	
	public PostingList readPosting (FileChannel fc) throws IOException;
	
	public void writePosting (FileChannel fc, PostingList p) throws IOException;
}
