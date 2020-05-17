package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.common.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LockContext wraps around LockManager to provide the hierarchical structure
 * of multigranularity locking. Calls to acquire/release/etc. locks should
 * be mostly done through a LockContext, which provides access to locking
 * methods at a certain point in the hierarchy (database, table X, etc.)
 */
public class LockContext {
    // You should not remove any of these fields. You may add additional fields/methods as you see fit.

    // The underlying lock manager.
    protected final LockManager lockman;
    // The parent LockContext object, or null if this LockContext is at the top of the hierarchy.
    protected final LockContext parent;
    // The name of the resource this LockContext represents.
    protected ResourceName name;
    // Whether this LockContext is readonly. If a LockContext is readonly, acquire/release/promote/escalate should
    // throw an UnsupportedOperationException.
    protected boolean readonly;
    // A mapping between transaction numbers, and the number of locks on children of this LockContext
    // that the transaction holds.
    protected final Map<Long, Integer> numChildLocks;
    // The number of children that this LockContext has, if it differs from the number of times
    // LockContext#childContext was called with unique parameters: for a table, we do not
    // explicitly create a LockContext for every page (we create them as needed), but
    // the capacity should be the number of pages in the table, so we use this
    // field to override the return value for capacity().
    protected int capacity;

    // You should not modify or use this directly.
    protected final Map<Long, LockContext> children;

    // Whether or not any new child LockContexts should be marked readonly.
    protected boolean childLocksDisabled;

    public LockContext(LockManager lockman, LockContext parent, Pair<String, Long> name) {
        this(lockman, parent, name, false);
    }

    protected LockContext(LockManager lockman, LockContext parent, Pair<String, Long> name,
                          boolean readonly) {
        this.lockman = lockman;
        this.parent = parent;
        if (parent == null) {
            this.name = new ResourceName(name);
        } else {
            this.name = new ResourceName(parent.getResourceName(), name);
        }
        this.readonly = readonly;
        this.numChildLocks = new ConcurrentHashMap<>();
        this.capacity = -1;
        this.children = new ConcurrentHashMap<>();
        this.childLocksDisabled = readonly;
    }

    /**
     * Gets a lock context corresponding to NAME from a lock manager.
     */
    public static LockContext fromResourceName(LockManager lockman, ResourceName name) {
        Iterator<Pair<String, Long>> names = name.getNames().iterator();
        LockContext ctx;
        Pair<String, Long> n1 = names.next();
        ctx = lockman.context(n1.getFirst(), n1.getSecond());
        while (names.hasNext()) {
            Pair<String, Long> p = names.next();
            ctx = ctx.childContext(p.getFirst(), p.getSecond());
        }
        return ctx;
    }

    /**
     * Get the name of the resource that this lock context pertains to.
     */
    public ResourceName getResourceName() {
        return name;
    }

    /**
     * Acquire a LOCKTYPE lock, for transaction TRANSACTION.
     *
     * Note: you *must* make any necessary updates to numChildLocks, or
     * else calls to LockContext#saturation will not work properly.
     *
     * @throws InvalidLockException if the request is invalid
     * @throws DuplicateLockRequestException if a lock is already held by TRANSACTION
     * @throws UnsupportedOperationException if context is readonly
     */
    public void acquire(TransactionContext transaction, LockType lockType)
    throws InvalidLockException, DuplicateLockRequestException {
        // TODO(hw4_part1): implement
        if (this.readonly) {
            throw new UnsupportedOperationException("this resource is read only");
        }
        LockContext parentCon = this.parentContext();
        if (parentCon!= null) {
            LockType parentLockType = parentCon.getEffectiveLockType(transaction);
            if (!LockType.canBeParentLock(parentLockType, lockType)) {
                throw new InvalidLockException("this lockType cannot be acquired under current parent lockType");
            }
        }

        for (Lock lock: this.lockman.getLocks(transaction)) {
            if (lock.name.isDescendantOf(this.getResourceName())) {
                if (!LockType.canBeParentLock(lockType, lock.lockType)) {
                    throw new InvalidLockException("this lockType cannot be acquired with current children lockTypes");
                }
            }
        }

        // throw DuplicationLockRequestException in LockManager acquire call
        this.lockman.acquire(transaction, name, lockType);

        if (parentCon != null) {
            int curParentChildLocks = parentCon.numChildLocks.getOrDefault(transaction.getTransNum(), 0);
            parentCon.numChildLocks.put(transaction.getTransNum(), curParentChildLocks + 1);
            // do i need to update parent contest's children?
        }
        return;
    }

