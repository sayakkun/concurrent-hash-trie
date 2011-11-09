package lobstre.chtrie;

public class TestInsert {
    public static void main (String[] args) {
        BasicTrie bt = new BasicTrie ();
        bt.insert ("a", "a");
        bt.insert ("b", "b");
        bt.insert ("c", "b");
        bt.insert ("d", "b");
        bt.insert ("e", "b");
        
        for (int i = 0;  i < 128; i++) {
            bt.insert (Integer.valueOf (i), Integer.valueOf (i));
        }
    }
}