import java.util.LinkedList;

class Simple  {
  private static int x;

  private static int read() {
    int y = 42;

    if(x==y){
      return 0;
    }

    return 1;
  }
 
  private static void test(String in) {
    System.out.println(in);
    String s2 = in + "asdw";
  }
 
  private static void write(int arg) {
    arg += 5;
    x = arg;
  }

  private static void takeThisObjectAndDoSomething(Particular obj) {
    //do nothing
    obj.incrementY();
  }

  private void doSomethingElse(int arg, boolean flag) {
    // do nothing external, but perform a read/write to local
    flag = !flag;
  }




  public static void main(String[] args) {/*
    new Thread() {
      public void run() {
        new Simple().write(42);
      }
    }.start();*/
    
    new Simple().doSomethingElse(5,true);
    x = 0;
    write(43);
      
    
    for(int i=0; i < 4000; i++) {
      write(42);
    }
    for(int i=0; i < 4000; i++) {
      x=0;
      write(43);
    }
    read();
    String s = "adoijwadpiowj";
    test(s);
    test(s);
    test(s);
    test(s);
    Particular obj = new Particular(33);
    obj.incrementY();
    takeThisObjectAndDoSomething(obj);
    
    
    //System.out.println(obj.getY());
  }
}