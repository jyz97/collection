package edu.berkeley.cs186.database.recovery;

import edu.berkeley.cs186.database.Transaction;
import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.common.Pair;
import edu.berkeley.cs186.database.concurrency.LockContext;
import edu.berkeley.cs186.database.concurrency.LockType;
import edu.berkeley.cs186.database.concurrency.LockUtil;
import edu.berkeley.cs186.database.io.DiskSpaceManager;
import edu.berkeley.cs186.database.memory.BufferManager;
import edu.berkeley.cs186.database.memory.Page;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Implementation of ARIES.
 */
public class ARIESRecoveryManager implements RecoveryManager {
    // Lock context of the entire database.
    private LockContext dbContext;
    // Disk space manager.
    DiskSpaceManager diskSpaceManager;
    // Buffer manager.
    BufferManager bufferManager;

    // Function to create a new transaction for recovery with a given transaction number.
    private Function<Long, Transaction> newTransaction;
    // Function to update the transaction counter.
    protected Consumer<Long> updateTransactionCounter;
    // Function to get the transaction counter.
    protected Supplier<Long> getTransactionCounter;

    // Log manager
    LogManager logManager;
    // Dirty page table (page number -> recLSN).
    Map<Long, Long> dirtyPageTable = new ConcurrentHashMap<>();
    // Transaction table (transaction number -> entry).
    Map<Long, TransactionTableEntry> transactionTable = new ConcurrentHashMap<>();

    // List of lock requests made during recovery. This is only populated when locking is disabled.
    List<String> lockRequests;

    public ARIESRecoveryManager(LockContext dbContext, Function<Long, Transaction> newTransaction,
                                Consumer<Long> updateTransactionCounter, Supplier<Long> getTransactionCounter) {
        this(dbContext, newTransaction, updateTransactionCounter, getTransactionCounter, false);
    }

    ARIESRecoveryManager(LockContext dbContext, Function<Long, Transaction> newTransaction,
                         Consumer<Long> updateTransactionCounter, Supplier<Long> getTransactionCounter,
                         boolean disableLocking) {
        this.dbContext = dbContext;
        this.newTransaction = newTransaction;
        this.updateTransactionCounter = updateTransactionCounter;
        this.getTransactionCounter = getTransactionCounter;
        this.lockRequests = disableLocking ? new ArrayList<>() : null;
    }

    /**
     * Initializes the log; only called the first time the database is set up.
     *
     * The master record should be added to the log, and a checkpoint should be taken.
     */
    @Override
    public void initialize() {
        this.logManager.appendToLog(new MasterLogRecord(0));
        this.checkpoint();
    }

    /**
     * Sets the buffer/disk managers. This is not part of the constructor because of the cyclic dependency
     * between the buffer manager and recovery manager (the buffer manager must interface with the
     * recovery manager to block page evictions until the log has been flushed, but the recovery
     * manager needs to interface with the buffer manager to write the log and redo changes).
     * @param diskSpaceManager disk space manager
     * @param bufferManager buffer manager
     */
    @Override
    public void setManagers(DiskSpaceManager diskSpaceManager, BufferManager bufferManager) {
        this.diskSpaceManager = diskSpaceManager;
        this.bufferManager = bufferManager;
        this.logManager = new LogManagerImpl(bufferManager);
    }

    // Forward Processing ////////////////////////////////////////////////////////////////////

    /**
     * Called when a new transaction is started.
     *
     * The transaction should be added to the transaction table.
     *
     * @param transaction new transaction
     */
    @Override
    public synchronized void startTransaction(Transaction transaction) {
        this.transactionTable.put(transaction.getTransNum(), new TransactionTableEntry(transaction));
    }

    /**
     * Called when a transaction is about to start committing.
     *
     * A commit record should be emitted, the log should be flushed,
     * and the transaction table and the transaction status should be updated.
     *
     * @param transNum transaction being committed
     * @return LSN of the commit record
     */
    @Override
    public long commit(long transNum) {
        // TODO(hw5): implement
        TransactionTableEntry tte = this.transactionTable.get(transNum);
        assert (tte != null);

        long prevLSN = tte.lastLSN;
        LogRecord commitRecord = new CommitTransactionLogRecord(transNum, prevLSN);
        // emit commit record
        long curLSN = this.logManager.appendToLog(commitRecord);
        // flush log
        this.logManager.flushToLSN(curLSN);
        // update lastLSN
        tte.lastLSN = curLSN;
        // update transaction status
        tte.transaction.setStatus(Transaction.Status.COMMITTING);

        return curLSN;
    }

