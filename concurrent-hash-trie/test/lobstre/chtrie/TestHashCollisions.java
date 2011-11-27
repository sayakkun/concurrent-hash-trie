package lobstre.chtrie;

public class TestHashCollisions {
    public static void main (final String[] args) {
        final BasicTrie bt = new BasicTrie ();
        
        insertStrings (bt);
        insertChars (bt);
        insertInts (bt);
        insertBytes (bt);
        
        removeStrings (bt);
        removeChars (bt);
        removeInts (bt);
        removeBytes (bt);
        
        insertStrings (bt);
        insertInts (bt);
        insertBytes (bt);
        insertChars (bt);
        
        removeBytes (bt);
        removeStrings (bt);
        removeChars (bt);
        removeInts (bt);
        
        System.out.println (bt);
    }
    
    private static void insertChars (final BasicTrie bt) {
        TestHelper.assertEquals (null, bt.insert ('a', 'a'));
        TestHelper.assertEquals (null, bt.insert ('b', 'b'));
        TestHelper.assertEquals (null, bt.insert ('c', 'c'));
        TestHelper.assertEquals (null, bt.insert ('d', 'd'));
        TestHelper.assertEquals (null, bt.insert ('e', 'e'));
    }

    private static void insertStrings (final BasicTrie bt) {
        TestHelper.assertEquals (null, bt.insert ("a", "a"));
        TestHelper.assertEquals (null, bt.insert ("b", "b"));
        TestHelper.assertEquals (null, bt.insert ("c", "b"));
        TestHelper.assertEquals (null, bt.insert ("d", "b"));
        TestHelper.assertEquals (null, bt.insert ("e", "b"));
    }

    private static void insertBytes (final BasicTrie bt) {
        for (byte i = 0; i < 128 && i >= 0; i++) {
            final Byte bigB = Byte.valueOf (i);
            TestHelper.assertEquals (null, bt.insert (bigB, bigB));
        }
    }

    private static void insertInts (final BasicTrie bt) {
        for (int i = 0; i < 128; i++) {
            final Integer bigI = Integer.valueOf (i);
            TestHelper.assertEquals (null, bt.insert (bigI, bigI));
        }
    }

    private static void removeChars (final BasicTrie bt) {
        TestHelper.assertTrue (null != bt.lookup ('a'));
        TestHelper.assertTrue (null != bt.lookup ('b'));
        TestHelper.assertTrue (null != bt.lookup ('c'));
        TestHelper.assertTrue (null != bt.lookup ('d'));
        TestHelper.assertTrue (null != bt.lookup ('e'));
        
        TestHelper.assertTrue (bt.remove ('a'));
        TestHelper.assertTrue (bt.remove ('b'));
        TestHelper.assertTrue (bt.remove ('c'));
        TestHelper.assertTrue (bt.remove ('d'));
        TestHelper.assertTrue (bt.remove ('e'));
        
        TestHelper.assertFalse (bt.remove ('a'));
        TestHelper.assertFalse (bt.remove ('b'));
        TestHelper.assertFalse (bt.remove ('c'));
        TestHelper.assertFalse (bt.remove ('d'));
        TestHelper.assertFalse (bt.remove ('e'));
        
        TestHelper.assertTrue (null == bt.lookup ('a'));
        TestHelper.assertTrue (null == bt.lookup ('b'));
        TestHelper.assertTrue (null == bt.lookup ('c'));
        TestHelper.assertTrue (null == bt.lookup ('d'));
        TestHelper.assertTrue (null == bt.lookup ('e'));
    }

    private static void removeStrings (final BasicTrie bt) {
        TestHelper.assertTrue (null != bt.lookup ("a"));
        TestHelper.assertTrue (null != bt.lookup ("b"));
        TestHelper.assertTrue (null != bt.lookup ("c"));
        TestHelper.assertTrue (null != bt.lookup ("d"));
        TestHelper.assertTrue (null != bt.lookup ("e"));
        
        TestHelper.assertTrue (bt.remove ("a"));
        TestHelper.assertTrue (bt.remove ("b"));
        TestHelper.assertTrue (bt.remove ("c"));
        TestHelper.assertTrue (bt.remove ("d"));
        TestHelper.assertTrue (bt.remove ("e"));
        
        TestHelper.assertFalse (bt.remove ("a"));
        TestHelper.assertFalse (bt.remove ("b"));
        TestHelper.assertFalse (bt.remove ("c"));
        TestHelper.assertFalse (bt.remove ("d"));
        TestHelper.assertFalse (bt.remove ("e"));
        
        TestHelper.assertTrue (null == bt.lookup ("a"));
        TestHelper.assertTrue (null == bt.lookup ("b"));
        TestHelper.assertTrue (null == bt.lookup ("c"));
        TestHelper.assertTrue (null == bt.lookup ("d"));
        TestHelper.assertTrue (null == bt.lookup ("e"));
    }

    private static void removeInts (final BasicTrie bt) {
        for (int i = 0; i < 128; i++) {
            final Integer bigI = Integer.valueOf (i);
            TestHelper.assertTrue (null != bt.lookup(bigI));
            TestHelper.assertTrue (bt.remove (bigI));
            TestHelper.assertFalse (bt.remove (bigI));
            TestHelper.assertTrue (null == bt.lookup(bigI));
        }
    }

    private static void removeBytes (final BasicTrie bt) {
        for (byte i = 0; i < 128 && i >= 0; i++) {
            final Byte bigB = Byte.valueOf (i);
            TestHelper.assertTrue (null != bt.lookup(bigB));
            TestHelper.assertTrue (bt.remove (bigB));
            TestHelper.assertFalse (bt.remove (bigB));
            TestHelper.assertTrue (null == bt.lookup(bigB));
        }
    }
}