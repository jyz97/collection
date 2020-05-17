package edu.berkeley.cs186.database.query;

import java.util.*;

import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.common.iterator.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.table.Record;

class SortMergeOperator extends JoinOperator {
    SortMergeOperator(QueryOperator leftSource,
                      QueryOperator rightSource,
                      String leftColumnName,
                      String rightColumnName,
                      TransactionContext transaction) {
        super(leftSource, rightSource, leftColumnName, rightColumnName, transaction, JoinType.SORTMERGE);

        this.stats = this.estimateStats();
        this.cost = this.estimateIOCost();
    }

    @Override
    public Iterator<Record> iterator() {
        return new SortMergeIterator();
    }

    @Override
    public int estimateIOCost() {
        //does nothing
        return 0;
    }

    /**
     * An implementation of Iterator that provides an iterator interface for this operator.
     *
     * Before proceeding, you should read and understand SNLJOperator.java
     *    You can find it in the same directory as this file.
     *
     * Word of advice: try to decompose the problem into distinguishable sub-problems.
     *    This means you'll probably want to add more methods than those given (Once again,
     *    SNLJOperator.java might be a useful reference).
     *
     */
    private class SortMergeIterator extends JoinIterator {
        /**
        * Some member variables are provided for guidance, but there are many possible solutions.
        * You should implement the solution that's best for you, using any member variables you need.
        * You're free to use these member variables, but you're not obligated to.
        */
        private BacktrackingIterator<Record> leftIterator;
        private BacktrackingIterator<Record> rightIterator;
        private Record leftRecord = null;
        private Record nextRecord;
        private Record rightRecord = null;
        private boolean marked;

        private SortMergeIterator() {
            super();
            // TODO(hw3_part1): implement
            SortOperator left = new SortOperator(SortMergeOperator.this.getTransaction(), this.getLeftTableName(),
                    new LeftRecordComparator());
            SortOperator right = new SortOperator(SortMergeOperator.this.getTransaction(), this.getRightTableName(),
                    new RightRecordComparator());
            this.leftIterator = SortMergeOperator.this.getTableIterator(left.sort());
            this.rightIterator = SortMergeOperator.this.getTableIterator(right.sort());
            if (this.leftIterator.hasNext()) {
                this.leftRecord = this.leftIterator.next();
            }
            if (this.rightIterator.hasNext()) {
                this.rightRecord = this.rightIterator.next();
            }
            this.marked = false;
            try {
                this.fetchNextRecord();
            } catch (NoSuchElementException e) {
                this.nextRecord = null;
            }

        }

        private void fetchNextRecord() {
//            if ((this.leftRecord == null) || (this.rightRecord == null)) {
//            if (this.leftRecord == null) {
//            if ((this.leftRecord == null) || ((this.rightRecord == null) && (!this.marked))) {
//                throw new NoSuchElementException("No new record to fetch");
//            }
            this.nextRecord = null;
            do {
                if ((this.leftRecord == null) || ((this.rightRecord == null) && (!this.marked))) {
                    throw new NoSuchElementException("No new record to fetch");
                }
                if (!this.marked) {
                    while (this.comp(this.leftRecord, this.rightRecord) < 0) {
                        this.advanceLeft();
                    }
                    while (this.comp(this.leftRecord, this.rightRecord) > 0) {
                        this.advanceRight();
                    }
                    this.rightIterator.markPrev();
                    this.marked = true;
                }

                if ((this.rightRecord != null) && (this.comp(this.leftRecord, this.rightRecord) == 0)) {
                    this.nextRecord = this.JoinRecords();
                    this.advanceRight();
                } else {
                    this.resetRightRecord();
                    this.advanceLeft();
                    this.marked = false;
                }
            } while (!hasNext());
        }

        private void advanceLeft() {
            this.leftRecord = this.leftIterator.hasNext() ? this.leftIterator.next() : null;
        }

        private void advanceRight() {
            if (this.rightIterator.hasNext()) {
                this.rightRecord = this.rightIterator.next();
            } else {
                this.rightRecord = null;
            }
        }

        private void resetRightRecord() {
            this.rightIterator.reset();
            this.rightRecord = this.rightIterator.hasNext() ? this.rightIterator.next() : null;
        }

        private int comp(Record r1, Record r2) {
            DataBox leftJoinValue = this.leftRecord.getValues().get(SortMergeOperator.this.getLeftColumnIndex());
            DataBox rightJoinValue = rightRecord.getValues().get(SortMergeOperator.this.getRightColumnIndex());
            return leftJoinValue.compareTo(rightJoinValue);
        }

        private Record JoinRecords() {
            List<DataBox> leftValues = new ArrayList<>(this.leftRecord.getValues());
            List<DataBox> rightValues = new ArrayList<>(this.rightRecord.getValues());
            leftValues.addAll(rightValues);
            return new Record(leftValues);
        }




        /**
         * Checks if there are more record(s) to yield
         *
         * @return true if this iterator has another record to yield, otherwise false
         */
        @Override
        public boolean hasNext() {
            // TODO(hw3_part1): implement

//            return false;
            return this.nextRecord != null;
        }

        /**
         * Yields the next record of this iterator.
         *
         * @return the next Record
         * @throws NoSuchElementException if there are no more Records to yield
         */
        @Override
        public Record next() {
            // TODO(hw3_part1): implement

//            throw new NoSuchElementException();
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            Record nextRecord = this.nextRecord;
            try {
                this.fetchNextRecord();
            } catch (NoSuchElementException e) {
                this.nextRecord = null;
            }
            return nextRecord;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private class LeftRecordComparator implements Comparator<Record> {
            @Override
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getLeftColumnIndex()).compareTo(
                           o2.getValues().get(SortMergeOperator.this.getLeftColumnIndex()));
            }
        }

        private class RightRecordComparator implements Comparator<Record> {
            @Override
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getRightColumnIndex()).compareTo(
                           o2.getValues().get(SortMergeOperator.this.getRightColumnIndex()));
            }
        }
    }
}