    /**
     * Called when a transaction is set to be aborted.
     *
     * An abort record should be emitted, and the transaction table and transaction
     * status should be updated. No CLRs should be emitted.
     *
     * @param transNum transaction being aborted
     * @return LSN of the abort record
     */
    @Override
    public long abort(long transNum) {
        // TODO(hw5): implement
        TransactionTableEntry tte = this.transactionTable.get(transNum);
        assert (tte != null);

        long prevLSN = tte.lastLSN;
        LogRecord abortRecord = new AbortTransactionLogRecord(transNum, prevLSN);
        // emit abort record
        long curLSN = this.logManager.appendToLog(abortRecord);
        // update lastLSN
        tte.lastLSN = curLSN;
        // update transaction status
        tte.transaction.setStatus(Transaction.Status.ABORTING);

        return curLSN;
    }

    /**
     * Called when a transaction is cleaning up; this should roll back
     * changes if the transaction is aborting.
     *
     * Any changes that need to be undone should be undone, the transaction should
     * be removed from the transaction table, the end record should be emitted,
     * and the transaction status should be updated.
     *
     * @param transNum transaction to end
     * @return LSN of the end record
     */
    @Override
    public long end(long transNum) {
        // TODO(hw5): implement
        TransactionTableEntry tte = this.transactionTable.get(transNum);
        assert (tte != null);

        long curLSN = tte.lastLSN;

        // transaction status is aborting, need to rollback/ undo
        if (tte.transaction.getStatus().equals(Transaction.Status.ABORTING)) {
            Optional<Long> nextLSNtoUndo = Optional.of(tte.lastLSN);
            LogRecord tobeUndoRecord;
            while (nextLSNtoUndo.isPresent()) {
                tobeUndoRecord = this.logManager.fetchLogRecord(nextLSNtoUndo.get());
                if (tobeUndoRecord.isUndoable()) {
                    Pair<LogRecord, Boolean> CLRandFlush = tobeUndoRecord.undo(curLSN);
                    LogRecord CLR = CLRandFlush.getFirst();
                    Boolean flush = CLRandFlush.getSecond();
                    // emit CLR
                    curLSN = this.logManager.appendToLog(CLR);
                    // flush to disk if needed
                    if (flush) {
                        this.logManager.flushToLSN(curLSN);
                    }
                    // actually undo
                    CLR.redo(diskSpaceManager, bufferManager);

                }
                nextLSNtoUndo = tobeUndoRecord.getPrevLSN();
            }
        }

        LogRecord endRecord = new EndTransactionLogRecord(transNum, curLSN);
        // emit end record
        curLSN = this.logManager.appendToLog(endRecord);
        // remove transaction from transaction table
        this.transactionTable.remove(transNum);
        // update transaction status
        tte.transaction.setStatus(Transaction.Status.COMPLETE);
        // update lastLSN (probably useless
        tte.lastLSN = curLSN;

        return curLSN;
    }

    /**
     * Called before a page is flushed from the buffer cache. This
     * method is never called on a log page.
     *
     * The log should be as far as necessary.
     *
     * @param pageLSN pageLSN of page about to be flushed
     */
    @Override
    public void pageFlushHook(long pageLSN) {
        logManager.flushToLSN(pageLSN);
    }

    /**
     * Called when a page has been updated on disk.
     *
     * As the page is no longer dirty, it should be removed from the
     * dirty page table.
     *
     * @param pageNum page number of page updated on disk
     */
    @Override
    public void diskIOHook(long pageNum) {
        dirtyPageTable.remove(pageNum);
    }

    /**
     * Called when a write to a page happens.
     *
     * This method is never called on a log page. Arguments to the before and after params
     * are guaranteed to be the same length.
     *
     * The appropriate log record should be emitted; if the number of bytes written is
     * too large (larger than BufferManager.EFFECTIVE_PAGE_SIZE / 2), then two records
     * should be written instead: an undo-only record followed by a redo-only record.
     *
     * Both the transaction table and dirty page table should be updated accordingly.
     *
     * @param transNum transaction performing the write
     * @param pageNum page number of page being written
     * @param pageOffset offset into page where write begins
     * @param before bytes starting at pageOffset before the write
     * @param after bytes starting at pageOffset after the write
     * @return LSN of last record written to log
     */
    @Override
    public long logPageWrite(long transNum, long pageNum, short pageOffset, byte[] before,
                             byte[] after) {
        assert (before.length == after.length);

        // TODO(hw5): implement
        TransactionTableEntry tte = this.transactionTable.get(transNum);
        assert (tte != null);
        long prevLSN = tte.lastLSN;
        long curLSN;
        // emit update/write record
        if (after.length <= BufferManager.EFFECTIVE_PAGE_SIZE / 2) {
            LogRecord updateRecord = new UpdatePageLogRecord(transNum, pageNum, prevLSN, pageOffset, before, after);
            curLSN =  this.logManager.appendToLog(updateRecord);
        } else {
            LogRecord undoOnlyRecord = new UpdatePageLogRecord(transNum, pageNum, prevLSN, pageOffset, before, null);
            prevLSN = this.logManager.appendToLog(undoOnlyRecord);
            LogRecord redoOnlyRecord = new UpdatePageLogRecord(transNum, pageNum, prevLSN, pageOffset, null, after);
            curLSN = this.logManager.appendToLog(redoOnlyRecord);
        }

        // update lastLSN
        tte.lastLSN = curLSN;
        // update touched page
        tte.touchedPages.add(pageNum);
        // update dirty page
        if (!dirtyPageTable.containsKey(pageNum)) {
            dirtyPageTable.put(pageNum, curLSN);
        }
        return curLSN;
    }

