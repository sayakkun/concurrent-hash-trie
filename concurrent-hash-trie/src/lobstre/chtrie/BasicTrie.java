package lobstre.chtrie;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public class BasicTrie {
    /**
     * Root node of the trie
     */
    @SuppressWarnings("unused")
    private volatile INode root = null;

    /**
     * Width in bits
     */
    private final byte width;

    /**
     * Builds a {@link BasicTrie} instance
     */
    public BasicTrie () {
        this.width = 6;
    }

    /**
     * Builds a {@link BasicTrie} instance
     * 
     * @param width
     *            the Trie width in power-of-two exponents. Values are expected
     *            between between 1 & 6, other values will be clamped.
     *            <p>
     *            The width defines the "speed" of the trie:
     *            <ul>
     *            <li>A value of 1: gives an actual width of two items per
     *            level, hence the trie is O(Log2(N))</li>
     *            <li>A value of 6: gives an actual width of 64 items per level,
     *            hence the trie is O(Log64(N)</li>
     *            </ul>
     */
    public BasicTrie (final int width) {
        if (width > 6) {
            this.width = 6;
        } else if (width < 1) {
            this.width = 1;
        } else {
            this.width = (byte) width;
        }
    }

    /**
     * Inserts or updates a key/value mapping.
     * 
     * @param k
     *            a key {@link Object}
     * @param v
     *            a value Object
     */
    public void insert (final Object k, final Object v) {
        while (true) {
            final INode r = getRoot ();
            if (r == null || r.getMain () == null) {
                // Insertion on an empty trie.
                final CNode cn = new CNode (new SNode (k, v), this.width);
                final INode nr = new INode (cn);
                if (casRoot (r, nr)) {
                    break;
                } else {
                    continue;
                }
                // Else tries inserting in a populated trie
            } else if (iinsert (r, k, v, 0, null)) {
                break;
            } else {
                continue;
            }
        }
    }

    /**
     * Looks up the value associated to a key
     * 
     * @param k
     *            a key {@link Object}
     * @return the value associated to k
     */
    public Object lookup (final Object k) {
        while (true) {
            final INode r = getRoot ();
            if (null == r) {
                // Empty trie
                return null;
            } else if (r.getMain () == null) {
                // Null Inode root, fix it and retry
                casRoot (r, null);
                continue;
            } else {
                // Getting lookup result
                final Result res = ilookup (r, k, 0, null);
                switch (res.type) {
                case FOUND:
                    return res.result;
                case NOTFOUND:
                    return null;
                case RESTART:
                    continue;
                default:
                    throw new RuntimeException ("Unexpected case: " + res.type);
                }
            }
        }
    }

    /**
     * Removes a key/value mapping
     * 
     * @param k
     *            the key Object
     * @return true if removed was performed, false otherwises
     */
    public boolean remove (final Object k) {
        while (true) {
            final INode r = getRoot ();
            if (null == r) {
                // Empty trie
                return false;
            } else if (r.getMain () == null) {
                // Null Inode trie, fix it and retry
                casRoot (r, null);
                continue;
            } else {
                // Getting remove result
                final Result res = iremove (r, k, 0, null);
                switch (res.type) {
                case FOUND:
                    return true;
                case NOTFOUND:
                    return false;
                case RESTART:
                    continue;
                default:
                    throw new RuntimeException ("Unexpected case: " + res.type);
                }
            }
        }
    }

    private Result ilookup (final INode i, final Object k, final int level, final INode parent) {
        final MainNode main = i.getMain ();

        // Usual case
        if (main instanceof CNode) {
            final CNode cn = (CNode) main;
            final FlagPos flagPos = flagPos (k.hashCode (), level, cn.bitmap, this.width);

            // Asked for a hash not in trie
            if (0L == (flagPos.flag & cn.bitmap)) {
                return new Result (ResultType.NOTFOUND, null);
            }

            final BranchNode an = cn.array [flagPos.position];
            if (an instanceof INode) {
                // Looking down
                final INode sin = (INode) an;
                return ilookup (sin, k, level + this.width, i);
            }
            if (an instanceof SNode) {
                // Found the hash locally, let's see if it matches
                final SNode sn = (SNode) an;
                if (sn.key.equals (k)) {
                    return new Result (ResultType.FOUND, sn.value);
                } else {
                    return new Result (ResultType.NOTFOUND, null);
                }
            }
        }

        // Cleaning up trie
        if (main instanceof TNode) {
            clean (parent, level - width);
            return new Result (ResultType.RESTART, null);
        }
        throw new RuntimeException ("Found CNODE/SNODE.!tomb");
    }

    private boolean iinsert (final INode i, final Object k, final Object v, final int level, final INode parent) {
        final MainNode main = i.getMain ();

        // Usual case
        if (main instanceof CNode) {
            final CNode cn = (CNode) main;
            final FlagPos flagPos = flagPos (k.hashCode (), level, cn.bitmap, this.width);

            // Asked for a hash not in trie, let's insert it
            if (0L == (flagPos.flag & cn.bitmap)) {
                final SNode snode = new SNode (k, v);
                final CNode ncn = cn.inserted (flagPos, snode);
                return i.casMain (main, ncn);
            }

            final BranchNode an = cn.array [flagPos.position];
            if (an instanceof INode) {
                // Looking down
                final INode sin = (INode) an;
                return iinsert (sin, k, v, level + this.width, i);
            }
            if (an instanceof SNode) {
                final SNode sn = (SNode) an;
                final SNode nsn = new SNode (k, v);
                // Found the hash locally, let's see if it matches
                if (sn.key.equals (k)) {
                    // Updates the key with the new value
                    final CNode ncn = cn.updated (flagPos.position, nsn);
                    return i.casMain (main, ncn);
                } else {
                    // Creates a sub-level
                    final CNode scn = new CNode (sn, nsn, level + this.width, this.width);
                    final INode nin = new INode (scn);
                    final CNode ncn = cn.updated (flagPos.position, nin);
                    return i.casMain (main, ncn);
                }
            }
        }

        // Cleaning up trie
        if (main instanceof TNode) {
            if (parent != null) {
                clean (parent, level - width);
            }
            return false;
        }
        throw new RuntimeException ("Found CNODE/SNODE.!tomb");
    }

    private Result iremove (final INode i, final Object k, final int level, final INode parent) {
        final MainNode main = i.getMain ();

        // Usual case
        if (main instanceof CNode) {
            final CNode cn = (CNode) main;
            final FlagPos flagPos = flagPos (k.hashCode (), level, cn.bitmap, this.width);

            // Asked for a hash not in trie
            if (0L == (flagPos.flag & cn.bitmap)) {
                return new Result (ResultType.NOTFOUND, null);
            }

            Result res = null;
            final BranchNode an = cn.array [flagPos.position];
            if (an instanceof INode) {
                // Looking down
                final INode sin = (INode) an;
                res = iremove (sin, k, level + this.width, i);
            }
            if (an instanceof SNode) {
                // Found the hash locally, let's see if it matches
                final SNode sn = (SNode) an;
                if (sn.key.equals (k)) {
                    final CNode ncn = cn.removed (flagPos);
                    final MainNode cntn = toContracted (ncn, level);
                    if (i.casMain (cn, cntn)) {
                        res = new Result (ResultType.FOUND, sn.value);
                    } else {
                        res = new Result (ResultType.RESTART, null);
                    }
                } else {
                    res = new Result (ResultType.NOTFOUND, null);
                }
            }
            if (null == res) {
                throw new RuntimeException ("Found CNODE/SNODE.!tomb");
            }
            if (res.type == ResultType.NOTFOUND || res.type == ResultType.RESTART) {
                return res;
            }
            
            if (i.getMain () instanceof TNode) {
                cleanParent (parent, i, k.hashCode (), level - width);
            }
            return res;
        }

        // Cleaning up trie
        if (main instanceof TNode) {
            if (parent != null) {
                clean (parent, level - width);
            }
            return new Result (ResultType.RESTART, null);
        }
        throw new RuntimeException ("Found CNODE/SNODE.!tomb");
    }

    private void cleanParent (final INode parent, final INode i, final int hashCode, final int level) {
        while (true) {
            final MainNode m = i.getMain ();
            final MainNode pm = parent.getMain ();
            if (pm instanceof CNode) {
                final CNode pcn = (CNode) pm;
                final FlagPos flagPos = flagPos (hashCode, level, pcn.bitmap, this.width);
                if (0L == (flagPos.flag & pcn.bitmap)) {
                    return;
                }
                final BranchNode sub = pcn.array [flagPos.position];
                if (sub != i) {
                    return;
                }
                if (m instanceof TNode) {
                    final CNode ncn = pcn.updated (flagPos.position, ((TNode) m).untombed ());
                    if (parent.casMain (pcn, ncn)) {
                        return;
                    } else {
                        continue;
                    }
                }
            } else {
                return;
            }
        }
    }

    private void clean (final INode i, final int level) {
        final MainNode m = i.getMain ();
        if (m instanceof CNode) {
            i.casMain (m, toCompressed ((CNode) m, level));
        }
    }

    private MainNode toCompressed (final CNode cn, final int level) {
        final CNode ncn = cn.copied ();
        
        // Resurrect tombed nodes.
        for (int i = 0; i < ncn.array.length; i++) {
            final BranchNode an = ncn.array [i];
            final TNode tn = getTombNode (an);
            if (null != tn) {
                ncn.array [i] = tn.untombed ();
            }
        }
        
        return toContracted (ncn, level);
    }

    private MainNode toContracted (final CNode cn, final int level) {
        if (level > 0 && 1 == cn.array.length) {
            final BranchNode bn = cn.array[0];
            if (bn instanceof SNode) {
                return ((SNode) bn).tombed ();
            }
        }
        return cn;
    }

    private TNode getTombNode (final BranchNode an) {
        if (an instanceof INode) {
            final INode in = (INode) an;
            final MainNode mn = in.getMain ();
            if (mn instanceof TNode) {
                final TNode tn = (TNode) mn;
                return tn;
            }
        }
        return null;
    }

    private boolean casRoot (final INode r, final INode nr) {
        return ROOT_UPDATER.compareAndSet (this, r, nr);
    }

    private INode getRoot () {
        return ROOT_UPDATER.get (this);
    }

    /**
     * Returns a copy an {@link BranchNode} array with an updated
     * {@link BranchNode} value at a certain position.
     * 
     * @param array
     *            the source {@link BranchNode} array
     * @param position
     *            the position
     * @param n
     *            the updated {@link BranchNode} value
     * @return an updated copy of the source {@link BranchNode} array
     */
    static BranchNode[] updated (final BranchNode[] array, final int position, final BranchNode n) {
        final BranchNode[] narr = new BranchNode[array.length];
        for (int i = 0; i < array.length; i++) {
            if (i == position) {
                narr [i] = n;
            } else {
                narr [i] = array [i];
            }
        }
        return narr;
    }

    /**
     * Returns a copy an {@link BranchNode} array with an inserted
     * {@link BranchNode} value at a certain position.
     * 
     * @param array
     *            the source {@link BranchNode} array
     * @param position
     *            the position
     * @param n
     *            the inserted {@link BranchNode} value
     * @return an updated copy of the source {@link BranchNode} array
     */
    static BranchNode[] inserted (final BranchNode[] array, final int position, final BranchNode n) {
        final BranchNode[] narr = new BranchNode[array.length + 1];
        for (int i = 0; i < array.length + 1; i++) {
            if (i < position) {
                narr [i] = array [i];
            } else if (i == position) {
                narr [i] = n;
            } else {
                narr [i] = array [i - 1];
            }
        }
        return narr;
    }

    /**
     * Returns a copy an {@link BranchNode} array with a removed
     * {@link BranchNode} value at a certain position.
     * 
     * @param array
     *            the source {@link BranchNode} array
     * @param position
     *            the position
     * @return an updated copy of the source {@link BranchNode} array
     */
    static BranchNode[] removed (final BranchNode[] array, final int position) {
        final BranchNode[] narr = new BranchNode[array.length - 1];
        for (int i = 0; i < array.length; i++) {
            if (i < position) {
                narr [i] = array [i];
            } else if (i > position) {
                narr [i - 1] = array [i];
            }
        }
        return narr;
    }

    /**
     * Gets the flag value and insert position for an hashcode, level & bitmap.
     * 
     * @param hc
     *            the hashcode value
     * @param level
     *            the level (in bit progression)
     * @param bitmap
     *            the current {@link CNode}'s bitmap.
     * @param w
     *            the fan width (in bits)
     * @return a {@link FlagPos}'s instance for the specified hashcode, level &
     *         bitmap.
     */
    static FlagPos flagPos (final int hc, final int level, final long bitmap, final int w) {
        final long flag = flag (hc, level, w);
        final int pos = Long.bitCount (flag - 1 & bitmap);
        return new FlagPos (flag, pos);
    }

    /**
     * Gets the flag value for an hashcode level.
     * 
     * @param hc
     *            the hashcode value
     * @param level
     *            the level (in bit progression)
     * @param the
     *            fan width (in bits)
     * @return the flag value
     */
    static long flag (final int hc, final int level, final int w) {
        final int bitsRemaining = Math.min (w, 32 - level);
        final int subHash = hc >> level & (1 << bitsRemaining) - 1;
        final long flag = 1L << subHash;
        return flag;
    }

    /**
     * Atomic Updater for the BasicTrie.root field
     */
    private static final AtomicReferenceFieldUpdater<BasicTrie, INode> ROOT_UPDATER = AtomicReferenceFieldUpdater.newUpdater (BasicTrie.class, INode.class, "root");

    static enum ResultType {
        FOUND, NOTFOUND, RESTART
    }

    static class Result {
        public Result (final ResultType type, final Object result) {
            this.type = type;
            this.result = result;
        }

        public final Object result;
        public final ResultType type;
    }

    /**
     * A Marker interface for what can be in an INode (CNode or SNode)
     */
    static interface MainNode {
    }

    /**
     * A Marker interface for what can be in a CNode array. (INode or SNode)
     */
    static interface BranchNode {
    }

    /**
     * A CAS-able Node which may reference either a CNode or and SNode
     */
    static class INode implements BranchNode {
        /**
         * Builds an {@link INode} instance
         * 
         * @param n
         *            a {@link MainNode}
         */
        public INode (final MainNode n) {
            INODE_UPDATER.set (this, n);
        }

        public MainNode getMain () {
            return INODE_UPDATER.get (this);
        }

        public boolean casMain (MainNode m, MainNode nm) {
            return INODE_UPDATER.compareAndSet (this, m, nm);
        }

        /**
         * Atomic Updater for the INode.main field
         */
        private static final AtomicReferenceFieldUpdater<INode, MainNode> INODE_UPDATER = AtomicReferenceFieldUpdater.newUpdater (INode.class, MainNode.class, "main");
        /**
         * The {@link MainNode} instance
         */
        @SuppressWarnings("unused")
        private volatile MainNode main;
    }

    /**
     * A Node that may contain sub-nodes.
     */
    static class CNode implements MainNode {
        /**
         * Builds a copy of this {@link CNode} instance where a sub-node
         * designated by a position has been added .
         * 
         * @param flagPos
         *            a FlagPos instance
         * @return a copy of this {@link CNode} instance with the inserted node.
         */
        public CNode inserted (final FlagPos flagPos, final SNode snode) {
            final BranchNode[] narr = BasicTrie.inserted (this.array, flagPos.position, snode);
            return new CNode (narr, flagPos.flag | this.bitmap);
        }

        /**
         * Builds a copy of this {@link CNode} instance where a sub {@link BranchNode}
         * designated by a position has been replaced by another one.
         * 
         * @param position
         *            an integer position
         * @param bn a {@link BranchNode} instance
         * @return a copy of this {@link CNode} instance with the updated node.
         */
        public CNode updated (final int position, final BranchNode bn) {
            final BranchNode[] narr = BasicTrie.updated (this.array, position, bn);
            return new CNode (narr, this.bitmap);
        }

        /**
         * Builds a copy of this {@link CNode} instance where a sub-node
         * designated by flag & a position has been removed.
         * 
         * @param flagPos
         *            a {@link FlagPos} instance
         * @return a copy of this {@link CNode} instance where where a sub-node
         *         designated by flag & a position has been removed or null if
         *         the resulting CNode would be empty.
         */
        public CNode removed (final FlagPos flagPos) {
            final BranchNode[] narr = BasicTrie.removed (this.array, flagPos.position);
            if (narr.length == 0) {
                return null;
            } else {
                return new CNode (narr, this.bitmap - flagPos.flag);
            }
        }

        /**
         * Builds a copy of the current node.
         * 
         * @return a {@link CNode} copy
         */
        public CNode copied () {
            final BranchNode[] narr = new BranchNode[array.length];
            System.arraycopy (this.array, 0, narr, 0, array.length);
            return new CNode (narr, bitmap);
        }

        /**
         * Builds a copy of this {@link CNode} instance where its sub-nodes have
         * been filtered.
         * 
         * @param filter
         *            a {@link Filter} instance
         * @return a copy of this {@link CNode} instance where its sub-nodes
         *         have been filtered, or null if the resulting CNode would be
         *         empty.
         */
        public CNode filtered (final Filter filter) {
            int traversed = 0;
            long filteredBitmap = 0L;
            for (int i = 0; i < 64; i++) {
                final long flag = 1L << i;
                if (0L != (this.bitmap & flag)) {
                    final BranchNode an = this.array [traversed++];
                    if (filter.accepts (an)) {
                        filteredBitmap += flag;
                    }
                }
            }

            final BranchNode[] filtered = new BranchNode[Long.bitCount (filteredBitmap)];

            traversed = 0;
            int copied = 0;
            for (int i = 0; i < 64; i++) {
                final long flag = 1L << i;
                if (0L != (filteredBitmap & flag)) {
                    filtered [copied++] = this.array [traversed];
                }
                if (0L != (this.bitmap & flag)) {
                    traversed++;
                }
            }

            return new CNode (filtered, filteredBitmap);
        }

        /**
         * Builds a {@link CNode} instance from a single {@link SNode} instance
         * 
         * @param sNode
         *            a {@link SNode} instance
         * @param width
         *            the width (in power-of-two exponents)
         */
        CNode (final SNode sNode, final int width) {
            final long flag = BasicTrie.flag (sNode.key.hashCode (), 0, width);
            this.array = new BranchNode[] { sNode };
            this.bitmap = flag;
        }

        /**
         * Builds a {@link CNode} instance from two {@link SNode} objects
         * 
         * @param sn1
         *            a first {@link SNode} instance
         * @param sn2
         *            a second {@link SNode} instance
         * @param level
         *            the current level (in bit progression)
         * @param width
         *            the width (in power-of-two exponents)
         */
        CNode (final SNode sn1, final SNode sn2, final int level, final int width) {
            final long flag1 = BasicTrie.flag (sn1.key.hashCode (), level, width);
            final long flag2 = BasicTrie.flag (sn2.key.hashCode (), level, width);
            if (flag1 < flag2) {
                this.array = new BranchNode[] { sn1, sn2 };
            } else {
                this.array = new BranchNode[] { sn2, sn1 };
            }
            this.bitmap = flag1 | flag2;
        }

        /**
         * Builds a {@link CNode} from an array of {@link BranchNode} and its
         * computed bitmap.
         * 
         * @param array
         *            the {@link BranchNode} array
         * @param bitmap
         *            the bitmap
         */
        CNode (final BranchNode[] array, final long bitmap) {
            this.array = array;
            this.bitmap = bitmap;
        }

        /**
         * The internal {@link BranchNode} array.
         */
        public final BranchNode[] array;

        /**
         * The bitmap of the currently allocated objects.
         */
        public final long bitmap;
    }

    static abstract class BaseKVNode {
        /**
         * Builds a {@link SNode} instance
         * 
         * @param k
         *            its key object
         * @param v
         *            its value
         * @param tomb
         *            the tomb flag.
         */
        BaseKVNode (final Object k, final Object v) {
            this.key = k;
            this.value = v;
        }

        /**
         * The object key
         */
        public final Object key;

        /**
         * The object value
         */
        public final Object value;
    }

    /**
     * A Single Node class, holds a key, a value & a tomb flag.
     */
    static class SNode extends BaseKVNode implements BranchNode {
        /**
         * Builds a {@link SNode} instance
         * 
         * @param k
         *            its key object
         * @param v
         *            its value
         */
        SNode (final Object k, final Object v) {
            super (k, v);
        }

        /**
         * @return a copied {@link TNode} for this instance.
         */
        public TNode tombed () {
            return new TNode (this.key, this.value);
        }
    }

    /**
     * A Tombed node instance
     */
    static class TNode extends BaseKVNode implements MainNode {
        /**
         * Builds a {@link TNode} instance
         * 
         * @param k
         *            its key object
         * @param v
         *            its value
         */
        TNode (Object k, Object v) {
            super (k, v);
        }

        /**
         * @return a copied {@link SNode} of this instance
         */
        public SNode untombed () {
            return new SNode (this.key, this.value);
        }
    }

    /**
     * The result of a {@link BasicTrie#flagPos(int, int, long, int)} call.
     * Contains a single bit flag & a position
     */
    static class FlagPos {
        /**
         * Builds a {@link FlagPos} instance
         * 
         * @param flag
         *            the bit flag
         * @param position
         *            the array location.
         */
        FlagPos (final long flag, final int position) {
            this.flag = flag;
            this.position = position;
        }

        /**
         * A single bit flag that may bit compared to a {@link CNode}'s bitmap.
         */
        public final long flag;
        /**
         * Its position in the array
         */
        public final int position;
    }

    /**
     * A filter interface. Has a single method for accepting/rejecting object(s)
     */
    static interface Filter {
        /**
         * @param an
         *            {@link BranchNode} instance
         * @return true if the {@link BranchNode} is accepted
         */
        public boolean accepts (BranchNode an);
    }
}