    /**
     * Release TRANSACTION's lock on NAME.
     *
     * Note: you *must* make any necessary updates to numChildLocks, or
     * else calls to LockContext#saturation will not work properly.
     *
     * @throws NoLockHeldException if no lock on NAME is held by TRANSACTION
     * @throws InvalidLockException if the lock cannot be released (because doing so would
     *  violate multigranularity locking constraints)
     * @throws UnsupportedOperationException if context is readonly
     */
    public void release(TransactionContext transaction)
    throws NoLockHeldException, InvalidLockException {
        // TODO(hw4_part1): implement
        if (this.readonly) {
            throw new UnsupportedOperationException("this resource is read only");
        }

        for (Lock lock: this.lockman.getLocks(transaction)) {
            if (lock.name.isDescendantOf(this.getResourceName())) {
                if (!lock.lockType.equals(LockType.NL)) {
                    throw new InvalidLockException("this lockType cannot be released because it's children haven't " +
                            "been released yet.");
                }
            }
        }

        // throw NoLockHeldException in LockManager release call
        this.lockman.release(transaction, this.getResourceName());
        LockContext parentCon = this.parentContext();
        if (parentCon != null) {
            int curParentChildLocks = parentCon.numChildLocks.getOrDefault(transaction.getTransNum(), 0);
            parentCon.numChildLocks.put(transaction.getTransNum(), curParentChildLocks - 1);
        }
        return;
    }

    /**
     * Promote TRANSACTION's lock to NEWLOCKTYPE. For promotion to SIX from IS/IX/S, all S,
     * IS, and SIX locks on descendants must be simultaneously released.
     *
     * Note: you *must* make any necessary updates to numChildLocks, or
     * else calls to LockContext#saturation will not work properly.
     *
     * @throws DuplicateLockRequestException if TRANSACTION already has a NEWLOCKTYPE lock
     * @throws NoLockHeldException if TRANSACTION has no lock
     * @throws InvalidLockException if the requested lock type is not a promotion or promoting
     * would cause the lock manager to enter an invalid state (e.g. IS(parent), X(child)). A promotion
     * from lock type A to lock type B is valid if B is substitutable
     * for A and B is not equal to A, or if B is SIX and A is IS/IX/S, and invalid otherwise.
     * @throws UnsupportedOperationException if context is readonly
     */
    public void promote(TransactionContext transaction, LockType newLockType)
    throws DuplicateLockRequestException, NoLockHeldException, InvalidLockException {
        // TODO(hw4_part1): implement
        if (this.readonly) {
            throw new UnsupportedOperationException("this resource is read only");
        }
        LockContext parentCon = this.parentContext();
        if (parentCon!= null) {
            LockType parentLockType = parentCon.getEffectiveLockType(transaction);
            if (!LockType.canBeParentLock(parentLockType, newLockType)) {
                throw new InvalidLockException("this lockType cannot be promote under current parent lockType");
            }
        }
        // throw DuplicationLockRequestException, NoLockHeldException, InvalidLockException in LockManager promote call

        if (newLockType.equals(LockType.SIX)) {
            List<ResourceName> toBeReleased = new ArrayList<>();
            List<LockContext> setChildNumToZero = new ArrayList<>();
            LockContext childCon;
            for (Lock lock: this.lockman.getLocks(transaction)) {
                if (lock.name.isDescendantOf(this.getResourceName())) {
                    if (lock.lockType.equals(LockType.S) || lock.lockType.equals(LockType.IS)) {
                        childCon = LockContext.fromResourceName(this.lockman, lock.name);
                        toBeReleased.add(lock.name);
                        setChildNumToZero.add(childCon);
                    }
                }
            }
            toBeReleased.add(0, this.getResourceName());
            setChildNumToZero.add(0, this);
            this.lockman.acquireAndRelease(transaction, name, newLockType, toBeReleased);
            for (LockContext lc: setChildNumToZero) {
                lc.numChildLocks.put(transaction.getTransNum(), 0);
            }

        } else {
            this.lockman.promote(transaction, name, newLockType);
        }
        return;
    }