    /**
     * Called when a new partition is allocated. A log flush is necessary,
     * since changes are visible on disk immediately after this returns.
     *
     * This method should return -1 if the partition is the log partition.
     *
     * The appropriate log record should be emitted, and the log flushed.
     * The transaction table should be updated accordingly.
     *
     * @param transNum transaction requesting the allocation
     * @param partNum partition number of the new partition
     * @return LSN of record or -1 if log partition
     */
    @Override
    public long logAllocPart(long transNum, int partNum) {
        // Ignore if part of the log.
        if (partNum == 0) {
            return -1L;
        }

        TransactionTableEntry transactionEntry = transactionTable.get(transNum);
        assert (transactionEntry != null);

        long prevLSN = transactionEntry.lastLSN;
        LogRecord record = new AllocPartLogRecord(transNum, partNum, prevLSN);
        long LSN = logManager.appendToLog(record);
        // Update lastLSN
        transactionEntry.lastLSN = LSN;
        // Flush log
        logManager.flushToLSN(LSN);
        return LSN;
    }

    /**
     * Called when a partition is freed. A log flush is necessary,
     * since changes are visible on disk immediately after this returns.
     *
     * This method should return -1 if the partition is the log partition.
     *
     * The appropriate log record should be emitted, and the log flushed.
     * The transaction table should be updated accordingly.
     *
     * @param transNum transaction requesting the partition be freed
     * @param partNum partition number of the partition being freed
     * @return LSN of record or -1 if log partition
     */
    @Override
    public long logFreePart(long transNum, int partNum) {
        // Ignore if part of the log.
        if (partNum == 0) {
            return -1L;
        }

        TransactionTableEntry transactionEntry = transactionTable.get(transNum);
        assert (transactionEntry != null);

        long prevLSN = transactionEntry.lastLSN;
        LogRecord record = new FreePartLogRecord(transNum, partNum, prevLSN);
        long LSN = logManager.appendToLog(record);
        // Update lastLSN
        transactionEntry.lastLSN = LSN;
        // Flush log
        logManager.flushToLSN(LSN);
        return LSN;
    }

    /**
     * Called when a new page is allocated. A log flush is necessary,
     * since changes are visible on disk immediately after this returns.
     *
     * This method should return -1 if the page is in the log partition.
     *
     * The appropriate log record should be emitted, and the log flushed.
     * The transaction table should be updated accordingly.
     *
     * @param transNum transaction requesting the allocation
     * @param pageNum page number of the new page
     * @return LSN of record or -1 if log partition
     */
    @Override
    public long logAllocPage(long transNum, long pageNum) {
        // Ignore if part of the log.
        if (DiskSpaceManager.getPartNum(pageNum) == 0) {
            return -1L;
        }

        TransactionTableEntry transactionEntry = transactionTable.get(transNum);
        assert (transactionEntry != null);

        long prevLSN = transactionEntry.lastLSN;
        LogRecord record = new AllocPageLogRecord(transNum, pageNum, prevLSN);
        long LSN = logManager.appendToLog(record);
        // Update lastLSN, touchedPages
        transactionEntry.lastLSN = LSN;
        transactionEntry.touchedPages.add(pageNum);
        // Flush log
        logManager.flushToLSN(LSN);
        return LSN;
    }

