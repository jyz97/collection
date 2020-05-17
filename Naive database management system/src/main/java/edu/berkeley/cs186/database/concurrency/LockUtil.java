package edu.berkeley.cs186.database.concurrency;

import edu.berkeley.cs186.database.TransactionContext;

import java.util.Stack;

/**
 * LockUtil is a declarative layer which simplifies multigranularity lock acquisition
 * for the user (you, in the second half of Part 2). Generally speaking, you should use LockUtil
 * for lock acquisition instead of calling LockContext methods directly.
 */
public class LockUtil {
    /**
     * Ensure that the current transaction can perform actions requiring LOCKTYPE on LOCKCONTEXT.
     *
     * This method should promote/escalate as needed, but should only grant the least
     * permissive set of locks needed.
     *
     * lockType is guaranteed to be one of: S, X, NL.
     *
     * If the current transaction is null (i.e. there is no current transaction), this method should do nothing.
     */
    public static void ensureSufficientLockHeld(LockContext lockContext, LockType lockType) {
        // TODO(hw4_part2): implement

        TransactionContext transaction = TransactionContext.getTransaction(); // current transaction

        // If the current transaction is null, do nothing
        if (transaction == null) { return;}

        // if ensure NL, do nothing
        if (lockType.equals(LockType.NL)) { return;}

        // if have substitutable effective lockType already, do nothing
        LockType currentLock = lockContext.getEffectiveLockType(transaction);
        if (LockType.substitutable(currentLock, lockType)) {
            return;
        }

        // part1: make sure have appropriate parent lockType
        LockType parentNeed = LockType.parentLock(lockType);
        LockContext parentCon = lockContext.parentContext();
        Stack<LockContext> parentsToUpdate = new Stack<>();
        while ( parentCon != null) {
            LockType parent = parentCon.getEffectiveLockType(transaction);
            if (LockType.canBeParentLock(parent, lockType)) {
                break;
            }
            parentsToUpdate.push(parentCon);
            parentCon = parentCon.parentContext();
        }

        while (parentsToUpdate.size() > 0) {
            LockContext paCon = parentsToUpdate.pop();
            LockType paLock = paCon.getExplicitLockType(transaction);
            if (paLock.equals(LockType.NL)) {
                paCon.acquire(transaction, parentNeed);
            } else if (paLock.equals(LockType.IS)) {
                paCon.promote(transaction, LockType.IX);
            } else { // == S
                paCon.promote(transaction, LockType.SIX);
            }
        }

        // part2: make sure the new lockType fits it's children
        if (currentLock.equals(LockType.NL)) {
            // means this lock shouldn't have children that have effective locks
            lockContext.acquire(transaction, lockType);
            return;
        }

        if (lockType.equals(LockType.X)) { // ensure X
            lockContext.escalate(transaction);
//            LockType afterEscalate = lockContext.getEffectiveLockType(transaction);
//            if (afterEscalate.equals(LockType.S)) {
//                // didn't escalate to X
//                lockContext.promote(transaction, LockType.X);
//            }
            LockType afterEscalate = lockContext.getEffectiveLockType(transaction);

            // escalate to S, not X as required
            if (afterEscalate.equals(LockType.S)) {
                LockType explicit = lockContext.getExplicitLockType(transaction);
                // a real S
                if (explicit.equals(LockType.S)) {
                    lockContext.promote(transaction, LockType.X);
                } else if (explicit.equals(LockType.NL)){
                    lockContext.acquire(transaction, LockType.X);
                }
            }
        } else if (lockType.equals(LockType.S) && currentLock.equals(LockType.IS)) {
            lockContext.escalate(transaction);
        } else if (lockType.equals(LockType.S) && currentLock.equals(LockType.IX)) {
            lockContext.promote(transaction, LockType.SIX);
        }

    }

    // TODO(hw4_part2): add helper methods as you see fit
}