    /**
     * Escalate TRANSACTION's lock from descendants of this context to this level, using either
     * an S or X lock. There should be no descendant locks after this
     * call, and every operation valid on descendants of this context before this call
     * must still be valid. You should only make *one* mutating call to the lock manager,
     * and should only request information about TRANSACTION from the lock manager.
     *
     * For example, if a transaction has the following locks:
     *      IX(database) IX(table1) S(table2) S(table1 page3) X(table1 page5)
     * then after table1Context.escalate(transaction) is called, we should have:
     *      IX(database) X(table1) S(table2)
     *
     * You should not make any mutating calls if the locks held by the transaction do not change
     * (such as when you call escalate multiple times in a row).
     *
     * Note: you *must* make any necessary updates to numChildLocks of all relevant contexts, or
     * else calls to LockContext#saturation will not work properly.
     *
     * @throws NoLockHeldException if TRANSACTION has no lock at this level
     * @throws UnsupportedOperationException if context is readonly
     */
    public void escalate(TransactionContext transaction) throws NoLockHeldException {
        // TODO(hw4_part1): implement
        if (this.readonly) {
            throw new UnsupportedOperationException("this resource is read only");
        }
        // throw NoLockHeldException in LockManager acquire and release call
        LockType thisLevelEffectiveLockType = this.getEffectiveLockType(transaction);
        if (thisLevelEffectiveLockType.equals(LockType.NL)) {
            throw new NoLockHeldException("transaction has no lock at this level");
        }
        if (thisLevelEffectiveLockType.equals(LockType.S) || thisLevelEffectiveLockType.equals(LockType.X)) {
            return;
        }

        List<ResourceName> toBeReleased = new ArrayList<>();
        LockType escalateTo = LockType.S;
        if (this.getEffectiveLockType(transaction).equals(LockType.IX)) {
            escalateTo = LockType.X;
        } else {
            for (Lock lock: this.lockman.getLocks(transaction)) {
                if (lock.name.isDescendantOf(this.getResourceName())) {
                    if (!lock.lockType.equals(LockType.NL)) {
                        toBeReleased.add(lock.name);
                        if (lock.lockType.equals(LockType.IX) || lock.lockType.equals(LockType.X) ||
                                lock.lockType.equals(LockType.SIX)) {
                            escalateTo = LockType.X;
                        }
                    }
                }
            }

        }
        toBeReleased.add(0, this.getResourceName());
        this.lockman.acquireAndRelease(transaction, name, escalateTo, toBeReleased);
        this.numChildLocks.put(transaction.getTransNum(), 0);
        return;
    }

    /**
     * Gets the type of lock that the transaction has at this level, either implicitly
     * (e.g. explicit S lock at higher level implies S lock at this level) or explicitly.
     * Returns NL if there is no explicit nor implicit lock.
     */
    public LockType getEffectiveLockType(TransactionContext transaction) {
        if (transaction == null) {
            return LockType.NL;
        }
        // TODO(hw4_part1): implement
        LockType explicitLockType = this.getExplicitLockType(transaction);
        if (!explicitLockType.equals(LockType.NL)) {
            return explicitLockType;
        } else {
            LockContext parentCon = this.parent;
            LockType parentLockType;
            while (parentCon != null) {
                parentLockType = parentCon.getEffectiveLockType(transaction);
                switch (parentLockType) {
                    case S: return LockType.S;
                    case X: return LockType.X;
                    case IS: return LockType.NL;
                    case IX: return LockType.NL;
                    case SIX: return LockType.S;
                    case NL: parentCon = parentCon.parent;
                             break;
                    default: throw new UnsupportedOperationException("bad lock type");
                }
            }
        }
        return LockType.NL;
    }

    /**
     * Get the type of lock that TRANSACTION holds at this level, or NL if no lock is held at this level.
     */
    public LockType getExplicitLockType(TransactionContext transaction) {
        if (transaction == null) {
            return LockType.NL;
        }
        // TODO(hw4_part1): implement
        return this.lockman.getLockType(transaction, name);
    }

    /**
     * Disables locking descendants. This causes all new child contexts of this context
     * to be readonly. This is used for indices and temporary tables (where
     * we disallow finer-grain locks), the former due to complexity locking
     * B+ trees, and the latter due to the fact that temporary tables are only
     * accessible to one transaction, so finer-grain locks make no sense.
     */
    public void disableChildLocks() {
        this.childLocksDisabled = true;
    }

    /**
     * Gets the parent context.
     */
    public LockContext parentContext() {
        return parent;
    }

    /**
     * Gets the context for the child with name NAME (with a readable version READABLE).
     */
    public synchronized LockContext childContext(String readable, long name) {
        LockContext temp = new LockContext(lockman, this, new Pair<>(readable, name),
                                           this.childLocksDisabled || this.readonly);
        LockContext child = this.children.putIfAbsent(name, temp);
        if (child == null) {
            child = temp;
        }
        if (child.name.getCurrentName().getFirst() == null && readable != null) {
            child.name = new ResourceName(this.name, new Pair<>(readable, name));
        }
        return child;
    }

    /**
     * Gets the context for the child with name NAME.
     */
    public synchronized LockContext childContext(long name) {
        return childContext(Long.toString(name), name);
    }

    /**
     * Sets the capacity (number of children).
     */
    public synchronized void capacity(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Gets the capacity. Defaults to number of child contexts if never explicitly set.
     */
    public synchronized int capacity() {
        return this.capacity < 0 ? this.children.size() : this.capacity;
    }

    /**
     * Gets the saturation (number of locks held on children / number of children) for
     * a single transaction. Saturation is 0 if number of children is 0.
     */
    public double saturation(TransactionContext transaction) {
        if (transaction == null || capacity() == 0) {
            return 0.0;
        }
        return ((double) numChildLocks.getOrDefault(transaction.getTransNum(), 0)) / capacity();
    }

    @Override
    public String toString() {
        return "LockContext(" + name.toString() + ")";
    }
}