    /**
     * Called when a page is freed. A log flush is necessary,
     * since changes are visible on disk immediately after this returns.
     *
     * This method should return -1 if the page is in the log partition.
     *
     * The appropriate log record should be emitted, and the log flushed.
     * The transaction table should be updated accordingly.
     *
     * @param transNum transaction requesting the page be freed
     * @param pageNum page number of the page being freed
     * @return LSN of record or -1 if log partition
     */
    @Override
    public long logFreePage(long transNum, long pageNum) {
        // Ignore if part of the log.
        if (DiskSpaceManager.getPartNum(pageNum) == 0) {
            return -1L;
        }

        TransactionTableEntry transactionEntry = transactionTable.get(transNum);
        assert (transactionEntry != null);

        long prevLSN = transactionEntry.lastLSN;
        LogRecord record = new FreePageLogRecord(transNum, pageNum, prevLSN);
        long LSN = logManager.appendToLog(record);
        // Update lastLSN, touchedPages
        transactionEntry.lastLSN = LSN;
        transactionEntry.touchedPages.add(pageNum);
        dirtyPageTable.remove(pageNum);
        // Flush log
        logManager.flushToLSN(LSN);
        return LSN;
    }

    /**
     * Creates a savepoint for a transaction. Creating a savepoint with
     * the same name as an existing savepoint for the transaction should
     * delete the old savepoint.
     *
     * The appropriate LSN should be recorded so that a partial rollback
     * is possible later.
     *
     * @param transNum transaction to make savepoint for
     * @param name name of savepoint
     */
    @Override
    public void savepoint(long transNum, String name) {
        TransactionTableEntry transactionEntry = transactionTable.get(transNum);
        assert (transactionEntry != null);

        transactionEntry.addSavepoint(name);
    }

    /**
     * Releases (deletes) a savepoint for a transaction.
     * @param transNum transaction to delete savepoint for
     * @param name name of savepoint
     */
    @Override
    public void releaseSavepoint(long transNum, String name) {
        TransactionTableEntry transactionEntry = transactionTable.get(transNum);
        assert (transactionEntry != null);

        transactionEntry.deleteSavepoint(name);
    }

    /**
     * Rolls back transaction to a savepoint.
     *
     * All changes done by the transaction since the savepoint should be undone,
     * in reverse order, with the appropriate CLRs written to log. The transaction
     * status should remain unchanged.
     *
     * @param transNum transaction to partially rollback
     * @param name name of savepoint
     */
    @Override
    public void rollbackToSavepoint(long transNum, String name) {
//        TransactionTableEntry transactionEntry = transactionTable.get(transNum);
//        assert (transactionEntry != null);
//
//        // All of the transaction's changes strictly after the record at LSN should be undone.
//        long LSN = transactionEntry.getSavepoint(name);
        TransactionTableEntry tte = transactionTable.get(transNum);
        assert (tte != null);

        // All of the transaction's changes strictly after the record at LSN should be undone.
        long savepointLSN = tte.getSavepoint(name);
        long curLSN = tte.lastLSN;

        // TODO(hw5): implement
        Optional<Long> nextLSNtoUndo = Optional.of(tte.lastLSN);
        LogRecord tobeUndoRecord;
        while (nextLSNtoUndo.isPresent() && (nextLSNtoUndo.get() != savepointLSN)) {
            tobeUndoRecord = this.logManager.fetchLogRecord(nextLSNtoUndo.get());
            if (tobeUndoRecord.isUndoable()) {
                Pair<LogRecord, Boolean> CLRandFlush = tobeUndoRecord.undo(curLSN);
                LogRecord CLR = CLRandFlush.getFirst();
                Boolean flush = CLRandFlush.getSecond();
                // emit CLR
                curLSN = this.logManager.appendToLog(CLR);
                // flush to disk if needed
                if (flush) {
                    this.logManager.flushToLSN(curLSN);
                }
                // actually undo
                CLR.redo(diskSpaceManager, bufferManager);

            }
            nextLSNtoUndo = tobeUndoRecord.getPrevLSN();
        }
        return;
    }

