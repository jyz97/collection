package edu.berkeley.cs186.database.concurrency;

public enum LockType {
    S,   // shared
    X,   // exclusive
    IS,  // intention shared
    IX,  // intention exclusive
    SIX, // shared intention exclusive
    NL;  // no lock held

    /**
     * This method checks whether lock types A and B are compatible with
     * each other. If a transaction can hold lock type A on a resource
     * at the same time another transaction holds lock type B on the same
     * resource, the lock types are compatible.
     */
    public static boolean compatible(LockType a, LockType b) {
        if (a == null || b == null) {
            throw new NullPointerException("null lock type");
        }
        // TODO(hw4_part1): implement
        if (a.equals(NL) || b.equals(NL)) { return true;}
        if (a.equals(X) || b.equals(X)) { return false;}
        if (a.equals(IS) || b.equals(IS)) { return true;}
        if (a.equals(SIX) || b.equals(SIX)) {return false;}
        if (a.equals(IX) && b.equals(IX)) { return true;}
        if (a.equals(S) && b.equals(S)) { return true;}

        return false;
    }

    /**
     * This method returns the lock on the parent resource
     * that should be requested for a lock of type A to be granted.
     */
    public static LockType parentLock(LockType a) {
        if (a == null) {
            throw new NullPointerException("null lock type");
        }
        switch (a) {
        case S: return IS;
        case X: return IX;
        case IS: return IS;
        case IX: return IX;
        case SIX: return IX;
        case NL: return NL;
        default: throw new UnsupportedOperationException("bad lock type");
        }
    }

    /**
     * This method returns if parentLockType has permissions to grant a childLockType
     * on a child.
     */
    public static boolean canBeParentLock(LockType parentLockType, LockType childLockType) {
        if (parentLockType == null || childLockType == null) {
            throw new NullPointerException("null lock type");
        }
        // TODO(hw4_part1): implement
        if (childLockType.equals(NL)) { return true;}

        switch (parentLockType) {
            case S: return false;
            case X: return false;
            case IS: return childLockType.equals(S) || childLockType.equals(IS);
            case IX: return true;
            case SIX: return childLockType.equals(X) || childLockType.equals(IX);
            case NL: return false;
            default: throw new UnsupportedOperationException("bad lock type");
        }
    }

    /**
     * This method returns whether a lock can be used for a situation
     * requiring another lock (e.g. an S lock can be substituted with
     * an X lock, because an X lock allows the transaction to do everything
     * the S lock allowed it to do).
     */
    public static boolean substitutable(LockType substitute, LockType required) {
        if (required == null || substitute == null) {
            throw new NullPointerException("null lock type");
        }
        // TODO(hw4_part1): implement
        if (substitute.equals(X)) { return true;}
        if (substitute.equals(required)) { return true;}
        switch (required) {
            case S: return substitute.equals(SIX);
            case X: return false;
            case IS: return substitute.equals(S) || substitute.equals(SIX) || substitute.equals(IX) ;
            case IX: return substitute.equals(SIX);
            case SIX: return false;
            case NL: return true;
            default: throw new UnsupportedOperationException("bad lock type");
        }
    }

    @Override
    public String toString() {
        switch (this) {
        case S: return "S";
        case X: return "X";
        case IS: return "IS";
        case IX: return "IX";
        case SIX: return "SIX";
        case NL: return "NL";
        default: throw new UnsupportedOperationException("bad lock type");
        }
    }
}

