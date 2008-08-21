package org.springframework.batch.item.support;

import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.NoWorkFoundException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.batch.item.util.ExecutionContextUserSupport;
import org.springframework.util.Assert;

/**
 * Abstract superclass for {@link ItemReader}s which use item buffering to
 * support reset/rollback. Supports restart by storing item count in the
 * {@link ExecutionContext} (therefore requires item ordering to be preserved
 * between runs).
 * 
 * Subclasses are inherently *not* thread-safe.
 * 
 * @author Robert Kasanicky
 */
public abstract class AbstractItemReaderItemStream<T> implements ItemReader<T>, ItemStream {

	private static final String READ_COUNT = "read.count";

	private int currentItemCount = 0;

	private ExecutionContextUserSupport ecSupport = new ExecutionContextUserSupport();

	private boolean saveState = true;

	/**
	 * Read next item from input.
	 * @return item
	 * @throws Exception
	 */
	protected abstract T doRead() throws Exception;
	
	/**
	 * Open resources necessary to start reading input.
	 */
	protected abstract void doOpen() throws Exception;

	/**
	 * Close the resources opened in {@link #doOpen()}.
	 */
	protected abstract void doClose() throws Exception;

	/**
	 * Move to the given item index. Subclasses should override this method if
	 * there is a more efficient way of moving to given index than re-reading
	 * the input using {@link #doRead()}.
	 */
	protected void jumpToItem(int itemIndex) throws Exception {
		for (int i = 0; i < itemIndex; i++) {
			doRead();
		}
	}

	public T read() throws Exception, UnexpectedInputException, NoWorkFoundException, ParseException {
		currentItemCount++;
		return doRead();
	}

	/**
	 * Mark is supported as long as this {@link ItemStream} is used in a
	 * single-threaded environment. The state backing the mark is a single
	 * counter, keeping track of the current position, so multiple threads
	 * cannot be accommodated.
	 */
	public void mark() {
	}

	public void reset() {
	}

	protected int getCurrentItemCount() {
		return currentItemCount;
	}

	protected void setCurrentItemCount(int count) {
		this.currentItemCount = count;
	}

	public void close(ExecutionContext executionContext) throws ItemStreamException {
		currentItemCount = 0;
		try {
			doClose();
		}
		catch (Exception e) {
			throw new ItemStreamException("Error while closing item reader", e);
		}
	}

	public void open(ExecutionContext executionContext) throws ItemStreamException {

		try {
			doOpen();
		}
		catch (Exception e) {
			throw new ItemStreamException("Failed to initialize the reader", e);
		}

		if (executionContext.containsKey(ecSupport.getKey(READ_COUNT))) {
			int itemCount = new Long(executionContext.getLong(ecSupport.getKey(READ_COUNT))).intValue();

			try {
				jumpToItem(itemCount);
			}
			catch (Exception e) {
				throw new ItemStreamException("Could not move to stored position on restart", e);
			}

			currentItemCount = itemCount;
		}

	}

	public void update(ExecutionContext executionContext) throws ItemStreamException {
		if (saveState) {
			Assert.notNull(executionContext, "ExecutionContext must not be null");
			executionContext.putLong(ecSupport.getKey(READ_COUNT), currentItemCount);
		}

	}

	public void setName(String name) {
		ecSupport.setName(name);
	}

	/**
	 * Set the flag that determines whether to save internal data for
	 * {@link ExecutionContext}. Only switch this to false if you don't want to
	 * save any state from this stream, and you don't need it to be restartable.
	 * 
	 * @param saveState flag value (default true).
	 */
	public void setSaveState(boolean saveState) {
		this.saveState = saveState;
	}

}