    /**
     * Create a checkpoint.
     *
     * First, a begin checkpoint record should be written.
     *
     * Then, end checkpoint records should be filled up as much as possible,
     * using recLSNs from the DPT, then status/lastLSNs from the transactions table,
     * and then finally, touchedPages from the transactions table, and written
     * when full (or when done).
     *
     * Finally, the master record should be rewritten with the LSN of the
     * begin checkpoint record.
     */
    @Override
    public void checkpoint() {
        // Create begin checkpoint log record and write to log
        LogRecord beginRecord = new BeginCheckpointLogRecord(getTransactionCounter.get());
        long beginLSN = logManager.appendToLog(beginRecord);

        Map<Long, Long> dpt = new HashMap<>();
        Map<Long, Pair<Transaction.Status, Long>> txnTable = new HashMap<>();
        Map<Long, List<Long>> touchedPages = new HashMap<>();
        int numTouchedPages = 0;

        // TODO(hw5): generate end checkpoint record(s) for DPT and transaction table

        // iterate through the dirtyPageTable and copy the entries.
        for (Map.Entry<Long, Long> entry : dirtyPageTable.entrySet()) {
            long pageID = entry.getKey();
            long recLSN = entry.getValue();
//            boolean fitsAfterAdd = EndCheckpointLogRecord.fitsInOneRecord(dpt.size() + 1,
//                                            txnTable.size(), touchedPages.size(), numTouchedPages);
            boolean fitsAfterAdd = EndCheckpointLogRecord.fitsInOneRecord(dpt.size() + 1,
                    0, 0, 0);
            if (!fitsAfterAdd) {
                LogRecord endRecord = new EndCheckpointLogRecord(dpt, txnTable, touchedPages);
                logManager.appendToLog(endRecord);

                dpt.clear();
//                txnTable.clear();
//                touchedPages.clear();
//                numTouchedPages = 0;
            }

            dpt.put(pageID, recLSN);
        }

        // iterate through the transaction table, and copy the status/lastLSN
        for (Map.Entry<Long, TransactionTableEntry> entry : transactionTable.entrySet()) {
            long transNum = entry.getKey();
            TransactionTableEntry tte = entry.getValue();
//            boolean fitsAfterAdd = EndCheckpointLogRecord.fitsInOneRecord(dpt.size(),
//                    txnTable.size() + 1, touchedPages.size(), numTouchedPages);
            boolean fitsAfterAdd = EndCheckpointLogRecord.fitsInOneRecord(dpt.size(),
                    txnTable.size() + 1, 0, 0);
            if (!fitsAfterAdd) {
                LogRecord endRecord = new EndCheckpointLogRecord(dpt, txnTable, touchedPages);
                logManager.appendToLog(endRecord);

                dpt.clear();
                txnTable.clear();
//                touchedPages.clear();
//                numTouchedPages = 0;
            }

            txnTable.put(transNum, new Pair<>(tte.transaction.getStatus(), tte.lastLSN));
        }

        // iterate through the transaction table, and copy the touched pages
        for (Map.Entry<Long, TransactionTableEntry> entry : transactionTable.entrySet()) {
            long transNum = entry.getKey();
            for (long pageNum : entry.getValue().touchedPages) {
                boolean fitsAfterAdd;
                if (!touchedPages.containsKey(transNum)) {
                    fitsAfterAdd = EndCheckpointLogRecord.fitsInOneRecord(
                                       dpt.size(), txnTable.size(), touchedPages.size() + 1, numTouchedPages + 1);
                } else {
                    fitsAfterAdd = EndCheckpointLogRecord.fitsInOneRecord(
                                       dpt.size(), txnTable.size(), touchedPages.size(), numTouchedPages + 1);
                }

                if (!fitsAfterAdd) {
                    LogRecord endRecord = new EndCheckpointLogRecord(dpt, txnTable, touchedPages);
                    logManager.appendToLog(endRecord);

                    dpt.clear();
                    txnTable.clear();
                    touchedPages.clear();
                    numTouchedPages = 0;
                }

                touchedPages.computeIfAbsent(transNum, t -> new ArrayList<>());
                touchedPages.get(transNum).add(pageNum);
                ++numTouchedPages;
            }
        }

        // Last end checkpoint record
        LogRecord endRecord = new EndCheckpointLogRecord(dpt, txnTable, touchedPages);
        logManager.appendToLog(endRecord);

        // Update master record
        MasterLogRecord masterRecord = new MasterLogRecord(beginLSN);
        logManager.rewriteMasterRecord(masterRecord);
    }

    // TODO(hw5): add any helper methods needed

    @Override
    public void close() {
        this.checkpoint();
        this.logManager.close();
    }

    // Restart Recovery //////////////////////////////////////////////////////////////////////

    /**
     * Called whenever the database starts up, and performs restart recovery. Recovery is
     * complete when the Runnable returned is run to termination. New transactions may be
     * started once this method returns.
     *
     * This should perform the three phases of recovery, and also clean the dirty page
     * table of non-dirty pages (pages that aren't dirty in the buffer manager) between
     * redo and undo, and perform a checkpoint after undo.
     *
     * This method should return right before undo is performed.
     *
     * @return Runnable to run to finish restart recovery
     */
    @Override
    public Runnable restart() {
        // TODO(hw5): implement
        // analysis phase
        this.restartAnalysis();
        // redo phase
        this.restartRedo();
        // check page is actually dirty
        this.bufferManager.iterPageNums((pageID, pageIsDirty) ->
                                                {if (!pageIsDirty) {this.dirtyPageTable.remove(pageID);}});

        return () -> {this.restartUndo(); this.checkpoint();};
    }

    /**
     * This method performs the analysis pass of restart recovery.
     *
     * First, the master record should be read (LSN 0). The master record contains
     * one piece of information: the LSN of the last successful checkpoint.
     *
     * We then begin scanning log records, starting at the begin checkpoint record.
     *
     * If the log record is for a transaction operation:
     * - update the transaction table
     * - if it's page-related (as opposed to partition-related),
     *   - add to touchedPages
     *   - acquire X lock
     *   - update DPT (alloc/free/undoalloc/undofree always flushes changes to disk)
     *
     * If the log record is for a change in transaction status:
     * - clean up transaction (Transaction#cleanup) if END_TRANSACTION ?
     * - update transaction status to COMMITTING/RECOVERY_ABORTING/COMPLETE
     * - update the transaction table
     *
     * If the log record is a begin_checkpoint record:
     * - Update the transaction counter? take the getmaxtransactionnumber
     *
     * If the log record is an end_checkpoint record:
     * - Copy all entries of checkpoint DPT (replace existing entries if any)
     * - Update lastLSN to be the larger of the existing entry's (if any) and the checkpoint's;
     *   add to transaction table if not already present.
     * - Add page numbers from checkpoint's touchedPages to the touchedPages sets in the
     *   transaction table if the transaction has not finished yet, and acquire X locks.
     *
     * Then, cleanup and end transactions that are in the COMMITING state, and
     * move all transactions in the RUNNING state to RECOVERY_ABORTING.
     */
    void restartAnalysis() {
        // Read master record
        LogRecord record = logManager.fetchLogRecord(0L);
        assert (record != null);
        // Type casting
        assert (record.getType() == LogType.MASTER);
        MasterLogRecord masterRecord = (MasterLogRecord) record;
        // Get start checkpoint LSN
        long LSN = masterRecord.lastCheckpointLSN;

        // TODO(hw5): implement
        // iterate through records start from last checkpoint
        Iterator<LogRecord> iter = this.logManager.scanFrom(LSN);
        while(iter.hasNext()) {
            LogRecord r = iter.next();
            LogType logType = r.getType();

            // a transaction operation
            if (r.getTransNum().isPresent()) {
                long transNum = r.getTransNum().get();
                // (create) update transaction lastLSN
                if (!this.transactionTable.containsKey(transNum)) {
                    startTransaction(this.newTransaction.apply(transNum));
                }
                TransactionTableEntry tte = this.transactionTable.get(transNum);
                tte.lastLSN = r.getLSN();
                if (r.getPageNum().isPresent()) { // involve page
                    long pageID = r.getPageNum().get();
                    // add to touched page
                    tte.touchedPages.add(pageID);
                    // acquire X lock
                    this.acquireTransactionLock(tte.transaction, getPageLockContext(pageID), LockType.X);
                    // update DTP (add dirty page to DTP ,or delete page from DTP if they are already been flushed into disk
                    if (logType.equals(LogType.ALLOC_PAGE) || logType.equals(LogType.UNDO_ALLOC_PAGE)
                            || logType.equals(LogType.FREE_PAGE) || logType.equals(LogType.UNDO_FREE_PAGE)) {
                        this.dirtyPageTable.remove(pageID);
                    } else if (!this.dirtyPageTable.containsKey(pageID)){
                        this.dirtyPageTable.put(pageID, r.getLSN());
                    }
                }


                // transaction status change
                Transaction tran = tte.transaction;
                if (logType.equals(LogType.END_TRANSACTION)) {
                    tran.cleanup();
                    tran.setStatus(Transaction.Status.COMPLETE);
                    this.transactionTable.remove(transNum);
                } else if (logType.equals(LogType.COMMIT_TRANSACTION)) {
                    tran.setStatus(Transaction.Status.COMMITTING);
                } else if (logType.equals(LogType.ABORT_TRANSACTION)) {
                    tran.setStatus(Transaction.Status.RECOVERY_ABORTING);
                }
            }

            // checkpoint record
            if (logType.equals(LogType.BEGIN_CHECKPOINT)) {
                if (r.getMaxTransactionNum().isPresent()) {
                    long maxTransNum = Math.max(this.getTransactionCounter.get(), r.getMaxTransactionNum().get());
                    this.updateTransactionCounter.accept(maxTransNum);
                }
            } else if (logType.equals(LogType.END_CHECKPOINT)) {
                for (Map.Entry<Long, Long> entry: r.getDirtyPageTable().entrySet()) {
                    long pageID = entry.getKey();
                    long recLSN = entry.getValue();
                    this.dirtyPageTable.put(pageID, recLSN);
                }

                for (Map.Entry<Long, Pair<Transaction.Status, Long>> entry: r.getTransactionTable().entrySet()) {
                    long transNum = entry.getKey();
                    //long entryLastLSN = entry.getValue().getSecond();
                    if (!this.transactionTable.containsKey(transNum)) {
                        startTransaction(this.newTransaction.apply(transNum));
                    }
                    TransactionTableEntry tte = this.transactionTable.get(transNum);
//                    tte.lastLSN = Math.max(tte.lastLSN, entryLastLSN);
                    tte.lastLSN = Math.max(tte.lastLSN, r.getLSN());
                }

                for (Map.Entry<Long, List<Long>> entry: r.getTransactionTouchedPages().entrySet()) {
                    long transNum = entry.getKey();
                    List<Long> touchedP = entry.getValue();
                    TransactionTableEntry tte = this.transactionTable.get(transNum);
                    tte.touchedPages.addAll(touchedP);
                    if (!tte.transaction.getStatus().equals(Transaction.Status.COMPLETE)) { // or check if it's in the table? from piazza
                        for (long pageNum: touchedP) {
                            this.acquireTransactionLock(tte.transaction, this.getPageLockContext(pageNum), LockType.X);
                        }
                    }
                }
            }
        }

        // ending transactions
        for (Map.Entry<Long, TransactionTableEntry> entry: this.transactionTable.entrySet()) {
            long transNum = entry.getKey();
            TransactionTableEntry tte = entry.getValue();
            Transaction trans = tte.transaction;
            if (trans.getStatus().equals(Transaction.Status.COMPLETE)) {
                trans.cleanup();
                this.transactionTable.remove(transNum);
            } else if (trans.getStatus().equals(Transaction.Status.RUNNING)) {
                long prevLSN = tte.lastLSN;
                LogRecord abortRecord = new AbortTransactionLogRecord(transNum, prevLSN);
                // emit abort record, update lastLSN
                tte.lastLSN = this.logManager.appendToLog(abortRecord);
//                this.logManager.appendToLog(abortRecord);
                trans.setStatus(Transaction.Status.RECOVERY_ABORTING);
            }
        }
        return;
    }

    /**
     * This method performs the redo pass of restart recovery.
     *
     * First, determine the starting point for REDO from the DPT.
     *
     * Then, scanning from the starting point, if the record is redoable and
     * - about a page (Update/Alloc/Free/Undo..Page) in the DPT with LSN >= recLSN,
     *   the page is fetched from disk and the pageLSN is checked, and the record is redone.
     * - about a partition (Alloc/Free/Undo..Part), redo it.
     */
    void restartRedo() {
        // TODO(hw5): implement
        if (this.dirtyPageTable.isEmpty()) {
            return;
        }
        long startLSN = Long.MAX_VALUE;
        for (Map.Entry<Long, Long> entry: this.dirtyPageTable.entrySet()) {
            startLSN = Math.min(startLSN, entry.getValue());
        }

        Iterator<LogRecord> iter = this.logManager.scanFrom(startLSN);
        while(iter.hasNext()) {
            LogRecord r = iter.next();
            if (!r.isRedoable()) {
                continue;
            }

            if (r.getPartNum().isPresent()) { // about a partition
                r.redo(diskSpaceManager, bufferManager);
            } else if (r.getPageNum().isPresent()) { // about a page
                long pageID = r.getPageNum().get();
                boolean noLessThanRecLSN = this.dirtyPageTable.containsKey(pageID) &&
                        (r.getLSN() >= this.dirtyPageTable.get(pageID));
                Page page = this.bufferManager.fetchPage(this.getPageLockContext(pageID).parentContext(),
                                                         pageID, false); // last parameter need to reconsider
                long pageLSN = Long.MAX_VALUE;
                try{
                    pageLSN = page.getPageLSN();
                } finally {
                    page.unpin();
                }
                boolean greaterThanPageLSN = r.getLSN() > pageLSN;
                if (noLessThanRecLSN && greaterThanPageLSN) {
                    r.redo(diskSpaceManager, bufferManager);
                }
            }
        }
        return;
    }

    /**
     * This method performs the redo pass of restart recovery.

     * First, a priority queue is created sorted on lastLSN of all aborting transactions.
     *
     * Then, always working on the largest LSN in the priority queue until we are done,
     * - if the record is undoable, undo it, emit the appropriate CLR, and update tables accordingly;
     * - replace the entry in the set should be replaced with a new one, using the undoNextLSN
     *   (or prevLSN if none) of the record; and
     * - if the new LSN is 0, end the transaction and remove it from the queue and transaction table.
     */
    void restartUndo() {
        // TODO(hw5): implement
        PriorityQueue<Pair<Long, Transaction>> nextToUndo = new PriorityQueue<>(this.transactionTable.size(),
                new ARIESRecoveryManager.PairFirstReverseComparator<>());


        for (Map.Entry<Long, TransactionTableEntry> entry: this.transactionTable.entrySet()) {
            long lastLSN = entry.getValue().lastLSN;
            Transaction trans = entry.getValue().transaction;
            if (trans.getStatus().equals(Transaction.Status.RECOVERY_ABORTING)) {
                nextToUndo.add(new Pair<>(lastLSN, trans));
            }
        }

        HashMap<Transaction, Long> curLSN = new HashMap<>();
        for (Pair<Long, Transaction> pair: nextToUndo) {
            curLSN.put(pair.getSecond(), pair.getFirst());
        }

        if (nextToUndo.isEmpty()) {
            return;
        }

        while (!nextToUndo.isEmpty()) {
            Pair<Long, Transaction> entry = nextToUndo.poll();
            long LSN = entry.getFirst();
            Transaction trans = entry.getSecond();
            LogRecord r = this.logManager.fetchLogRecord(LSN);

            if (r.isUndoable()) {
//                Pair<LogRecord, Boolean> CLRandFlush = r.undo(LSN);
                Pair<LogRecord, Boolean> CLRandFlush = r.undo(curLSN.get(trans));
                LogRecord CLR = CLRandFlush.getFirst();
                Boolean flush = CLRandFlush.getSecond();
                // emit CLR
                long clrLSN = this.logManager.appendToLog(CLR);
                // flush to disk if needed
                if (flush) {
                    this.logManager.flushToLSN(clrLSN);
                }
                // update transaction table
                this.transactionTable.get(trans.getTransNum()).lastLSN = clrLSN;
                curLSN.put(trans, clrLSN);
                // actually undo
                CLR.redo(diskSpaceManager, bufferManager);

            }

            long newLSN;
            if (r.getUndoNextLSN().isPresent()) {
                newLSN = r.getUndoNextLSN().get();
            } else {
                newLSN = r.getPrevLSN().get();
            }
            nextToUndo.add(new Pair<>(newLSN, trans));

            if (newLSN == 0) {
                long transNum = trans.getTransNum();
                LogRecord endRecord = new EndTransactionLogRecord(transNum, r.getPrevLSN().get());
                // emit end record
                this.logManager.appendToLog(endRecord);
                // remove transaction from transaction table
                this.transactionTable.remove(transNum);
                // update transaction status
                trans.setStatus(Transaction.Status.COMPLETE);
                // remove transaction from the queue ??? if I only add it back to the queue when not meet this condition
                nextToUndo.remove(new Pair<>(newLSN, trans));
            }
        }
        return;
    }

    // TODO(hw5): add any helper methods needed


    // Helpers ///////////////////////////////////////////////////////////////////////////////

    /**
     * Returns the lock context for a given page number.
     * @param pageNum page number to get lock context for
     * @return lock context of the page
     */
    private LockContext getPageLockContext(long pageNum) {
        int partNum = DiskSpaceManager.getPartNum(pageNum);
        return this.dbContext.childContext(partNum).childContext(pageNum);
    }

    /**
     * Locks the given lock context with the specified lock type under the specified transaction,
     * acquiring locks on ancestors as needed.
     * @param transaction transaction to request lock for
     * @param lockContext lock context to lock
     * @param lockType type of lock to request
     */
    private void acquireTransactionLock(Transaction transaction, LockContext lockContext,
                                        LockType lockType) {
        acquireTransactionLock(transaction.getTransactionContext(), lockContext, lockType);
    }

    /**
     * Locks the given lock context with the specified lock type under the specified transaction,
     * acquiring locks on ancestors as needed.
     * @param transactionContext transaction context to request lock for
     * @param lockContext lock context to lock
     * @param lockType type of lock to request
     */
    private void acquireTransactionLock(TransactionContext transactionContext,
                                        LockContext lockContext, LockType lockType) {
        TransactionContext.setTransaction(transactionContext);
        try {
            if (lockRequests == null) {
                LockUtil.ensureSufficientLockHeld(lockContext, lockType);
            } else {
                lockRequests.add("request " + transactionContext.getTransNum() + " " + lockType + "(" +
                                 lockContext.getResourceName() + ")");
            }
        } finally {
            TransactionContext.unsetTransaction();
        }
    }

    /**
     * Comparator for Pair<A, B> comparing only on the first element (type A), in reverse order.
     */
    private static class PairFirstReverseComparator<A extends Comparable<A>, B> implements
        Comparator<Pair<A, B>> {
        @Override
        public int compare(Pair<A, B> p0, Pair<A, B> p1) {
            return p1.getFirst().compareTo(p0.getFirst());
        }
    }
}
